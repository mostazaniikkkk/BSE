"""Dumps The Last Giant (c3096, EnemyParam 309600) DS2 animations (.nsa, decoded by nsa.py) to Bedrock / Player
Animation Library JSON for Minecraft's Giant (GiantZombieModel = HumanoidModel), plus the attack index
c3096_attacks.json (docs/research/last_giant.md).

Same geometric retarget as enemy_anims.py (c1020 on the zombie), on c3096's own skeleton:
- Rig: the AnimRigDef of c3096.nmb (gamedata:/morpheme4/chr/c3096.anibnd.dcx). Like c3000 it has 79 bones, so the
  pointer-following reader of ogre_anims.Rig is used (bind pose through the transform buffer's own pointers, channel
  map at the type-2 object's +0x20 pointer).
- Scale: HumanoidModel legs pivot y 12 and are 12 px long (hip 12 px above the feet), c3096's Root bind z is
  4.9672 m: enemy_anims.Retarget's 12 / Root z px per metre already fits. (GiantMobRenderer scales the model by 6:
  hip 72 px = 4.5 blocks in the world, 0.906 of the DS2 hip.)
- Bones: body = chest (Spine1) rotation, head relative to the chest, limbs shoulder -> wrist / hip -> ankle with the
  elbow / knee hinge for the twist (ds2_anims.LIMBS). Fingers, F_Pillar / B_Pillar and the toes are not used.
- held_arm (only in the phase-2 clips, M = 2, and in 65001050): the torn-off left arm the giant wields in its right
  hand. c3096.nmb hangs it from R_Weapon -> R_Weapon_Main: R_Wep_L_Forearm (at the grip) -> R_Wep_L_UpperArm
  (2.371 m along, the elbow). Exported as a limb grip -> elbow (hinge = R_Wep_L_Forearm local Z, sign from the bind
  pose like the other limbs), relative to the chest, plus "position" = the grip relative to the right shoulder
  (R_UpperArm) in the chest frame, Minecraft model axes, px. The Giant model has no such part: it is data for the mod
  to use or ignore.
- Root motion (Morpheme trajectory): clips play in place. Position keys = nsa.trajectory; the rotation channel
  (.nsa +0xb8 block +0x40 offset / +0x4c scale / +0x60 keys, 3 x u16 -> v, q = (2v, 1 - |v|^2) / (1 + |v|^2),
  FUN_14065ef00, docs/research/hit_reaction.md s.12) is decoded here as the yaw about model Z ("trajectoryYaw", deg,
  + = counter-clockwise seen from above, i.e. turning toward model +X = left).

Output values: as enemy_anims.py (ModelPart Euler degrees, R = Rz * Ry * Rx; body about the hip pivot, model point
(0, 12, 0) px).

Usage: last_giant_anims.py <enemy_animations dir> <c3096_attacks.json>
"""
import ds2paths  # noqa: E402
import glob
import json
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import enemy_anims as E  # noqa: E402
import ogre_anims as O  # noqa: E402
import ds2_anims as P  # noqa: E402
import nsa  # noqa: E402
import nmb  # noqa: E402  (D:\Reversing\DS2\tools, put on the path by nsa)
import params  # noqa: E402
import tae  # noqa: E402

SRC = os.path.join(ds2paths.EXTRACTED, 'c3096')
ANIM_DIR = os.path.join(SRC, 'anibnd')
NMB = os.path.join(ANIM_DIR, 'c3096.nmb')
TAE = os.path.join(SRC, 'timeact__chr__c309600_pl.tae')
ENEMY = 309600
# EnemyParam 309600 +0x4c / +0x50 -> EnemyEquipWeaponParam 30960000 / 30961000 -> +0x00 = behaviour rows.
BEHAVIOR_ROWS = {0: 309600, 1: 309610}
HELD = ('R_Wep_L_Forearm', 'R_Wep_L_UpperArm', 'R_Wep_L_Forearm')


