"""Builds data/soulsmode/ds2/weapons.json: the DS2 weapons the Minecraft items use, read the way the game does
(docs/research/weapons.md):

WeaponParam[id] +0x0c -> WeaponActionCategoryParam row (64 WeaponAttackMotionParam ids, one per action slot)
-> WeaponAttackMotionParam +0x00 = TAE id of the animation.

Slots exported per hand / grip (slot = base(grip) + offset(action type), FUN_140397514): see HANDS.
Timings of each animation: .nsa +0x28 (duration), TAE event 2200 (hit windows), c0001.nmb tracks
CancelControl_Avoid / Move / Attack1L / 1H / 2L / 2H and AttackComboControl (userData 130 -> chr.b8+0x4d4, the
combo flag FUN_1401f9d50 checks).

Usage: weapons.py <weapons.json>
"""
import ds2paths  # noqa: E402
import glob
import json
import os
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import nmb  # noqa: E402
import params  # noqa: E402
import tae  # noqa: E402

import hitback  # noqa: E402
import nsa  # noqa: E402
import powerstance  # noqa: E402

# FUN_14034ede0: left weapon's power stance category -> byte of the DualWieldingPermissionParam row (100 / 110 are
# crossed in the code).
DUAL_COLUMN = {0: 0x00, 10: 0x01, 20: 0x02, 30: 0x03, 40: 0x04, 50: 0x05, 55: 0x1a, 60: 0x06, 70: 0x07, 80: 0x08,
               85: 0x19, 90: 0x09, 100: 0x0b, 110: 0x0a, 120: 0x0c, 130: 0x0d, 140: 0x0e, 150: 0x0f, 160: 0x10,
               170: 0x11, 180: 0x12, 190: 0x13, 200: 0x14, 210: 0x15, 220: 0x16, 230: 0x17, 240: 0x18}

# Minecraft item -> DS2 weapon (WeaponParam id); chosen by the user.
WEAPONS = {
    'longsword': 1220000,
    # One DS2 weapon per Minecraft material (the user's table, 2026-10-31): requirements, damage, scaling and moves
    # are each weapon's own.
    'broken_straight_sword': 1200000,  # wooden sword
    'puzzling_stone_sword': 1250000,  # stone sword
    'shortsword': 1210000,  # golden sword
    'drakekeepers_sword': 1370000,  # diamond sword
    'red_rust_sword': 1330000,  # netherite sword
    'hand_axe': 2000000,  # wooden axe
    'infantry_axe': 2030000,  # stone axe
    'bandit_axe': 2020000,  # golden axe
    'dragonslayers_crescent_axe': 2080000,  # diamond axe
    'gyrm_axe': 2070000,  # netherite axe
    'battle_axe': 2010000,
    'mace': 2410000,
    'club': 2400000,  # the stick (user's request, 2026-10-30)
    'spear': 2800000,
    'pickaxe': 2610000,
    # Hoes (the user's choice, 2026-11-09): the Silverblack Sickle, WeaponTypeParam 151, axe-like moves
    # (docs/research/scythes.md).
    'silverblack_sickle': 2100000,
    'shield': 11230000,  # Drangleic Shield
    'fists': 3400000,
    # The lit torch: FUN_14034f300 puts WeaponParam 6000000 in hand 0 (the left) as a temporary override.
    'torch': 6000000,
    # Catalysts (docs/research/catalysts.md): no Minecraft item uses them; datapacks bind them (docs/datapacks.md).
    'sorcerers_staff': 3800000,
    'clerics_chime': 4010000,
    'pyromancy_flame': 5400000,
    # Bow (docs/research/bow.md): no Minecraft mapping chosen yet; the bow block below holds its aim camera and arrow.
    'short_bow': 4200000,
    # Crossbow (docs/research/crossbow.md): Minecraft's crossbow item.
    'light_crossbow': 4600000,
}
# Bow motion types (docs/research/bow.md section 1.5, jump table of FUN_140393c00 at 0x14039475c): 8..11 shoot
# (case 0x1403942eb), 15 does nothing (the function's exit 0x140394729), 16 only requests the aim camera
# (case 0x14039450e -> FUN_140390f90). Their WAMP TAE ids are placeholders (40230010 has no c0001 node; 20010010).
BOW_ACTIONS = {8: 'shoot', 9: 'shoot', 10: 'shoot', 11: 'shoot', 15: 'none', 16: 'aim'}
# Bow animations (c0001.nmb SM 0x1132 -> 0x1138 / mirrored 0x1163; AttackID_Number 230010): Prepare, CF (TAE 2300
# arrow), NoBullet, and the stances a40_41_0010 (one hand, AttackStance_R 404110) / a40_43_0010 (two hands,
# AttackStance_LR 404310).
BOW_PREPARE, BOW_CF, BOW_NO_BULLET = 40240010, 40240020, 40215030
BOW_STANCES = {'oneHand': 40410010, 'twoHand': 40430010}
# Light Crossbow animations (docs/research/crossbow.md; c0001.nmb SM 0x150f, AttackID_Number 210010..210019 one hand,
# 230010.. two hands, 250010.. two weapons). One hand has its own right (a42_21) and left (a42_22) clips; two hands and
# two weapons only _R ones, the left side through the mirror node 0x87 (states 0x15e9 / 0x1677). The *_0011 / *_0021
# variants are the Sanctum Repeating Crossbow's (its WAMP rows), not exported.
CROSSBOW_ANIMATIONS = {'oneHandRight': (42210010, 42210020, 42215030), 'oneHandLeft': (42220010, 42220020, 42225030),
                       'twoHand': (42230010, 42230020, 42215030), 'twoWeapon': (42250010, 42250020, 42215030)}
CROSSBOW_STANCES = {'oneHand': 42410010, 'twoHand': 42430010, 'twoWeapon': 42450010}
# Spell cast animations (docs/research/casting.md, sections 1-2): prepare / cast pairs picked by SpellParam, the failed
# cast StickSpellNoBullet_R, and the two-handed / dual-wield variants no SpellParam row references.
CAST_ANIMATIONS = [38110010, 38110020, 38110120, 38110220, 38110320, 38110420, 38110520, 38110610, 38110620,
                   38110720, 38110820, 38110920, 38111110, 38111120, 38111220, 38111320, 38111420, 38111510,
                   38111520, 38111610, 38111620, 38111720, 38115030, 38130010, 38130020, 38150010,
                   39110010, 39110020, 39110110, 39110120, 39110220, 39110320, 39110420, 39110520, 39110620,
                   39110710, 39110720, 39110810, 39110820, 39110910, 39110920, 39130010, 39150010]
# Hand / grip -> (first slot, slot names). One-handed right (grip 2) base 0, two-handed (grip 3) base 10, one-handed left
# (grip 6) base 40; action types 1..4 use offsets 0..3. While the OTHER weapon is two-handed, this hand's buttons give
# action types 0x0b / 0x0c (FUN_140396680), which are slots 50 / 51 of the two-handed weapon (table at 0x1403977b8).
# Movement attacks (docs/research/weapons.md, "Ataques en movimiento"): types 5 dash, 6 rolling, 10 backstep and 9 jump
# use offsets 4, 5, 6 and 9 ([0,1,2,3,4,5,7,8,9,6] for types 1..10).
TYPES = [('light', 0), ('light_combo', 1), ('heavy', 2), ('heavy_combo', 3),
         ('dash', 4), ('rolling', 5), ('backstep', 6), ('fall', 8), ('jump', 9)]
HANDS = {'right': (0, TYPES), 'left': (40, TYPES), 'two_handed': (10, TYPES),
         'two_handed_other': (50, [('light', 0), ('heavy', 1)]),
         # Critical attacks: action kinds 0x16 riposte, 0x17 guard-break stab, 0x18 backstab -> slots 61..63
         # (FUN_1403947b0 looks them up with grip 2, the right hand).
         'critical': (61, [('riposte', 0), ('guard_break', 1), ('backstab', 2)]),
         # Kick, types 0x10 / 0x11 -> slots 55 / 56 (FUN_1403962f0: 0x11 while a weapon is two-handed).
         'kick': (55, [('one_handed', 0), ('two_handed', 1)]),
         # Power stance (states 5 / 6, docs/research/powerstance.md): the left buttons give types 0x0d / 0x0e / 0x0f ->
         # slots 52 / 53 / 54 of the higher-category weapon's row (FUN_140395b40); FUN_140392920 builds a second
         # descriptor for the other hand with ITS OWN row's same slot (FUN_14038f4d0). Hit slots 0..4 are the right
         # weapon's, 5..9 the left weapon's (base 0 / 5 by hand).
         'power_stance': (52, [('light', 0), ('light_combo', 1), ('heavy', 2)]),
         # Attack from guard (docs/research/weapon_arts.md section 2): action types 0x12 / 0x13 / 0x14 / 0x15 -> slots
         # 57 / 58 / 59 / 60 of the ATTACKING weapon's row, from either hand. FUN_140396680 gives 0x12 when
         # FUN_1401f9f00 holds (hand state 1 and the other hand guarding: b8+0x14 == 2 with the right hand's button,
         # == 1 with the left's), and 0x13 when the current action is 0x12 of the same hand inside its
         # AttackComboControl (FUN_1401f9d50(ctx, 0x12)); 0x14 / 0x15 the same on the heavy branch. Motion types 4 / 5
         # let the guard go on during the attack (FUN_14038fd30).
         'guard_attack': (57, [('light', 0), ('light_combo', 1), ('heavy', 2), ('heavy_combo', 3)])}

ANIM_DIR = ds2paths.C0001
TAE_PATH = ds2paths.TAE_PL
_, TAE = tae.load(TAE_PATH)
TAE_DATA = open(TAE_PATH, 'rb').read()
ND = open(os.path.join(ANIM_DIR, 'c0001.nmb'), 'rb').read()
_TABLES = nmb.lookup_tables(ND)
NAMES = _TABLES[0]
# Source names (.xmd) say which side each animation was made for: "..._L" / "..._L_Hit" = left hand.
XMD = {i: x.split(chr(92))[-1][:-4] for i, x in enumerate(_TABLES[2])}
NODE = {}
for _p, _anim, _tracks in nmb.anim_nodes(ND):
    for _name, _ch, _ev in _tracks:
        if _name == 'TimeAct' and _ev:
            NODE.setdefault(_ev[0][2], (_anim, _tracks))
W = params.load('WeaponParam')
A = params.load('WeaponActionCategoryParam')
M = params.load('WeaponAttackMotionParam')
R = params.load('WeaponReinforceParam')
T = params.load('WeaponTypeParam')
PDP = params.load('PlayerDamageParam')
ITEMS = params.load('ItemParam')
WSC = params.load('WeaponStaminaCostParam')
DCP = params.load('DamageCtrlParam')
HIT_BACK_MATRIX = params.load('HitBackMatrixParam')
PSPL = params.load('PhysicalStatsPerLevelStatValuesParam')
LACK = params.load('PlayerLackOfStatsParam')
HIT_BACK = params.load('HitBackParam')
RCP = params.load('ReinforceCostParam')
CAS = params.load('CustomAttrSpecParam')
WSA = params.load('WeaponStatsAffectParam')


def hit_back(pdp_id):
    """Wall bounce of one hit (docs/research/wall_bounce.md). MapHitDamageComponent +0x38 (FUN_1401c95d0) gives result 3
    (bounce) only with PDP +0x40 bit 0x40 (FUN_1401c99a0) and the PDP +0x65 bit it picks clear: 0x08 on a wall (contact
    normal |y| <= sin 45 deg, FUN_140170d60) or a contact > PlayerCommonParam +0x134 above the feet, 0x04 otherwise.
    Then FUN_14030e630 case 3 -> FUN_140140ac0(row 0, DamageCtrlParam(PDP +0x00) +0x0c): level = HitBackMatrixParam row 0
    int[column] (FUN_140140c50; column outside 0..5 -> 3), DmgHitBackDistance / DmgHitBackDuration = HitBackParam
    f[2 level] / f[2 level + 1] (FUN_140140b60 / bd0; level >= 5 -> 1.0). No DamageCtrlParam row = no bounce."""
    r = PDP.get(pdp_id) if pdp_id else None
    if r is None:
        return None
    d = DCP.get(struct.unpack_from('<I', r, 0)[0])
    column = struct.unpack_from('<I', d, 0x0c)[0] if d else None
    level = struct.unpack_from('<6i', HIT_BACK_MATRIX[0])[column] if column is not None and column < 6 else 3
    f = struct.unpack_from('<10f', HIT_BACK[0])
    distance, speed = (f[2 * level], f[2 * level + 1]) if 0 <= level < 5 else (1.0, 1.0)
    return {'map': bool(r[0x40] & 0x40) and d is not None, 'noFloor': bool(r[0x65] & 0x04),
            'noWall': bool(r[0x65] & 0x08), 'distance': round(distance, 4), 'speed': round(speed, 4)}


