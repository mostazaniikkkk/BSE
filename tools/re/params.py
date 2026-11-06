"""DS2 regulation .param reader (format checked against the accessor FUN_140358b90, docs/research/regulation.md)."""
import ds2paths  # noqa: E402
import os
import struct

REG = ds2paths.REGULATION


def load(name, size=None):
    """{row id: row bytes}."""
    d = open(os.path.join(REG, name + '.param'), 'rb').read()
    count, = struct.unpack_from('<H', d, 0xa)
    rows = [struct.unpack_from('<QQ', d, 0x40 + i * 0x18) for i in range(count)]
    if size is None:
        offs = sorted(o for _, o in rows)
        size = offs[1] - offs[0] if len(offs) > 1 else len(d) - offs[0]
    return {i: d[o:o + size] for i, o in rows}
