"""Exports a DS2 spell (SpellParam row + its BulletParam / PlayerDamageParam rows and their child bullets) in the
datapack spell format of docs/datapacks.md. Field meanings: docs/research/spells.md, casting.md, catalysts.md.

Usage: spell_export.py <SpellParam id> <out.json>
"""
import ds2paths  # noqa: E402
import os
import json
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import params  # noqa: E402

import glob  # noqa: E402

SP = params.load('SpellParam')
BP = params.load('BulletParam')
PDP = params.load('PlayerDamageParam')
DCP = params.load('DamageCtrlParam')
DMW = params.load('DamageMotionWeightParam')
# Reaction column of the victim (hit_reaction.md s.4: the mod reacts mobs like DS2's player victim, column 2).
VICTIM_COLUMN = 2


def reaction(dcp, level):
    """FUN_140138a40: the reaction after the remap of DamageMotionWeightParam row 1000 (DCP +0x10 + 1) + 10 (level + 1),
    the victim's column; DCP +0x10 itself when the row is missing or the level is negative."""
    base = dcp[0x10]
    row = DMW.get(1000 * (base + 1) + 10 * (level + 1)) if level >= 0 else None
    return row[VICTIM_COLUMN] if row else base


def f(row, off):
    return round(struct.unpack_from('<f', row, off)[0], 4)


def i32(row, off):
    return struct.unpack_from('<i', row, off)[0]


def damage(pdp_id, level=None):
    """PlayerDamageParam fields a spell hit reads (catalysts.md §3, casting.md §3): flat damage +0x0c with its element
    code +0x19 (1 magic, 2 lightning, 3 fire, 4 dark), floor +0x18, rate +0x1c, guard stamina damage +0x24, hit volume
    +0x33 (1 capsule) / +0x34 radius / +0x38 length, radius growth to +0x74 over +0x78 s. {@code level}: the reaction
    level the bullet carries (a child copies its parent's block, FUN_140445ec0), else its own DCP +0x0c."""
    r = PDP[pdp_id]
    dcp = DCP.get(struct.unpack_from('<I', r, 0x00)[0])
    if level is None and dcp is not None:
        level = struct.unpack_from('<b', dcp, 0x0c)[0]
    return {
            # Hit reaction (hit_reaction.md s.8): a bullet's damage row is a PDP like a melee hit's (FUN_140391fe0 ->
            # block +0x8c = DCP +0x0c; FUN_1401345c0 / FUN_140136f80): playback speed PDP +0x2c, reaction after the
            # remap; 'reactionLevel' is what its children inherit.
            'reactionSpeed': f(r, 0x2c), 'reaction': reaction(dcp, level) if dcp is not None else -1,
            'reactionLevel': level if level is not None else -1,
            'flat': i32(r, 0x0c), 'flatElement': r[0x19], 'floor': r[0x18], 'rate': f(r, 0x1c),
            'staminaDamage': struct.unpack_from('<H', r, 0x24)[0], 'capsule': r[0x33] == 1, 'radius': f(r, 0x34),
            'length': f(r, 0x38), 'radiusEnd': f(r, 0x74), 'radiusTime': f(r, 0x78),
            # Origin dummy poly +0x31 and team bits +0x40 (4 self, 8 ally, 0x10 hostile; casting.md s.7).
            'dummyPoly': r[0x31], 'teams': r[0x40],
            # SpEffect applied to whoever is hit when +0x4c bit 0 (FUN_14014be20, docs/research/speffects.md s.3).
            'spEffect': i32(r, 0x48), 'spEffectOnHit': (r[0x4c] & 1) != 0,
            # Effect slot +0x4d (26 left weapon, 27 right weapon, 31 body...) and mode +0x4e (2: replaces the slot).
            'effectSlot': r[0x4d], 'effectMode': r[0x4e]}


def load_speffects():
    """Every SpEffect*.emevd event: id -> [(bank, id, args bytes)] (format: out/hollow/evd.py)."""
    out = {}
    for path in glob.glob(os.path.join(ds2paths.REGULATION, 'SpEffect*.emevd')):
        d = open(path, 'rb').read()
        q = lambda o: struct.unpack_from('<q', d, o)[0]
        ne, eo, io, ao = q(0x10), q(0x18), q(0x28), q(0x78)
        for i in range(ne):
            o = eo + i * 0x30
            eid, cnt, off = q(o), q(o + 8), q(o + 0x10)
            ins = []
            for j in range(cnt):
                p = io + off + j * 0x20
                bank, iid = struct.unpack_from('<ii', d, p)
                alen, aoff = q(p + 8), q(p + 0x10)
                ins.append((bank, iid, d[ao + aoff: ao + aoff + alen]))
            out.setdefault(eid, ins)
    return out


