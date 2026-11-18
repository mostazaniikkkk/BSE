"""The incandescent minerals' item textures (the user's design, 2026-11-16; dev.soulsmode.SoulsIncandescent): Minecraft's
own ingot / scrap texture with its shades moved, in order, onto a hot-metal ramp (the same method and iron ramp as
DarkTwo's incandescent steel, tools/steel_sprites.py there). Minecraft's textures are not in the repository, so this
reads them from the Minecraft client jar Fabric Loom downloaded for the build.

  python tools/re/incandescent_sprites.py [client jar] [textures/item dir]

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
# Darkest -> brightest. Iron: DarkTwo's incandescent steel. Gold runs hotter and yellower, copper redder, the scrap stays
# dark with a glowing core.
RAMPS = {
    'iron': [(92, 22, 6), (150, 40, 8), (205, 72, 10), (240, 120, 20), (255, 176, 58), (255, 222, 128), (255, 248, 208)],
    'gold': [(120, 44, 4), (184, 82, 6), (232, 132, 14), (252, 184, 40), (255, 222, 96), (255, 244, 170),
             (255, 254, 228)],
    'copper': [(84, 14, 8), (138, 28, 10), (190, 50, 14), (228, 88, 22), (248, 136, 48), (255, 186, 104),
               (255, 228, 178)],
    'scrap': [(30, 10, 8), (58, 16, 10), (96, 24, 10), (150, 40, 8), (205, 72, 10), (240, 120, 20), (255, 176, 58)],
}
# texture -> (vanilla source, ramp)
SPRITES = {
    'incandescent_iron_ingot': ('iron_ingot', 'iron'),
    'incandescent_gold_ingot': ('gold_ingot', 'gold'),
    'incandescent_copper_ingot': ('copper_ingot', 'copper'),
    'incandescent_netherite_scrap': ('netherite_scrap', 'scrap'),
}


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def draw(src, ramp):
    shades = sorted({p[:3] for p in src.getdata() if p[3]}, key=lum)
    mapping = {c: ramp[round(i * (len(ramp) - 1) / max(1, len(shades) - 1))] for i, c in enumerate(shades)}
    img = Image.new('RGBA', src.size, (0, 0, 0, 0))
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3]:
                img.putpixel((x, y), mapping[p[:3]] + (p[3],))
    return img


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else JAR
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ds2paths.ASSETS, 'textures', 'item')
    os.makedirs(out, exist_ok=True)
    with zipfile.ZipFile(jar) as z:
        for name, (source, ramp) in SPRITES.items():
            src = Image.open(io.BytesIO(z.read('assets/minecraft/textures/item/%s.png' % source))).convert('RGBA')
            draw(src, RAMPS[ramp]).save(os.path.join(out, name + '.png'))
            print('wrote', name)


if __name__ == '__main__':
    main()
