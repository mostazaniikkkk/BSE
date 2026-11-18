"""Dumps DS2 animations (attacks, locomotion, dodges) (.nsa, decoded by nsa.py) to Player Animation Library JSON for the Minecraft rig.

Retarget (geometric, no hand-made poses):
- DS2 model space is X left, Y back (the character faces -Y), Z up; Minecraft model space is X left, Y down, Z back.
  Vectors map as (x, y, z) -> (x, -z, y).
- body: the chest (Spine1) rotation relative to its bind pose; position: the pelvis offset from its bind height,
  at the scale of the legs (Minecraft 12 px for DS2's 0.97 m hip height).
- head: the head rotation relative to the chest.
- arms / legs: Minecraft limbs are rigid (PAL does not render elbow/knee bends without a bending add-on), so each
  limb points from the shoulder to the wrist (hip to ankle). Its twist comes from the elbow / knee hinge
  (the forearm / calf local Z axis), with the sign fixed per limb so the DS2 bind pose maps to the Minecraft rest pose.
- Root motion (the trajectory channel) is not part of the pose; it is not applied here.
Euler angles follow ModelPart: R = Rz * Ry * Rx (degrees), which PAL uses for limbs and, after its own sign flips,
for the body.

Usage: ds2_anims.py <player_animations dir> <weapons.json>
"""
import ds2paths  # noqa: E402
import glob
import json
import math
import os
import sys

import hitback
import nsa
import powerstance

ANIM_DIR = ds2paths.C0001
PX_PER_M = 12.0 / 0.97


