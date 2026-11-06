"""Dumps the header, bones and dummy polys of a FLVER2 (.flv) file.

Layout read structurally (checked by the counts fitting the file and bone names decoding):
  header 0x80: 'FLVER\0', 'L\0', u32 version, u32 data offset, u32 data size, i32 dummy count, i32 material count,
    i32 bone count, i32 mesh count, i32 vertex buffer count, f32[3] bbox min, f32[3] bbox max, ..., +0x49 unicode
  then dummy table (0x40 each), material table (0x20 each), bone table (0x80 each).
  dummy: f32[3] position, u8[4] color, f32[3] forward, i16 reference id, i16 parent bone, f32[3] upward,
    i16 attach bone, u8 flag1, u8 use upward, i32 unk30, i32 unk34, 2 x i32 zero.
  bone: f32[3] translation, u32 name offset, f32[3] rotation (euler, rad), i16 parent, i16 child, f32[3] scale,
    i16 next sibling, i16 previous sibling, f32[3] bbox min, i32, f32[3] bbox max, padding.
Usage: flver_dummy.py <file.flv> [...]
"""
import struct, sys

def read(path):
    d = open(path, 'rb').read()
    assert d[:6] == b'FLVER\0' and d[6:8] == b'L\0', d[:8]
    ver, doff, dsize, nd, nm, nb, nmesh, nvb = struct.unpack_from('<IIIiiiii', d, 8)
    uni = d[0x49] == 1
    def name(off):
        if uni:
            e = off
            while d[e:e+2] != b'\0\0': e += 2
            return d[off:e].decode('utf-16-le')
        return d[off:d.index(b'\0', off)].decode('shift_jis')
    dummies, bones = [], []
    p = 0x80
    for i in range(nd):
        pos = struct.unpack_from('<3f', d, p); col = d[p+12:p+16]
        fwd = struct.unpack_from('<3f', d, p + 0x10)
        ref, parent = struct.unpack_from('<hh', d, p + 0x1c)
        up = struct.unpack_from('<3f', d, p + 0x20)
        attach, f1, useup = struct.unpack_from('<hBB', d, p + 0x2c)
        u30, u34, z0, z1 = struct.unpack_from('<iiii', d, p + 0x30)
        dummies.append(dict(i=i, ref=ref, pos=pos, fwd=fwd, up=up, parent=parent, attach=attach, flag1=f1,
                            useup=useup, unk30=u30, unk34=u34, color=col.hex(), zeros=(z0, z1)))
        p += 0x40
    p += nm * 0x20
    for i in range(nb):
        t = struct.unpack_from('<3f', d, p); no, = struct.unpack_from('<I', d, p + 0xc)
        r = struct.unpack_from('<3f', d, p + 0x10); par, ch = struct.unpack_from('<hh', d, p + 0x1c)
        s = struct.unpack_from('<3f', d, p + 0x20); nx, pv = struct.unpack_from('<hh', d, p + 0x2c)
        bones.append(dict(i=i, name=name(no), t=t, r=r, s=s, parent=par, child=ch, next=nx, prev=pv))
        p += 0x80
    return dict(version=ver, counts=(nd, nm, nb, nmesh, nvb), unicode=uni, dummies=dummies, bones=bones)

if __name__ == '__main__':
    for path in sys.argv[1:]:
        f = read(path)
        print(f'== {path}  version {f["version"]:#x} dummies/materials/bones/meshes/vbufs {f["counts"]}')
        for b in f['bones']:
            print('  bone %2d %-22s par %3d  t %s  r %s  s %s' % (b['i'], b['name'], b['parent'],
                  ' '.join('%8.4f' % v for v in b['t']), ' '.join('%7.4f' % v for v in b['r']),
                  ' '.join('%5.2f' % v for v in b['s'])))
        for m in f['dummies']:
            print('  dummy %3d ref %4d par %3d att %3d  pos %s  fwd %s  up %s  f1 %d useup %d u30 %d u34 %d col %s z %s' % (
                m['i'], m['ref'], m['parent'], m['attach'], ' '.join('%8.4f' % v for v in m['pos']),
                ' '.join('%7.4f' % v for v in m['fwd']), ' '.join('%7.4f' % v for v in m['up']),
                m['flag1'], m['useup'], m['unk30'], m['unk34'], m['color'], m['zeros']))
