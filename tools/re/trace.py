"""Follows direct JMPs through obfuscated code chunks and prints the real instructions. Usage: trace.py <va hex> [max]"""
import sys

import capstone

import disasm

md = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)


def trace(va, limit=40):
    out = []
    seen = set()
    while len(out) < limit and va not in seen:
        seen.add(va)
        ins = next(md.disasm(disasm.read(va, 16), va), None)
        if ins is None:
            out.append('%x  ??' % va)
            break
        if ins.mnemonic == 'jmp' and ins.op_str.startswith('0x'):
            va = int(ins.op_str, 16)
            continue
        out.append('%x  %-6s %s' % (ins.address, ins.mnemonic, ins.op_str))
        if ins.mnemonic in ('ret', 'retf') or (ins.mnemonic == 'jmp'):
            break
        va = ins.address + ins.size
    return out


if __name__ == '__main__':
    for line in trace(int(sys.argv[1], 16), int(sys.argv[2]) if len(sys.argv) > 2 else 40):
        print(line)
