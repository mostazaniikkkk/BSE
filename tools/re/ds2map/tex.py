"""DS2 TPF (PC) -> PIL image. Layout read from the files: 'TPF\\0', +0x04 u32 data size, +0x08 i32 texture count,
+0x0c u8 platform (0 = PC), ...; entries from 0x10, 0x14 bytes each: u32 data offset, u32 size, u8 format, u8 type,
u8 mipmaps, u8 flags, u32 name offset (Shift-JIS), u32. Data = a DDS file (same as icons.py).
DDS decoded with Pillow, or texture2ddecoder for the DX10 / BC formats Pillow refuses."""
import io, struct
import numpy as np
from PIL import Image


def tpf_textures(d):
    assert d[:4] == b'TPF\0', d[:4]
    n, = struct.unpack_from('<i', d, 8)
    out = []
    for i in range(n):
        off, size, fmt, typ, mips, flags, no = struct.unpack_from('<IIBBBBI', d, 0x10 + i * 0x14)
        name = d[no:d.index(b'\0', no)].decode('shift_jis')
        out.append((name, fmt, d[off:off + size]))
    return out


def dds_image(dds):
    try:
        im = Image.open(io.BytesIO(dds))
        im.load()
        return im.convert('RGBA')
    except Exception:
        pass
    import texture2ddecoder as t2d
    h, w = struct.unpack_from('<II', dds, 0x0c)
    fourcc = dds[0x54:0x58]
    off = 0x80
    if fourcc == b'DX10':
        dxgi, = struct.unpack_from('<I', dds, 0x80)
        off = 0x94
        fn = {71: t2d.decode_bc1, 72: t2d.decode_bc1, 74: t2d.decode_bc2, 77: t2d.decode_bc3, 78: t2d.decode_bc3,
              80: t2d.decode_bc4, 83: t2d.decode_bc5, 95: t2d.decode_bc6, 96: t2d.decode_bc6,
              98: t2d.decode_bc7, 99: t2d.decode_bc7}[dxgi]
    else:
        fn = {b'DXT1': t2d.decode_bc1, b'DXT3': t2d.decode_bc2, b'DXT5': t2d.decode_bc3, b'ATI1': t2d.decode_bc4,
              b'ATI2': t2d.decode_bc5, b'BC4U': t2d.decode_bc4, b'BC5U': t2d.decode_bc5}[fourcc]
    raw = fn(dds[off:], w, h)
    return Image.frombytes('RGBA', (w, h), raw, 'raw', 'BGRA')


def average(im):
    """Mean RGB weighted by alpha (alpha-tested leaves etc. ignore transparent texels), and mean alpha."""
    a = np.asarray(im.convert('RGBA'), np.float64) / 255
    w = a[..., 3]
    if w.sum() < 1e-6:
        w = np.ones_like(w)
    rgb = (a[..., :3] * w[..., None]).sum((0, 1)) / w.sum()
    return tuple(int(round(c * 255)) for c in rgb), float(a[..., 3].mean())
