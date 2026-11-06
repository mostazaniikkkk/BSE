"""Dumps the Ogre (c3000) DS2 animations (.nsa, decoded by nsa.py) to Bedrock / Player Animation Library JSON for
Minecraft's iron golem, plus the attack index c3000_attacks.json (docs/research/c3000_ogre.md).

Same geometric retarget as enemy_anims.py (c1020 on the zombie), on c3000's own skeleton:
- Rig: the AnimRigDef of c3000.nmb (gamedata:/morpheme4/chr/c3000.anibnd.dcx). c3000 has 79 bones, so the bone
  bitmask before the bind pose's arrays takes 3 words and enemy_anims.EnemyRig's fixed +0x80 / +0x88 do not hold: the
  pose transform buffer's own pointers are followed (pose +0x40 -> sub-struct; sub +0x18 / +0x20 = position /
  quaternion arrays, relative to pose +0x20). The channel -> bone map of the type-2 object is at its +0x20 pointer.
- Scale: the golem's hip is 13 px above its feet (IronGolemModel: legs pivot y 11, boxes 16 px long, feet at y 24),
  c3000's Root bind z is 1.7473 m: 13 / 1.7473 px per metre for the pelvis offset.
- Bones: body = chest (Spine1) rotation, head relative to the chest, limbs shoulder -> wrist / hip -> ankle with the
  elbow / knee hinge for the twist (ds2_anims.LIMBS). The tail, face and fingers are not used.
- Root motion (Morpheme trajectory, nsa.trajectory): clips play in place; the attacks' and the damage reactions'
  trajectories go to c3000_attacks.json for the server to move the golem.

Output values: as enemy_anims.py (ModelPart Euler degrees, R = Rz * Ry * Rx; body about the hip pivot, the golem's
model point (0, 11, 0) px). The arms are meant to turn about the golem's shoulder (model (-+11, -7, 0) px), not the
vanilla arm pivot (0, -7, 0): the client moves the pivot while a c3000 clip plays (ZombieAnimClient).

Usage: ogre_anims.py <enemy_animations dir> <c3000_attacks.json>
"""
import ds2paths  # noqa: E402
import glob
import json
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import enemy_anims as E  # noqa: E402
import nsa  # noqa: E402
import nmb  # noqa: E402  (D:\Reversing\DS2\tools, put on the path by nsa)
import params  # noqa: E402
import tae  # noqa: E402

SRC = os.path.join(ds2paths.EXTRACTED, 'c3000')
ANIM_DIR = os.path.join(SRC, 'anibnd')
NMB = os.path.join(ANIM_DIR, 'c3000.nmb')
TAE = os.path.join(SRC, 'timeact__chr__c300000_pl.tae')
# The golem's hip height (px): IronGolemModel legs pivot y 11, 24 px = the feet.
HIP_PX = 13.0
# EnemyParam 300000 -> EnemyEquipWeaponParam 30000000 +0x00 = 300000: the behaviour rows below.
BEHAVIOR_ROW = 300000


class Rig:
    """AnimRigDef of c3000.nmb, with the same fields as nsa.Rig (pointer-following variant of enemy_anims.EnemyRig)."""

    def __init__(self, path=NMB):
        d = open(path, 'rb').read()
        b = [body for o, t, i, body in nmb.objects(d) if t == 1][0]
        u32 = lambda o: struct.unpack_from('<I', b, o)[0]
        n = u32(0x44)
        self.parents = list(struct.unpack_from('<%di' % n, b, 0x54))
        self.names = nmb.string_table(b, u32(0x34) + 4)[:n]
        pose = u32(0x3c) + 4
        base = pose + 0x20
        sub = base + u32(pose + 0x40)
        pos_arr, quat_arr = base + u32(sub + 0x18), base + u32(sub + 0x20)
        self.bind_pos = [struct.unpack_from('<3f', b, pos_arr + 16 * i) for i in range(n)]
        self.bind_quat = [struct.unpack_from('<4f', b, quat_arr + 16 * i) for i in range(n)]
        for q in self.bind_quat:
            assert abs(sum(c * c for c in q) - 1) < 1e-3, q
        self.maps = []
        for o, t, i, body in nmb.objects(d):
            if t == 2:
                mo, = struct.unpack_from('<I', body, 0x20)
                count, = struct.unpack_from('<I', body, mo)
                self.maps.append(list(struct.unpack_from('<%dH' % count, body, mo + 0x12)))

    def index(self, name):
        return self.names.index(name)


