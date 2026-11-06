"""Dumps the Hollow Soldier (c1020) DS2 animations (.nsa, decoded by nsa.py) to Bedrock / Player Animation Library JSON
for Minecraft's zombie (vanilla HumanoidModel), plus the attack index c1020_attacks.json (docs/research/c1020_anims.md).

Same retarget as ds2_anims.py (geometric, no hand-made poses), but on c1020's own skeleton:
- Rig: the AnimRigDef of c1020.nmb (gamedata:/morpheme4/chr/c1020.anibnd.dcx), read through the pointer fields of the
  rig object instead of the c0001 constants of nsa.Rig: +0x44 bone count, +0x54 parents, +0x34 bone-name string table,
  +0x3c bind pose (transform buffer; +0x80 / +0x88 the position / quaternion arrays). Pointers are offsets from the body
  start minus 4. The bind pose equals the c1020.flv bone table with Y and Z swapped (checked bone by bone).
- DS2 model space X left, Y back (faces -Y), Z up -> Minecraft model space X left, Y down, Z back: (x, y, z) -> (x, -z, y).
- body: the chest (Spine1) rotation relative to its bind pose; position: the pelvis offset from its bind position at the
  scale of the legs (12 px for c1020's 0.9803 m hip height, Root bind z).
- head: the head rotation relative to the chest.
- arms / legs (relative to the chest): Minecraft limbs are rigid, so each limb points from the shoulder to the wrist (hip
  to ankle); its twist comes from the elbow / knee hinge (forearm / calf local Z), with the sign fixed per limb so the
  DS2 bind pose maps to the Minecraft rest pose.
- Root motion (Morpheme trajectory, nsa.trajectory): the clips play in place; the damage reactions' trajectories
  go to c1020_attacks.json for the server to move the mob (SoulsHitReaction).

Output values (degrees) are ModelPart Euler angles with R = Rz(z) * Ry(y) * Rx(x) (ModelPart.translateAndRotate):
head / right_arm / left_arm / right_leg / left_leg: xRot, yRot, zRot = radians(value), absolute (the rest pose is 0).
body: a rotation of the whole model about the hip pivot (model point (0, 12, 0) px), plus position = (left, up, back) px
(PAL's "body" bone, PlayerRendererMixin). The body ModelPart itself keeps its rest pose.

Usage: enemy_anims.py <enemy_animations dir> <c1020_attacks.json>
"""
import ds2paths  # noqa: E402
import glob
import json
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nsa  # noqa: E402
import nmb  # noqa: E402  (D:\Reversing\DS2\tools, put on the path by nsa)
import ds2_anims as P  # noqa: E402
import tae  # noqa: E402

SRC = os.path.join(ds2paths.EXTRACTED, 'c1020')
ANIM_DIR = os.path.join(SRC, 'anibnd')
NMB = os.path.join(ANIM_DIR, 'c1020.nmb')
TAE = os.path.join(SRC, 'timeact__chr__c102000_pl.tae')


class EnemyRig:
    """AnimRigDef of an enemy .nmb (first rig asset), with the same fields as nsa.Rig."""

    def __init__(self, path=NMB):
        d = open(path, 'rb').read()
        b = [body for o, t, i, body in nmb.objects(d) if t == 1][0]
        u32 = lambda o: struct.unpack_from('<I', b, o)[0]
        n = u32(0x44)
        self.parents = list(struct.unpack_from('<%di' % n, b, 0x54))
        self.names = nmb.string_table(b, u32(0x34) + 4)[:n]
        pose = u32(0x3c) + 4
        pos_arr, quat_arr = pose + 0x20 + u32(pose + 0x80), pose + 0x20 + u32(pose + 0x88)
        self.bind_pos = [struct.unpack_from('<3f', b, pos_arr + 16 * i) for i in range(n)]
        self.bind_quat = [struct.unpack_from('<4f', b, quat_arr + 16 * i) for i in range(n)]
        for q in self.bind_quat:
            assert abs(sum(c * c for c in q) - 1) < 1e-3, q
        self.maps = []
        for o, t, i, body in nmb.objects(d):
            if t == 2:
                count, = struct.unpack_from('<I', body, 0x38)
                self.maps.append(list(struct.unpack_from('<%dH' % count, body, 0x4a)))

    def index(self, name):
        return self.names.index(name)


