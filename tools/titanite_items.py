"""Pixel art (16 x 16, drawn pixel by pixel, never a scaled-down DS2 icon) of the titanite items: shard, large shard,
chunk and twinkling titanite (the slab shows its block model, the user's rule). The user's rule (2026-11-09): no DS2 dumps in Minecraft, pixel-art versions.

  python tools/titanite_items.py [textures/item dir]
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'soulsmode', 'textures', 'item')

TITANITE = {'o': (30, 32, 38), 'd': (66, 70, 80), 'm': (100, 106, 118), 'l': (146, 154, 168), 'h': (204, 212, 224)}
TWINKLING = {'o': (40, 64, 118), 'e': (76, 118, 186), 'b': (122, 172, 236), 'c': (196, 226, 255), 'w': (255, 255, 255)}

ART = {
    'titanite_shard': (TITANITE, [
        '................',
        '................',
        '................',
        '................',
        '................',
        '.........oo.....',
        '........ohlo....',
        '.......ohlmo....',
        '......olmmdo....',
        '.....olmmdo.....',
        '.....ommddo.....',
        '......oddo......',
        '.......oo.......',
        '................',
        '................',
        '................']),
    'large_titanite_shard': (TITANITE, [
        '................',
        '................',
        '............oo..',
        '...........ohlo.',
        '..........ohlmo.',
        '.........olmmdo.',
        '........olmmdo..',
        '.......olmmddo..',
        '......olmmddo...',
        '.....olmmddo....',
        '....olmmddo.....',
        '....ommddo......',
        '.....oddo.......',
        '......oo........',
        '................',
        '................']),
    'titanite_chunk': (TITANITE, [
        '................',
        '................',
        '................',
        '.....oooooo.....',
        '....ohhlllmo....',
        '...ohllmmmmdo...',
        '...olmmmmmddo...',
        '..olmmmmmdddo...',
        '..ommmmmddddo...',
        '..ommmmdddddo...',
        '...odddddddo....',
        '....ooooooo.....',
        '................',
        '................',
        '................',
        '................']),
    # A large tablet (the user's design, 2026-11-09); the item also has the enchantment glint (SoulsItems).
    'twinkling_titanite': (TWINKLING, [
        '................',
        '..oooooooooooo..',
        '.ocwwwwwwwwwwbo.',
        '.owccccccccccbo.',
        '.owcwcccccccbbo.',
        '.owccccccccbbbo.',
        '.owcccccwcccbbo.',
        '.owccccccccccbo.',
        '.owcccwcccccbbo.',
        '.owccccccccccbo.',
        '.owccccccccbbbo.',
        '.owcccccccbbbbo.',
        '.obbbbbbbbbbbbo.',
        '.oeeeeeeeeeeeeo.',
        '..oooooooooooo..',
        '................']),
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    os.makedirs(out, exist_ok=True)
    for name, (palette, rows) in ART.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != '.':
                    im.putpixel((x, y), palette[ch] + (255,))
        im.save(os.path.join(out, name + '.png'))
        print('wrote', name)


if __name__ == '__main__':
    main()