# ---------------------------------------------------------------- 3x3 matrices
def mat(q):
    x, y, z, w = q
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mt(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


C = [[1, 0, 0], [0, 0, -1], [0, 1, 0]]  # DS2 -> Minecraft model space


def to_mc(m):
    return mm(mm(C, m), mt(C))


def norm(v):
    n = math.sqrt(sum(c * c for c in v))
    return [c / n for c in v]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def euler_zyx(m):
    """(x, y, z) degrees with m = Rz(z) Ry(y) Rx(x)."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    x = math.atan2(m[2][1], m[2][2])
    z = math.atan2(m[1][0], m[0][0])
    return [math.degrees(x), math.degrees(y), math.degrees(z)]


def unwrap(prev, cur):
    """Of the two Euler triples for the same rotation (x, y, z) and (x + 180, 180 - y, z + 180), the one closest to the
    previous keyframe, unwrapped by whole turns: PAL interpolates each angle linearly between keyframes."""
    if prev is None:
        return cur
    best = None
    for cand in (cur, [cur[0] + 180, 180 - cur[1], cur[2] + 180]):
        c = [v + 360 * round((p - v) / 360) for p, v in zip(prev, cand)]
        dist = sum((p - v) ** 2 for p, v in zip(prev, c))
        if best is None or dist < best[0]:
            best = (dist, c)
    return best[1]


def rot_x(deg):
    c, s_ = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[1, 0, 0], [0, c, -s_], [0, s_, c]]


def rot_y(deg):
    c, s_ = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, 0, s_], [0, 1, 0], [-s_, 0, c]]


# Vanilla ItemInHandLayer.renderArmWithItem: after translateToHand, mulPose(X -90) then mulPose(Y 180).
ITEM_FRAME = mm(rot_x(-90), rot_y(180))


def item_euler(m):
    """PAL rotates the held item by Y(-rotY) * Z(-rotZ) * X(-rotX) (ItemInHandLayerMixin): returns (rotX, rotY, rotZ)
    in degrees for the rotation m."""
    q = math.asin(max(-1.0, min(1.0, m[1][0])))
    p = math.atan2(-m[2][0], m[0][0])
    r = math.atan2(-m[1][2], m[1][1])
    return [-math.degrees(r), -math.degrees(p), -math.degrees(q)]


# Held items follow the DS2 weapon bones. The weapon bone axes map to the Minecraft item axes by the constant that makes
# the DS2 idle (a00_00_0000, t = 0) equal the vanilla held-item pose: a calibration, not data.
ITEMS = {'right_item': ('right_arm', 'R_Weapon'), 'left_item': ('left_arm', 'L_Weapon')}

LIMBS = {  # name: (start bone, end bone, hinge bone)
    'right_arm': ('R_UpperArm', 'R_Hand', 'R_Forearm'),
    'left_arm': ('L_UpperArm', 'L_Hand', 'L_Forearm'),
    'right_leg': ('R_Thigh', 'R_Foot', 'R_Calf'),
    'left_leg': ('L_Thigh', 'L_Foot', 'L_Calf'),
}


class Retarget:
    def __init__(self, rig):
        self.rig = rig
        bp, bq = nsa.world(rig, rig.bind_pos, rig.bind_quat)
        self.bind_world = bq
        self.bind_pelvis = bp[rig.index('Pelvis')]
        # Hinge sign per limb: the hinge must point to Minecraft +X (left) in the bind pose.
        self.sign = {}
        for limb, (a, b, h) in LIMBS.items():
            hinge = to_mc_vec(mv(mat(bq[rig.index(h)]), [0, 0, 1]))
            self.sign[limb] = 1.0 if hinge[0] >= 0 else -1.0

    def frame(self, lp, lq):
        return self.frame_world(*nsa.world(self.rig, lp, lq))

    def frame_world(self, wp, wq):
        """frame() from model-space (pos, quat) per bone."""
        r = self.rig
        chest = r.index('Spine1')
        body = mm(mat(wq[chest]), mt(mat(self.bind_world[chest])))  # world delta of the chest
        out = {'body': euler_zyx(to_mc(body))}
        pel = wp[r.index('Pelvis')]
        d = [pel[k] - self.bind_pelvis[k] for k in range(3)]
        out['body_pos'] = [d[0] * PX_PER_M, d[2] * PX_PER_M, d[1] * PX_PER_M]  # left, up, back
        head = r.index('Head')
        out['head'] = euler_zyx(to_mc(mm(mt(body), mm(mat(wq[head]), mt(mat(self.bind_world[head]))))))
        inv_body = mt(body)
        arms = {}
        for limb, (a, b, h) in LIMBS.items():
            pa, pb = wp[r.index(a)], wp[r.index(b)]
            y = norm(to_mc_vec(mv(inv_body, [pb[k] - pa[k] for k in range(3)])))
            hinge = to_mc_vec(mv(inv_body, mv(mat(wq[r.index(h)]), [0, 0, 1])))
            hinge = [c * self.sign[limb] for c in hinge]
            dot = sum(hinge[k] * y[k] for k in range(3))
            x = norm([hinge[k] - dot * y[k] for k in range(3)])
            z = cross(x, y)
            arms[limb] = [[x[0], y[0], z[0]], [x[1], y[1], z[1]], [x[2], y[2], z[2]]]
            out[limb] = euler_zyx(arms[limb])
        # Item frame and weapon bone orientation, body-relative, in Minecraft model space.
        out['_items'] = {item: (mm(arms[arm], ITEM_FRAME), to_mc(mm(inv_body, mat(wq[r.index(bone)]))))
                         for item, (arm, bone) in ITEMS.items()}
        return out

    def calibrate(self, lp, lq):
        """K^-1 per item from a reference pose where the held item is at its vanilla pose (PAL rotation zero)."""
        self.kinv = {item: mm(mt(weapon), frame) for item, (frame, weapon) in self.frame(lp, lq)['_items'].items()}

    def item_rotations(self, fr):
        """PAL item rotations: item frame * Rpal = weapon * K^-1."""
        return {item: item_euler(mm(mt(frame), mm(weapon, self.kinv[item])))
                for item, (frame, weapon) in fr['_items'].items()}


def to_mc_vec(v):
    return mv(C, v)


def nsa_file(tae_id):
    gg, ss, nnnn = tae_id // 1000000, tae_id // 10000 % 100, tae_id % 10000
    files = glob.glob(os.path.join(ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (gg, ss, nnnn)))
    return files[0] if files else None


def dump(rig, retarget, path, name, loop=False, overlay=None, start=0.0, end=None, mirror=False):
    """overlay: (local pos, local quat, alpha per bone) of a pose blended over every frame (FeatherBlend2).
    start / end: export only that part of the animation (re-timed from 0).
    mirror: the animation through Morpheme's mirror node 0x87 (nsa.mirror)."""
    anim = nsa.NSA(path)
    end = anim.duration if end is None else min(end, anim.duration)

    def pose_at(t):
        lp, lq = nsa.pose(rig, anim, t)
        if mirror:
            lp, lq = nsa.mirror(rig, lp, lq)
        if overlay is not None:
            olp, olq, alphas = overlay
            for i, a in enumerate(alphas):
                if a:
                    lq[i] = nsa.slerp(lq[i], olq[i], a)
                    lp[i] = tuple(x + (y - x) * a for x, y in zip(lp[i], olp[i]))
        return lp, lq
    return dump_poses(retarget, name, loop, start, end, anim.rate, pose_at)


def dump_poses(retarget, name, loop, start, end, sample_rate, pose_at, world=False):
    """Keyframes of pose_at(t) (local DS2 pose; model-space with world) for t in [start, end], re-timed from 0."""
    # 3 keyframes per DS2 sample (60 Hz): PAL interpolates Euler angles linearly, so near gimbal lock a coarse
    # keyframe spacing would sweep the limb through wrong poses.
    rate = sample_rate * 3
    frames = int(round((end - start) * rate))
    keys = {}
    prev = {}
    for f in range(frames + 1):
        t = min(start + f / rate, end)
        lp, lq = pose_at(t)
        fr = retarget.frame_world(lp, lq) if world else retarget.frame(lp, lq)
        ts = str(round(t - start, 4))
        fr.update(retarget.item_rotations(fr))
        for bone in ('body', 'head', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'right_item', 'left_item'):
            rot = unwrap(prev.get(bone), fr[bone])
            prev[bone] = rot
            keys.setdefault(bone, {}).setdefault('rotation', {})[ts] = [round(c, 2) for c in rot]
        keys['body'].setdefault('position', {})[ts] = [round(c, 3) for c in fr['body_pos']]
    return {'format_version': '1.8.0', 'animations': {name: {
        'loop': loop, 'animation_length': round(end - start, 4), 'bones': keys}}}


# Non-attack animations: Minecraft name -> (TAE id of the DS2 animation, loops).
OTHER = {
    'ds2_idle': (0, True),            # a00_00_0000 Idle
    'ds2_walk': (30010, True),        # a00_03_0010 Walk
    # Jump / fall (docs/research/movement.md "Salto y caída: animación"): SM_Jump, SM_Fall.
    'ds2_jump_takeoff': (130020, False),  # a00_13_0020 JumpTakeoff
    'ds2_fall_loop': (140010, True),      # a00_14_0010 FallLoop
    'ds2_roll_landing': (140020, False),  # a00_14_0020 RollLanding
    'ds2_jog': (40010, True),         # a00_04_0010 Jog
    'ds2_dash': (50010, True),        # a00_05_0010 Dash
    # Locked-on side walk (Morpheme IsLockOn / IsSideWalk, FB_Speed / LR_Speed in the character's frame).
    'ds2_side_walk_forward': (60011, True),   # a00_06_0011 SideWalkForward
    'ds2_side_walk_back': (60012, True),      # a00_06_0012 SideWalkBack
    'ds2_side_walk_left': (60013, True),      # a00_06_0013 SideWalkLeft
    'ds2_side_walk_right': (60014, True),     # a00_06_0014 SideWalkRight
    'ds2_side_jog_forward': (70011, True),    # a00_07_0011 SideWalkJog
    'ds2_side_jog_back': (70012, True),       # a00_07_0012 SideWalkJogBack
    'ds2_side_jog_left': (70013, True),       # a00_07_0013 SideWalkJogLeft
    'ds2_side_jog_right': (70014, True),      # a00_07_0014 SideWalkJogRight
    'roll': (120020, False),          # a00_12_0020 DodgeRollingShort
    'backstep': (110011, False),      # a00_11_0011 StepBackRightShort
    # Equip load variants (docs/research/equipload.md): Tired 70-120 %, Failed > 120 %.
    'roll_tired': (120030, False),          # a00_12_0030 DodgeRollingShortTired
    'roll_failed': (120040, False),         # a00_12_0040 DodgeRollingFailed
    'backstep_tired': (110051, False),      # a00_11_0051 StepBackRightShortTired
    'backstep_failed': (110081, False),     # a00_11_0081 StepBackRightFailed
    'ds2_180220': (180220, False),    # a00_18_0220 ItemUseTorchMake (lighting the torch)
    # Bonfire rest (docs/research/bonfire.md).
    'ds2_bonfire_start': (76010010, False),   # a76_01_0010 BonfireStart (sit down)
    'ds2_bonfire_loop': (76010020, True),     # a76_01_0020 BonfireLoop
    'ds2_bonfire_end': (76010030, False),     # a76_01_0030 BonfireEnd (stand up)
    # Grip change (SM_ChangeWeapon_L / _R, picked by WeaponStorage = WeaponTypeParam +0x24 of the weapon put away).
    'ds2_weapon_change_10': (80010, False),   # a00_08_0010 WeaponChangeToBackTop
    'ds2_weapon_change_20': (80020, False),   # a00_08_0020 WeaponChangeToBackBottom
    'ds2_weapon_change_30': (80030, False),   # a00_08_0030 WeaponChangeToHipFar
    'ds2_180190': (180190, False),            # a00_18_0190 ItemUseAbsorb (Human Effigy)
    'ds2_180060': (180060, False),            # a00_18_0060 ItemUseEnchantWeapon (pine resins)
    'ds2_180090': (180090, False),            # a00_18_0090 ItemUseDeamonsSoul (soul items)
    'ds2_180100': (180100, False),            # a00_18_0100 ItemUseThrowOverhand (Lloyd's Talisman)
    'ds2_180110': (180110, False),            # a00_18_0110 ItemUseThrowHeavyItem (Firebomb)
    'ds2_180080': (180080, False),            # a00_18_0080 ItemUsePray (Homeward Bone)
    'ds2_guard_break': (161010, False),
    # Hit reaction (docs/research/hit_reaction.md s.2 / s.9), for the NPC invader: a00_16_00x0..x3
    # DmgKnockBack{Light,Heavy}{SlashDown,SlashUp,ThrustDown,ThrustUp}_{FL,FR,BL,BR} (x = 1..4 Light, 5..8 Heavy).
    **{'ds2_%d' % (160000 + 10 * x + q): (160000 + 10 * x + q, False) for x in range(1, 9) for q in range(4)},
    # The player's own stagger (docs/research/poise.md s.7): DmgExtraHeavy{Slash,Thrust}{Down,Up}{Short,Long}_{FL,FR,BL,BR}
    # (160110..160183, reaction 4) and DmgTumbleFaceDown / FaceUp (160510 / 160520, reaction 8).
    **{'ds2_%d' % (160000 + 10 * x + q): (160000 + 10 * x + q, False) for x in range(11, 19) for q in range(4)},
    'ds2_160510': (160510, False), 'ds2_160520': (160520, False),
    # Wall bounce (docs/research/wall_bounce.md): SM_HitBack plays these straight-sword clips for every weapon.
    'ds2_29010100': (29010100, False),        # a29_01_0100 StraightSwordAttack1Hand_R_HitBack_Upper_R
    'ds2_29010110': (29010110, False),        # a29_01_0110 StraightSwordAttack1Hand_R_HitBack_Upper_L
    'ds2_29030100': (29030100, False),        # a29_03_0100 StraightSwordAttack2Hand_R_HitBack_Upper_R
    'ds2_29030110': (29030110, False),        # a29_03_0110 StraightSwordAttack2Hand_R_HitBack_Upper_L
    # Critical victims (players; docs/research/weapons.md "Lado de la víctima" / "Riposte"): the catch animations, the
    # get-up, and the parried sequence DmgParryBreak Start / Loop / End.
    'ds2_victim_79020010': (79020010, False), 'ds2_victim_79020020': (79020020, False),
    'ds2_victim_79020030': (79020030, False), 'ds2_victim_79020040': (79020040, False),
    'ds2_victim_79020050': (79020050, False), 'ds2_victim_79030010': (79030010, False),
    'ds2_victim_79040010': (79040010, False),
    'ds2_get_up': (160730, False),                # a00_16_0730 DmgDownFaceDownEnd
    'ds2_parried_loop': (161020, True),           # a00_16_1020 DmgParryBreak_Loop
    'ds2_parried_end': (161030, False),           # a00_16_1030 DmgParryBreak_End       # a00_16_1010 DmgParryBreak_Start (guard break; user's choice)
    # Spell casts (docs/research/casting.md): a38 = StickSpell, a39 = BellSpell. Prepare (..10) -> cast (..20).
    'ds2_38110010': (38110010, False), 'ds2_38110020': (38110020, False), 'ds2_38110120': (38110120, False),
    'ds2_38110220': (38110220, False), 'ds2_38110320': (38110320, False), 'ds2_38110420': (38110420, False),
    'ds2_38110520': (38110520, False), 'ds2_38110610': (38110610, False), 'ds2_38110620': (38110620, False),
    'ds2_38110720': (38110720, False), 'ds2_38110820': (38110820, False), 'ds2_38110920': (38110920, False),
    'ds2_38111110': (38111110, False), 'ds2_38111120': (38111120, False), 'ds2_38111220': (38111220, False),
    'ds2_38111320': (38111320, False), 'ds2_38111420': (38111420, False), 'ds2_38111510': (38111510, False),
    'ds2_38111520': (38111520, False), 'ds2_38111610': (38111610, False), 'ds2_38111620': (38111620, False),
    'ds2_38111720': (38111720, False), 'ds2_38115030': (38115030, False),   # StickSpellNoBullet_R
    'ds2_38130010': (38130010, False), 'ds2_38130020': (38130020, False),   # 2Hand (not referenced by SpellParam)
    'ds2_38150010': (38150010, False),                                      # 2Weapon (not referenced)
    'ds2_39110010': (39110010, False), 'ds2_39110020': (39110020, False), 'ds2_39110110': (39110110, False),
    'ds2_39110120': (39110120, False), 'ds2_39110220': (39110220, False), 'ds2_39110320': (39110320, False),
    'ds2_39110420': (39110420, False), 'ds2_39110520': (39110520, False), 'ds2_39110620': (39110620, False),
    'ds2_39110710': (39110710, False), 'ds2_39110720': (39110720, False), 'ds2_39110810': (39110810, False),
    'ds2_39110820': (39110820, False), 'ds2_39110910': (39110910, False), 'ds2_39110920': (39110920, False),
    'ds2_39130010': (39130010, False), 'ds2_39150010': (39150010, False),
    # Fragrant Branch of Yore on a petrified statue (docs/research/petrification.md section 5): event action 15 ->
    # EventActionCategory 230 -> a70_18_0010 PrayStand.
    'ds2_70180010': (70180010, False),
    # Petrification death (petrification.md section 3): SM_Die 0x1804, DieCategory 110 -> state 0x1835, one of
    # a00_17_0710..0713 DieCurse_00..03 at random; the body keeps the last frame (_hold).
    'ds2_170710': (170710, False), 'ds2_170711': (170711, False), 'ds2_170712': (170712, False),
    'ds2_170713': (170713, False),
}
DIE_CURSE = (170710, 170711, 170712, 170713)

# Locomotion clips that get the two-handed stance layered on (the stance SM 0x3bc sits inside BT_Move).
LOCOMOTION = ['ds2_idle', 'ds2_walk', 'ds2_jog', 'ds2_dash', 'ds2_side_walk_forward', 'ds2_side_walk_back',
              'ds2_side_walk_left', 'ds2_side_walk_right', 'ds2_side_jog_forward', 'ds2_side_jog_back',
              'ds2_side_jog_left', 'ds2_side_jog_right']


def stance_alphas(rig, stance):
    """Per-bone alphas of the FeatherBlend2 that lays the two-handed stance over locomotion (switch 0x3be on
    AttackStance_LR, c0001.nmb; dumped in out/grip/grip_stances.txt of the DS2 reversing folder). Unlisted bones are 0."""
    group = stance // 10000
    if group in (20, 21, 25):            # Fist, Knife, Spear
        spine, upper, clav, arm = (0.2, 0.2), 0.8, 0.75, 1.0
    elif group == 29:                    # StraightSword
        spine, upper, clav, arm = (0.2, 0.5), 1.0, 0.8, 1.0
    elif group in (36, 40):              # LargeAxe; Bow (switch 0x3d5, value 404310: the same alphas)
        spine, upper, clav, arm = (0.2, 0.3), 1.0, 1.0, 1.0
    elif group in (42, 44, 45):          # Crossbow, SmallShield, MiddleShield
        spine, upper, clav, arm = (0.2, 0.3), 0.8, 0.75, 1.0
    else:
        raise ValueError('no alphas for stance %d' % stance)
    out = []
    for n in rig.names:
        if n == 'Spine':
            out.append(spine[0])
        elif n in ('Spine1', 'Neck', 'Head'):
            out.append(spine[1])
        elif n in ('L_Clavicle', 'R_Clavicle'):
            out.append(clav)
        elif n in ('L_UpperArm', 'R_UpperArm'):
            out.append(upper)
        elif n[:2] in ('L_', 'R_') and (n[2:] in ('Forearm', 'Hand', 'Weapon') or n[2:].startswith('Finger')):
            out.append(arm)
        else:
            out.append(0.0)
    return out


def write_hold(out_dir, name, anim, suffix='_hold'):
    """The last DS2 frame of an animation as a static loop (<name><suffix>)."""
    hold = name + suffix
    bones = {}
    for bone, channels in anim['animations'][name]['bones'].items():
        bones[bone] = {ch: {'0.0': list(keys.values())[-1], '0.05': list(keys.values())[-1]}
                       for ch, keys in channels.items()}
    with open(os.path.join(out_dir, hold + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
        json.dump({'format_version': '1.8.0', 'animations': {hold: {
            'loop': True, 'animation_length': 0.05, 'bones': bones}}}, f, separators=(',', ':'))


# Landing shake (docs/research/movement.md "Salto y caída: animación", LandingShakeClient): BT_Move's additive landing
# layer, weighed by LandingRate r. Exported at r = 1 over AdditiveStand; LandingShakeClient takes each pose's delta from
# ds2_landing_stand, scales it by r and lays it over locomotion.
LANDING_STAND = 500220    # a00_50_0220 AdditiveStand (single pose)
LANDING_CROUCH = 500210   # a00_50_0210 AdditiveCrouch (single pose)
LANDING_SHAKE = 500240    # a00_50_0240 AdditiveShakeLanding (0.7333 s)
# Crouch additive: BlendN weights (0, 10) on LandingRate, so Stand -> Crouch at r / 10; the legs, hands and weapons are
# filtered out of it.
LANDING_CROUCH_AT = 0.1
LANDING_CROUCH_BONES = ('Pelvis', 'Spine', 'Spine1', 'Neck', 'Head', 'L_Clavicle', 'R_Clavicle', 'L_UpperArm',
                        'R_UpperArm', 'L_Forearm', 'R_Forearm')
# HipsIK (task 0x47, FUN_14062bd20, the damage reaction's node attributes): Pelvis offset (model space: X left, Y back,
# Z up, metres) and Euler rotation (degrees, FUN_14062d040: q = q_y q_z q_x), both at weight r.
LANDING_HIPS_OFFSET = (0.0, 0.1, -0.4)
LANDING_HIPS_EULER = (25.0, 0.0, 0.0)
# Shake additive per-bone alphas (horizontal speed <= 3.4 m/s slow, else fast); unlisted bones 0.
LANDING_SHAKE_ALPHAS = {
    'slow': {'Spine': 0.65, 'Spine1': 0.65, 'Neck': 0.5, 'Head': 0.5, 'L_Clavicle': 0.1, 'R_Clavicle': 0.1,
             'L_UpperArm': 0.1, 'R_UpperArm': 0.1, 'L_Forearm': 0.2, 'R_Forearm': 0.2},
    'fast': {'Spine': 0.85, 'Spine1': 0.85, 'Neck': 1.0, 'Head': 1.0, 'L_Clavicle': 0.5, 'R_Clavicle': 0.5,
             'L_UpperArm': 1.0, 'R_UpperArm': 1.0, 'L_Forearm': 1.0, 'R_Forearm': 1.0},
}
IDENTITY = (0.0, 0.0, 0.0, 1.0)


def euler_quat(deg):
    """FUN_14062d040: Euler degrees (x, y, z) -> q_y q_z q_x."""
    qs = []
    for axis, a in enumerate(deg):
        h = math.radians(a) / 2
        v = [0.0, 0.0, 0.0]
        v[axis] = math.sin(h)
        qs.append((v[0], v[1], v[2], math.cos(h)))
    return nsa.qmul(nsa.qmul(qs[1], qs[2]), qs[0])


def descends(rig, bone, ancestor):
    while bone >= 0:
        if bone == ancestor:
            return True
        bone = rig.parents[bone]
    return False


def landing_clips(rig, retarget, out_dir):
    """ds2_landing_stand / _crouch / _hips (static poses at r = 1) and ds2_landing_shake_slow / _fast (the shake over
    AdditiveStand with each alpha set)."""
    stand = nsa.pose(rig, nsa.NSA(nsa_file(LANDING_STAND)), 0.0)
    crouch = nsa.pose(rig, nsa.NSA(nsa_file(LANDING_CROUCH)), 0.0)

    def write(name, anim):
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(anim, f, separators=(',', ':'))

    def static(name, pose, world=False):
        write(name, dump_poses(retarget, name, True, 0.0, 0.05, 20.0, lambda t: pose, world))

    static('ds2_landing_stand', stand)
    # Additive (C Stand^-1) over Stand = C on the bones it keeps; positions add C.p - Stand.p.
    lp, lq = list(stand[0]), list(stand[1])
    for n in LANDING_CROUCH_BONES:
        i = rig.index(n)
        lq[i] = nsa.slerp(stand[1][i], crouch[1][i], LANDING_CROUCH_AT)
        lp[i] = tuple(a + (b - a) * LANDING_CROUCH_AT for a, b in zip(stand[0][i], crouch[0][i]))
    static('ds2_landing_crouch', (lp, lq))
    # HipsIK over Stand: the pelvis and what hangs from it turn about the pelvis by Q in model space and move by the
    # offset (the 0x47 conversion through the pelvis' model transform); the leg IK keeps the feet where they were. The
    # retarget reads only the thigh and foot positions and the calf's hinge axis: the calf and foot keep their model
    # transform (the IK solver itself, FUN_140c62640, is not decoded; the hip moves in the leg's plane, so its hinge is
    # kept).
    wp, wq = nsa.world(rig, *stand)
    pelvis = rig.index('Pelvis')
    q = euler_quat(LANDING_HIPS_EULER)
    pivot = wp[pelvis]
    legs = [rig.index(n) for n in ('L_Thigh', 'R_Thigh')]
    hp, hq = list(wp), list(wq)
    for i in range(len(rig.names)):
        if not descends(rig, i, pelvis) or any(descends(rig, i, t) and i != t for t in legs):
            continue
        d = nsa.qrot(q, [wp[i][k] - pivot[k] for k in range(3)])
        hp[i] = tuple(pivot[k] + LANDING_HIPS_OFFSET[k] + d[k] for k in range(3))
        hq[i] = nsa.qmul(q, wq[i])
    static('ds2_landing_hips', (hp, hq), world=True)
    # Shake: out.q = slerp(I, S.q Stand.q^-1, alpha) Stand.q per bone; positions are not added.
    shake = nsa.NSA(nsa_file(LANDING_SHAKE))
    for mode, alphas in LANDING_SHAKE_ALPHAS.items():
        a = [alphas.get(n, 0.0) for n in rig.names]

        def pose_at(t, a=a):
            sp, sq = nsa.pose(rig, shake, t)
            out = list(stand[1])
            for i, w in enumerate(a):
                if w:
                    out[i] = nsa.qmul(nsa.slerp(IDENTITY, nsa.qmul(sq[i], nsa.qconj(stand[1][i])), w), stand[1][i])
            return list(stand[0]), out
        name = 'ds2_landing_shake_' + mode
        write(name, dump_poses(retarget, name, False, 0.0, shake.duration, shake.rate, pose_at))


def stance_tae(stance):
    """Stance id GGSSNN -> animation aGG_SS_00NN."""
    return stance // 10000 * 1000000 + stance // 100 % 100 * 10000 + stance % 100


def main():
    """Writes ds2_<taeId>.json for every animation in weapons.json, plus the OTHER animations."""
    out_dir, weapons_json = sys.argv[1], sys.argv[2]
    rig = nsa.Rig()
    retarget = Retarget(rig)
    retarget.calibrate(*nsa.pose(rig, nsa.NSA(nsa_file(0)), 0.0))
    for name, (tae_id, loop) in OTHER.items():
        # The catch animations are left at 0.9 of their length (condition 0x25b): only that part is exported.
        end = 0.9 * nsa.NSA(nsa_file(tae_id)).duration if name.startswith('ds2_victim_') else None
        anim = dump(rig, retarget, nsa_file(tae_id), name, loop, end=end)
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(anim, f, separators=(',', ':'))
        if tae_id in DIE_CURSE:
            write_hold(out_dir, name, anim)
        if name.startswith('ds2_3') and str(tae_id).endswith('10'):
            # Spell Prepare: after it, state 0x1220 holds its last frame (SingleFrame node 0x1223 at 1.0) until the
            # cast (docs/research/casting.md section 6).
            write_hold(out_dir, name, anim)
    landing_clips(rig, retarget, out_dir)
    weapons = json.load(open(weapons_json, encoding='utf8'))
    # Wall bounce (tools/re/hitback.py, docs/research/wall_bounce.md section 3): SM_HitBack's FeatherBlend2 of the pure
    # clip (upper body) over the 1h / 2h Blend2 by DmgHitBackDistance (pelvis and legs), baked per hold state, EventID
    # side and the distances the weapons use (hitBack.<grip>.<side>.clips).
    bounce = hitback.Bounce(rig)
    for grip, hold in (('oneHanded', '1h'), ('twoHanded', '2h')):
        for side_key, side in (('upperR', 'r'), ('upperL', 'l')):
            for dist, anim_id in weapons['hitBack'][grip][side_key]['clips'].items():
                name = anim_id.split(':')[1]
                anim = dump_poses(retarget, name, False, 0.0, bounce.length(hold, side), bounce.rate(hold, side),
                                  lambda t, h=hold, s=side, d=float(dist): bounce.pose(h, s, d, t))
                with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                    json.dump(anim, f, separators=(',', ':'))
    # Bow (docs/research/bow.md s.3): Prepare / CF / NoBullet as authored (bow in the left hand) and through the mirror
    # node 0x87 (bow in the right hand, SM 0x1163) as ds2_<tae>_mirror; the Prepare's last frame as _hold (state 0x1159,
    # SingleFrame node 0x6d); the one- and two-handed stance clips.
    bow = weapons.get('bow')
    if bow:
        clips = [(int(t), False) for t in weapons['bowAnimations']]
        clips += [(t, True) for t in bow['animations']['stances'].values() for t in [int(t.split('_')[-1])]]
        for tae_id, loop in clips:
            for mirror, suffix in ((False, ''), (True, bow['animations']['mirrorSuffix'])):
                name = 'ds2_%d%s' % (tae_id, suffix)
                anim = dump(rig, retarget, nsa_file(tae_id), name, loop, mirror=mirror)
                with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                    json.dump(anim, f, separators=(',', ':'))
                if tae_id == bow['animations']['prepare']:
                    write_hold(out_dir, name, anim)
    # Crossbow (docs/research/crossbow.md): every clip as authored and through the mirror node; the hold is the Prepare
    # at the SingleFrame_095 fraction (holdFraction) instead of its last frame; the stance clips loop.
    xbow = weapons.get('crossbow')
    if xbow:
        anims = xbow['animations']
        prepares = {g['prepare'] for k, g in anims.items() if isinstance(g, dict) and 'prepare' in g}
        clips = [(int(t), False) for t in weapons['crossbowAnimations']]
        clips += [(int(t.split('_')[-1]), True) for t in anims['stances'].values()]
        for tae_id, loop in clips:
            for mirror, suffix in ((False, ''), (True, anims['mirrorSuffix'])):
                name = 'ds2_%d%s' % (tae_id, suffix)
                anim = dump(rig, retarget, nsa_file(tae_id), name, loop, mirror=mirror)
                with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                    json.dump(anim, f, separators=(',', ':'))
                if tae_id in prepares:
                    end = anims['holdFraction'] * nsa.NSA(nsa_file(tae_id)).duration
                    write_hold(out_dir, name, dump(rig, retarget, nsa_file(tae_id), name, False, end=end, mirror=mirror))
    stances = sorted({w['twoHandStance'] for w in weapons['weapons'].values()})
    for stance in stances:
        stance_pose = nsa.pose(rig, nsa.NSA(nsa_file(stance_tae(stance))), 0.0)
        overlay = (stance_pose[0], stance_pose[1], stance_alphas(rig, stance))
        for loco in LOCOMOTION:
            name = '%s_2h_%d' % (loco, stance)
            with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                json.dump(dump(rig, retarget, nsa_file(OTHER[loco][0]), name, True, overlay), f, separators=(',', ':'))
        # Left weapon two-handed (TwoHandStance -1, SM 0x3bc state 0x3fb): the same `_R` stance through the mirror node
        # 0x87 (docs/research/animations.md "Espejo de Morpheme"); locomotion and alphas are not mirrored.
        mlp, mlq = nsa.mirror(rig, stance_pose[0], stance_pose[1])
        overlay = (mlp, mlq, stance_alphas(rig, stance))
        for loco in LOCOMOTION:
            name = '%s_2h_%d_l' % (loco, stance)
            with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                json.dump(dump(rig, retarget, nsa_file(OTHER[loco][0]), name, True, overlay), f, separators=(',', ':'))
    # Power stance (tools/re/powerstance.py, docs/research/powerstance.md): the stance clips (ds2_<tae>, and ds2_<tae>_l
    # through the mirror node, SM 0x46a), and locomotion with the hold phase baked in (_2w_<stance>[_l]): the clip's
    # last frame (SingleFrame_1 = 1.0) over each locomotion clip with the hold alphas; locomotion is not mirrored.
    ps_stances = sorted(int(k) for k in weapons.get('powerStanceClips', {}))
    for stance in ps_stances:
        tae_id = powerstance.STANCE_CLIPS[stance]
        clip = nsa.NSA(nsa_file(tae_id))
        hold = nsa.pose(rig, clip, clip.duration)
        for side, suffix in (('right', ''), ('left', '_l')):
            name = 'ds2_%d%s' % (tae_id, suffix)
            anim = dump(rig, retarget, nsa_file(tae_id), name, False, mirror=side == 'left')
            with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                json.dump(anim, f, separators=(',', ':'))
            # The hold phase on its own (<name>_hold): the same pose the _2w_ clips bake in (last frame).
            write_hold(out_dir, name, anim)
            lp, lq = hold if side == 'right' else nsa.mirror(rig, hold[0], hold[1])
            overlay = (lp, lq, powerstance.rig_alphas(rig, side, 'hold', stance))
            for loco in LOCOMOTION:
                name = '%s_2w_%d%s' % (loco, stance, suffix)
                with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                    json.dump(dump(rig, retarget, nsa_file(OTHER[loco][0]), name, True, overlay), f,
                              separators=(',', ':'))
    animations = weapons['animations']
    for tae_id, data in animations.items():
        if data is None:
            continue  # no animation in c0001 (weapons.py printed why)
        name = 'ds2_%s' % tae_id
        if data.get('fall'):
            # Plunging attack: the start (0260 up to StateControl1), the airborne loop (0250) and the landing (0260
            # from its Footsteps sync event 2).
            land = nsa_file(data['landTaeId'])
            for clip, path, loop, t0, t1 in ((name + '_start', land, False, 0.0, data['startEnd']),
                                              (name, nsa_file(data['loopTaeId']), True, 0.0, None),
                                              (name + '_landing', land, False, data['landingAt'], None)):
                with open(os.path.join(out_dir, clip + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
                    json.dump(dump(rig, retarget, path, clip, loop, start=t0, end=t1), f, separators=(',', ':'))
            continue
        anim = dump(rig, retarget, nsa_file(int(tae_id)), name)
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(anim, f, separators=(',', ':'))
        if data and data.get('guard'):
            # The guard clip plays at GuardSpeed and blends into its hold at run time (GuardLayerClient,
            # docs/research/weapon_arts.md s.8.4); past its end the clip (no loop) stays on its last frame (<name>_end)
            # while that blend may still run.
            write_hold(out_dir, name, anim, '_end')
            # Held guard, played after the raise: the SingleFrame of the hold state (CP BlenWeight_095, node 0x30
            # default 0.95 = guardLayer.holdFrame, clamped to [0, 1] by TaskSetUpdateTimeViaControlParam) of the clip:
            # node 0x2113 in BT_Guard1Hand_L / _R, 0x21fa in BT_Guard2Hand_R, 0x2286 in BT_Guard2Hand_L.
            end = weapons['guardLayer']['holdFrame'] * nsa.NSA(nsa_file(int(tae_id))).duration
            write_hold(out_dir, name, dump(rig, retarget, nsa_file(int(tae_id)), name, False, end=end))
    # Guard clips a switch picks that no weapon slot names (the torch in the right hand -> a44_32_0010, GuardLayerClient).
    extra = sorted({e['animation'].split('_')[-1] for sides in weapons['guardLayer']['clips'].values()
                    for e in sides.values()} - set(animations))
    for tae_id in extra:
        name = 'ds2_%s' % tae_id
        anim = dump(rig, retarget, nsa_file(int(tae_id)), name)
        with open(os.path.join(out_dir, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(anim, f, separators=(',', ':'))
        write_hold(out_dir, name, anim, '_end')
        end = weapons['guardLayer']['holdFrame'] * nsa.NSA(nsa_file(int(tae_id))).duration
        write_hold(out_dir, name, dump(rig, retarget, nsa_file(int(tae_id)), name, False, end=end))
    print('written', len(animations) + len(OTHER) + 5 + 2 * len(stances) * len(LOCOMOTION)
          + 2 * len(ps_stances) * (1 + len(LOCOMOTION)))


if __name__ == '__main__':
    main()