class Retarget:
    """ds2_anims.Retarget without the held items (c1020 names its weapon bones R_weapon / L_shield)."""

    def __init__(self, rig):
        self.rig = rig
        bp, bq = nsa.world(rig, rig.bind_pos, rig.bind_quat)
        self.bind_world = bq
        self.bind_pelvis = bp[rig.index('Pelvis')]
        self.px_per_m = 12.0 / rig.bind_pos[rig.index('Root')][2]
        self.sign = {}
        for limb, (a, b, h) in P.LIMBS.items():
            hinge = P.to_mc_vec(P.mv(P.mat(bq[rig.index(h)]), [0, 0, 1]))
            self.sign[limb] = 1.0 if hinge[0] >= 0 else -1.0

    def frame(self, lp, lq):
        r = self.rig
        wp, wq = nsa.world(r, lp, lq)
        chest = r.index('Spine1')
        body = P.mm(P.mat(wq[chest]), P.mt(P.mat(self.bind_world[chest])))
        out = {'body': P.euler_zyx(P.to_mc(body))}
        pel = wp[r.index('Pelvis')]
        d = [pel[k] - self.bind_pelvis[k] for k in range(3)]
        out['body_pos'] = [d[0] * self.px_per_m, d[2] * self.px_per_m, d[1] * self.px_per_m]  # left, up, back
        head = r.index('Head')
        out['head'] = P.euler_zyx(P.to_mc(P.mm(P.mt(body), P.mm(P.mat(wq[head]), P.mt(P.mat(self.bind_world[head]))))))
        inv_body = P.mt(body)
        for limb, (a, b, h) in P.LIMBS.items():
            pa, pb = wp[r.index(a)], wp[r.index(b)]
            y = P.norm(P.to_mc_vec(P.mv(inv_body, [pb[k] - pa[k] for k in range(3)])))
            hinge = P.to_mc_vec(P.mv(inv_body, P.mv(P.mat(wq[r.index(h)]), [0, 0, 1])))
            hinge = [c * self.sign[limb] for c in hinge]
            dot = sum(hinge[k] * y[k] for k in range(3))
            x = P.norm([hinge[k] - dot * y[k] for k in range(3)])
            z = P.cross(x, y)
            out[limb] = P.euler_zyx([[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]])
        return out


BONES = ('body', 'head', 'right_arm', 'left_arm', 'right_leg', 'left_leg')


def nsa_file(tae_id):
    gg, ss, nnnn = tae_id // 1000000, tae_id // 10000 % 100, tae_id % 10000
    files = sorted(glob.glob(os.path.join(ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (gg, ss, nnnn))))
    if len(files) > 1:  # a00_00_0000 has Idle and Idle_Head_A/B/C (head overlays): the plain Idle
        files = [f for f in files if '_Head_' not in f]
    assert len(files) == 1, (tae_id, files)
    return files[0]


def dump(rig, retarget, path, name, loop=False):
    anim = nsa.NSA(path)
    rate = anim.rate * 3  # 3 keyframes per DS2 sample, as ds2_anims (linear Euler interpolation near gimbal lock)
    frames = int(round(anim.duration * rate))
    keys, prev = {}, {}
    for f in range(frames + 1):
        t = min(f / rate, anim.duration)
        fr = retarget.frame(*nsa.pose(rig, anim, t))
        ts = str(round(t, 4))
        for bone in BONES:
            rot = P.unwrap(prev.get(bone), fr[bone])
            prev[bone] = rot
            keys.setdefault(bone, {}).setdefault('rotation', {})[ts] = [round(c, 2) for c in rot]
        keys['body'].setdefault('position', {})[ts] = [round(c, 3) for c in fr['body_pos']]
    return anim, {'format_version': '1.8.0', 'animations': {name: {
        'loop': loop, 'animation_length': round(anim.duration, 4), 'bones': keys}}}