class Retarget(E.Retarget):
    """enemy_anims.Retarget (12 / Root z px per metre = the humanoid leg) plus the held arm."""

    def __init__(self, rig):
        super().__init__(rig)
        bp, bq = nsa.world(rig, rig.bind_pos, rig.bind_quat)
        hinge = P.to_mc_vec(P.mv(P.mat(bq[rig.index(HELD[2])]), [0, 0, 1]))
        self.held_sign = 1.0 if hinge[0] >= 0 else -1.0

    def frame(self, lp, lq, held=False):
        out = super().frame(lp, lq)
        if not held:
            return out
        r = self.rig
        wp, wq = nsa.world(r, lp, lq)
        chest = r.index('Spine1')
        body = P.mm(P.mat(wq[chest]), P.mt(P.mat(self.bind_world[chest])))
        inv_body = P.mt(body)
        pa, pb = wp[r.index(HELD[0])], wp[r.index(HELD[1])]
        y = P.norm(P.to_mc_vec(P.mv(inv_body, [pb[k] - pa[k] for k in range(3)])))
        hinge = P.to_mc_vec(P.mv(inv_body, P.mv(P.mat(wq[r.index(HELD[2])]), [0, 0, 1])))
        hinge = [c * self.held_sign for c in hinge]
        dot = sum(hinge[k] * y[k] for k in range(3))
        x = P.norm([hinge[k] - dot * y[k] for k in range(3)])
        z = P.cross(x, y)
        out['held_arm'] = P.euler_zyx([[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]])
        sh = wp[r.index('R_UpperArm')]
        g = P.to_mc_vec(P.mv(inv_body, [pa[k] - sh[k] for k in range(3)]))
        out['held_pos'] = [c * self.px_per_m for c in g]
        return out


def nsa_file(tae_id):
    gg, ss, nnnn = tae_id // 1000000, tae_id // 10000 % 100, tae_id % 10000
    files = sorted(glob.glob(os.path.join(ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (gg, ss, nnnn))))
    assert len(files) == 1, (tae_id, files)
    return files[0]


def dump(rig, retarget, path, name, loop=False, held=False):
    anim = nsa.NSA(path)
    rate = anim.rate * 3  # 3 keyframes per DS2 sample, as enemy_anims
    frames = int(round(anim.duration * rate))
    keys, prev = {}, {}
    bones = E.BONES + (('held_arm',) if held else ())
    for f in range(frames + 1):
        t = min(f / rate, anim.duration)
        fr = retarget.frame(*nsa.pose(rig, anim, t), held=held)
        ts = str(round(t, 4))
        for bone in bones:
            rot = P.unwrap(prev.get(bone), fr[bone])
            prev[bone] = rot
            keys.setdefault(bone, {}).setdefault('rotation', {})[ts] = [round(c, 2) for c in rot]
        keys['body'].setdefault('position', {})[ts] = [round(c, 3) for c in fr['body_pos']]
        if held:
            keys['held_arm'].setdefault('position', {})[ts] = [round(c, 3) for c in fr['held_pos']]
    return anim, {'format_version': '1.8.0', 'animations': {name: {
        'loop': loop, 'animation_length': round(anim.duration, 4), 'bones': keys}}}


def trajectory_yaw(path):
    """Yaw (deg) of the trajectory rotation channel per sample (FUN_14065ef00; hit_reaction.md s.12)."""
    d = open(path, 'rb').read()
    o, = struct.unpack_from('<Q', d, 0xb8)
    n, = struct.unpack_from('<I', d, o + 0x24)
    kp, = struct.unpack_from('<Q', d, o + 0x60)
    if kp == 0:
        q = struct.unpack_from('<4f', d, o + 0x40)
        return [round(math.degrees(2 * math.atan2(q[2], q[3])), 3)] * n
    off = struct.unpack_from('<3f', d, o + 0x40)
    sc = struct.unpack_from('<3f', d, o + 0x4c)
    out, prev = [], None
    for i in range(n):
        k = struct.unpack_from('<3H', d, o + kp + 6 * i)
        v = [k[j] * sc[j] + off[j] for j in range(3)]
        m = sum(c * c for c in v)
        q = (2 * v[0] / (1 + m), 2 * v[1] / (1 + m), 2 * v[2] / (1 + m), (1 - m) / (1 + m))
        a = math.degrees(2 * math.atan2(q[2], q[3]))
        if prev is not None:  # unwrap across +-180
            while a - prev > 180:
                a -= 360
            while a - prev < -180:
                a += 360
        prev = a
        out.append(round(a, 3))
    return out