SPEFFECTS = load_speffects()
EFFECTS = {}


def speffect(sid, bullets, effects):
    """The instructions of a SpEffect the mod runs (docs/research/speffects.md); the others are listed as
    'unsupported' so a pack can see what is missing."""
    key = str(sid)
    if key in effects or sid not in SPEFFECTS:
        return
    out = []
    unsupported = []
    effects[key] = {'instructions': out, 'unsupported': unsupported}
    for bank, iid, a in SPEFFECTS[sid]:
        if (bank, iid) == (100120, 6):
            # StatusSelectBullet (FUN_140216ad0): i32 bullet, i32 pdp, u8 stat, u16 thresholds 1..4.
            b, d, stat = struct.unpack_from('<iiB', a, 0)
            th = list(struct.unpack_from('<4H', a, 0xa))
            variants = [bullet(b + k, d + k, bullets) for k in range(5) if b + k in BP]
            out.append({'type': 'statusSelectBullet', 'stat': stat, 'thresholds': [0] + th, 'bullets': variants})
        elif (bank, iid) == (100040, 0):
            # Stat multiplier (FUN_14023cbd0): u8 key, u8 stat, u16 lo, hi, f32 f_lo, f_hi, c1..c4.
            k, stat, lo, hi = struct.unpack_from('<BBHH', a, 0)
            flo, fhi, c1, c2, c3, c4 = struct.unpack_from('<6f', a, 8)
            out.append({'type': 'statMultiplier', 'key': k, 'stat': stat, 'lo': lo, 'hi': hi, 'low': round(flo, 4),
                        'high': round(fhi, 4), 'category': [round(c, 4) for c in (c1, c2, c3, c4)]})
        elif (bank, iid) == (100050, 0) and struct.unpack_from('<ii', a, 0) == (1, 850):
            # MustSub event 850 -> 101050[00]: u16 amount, f32 seconds, u8 stop when HP is full.
            n, t, f = struct.unpack_from('<HxxfB', a, 8)
            out.append({'type': 'heal', 'amount': n, 'duration': round(t, 4), 'stopWhenFull': f != 0})
        elif (bank, iid) == (100040, 6):
            out.append({'type': 'durationScales'})
        elif (bank, iid) == (100080, 1):
            # Property (speffects.md s.11.4): f32 seconds, u16 damage type i, f32 v -> split M[i] += 0.01 v.
            t, i = struct.unpack_from('<fH', a, 0)
            v, = struct.unpack_from('<f', a, 8)
            out.append({'type': 'split', 'duration': round(t, 4), 'damageType': i, 'value': round(v, 4)})
        elif bank == 100090 and iid in (2, 3, 4, 5):
            # ChangeAtkDef (s.11.2 / 11.3): f32 seconds, u16 type a, u16 b; [02]/[03] attack +/- (flat, into S),
            # [04]/[05] defence +/- (a = 0 flat physical, a >= 1 absorption + 0.01 b).
            t, ta, b = struct.unpack_from('<fHH', a, 0)
            out.append({'type': 'attack' if iid < 4 else 'defence', 'duration': round(t, 4), 'damageType': ta,
                        'value': b if iid in (2, 4) else -b})
        elif (bank, iid) == (100090, 6):
            # Movement speed rate (s.11.7): f32 seconds, f32 rate, u8 who (bit 0: player-type characters).
            t, r = struct.unpack_from('<ff', a, 0)
            out.append({'type': 'movementRate', 'duration': round(t, 4), 'rate': round(r, 4)})
        elif bank == 1000 and iid in (0, 1, 9):
            # Stat arrays (s.11.7): i32 index, f32 seconds, then i32 (add) or f32 (rate).
            idx, t = struct.unpack_from('<if', a, 0)
            v = struct.unpack_from('<f' if iid == 1 else '<i', a, 8)[0]
            kind = {0: 'statAdd', 1: 'statRate', 9: 'statAddLarge'}[iid]
            out.append({'type': kind, 'index': idx, 'duration': round(t, 4), 'value': round(v, 4)})
        else:
            unsupported.append('%d[%02d]' % (bank, iid))


