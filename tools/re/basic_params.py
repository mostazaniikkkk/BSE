"""Builds the small regulation tables of data/soulsmode/ds2 (docs/research/stats.md, stamina.md, equipload.md,
classes.md):
- physical_stats_per_level.json: PhysicalStatsPerLevelStatValuesParam rows 1..99 (hp s16 +0x04, hpBonus u8 +0x8a,
  stamina s16 +0x06, equipLoad f32 +0x08, attunementSlots u8 +0x02, spellUsesLevel u8 +0x03, castTime f32 +0x34) and
  RelatePhysicalStatToLevelStatParam row 0 (the first 38 bytes).
- player_level_up_souls.json: PlayerLevelUpSoulsParam rows (u16 level, u16 pad, i32 slope, i32 base).
- player_stamina.json: ChrParam / ChrCommonParam row 100 (stamina.md), PlayerCommonParam row 0 +0x10, and the
  exe's read-only regen delay (DAT_14157f380).
- equip_load_curves.json: ChrEquipLoadParam rows 1 (thresholds), 29, 30, 31, 32 (FUN_140320230).
- starting_classes.json: PlayerStatusParam row 10 x index + 10 (FUN_140203e80; soul level u16 +0x04, slots u16 at
  the offsets FUN_1401fbc00 copies) and common.fmg 11010000 + index (English name).
Usage: basic_params.py [out_dir]   (default: src/main/resources/data/soulsmode/ds2)
"""
import json
import os
import struct
import sys

import ds2paths  # noqa: E402
import params  # noqa: E402

sys.path.insert(0, ds2paths.TOOLS)
import dump_fmg  # noqa: E402

# FUN_140203e80's class list (index -> PlayerStatusParam row 10 x index + 10); 3 and 5 have no class.
CLASS_INDICES = [1, 2, 4, 6, 7, 8, 9, 10]
# FUN_1401fbc00: u16 offset of each attribute slot 0..10 in PlayerStatusParam.
CLASS_SLOT_OFFSETS = [0x06, 0x0c, 0x10, 0x0e, 0x12, 0x14, 0x16, 0x18, 0x1a, 0x1c, 0x1e]
# DAT_14157f380 (f32, read only): the stamina regen delay (stamina.md).
REGEN_DELAY_VA = 0x14157f380


def f32(r, o):
    return round(struct.unpack_from('<f', r, o)[0], 4)


def exe_f32(va):
    """A float of DarkSoulsII.exe at a virtual address (section table of the PE header)."""
    d = open(ds2paths.EXE, 'rb').read()
    pe = struct.unpack_from('<I', d, 0x3c)[0]
    count = struct.unpack_from('<H', d, pe + 6)[0]
    opt = struct.unpack_from('<H', d, pe + 20)[0]
    base = struct.unpack_from('<Q', d, pe + 24 + 24)[0]
    sections = pe + 24 + opt
    for i in range(count):
        vsize, vaddr, rsize, raw = struct.unpack_from('<IIII', d, sections + i * 40 + 8)
        if vaddr <= va - base < vaddr + max(vsize, rsize):
            return round(struct.unpack_from('<f', d, raw + va - base - vaddr)[0], 4)
    raise ValueError('address outside the exe: %x' % va)


def physical_stats():
    pspl = params.load('PhysicalStatsPerLevelStatValuesParam')
    relate = params.load('RelatePhysicalStatToLevelStatParam')[0]
    rows = []
    for level in range(1, 100):
        r = pspl[level]
        rows.append({'level': level, 'hp': struct.unpack_from('<h', r, 0x04)[0], 'hpBonus': r[0x8a],
                     'stamina': struct.unpack_from('<h', r, 0x06)[0], 'equipLoad': f32(r, 0x08),
                     'attunementSlots': r[0x02], 'spellUsesLevel': r[0x03], 'castTime': f32(r, 0x34)})
    return {'source': 'regulation PhysicalStatsPerLevelStatValuesParam (rows by stat value 1..99) + '
                      'RelatePhysicalStatToLevelStatParam row 0',
            'fields': {'hp': 's16 +0x04', 'hpBonus': 'u8 +0x8a', 'stamina': 's16 +0x06', 'equipLoad': 'f32 +0x08',
                       'attunementSlots': 'u8 +0x02', 'spellUsesLevel': 'u8 +0x03', 'castTime': 'f32 +0x34'},
            'relate': list(relate[:38]), 'rows': rows}


