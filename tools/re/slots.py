"""Prints a WeaponActionCategoryParam row: 64 action slots -> WeaponAttackMotionParam row -> TAE id and .xmd name."""
import ds2paths  # noqa: E402
import os
import re
import struct
import sys

sys.path.insert(0, ds2paths.TOOLS)
import nmb  # noqa: E402
import params  # noqa: E402

d = open(os.path.join(ds2paths.C0001, 'c0001.nmb'), 'rb').read()
XMD = {}
for x in nmb.lookup_tables(d)[2]:
    m = re.search(r'a(\d\d)_(\d\d)_(\d{4})_(\w+)\.xmd', x)
    if m:
        XMD[int(m.group(1)) * 1000000 + int(m.group(2)) * 10000 + int(m.group(3))] = m.group(4)
A = params.load('WeaponActionCategoryParam')
M = params.load('WeaponAttackMotionParam')

if __name__ == '__main__':
    for cat in map(int, sys.argv[1:]):
        print('== category', cat)
        for k, s in enumerate(struct.unpack_from('<64I', A[cat])):
            r = M.get(s)
            if not r:
                print(k, s)
                continue
            a, typ = struct.unpack_from('<Ii', r)
            print(k, s, a, XMD.get(a, '?'), typ, [round(x, 2) for x in struct.unpack_from('<5f', r, 8)])