def hit_volume(pdp_id):
    """Hit shape of one hit (FUN_140316a30, docs/research/enemy_ai.md 8.4): PDP +0x30 dummy source (0 = the
    character's model, c0001; 1 = an equipped part), +0x31 dummy id, +0x33 1 = capsule / 0 = sphere, +0x34 radius,
    +0x38 length (both x 1.0 from the attack slot)."""
    r = PDP.get(pdp_id) if pdp_id else None
    if r is None:
        return None
    return {'dummy': r[0x31], 'radius': round(struct.unpack_from('<f', r, 0x34)[0], 4),
            'length': round(struct.unpack_from('<f', r, 0x38)[0], 4),
            'source': 'body' if r[0x30] == 0 else 'weapon' if r[0x30] == 1 else 'owner', 'capsule': r[0x33] == 1}


def slot_hits(anim):
    """The hit events of an exported animation, [start, end, slot] each (fall: air hits, then landing hits)."""
    if anim is None:
        return []
    if anim.get('fall'):
        return anim['airHits'] + anim['landingHits']
    return anim.get('hits') or []


def per_hit(fn, pdp_ids, hits, both_hands=False):
    """fn of the PDP row of the slot's first hit event, or a list aligned with the hits when they differ.
    both_hands (power stance): hit slots 5..9 are the other hand's descriptor, built from the same slot of its own
    weapon's row, so slot s reads this weapon's PDP s % 5 (FUN_14038f4d0 / FUN_140392b20)."""
    if not hits:
        return None
    values = [fn(pdp_ids[h[2] % 5 if both_hands else h[2]] if 0 <= (h[2] % 5 if both_hands else h[2]) < 3 else 0)
              for h in hits]
    return values[0] if all(v == values[0] for v in values) else values


def hit_back_event(tae_id):
    """Morpheme AttackHitBackControl track (channel 200 -> ChrEventTrackActionDamage FUN_140369890): its userData
    (280..291) goes to chr.b8+0x270 (+6 for a left-hand attack, b8+0x188 == 0), the DmgHitBackEventID that picks the
    bounce animation in SM_HitBack (280 -> ..._0100 Upper_R, 281 -> ..._0110 Upper_L, 286 / 287 mirrored)."""
    if tae_id not in NODE:
        return None
    for name, _, ev in NODE[tae_id][1]:
        if name.strip() == 'AttackHitBackControl' and ev:
            return ev[0][2]
    return None


def reaction_motion(pdp_id):
    """What the victim's damage animation reads of a hit (docs/research/hit_reaction.md s.9; FUN_14013b4d0 /
    FUN_14013b0a0 -> b8+0x708..+0x718 -> CPs in ChrDamageMotionCtrl vt+0x18 FUN_14035e820): [reaction after the remap
    (column 2), thrust (b8+0x710 = PDP s8 +0x32, or DCP +0x00 when it is 0xff; CP DmgSlashOrThrust = 1 when 1),
    DmgDownOrUp mode PDP +0x43 (0 down, 1 up, 2 by the hit height, FUN_140138cc0), DmgDistance base PDP +0x28,
    DCP +0xa4 / +0xa8 near / far range (m) and +0xbc falloff (%): DmgDistance = x (1 + 0.01 falloff t), t = 0..1 between
    near and far]. None for an empty slot."""
    if not pdp_id or pdp_id not in PDP:
        return None
    import spell_export
    r = PDP[pdp_id]
    dcp_id = struct.unpack_from('<I', r, 0x00)[0]
    d = DCP.get(dcp_id)
    if d is None:
        return None
    level = struct.unpack_from('<b', d, 0x0c)[0]
    sub = struct.unpack_from('<b', r, 0x32)[0] if r[0x32] != 0xff else d[0x00]
    return [spell_export.reaction(d, level), 1 if sub == 1 else 0, r[0x43], round(struct.unpack_from('<f', r, 0x28)[0], 4),
            round(struct.unpack_from('<f', d, 0xa4)[0], 4), round(struct.unpack_from('<f', d, 0xa8)[0], 4),
            round(struct.unpack_from('<f', d, 0xbc)[0], 4)]


def stamina_cost(weapon_id, slot, motion_type):
    """FUN_14038fb10: base x WeaponStaminaCostParam[WeaponParam +0x3c][slot]. The base is WeaponParam +0x34 for
    motion types 1..7, +0x38 for 8..12, else 0 (guard = 13). The slot index is the WeaponActionCategoryParam one
    (FUN_14038fa50). The lack-of-stats factor (PlayerLackOfStatsParam row 11) is 0 while requirements are met."""
    if 1 <= motion_type <= 7:
        base, = struct.unpack_from('<f', W[weapon_id], 0x34)
    elif 8 <= motion_type <= 12:
        base, = struct.unpack_from('<f', W[weapon_id], 0x38)
    else:
        return 0.0
    row, = struct.unpack_from('<I', W[weapon_id], 0x3c)
    return round(base * struct.unpack_from('<64f', WSC[row])[slot], 3)


def attack_data(weapon_id):
    """Attack rating data (docs/research/damage.md, FUN_140391fe0 / FUN_1403903b0): WeaponReinforceParam base per
    type at level 0 and at the max level (+0x00.. / +0x24.., lerp by level / +0x48), the split +0xa0.. (x0.01), the
    WeaponStatsAffectParam (WRP +0x4c) scale +0x00 and coefficients per level (+0x08 + level x 0x24, 9 floats),
    requirements WeaponParam +0x18.. (Str, Dex, Int, Fth), WeaponParam +0x80 and WeaponTypeParam +0x60 / +0x54 (right /
    left hand factor)."""
    w = W[weapon_id]
    reinforce, = struct.unpack_from('<I', w, 0x08)
    weapon_type, = struct.unpack_from('<I', w, 0x10)
    r = R[reinforce]
    max_level, = struct.unpack_from('<i', r, 0x48)
    wsa = params.load('WeaponStatsAffectParam')[struct.unpack_from('<I', r, 0x4c)[0]]
    return {'maxLevel': max_level,
            'base0': [round(x, 4) for x in struct.unpack_from('<9f', r, 0x00)],
            'baseMax': [round(x, 4) for x in struct.unpack_from('<9f', r, 0x24)],
            'split': [round(x * 0.01, 4) for x in struct.unpack_from('<10f', r, 0xa0)],
            'scale': round(struct.unpack_from('<f', wsa, 0)[0], 4),
            'coef': [[round(x, 4) for x in struct.unpack_from('<9f', wsa, 8 + level * 0x24)] for level in range(11)],
            'requirements': list(struct.unpack_from('<4H', w, 0x18)),
            'weaponFactor': round(struct.unpack_from('<f', w, 0x80)[0], 4),
            'handFactor': [round(struct.unpack_from('<f', T[weapon_type], off)[0], 4) for off in (0x60, 0x54)],
            'reinforceCost': reinforce_cost(struct.unpack_from('<I', r, 0xf0)[0], max_level),
            # Mundane (infusion 9, docs/research/bleed.md section 8): S[0] = PSPL +0x8c of the lowest stat x WTP u8
            # +0x13a x 0.01 (FUN_1401641c0; applies with the WeaponTypeParam version >= 0x19, which the regulation has).
            'mundaneFactor': round(T[weapon_type][0x13a] * 0.01, 4),
            **infusions(r)}


def reinforce_cost(rcp_id, max_level):
    """ReinforceCostParam (docs/research/titanite.md section 2.2; WRP +0xf0 / ARP +0x70): per target level 1..max,
    [souls, material ItemParam id, quantity] from +0x00 + 4L (i32), +0x28 + 4L (u32), +0x50 + 2L (u16), L = current
    level (FUN_14003c690). Empty when the row is missing."""
    row = RCP.get(rcp_id)
    if row is None:
        return []
    return [[struct.unpack_from('<i', row, 4 * lv)[0], struct.unpack_from('<I', row, 0x28 + 4 * lv)[0],
             struct.unpack_from('<H', row, 0x50 + 2 * lv)[0]] for lv in range(max_level)]


# Infusion i (docs/research/titanite.md section 4): the element it adds to (exe table 0x1410c3e10 byte 0: 1 magic,
# 2 lightning, 3 fire, 4 dark, 5 poison, 6 bleed, 0 physical) and the WRP column holding the amount moved
# (FUN_14034fe10). Its WeaponStatsAffectParam row is WRP +0x4c + i (byte 1 of the table).
INFUSION_ELEMENT = [0, 3, 1, 2, 4, 5, 6, 0, 0, 0]
INFUSION_AMOUNT = [None, 0xcc, 0xc4, 0xc8, 0xd0, 0xd4, 0xd8, 0xdc, 0xe0, 0xe4]


def infusions(r):
    """The weapon's infusions: CustomAttrSpecParam (WRP +0xe8) u32 +0x00 = mask, bit i = infusion i allowed
    (FUN_1401a9290), 0 without a row; per infusion its WSA row's scale +0x00 and coefficients per level, the element and
    the amount moved (FUN_14034c5c0 / FUN_14034fed0). null for an infusion whose WSA row does not exist."""
    cas = CAS.get(struct.unpack_from('<I', r, 0xe8)[0])
    wsa_base = struct.unpack_from('<I', r, 0x4c)[0]
    out = []
    for i in range(10):
        row = WSA.get(wsa_base + i)
        if row is None:
            out.append(None)
            continue
        out.append({'element': INFUSION_ELEMENT[i],
                    'amount': round(struct.unpack_from('<f', r, INFUSION_AMOUNT[i])[0], 4) if INFUSION_AMOUNT[i] else 0.0,
                    'scale': round(struct.unpack_from('<f', row, 0)[0], 4),
                    'coef': [[round(x, 4) for x in struct.unpack_from('<9f', row, 8 + level * 0x24)] for level in range(11)]})
    return {'infusionMask': struct.unpack_from('<I', cas, 0)[0] if cas else 0, 'infusions': out}


def critical_ranges(row):
    """CommonParam +0x00 class (1..3 can be critically hit) and the backstab / riposte / guard-break limits:
    +0x100.. (facing angle, slope angle, max |dy|, max horizontal distance) x3 (FUN_140390a50 / 1403908a0 /
    140390700)."""
    f = struct.unpack_from('<12f', row, 0x100)
    group = lambda k: {'facing': round(f[k], 4), 'slope': round(f[k + 1], 4), 'height': round(f[k + 2], 4),
                       'distance': round(f[k + 3], 4)}
    return {'class': struct.unpack_from('<i', row, 0)[0], 'backstab': group(0), 'riposte': group(4),
            'guard_break': group(8)}


def hit_events(tae_id, typ):
    """TAE hit events of one type, each [start, end, slot]: param[0] is the attack slot (0..2) whose hit the event
    deals (2200: FUN_140326e80 looks it up on chr+0x230 with FUN_1403741c0 and starts it only if its PlayerDamageParam
    id is not 0; 2201: FUN_14015c2d0 reads it from b8+0x290)."""
    return sorted([round(t0, 4), round(t1, 4), struct.unpack_from('<i', TAE_DATA, po)[0]]
                  for t, t0, t1, po in TAE.get(tae_id, []) if t == typ)


def regen_block(tae_id):
    """TAE 101100 (0x18aec, ChrMorphemeTimeActTrackStaminaCtrl FUN_1403287b0): chr.b8+0x558 += 1 at the start and
    -= 1 at the end, so stamina does not regenerate inside these windows (FUN_140333900)."""
    return sorted([round(t0, 4), round(t1, 4)] for typ, t0, t1, _ in TAE.get(tae_id, []) if typ == 101100)


def window(tracks, track, duration):
    for name, _, ev in tracks:
        if name.strip() == track and ev:
            s, l, _ = ev[0]
            return [round(s * duration, 4), round((s + l) * duration, 4)]
    return None


# Fixed victim animations of the riposte and the guard-break stab (SM 0x1eb0, docs/research/weapons.md "Riposte").
CRITICAL_VICTIM = {'riposte': 79030010, 'guard_break': 79040010}


def event_window(tracks, track, user, duration):
    """[start, end] in seconds of the first event with this userData on a track, or None."""
    for name, _, ev in tracks:
        if name.strip() == track:
            for s, l, u in ev:
                if u == user:
                    return [round(s * duration, 4), round((s + l) * duration, 4)]
    return None