# Attacks of ai102000 (docs/research/enemy_ai.md §7.3 / §8.3). Per animation: (action, combo step or None,
# EnemyBehaviorSecondParam / EnemyComboBehaviorParam 102000 entry, reach m, angle deg, ESD call extra).
# Reach / angle: arguments of S2147483595 in the attack table S2147483585 of ai102000.esd.
ATTACKS = {
    65131010: (13, None, 'EnemyBehaviorSecondParam[3]', 4.0, 180, None),
    65141010: (14, None, 'EnemyBehaviorSecondParam[4]', 2.8, 40, 'also S2147483597(14) when f7002() >= 110'),
    65151010: (15, None, 'EnemyBehaviorSecondParam[5]', 4.1, 20, None),
    65161010: (16, None, 'EnemyBehaviorSecondParam[6]', 4.0, 25, 'x = 1'),
    65171010: (17, None, 'EnemyBehaviorSecondParam[7]', 5.1, 20, None),
    65111010: (50, 0, 'EnemyComboBehaviorParam[0]', 2.9, 20, None),
    65111020: (50, 1, 'EnemyComboBehaviorParam[0]', 2.9, 20, None),
    65111030: (50, 2, 'EnemyComboBehaviorParam[0]', 2.9, 20, None),
    65121010: (51, 0, 'EnemyComboBehaviorParam[1]', 3.8, 20, 'also S2147483602(51, 5, 10, 10, 20)'),
    65121020: (51, 1, 'EnemyComboBehaviorParam[1]', 3.8, 20, 'also S2147483602(51, 5, 10, 10, 20)'),
    65121030: (51, 2, 'EnemyComboBehaviorParam[1]', 3.8, 20, 'also S2147483602(51, 5, 10, 10, 20)'),
}
# Attack slots k -> EnemyDamageParam row, read from the regulation (EnemyBehaviorSecondParam 102000 entry +0x00 + 4k;
# EnemyComboBehaviorParam 102000 entry +0x08 + 4k) and the EnemyAttackMoveParam row (+0x18 / +0x3c + 4 * step).
SLOTS = {13: [110200310], 14: [110200410], 15: [110200510], 16: [110200610, 110200620], 17: [110200710],
         50: [110200110, 110200120, 110200130], 51: [110200210, 110200220, 110200230]}
ATTACK_MOVE = {13: 40, 14: 34, 15: 40, 16: 8, 17: 40, 50: 40, 51: 34}

# Locomotion loops. Wep01 = mode 0 (M = 1), the one of EnemyParam 102000 (enemy_ai.md §8.2: a00_00_700M_Wep0M_Idle);
# the plain ones are exported too. Which Morpheme state plays each one is not traced.
LOCOMOTION = {
    7001: 'Wep01_Idle', 37001: 'Wep01_Walk', 47001: 'Wep01_Jog',
    0: 'Idle', 30010: 'Walk', 40010: 'Jog',
}
# Damage reaction for DmgCategory 30 / 40 (docs/research/hit_reaction.md s.9): c1020.nmb BT_Damage 0x779 -> state
# 0x61c, the same for both categories. FeatherBlend2 0x632 (BlendWeight_1 = 1.0): Spine and above from the Light-only
# tree, Root / Pelvis / legs from the Light <-> Heavy Blend2 on DmgDistance (weights 0, 4); switches DmgDownOrUp and
# DmgDirection (-180, 0, 180 -> B / F / B). Up and Down share the _B clips.
DAMAGE = {
    160015: ('light', 'down', 'front'), 160016: ('light', 'any', 'back'), 160025: ('light', 'up', 'front'),
    160055: ('heavy', 'down', 'front'), 160056: ('heavy', 'any', 'back'), 160065: ('heavy', 'up', 'front'),
}