def trajectory(tae_id):
    path = nsa_file(tae_id)
    rate, keys = nsa.trajectory(path)
    yaw = trajectory_yaw(path)
    turns = any(abs(a - yaw[0]) > 1e-3 for a in yaw)
    return round(rate, 4), [[k[0], k[1]] for k in keys], (yaw if turns else None)


def tracks():
    d = open(NMB, 'rb').read()
    out = {}
    for _p, _anim, trs in nmb.anim_nodes(d):
        ids = [ev[0][2] for name, _c, ev in trs if name == 'TimeAct' and ev]
        if ids and ids[0] not in out:
            out[ids[0]] = trs
    return out


TRACKS = ('HitDuration', 'AttackStateControl', 'AttackCancelControl_Attack', 'StateControl', 'DamageDuration',
          'DamageControl', 'DieControl', 'TargetAngleControl')


def windows(trs, duration):
    out = {}
    for n, _c, ev in trs or []:
        n = n.strip()
        if n in TRACKS:
            out[n] = [[round(s * duration, 4), round((s + l) * duration, 4), u] for s, l, u in ev]
    return out


def anim_id(mode, nn, step=0, one_action=False):
    """aNN_SS_MSSS (enemy_ai.md s.8.2): 65 * 10^6 + NN * 10^4 + (mode + 1) * 10^3 + SSS; SSS = 010 / 020 / 030 per
    step, 050 for the "OneAction" slots (0, 1, 8, 9 here: the only .nsa that exist for them)."""
    return 65000000 + nn * 10000 + (mode + 1) * 1000 + (50 if one_action else 10 * (step + 1))


# Actions the lower ESD ai309600 launches (docs/research/last_giant.md s.4), per mode (f5100: 0 = phase 1, 1 = phase
# 2). Singles: EnemyBehavior{,Second}Param entry (action < 10 -> Behavior[a], < 20 -> Second[a - 10]). Combos:
# EnemyComboBehaviorParam entry action - 50, its +0x00 = the NN of the clips.
SINGLES = {0: [0, 1, 12, 13, 14, 16, 17, 18], 1: [1, 8, 9, 12, 13, 14, 17, 18, 19]}
ONE_ACTION = (0, 1, 8, 9)
COMBOS = (50, 51, 52)