def swing_window(tracks, duration):
    """AttackSpeedControl userData 151 (FUN_140368e70 case 0x97 -> chr.c0+0xa4): while inside it, FUN_14035d580 writes
    CP Attack_SwingSpeed = b8+0x1fc, which the attack's 0x7d play-speed nodes apply to the whole clip
    (docs/research/lack_of_stats.md section 1). The 150 window (b8+0x200 x b8+0x1f8) is not exported: its multiplier
    b8+0x200 has no confirmed writer."""
    return event_window(tracks, 'AttackSpeedControl', 151, duration)


def animation(tae_id, motion_type, critical=False, kind=None):
    """Timing data of one TAE animation, or None with the reason. Guards (WeaponAttackMotionParam type 13) are held
    stances without cancel tracks: only their length is exported."""
    if tae_id not in NODE:
        return None, 'no c0001.nmb node'
    anim, tracks = NODE[tae_id]
    files = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))
    if not files:
        return None, 'no .nsa'
    duration = struct.unpack_from('<f', open(files[0], 'rb').read(), 0x28)[0]
    tokens = XMD[anim].split('_')
    left = 'L' in tokens[3:]
    if motion_type == 13:
        return {'nsa': os.path.basename(files[0]), 'xmd': XMD[anim], 'leftAuthored': left,
                'duration': round(duration, 4), 'guard': True}, None
    w = {k: window(tracks, k, duration) for k in (
        'CancelControl_Avoid', 'CancelControl_Move', 'CancelControl_Attack1L', 'CancelControl_Attack1H',
        'CancelControl_Attack2L', 'CancelControl_Attack2H', 'AttackComboControl')}
    missing = [k for k, v in w.items() if v is None]
    if missing and critical:
        # Critical attacks (backstab...) have no CancelControl_Attack1L..2H / combo tracks, but CancelControl_Avoid /
        # Move / AttackAll end before the animation does (docs/research/weapons.md, "Lado de la víctima"). Their damage
        # comes from TAE 2201 (0x899, FUN_1403269e0 flags chr.b8+0x4c0 bit 38 with the damage id) instead of 2200, and
        # TAE 110100 (chr.b8+0x608, FUN_140139b60 rejects hits) makes the attacker invulnerable. AttackMasterBind
        # (userData 170 -> b8+0x28d) holds the grab.
        grab = hit_events(tae_id, 2201)
        inv = sorted([round(t0, 4), round(t1, 4)] for typ, t0, t1, _ in TAE.get(tae_id, []) if typ == 110100)
        free = window(tracks, 'CancelControl_AttackAll', duration)
        bind = window(tracks, 'AttackMasterBind', duration)
        return {'nsa': os.path.basename(files[0]), 'xmd': XMD[anim], 'leftAuthored': left,
                'duration': round(duration, 4), 'hits': grab, 'invulnerable': inv, 'committed': True,
                'avoidLock': window(tracks, 'CancelControl_Avoid', duration),
                'moveLock': window(tracks, 'CancelControl_Move', duration)[1],
                'attackLock': free[1] if free else round(duration, 4),
                'grabRelease': bind[1] if bind else None,
                'swingSpeedWindow': swing_window(tracks, duration),
                'victim': catch_victim(CRITICAL_VICTIM[kind]) if kind in CRITICAL_VICTIM else backstab_victim(tae_id),
                'regenBlock': regen_block(tae_id)}, None
    if missing:
        return None, 'missing tracks %s' % missing
    return {
        'nsa': os.path.basename(files[0]), 'xmd': XMD[anim], 'leftAuthored': left,
        'duration': round(duration, 4),
        'hits': hit_events(tae_id, 2200),
        'avoidLock': w['CancelControl_Avoid'], 'moveLock': w['CancelControl_Move'][1],
        'lightLock': w['CancelControl_Attack1L'][1], 'heavyLock': w['CancelControl_Attack1H'][1],
        'leftLightLock': w['CancelControl_Attack2L'][1], 'leftHeavyLock': w['CancelControl_Attack2H'][1],
        'combo': w['AttackComboControl'],
        # TAE 1220 (FUN_14016cd70): the parry flag b8+0x550, raised once the event's elapsed fraction reaches
        # 1 - WeaponParam +0x74 of the weapon in that hand (parryRate).
        'parry': sorted([round(t0, 4), round(t1, 4)] for typ, t0, t1, _ in TAE.get(tae_id, []) if typ == 1220),
        # TAE 111600 (DamageActionCtrl, c0+0xf4 |= 0x20): counter window; a hit taken inside is multiplied by the
        # attacker's PDP +0x20 x its WeaponTypeParam +0x6c (FUN_140138d50; DarkTwo espadones.md section 11).
        'counter': sorted([round(t0, 4), round(t1, 4)] for typ, t0, t1, _ in TAE.get(tae_id, []) if typ == 111600),
        # TAE 2300 (FUN_140325880): fires the bullet slot param[0] of chr+0x230 +0x760 (espadones.md section 10).
        'shots': sorted([round(t0, 4), struct.unpack_from('<i', TAE_DATA, po)[0]]
                        for typ, t0, t1, po in TAE.get(tae_id, []) if typ == 2300),
        'regenBlock': regen_block(tae_id),
        # Morpheme AttackDuration track (userData 120, FUN_140368e70 drives chr.c0+0x54): a dodge started inside it
        # costs ChrCommonParam+0x3c (1.25) times more (FUN_140376770).
        'attackDuration': window(tracks, 'AttackDuration', duration),
        'swingSpeedWindow': swing_window(tracks, duration),
    }, None


def cast_animation(tae_id):
    """A spell's prepare or cast animation (docs/research/casting.md, section 2): length, TAE 2300 bullet events
    {time, slot} (FUN_140325880, slot of the chr+0x230 +0x760 table), TAE 2301 direct bullets {time, bullet, damage},
    TAE 101100 regen block and the CancelControl_* windows. A missing track is null: the action is not locked."""
    anim, tracks = NODE[tae_id]
    path = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))[0]
    duration = struct.unpack_from('<f', open(path, 'rb').read(), 0x28)[0]
    w = {k: window(tracks, k, duration) for k in (
        'CancelControl_Avoid', 'CancelControl_Move', 'CancelControl_Attack1L', 'CancelControl_Attack1H',
        'CancelControl_Attack2L', 'CancelControl_Attack2H', 'CancelControl_Guard', 'CancelControl_AttackAll')}
    end = lambda k: w[k][1] if w[k] else None
    return {'nsa': os.path.basename(path), 'xmd': XMD[anim], 'leftAuthored': 'L' in XMD[anim].split('_')[3:],
            'duration': round(duration, 4),
            'bullets': [[h[0], h[2]] for h in hit_events(tae_id, 2300)],
            'directBullets': [[round(t0, 4)] + list(struct.unpack_from('<2i', TAE_DATA, po))
                              for t, t0, t1, po in TAE.get(tae_id, []) if t == 2301],
            'regenBlock': regen_block(tae_id),
            'avoidLock': w['CancelControl_Avoid'], 'moveLock': end('CancelControl_Move'),
            'lightLock': end('CancelControl_Attack1L'), 'heavyLock': end('CancelControl_Attack1H'),
            'leftLightLock': end('CancelControl_Attack2L'), 'leftHeavyLock': end('CancelControl_Attack2H'),
            'guardLock': end('CancelControl_Guard'), 'attackLock': end('CancelControl_AttackAll'),
            # ShootStateControl (userData 107): entering it charges the cast's stamina (FUN_140368e70 case 0x6b ->
            # FUN_1403335d0(b8+0x1ac), docs/research/casting.md section 6).
            'shootState': window(tracks, 'ShootStateControl', duration)}


def fall_animation(loop_id):
    """Plunging attack (type 8, docs/research/weapons.md "Tipo 8"): SM 0x6c7 plays aGG_01_0260 from 0 until its
    StateControl1 event (state 0, in the air), loops aGG_01_0250 while airborne (state 1), and on landing plays 0260 from
    its Footsteps sync event 2 to the end (state 2). Hits: the loop's 2200 covers the whole descent; 0260's 2200 events
    are split into the start (air) and landing parts. Landing locks / regen block are made relative to the landing."""
    land_id = loop_id + 10
    if loop_id not in NODE or land_id not in NODE:
        return None, 'no c0001.nmb node for the fall animations'
    anim, tracks = NODE[land_id]
    path = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))[0]
    duration = struct.unpack_from('<f', open(path, 'rb').read(), 0x28)[0]
    loop_anim = NODE[loop_id][0]
    loop_path = glob.glob(os.path.join(ANIM_DIR, NAMES[loop_anim].split('.')[0] + '.nsa'))[0]
    loop_duration = struct.unpack_from('<f', open(loop_path, 'rb').read(), 0x28)[0]
    start_end = window(tracks, 'StateControl1', duration)[0]
    foot = next(ev for name, _, ev in tracks if name.strip() == 'Footsteps')
    landing = round(foot[2][0] * duration, 4)
    hits = hit_events(land_id, 2200)

    def rel(w):
        return None if w is None else [round(max(0.0, w[0] - landing), 4), round(max(0.0, w[1] - landing), 4)]
    return {
        'fall': True, 'leftAuthored': 'L' in XMD[anim].split('_')[3:],
        'loopTaeId': loop_id, 'landTaeId': land_id, 'loopDuration': round(loop_duration, 4),
        'startEnd': start_end, 'landingAt': landing, 'landingDuration': round(duration - landing, 4),
        'airHits': [h for h in hit_events(loop_id, 2200)] + [h for h in hits if h[0] < start_end],
        'landingHits': [[round(h[0] - landing, 4), round(h[1] - landing, 4), h[2]] for h in hits if h[0] >= landing],
        'avoidLock': rel(window(tracks, 'CancelControl_Avoid', duration)),
        'moveLock': rel(window(tracks, 'CancelControl_Move', duration))[1],
        'lightLock': rel(window(tracks, 'CancelControl_Attack1L', duration))[1],
        'heavyLock': rel(window(tracks, 'CancelControl_Attack1H', duration))[1],
        'leftLightLock': rel(window(tracks, 'CancelControl_Attack2L', duration))[1],
        'leftHeavyLock': rel(window(tracks, 'CancelControl_Attack2H', duration))[1],
        'regenBlock': [rel(w) for w in regen_block(land_id)],
    }, None


# c0001.nmb switch 0x1ebb on CatchDmgHitCategory (the attacker animation's aXX prefix): victim animation per
# threshold (docs/research/weapons.md, "Lado de la víctima").
CATCH_BACKSTAB = [(21, 79020020), (25, 79020050), (29, 79020010), (32, 79020030), (36, 79020040)]
GET_UP = 160730  # a00_16_0730 DmgDownFaceDownEnd


def nsa_path(anim):
    return glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))[0]


def anim_duration(tae_id):
    anim, _ = NODE[tae_id]
    path = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))[0]
    return struct.unpack_from('<f', open(path, 'rb').read(), 0x28)[0]


def input_control():
    """The input buffer's recording windows of every player animation (docs/research/attack.md "Búfer de entrada"):
    nmb track InputControl (channel 80, userData 37 -> b8+0x4d8: every command) and InputControl_Attack (userData 42 ->
    b8+0x4dc: attacks only), read by FUN_140368900. {taeId: {'all': [[s, e]...], 'attack': [[s, e]...]}}."""
    out = {}
    for tae_id, (anim, tracks) in NODE.items():
        files = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))
        if not files:
            continue
        duration = struct.unpack_from('<f', open(files[0], 'rb').read(), 0x28)[0]
        spans = {'all': [], 'attack': []}
        for name, _, ev in tracks:
            key = {'InputControl': 'all', 'InputControl_Attack': 'attack'}.get(name.strip())
            if key:
                spans[key] += [[round(s * duration, 4), round((s + l) * duration, 4)] for s, l, _ in ev]
        if spans['all'] or spans['attack']:
            out[str(tae_id)] = {k: sorted(v) for k, v in spans.items()}
    return out


def backstab_victim(attacker_tae):
    """The victim side of a backstab: the a79_02 animation picked by the attacker's prefix (switch 0x1ebb)."""
    prefix = attacker_tae // 1000000
    return catch_victim(next((v for t, v in CATCH_BACKSTAB if prefix <= t), CATCH_BACKSTAB[-1][1]))


