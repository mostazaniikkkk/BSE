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

# Mod item -> ItemParam +0x00 (icon id): hollowing.md, llama_adversidad.md, resins.md, soul_items.md, thrown_items.md.
ICONS = {
    'human_effigy': 60151000,
    'bonfire_ascetic': 60527000,
    'gold_pine_resin': 60250000,
    'charcoal_pine_resin': 60260000,
    'dark_pine_resin': 60270000,
    'rotten_pine_resin': 60280000,
    'soul_of_a_lost_undead': 60630000,
    'large_soul_of_a_lost_undead': 60640000,
    'soul_of_a_nameless_soldier': 60650000,
    'large_soul_of_a_nameless_soldier': 60660000,
    'lloyds_talisman': 60531000,
    'firebomb': 60570000,
    'soul_of_a_giant': 50920000,
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
