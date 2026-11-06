"""TPF (DS2, 1 texture) -> DDS -> PNG (full + cropped to alpha bbox). Usage: tpf2png.py <tpf> <out_dir> <name>"""
import struct, sys, io, os
from PIL import Image
tpf, outdir, name = sys.argv[1:4]
d = open(tpf, 'rb').read()
assert d[:4] == b'TPF\0'
cnt, = struct.unpack_from('<I', d, 8)
off, size, fmt = struct.unpack_from('<IIB', d, 0x10)
noff, = struct.unpack_from('<I', d, 0x1c)
tex = d[noff:d.index(b'\0', noff)].decode()
dds = d[off:off + size]
stem = os.path.splitext(os.path.basename(tpf))[0].split('__')[-1]
open(os.path.join(outdir, 'icon', stem + '.dds'), 'wb').write(dds)
im = Image.open(io.BytesIO(dds)).convert('RGBA')
im.save(os.path.join(outdir, 'icon', stem + '_full.png'))
bb = im.getchannel('A').getbbox()
im.crop(bb).save(os.path.join(outdir, name + '_icon.png'))
print(stem, tex, dds[84:88], im.size, 'crop', bb, im.crop(bb).size)