# Attack tables, transcribed from ai309600.esd (out\ai3096\ai309600.txt). Each row in ESD order (the first that holds
# wins), checked every tick while the approach subroutine S2147483645 runs. angle = f7002() (deg, absolute):
# [min, max) ; side = f7003(0, 179) != 0 (True), == 0 (False) or not checked (None); speed = f5900(4), the byte the
# approach writes (0 / 39 / 38 / 37, see s.4.4): 'slow' = neither 37 nor 38; rand = 0 < f1(0) < n (f1(0) =
# RandInt(1, 100) rolled when the table starts); dist = f7018(min, max).
# call: ('single', action) = S2147483647(action, 0); ('combo', action, reach, angle, cut1, cut2) =
# S2147483641(action, reach, angle, 0, cut1, cut2, 0, 0); ('back', 1) = S2147483642(1, 5).
TABLES = {
    0: {'esd': 'S2147483644', 'map': 20, 'approach': [5, 0.8, 1, 3, 10, 2.5], 'rows': [
        (10, [0, 10], None, 'slow', 10, [7, 9], ('single', 18)),
        (10, [0, 10], None, 38, 10, [9.2, 11.2], ('single', 18)),
        (10, [0, 10], None, 37, 10, [11.5, 13.5], ('single', 18)),
        (5, [0, 60], None, 'slow', None, [6, 7], ('single', 17)),
        (5, [0, 60], None, 38, None, [8.2, 9.2], ('single', 17)),
        (5, [0, 60], None, 37, None, [10.5, 11.5], ('single', 17)),
        (12, [70, 90], True, None, 50, [2.5, 4.5], ('combo', 51, 5, 120, 30, 0)),
        (9, [0, 90], True, None, 100, [2.5, 4.5], ('single', 16)),
        (11, [30, 150], False, None, None, [2.5, 3.5], ('combo', 50, 5, 120, 30, 0)),
        (13, [0, 60], False, None, None, [2.5, 5], ('combo', 52, 5, 90, 10, 30)),
        (8, [0, 360], False, None, 30, [0, 3.5], ('single', 14)),
        (6, [0, 360], False, None, None, [0, 3.5], ('single', 12)),
        (7, [0, 360], True, None, None, [0, 3.5], ('single', 13)),
    ]},
    1: {'esd': 'S2147483643', 'map': 30, 'approach': [5, 1, 1, 3.5, 10, 2.5], 'rows': [
        (13, [0, 5], None, 'slow', None, [7, 10], ('single', 19)),
        (13, [0, 5], None, 38, None, [9.2, 12.2], ('single', 19)),
        (13, [0, 5], None, 37, None, [11.5, 14.5], ('single', 19)),
        (11, [0, 10], None, 'slow', 10, [7, 9], ('single', 18)),
        (11, [0, 10], None, 38, 10, [9.2, 11.2], ('single', 18)),
        (11, [0, 10], None, 37, 10, [11.5, 13.5], ('single', 18)),
        (7, [0, 60], None, 'slow', None, [6, 8.5], ('single', 17)),
        (7, [0, 60], None, 38, None, [8.2, 10.7], ('single', 17)),
        (7, [0, 60], None, 37, None, [10.5, 13], ('single', 17)),
        (10, [0, 60], False, None, None, [2.5, 6.5], ('combo', 52, 5, 90, 10, 30)),
        (14, [0, 135], False, None, None, [3.5, 6], ('single', 8)),
        (14, [135, 180], False, None, None, [6, 9], ('single', 8)),
        (15, [0, 60], True, None, None, [3, 5], ('single', 9)),
        (15, [60, 90], True, None, None, [5, 7], ('single', 9)),
        (8, [0, 90], False, None, None, [2, 3.5], ('combo', 50, 4.5, 180, 30, 0)),
        (8, [90, 135], False, None, None, [3, 5], ('combo', 50, 4.5, 180, 30, 0)),
        (8, [135, 180], False, None, None, [4, 6], ('combo', 50, 4.5, 180, 30, 0)),
        (9, [0, 135], True, None, None, [2, 5], ('combo', 51, 5, 90, 30, 0)),
        (9, [135, 360], True, None, None, [3.5, 7], ('combo', 51, 5, 90, 30, 0)),
        (6, [0, 180], False, None, 30, [0, 3.5], ('single', 14)),
        (4, [0, 180], False, None, None, [0, 3.5], ('single', 12)),
        (5, [0, 180], True, None, None, [0, 3.5], ('single', 13)),
    ]},
}
LOCOMOTION = {7001: 'Wep01_Idle', 7002: 'Wep02_Idle', 37001: 'Wep01_Walk', 47001: 'Wep01_Jog'}
DAMAGE = {160115: 'DmgExtraHeavySlash', 160116: 'DmgExtraHeavySlash', 160125: 'DmgExtraHeavySlash',
          160155: 'DmgExtraHeavySlash', 160156: 'DmgExtraHeavySlash', 160165: 'DmgExtraHeavySlash',
          168000: 'DmgBossHit', 168103: 'DmgBreakParts03'}
DEATH = {170010: 'DieNormalSlash_F', 170016: 'DieNormalSlash_F', 170020: 'DieNormalSlash_B'}
STARTUP = {75017101: 'Wep01_StartUp_01'}


def f32(b, o):
    return round(struct.unpack_from('<f', b, o)[0], 4)