def hit_windows(anims, tae_d, tae_id):
    out = []
    for typ, t0, t1, po in anims.get(tae_id, []):
        if typ == 2200:  # ChrMorphemeTimeActTrackDamageCtrl; parameter[0] = attack slot k
            out.append((round(t0, 4), round(t1, 4), struct.unpack_from('<i', tae_d, po)[0]))
    return out


def main():
    out_dir, index_path = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    rig = EnemyRig()
    retarget = Retarget(rig)
    tae_d, tae_anims = tae.load(TAE)
    index = {'source': {'animations': 'gamedata:/morpheme4/chr/c1020.anibnd.dcx',
                        'timeact': 'gamedata:/timeact/chr/c102000_pl.tae',
                        'ai': 'gamedata:/ezstate/ai102000.esd', 'enemyParam': 102000,
                        'doc': 'docs/research/c1020_anims.md',
                        'units': 'duration / hits in s (TAE event 2200, .nsa +0x28); reach in m, launched when '
                                 'f7001() (distance to target) < reach; angle in deg, launched when f7002() '
                                 '(absolute angle to target) < angle'},
             'attacks': [], 'locomotion': []}
    for tae_id, (action, step, entry, reach, angle, extra) in ATTACKS.items():
        name = 'c1020_%d' % tae_id
        anim, js = dump(rig, retarget, nsa_file(tae_id), name)
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(js, f, separators=(',', ':'))
        slots = SLOTS[action]
        hits = []
        for t0, t1, k in hit_windows(tae_anims, tae_d, tae_id):
            row = slots[k] if step is None else slots[step]
            assert step is None or k == step, (tae_id, k, step)
            hits.append({'start': t0, 'end': t1, 'slot': k, 'enemyDamageParam': row})
        e = {'id': tae_id, 'animation': name, 'duration': round(anim.duration, 4), 'action': action,
             'behaviorEntry': entry, 'reach': reach, 'angle': angle, 'enemyAttackMoveParam': ATTACK_MOVE[action],
             'hits': hits}
        if step is not None:
            e['comboStep'] = step
        if extra:
            e['note'] = extra
        index['attacks'].append(e)
    for tae_id, label in LOCOMOTION.items():
        name = 'c1020_%d' % tae_id
        anim, js = dump(rig, retarget, nsa_file(tae_id), name, loop=True)
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(js, f, separators=(',', ':'))
        index['locomotion'].append({'id': tae_id, 'animation': name, 'name': label, 'loop': True,
                                    'duration': round(anim.duration, 4)})
    index['damage'] = []
    for tae_id, (weight, height, side) in DAMAGE.items():
        name = 'c1020_%d' % tae_id
        anim, js = dump(rig, retarget, nsa_file(tae_id), name)
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(js, f, separators=(',', ':'))
        rate, keys = nsa.trajectory(nsa_file(tae_id))
        index['damage'].append({'id': tae_id, 'animation': name, 'weight': weight, 'height': height, 'side': side,
                                'duration': round(anim.duration, 4),
                                # Root motion (nsa.trajectory): X left / Y back, metres, per sample at trajectoryRate.
                                'trajectoryRate': round(rate, 4), 'trajectory': [[k[0], k[1]] for k in keys]})
    os.makedirs(os.path.dirname(index_path), exist_ok=True)
    with open(index_path, 'w', encoding='utf8', newline=chr(10)) as f:
        json.dump(index, f, indent=2)
    print('written', len(ATTACKS) + len(LOCOMOTION) + len(DAMAGE), 'animations')


if __name__ == '__main__':
    main()
