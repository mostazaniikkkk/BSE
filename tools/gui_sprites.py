"""Pixel art of the mod's GUI pieces (drawn pixel by pixel, the user's rule: pixel art, not DS2's textures scaled down).

- bow_sights.png (49 x 49): DS2's bow sight (menu/01.febnd.dcx l01_18_bow_sights.flo with 10_bloodymail.tpf: a thin
  brushed circle, four diagonal strokes crossing it and a small tick across each stroke inside and outside the circle),
  as pixel art in the tint seen in game.

  python tools/gui_sprites.py [textures/gui dir]
"""
import math
import os
import sys

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src', 'main', 'resources', 'assets',
                   'soulsmode', 'textures', 'gui')
LIGHT = (246, 214, 176, 235)
DARK = (120, 70, 40, 150)


def bow_sights():
    n = 49
    c = 24
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    pts = set()
    # The circle, radius 13 (midpoint circle: one pixel wide, no stray pixels).
    x, y, err = 13, 0, 1 - 13
    while x >= y:
        for a, b in ((x, y), (y, x), (-y, x), (-x, y), (-x, -y), (-y, -x), (y, -x), (x, -y)):
            pts.add((c + a, c + b))
        y += 1
        if err < 0:
            err += 2 * y + 1
        else:
            x -= 1
            err += 2 * (y - x) + 1
    # Four diagonal strokes (pixel diagonals 4..16 from the centre), each with a tick across it at 7 and 13.
    for sx, sy in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
        for i in range(4, 17):
            pts.add((c + sx * i, c + sy * i))
        for i in (7, 13):
            for j in (-2, -1, 1, 2):
                pts.add((c + sx * (i + j), c + sy * (i - j)))
    # A dark edge so it reads on bright skies.
    for x, y in pts:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in pts and 0 <= q[0] < n and 0 <= q[1] < n:
                im.putpixel(q, DARK)
    for q in pts:
        im.putpixel(q, LIGHT)
    return im


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    os.makedirs(out, exist_ok=True)
    bow_sights().save(os.path.join(out, 'bow_sights.png'))
    print('wrote bow_sights')


if __name__ == '__main__':
    main()
