"""Power stance (states 5 / 6, docs/research/powerstance.md) data shared by weapons.py and ds2_anims.py.

Stance layer: SM 0x400 in BT_Move picks 0x442 (TwoWeaponStance +2, stance id = right weapon's WeaponTypeParam +0x1c)
or 0x46a (-2, left weapon's +0x20, the `_R` clip through mirror node 0x87). Each is a two-state SM: phase 'clip' plays
the whole stance animation (0x443 / 0x46b), then phase 'hold' (0x450 / 0x47e, after condition 0x262 on the
ChangeStanceTwoWeaponPrepare track) samples one frame through a SingleFrame node (CP SingleFrame_1, default 1.0 =
taken as the last frame; unconfirmed). Both are FeatherBlend2 (BlendWeight_1) of locomotion 0x500 and the stance.

ALPHAS: per-bone FeatherBlend2 alphas (attrib 0x57) read from c0001.nmb, the switch child for each stance value:
right clip 0x443 -> 0x444 / 0x44c / 0x448, right hold 0x450 -> 0x451 / 0x466 / 0x459, left clip 0x46b -> 0x46c / 0x478
/ 0x473, left hold 0x47e -> 0x47f / 0x494 / 0x491 (214610 / 254610 / 364610). Groups: (Spine, Spine1, Neck = Head,
L_Clavicle, L_UpperArm, L_Forearm, L_Hand = L fingers = L_Weapon, the same five on the right). Unlisted bones are 0.
"""

# stance id -> (DS2 TAE id of the stance clip, ...)
STANCE_CLIPS = {214610: 21450010,   # a21_45_0010_KnifeStance2Weapon_R
                254610: 25450010,   # a25_45_0010_SpearStance2Weapon_R
                364610: 36450010}   # a36_45_0010_LargeAxeStance2Weapon_R

ALPHAS = {
    ('right', 'clip'): {214610: (0.5, 0.5, 0.2, 1, 1, 1, 1, 0.5, 0.5, 0.5, 1),
                        254610: (0.5, 0.5, 0.2, 1, 1, 1, 1, 0.5, 0.5, 0.5, 1),
                        364610: (0.5, 0.5, 0.2, 1, 1, 1, 1, 1, 1, 1, 1)},
    ('right', 'hold'): {214610: (0.2, 0.25, 0.1, 0.9, 0.5, 0.75, 1, 0.1, 0.1, 1, 1),
                        254610: (0.2, 0.25, 0.1, 0.9, 0.5, 0.75, 1, 0.1, 0.1, 1, 1),
                        364610: (0.1, 0.1, 0.1, 1, 0.7, 1, 1, 1, 0.7, 1, 1)},
    ('left', 'clip'): {214610: (0.5, 0.5, 0.2, 0.5, 0.5, 0.5, 1, 1, 1, 1, 1),
                       254610: (0.5, 0.5, 0.2, 0.5, 0.5, 0.5, 1, 1, 1, 1, 1),
                       364610: (0.5, 0.5, 0.7, 1, 1, 1, 1, 1, 1, 1, 1)},
    ('left', 'hold'): {214610: (0.1, 0.1, 0.2, 0.1, 0.1, 0.1, 1, 0.9, 0.5, 0.75, 1),
                       254610: (0.1, 0.1, 0.5, 0.1, 0.1, 0.1, 1, 0.9, 0.5, 0.75, 1),
                       364610: (0.1, 0.1, 0.2, 1, 0.7, 1, 1, 1, 0.7, 1, 1)},
}
# Blend times of the SM transitions (attrib 0x3b +0x08): 0x401 <-> 0x442 / 0x46a and 0x442 <-> 0x46a 0.2 s;
# clip -> hold 0.2 s on the right (0x469), 0.1 s on the left (0x49f).
BLEND_IN = 0.2
HOLD_BLEND = {'right': 0.2, 'left': 0.1}


def bone_alpha(name, group):
    """Alpha of one DS2 rig bone from an ALPHAS tuple."""
    spine, spine1, neck, *arms = group
    if name == 'Spine':
        return spine
    if name == 'Spine1':
        return spine1
    if name in ('Neck', 'Head'):
        return neck
    if name[:2] in ('L_', 'R_'):
        base = 0 if name[0] == 'L' else 4
        part = name[2:]
        if part == 'Clavicle':
            return arms[base]
        if part == 'UpperArm':
            return arms[base + 1]
        if part == 'Forearm':
            return arms[base + 2]
        if part in ('Hand', 'Weapon') or part.startswith('Finger'):
            return arms[base + 3]
    return 0.0


def rig_alphas(rig, side, phase, stance):
    return [float(bone_alpha(n, ALPHAS[(side, phase)][stance])) for n in rig.names]


# Minecraft bone -> the DS2 bone the retarget builds it from (ds2_anims.py; the same mapping GripClient uses for the
# weapon change layer): body = Spine1, head = Head, arm = UpperArm, held item = Weapon. Legs and the body position
# (Pelvis) have alpha 0 in every stance layer.
MC_BONES = {'body': 'Spine1', 'head': 'Head', 'right_arm': 'R_UpperArm', 'left_arm': 'L_UpperArm',
            'right_item': 'R_Weapon', 'left_item': 'L_Weapon', 'right_leg': None, 'left_leg': None}


def mc_weights(side, phase, stance):
    group = ALPHAS[(side, phase)][stance]
    return {mc: float(bone_alpha(ds2, group)) if ds2 else 0.0 for mc, ds2 in MC_BONES.items()}
