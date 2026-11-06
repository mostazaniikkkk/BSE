"""Builds data/soulsmode/ds2/poise.json: the player's poise (DS2 "Estabilidad") and the player's own hit reaction when
poise holds (reaction 1), read the way the game does (docs/research/poise.md).

- Max (FUN_140145a40 / player vt+0x1f8 = FUN_14037fdd0): sum over the 4 armour pieces of ArmorParam +0x40 x
  (1 - lack penalty, PlayerLackOfStatsParam row 0x1c, piece requirements ArmorParam +0x2c..+0x32) + stat bonus
  (FUN_14038d790: PhysicalStatsPerLevelStatValuesParam +0x90 at min(Endurance, Adaptability)) + ChrParam 100 +0x118
  + SpEffect s8 b8+0x305 + i32 b8+0x3a0.
- Regen (FUN_140145ba0): ChrParam 100 +0x11c per s outside a reaction (b8+0x7e6 == 0); refill to max when a reaction
  ends at <= 0.
- Damage taken per hit (FUN_140137aa0 -> vt+0x120 = FUN_140139160(ctrl, DCP, ...), category 1 only):
  (DamageCtrlParam +0xa0 x A+0x74 + victim calc vt+0x188 + (s8) A+0x70) x multiplay x fall (1 + 0.01 DCP+0xb8 t)
  x victim b8+0x7d0 (TAE 111900 = WeaponParam +0xb8 of the attacking hand) when > 0.
- Reaction 1 (FUN_140136570 / FUN_140139380 -> FUN_14036c310(..., 4, 0)): ChrCommonParam 100 +0x134..+0x144 and the
  c0001.nmb nodes it drives (SM 0x5c7 in BT_Move, SM 0x187d in BT_Damage, HipsIK SM 0x4f9 on MoveHit_Weight).

Usage: poise.py <poise.json>
"""
import ds2paths  # noqa: E402
import json
import os
import re
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import nmb  # noqa: E402
import params  # noqa: E402
import tae  # noqa: E402

import nsa  # noqa: E402

ANIM_DIR = ds2paths.C0001
TAE_PATH = ds2paths.TAE_PL
MOD = ds2paths.DATA

# The mod's armour mapping (src/main/java/dev/soulsmode/SoulsEquipLoad.java, docs/research/equipload.md s.7):
# Minecraft material -> first ArmorParam id of the DS2 set; pieces +0 head, +1 chest, +2 arms, +3 legs.
# Minecraft helmet = head, chestplate = chest, leggings = legs, boots = arms; netherite = the diamond piece.
ARMOR_SETS = {'leather': 11040100, 'chainmail': 17420100, 'iron': 11050100, 'gold': 15130100, 'diamond': 17240100,
              'netherite': 17240100}
TURTLE_HELMET = 11660100  # Old Bell Helm
MC_PIECE = {'helmet': 0, 'chestplate': 1, 'boots': 2, 'leggings': 3}

# TAE events that touch the poise path (FUN_140326240, ChrMorphemeTimeActTrackDamageActionCtrl).
HYPER_EVENTS = {110500: 'b8+0x5e8', 110600: 'b8+0x5ec', 111900: 'b8+0x7d0'}


def f32(b, o):
    return struct.unpack_from('<f', b, o)[0]


