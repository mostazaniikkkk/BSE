"""Item textures from DS2's menu icons (docs/research/soul_items.md s.4): gamedata:/menu/tex/icon/IC_<ItemParam +0x00>.tpf,
its DDS (DXT5) cropped to the alpha bounding box (tpf2png.py), then icon16.py (16 x 16, Lanczos, centred).
Usage: icons.py [textures/item dir]   (default: src/main/resources/assets/soulsmode/textures/item)
"""
import io
import os
import struct
import sys

from PIL import Image

import ds2fs
import ds2paths
from icon16 import icon16

# Mod item -> ItemParam +0x00 (icon id), soul_items.md. Only the souls use DS2's icon (the user's rule, 2026-11-09);
# every other item is pixel art (tools/item_sprites.py, tools/titanite_items.py).
ICONS = {
    'soul_of_a_lost_undead': 60630000,
    'large_soul_of_a_lost_undead': 60640000,
    'soul_of_a_nameless_soldier': 60650000,
    'large_soul_of_a_nameless_soldier': 60660000,
    'soul_of_a_giant': 50920000,
    'soul_of_the_last_giant': 64000000,
}


def cropped(tpf_path):
    """The TPF's single texture as RGBA, cropped to its alpha (tpf2png.py: +0x10 offset, size; DDS)."""
    d = open(tpf_path, 'rb').read()
    assert d[:4] == b'TPF\0'
    off, size = struct.unpack_from('<II', d, 0x10)
    im = Image.open(io.BytesIO(d[off:off + size])).convert('RGBA')
    return im.crop(im.getchannel('A').getbbox())


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ds2paths.ASSETS, 'textures', 'item')
    os.makedirs(out, exist_ok=True)
    os.makedirs(ds2paths.ICONS, exist_ok=True)
    for name, icon_id in ICONS.items():
        crop = os.path.join(ds2paths.ICONS, name + '_icon.png')
        cropped(ds2fs.icon(icon_id)).save(crop)
        icon16(crop, os.path.join(out, name + '.png'))
        print('wrote', name)


if __name__ == '__main__':
    main()
