"""Linear disassembly of DarkSoulsII.exe at a virtual address. Usage: disasm.py <va hex> <bytes>"""
import ds2paths  # noqa: E402
import os
import struct
import sys

import capstone

EXE = ds2paths.EXE
_d = open(EXE, 'rb').read()
_pe = struct.unpack_from('<I', _d, 0x3c)[0]
_n = struct.unpack_from('<H', _d, _pe + 6)[0]
_opt = struct.unpack_from('<H', _d, _pe + 20)[0]
_secs = [struct.unpack_from('<8sIIII', _d, _pe + 24 + _opt + i * 40) for i in range(_n)]


def read(va, size):
    rva = va - 0x140000000
    for _, vs, vaddr, rs, raw in _secs:
        if vaddr <= rva < vaddr + max(vs, rs):
            return _d[raw + rva - vaddr: raw + rva - vaddr + size]
    raise ValueError(hex(va))


def disasm(va, size):
    md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    return list(md.disasm(read(va, size), va))


if __name__ == '__main__':
    for i in disasm(int(sys.argv[1], 16), int(sys.argv[2])):
        print('%x  %-8s %s' % (i.address, i.mnemonic, i.op_str))