def level_up_souls():
    p = params.load('PlayerLevelUpSoulsParam')
    rows = []
    for rid in sorted(p):
        level, _, slope, base = struct.unpack_from('<HHii', p[rid], 0)
        rows.append([level, slope, base])
    return {'source': 'regulation PlayerLevelUpSoulsParam (row data: u16 level, u16 pad, i32 slope, i32 base)',
            'rows': rows}


def stamina():
    chr_p = params.load('ChrParam')[100]
    ccp = params.load('ChrCommonParam')[100]
    pcp = params.load('PlayerCommonParam')[0]
    return {'source': 'regulation ChrParam row 100 and ChrCommonParam row 100 (the only rows; ids < 1000 = player, '
                      'FUN_140358620)',
            'regenPerSecond': f32(chr_p, 0x30), 'exhaustedCeiling': f32(chr_p, 0x34),
            'exhaustedFloor': f32(chr_p, 0x38), 'drainBase': f32(chr_p, 0x3c), 'refillBonus': f32(chr_p, 0x2c),
            'exhaustTimer': f32(ccp, 0x10), 'exhaustTimer2': f32(ccp, 0x60), 'drainRateA': f32(ccp, 0x14),
            'drainRateA2': f32(ccp, 0x18), 'drainRateB': f32(ccp, 0x1c), 'drainRateC': f32(ccp, 0x20),
            'regenDelay': exe_f32(REGEN_DELAY_VA), 'actionCostBase': f32(ccp, 0x24), 'jumpFactor': f32(ccp, 0x30),
            'dodgeFactor': f32(ccp, 0x2c), 'dodgeAltFactor': f32(ccp, 0x28), 'chainedMultiplier': f32(ccp, 0x38),
            'flaggedMultiplier': f32(ccp, 0x3c), 'sprintCooldown': f32(pcp, 0x10),
            'source_sprintCooldown': 'PlayerCommonParam row 0 +0x10 (FUN_140385a80: no sprint for this long once '
                                     'stamina is back above 0)'}


def equip_load():
    p = params.load('ChrEquipLoadParam')
    row = lambda r: [f32(p[r], i * 4) for i in range(5)]
    return {'source': 'regulation ChrEquipLoadParam (param slot 0xf, accessor FUN_140358920); row 1 = load % '
                      'thresholds, other rows = values per threshold (FUN_140320230)',
            'thresholds': row(1), 'regenPenalty': row(29), 'drainA': row(30), 'drainB': row(31), 'drainC': row(32)}


def classes():
    ps = params.load('PlayerStatusParam')
    names = dump_fmg.read_fmg(open(os.path.join(ds2paths.TEXT, 'English', 'common.fmg'), 'rb').read())
    out = []
    for index in CLASS_INDICES:
        row_id = index * 10 + 10
        r = ps[row_id]
        out.append({'index': index, 'paramRow': row_id, 'name': names[11010000 + index],
                    'soulLevel': struct.unpack_from('<H', r, 0x04)[0],
                    'attributes': [struct.unpack_from('<H', r, o)[0] for o in CLASS_SLOT_OFFSETS]})
    return {'source': 'FUN_140203e80 (class index -> PlayerStatusParam row), FUN_1401fbc00 (row offsets -> slots), '
                      'common.fmg 11010000+index (name)', 'classes': out}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else ds2paths.DATA
    for name, build in (('physical_stats_per_level', physical_stats), ('player_level_up_souls', level_up_souls),
                        ('player_stamina', stamina), ('equip_load_curves', equip_load),
                        ('starting_classes', classes)):
        with open(os.path.join(out, name + '.json'), 'w', encoding='utf8', newline=chr(10)) as f:
            json.dump(build(), f, indent=1, ensure_ascii=False)
        print('wrote', name)


if __name__ == '__main__':
    main()
