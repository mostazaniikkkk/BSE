"""The titanite block's texture (the user's design, 2026-11-09: "como la netherita, pero más clara y trizada"): Minecraft's
own netherite_block.png, lightened and cracked. Minecraft's textures are not in the repository, so this reads them from
the Minecraft client jar Fabric Loom downloaded for the build.

  python tools/re/titanite_block.py [client jar] [textures/block dir]

The jar defaults to ~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar (or MINECRAFT_JAR).
"""
import io
import os
import sys
import zipfile

from PIL import Image

import ds2paths

JAR = os.environ.get('MINECRAFT_JAR') or os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'fabric-loom',
                                                      '1.21.1', 'minecraft-client.jar')
# Lighter: each pixel moves this far toward the light grey below (titanite reads lighter than netherite).
LIGHTEN = 0.35
LIGHT = (196, 200, 206)
# Cracks: pixel paths on the 16 x 16 tile (x, y), dark core; the pixel above-left of each one is lit as an edge.
# Zig-zag lines from the edges, without side branches (a branch near the end of a long line read as an unwanted shape).
CRACKS = [
    [(1, 1), (2, 2), (3, 2), (4, 3), (4, 4), (5, 5)],
    [(15, 3), (14, 4), (13, 4), (12, 5), (12, 6), (11, 7), (10, 7), (9, 8)],
    [(2, 15), (3, 14), (4, 13), (4, 12), (5, 11), (6, 11)],
    [(12, 15), (12, 14), (13, 13), (14, 13), (15, 12)],
]


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else JAR
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ds2paths.ASSETS, 'textures', 'block')
    with zipfile.ZipFile(jar) as z:
        im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/netherite_block.png'))).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            px[x, y] = (round(r + (LIGHT[0] - r) * LIGHTEN), round(g + (LIGHT[1] - g) * LIGHTEN),
                        round(b + (LIGHT[2] - b) * LIGHTEN), a)
    for crack in CRACKS:
        for x, y in crack:
            ex, ey = x - 1, y - 1
            if 0 <= ex < im.width and 0 <= ey < im.height and (ex, ey) not in crack:
                r, g, b, a = px[ex, ey]
                px[ex, ey] = (min(255, r + 40), min(255, g + 40), min(255, b + 42), a)
        for x, y in crack:
            r, g, b, a = px[x, y]
            px[x, y] = (r * 35 // 100, g * 35 // 100, b * 38 // 100, a)
    os.makedirs(out, exist_ok=True)
    im.save(os.path.join(out, 'titanite_block.png'))
    print('wrote titanite_block.png')


if __name__ == '__main__':
    main()