# ---------- c0001.nmb node scanner (same layout as D:\Reversing\DS2\out\grip\grip_nodes.py) ----------
class Net:
    def __init__(self, path):
        self.D = D = open(path, 'rb').read()
        self.nodes = {}
        for m in re.finditer(rb'\xcd\xcd\xcd\xcd', D):
            p = m.start() - 0x14
            if p < 0 or p % 4:
                continue
            typ, flags, nid, parent, nch, x, ncp, y = struct.unpack_from('<IHHHHHHI', D, p)
            if typ == 0 or typ > 0x400 or nid > 0x3000:
                continue
            coff, cpoff, aoff = struct.unpack_from('<QQQ', D, p + 0x20)
            if not ((nch == 0 or 0 < coff < 0x400) and coff % 2 == 0 and cpoff < 0x400 and aoff < 0x1000):
                continue
            if nid in self.nodes:
                continue
            ch = list(struct.unpack_from('<%dH' % nch, D, p + coff)) if nch < 200 else []
            cps = [struct.unpack_from('<HH', D, p + cpoff + 4 * k)[0] for k in range(ncp)] if ncp < 64 else []
            self.nodes[nid] = {'p': p, 'type': typ, 'parent': parent, 'children': ch, 'cp': cps}
        order = sorted(self.nodes.values(), key=lambda n: n['p'])
        heads = [(m.start(), m.group(1)[0]) for m in re.finditer(nmb.ATTRIB_HEAD, D, re.S)]
        k = 0
        for i, n in enumerate(order):
            end = order[i + 1]['p'] if i + 1 < len(order) else len(D)
            n['attribs'] = []
            while k < len(heads) and heads[k][0] < n['p']:
                k += 1
            j = k
            while j < len(heads) and heads[j][0] < end:
                n['attribs'].append(heads[j])
                if heads[j][1] == 0x15:
                    n['anim'] = struct.unpack_from('<I', D, heads[j][0] + 0x6c)[0]
                j += 1

    def attrib(self, nid, typ):
        return [q - 8 for q, t in self.nodes[nid]['attribs'] if t == typ]

    def feather_alphas(self, nid):
        """FeatherBlend2 (type 0x72) per-bone weights: attrib 0x57, u16 count at +0x12, offset u64 at +0x18."""
        b = self.attrib(nid, 0x57)[0]
        count, = struct.unpack_from('<H', self.D, b + 0x12)
        off, = struct.unpack_from('<Q', self.D, b + 0x18)
        return struct.unpack_from('<%df' % count, self.D, b + off)

    def cp_default(self, nid):
        """Default value of a control-parameter node: attrib 0x3 (float) / 0x4 (vector3) at +0x10."""
        for typ, n in ((0x4, 3), (0x3, 1)):
            b = self.attrib(nid, typ)
            if b:
                return list(struct.unpack_from('<%df' % n, self.D, b[0] + 0x10))
        return None

    def transition_duration(self, nid):
        b = self.attrib(nid, 0x3b)
        return f32(self.D, b[0] + 0x10) if b else None


def tracks_window(tracks, name, duration):
    for nm, _, ev in tracks:
        if nm.strip() == name and ev:
            s, l, _ = ev[0]
            return [round(s * duration, 4), round((s + l) * duration, 4)]
    return None


