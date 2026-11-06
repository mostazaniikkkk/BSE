"""Exports the Agility (AGL) tables to data/soulsmode/ds2/agility.json (docs/research/agility.md).

AGL index = (Attunement + 3 * Adaptability) / 4 over the effective stats (slots 3 and 8, FUN_14038d790); 1..99.
- shown value: MenuStatsParam[i] u16 +0x32 (menu builder 0x14038c1f0 -> 0x141ca247e, display struct +0x3a)
- PhysicalStatsPerLevelStatValuesParam[i] fields read through the derived block (FUN_14038d790):
    +0x68 * 0.01 -> derived +0x7c, step i-frame fraction (calc vt+0xd8 -> FUN_14038d280(b, 0))
    +0x6c * 0.01 -> derived +0x80, roll i-frame fraction (calc vt+0xd0 -> FUN_14038d280(b, 1))
    +0x70 * 0.01 -> derived +0x84, jump-ctrl i-frame fraction (calc vt+0x130 -> FUN_14038d280(b, 2))
    +0x74        -> derived +0x88, WeaponChangeSpeed (calc vt+0x210 -> FUN_14038d500(b, 0))
    +0x78        -> derived +0x8c, item use speed base (calc vt+0x200 -> FUN_14038d500(b, 1))
    +0x7c        -> derived +0x90, GuardSpeed factor (FUN_14038d500(b, 2), weapon_arts.md 8.5)
    +0x54 * 0.01 -> derived +0x68, resistance slot 9 of calc vt+0x1d8 (FUN_14038d250(b, 4))
Also the PlayerLackOfStatsParam rows, constants and the TAE 110100 / 110300 / 110500 windows of the dodges.

Usage: agility.py <out.json>
"""
import os
import ds2paths  # noqa: E402
import json
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import params  # noqa: E402
import tae  # noqa: E402

TAE = ds2paths.TAE_PL


def f32(row, off):
    return struct.unpack_from('<f', row, off)[0]


def main(out):
    pspl = params.load('PhysicalStatsPerLevelStatValuesParam')
    menu = params.load('MenuStatsParam')
    rows = []
    for i in range(1, 100):
        p = pspl[i]
        rows.append({
            'index': i,
            'shown': struct.unpack_from('<H', menu[i], 0x32)[0],
            'stepIframeFraction': round(f32(p, 0x68) * 0.01, 6),
            'rollIframeFraction': round(f32(p, 0x6c) * 0.01, 6),
            'jumpIframeFraction': round(f32(p, 0x70) * 0.01, 6),
            'weaponChangeSpeed': round(f32(p, 0x74), 6),
            'itemUseSpeed': round(f32(p, 0x78), 6),
            'guardSpeed': round(f32(p, 0x7c), 6),
            'resist9': round(f32(p, 0x54) * 0.01, 6),
        })
    lack = params.load('PlayerLackOfStatsParam')
    ccp = params.load('ChrCommonParam')[100]
    cmp_ = params.load('ChrMoveParam')[100]
    d, anims = tae.load(TAE)
    windows = {}
    for aid in (120020, 120021, 120030, 120031, 120040, 110011, 110031, 110051, 110071, 110081, 140020):
        ev = anims[aid]
        windows[str(aid)] = {str(t): [[round(t0, 4), round(t1, 4)] for (tt, t0, t1, _) in ev if tt == t]
                             for t in (110100, 110300, 110500)}
    data = {
        'source': 'index = (ATN + 3*ADP)/4 (FUN_14038d790); MenuStatsParam u16 +0x32; '
                  'PhysicalStatsPerLevelStatValuesParam +0x54/+0x68/+0x6c/+0x70/+0x74/+0x78/+0x7c; '
                  'see docs/research/agility.md',
        'rows': rows,
        # PlayerLackOfStatsParam rows: (base, slope) of the stat-lack term, (base, slope) of the stamina term
        'dodgeLackRow': list(struct.unpack('<4f', lack[0x12])),
        'itemUseLackRow': list(struct.unpack('<4f', lack[0x1a])),
        # item use speed while moving: lerp(ChrCommonParam+0x2fc, +0x300, |v| / ChrMoveParam+0x20)
        'itemUseMoveSpeed': [f32(ccp, 0x2fc), f32(ccp, 0x300)],
        'itemUseMoveRef': round(f32(cmp_, 0x20), 6),
        'tae': windows,
    }
    json.dump(data, open(out, 'w', encoding='utf8'), indent=1)
    for r in rows:
        if r['index'] in (1, 5, 10, 20, 30, 40, 50, 99):
            print(r)


if __name__ == '__main__':
    main(sys.argv[1])