class Retarget(E.Retarget):
    """enemy_anims.Retarget at the golem's leg scale."""

    def __init__(self, rig):
        super().__init__(rig)
        self.px_per_m = HIP_PX / rig.bind_pos[rig.index('Root')][2]


def nsa_file(tae_id):
    """The .nsa of a TAE id. 65111010 has a plain and an "_L_" version (same tracks and root motion); which one the
    network plays is not traced: the plain one."""
    gg, ss, nnnn = tae_id // 1000000, tae_id // 10000 % 100, tae_id % 10000
    files = sorted(glob.glob(os.path.join(ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (gg, ss, nnnn))))
    if len(files) > 1:
        files = [f for f in files if '_L_' not in os.path.basename(f) and '_Head_' not in f]
    assert len(files) == 1, (tae_id, files)
    return files[0]


def tracks():
    """c3000.nmb animation nodes by TAE id (their TimeAct track's event): {name: [(start, end) s, ...]}; the track
    events are fractions of the clip, times here at speed 1."""
    d = open(NMB, 'rb').read()
    out = {}
    for _p, _anim, trs in nmb.anim_nodes(d):
        ids = [ev[0][2] for name, _c, ev in trs if name == 'TimeAct' and ev]
        if ids and ids[0] not in out:
            out[ids[0]] = trs
    return out


def window(trs, name, duration):
    for n, _c, ev in trs:
        if n.strip() == name:
            return [(round(s * duration, 4), round((s + l) * duration, 4)) for s, l, _u in ev]
    return []


def trajectory(tae_id):
    """Root motion (nsa.trajectory), (left, back) metres per sample; the rotation channel must be constant."""
    path = nsa_file(tae_id)
    raw = open(path, 'rb').read()
    o, = struct.unpack_from('<Q', raw, 0xb8)
    rot_keys, = struct.unpack_from('<Q', raw, o + 0x60)
    assert rot_keys == 0, (tae_id, 'trajectory turns')
    rate, keys = nsa.trajectory(path)
    return round(rate, 4), [[k[0], k[1]] for k in keys]


# ai300000 (docs/research/c3000_ogre.md s.4): single actions (EnemyBehaviorSecondParam entry action - 10) with the
# reach / angle of their attack subroutine S2147483639(action, reach, angle).
SINGLES = {14: (65141010, 2.0, 30.0), 15: (65151010, 2.0, 30.0)}
# Combos (EnemyComboBehaviorParam entry action - 50): steps, launch reach / angle (S2147483621 -> S2147483632(5, 6,
# 5, 30, ...): f7018(0, 5) and f7002() < 30), continuation limits (S2147483628(combo, 6, 90, ...): cut when
# f7001() >= 6 or f7002() >= 90). Combo 52 goes through S2147483633(52, 0, ...): no approach, and its zero limits cut
# it after the first step.
COMBOS = {50: ([65101010, 65101020, 65101030], 5.0, 30.0, 6.0, 90.0),
          51: ([65101010, 65101020, 65101130], 5.0, 30.0, 6.0, 90.0),
          52: ([65111010, 65111020], None, None, 0.0, 0.0)}
LOCOMOTION = {7001: 'Wep01_Idle', 37001: 'Wep01_Walk', 47001: 'Wep01_Jog'}
# Damage reaction for DmgCategory 30 / 40: c3000.nmb state 0x284, the same tree as c1020's 0x61c (hit_reaction.md
# s.9): Light <-> Heavy Blend2 on DmgDistance (0, 4) for the lower body, Light for the upper; DmgDownOrUp; F / B.
DAMAGE = {
    160015: ('light', 'down', 'front'), 160016: ('light', 'any', 'back'), 160025: ('light', 'up', 'front'),
    160055: ('heavy', 'down', 'front'), 160056: ('heavy', 'any', 'back'), 160065: ('heavy', 'up', 'front'),
}