def catch_victim(victim):
    """The victim side of a critical (player victim, c0001): its a79 animation (CatchDmgCategory 10 backstab, 20
    riposte a79_03_0010, 30 guard break a79_04_0010; SM 0x1eb0), left at 0.9 of its length (condition 0x25b), then the
    get-up 0730 (the down loop lasts a frame once the grab is released, FUN_14032e000). Times from the start, assuming
    both animations start together."""
    leave = 0.9 * anim_duration(victim)
    _, tracks = NODE[GET_UP]
    d = anim_duration(GET_UP)
    return {'taeId': victim, 'getUpAt': round(leave, 4),
            'moveAt': round(leave + window(tracks, 'CancelControl_Move', d)[1], 4),
            'attackAt': round(leave + window(tracks, 'CancelControl_AttackAll', d)[1], 4),
            # b8+0x618 stays set 3.0 s after getting up: no new critical on it (141b9e50a).
            'noCriticalUntil': round(leave + d + 3.0, 4),
            # TAE 110100 (b8+0x608, FUN_140139b60 rejects hits): the catch's windows up to its exit, then the get-up's.
            'invulnerable': [[round(t0, 4), round(min(t1, leave), 4)]
                             for typ, t0, t1, _ in TAE.get(victim, []) if typ == 110100 and t0 < leave]
                            + [[round(leave + t0, 4), round(leave + t1, 4)]
                               for typ, t0, t1, _ in TAE.get(GET_UP, []) if typ == 110100]}


def item_use(tae_id):
    """An item-use animation (a00_18_*): length, the CancelControl_Avoid window and the end of CancelControl_AttackAll
    / CancelControl_Guard (no CancelControl_Move track: the item is used while moving), and its TAE events."""
    anim, tracks = NODE[tae_id]
    path = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))[0]
    duration = struct.unpack_from('<f', open(path, 'rb').read(), 0x28)[0]
    return {'nsa': os.path.basename(path), 'xmd': XMD[anim], 'leftAuthored': 'L' in XMD[anim].split('_')[3:],
            'duration': round(duration, 4), 'avoidLock': window(tracks, 'CancelControl_Avoid', duration),
            'attackLock': window(tracks, 'CancelControl_AttackAll', duration)[1],
            'guardLock': window(tracks, 'CancelControl_Guard', duration)[1],
            'events': {str(typ): round(t0, 4) for typ, t0, _, _ in TAE.get(tae_id, [])}}


def thrown_use(tae_id):
    """item_use() plus the end of CancelControl_Move: the throws (a00_18_0100 / 0110) do have that track, unlike the
    other item uses (docs/research/thrown_items.md)."""
    out = item_use(tae_id)
    _, tracks = NODE[tae_id]
    out['moveLock'] = window(tracks, 'CancelControl_Move', out['duration'])[1]
    return out


def bow_block(weapons, weapon='short_bow', arrow_id=60760000):
    """Aim camera, arrow, lock-on and stamina of the bow (docs/research/bow.md; 'confidence' / 'unconfirmed' fields
    follow its "Exportado" section)."""
    import math
    import spell_export
    f = lambda r, o: round(struct.unpack_from('<f', r, o)[0], 4)
    bow = weapons[weapon]
    weapon_type = bow['weaponType']
    row = struct.unpack_from('<I', T[weapon_type], 0x134)[0]
    cam = params.load('CameraSubjectiveParam')[row]
    half_pi = math.pi / 2
    aim = {
        # SubjectiveCameraOperator (vtable 0x1410f4c98), row = WeaponTypeParam +0x134 (b8+0x98, FUN_140390f90).
        'cameraRow': row,
        'fov': f(cam, 0x04),  # radians (FUN_1404a3450; min 1 deg)
        'near': 0.1, 'far': 5000.0,
        # Pivot mode +0x01 (FUN_1404a3bf0): 0 = the character's vt+0x148 point (which point: unconfirmed).
        'pivotMode': cam[0x01],
        # Eye = pivot + side +0x14 / up +0x18 / forward +0x1c, turned by the yaw (FUN_1404a3ea0).
        'eye': {'side': f(cam, 0x14), 'up': f(cam, 0x18), 'forward': f(cam, 0x1c)},
        # Looked-at point = pivot + R(yaw) Rx(pitch) (side +0x08, up +0x0c, forward +0x10) (FUN_1404a3cf0).
        'look': {'side': f(cam, 0x08), 'up': f(cam, 0x0c), 'forward': f(cam, 0x10)},
        # Pitch clamp [+0x50 - pi/2, pi/2 - +0x54] (FUN_1404a4540). Sign: positive = looking down, from the entry
        # conversion pitch = -atan2(forward.y, |forward.xz|) (FUN_1404a3710); not checked in game.
        'pitchMin': round(f(cam, 0x50) - half_pi, 4), 'pitchMax': round(half_pi - f(cam, 0x54), 4),
        'pitchPositiveIsDown': True,
        # Stick turn speed (rad/s) = lerp(a, b, camera speed option / 100) (FUN_1404a4050), and the ramp fields.
        'yawSpeed': [f(cam, 0x20), f(cam, 0x24)], 'pitchSpeed': [f(cam, 0x38), f(cam, 0x3c)],
        'yawBrake': [f(cam, 0x30), f(cam, 0x34)], 'pitchBrake': [f(cam, 0x48), f(cam, 0x4c)],
        'yawAccel': [f(cam, 0x28), f(cam, 0x2c)], 'pitchAccel': [f(cam, 0x40), f(cam, 0x44)],
        # PC mouse (FUN_1404a4050 / FUN_1404a4540): delta ignored at |d| <= deadZone, x radPerUnit, smoothed
        # v += (target - v) x smoothing per frame, added to yaw / pitch x (sensitivityBase + sensitivityPerStep x
        # option 0..100) x scale; not multiplied by dt.
        # Mouse (bow.md s.2.2, FUN_1404a4050 / FUN_1404a4540): the frame's raw DirectInput relative counts (relative axis
        # mode, GetDeviceState in FUN_140f074a0, no scaling on the way); any count -> target = counts x radPerUnit, else 0;
        # v += (target - v) x smoothing per axis each game frame (60 fps limiter, FUN_140b4a380); yaw / pitch +=
        # v x (sensitivityBase + sensitivityPerStep x sens) x scale rad, no dt. sens = options byte +0x14, default 50
        # (options constructor FUN_14019cb30).
        'mouse': {'deadZone': 0.1, 'radPerUnit': 0.017453, 'smoothing': 0.5, 'sensitivityBase': 0.1,
                  'sensitivityPerStep': 0.019, 'scale': 0.24, 'defaultSensitivity': 50, 'frameRate': 60},
        # FUN_1404a33e0 (vt[7]): bytes +0x58 / +0x59 and floats +0x5c / +0x60; probably the blend in / out (consumer not
        # traced).
        'blend': {'types': [cam[0x58], cam[0x59]], 'times': [f(cam, 0x5c), f(cam, 0x60)], 'confirmed': False},
        # Lasts while the aim request repeats each frame (held button; FUN_140393a30 exits on the first frame without
        # one). Entering keeps the camera's direction (FUN_1404a3710); leaving turns the body to the camera's forward and
        # hands its yaw to the follow camera (FUN_14030eb60).
        'holdToAim': True,
        # While aiming the arrow follows a ray from the camera of this length against the collision (FUN_14043fa80).
        'rayLength': 100.0,
    }
    ap = params.load('ArrowParam')[arrow_id]
    # FUN_140392ce0: 16-byte group by grip (2 -> +0x04, 3 -> +0x14); FUN_140391bf0(.., bullet, damage = group + 8).
    group = 0x14
    bullet_id, damage_id = struct.unpack_from('<I', ap, group)[0], struct.unpack_from('<I', ap, group + 8)[0]
    bullets = {}
    spell_export.bullet(bullet_id, damage_id, bullets)
    pdp = PDP[damage_id]
    pcp = params.load('PlayerCommonParam')[0]
    ccp = params.load('ChrCommonParam')[100]
    root = bullets[str(bullet_id)]
    return {
        'weapon': weapon,
        'aimCamera': aim,
        'arrow': {
            'arrowId': arrow_id,
            # Same shape as the spell bullets (tools/re/spell_export.py, ds2/Bullets.java): root id + map.
            'bullet': str(bullet_id), 'bullets': bullets,
            'bulletConfidence': 'the only BulletParam row of the group; the bullet / damage order of FUN_140391bf0 is '
                                'not confirmed',
            'damageRow': {'id': damage_id, 'rate': f(pdp, 0x1c), 'floor': pdp[0x18],
                          'staminaDamage': struct.unpack_from('<H', pdp, 0x24)[0], 'confidence': 'data'},
            # Bow request multipliers (casting.md s.7.1): speed b8+0x43c, gravity delay +0x440, life +0x438. The
            # constructor FUN_14031bcf0 fills the array b8+0x3bc.. with 1.0 (casting.md s.8); what changes them later
            # is not traced.
            'multipliers': {'speed': 1.0, 'gravityDelay': 1.0, 'life': 1.0, 'confidence': 'default from the ctor'},
            # ArrowParam: +0x56 ammo kind (1 arrow, 2 greatarrow, 4 bolt) read by FUN_14034b7f0; the per-element u16
            # +0x46.. are a data pattern (reader not located).
            'arrowParam': {'ammoKind': ap[0x56], 'ammoKindConfidence': 'code (FUN_14034b7f0)',
                           'physical': struct.unpack_from('<H', ap, 0x46)[0],
                           'elements': {k: struct.unpack_from('<H', ap, o)[0] for k, o in (
                               ('magic', 0x48), ('lightning', 0x4a), ('fire', 0x4c), ('dark', 0x4e),
                               ('poison', 0x50), ('bleed', 0x52), ('x54', 0x54))},
                           'valuesConfidence': 'data pattern, reader not located; how they add to the bow AR is not '
                                               'confirmed'},
            # Origin: PDP +0x30 = 1 / +0x31 = 200 -> the bow model's dummy 200 (see dummyPolys of short_bow).
            'originDummy': pdp[0x31],
            # Light button (codes 1 / 3) -> ammo slot 0 (Arrow1), heavy (2 / 4) -> slot 1 (Arrow2) (FUN_1403486e0 ->
            # FUN_14034b7f0). When the arrow is taken from the inventory: unconfirmed.
            'ammoSlotByButton': {'light': 0, 'heavy': 1},
        },
        'lockOn': {
            # With a locked target (casting.md s.7.2 step 5): turn towards it at most +-aimYaw horizontally, aimPitch
            # vertically; no lead. Homing only with a target: turn rate, cut-off angle, stop distance; distance 0.
            'maxYaw': root['aimYaw'], 'maxPitch': root['aimPitch'],
            'leadHeight': root['leadHeight'], 'leadHorizontal': root['leadHorizontal'],
            'homingTurn': root['homingTurn'], 'homingAngle': root['homingAngle'],
            'homingStopNear': root['homingStopNear'], 'homingDistance': root['homingDistance'],
            'homeWithoutLock': root['homeWithoutLock'],
        },
        'hipFire': {
            # Without the aim camera (casting.md s.7.7): the head look direction; during the shot c0+0x54 is active, so
            # it takes the camera forward's Y, zeroed when -sin(down) <= y <= sin(up) (PlayerCommonParam row 0
            # +0xc4 / +0xc0), within ChrCommonParam 100 +0x1e0.. (horizontal, vertical, speeds) x WeaponTypeParam
            # +0xac.. (which factor is which axis: unconfirmed). The look is smoothed (formula not broken down).
            'pitchDeadZoneUp': f(pcp, 0xc0), 'pitchDeadZoneDown': f(pcp, 0xc4),
            'lookLimits': {'horizontal': [f(ccp, 0x1e0), f(ccp, 0x1e4)], 'vertical': [f(ccp, 0x1ec), f(ccp, 0x1e8)],
                           'speeds': [f(ccp, 0x1f8), f(ccp, 0x1fc)]},
            'weaponFactors': [f(T[weapon_type], o) for o in (0xac, 0xb0, 0xb4, 0xb8)],
            'weaponFactorsConfirmed': False,
        },
        # FUN_14038fb10: WeaponParam +0x38 x WSC[slot 10] (R1 and R2 both give action type 1), charged on entering
        # ShootStateControl of the Prepare (casting.md s.6.1).
        'staminaCost': bow['two_handed']['light']['staminaCost'],
        'animations': {
            'prepare': BOW_PREPARE, 'cf': BOW_CF, 'noBullet': BOW_NO_BULLET,
            # The clips are authored with the bow in the left hand (_L); the right hand plays them through the mirror
            # node 0x87 (SM 0x1163): ds2_<tae>_mirror. Which SM entry is which hand: unconfirmed.
            'mirrorSuffix': '_mirror',
            # SM 0x1138: Prepare -> hold (last frame, ds2_<prepare>_hold) when AttackDuration crosses 0.2 (cond 0x262)
            # -> CF when IsMoveAttackHold is set (cond 0x270, value 1; set by a held fire button). Both semantics
            # unconfirmed.
            'holdSuffix': '_hold', 'sequenceConfirmed': False,
            'stances': {k: 'soulsmode:ds2_%d' % v for k, v in BOW_STANCES.items()},
        },
    }