def main(out_path):
    armor = params.load('ArmorParam')
    lack = params.load('PlayerLackOfStatsParam')
    pspl = params.load('PhysicalStatsPerLevelStatValuesParam')
    chr_param = params.load('ChrParam')[100]
    ccp = params.load('ChrCommonParam')[100]
    poise_dmg = params.load('PoiseDamageParam')[0]
    edp = params.load('EnemyDamageParam')
    pdp = params.load('PlayerDamageParam')
    dcp = params.load('DamageCtrlParam')
    wp = params.load('WeaponParam')
    wamp = params.load('WeaponAttackMotionParam')
    _, events = tae.load(TAE_PATH)

    def piece(aid):
        r = armor[aid]
        return {'armorParam': aid, 'poise': round(f32(r, 0x40), 3),
                'requirements': list(struct.unpack_from('<4H', r, 0x2c))}  # Str, Dex, Int, Faith (FUN_14034d150)

    out = {'source': 'docs/research/poise.md'}
    out['armor'] = {m: {p: piece(base + k) for p, k in MC_PIECE.items()} for m, base in ARMOR_SETS.items()}
    out['armor']['turtle'] = {'helmet': piece(TURTLE_HELMET)}
    row = struct.unpack_from('<4f', lack[0x1c], 0)
    out['armorLackOfStats'] = {
        'row': 0x1c, 'values': list(row),
        'formula': 'piece factor = 1 - clamp(0.01*(v0 + (v1 - v0)*f), 0, 1) if f > 0 else 1; f = clamp(sum over Str, '
                   'Dex, Int, Faith with req > 0 of max(0, 1 - stat/req), 0, 1) (FUN_1403811f0, FUN_14034d150)'}
    out['statPoise'] = {'source': 'PhysicalStatsPerLevelStatValuesParam +0x90, row = min(Endurance, Adaptability) '
                                  '(effective stats, outside 1..99 -> 1; FUN_14038d790 derived +0x20)',
                        'values': [round(f32(pspl[s], 0x90), 4) for s in range(1, 100)]}
    out['base'] = round(f32(chr_param, 0x118), 4)
    out['regenPerSecond'] = round(f32(chr_param, 0x11c), 4)
    out['regenPerSecondAlt'] = round(f32(chr_param, 0x124), 4)  # with b8+0x610 (TAE 111200; none in c000100_pl)
    out['poiseDamageParam'] = [round(x, 4) for x in struct.unpack_from('<11f', poise_dmg, 0)]

    # Reaction 1 (poise holds, player-type victim).
    # Dmg_Rotation of the player victim's HipsIK (FUN_14035e820 reads chr+0x40 = ChrCommonParam 100 +0x30c..+0x318).
    out['hipsRotation'] = [round(x, 4) for x in struct.unpack_from('<4f', ccp, 0x30c)]
    out['reaction1'] = {
        'canBeForced': ccp[0x130],  # FUN_140139380 needs ChrCommonParam +0x130 != 0
        'hipsWeight': round(f32(ccp, 0x134) * 0.01, 4),  # FUN_14036c310 type 4: peak MoveHit_Weight
        'rampIn': round(f32(ccp, 0x138), 4),  # s, sin(pi/2 * t / rampIn) (FUN_14036c3f0)
        'rampOut': round(f32(ccp, 0x13c), 4),  # s, 1 - sin(pi/2 * t / rampOut)
        'dmgDuration': round(1.0 / (f32(ccp, 0x138) + f32(ccp, 0x13c)), 4),  # CP DmgDuration = 1 / b8+0x724
        'moveSpeedFactors': [round(1 - 0.01 * f32(ccp, 0x140), 4), round(1 - 0.01 * f32(ccp, 0x144), 4)],
    }
    net = Net(os.path.join(ANIM_DIR, 'c0001.nmb'))
    rig = nsa.Rig()
    names = rig.names
    assert net.nodes[0x5ea]['type'] == 0x72 and net.nodes[0x5e5]['type'] == 0x97 and net.nodes[0x4f9]['type'] == 0xa
    out['reaction1']['overlay'] = {
        'source': 'c0001.nmb SM 0x5c7 (BT_Move): FeatherBlend2 0x5ea (locomotion 0x4e0, ScaleToDuration 0x5e5 on CP '
                  'DmgDuration -> switch DmgSlashOrThrust / DmgDownOrUp -> BlendN DmgDirection of the 16 '
                  'DmgKnockBackLight clips); enters on message 16 with DmgCategory 20 (also restarting itself), '
                  'leaves inside the clip track userData 80 (StateControl)',
        'boneWeights': {names[i]: round(a, 3) for i, a in enumerate(net.feather_alphas(0x5ea)) if a},
        'blendIn': round(net.transition_duration(0x5ed), 4),
        'restartBlend': round(net.transition_duration(0x5eb), 4),
        'blendOut': round(net.transition_duration(0x5ec), 4),
    }
    clips = {}
    for nid, n in net.nodes.items():
        if 'anim' in n:
            p = n['parent']
            while p in net.nodes and p != 0x5c7 and p != 0xffff:
                p = net.nodes[p]['parent']
            if p == 0x5c7:
                clips[n['anim']] = nmb.lookup_tables(net.D)[2][n['anim']].split(chr(92))[-1][:-4]
    out['reaction1']['overlay']['clips'] = sorted(set(clips.values()))
    # Track windows (clip seconds at play speed 1) of the c0001 reaction clips: the Light ones (reaction 1 overlay and
    # DmgCategory 30), Heavy (40), ExtraHeavy (50 / 150 / 160 / 190), Tumble (90) and Stagger (180).
    by_tae = {}
    for _p, anim, trk in nmb.anim_nodes(net.D):
        for name, _, ev in trk:
            if name == 'TimeAct' and ev:
                by_tae.setdefault(ev[0][2], trk)

    def clip(tid):
        prefix = 'a00_16_%04d_' % (tid % 10000)
        f = next(f for f in os.listdir(ANIM_DIR) if f.startswith(prefix))
        dur = nsa.NSA(os.path.join(ANIM_DIR, f)).duration
        return {'name': f[:-4].rsplit('_', 1)[0], 'duration': round(dur, 4),
                'tracks': {t: tracks_window(by_tae[tid], t, dur) for t in (
                    'CancelControl_Avoid', 'CancelControl_AttackAll', 'CancelControl_Guard', 'CancelControl_Move',
                    'StateControl', 'StateControl_1', 'DamageDuration')}}
    out['staggerClips'] = {str(t): clip(t) for t in (160010, 160050, 160110, 160150, 160510, 161210)}
    out['reaction1']['overlay']['clipDuration'] = out['staggerClips']['160010']['duration']
    out['reaction1']['inDamage'] = {
        'source': 'c0001.nmb SM 0x187d (BT_Damage, FeatherBlend2 0x18c1 over SM_Damage 0x18c2): Blend2 '
                  'DmgSlashOrThrust -> DmgDownOrUp -> Blend2 DmgDistance (0.8 Light .. 2.5 Heavy) -> BlendN '
                  'DmgDirection; no ScaleToDuration node',
        'boneWeights': {names[i]: round(a, 3) for i, a in enumerate(net.feather_alphas(0x18c1)) if a},
        'blendIn': round(net.transition_duration(0x18be), 4), 'blendOut': round(net.transition_duration(0x18bf), 4)}
    out['reaction1']['hipsIK'] = {
        'source': 'c0001.nmb SM 0x4f9 (BT_Move): HipsIK 0x4fa (MoveHit_Rotation_R) / 0x4fb (MoveHit_Rotation_L) '
                  'blended by DmgDirection over locomotion 0x523; weight CP MoveHit_Weight (FUN_140362c40 writes the '
                  'type-4 envelope); MoveHit_Position / Rotation are never written by the exe (NMB defaults)',
        'position': net.cp_default(0xb9), 'rotationL': net.cp_default(0xba), 'rotationR': net.cp_default(0xbb),
        'weightDefault': net.cp_default(0xbc)[0],
        'blendIn': round(net.transition_duration(0x4fe), 4), 'blendOut': round(net.transition_duration(0x4ff), 4)}

    # Hyper-armour windows in the player TAE.
    out['taeWindows'] = {str(t): sorted([aid, round(t0, 4), round(t1, 4)] for aid, ev in events.items()
                                        for typ, t0, t1, _ in ev if typ == t) for t in HYPER_EVENTS}

    dmw = params.load('DamageMotionWeightParam')

    def remap(base, level):
        # FUN_140138a40 for the player victim: DamageMotionWeightParam row 1000 (r + 1) + 10 (level + 1), column 2
        # (FUN_140380b30); DCP +0x10 itself without the row or with level < 0; 7 -> 4 when ChrCommonParam +0x1a4 == 0.
        row = dmw.get(1000 * (base + 1) + 10 * (level + 1)) if level >= 0 else None
        r = row[2] if row else base
        return 4 if r == 7 and ccp[0x1a4] == 0 else r

    def hit_fields(did, row):
        # FUN_14013b4d0 / FUN_140138cc0 read these from the damage row itself (PDP or EDP, resolved by id in
        # FUN_1403b56d0), not from the hit block: +0x2c speed, +0x28 distance, s8 +0x32 slash/thrust (DCP +0x00 when
        # 0xff), +0x43 down/up mode; DCP +0xa4 / +0xa8 / +0xbc near / far / distance falloff.
        d = dict(dcp_fields(did))
        st = row[0x32] if row[0x32] != 0xff else dcp[did][0x00]
        d.update({'remapped': remap(dcp[did][0x10], d['level']), 'speed': round(f32(row, 0x2c), 4),
                  'distance': round(f32(row, 0x28), 4), 'thrust': 1 if st == 1 else 0, 'downUp': row[0x43],
                  'near': round(f32(dcp[did], 0xa4), 4), 'far': round(f32(dcp[did], 0xa8), 4),
                  'falloff': round(f32(dcp[did], 0xbc), 4)})
        return d

    def dcp_fields(did):
        r = dcp[did]
        return {'dcp': did, 'poiseDamage': round(f32(r, 0xa0), 4), 'poiseRule': r[0x02], 'reaction': r[0x10], 'level': struct.unpack_from('<i', r, 0x0c)[0],
                'poiseFall': round(f32(r, 0xb8), 3), 'fallNear': round(f32(r, 0xa4), 3),
                'fallFar': round(f32(r, 0xa8), 3), 'forceLight': round(f32(r, 0x2c), 3), 'forceLightFlag': r[0x4d]}

    # Enemy hits the mod uses: poise damage = DCP +0xa0 (A+0x74 = 1.0, A+0x70 = 0 for enemy-built hits).
    enemy = {}
    for f in ('c1020_attacks.json', 'c3000_attacks.json'):
        ids = sorted(set(map(int, re.findall(r'"enemyDamageParam": (\d+)', open(os.path.join(MOD, f)).read()))))
        enemy[f.split('_')[0]] = {str(i): hit_fields(struct.unpack_from('<I', edp[i], 0)[0], edp[i]) for i in ids}
    out['enemyHits'] = enemy

    # Player weapons: DCP of each PDP (poise damage DCP +0xa0 and rule +0x02 against a poised target) and the
    # TAE windows of the attack; attackPoiseFactor = WeaponParam +0xb8 (b8+0x7d0 while TAE 111900 is active).
    weapons = json.load(open(os.path.join(MOD, 'weapons.json')))['weapons']
    pw = {}
    for name, w in weapons.items():
        row = wp.get(w['weaponId'])
        if row is None:
            continue
        entry = {'attackPoiseFactor': round(f32(row, 0xb8), 4), 'attacks': {}}
        for hand in ('right', 'left', 'two_handed', 'two_handed_other', 'kick', 'power_stance', 'guard_attack'):
            for kind, a in (w.get(hand) or {}).items():
                if not isinstance(a, dict) or 'motionId' not in a:
                    continue
                m = wamp.get(a['motionId'])
                # The three PDP slots of WeaponAttackMotionParam +0x2c, kept in place (null = empty) so the mod can
                # index them by the hit window's slot like weapons.json reactionMotions.
                ids = list(struct.unpack_from('<3I', m, 0x2c)) if m else []
                win = {HYPER_EVENTS[t]: [[round(t0, 4), round(t1, 4)] for typ, t0, t1, _ in events.get(a['taeId'], [])
                                         if typ == t] for t in HYPER_EVENTS}
                entry['attacks']['%s.%s' % (hand, kind)] = {
                    'taeId': a['taeId'],
                    'hits': [hit_fields(struct.unpack_from('<I', pdp[i], 0)[0], pdp[i]) if i in pdp else None
                             for i in ids],
                    **{k: v for k, v in win.items() if v}}
        pw[name] = entry
    out['playerWeapons'] = pw
    json.dump(out, open(out_path, 'w', encoding='utf8'), indent=1)
    print('armor sets', len(out['armor']), 'enemy rows', sum(len(v) for v in enemy.values()), 'weapons', len(pw))


if __name__ == '__main__':
    main(sys.argv[1])
