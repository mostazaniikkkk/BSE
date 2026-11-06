"""Adds the spell uses level (derived +0x03, PhysicalStatsPerLevelStatValuesParam u8 +0x03, fed by
RelatePhysicalStatToLevelStatParam[1] = Attunement; read by FUN_1401ab750, docs/research/spells.md "Usos") to
data/soulsmode/ds2/physical_stats_per_level.json.

Usage: spell_uses_level.py <physical_stats_per_level.json>
"""
import os
import ds2paths  # noqa: E402
import json
import sys

sys.path.insert(0, ds2paths.TOOLS)
import params  # noqa: E402

path = sys.argv[1]
data = json.load(open(path, encoding='utf8'))
pspl = params.load('PhysicalStatsPerLevelStatValuesParam')
import struct  # noqa: E402
for row in data['rows']:
    row['spellUsesLevel'] = pspl[row['level']][3]
    # Derived +0x0c (0x14038d8a1): row ((2 Att + Int + Fth) / 4) f32 +0x34, a factor of the wait before the cast
    # animation (FUN_1403936a0 -> b8+0x1b8, docs/research/casting.md section 6).
    row['castTime'] = round(struct.unpack_from('<f', pspl[row['level']], 0x34)[0], 4)
data['fields']['spellUsesLevel'] = 'u8 +0x03'
data['fields']['castTime'] = 'f32 +0x34'
json.dump(data, open(path, 'w', encoding='utf8'), indent=1)
print([(r['level'], r['attunementSlots'], r['spellUsesLevel']) for r in data['rows'] if r['level'] in (9, 10, 15, 30, 99)])