def crossbow_block(weapons, clips):
    """The bow block for the Light Crossbow with its Wood Bolt (docs/research/crossbow.md), plus the per-grip bolt
    bullets, the per-grip animations and the reload data. Same field meanings as bow_block."""
    import spell_export
    out = bow_block(weapons, 'light_crossbow', 60910000)
    ap = params.load('ArrowParam')[60910000]
    # FUN_140392ce0: ArrowParam group by grip desc+0x04: 2 and 6 (one hand) -> +0x04, 3 (two hands) -> +0x14,
    # 4 -> +0x24, 5 -> +0x34 (branches 0x140392d2b / 0x140392de4 / 0x140392ddb / 0x140392dd2); bullet = group +0,
    # damage = group +8. The bow never fires one-handed, so only its +0x14 group matters; the crossbow does.
    groups = {'2': 0x04, '3': 0x14, '4': 0x24, '5': 0x34, '6': 0x04}
    bullets = {}
    by_grip = {}
    for grip, off in groups.items():
        b, d = struct.unpack_from('<I', ap, off)[0], struct.unpack_from('<I', ap, off + 8)[0]
        spell_export.bullet(b, d, bullets)
        by_grip[grip] = {'bullet': str(b), 'damage': d}
    arrow = out['arrow']
    arrow['bullets'] = bullets
    arrow['bulletByGrip'] = by_grip
    # Bullet and damage rows of grip 2 (one hand), the crossbow's main use; the two-handed ones are in bulletByGrip.
    arrow['bullet'] = by_grip['2']['bullet']
    pdp = PDP[by_grip['2']['damage']]
    arrow['damageRow'] = {'id': by_grip['2']['damage'], 'rate': round(struct.unpack_from('<f', pdp, 0x1c)[0], 4),
                          'floor': pdp[0x18], 'staminaDamage': struct.unpack_from('<H', pdp, 0x24)[0],
                          # PDP +0x0c: flat damage added by the bolt (element +0x19); the arrows have 0.
                          'flat': struct.unpack_from('<i', pdp, 0x0c)[0], 'confidence': 'data'}
    # Types 10-11 take their request multipliers from b8+0x450 (speed) / +0x454 (gravity delay) / +0x44c (life),
    # filled with 1.0 by the constructor FUN_14031bcf0 (casting.md s.7.1, s.8).
    arrow['multipliers']['fields'] = ['b8+0x450', 'b8+0x454', 'b8+0x44c']
    root = bullets[arrow['bullet']]
    out['lockOn'] = {'maxYaw': root['aimYaw'], 'maxPitch': root['aimPitch'], 'leadHeight': root['leadHeight'],
                     'leadHorizontal': root['leadHorizontal'], 'homingTurn': root['homingTurn'],
                     'homingAngle': root['homingAngle'], 'homingStopNear': root['homingStopNear'],
                     'homingDistance': root['homingDistance'], 'homeWithoutLock': root['homeWithoutLock']}
    w = weapons['light_crossbow']
    # Crossbow slots: one hand (right 0.., left 40..) and two hands (10..) shoot (motion type 10); the other hand's
    # buttons with it two-handed aim (16); power stance 52..54 shoot (type 11).
    out['staminaCost'] = {'oneHandRight': w['right']['light']['staminaCost'],
                          'oneHandLeft': w['left']['light']['staminaCost'],
                          'twoHand': w['two_handed']['light']['staminaCost'],
                          # The Prepare and the CF both carry ShootStateControl (u107); FUN_140368e70 case 0x6b charges
                          # b8+0x1ac on entering it and does not clear it: maybe charged twice (unconfirmed).
                          'maybeChargedTwice': True}
    anims = {'mirrorSuffix': '_mirror', 'holdSuffix': '_hold',
             # The hold state (0x1548 etc.) samples the Prepare through a SingleFrame node on CP SingleFrame_095
             # (0x17): fraction 0.95 by the CP's name (the value is not read). The bow's uses SingleFrame_1.
             'holdFraction': 0.95, 'sequenceConfirmed': False,
             'stances': {k: 'soulsmode:ds2_%d' % v for k, v in CROSSBOW_STANCES.items()}}
    for grip, (prep, cf, nob) in CROSSBOW_ANIMATIONS.items():
        c = clips[str(cf)]
        anims[grip] = {'prepare': prep, 'cf': cf, 'noBullet': nob,
                       # Left side of two hands / two weapons = the _R clips through the mirror node 0x87.
                       'leftMirrored': grip in ('twoHand', 'twoWeapon')}
    out['animations'] = anims
    # Reload: no reload animation or state of its own in c0001.nmb; the CF goes on after the bolt (TAE 2300) with the
    # weapon locked. Per grip: bolt time, CF length, locks (end) and the OverwriteControl track (u171) of the one-hand
    # CF. That this part is the reload is a reading of the animation, not confirmed.
    out['reload'] = {grip: {'boltAt': clips[str(cf)]['bullets'][0][0] if clips[str(cf)]['bullets'] else None,
                            'cfDuration': clips[str(cf)]['duration'], 'attackLock': clips[str(cf)]['lightLock'],
                            'avoidLock': clips[str(cf)]['avoidLock'], 'guardLock': clips[str(cf)]['guardLock'],
                            'regenBlock': clips[str(cf)]['regenBlock']}
                     for grip, (prep, cf, nob) in CROSSBOW_ANIMATIONS.items()}
    out['reload']['separateAnimation'] = False
    out['reload']['confirmed'] = False
    return out


# Guard layer (docs/research/weapon_arts.md section 8). c0001.nmb: the top SM 0x210e has state 0x210f (no guard: the whole
# body network 0x233e, attacks included) and state 0x2110 SM_MoveGuard, entered when CP GuardStance (0x3f) is +1 / -1
# (written by the CP updater at 0x14035d82c from b8+0x14: 1 -> +1.0, 2 -> -1.0, else 0). In SM_MoveGuard, GuardStance -1
# with GuardID_Number 310010 / 320010 goes to BT_Guard1Hand_L 0x216c, +1 to BT_Guard1Hand_R 0x21d5. Each holds an SM
# (0x211f / 0x2180) whose states FeatherBlend2 (BlendWeight_1 = 1.0) the body network 0x233e with the guard clip:
# 0x2120 / 0x2181 play the clip (switch on GuardID_Category = guard TAE / 1e6 % 100, attrib 0x09 values 20 / 44 / 45 / 46
# on the left, 44 / 45 / 46 through mirror nodes 0x87 on the right), then after condition 0x262 (0.1 s blend)
# 0x2126 / 0x2187 hold a SingleFrame (node 0x2113, CP BlenWeight_095, default 0.95) of the same clip. The attack from
# guard plays inside 0x233e, so the guard clip is laid over it with these per-bone alphas (attrib 0x57, the same in the
# clip and hold states): Spine 0.2, Spine1 / Neck / Head 0.3, guarding side Clavicle 0.75, UpperArm 0.8, Forearm 0.9,
# Hand / fingers / Weapon 1.0, the rest 0.
GUARD_ALPHAS = {'left': (0.2, 0.3, 0.3, 0.75, 0.8, 0.9, 1.0, 0, 0, 0, 0),
                'right': (0.2, 0.3, 0.3, 0, 0, 0, 0, 0.75, 0.8, 0.9, 1.0)}
