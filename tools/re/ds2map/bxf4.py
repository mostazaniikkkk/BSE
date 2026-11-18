"""BHF4/BDF4 split binder (DS2 .mapbhd/.mapbdt, .tpfbhd/.tpfbdt). Layout read from the files:
BHF4 header 0x40: +0x0c i32 file count, +0x20 i64 file header size (0x24), +0x30 u8 unicode, +0x31 u8 format (0x74).
File header 0x24: u8 flags, 3 pad, i32 -1, i64 size, i64 uncompressed size, u32 data offset in .bdt, i32 id, u32 name offset.
Entries may be DCX (inflated with tools/ds2 unpack_bnd.undcx)."""
import struct, sys
sys.path.insert(0, r'D:\Reversing\DS2\tools')
import unpack_bnd

class BXF4:
    def __init__(self, bhd, bdt):
        h = self.h = open(bhd, 'rb').read(); assert h[:4] == b'BHF4'
        self.bdt = bdt
        n, = struct.unpack_from('<i', h, 0xc); hs, = struct.unpack_from('<q', h, 0x20); uni = h[0x30]
        self.files = {}
        for i in range(n):
            o = 0x40 + i * hs
            size, usize, off, fid, no = struct.unpack_from('<qqIiI', h, o + 8)
            if uni:
                e = no
                while h[e:e + 2] != b'\0\0': e += 2
                name = h[no:e].decode('utf-16le')
            else:
                name = h[no:h.index(b'\0', no)].decode('shift_jis')
            self.files[name] = (off, size, fid)
    def read(self, name):
        off, size, _ = self.files[name]
        with open(self.bdt, 'rb') as f:
            f.seek(off); d = f.read(size)
        return unpack_bnd.undcx(d) if d[:4] == b'DCX\0' else d

if __name__ == '__main__':
    b = BXF4(sys.argv[1], sys.argv[2])
    for k, v in list(b.files.items())[:int(sys.argv[3]) if len(sys.argv) > 3 else 20]:
        print(k, v)