def main():
    out_dir, index_path = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    rig = O.Rig(NMB)
    retarget = Retarget(rig)
    tae_d, tae_anims = tae.load(TAE)
    trs = tracks()
    edp = params.load('EnemyDamageParam')
    dcp = params.load('DamageCtrlParam')
    amp_p = params.load('EnemyAttackMoveParam')
    ebp = {n: params.load(n) for n in ('EnemyBehaviorParam', 'EnemyBehaviorSecondParam')}
    combo_p = params.load('EnemyComboBehaviorParam')
    ebullet = params.load('EnemyBulletParam')
    written, rows_used, amps_used, bullets_used = {}, set(), set(), set()

    def export(tae_id, loop=False, held=False):
        name = 'c3096_%d' % tae_id
        if name not in written:
            anim, js = dump(rig, retarget, nsa_file(tae_id), name, loop=loop, held=held)
            with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                json.dump(js, f, separators=(',', ':'))
            written[name] = anim
        return name, written[name]

    def events(tae_id, slots):
        hits = []
        for typ, t0, t1, po in tae_anims.get(tae_id, []):
            if typ == 2200:  # ChrMorphemeTimeActTrackDamageCtrl 0x898: parameter[0] = attack slot k
                k, = struct.unpack_from('<i', tae_d, po)
                row = slots[k] if k < len(slots) else -1
                hits.append({'type': 2200, 'start': round(t0, 4), 'end': round(t1, 4), 'slot': k,
                             'enemyDamageParam': row})
            elif typ == 2202:  # 0x89a (FUN_1403269e0): hit with an explicit damage id, parameter[0]
                row, = struct.unpack_from('<i', tae_d, po)
                hits.append({'type': 2202, 'start': round(t0, 4), 'end': round(t1, 4), 'enemyDamageParam': row})
            elif typ == 2301:  # 0x8fd: bullet (bullet id, damage id) (casting.md s.2)
                bullet, row = struct.unpack_from('<2i', tae_d, po)
                bullets_used.add(bullet)
                hits.append({'type': 2301, 'start': round(t0, 4), 'end': round(t1, 4), 'bullet': bullet,
                             'enemyDamageParam': row})
        for h in hits:
            row = h['enemyDamageParam']
            h['rate'] = f32(edp[row], 0x1c) if row in edp else 0.0
            if row in edp:
                rows_used.add(row)
        other = sorted({(typ, round(t0, 4), round(t1, 4)) for typ, t0, t1, _ in tae_anims.get(tae_id, [])
                        if typ in (110600, 2500, 270001)})
        return hits, [{'type': t, 'start': a, 'end': b} for t, a, b in other]

    def step(tae_id, slots, amp, held):
        name, anim = export(tae_id, held=held)
        amps_used.add(amp)
        hits, other = events(tae_id, slots)
        rate, keys, yaw = trajectory(tae_id)
        w = windows(trs.get(tae_id), anim.duration)
        hd = w.get('HitDuration')
        s = {'id': tae_id, 'animation': name, 'duration': round(anim.duration, 4), 'enemyAttackMoveParam': amp,
             'hits': hits, 'otherEvents': other,
             'hitDurationEnd': hd[-1][1] if hd else None, 'tracks': w,
             'trajectoryRate': rate, 'trajectory': keys}
        if yaw:
            s['trajectoryYaw'] = yaw
        return s

    index = {'source': {'animations': 'gamedata:/morpheme4/chr/c3096.anibnd.dcx',
                        'timeact': 'gamedata:/timeact/chr/c309600_pl.tae',
                        'ai': 'gamedata:/ezstate/ai309600.esd (lower), ai309610.esd (upper)',
                        'generator': 'GeneratorRegistParam_m10_10_00_00 30960000 = (309600, 309610, 309600)',
                        'enemyParam': ENEMY, 'doc': 'docs/research/last_giant.md',
                        'units': 'times in s (TAE events, .nsa +0x28, c3096.nmb tracks [start, end, userData]); '
                                 'distances in m (f7001 / f7018), angles in deg (f7002); trajectory (left, back) m '
                                 'per sample at trajectoryRate, trajectoryYaw deg (+ = toward model left)'},
             'phases': [], 'actions': [], 'locomotion': [], 'damage': [], 'death': [], 'startup': [],
             'damageRows': {}, 'attackMove': {}, 'bullets': {}}

    for mode, row in BEHAVIOR_ROWS.items():
        held = mode == 1
        for a in SINGLES[mode]:
            p = ebp['EnemyBehaviorParam' if a < 10 else 'EnemyBehaviorSecondParam'][row]
            i = a if a < 10 else a - 10
            e = p[i * 0x58:(i + 1) * 0x58]
            assert e[0x1f] != 4, (mode, a)
            slots = list(struct.unpack_from('<3i', e, 0x00))
            amp, = struct.unpack_from('<i', e, 0x18)
            tid = anim_id(mode, a, one_action=a in ONE_ACTION)
            index['actions'].append({
                'mode': mode, 'action': a, 'combo': False,
                'behaviorEntry': '%s %d[%d]' % ('EnemyBehaviorParam' if a < 10 else 'EnemyBehaviorSecondParam',
                                                row, i),
                'steps': [step(tid, slots, amp, held or a == 0)]})
        for a in COMBOS:
            e = combo_p[row][(a - 50) * 0xd8:(a - 49) * 0xd8]
            assert e[0x07] != 4, (mode, a)
            nn, = struct.unpack_from('<i', e, 0x00)
            slots = list(struct.unpack_from('<4i', e, 0x08))
            amps = list(struct.unpack_from('<4i', e, 0x3c))
            steps = []
            for k in range(4):
                tid = anim_id(mode, nn, k)
                if not glob.glob(os.path.join(ANIM_DIR, 'a65_%02d_%d%03d_*.nsa' % (nn, mode + 1, 10 * (k + 1)))):
                    break
                steps.append(step(tid, slots, amps[k], held))
            index['actions'].append({'mode': mode, 'action': a, 'combo': True,
                                     'behaviorEntry': 'EnemyComboBehaviorParam %d[%d]' % (row, a - 50),
                                     'clipGroup': nn, 'steps': steps})

    for mode, t in TABLES.items():
        rows = []
        for state, angle, side, speed, rand, dist, call in t['rows']:
            r = {'esdState': state, 'angle': angle, 'dist': dist}
            if side is not None:
                r['side'] = side
            if speed is not None:
                r['speed'] = speed
            if rand is not None:
                r['rand'] = rand
            if call[0] == 'single':
                r['call'] = {'type': 'single', 'action': call[1]}
            else:
                r['call'] = {'type': 'combo', 'action': call[1], 'continueDistance': call[2],
                             'continueAngle': call[3], 'cutRand': [call[4], call[5]]}
            rows.append(r)
        ap = t['approach']
        index['phases'].append({
            'phase': mode + 1, 'mode': mode, 'lowerMap': t['map'], 'behaviorRow': BEHAVIOR_ROWS[mode],
            'table': t['esd'], 'rows': rows,
            # S2147483645(env0..5): run if d >= env3 + env0, walk if env3 <= d < env3 + env0, stop below env3;
            # give up after env4 s (whole approach) or env5 s walking.
            'approach': {'esd': 'S2147483645', 'env': ap, 'stopDistance': ap[3], 'runDistance': ap[3] + ap[0],
                         'runAngle': 30, 'walkAngle': 45, 'giveUp': ap[4], 'walkGiveUp': ap[5]},
            # After every attack: f1(0) = RandInt(1, 100); 0 < f1(0) < 10 and d < 4.5 -> action 1 (S2147483642).
            'afterAttack': {'rand': 10, 'maxDistance': 4.5, 'action': 1},
            # Table entry (state 3) after the approach returns: d < 8 -> action 1 as well.
            'onApproachEnd': {'maxDistance': 8, 'action': 1}})
    index['phaseChange'] = {
        # ai309600 group 20 state 2: 5300(f5200() < 50 && f5207(1) != 1, 0, 39); f5200 = trunc(chr+0x168 /
        # chr+0x174 * 100) (ChrAi vt +0xd8 = FUN_1404252f0, chr vt +0x1b8 / +0x1c0 = FUN_140312910 / FUN_140312a20).
        'hpPercentBelow': 50, 'unconfirmed': 'f5207(1) != 1',
        # group 30 state 1 -> 5: S2147483646(10) = 5204(0, 0) while f5100() == 0; the mode is 1 afterwards.
        'action': 0, 'mode': 0, 'clip': 65001050, 'newMode': 1}

    for tae_id, label in LOCOMOTION.items():
        name, anim = export(tae_id, loop=True)
        rate, keys, yaw = trajectory(tae_id)
        span = keys[-1][1] - keys[0][1]
        index['locomotion'].append({'id': tae_id, 'animation': name, 'name': label, 'loop': True,
                                    'duration': round(anim.duration, 4),
                                    'speed': round(abs(span) / anim.duration, 4)})
    for group, table in (('damage', DAMAGE), ('death', DEATH), ('startup', STARTUP)):
        for tae_id, label in table.items():
            name, anim = export(tae_id)
            rate, keys, yaw = trajectory(tae_id)
            hits, other = events(tae_id, [])
            e = {'id': tae_id, 'animation': name, 'name': label, 'duration': round(anim.duration, 4),
                 'tracks': windows(trs.get(tae_id), anim.duration), 'trajectoryRate': rate, 'trajectory': keys}
            if hits:
                e['hits'] = hits
            if other:
                e['otherEvents'] = other
            if yaw:
                e['trajectoryYaw'] = yaw
            index[group].append(e)

    for row in sorted(rows_used):
        r = edp[row]
        did, = struct.unpack_from('<I', r, 0)
        c = dcp[did]
        vals = struct.unpack_from('<3i', r, 0x0c)
        index['damageRows'][str(row)] = {
            # EnemyDamageParam (enemies.md s.4.1, enemy_ai.md s.8.4): (type, value) pairs, floor, rate, stamina
            # damage base, knockback speed / distance, volume (dummy +0x31 of c3096.flv, shape +0x33, radius +0x34,
            # length +0x38, radius growth +0x74 over +0x78 s).
            'damage': [[r[0x19 + j], vals[j]] for j in range(3) if vals[j]], 'floor': r[0x18],
            'rate': f32(r, 0x1c), 'staminaDamage': struct.unpack_from('<H', r, 0x24)[0],
            'speed': f32(r, 0x2c), 'distance': f32(r, 0x28), 'downUp': r[0x43],
            'dummySource': r[0x30], 'dummy': r[0x31], 'capsule': r[0x33] == 1, 'radius': f32(r, 0x34),
            'length': f32(r, 0x38), 'radiusEnd': f32(r, 0x74), 'radiusTime': f32(r, 0x78),
            # DamageCtrlParam (poise.md s.4.2, hit_reaction.md s.1): +0x00 subtype, +0x02 poise rule, +0x03 parry,
            # +0x0c level, +0x10 / +0x12 reaction, +0x18 time down, +0xa0 poise damage.
            'dcp': did, 'subtype': c[0x00], 'poiseRule': c[0x02], 'parry': c[0x03],
            'level': struct.unpack_from('<i', c, 0x0c)[0], 'reaction': c[0x10], 'reaction2': c[0x12],
            'downTime': f32(c, 0x18), 'poiseDamage': f32(c, 0xa0)}
    for a in sorted(amps_used):
        r = amp_p[a]
        index['attackMove'][str(a)] = {'turnFrom': f32(r, 0x08), 'turnTo': f32(r, 0x0c), 'lerpFrom': f32(r, 0x10),
                                       'lerpTo': f32(r, 0x14), 'factor': f32(r, 0x24), 'units': 'rad/s'}
    for b in sorted(bullets_used):
        r = ebullet[b]
        # EnemyBulletParam (casting.md s.3): +0x04 origin mode, +0x48 life (s), +0x4c speed, +0x2e / +0x2f
        # bullets / shots, +0xb0..+0xb3 keeps going on contact.
        index['bullets'][str(b)] = {'origin': r[0x04], 'life': f32(r, 0x48), 'speed': f32(r, 0x4c),
                                    'perShot': r[0x2e], 'shots': r[0x2f], 'onContact': list(r[0xb0:0xb4])}

    os.makedirs(os.path.dirname(index_path), exist_ok=True)
    with open(index_path, 'w', encoding='utf8', newline=chr(10)) as f:
        json.dump(index, f, indent=2)
    print('written', len(written), 'animations')


if __name__ == '__main__':
    main()
