"""Downscales a cropped DS2 icon PNG (out of D:\\Reversing\\DS2\\out\\souls_items\\tpf2png.py) to a 16x16 RGBA Minecraft item
texture: aspect kept, longest side 16, PIL Lanczos, centred on a transparent 16x16 canvas. No hand editing.

Usage: icon16.py <cropped.png> <out.png>
"""
import sys

from PIL import Image


def icon16(src, dst):
    im = Image.open(src).convert('RGBA')
    w, h = im.size
    s = 16 / max(w, h)
    size = (max(1, round(w * s)), max(1, round(h * s)))
    small = im.resize(size, Image.LANCZOS)
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    out.paste(small, ((16 - size[0]) // 2, (16 - size[1]) // 2))
    out.save(dst)
    return im.size, size


if __name__ == '__main__':
    print(icon16(sys.argv[1], sys.argv[2]))
