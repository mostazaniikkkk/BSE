"""DS2's status build-up gauges on the HUD (docs/research/petrification.md section 3, "HUD"). Writes:
  assets/soulsmode/textures/gui/status/<piece>.png   the sprites of l01_06_tolerance.flo (menu/01.febnd.dcx: Boss_hp,
                                                     FE_HPgage_01; menu/99.febnd.dcx: waku_02, waku_03, Icon_tex)
  data/soulsmode/ds2/status_hud.json                 each piece's screen rectangle in the 1280 x 720 FLO space for slot
                                                     1, the slots' offsets and the fill mask
Layout (FUN_14006cf10 / FUN_14006cb40): up to 3 slots (instances 0x5f5c3e0 + i) at y -30 / +0.5 / +31; per slot the
shadow plate, the bar frame, the hexagon behind the icon, the status icon (only petrification and curse are ported),
the bar background and the fill, masked by a 124 px sprite keyed over frames 1..11 (frame = 10 - 10 x fraction + 1).
Type 0 (build-up) uses the purple / teal bar, type 1 (state active) the yellow / orange one. Rectangles: sprite
position + uv x sprite scale. Usage: status_hud.py (env DS2_WORK).
"""
import json
import os

from PIL import Image

import ds2fs
import ds2paths
from you_died import bnd4, crop, tpf_image

# piece: (febnd, texture, uv, screen rect of slot 1 [x0, y0, x1, y1], mirrored)
PIECES = {
    'plate_left': ('99', 'waku_02', (250.90, 113.56, 266.20, 129.84), (566.15, 463.45, 581.45, 475.21), False),
    'plate_mid': ('99', 'waku_02', (266.30, 114.95, 280.45, 129.77), (581.60, 464.51, 695.49, 475.21), False),
    'plate_right': ('99', 'waku_02', (280.45, 113.36, 298.70, 129.84), (695.25, 463.26, 713.50, 475.16), False),
    'plate_notch': ('99', 'waku_02', (282.65, 84.90, 298.70, 100.24), (697.45, 453.30, 713.50, 463.26), False),
    'gage_cap_left': ('01', 'FE_HPgage_01', (244.10, 44.45, 252.05, 57.05), (569.85, 455.50, 577.80, 468.10), True),
    'gage_mid': ('01', 'FE_HPgage_01', (135.59, 44.45, 242.16, 57.05), (577.83, 455.50, 691.86, 468.10), False),
    'gage_cap_right': ('01', 'FE_HPgage_01', (243.70, 44.45, 252.05, 57.05), (691.75, 455.50, 700.10, 468.10), False),
    'icon_frame': ('99', 'waku_02', (786.35, 89.95, 826.05, 125.80), (536.55, 446.05, 576.25, 481.90), False),
    'icon_petrify': ('99', 'waku_03', (959, 135, 985, 161), (538.20, 447.05, 564.20, 473.05), False),
    'icon_curse': ('99', 'Icon_tex', (189, 216, 215, 242), (538.20, 447.05, 564.20, 473.05), False),
    'bar_gauge_bg': ('01', 'Boss_hp', (0, 34.90, 128, 42.45), (569.95, 457.10, 697.95, 464.65), False),
    'bar_gauge_fill': ('01', 'Boss_hp', (0, 56.70, 128, 64.00), (569.95, 457.90, 697.95, 465.20), False),
    'bar_active_bg': ('01', 'Boss_hp', (0, 42.25, 128, 49.65), (569.95, 457.50, 697.95, 464.90), False),
    'bar_active_fill': ('01', 'Boss_hp', (0, 49.60, 128, 56.35), (569.95, 457.85, 697.95, 464.60), False),
}
# Slot i's y offset from slot 1 (clip 0x29: -30 / +0.5 / +31).
SLOTS = [-30.5, 0.0, 30.5]
# The fill mask (depth 3): 124 px wide, local x -0.25 at fraction 1 to -124.25 at fraction 0; the bar's local
# origin is 4 px right of its left edge (the bar spans -4..124).
MASK = {'width': 124, 'full': -0.25, 'empty': -124.25, 'barOrigin': 4}
THRESHOLD = 0.02   # DAT_1410b1410


def main():
    gui = os.path.join(ds2paths.ASSETS, 'textures', 'gui', 'status')
    os.makedirs(gui, exist_ok=True)
    menus = {k: bnd4(ds2fs.undcx(ds2fs.read('gamedata:/menu/%s.febnd.dcx' % k))) for k in ('01', '99')}
    out = {}
    for name, (menu, tex, uv, rect, mirrored) in PIECES.items():
        img = crop(tpf_image(menus[menu][tex + '.tpf']), uv)
        if mirrored:
            # Sprite scale x -1: written already flipped.
            img = img.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        img.save(os.path.join(gui, name + '.png'))
        out[name] = {'rect': list(rect), 'size': list(img.size), 'mirrored': mirrored}
        print('wrote', name, img.size)
    data = {'source': 'tools/re/status_hud.py (petrification.md section 3): l01_06_tolerance.flo, 1280 x 720 FLO space',
            'space': [1280, 720], 'slots': SLOTS, 'mask': MASK, 'threshold': THRESHOLD, 'pieces': out}
    json.dump(data, open(os.path.join(ds2paths.DATA, 'status_hud.json'), 'w'), indent=1)


if __name__ == '__main__':
    main()
