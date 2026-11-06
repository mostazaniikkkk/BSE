"""Lists MSVC RTTI vtables of DarkSoulsII.exe: class name -> vtable address. Usage: vtables.py <name substring> [entry offset hex]"""
import struct
import sys

import disasm

D = disasm._d
BASE = 0x140000000


def sec(name):
    for n, vs, va, rs, raw in disasm._secs:
        if n.rstrip(b'\0') == name:
            return va, vs, raw
    raise KeyError(name)


def all_vtables():
    rva, rsize, rraw = sec(b'.rdata')
    data = D[rraw:rraw + rsize]
    out = {}
    # Complete object locators: signature 1 (x64), then offsets, typeDescriptor RVA, classDescriptor RVA, self RVA.
    for off in range(0, len(data) - 24, 4):
        sig, o1, o2, td, cd, selfrva = struct.unpack_from('<6I', data, off)
        if sig != 1 or selfrva != rva + off or o1 != 0:
            continue
        name = disasm.read(BASE + td + 0x10, 128).split(b'\0')[0].decode('latin1')
        col_va = BASE + rva + off
        needle = struct.pack('<Q', col_va)
        p = data.find(needle)
        while p >= 0:
            out.setdefault(name, []).append(BASE + rva + p + 8)
            p = data.find(needle, p + 1)
    return out


if __name__ == '__main__':
    vts = all_vtables()
    entry = int(sys.argv[2], 16) if len(sys.argv) > 2 else None
    for name, addrs in sorted(vts.items()):
        if sys.argv[1] in name:
            for a in addrs:
                extra = ''
                if entry is not None:
                    extra = hex(struct.unpack_from('<Q', disasm.read(a + entry, 8))[0])
                print(name, hex(a), extra)
