"""Unpacks a BND4 with 0x24-byte entries (u32 flags, i32 -1, i64 size, i64 uncompressed size, u32 data offset,
i32 id, u32 name offset), names UTF-16 (header +0x30 == 1) or Shift-JIS. Seen in model/parts/weapon/*.bnd and
model/chr/c0001.bnd. Usage: unpack_bnd4_any.py <bnd> <outdir>"""
import os, struct, sys
d = open(sys.argv[1], 'rb').read()
assert d[:4] == b'BND4'
n = struct.unpack_from('<I', d, 0xC)[0]
es = struct.unpack_from('<q', d, 0x20)[0]
assert es == 0x24, es
uni = d[0x30] == 1
os.makedirs(sys.argv[2], exist_ok=True)
for i in range(n):
    e = 0x40 + i * es
    size, usize, off, fid, noff = struct.unpack_from('<qqIiI', d, e + 8)
    if uni:
        end = noff
        while d[end:end + 2] != b'\0\0': end += 2
        name = d[noff:end].decode('utf-16-le')
    else:
        name = d[noff:d.index(b'\0', noff)].decode('shift_jis')
    assert size == usize and off + size <= len(d)
    base = name.replace(chr(92), '/').split('/')[-1]
    open(os.path.join(sys.argv[2], base), 'wb').write(d[off:off + size])
    print(f'  {fid:6} {size:9} {d[off:off+4]!r} {name}')