GUARD_CLIP_CATEGORIES = {'left': (20, 44, 45, 46), 'right': (44, 45, 46)}
# Two-handed guards: GuardID_Number 330010 / 340010 go to BT_Guard2Hand_L (0x22ed -> SM 0x22a8) with GuardStance -1 and
# BT_Guard2Hand_R (0x2253 -> SM 0x2210) with +1 (SM 0x2110 transitions 0x2318..0x2328). Their clip states 0x22a9 /
# 0x2211 and hold states 0x22af / 0x2217 are FeatherBlend2 of 0x233e with alphas (attrib 0x57) that differ between clip
# and hold (out/grip/grip_alpha.py). Their switches on GuardID_Category (right: 0x21fc clip, 0x2206 hold; left: 0x228a clip):
# 25 / 29 / 36 -> aCC_33_0010 (authored _R), 44 / 45 / 46 -> aCC_34_0010 (authored _L); the right side plays the _R clips
# as authored and the _L ones through mirror nodes 0x87, the left side the other way round.
GUARD2_ALPHAS = {('left', 'clip'): (0.2, 0.4, 0.4, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
                 ('left', 'hold'): (0.2, 0.5, 0.5, 0.8, 0.9, 0.9, 1.0, 0.8, 0.8, 0.9, 1.0),
                 ('right', 'clip'): (0.2, 0.4, 0.5, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
                 ('right', 'hold'): (0.2, 0.5, 0.5, 0.8, 0.8, 0.9, 1.0, 0.8, 0.9, 0.9, 1.0)}
GUARD2_CLIPS = {25: 330010, 29: 330010, 36: 330010, 44: 340010, 45: 340010, 46: 340010}
# Switch node 0x83 (nodeSwitchUpdateConnections FUN_14064bc70 -> FUN_14064bad0): the CP is clamped to the first / last
# value of the list (attrib 0x09) and otherwise, with rule 0 (attrib 0x39 +0x14 = 0 in every guard switch), goes to the
# nearest value, splitting at the midpoint. No blending: one child. So a category missing from the list still plays a
# clip (the torch, 20, in the right one-handed switch 0x21b5 (44, 45, 46) -> a44).


def switch_nearest(values, v):
    if v <= values[0]:
        return values[0]
    if v >= values[-1]:
        return values[-1]
    for a, b in zip(values, values[1:]):
        if a <= v <= b:
            return a if v < (a + b) / 2 else b
    return values[-1]


def nsa_duration(tae_id):
    path = glob.glob(os.path.join(ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (tae_id // 1000000, tae_id // 10000 % 100,
                                                                         tae_id % 10000)))[0]
    return round(struct.unpack_from('<f', open(path, 'rb').read(), 0x28)[0], 4)
# FUN_14038ffb0 (0x140201743..0x14015bff8): with the Str/Dex lack > 0, WeaponTypeParam +0x51 10 / 20 / 30 / 40 ->
# 20 / 30 / 40 / 40; any other value is returned unchanged.
GUARD_CANCEL_STEP = {10: 20, 20: 30, 30: 40, 40: 40}


def guard_layer(weapons):
    """The guard layer for the guard clips the mod's weapons use (one-handed guard slots, motion type 13)."""
    taes = sorted({k['taeId'] for w in weapons.values() for hand in ('right', 'left', 'two_handed', 'two_handed_other')
                   for k in w[hand].values() if k and k['motionType'] == 13})
    clips = {}
    for tae_id in taes:
        category = tae_id // 1000000 % 100
        if tae_id % 1000000 in (310010, 320010):
            sides = {}
            for side in ('left', 'right'):
                # The aCC_32_0010 node the switch picks; right side: the same _L clip through mirror node 0x87.
                clip = switch_nearest(GUARD_CLIP_CATEGORIES[side], category) * 1000000 + 320010
                sides[side] = {'animation': 'soulsmode:ds2_%d' % clip, 'duration': nsa_duration(clip),
                               'mirror': side == 'right', 'twoHanded': False}
        elif tae_id % 1000000 in (330010, 340010):
            picked = switch_nearest(sorted(GUARD2_CLIPS), category)
            clip = picked * 1000000 + GUARD2_CLIPS[picked]
            authored_left = GUARD2_CLIPS[picked] == 340010
            sides = {side: {'animation': 'soulsmode:ds2_%d' % clip, 'duration': nsa_duration(clip),
                            'mirror': (side == 'left') != authored_left, 'twoHanded': True}
                     for side in ('left', 'right')}
        else:
            sides = None
        if sides:
            clips[str(tae_id)] = sides
    return {'clips': clips,
            # Which side: GuardStance -1 (b8+0x14 == 2, left hand guarding) -> left; +1 -> right.
            'bones': {side: {mc: float(powerstance.bone_alpha(ds2, GUARD_ALPHAS[side])) if ds2 else 0.0
                             for mc, ds2 in powerstance.MC_BONES.items()} for side in ('left', 'right')},
            # BT_Guard2Hand_L / _R: the clip state's alphas and the hold state's.
            'bones2h': {side: {phase: {mc: float(powerstance.bone_alpha(ds2, GUARD2_ALPHAS[(side, phase)])) if ds2
                                       else 0.0 for mc, ds2 in powerstance.MC_BONES.items()}
                               for phase in ('clip', 'hold')} for side in ('left', 'right')},
            # 0x210e 0x210f -> 0x2110 transitions (attrib 0x3b +0x08).
            'enterBlend': 0.1,
            # 0x2141 / 0x21a2: clip -> hold blend; SingleFrame CP BlenWeight_095 default (fraction, as in powerstance).
            # The two-handed guards (BT_Guard2Hand_R / _L, SMs 0x2210 / 0x22a8) hold the same CP (SingleFrame 0x21fa /
            # 0x2286, transitions 0x222e / 0x22c6: condition 0x262, 0.1 s); ds2_anims.py uses it for every guard clip.
            'holdBlend': 0.1, 'holdFrame': 0.95,
            # 0x2110 -> 0x210f by GuardCancelSpeedCategory (CP 0x8c = b8+0x26c): 10 / 20 / 30 / 40..100.
            'exitBlend': {'10': 0.3, '20': 0.5, '30': 0.8, '40-100': 1.1},
            # GuardSpeed (FUN_14038f870; 1.0 without a WAMP row) = WAMP +0x08 (the slot's guardSpeed) x
            # FUN_14038d500(stats, 2) = derived +0x90 = PhysicalStatsPerLevelStatValuesParam[(stat3 + 3 * stat8) / 4]
            # f32 +0x7c (FUN_14038d790 at 0x14038e0f7; stat slot 3 = Attunement, 8 = Adaptability; index outside 1..99
            # -> 1; list index = row - 1) x PlayerGameParamCalculator vt+0x68 (1 - pen) with PlayerLackOfStatsParam
            # row 9, mode 0 (FUN_14031fcb0 -> FUN_140349100: the Str/Dex lack f of the guarding weapon):
            # pen = clamp(0.01 * [(r0 + (r1 - r0) f) if f > 0] + 0.01 * [(r2 + (r3 - r2) g) if g > 0], 0, 1).
            'guardSpeedStat': [round(struct.unpack_from('<f', PSPL[v], 0x7c)[0], 4) for v in range(1, 100)],
            'guardSpeedLackRow': [round(x, 4) for x in struct.unpack_from('<4f', LACK[9], 0)]}


def main():
    import collections
    enemy = params.load('EnemyCommonParam')
    common = collections.Counter(bytes(r[0:4] + r[0x100:0x130]) for r in enemy.values()).most_common(1)[0][0]
    typical = next(r for r in enemy.values() if bytes(r[0:4] + r[0x100:0x130]) == common)
    out = {'source': __doc__.strip().splitlines()[0], 'weapons': {}, 'animations': {},
           # Victim limits: players use ChrCommonParam row 100; Minecraft mobs use the most common EnemyCommonParam
           # values (a choice: mobs have no DS2 row).
           'criticalTargets': {'player': critical_ranges(params.load('ChrCommonParam')[100]),
                               'mob': critical_ranges(typical)},
           # Parry angle limits of the parrier (FUN_140144b00): ChrCommonParam row 100 +0x9c horizontal, +0x98 vertical.
           'parryAngles': [round(x, 4) for x in struct.unpack_from('<2f', params.load('ChrCommonParam')[100], 0x98)][::-1]}
    for name, weapon_id in WEAPONS.items():
        category, = struct.unpack_from('<I', W[weapon_id], 0x0c)
        slots = struct.unpack_from('<64I', A[category])
        # Guard rates: WeaponReinforceParam (row = WeaponParam +0x08) +0x50.., read by FUN_14034cae0 /
        # FUN_14034f8c4, scaled by 0.01 (DAT_1410acb08) and clamped to [0, 1] (FUN_14034f370); index 0 is the one
        # FUN_140138d50 applies first (physical). Reinforcement level 0.
        reinforce, = struct.unpack_from('<I', W[weapon_id], 0x08)
        rates = struct.unpack_from('<5f', R[reinforce], 0x50)
        weapon_type, = struct.unpack_from('<I', W[weapon_id], 0x10)
        # WeaponTypeParam +0x44 / +0x48 / +0x4c widen the riposte / guard-break / backstab facing test
        # (FUN_1403908a0 / FUN_140390700 / FUN_140390a50).
        crit_bonus = [round(x, 4) for x in struct.unpack_from('<3f', T[weapon_type], 0x44)]
        parry_rate, = struct.unpack_from('<f', W[weapon_id], 0x74)
        # Two-handing (docs/research/weapons.md, "Agarre a dos manos: cambio y postura"): FUN_14035db00 sets the
        # Morpheme CP AttackStance_LR from WeaponTypeParam +0x0c (this weapon two-handed in the right hand); +0x24 (u8) is
        # where the weapon is put away when the other weapon is two-handed (WeaponStorage, FUN_14035e090).
        two_hand_stance, = struct.unpack_from('<I', T[weapon_type], 0x0c)
        storage = T[weapon_type][0x24]
        entry = {'weaponId': weapon_id, 'parryRate': round(parry_rate, 4), 'actionCategory': category, 'reinforceId': reinforce,
                 'weaponType': weapon_type, 'criticalAngleBonus': crit_bonus,
                 # Equip load weight (WeaponParam +0x20, summed by FUN_14034b390; docs/research/equipload.md).
                 'weight': round(struct.unpack_from('<f', W[weapon_id], 0x20)[0], 4),
                 'twoHandStance': two_hand_stance, 'storage': storage,
                 # Power stance (docs/research/powerstance.md): WeaponTypeParam +0x38 = category for
                 # DualWieldingPermissionParam (FUN_14034d930 / FUN_14034ede0); stance ids +0x1c (right weapon, state 6,
                 # CP AttackStance_R_2Weapon) / +0x20 (left weapon, state 5, AttackStance_L_2Weapon) (FUN_14035db00).
                 'powerStanceCategory': struct.unpack_from('<I', T[weapon_type], 0x38)[0],
                 'powerStanceStance': {'right': struct.unpack_from('<I', T[weapon_type], 0x1c)[0],
                                       'left': struct.unpack_from('<I', T[weapon_type], 0x20)[0]},
                 'attack': attack_data(weapon_id),
                 # WeaponTypeParam f32 +0x6c: the attacker's counter-hit factor (FUN_140138d50, A+0x80).
                 'counterRate': round(struct.unpack_from('<f', T[weapon_type], 0x6c)[0], 4),
                 # Guard (docs/research/stamina.md, "Guardia"): stability = WeaponReinforceParam +0x74 (+4 per level;
                 # level 0) + WeaponTypeParam +0xd0 one-handed / +0xd4 two-handed (FUN_14034d7b0, no effects or lack).
                 'guardStability': [round(struct.unpack_from('<f', R[reinforce], 0x74)[0]
                                          + struct.unpack_from('<f', T[weapon_type], off)[0], 4) for off in (0xd0, 0xd4)],
                 # Its reinforcement part per level (WRP +0x74 + 4 x level, 11 floats; titanite.md section 3): the shield's
                 # stability grows with the level (Drangleic 55 -> 63).
                 'guardStabilityByLevel': [round(x, 4) for x in struct.unpack_from('<11f', R[reinforce], 0x74)],
                 # Attacker side of the stamina damage on guard (hit+0xc0, FUN_140391fe0): WeaponParam +0x84 x
                 # WeaponTypeParam +0x64 (right hand) / +0x58 (left hand).
                 'guardStaminaRates': [round(struct.unpack_from('<f', W[weapon_id], 0x84)[0]
                                             * struct.unpack_from('<f', T[weapon_type], off)[0], 4) for off in (0x64, 0x58)],
                 'guardRates': [round(min(1.0, max(0.0, r * 0.01)), 4) for r in rates],
                 # Schools this weapon can cast (FUN_140397a30 via FUN_1403963b0, docs/research/spells.md): WeaponTypeParam
                 # bytes +0x00 / +0x01 / +0x02 / +0x03 / +0x53 for SpellParam +0x00 = 0..4.
                 'castSchools': [T[weapon_type][off] for off in (0x00, 0x01, 0x02, 0x03, 0x53)],
                 # WeaponParam +0x40: x SpellParam +0xb4 x LockOnParam f1 = a bullet's homing range (FUN_140337fb0,
                 # casting.md section 7).
                 'bulletRange': round(struct.unpack_from('<f', W[weapon_id], 0x40)[0], 4),
                 # WeaponTypeParam +0x10c: a factor of the wait before the cast animation (FUN_1403936a0, casting.md s.6).
                 'castTime': round(struct.unpack_from('<f', T[weapon_type], 0x10c)[0], 4),
                 # Weapon-buff spell target (docs/research/spells.md, "Condición de objetivo 1/2"): WeaponParam +0x15
                 # (0 melee / catalyst, 1 bow, 2 crossbow, 3 shield) and ItemParam +0x50 bit 1 of the row whose +0x14
                 # is this weapon (FUN_1401a4740; no row = not enchantable).
                 'weaponClass': W[weapon_id][0x15],
                 'enchantable': any(struct.unpack_from('<i', r, 0x14)[0] == weapon_id and r[0x50] & 2
                                    for r in ITEMS.values())}
        # The weapon's own SpEffect (ItemParam +0x04 of the row whose +0x14 is this weapon, FUN_1401a6890; DarkTwo
        # espadones.md section 12): reported, a built-in weapon with one would need its instructions decoded here.
        own = [struct.unpack_from('<i', r, 0x04)[0] for r in ITEMS.values() if struct.unpack_from('<i', r, 0x14)[0] == weapon_id]
        if any(e not in (-1, 0) for e in own):
            print('%s: weapon SpEffect %s not exported' % (name, own), file=sys.stderr)
        for hand, (base, kinds) in HANDS.items():
            entry[hand] = {}
            for kind, k in kinds:
                motion_id = slots[base + k]
                if motion_id == 1:
                    # thunk_FUN_141ca0bab returns 1 for an empty slot: FUN_140396680 skips that action type.
                    entry[hand][kind] = None
                    continue
                row = M[motion_id]
                tae_id, motion_type = struct.unpack_from('<Ii', row)
                if motion_type == 12:
                    # Spell cast slot (docs/research/catalysts.md): its TAE is a placeholder; the animation, bullet
                    # and damage come from the attuned spell's SpellParam row.
                    entry[hand][kind] = {'slot': base + k, 'motionId': motion_id, 'taeId': tae_id,
                                         'motionType': motion_type, 'cast': True,
                                         'staminaCost': stamina_cost(weapon_id, base + k, motion_type),
                                         'hitBack': None, 'hitVolume': None}
                    continue
                if motion_type in BOW_ACTIONS:
                    # Bow slot (docs/research/bow.md): 'shoot' plays bowAnimations (Prepare -> CF), 'aim' holds the
                    # aim camera while the button is held, 'none' ignores the button. The TAE id is a placeholder.
                    entry[hand][kind] = {'slot': base + k, 'motionId': motion_id, 'taeId': tae_id,
                                         'motionType': motion_type, 'bowAction': BOW_ACTIONS[motion_type],
                                         'staminaCost': stamina_cost(weapon_id, base + k, motion_type),
                                         'hitBack': None, 'hitVolume': None}
                    continue
                entry[hand][kind] = {'slot': base + k, 'motionId': motion_id, 'taeId': tae_id,
                                     'motionType': motion_type,
                                     # Charged by each TAE 2200 that starts on a filled slot (FUN_1403269e0).
                                     'staminaCost': stamina_cost(weapon_id, base + k, motion_type),
                                     'motionFactors': [round(x, 4) for x in struct.unpack_from('<5f', row, 8)],
                                     # Attack slots 0..2: PlayerDamageParam ids at +0x2c/+0x30/+0x34 (FUN_140392b20,
                                     # FUN_1403947b0; 0 = no hit) and each one's damage multiplier PDP +0x1c, applied
                                     # after defense in FUN_140138d50. null = empty slot.
                                     # PlayerDamageParam +0x18 (byte): damage floor per type before the divisor,
                                     # when the attack is > 0 (FUN_140138d50).
                                     'damageFloors': [PDP[i][0x18] if i else None
                                                      for i in struct.unpack_from('<3I', row, 0x2c)],
                                     'damageRates': [round(struct.unpack_from('<f', PDP[i], 0x1c)[0], 4) if i else None
                                                     for i in struct.unpack_from('<3I', row, 0x2c)],
                                     # Victim reaction play speed of each hit slot: PlayerDamageParam +0x2c ->
                                     # b8+0x104 (FUN_14013b4d0, reactions 0-3) -> Morpheme CP DmgDuration, read by
                                     # a 0x7d play-speed node (docs/research/hit_reaction.md). null = empty slot.
                                     'reactionSpeeds': [round(struct.unpack_from('<f', PDP[i], 0x2c)[0], 4) if i else None
                                                        for i in struct.unpack_from('<3I', row, 0x2c)],
                                     'reactionMotions': [reaction_motion(i) for i in struct.unpack_from('<3I', row, 0x2c)],
                                     # Base stamina damage on guard of each hit slot: PlayerDamageParam u16 +0x24 (A+0x68 in
                                     # FUN_140139210).
                                     'staminaDamages': [struct.unpack_from('<H', PDP[i], 0x24)[0] if i else None
                                                        for i in struct.unpack_from('<3I', row, 0x2c)],
                                     # Counter-hit factor of each hit slot: PlayerDamageParam f32 +0x20, x the
                                     # weapon's counterRate when the victim is in a TAE 111600 window (FUN_140138d50).
                                     'counterRates': [round(struct.unpack_from('<f', PDP[i], 0x20)[0], 4) if i else None
                                                      for i in struct.unpack_from('<3I', row, 0x2c)]}
                pairs = struct.unpack_from('<6I', row, 0x38)
                if any(pairs):
                    # WeaponAttackMotionParam +0x38..+0x4c: (bullet, PDP) for bullet slots 0..2 (FUN_140392a60); no
                    # built-in weapon has them and their bullet rows are not exported here.
                    print('%s %s %s: WAMP bullets %s not exported' % (name, hand, kind, pairs), file=sys.stderr)
                if motion_type == 13:
                    # Guard request (case 0x140394276 of FUN_140393c00; copied to b8 at 0x1403931ce, read by the CP
                    # updater 0x14035d82c): ctrl+0x30 -> b8+0x268 -> CP GuardSpeed, ctrl+0x34 -> b8+0x26c -> CP
                    # GuardCancelSpeedCategory (docs/research/weapon_arts.md section 8.5).
                    # GuardSpeed (FUN_14038f870) = this WAMP row's +0x08 x guardLayer.guardSpeedStat x
                    # (1 - lack, guardLayer.guardSpeedLackRow); only the weapon's factor is per slot.
                    entry[hand][kind]['guardSpeed'] = round(struct.unpack_from('<f', row, 0x08)[0], 4)
                    # GuardCancelSpeedCategory (FUN_14038ffb0) = WeaponTypeParam u8 +0x51, one step up (10 -> 20 ->
                    # 30 -> 40, 40 stays) when the guarding weapon's Str/Dex lack (equip slot +0x8c, FUN_14034ce60
                    # via FUN_140349100) is > 0.
                    cancel = T[weapon_type][0x51]
                    entry[hand][kind]['guardCancelCategory'] = cancel
                    entry[hand][kind]['guardCancelCategoryLacking'] = GUARD_CANCEL_STEP.get(cancel, cancel)
                if str(tae_id) not in out['animations']:
                    data, why = (fall_animation(tae_id) if kind == 'fall'
                                 else animation(tae_id, motion_type, hand == 'critical', kind))
                    if data is None:
                        print('%s %s %s: %s' % (name, hand, kind, why), file=sys.stderr)
                    out['animations'][str(tae_id)] = data
                # Wall bounce and hit shape of each hit (docs/research/wall_bounce.md): one value for the slot, or a list
                # aligned with the animation's hits (fall: airHits then landingHits) when the hits' PDP rows differ.
                pdp_ids = struct.unpack_from('<3I', row, 0x2c)
                hits = slot_hits(out['animations'][str(tae_id)])
                entry[hand][kind]['hitBack'] = per_hit(hit_back, pdp_ids, hits, hand == 'power_stance')
                entry[hand][kind]['hitVolume'] = per_hit(hit_volume, pdp_ids, hits, hand == 'power_stance')
        out['weapons'][name] = entry
    # Torch time (FUN_1401bba90 / FUN_1401bbd70): PlayerCommonParam row 0 u16 +0xc8 (time set by a torch picked up
    # with no time left), u16 +0xca (time added otherwise), u32 +0xcc (limit). Lighting: a00_18_0220 ItemUseTorchMake,
    # whose TAE 210099 puts the torch (WeaponParam 6000000) in hand 0 and 210100 lights it (FUN_14016c9f0).
    first, add, limit = struct.unpack_from('<HHI', params.load('PlayerCommonParam')[0], 0xc8)
    # Guard break (reaction 10 -> BT_Damage): a00_16_1010 DmgParryBreak_Start. Which animation the network picks is not
    # decoded; this one is the user's choice among the candidates (docs/research/stamina.md, "Guardia").
    anim, tracks = NODE[161010]
    path = glob.glob(os.path.join(ANIM_DIR, NAMES[anim].split('.')[0] + '.nsa'))[0]
    duration = struct.unpack_from('<f', open(path, 'rb').read(), 0x28)[0]
    out['guardBreak'] = {'nsa': os.path.basename(path), 'xmd': XMD[anim], 'duration': round(duration, 4),
                         'avoidLock': window(tracks, 'CancelControl_Avoid', duration),
                         'moveLock': window(tracks, 'CancelControl_Move', duration),
                         'attackLock': window(tracks, 'CancelControl_AttackAll', duration),
                         'regenBlock': regen_block(161010)}
    # Wall bounce (docs/research/wall_bounce.md): SM_HitBack (c0001.nmb node 0x1a93) plays only the straight-sword
    # animations for every weapon. DmgHitBackHold (1.0 two-handed, FUN_14015ee80) picks a29_01 / a29_03; the event id
    # 280 / 281 picks _0100 Upper_R / _0110 Upper_L (286 / 287 = the same, mirrored, for the left hand). The state's
    # 0x7d node plays it at DmgHitBackDuration speed (the slot's hitBack.speed), so every time here is divided by it.
    def hit_back_anim(tae_id):
        anim, tracks = NODE[tae_id]
        d = anim_duration(tae_id)
        return {'taeId': tae_id, 'nsa': os.path.basename(nsa_path(anim)), 'xmd': XMD[anim], 'duration': round(d, 4),
                'avoidLock': window(tracks, 'CancelControl_Avoid', d), 'moveLock': window(tracks, 'CancelControl_Move', d),
                'attackLock': window(tracks, 'CancelControl_AttackAll', d),
                'guardLock': window(tracks, 'CancelControl_Guard', d), 'regenBlock': regen_block(tae_id)}
    out['hitBack'] = {'oneHanded': {'upperR': hit_back_anim(29010100), 'upperL': hit_back_anim(29010110)},
                      'twoHanded': {'upperR': hit_back_anim(29030100), 'upperL': hit_back_anim(29030110)}}
    # What the network really plays (tools/re/hitback.py): the FeatherBlend2 of the pure clip (upper body) over the
    # 1h / 2h Blend2 by DmgHitBackDistance (pelvis, legs), baked by ds2_anims.py for every distance a bouncing slot uses.
    distances = sorted({h['distance'] for w in out['weapons'].values() for hand in w.values() if isinstance(hand, dict)
                        for s in hand.values() if isinstance(s, dict) and s.get('hitBack')
                        for h in (s['hitBack'] if isinstance(s['hitBack'], list) else [s['hitBack']])
                        if h and h['map']})
    for grip, hold in (('oneHanded', '1h'), ('twoHanded', '2h')):
        for side_key, side in (('upperR', 'r'), ('upperL', 'l')):
            out['hitBack'][grip][side_key]['clips'] = {
                str(d): 'soulsmode:' + hitback.clip_name(hold, side, d) for d in distances}
    for k, v in out['animations'].items():
        if v is not None:
            v['hitBackEvent'] = hit_back_event(int(k))
    # Human Effigy (docs/research/hollowing.md): a00_18_0190 ItemUseAbsorb; TAE 220000 applies ItemParam +0x04's SpEffect.
    out['effigy'] = item_use(180190)
    # Stat bonus per stat value (PhysicalStatsPerLevelStatValuesParam row = stat value, ints at +0x0c..+0x2c:
    # str, dex, magic, fire, lightning, dark, (+0x24), bleed, poison; FUN_14038d790).
    pspl = params.load('PhysicalStatsPerLevelStatValuesParam')
    # Player defence (docs/research/damage.md "Defensa del jugador"): per armour piece ArmorParam +0x1c (defStat factor)
    # and its ArmorReinforceParam (ArmorParam +0x18) at level 0: physical +0x00..+0x0c (subtypes 0..3) and absorption
    # magic / lightning / fire / dark +0x10..+0x1c (x0.01). Pieces head, chest, arms, legs; the mod's armour sets
    # (docs/research/equipload.md) and the naked rows 11001100..03 for empty slots.
    armor_p = params.load('ArmorParam')
    arp = params.load('ArmorReinforceParam')

    def piece(row_id):
        a = armor_p.get(row_id)
        if a is None:
            return None
        r = arp[struct.unpack_from('<I', a, 0x18)[0]]
        # Reinforcement (titanite.md section 5, damage.md: FUN_14034dbb0 / FUN_14034dda0): physical and elements lerp
        # from +0x00.. / +0x10.. to +0x30.. / +0x40.. by level / ARP +0x60 (max level); cost row ARP +0x70.
        max_level = struct.unpack_from('<i', r, 0x60)[0]
        return {'factor': round(struct.unpack_from('<f', a, 0x1c)[0], 4),
                'defence': [round(x, 4) for x in struct.unpack_from('<4f', r, 0x00)],
                'absorption': [round(x * 0.01, 5) for x in struct.unpack_from('<4f', r, 0x10)],
                'maxLevel': max_level,
                'defenceMax': [round(x, 4) for x in struct.unpack_from('<4f', r, 0x30)],
                'absorptionMax': [round(x * 0.01, 5) for x in struct.unpack_from('<4f', r, 0x40)],
                'reinforceCost': reinforce_cost(struct.unpack_from('<I', r, 0x70)[0], max_level),
                # Bleed resistance % (bleed.md section 2, FUN_14034dda0 [6]): ARP +0x24 at +0, +0x54 at the max level.
                'bleedAbsorption': round(struct.unpack_from('<f', r, 0x24)[0] * 0.01, 5),
                'bleedAbsorptionMax': round(struct.unpack_from('<f', r, 0x54)[0] * 0.01, 5),
                # Petrification / curse resistance % (docs/research/petrification.md section 1.2, FUN_14034dda0 [8] /
                # [9]): ARP +0x28 / +0x2c at +0, +0x58 / +0x5c at the max level.
                'petrifyAbsorption': round(struct.unpack_from('<f', r, 0x28)[0] * 0.01, 5),
                'petrifyAbsorptionMax': round(struct.unpack_from('<f', r, 0x58)[0] * 0.01, 5),
                'curseAbsorption': round(struct.unpack_from('<f', r, 0x2c)[0] * 0.01, 5),
                'curseAbsorptionMax': round(struct.unpack_from('<f', r, 0x5c)[0] * 0.01, 5)}
    out['armor'] = {name: [piece(base + i) for i in range(4)] for name, base in (
        ('naked', 11001100), ('leather', 11040100), ('chainmail', 17420100), ('iron', 11050100), ('gold', 15130100),
        ('diamond', 17240100), ('turtle', 11660100))}
    out['statBonus'] = {name: [struct.unpack_from('<f' if name.startswith('absorb') else '<i', pspl[v], off)[0]
                               for v in range(1, 100)] for name, off in (
        ('str', 0x0c), ('dex', 0x10), ('magic', 0x14), ('fire', 0x18), ('lightning', 0x1c), ('dark', 0x20),
        ('bleed', 0x28), ('poison', 0x2c), ('mundane', 0x8c),
        # Defence side (FUN_14038d790 / FUN_14038d270): defStat +0x80, elemental absorption x0.01 magic +0x58,
        # fire +0x5c, lightning +0x60, dark +0x64.
        ('defStat', 0x80), ('absorbMagic', 0x58), ('absorbFire', 0x5c), ('absorbLightning', 0x60), ('absorbDark', 0x64),
        # Bleed resistance % of the stats (bleed.md section 2): PSPL f32 +0x48 of row floor((3 Adp + Fth) / 4).
        ('absorbBleed', 0x48),
        # Petrification / curse resistance % (petrification.md section 1.2): PSPL f32 +0x50 of row
        # floor((3 Adp + Vgr) / 4), +0x54 of floor((3 Adp + Att) / 4).
        ('absorbPetrify', 0x50), ('absorbCurse', 0x54))}
    # A parried attacker (player victim, c0001; docs/research/weapons.md "Riposte"): a00_16_1010 DmgParryBreak_Start
    # (StabControl 261, the riposte window, from the end of 262 to its end), the loop 1020 for DamageCtrlParam
    # 99933130 +0x18 = 2.0 s (reaction 9: the user's choice, not confirmed as the parry's), then 1030 End.
    start = anim_duration(161010)
    _, s_tracks = NODE[161010]
    loop = struct.unpack_from('<f', params.load('DamageCtrlParam')[99933130], 0x18)[0]
    end_at = start + loop
    _, e_tracks = NODE[161030]
    e_dur = anim_duration(161030)
    out['parried'] = {'riposteFrom': window(s_tracks, 'StabControl', start)[1], 'riposteUntil': round(end_at, 4),
                      'moveAt': round(end_at + window(e_tracks, 'CancelControl_Move', e_dur)[1], 4),
                      'attackAt': round(end_at + window(e_tracks, 'CancelControl_AttackAll', e_dur)[1], 4),
                      'invulnerable': [[round(end_at + t0, 4), round(end_at + t1, 4)]
                                       for typ, t0, t1, _ in TAE.get(161030, []) if typ == 110100]}
    # Pine resins (docs/research/resins.md): a00_18_0060 ItemUseEnchantWeapon; TAE 220000 applies the SpEffect.
    out['resin'] = item_use(180060)
    # Soul items (docs/research/soul_items.md): ItemTypeParam 550 -> a00_18_0090 ItemUseDeamonsSoul; TAE 220000 applies
    # the SpEffect (930 with the souls).
    out['soul_item'] = item_use(180090)
    # Lloyd's Talisman (ItemParam 60531000 -> ItemTypeParam 900 +0x10 = 100): a00_18_0100 ItemUseThrowOverhand; the
    # item leaves the hand at TAE 220000 (docs/research/thrown_items.md). Its effect is the user's design.
    out['lloyds_talisman'] = thrown_use(180100)
    # Firebomb (ItemParam 60570000 -> ItemTypeParam 1000 +0x10 = 110): a00_18_0110 ItemUseThrowHeavyItem; the bullet
    # (SpEffect 60570000 100120[00] -> BulletParam 60570000) at TAE 220000 (docs/research/thrown_items.md).
    out['firebomb'] = thrown_use(180110)
    # Homeward Bone (ItemParam 60350000 -> ItemTypeParam 250 +0x10 = 210 -> nmb node 0x177b weight 210): a00_18_0080
    # ItemUsePray; TAE 220000 applies SpEffect 60350000, the warp (docs/research/bonfire.md "Hueso de regreso").
    out['homeward_bone'] = item_use(180080)
    out['torch'] = {'firstAdd': first, 'add': add, 'limit': limit, 'make': item_use(180220)}
    # Dodges have no cost event: the cost is charged on entering the Morpheme state (FUN_140328d10), but their TAE
    # blocks regen too. Roll a00_12_0020, backstep a00_11_0011.
    # Input buffer (FUN_140368900): one command kept PlayerCommonParam row 0 +0x64 seconds (1.5), recorded inside these
    # windows while its lock is on, replayed when the lock drops (docs/research/attack.md "Búfer de entrada").
    out['inputBufferSeconds'] = round(struct.unpack_from('<f', params.load('PlayerCommonParam')[0], 0x64)[0], 4)
    out['inputControl'] = input_control()
    # Covenant ranks (docs/research/covenants.md): VowParam row = covenant id, u16 +0/+2/+4 = points for ranks 1/2/3
    # (FUN_1403590c0 / FUN_140204270: rank = how many thresholds the points reach).
    vow = params.load('VowParam')
    out['vowThresholds'] = {str(k): list(struct.unpack_from('<3H', r, 0)) for k, r in vow.items() if 1 <= k <= 9}
    # The level-up screen (docs/research/stats.md "Pantalla de subir nivel"): MenuStatsParam rows 1..99 (FUN_140358cf0),
    # the u16 columns read by 0x14038c1f0 for the menu's display struct, and +0x38 f32 equip load.
    msp = params.load('MenuStatsParam')
    cols = {'stamina': 0x02, 'slots': 0x06, 'castSpeed': 0x08, 'atkStr': 0x0a, 'atkDex': 0x0c, 'bonusMagic': 0x0e,
            'bonusFire': 0x10, 'bonusLightning': 0x12, 'bonusDark': 0x14, 'bonusPoison': 0x16, 'bonusBleed': 0x18,
            'defPhysical': 0x1c, 'defMagic': 0x1e, 'defFire': 0x20, 'defLightning': 0x22, 'defDark': 0x24,
            'resPoison': 0x26, 'resBleed': 0x28, 'resPetrify': 0x2c, 'resCurse': 0x2e, 'agility': 0x32}
    out['menuStats'] = {k: [struct.unpack_from('<H', msp[i], o)[0] for i in range(1, 100)] for k, o in cols.items()}
    out['menuStats']['equipLoad'] = [round(struct.unpack_from('<f', msp[i], 0x38)[0], 4) for i in range(1, 100)]
    # ItemUseCheckDialogParam (FUN_14048c770; FUN_140500c40 picks the first condition i that holds, then asks msg[i]
    # (common.fmg) with YES / NO = common 200 / 201, default NO, or uses at once if msg[i] = 0; docs/research/bonfire.md
    # "Hueso de regreso"). Rows of 0x14 bytes: u8 cond[4] (0 never, 1 always, 2..4 network states), u32 msg[4].
    out['itemUseCheck'] = {str(k): {'conditions': list(r[0:4]), 'messages': list(struct.unpack_from('<4I', r, 4))}
                           for k, r in params.load('ItemUseCheckDialogParam').items() if k != 0}
    out['dodgeRegenBlock'] = {'roll': regen_block(120020), 'backstep': regen_block(110011),
                              # Load variants (docs/research/equipload.md): Tired 70-120 %, Failed > 120 %.
                              'roll_tired': regen_block(120030), 'roll_failed': regen_block(120040),
                              'backstep_tired': regen_block(110051), 'backstep_failed': regen_block(110081)}
    # ChrEquipLoadParam rows used by the equip load (FUN_140320230; row 1 = thresholds): speed 3 walk / 4 jog / 5 dash,
    # dodge distance 0x19 roll / 0x1b roll Tired / 0x17 step / 0x26 step Tired.
    load = params.load('ChrEquipLoadParam')
    out['equipLoadRows'] = {name: [round(x, 4) for x in struct.unpack_from('<5f', load[row])] for name, row in (
        ('thresholds', 1), ('walkSpeed', 3), ('jogSpeed', 4), ('dashSpeed', 5), ('rollDistance', 0x19),
        ('rollTiredDistance', 0x1b), ('stepDistance', 0x17), ('stepTiredDistance', 0x26),
        # CP DodgeSpeed / StepSpeed of the Tired variants: rows 0x1a / 0x25 read raw (playback speed).
        ('rollTiredSpeed', 0x1a), ('stepTiredSpeed', 0x25),
        # Turn rate (FUN_14031c970, b8+0x138): 0x10 walk / jog, 0xf locked walk / jog, 0x11 dash, 0x12 locked dash.
        ('turn', 0x10), ('lockedTurn', 0xf), ('dashTurn', 0x11), ('lockedDashTurn', 0x12))}
    out['castAnimations'] = {str(t): cast_animation(t) for t in CAST_ANIMATIONS}
    # Bow Prepare / CF / NoBullet, same fields as the casts (TAE 2300 arrow times, ShootStateControl, locks, regen).
    out['bowAnimations'] = {str(t): cast_animation(t) for t in (BOW_PREPARE, BOW_CF, BOW_NO_BULLET)}
    out['bow'] = bow_block(out['weapons'])
    clips = sorted({t for v in CROSSBOW_ANIMATIONS.values() for t in v})
    out['crossbowAnimations'] = {str(t): cast_animation(t) for t in clips}
    out['crossbow'] = crossbow_block(out['weapons'], out['crossbowAnimations'])
    # Power stance pairs (FUN_14034d930): DualWieldingPermissionParam row = right weapon's category, byte picked from
    # the left weapon's category by FUN_14034ede0's switch (DUAL_COLUMN); pre-resolved for the mod's categories.
    dual = params.load('DualWieldingPermissionParam')
    cats = sorted({w['powerStanceCategory'] for w in out['weapons'].values()})
    out['dualWielding'] = {str(r): {str(l): bool(r in dual and l in DUAL_COLUMN and dual[r][DUAL_COLUMN[l]])
                                    for l in cats} for r in cats}
    # Stance clips (tools/re/powerstance.py): the right clip is the `_R` animation, the left one the same through mirror
    # node 0x87 (ds2_anims.py writes ds2_<tae> and ds2_<tae>_l). Weights per Minecraft bone for both phases.
    stances = sorted({s for w in out['weapons'].values() for s in w['powerStanceStance'].values()
                      if s in powerstance.STANCE_CLIPS})
    out['powerStanceClips'] = {}
    for stance in stances:
        tae_id = powerstance.STANCE_CLIPS[stance]
        out['powerStanceClips'][str(stance)] = {
            side: {'animation': 'soulsmode:ds2_%d%s' % (tae_id, '' if side == 'right' else '_l'),
                   # The stance clips have no TimeAct track (no NODE entry): length read from the .nsa (+0x28).
                   'duration': round(struct.unpack_from('<f', open(glob.glob(os.path.join(
                       ANIM_DIR, 'a%02d_%02d_%04d_*.nsa' % (tae_id // 1000000, tae_id // 10000 % 100, tae_id % 10000)))[0],
                       'rb').read(), 0x28)[0], 4),
                   'blendIn': powerstance.BLEND_IN, 'holdBlend': powerstance.HOLD_BLEND[side],
                   'clipBones': powerstance.mc_weights(side, 'clip', stance),
                   'holdBones': powerstance.mc_weights(side, 'hold', stance),
                   # Locomotion with the hold pose baked in (ds2_anims.py), like the two-handed `_2h_` clips.
                   'holdSuffix': '_2w_%d%s' % (stance, '' if side == 'right' else '_l')}
            for side in ('right', 'left')}
    out['guardLayer'] = guard_layer(out['weapons'])
    # Attack swing speed (docs/research/lack_of_stats.md section 1.4, FUN_140393350 at the attack start): inside the
    # AttackSpeedControl 151 window the clip plays at WAMP +0x0c (the slot's motionFactors[1]) x (1 - pen),
    # pen = PlayerLackOfStatsParam row 8 with the weapon's Str/Dex lack f (slot+0x8c) and the stamina shortfall g.
    out['swingSpeedLackRow'] = [round(x, 4) for x in struct.unpack_from('<4f', LACK[8], 0)]
    # c0001 damage reaction (docs/research/hit_reaction.md): DmgCategory 30 / 40 play the a00_16_0010..0083
    # DmgKnockBackLight / Heavy clips; locks at play speed 1 (divide by reactionSpeeds).
    kb_d = anim_duration(160050)
    _, kb_tracks = NODE[160050]
    out['hitReaction'] = {
        'source': 'c0001.nmb a00_16_0050 DmgKnockBackHeavySlashDown_FL (TAE 160050, .nsa +0x28): CancelControl_Move / '
                  'AttackAll / Avoid / Guard track ends; StateControl_1 (81) start = exit to locomotion with the stick '
                  'held (SM 0xd2 transition 0x20b5); speed = PDP +0x2c via CP DmgDuration (node 0x7d)',
        'duration': round(kb_d, 4),
        'moveLock': window(kb_tracks, 'CancelControl_Move', kb_d)[1],
        'moveLockStickHeld': window(kb_tracks, 'StateControl_1', kb_d)[0],
        'attackLock': window(kb_tracks, 'CancelControl_AttackAll', kb_d)[1],
        'avoidLock': window(kb_tracks, 'CancelControl_Avoid', kb_d)[1],
        'guardLock': window(kb_tracks, 'CancelControl_Guard', kb_d)[1],
        # Root motion of the 32 DmgKnockBack clips (nsa.trajectory, model space X left / Y back, metres, per sample at
        # trajectoryRate): Light ones stay in place, Heavy ones travel ~5 m along their quadrant's diagonal.
        'trajectories': {str(t): [[k[0], k[1]] for k in nsa.trajectory(glob.glob(os.path.join(
            ANIM_DIR, 'a00_16_%04d_*.nsa' % (t % 10000)))[0])[1]] for t in
            (160000 + 10 * x + q for x in range(1, 9) for q in range(4))},
        'trajectoryRate': round(nsa.trajectory(glob.glob(os.path.join(ANIM_DIR, 'a00_16_0050_*.nsa'))[0])[0], 4)}
    json.dump(out, open(sys.argv[1], 'w', encoding='utf8'), indent=1)
    print('weapons', len(out['weapons']), 'animations', len(out['animations']),
          'without data', [k for k, v in out['animations'].items() if v is None])


if __name__ == '__main__':
    main()