def bullet(bullet_id, pdp_id, out, level=None):
    """BulletParam fields with a confirmed reader (casting.md §3)."""
    key = str(bullet_id)
    if key in out:
        return key
    r = BP[bullet_id]
    entry = {
        'damage': damage(pdp_id, level) if pdp_id in PDP else None,
        'life': f(r, 0x48), 'speed': f(r, 0x4c), 'accelFrom': f(r, 0x50), 'accel': f(r, 0x54), 'maxSpeed': f(r, 0x58),
        'gravity': f(r, 0x5c), 'gravityFrom': f(r, 0x60), 'gravityScale': f(r, 0x64),
        'homingTurn': f(r, 0x98), 'homingFrom': f(r, 0x9c), 'homingDelay': f(r, 0xa0), 'homingDistance': f(r, 0x90),
        'homingAngle': f(r, 0x94), 'homingStopNear': f(r, 0x12c),
        'perShot': r[0x2e], 'shots': struct.unpack_from('<b', r, 0x2f)[0], 'interval': f(r, 0x38),
        # Emitter (casting.md section 8, FUN_1404404a0): bullet i of shot n leaves at vertical angle
        # +0x14 + i +0x30 + n +0x3c (positive = down) and horizontal +0x18 + i +0x34 + n +0x40 (positive = +X), then
        # a uniform random spread of +-0x24 (vertical) / +-0x28 (horizontal); origin mode +0x04, direction mode +0x05.
        'fan': [f(r, 0x14), f(r, 0x18), f(r, 0x30), f(r, 0x34), f(r, 0x3c), f(r, 0x40)],
        'spread': [f(r, 0x24), f(r, 0x28)], 'originMode': r[0x04], 'directionMode': r[0x05],
        'origin': [f(r, 0x08), f(r, 0x0c), f(r, 0x10)],
        # Auto-aim with a target (casting.md s.7): horizontal turn limit +0x1c, pitch limit +0x20 (radians); lead the
        # target's predicted height (+0x2c) / horizontal position (+0x2d); +0x07: without lock-on, home on the best
        # lock-on candidate.
        'aimYaw': f(r, 0x1c), 'aimPitch': f(r, 0x20), 'leadHeight': r[0x2c] != 0, 'leadHorizontal': r[0x2d] != 0,
        'homeWithoutLock': r[0x07] != 0,
        'contact': [r[0xb0], r[0xb1], r[0xb2], r[0xb3]], 'onWall': r[0x106],
        'childrenOnHit': r[0xec] != 0, 'childrenOnExpiry': r[0xed] != 0, 'childDelay': f(r, 0xfc),
        'children': [],
    }
    out[key] = entry
    if entry['damage'] and entry['damage']['spEffect'] > 0:
        speffect(entry['damage']['spEffect'], out, EFFECTS)
    for off in (0xd4, 0xdc, 0xe4):
        child, child_damage = i32(r, off), i32(r, off + 4)
        if child > 0 and child in BP:
            entry['children'].append(bullet(child, child_damage, out,
                                            entry['damage']['reactionLevel'] if entry['damage'] else level))
    return key


def main():
    spell_id, path = int(sys.argv[1]), sys.argv[2]
    r = SP[spell_id]
    block = 0x14  # The 8 cast blocks are identical for every player spell (spells.md).
    bullets = {}
    EFFECTS.clear()
    root = bullet(i32(r, 0x0c), i32(r, 0x10), bullets)
    out = {'school': r[0x00], 'requirements': list(struct.unpack_from('<2H', r, 0x08)), 'slots': r[0xf0],
           'uses': list(r[0xf1:0xfb]), 'target': r[0x05], 'rangeFactor': f(r, 0xb4),
           # Block +0x00: a factor of the wait before the cast animation (b8+0x1b8, casting.md section 6).
           'castTime': f(r, block + 0x00),
           'prepare': str(i32(r, block + 0x08)), 'cast': str(i32(r, block + 0x0c)), 'staminaFactor': f(r, block + 0x10),
           'bullet': root, 'bullets': bullets, 'spEffects': dict(EFFECTS)}
    json.dump(out, open(path, 'w', encoding='utf8'), indent=1)
    print(json.dumps(out)[:400])


if __name__ == '__main__':
    main()