def main():
    out_dir, index_path = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    rig = Rig()
    retarget = Retarget(rig)
    tae_d, tae_anims = tae.load(TAE)
    trs = tracks()
    second = params.load('EnemyBehaviorSecondParam')[BEHAVIOR_ROW]
    combo_rows = params.load('EnemyComboBehaviorParam')[BEHAVIOR_ROW]
    edp = params.load('EnemyDamageParam')
    written = {}

    def export(tae_id, loop=False):
        name = 'c3000_%d' % tae_id
        if name not in written:
            anim, js = E.dump(rig, retarget, nsa_file(tae_id), name, loop=loop)
            with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                json.dump(js, f, separators=(',', ':'))
            written[name] = anim
        return name, written[name]

    def step(tae_id, slots, amp):
        name, anim = export(tae_id)
        duration = round(anim.duration, 4)
        hits = []
        for t0, t1, k in E.hit_windows(tae_anims, tae_d, tae_id):
            row = slots[k] if k < len(slots) else -1
            # EnemyDamageParam +0x1c: the damage rate (0 in the rows without damage values).
            rate = round(struct.unpack_from('<f', edp[row], 0x1c)[0], 4) if row in edp else 0.0
            hits.append({'start': t0, 'end': t1, 'slot': k, 'enemyDamageParam': row, 'rate': rate})
        rate, keys = trajectory(tae_id)
        tr = trs[tae_id]
        hit = window(tr, 'HitDuration', anim.duration)
        return {'id': tae_id, 'animation': name, 'duration': duration, 'enemyAttackMoveParam': amp, 'hits': hits,
                # nmb track HitDuration (userData 123) end = AttackStateControl 100 -> 101: the next combo step's first
                # frame is this step's pose there (c3000_ogre.md s.5).
                'hitDurationEnd': hit[-1][1] if hit else duration,
                'trajectoryRate': rate, 'trajectory': keys}

    index = {'source': {'animations': 'gamedata:/morpheme4/chr/c3000.anibnd.dcx',
                        'timeact': 'gamedata:/timeact/chr/c300000_pl.tae',
                        'ai': 'gamedata:/ezstate/ai300000.esd', 'enemyParam': 300000,
                        'doc': 'docs/research/c3000_ogre.md',
                        'units': 'duration / hits / hitDurationEnd in s (TAE event 2200, .nsa +0x28, c3000.nmb '
                                 'tracks); reach / limits in m (f7001 / f7018), angles in deg (f7002); trajectory '
                                 '(left, back) m per sample at trajectoryRate'},
             'actions': [], 'locomotion': [], 'damage': []}
    for action, (tae_id, reach, angle) in SINGLES.items():
        e = second[(action - 10) * 0x58:(action - 9) * 0x58]
        slots = list(struct.unpack_from('<3i', e, 0x00))
        amp, = struct.unpack_from('<i', e, 0x18)
        index['actions'].append({'action': action, 'combo': False,
                                 'behaviorEntry': 'EnemyBehaviorSecondParam[%d]' % (action - 10),
                                 'reach': reach, 'angle': angle, 'steps': [step(tae_id, slots, amp)]})
    for action, (ids, reach, angle, limit, limit_angle) in COMBOS.items():
        e = combo_rows[(action - 50) * 0xd8:(action - 49) * 0xd8]
        slots = list(struct.unpack_from('<4i', e, 0x08))
        amps = list(struct.unpack_from('<4i', e, 0x3c))
        a = {'action': action, 'combo': True, 'behaviorEntry': 'EnemyComboBehaviorParam[%d]' % (action - 50),
             'continueDistance': limit, 'continueAngle': limit_angle,
             'steps': [step(t, slots, amps[i]) for i, t in enumerate(ids)]}
        if reach is not None:
            a['reach'] = reach
            a['angle'] = angle
        index['actions'].append(a)
    for tae_id, label in LOCOMOTION.items():
        name, anim = export(tae_id, loop=True)
        rate, keys = trajectory(tae_id)
        span = keys[-1][1] - keys[0][1]
        index['locomotion'].append({'id': tae_id, 'animation': name, 'name': label, 'loop': True,
                                    'duration': round(anim.duration, 4),
                                    # Root motion speed (m/s) of one cycle: |trajectory end - start| / duration.
                                    'speed': round(abs(span) / anim.duration, 4)})
    for tae_id, (weight, height, side) in DAMAGE.items():
        name, anim = export(tae_id)
        rate, keys = trajectory(tae_id)
        tr = trs[tae_id]
        dd = window(tr, 'DamageDuration', anim.duration)
        index['damage'].append({'id': tae_id, 'animation': name, 'weight': weight, 'height': height, 'side': side,
                                'duration': round(anim.duration, 4),
                                # nmb track DamageDuration (userData 250): "in reaction" (b8+0x7e6).
                                'damageDuration': dd[-1][1] if dd else None,
                                'trajectoryRate': rate, 'trajectory': keys})
    os.makedirs(os.path.dirname(index_path), exist_ok=True)
    with open(index_path, 'w', encoding='utf8', newline=chr(10)) as f:
        json.dump(index, f, indent=2)
    print('written', len(written), 'animations')


if __name__ == '__main__':
    main()
