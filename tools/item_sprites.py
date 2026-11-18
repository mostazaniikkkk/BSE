"""Pixel art (16 x 16, drawn pixel by pixel) of the mod's DS2 items. The user's rule (2026-11-09): items get pixel-art
sprites, never a scaled-down DS2 icon; the only exception is the soul items (tools/re/icons.py). Titanite is in
tools/titanite_items.py. Shapes and colours follow each item's DS2 menu icon (IC_<ItemParam +0x00>):
human effigy 60151000, bonfire ascetic 60527000, pine resins 60250000 / 60260000 / 60270000 / 60280000, Lloyd's
talisman 60531000, firebomb 60570000, catalysts 3800000 / 4010000 / 5400000, alluring skull 60530000, cracked red eye orb 62060000, black separation crystal 62160000, homeward bone
60350000, fragrant branch of yore 60537000.

  python tools/item_sprites.py [textures/item dir]
"""
import os
import sys

from PIL import Image

from titanite_items import OUT


def resin(main, dark, light):
    """The four resins share DS2's shape (a curved bark holder) with their own resin colour."""
    return {'o': (34, 22, 14), 'b': (104, 70, 40), 'c': (150, 106, 62), 'R': main, 'r': dark, 'h': light}


RESIN = [
    '..........oooo..',
    '........oohRRRo.',
    '.......ohRRrRRo.',
    '......ohRrRRRo..',
    '.....oRRRRrRco..',
    '....oRRrRRRcbo..',
    '...ocRRRRcbbo...',
    '...ocbRcbbbo....',
    '..ocbbbbbbo.....',
    '..ocbbbbbo......',
    '.ocbbbbo........',
    '.ocbbbo.........',
    'ocbbo...........',
    'ooo.............',
    '................',
    '................']

ART = {
    'human_effigy': ({'o': (20, 20, 22), 'd': (60, 60, 64), 'm': (100, 100, 106), 'l': (160, 160, 168)}, [
        '......oooo......',
        '.....oddddo.....',
        '.....odmmdo.....',
        '......oddo......',
        '....oooooooo....',
        '...oddmmmmddo...',
        '...odmlllmmdo...',
        '..oddmlddlmddo..',
        '..odmldmmdlmdo..',
        '..odmlddddlmdo..',
        '..oddmllllmddo..',
        '...oddmmmmddo...',
        '...odddddddddo..',
        '....oddddddo....',
        '.....oooooo.....',
        '................']),
    'bonfire_ascetic': ({'o': (60, 20, 8), 'r': (150, 50, 14), 'R': (214, 96, 30), 'f': (245, 150, 40),
                         'y': (255, 214, 90), 'w': (255, 250, 215)}, [
        '.......o........',
        '......oyo.......',
        '......owyo......',
        '.....oywyo......',
        '....oooyfooo....',
        '...oRRfyfRRRo...',
        '..oRRffwyffRRo..',
        '..oRfywwwyfRRo..',
        '.oRRfywwwyfRRRo.',
        '.oRRffyyyffRRro.',
        '.orRRfffffRRrro.',
        '..orRRRRRRRrro..',
        '..orrRRRRRrrro..',
        '...orrrrrrrro...',
        '....oooooooo....',
        '................']),
    'gold_pine_resin': (resin((230, 190, 60), (170, 130, 30), (255, 236, 140)), RESIN),
    'charcoal_pine_resin': (resin((50, 56, 80), (28, 30, 44), (110, 120, 160)), RESIN),
    'dark_pine_resin': (resin((60, 50, 80), (30, 24, 42), (220, 220, 230)), RESIN),
    'rotten_pine_resin': (resin((130, 170, 40), (80, 110, 24), (190, 220, 90)), RESIN),
    'lloyds_talisman': ({'o': (30, 30, 34), 'd': (80, 82, 90), 'm': (130, 134, 142), 'l': (180, 184, 190),
                         'h': (220, 224, 228), 'w': (255, 255, 255)}, [
        '................',
        '......oooo......',
        '.....oo..oo.....',
        '.....o....o.....',
        '......oooo......',
        '....oooooooo....',
        '...ommllllmmo...',
        '..omlhhhhllmdo..',
        '..omlhwhhlmmdo..',
        '..oddddddddddo..',
        '..omllhhllmmdo..',
        '..omllllllmmdo..',
        '...ommmmmmddo...',
        '....oddddddo....',
        '.....oooooo.....',
        '................']),
    'firebomb': ({'o': (34, 24, 16), 'd': (78, 58, 38), 'm': (120, 92, 60), 'l': (164, 130, 86)}, [
        '................',
        '.....oooooo.....',
        '...oommlmmmoo...',
        '..omlmmdmmlmdo..',
        '..olmdmmmdmmdo..',
        '.omddlmmmldmddo.',
        '.ommmdllmdmmmdo.',
        '.odmmmdldmmmddo.',
        '.ommldmmmdlmddo.',
        '.odldmmlmmdlddo.',
        '..odmmdmdmmddo..',
        '..oddmdddmdddo..',
        '...ooddddddoo...',
        '.....oooooo.....',
        '................',
        '................']),
    'sorcerers_staff': ({'o': (38, 30, 24), 'w': (112, 88, 60), 'l': (158, 134, 96), 'g': (128, 138, 106),
                         'h': (178, 186, 150), 'k': (34, 32, 32)}, [
        '...........ooo..',
        '..........oghgo.',
        '..........ogggo.',
        '..........oggo..',
        '.........oggo...',
        '........olwo....',
        '.......olwo.....',
        '......olwo......',
        '.....olwo.......',
        '....olwo........',
        '...okko.........',
        '..okko..........',
        '.olwo...........',
        'olwo............',
        'oo..............',
        '................']),
    'clerics_chime': ({'o': (36, 30, 24), 'd': (98, 80, 56), 'm': (148, 124, 88), 'l': (196, 174, 132),
                       'h': (232, 220, 186)}, [
        '....oooooooo....',
        '....ohhllmmo....',
        '.....ollmmo.....',
        '......olmo......',
        '......olmo......',
        '......olmo......',
        '......olmo......',
        '......olmo......',
        '......olmo......',
        '.oooooolmoooooo.',
        '.ollllllmmmmmdo.',
        '.oooooooooooooo.',
        '..o....oo....o..',
        '.olo..olmo..olo.',
        'ohlmoohllmoohlmo',
        'oooooooooooooooo']),
    'pyromancy_flame': ({'o': (44, 14, 10), 'r': (104, 30, 20), 'R': (152, 50, 30), 's': (196, 86, 56),
                         'f': (238, 128, 36), 'y': (255, 208, 84), 'w': (255, 246, 200)}, [
        '.......oo.......',
        '......osRooo....',
        '....ooosRosRo...',
        '...osRosRosRooo.',
        '...osRosRosRosRo',
        'oo.osRosRosRosRo',
        'osoosRosoosRosRo',
        'ossosRooyosRosRo',
        'oosRRRRofyoRRRRo',
        '.ooRRRofywyoRRRo',
        '..oRRRofwwfoRRRo',
        '..oRRRRoyyoRRRRo',
        '..orrrrrrrrrrrro',
        '....orrrrrrrro..',
        '....orrrrrrrro..',
        '....oooooooooo..']),
    'alluring_skull': ({'o': (18, 16, 16), 'd': (48, 42, 38), 'm': (78, 70, 62), 'l': (112, 102, 90),
                        'c': (40, 150, 190), 'g': (120, 220, 245), 'w': (230, 250, 255)}, [
        '................',
        '.....oooooo.....',
        '....ommllmmo....',
        '...omllllmmdo...',
        '..omllmmmmmddo..',
        '..omlmmmmmmddo..',
        '..ocgwmmcgwddo..',
        '..ocgwgdcgwgdo..',
        '..odcgcddcgcdo..',
        '...odmmgmmddo...',
        '...omdmgmdddo...',
        '....ocgcgcgo....',
        '....odcdcdco....',
        '.....odmmdo.....',
        '......oooo......',
        '................']),
    'homeward_bone': ({'o': (40, 36, 30), 'd': (110, 100, 84), 'm': (158, 146, 124), 'l': (204, 194, 172),
                       'h': (232, 226, 208)}, [
        '...........ooo..',
        '..........ohlmo.',
        '.........ohllmdo',
        '.........olmmdo.',
        '........olmmdo..',
        '.......olmdo....',
        '......olmdo.....',
        '.....olmdo......',
        '....olmdo.......',
        '...olmdo........',
        '.oolmdo.........',
        'ohlmmdo.........',
        'olmmddo.........',
        '.oddoo..........',
        '..oo............',
        '................']),
    'cracked_red_eye_orb': ({'o': (40, 6, 8), 'd': (110, 16, 20), 'm': (170, 30, 34), 'l': (225, 70, 66),
                             'h': (255, 170, 150), 'k': (20, 4, 6), 'c': (60, 10, 12)}, [
        '................',
        '.....oooooo.....',
        '...oodmmmmdoo...',
        '..odmmllmmmmdo..',
        '..odmlhlmcmmdo..',
        '.odmmllmmmcmmdo.',
        '.odmmmdkkdmcmdo.',
        '.odmmdkkkkdmmdo.',
        '.odmcdkkkkdmmdo.',
        '.odmmcdkkdmmmdo.',
        '..odmmcmmmmmdo..',
        '..oddmmcmmmddo..',
        '...ooddddddoo...',
        '.....oooooo.....',
        '................',
        '................']),
    'black_separation_crystal': ({'o': (8, 8, 12), 'd': (28, 26, 36), 'm': (52, 48, 66), 'l': (96, 90, 120),
                                  'h': (170, 165, 200)}, [
        '.......oo.......',
        '......ohdo......',
        '......ohmo......',
        '.....ohlmdo.....',
        '.....ohlmdo.....',
        '....ohllmmdo....',
        '....ohlmmmdo....',
        '...ohllmmmddo...',
        '...ohlmmmmddo...',
        '...ohlmmmmddo...',
        '....ohmmmddo....',
        '....ohmmmddo....',
        '.....olmddo.....',
        '.....odmddo.....',
        '......oddo......',
        '.......oo.......']),
    # A pale, twisted branch standing almost upright, thin dry twigs off both sides (IC_60537000).
    'fragrant_branch': ({'o': (58, 48, 38), 'd': (112, 100, 84), 'm': (162, 152, 132), 'l': (214, 208, 192),
                         't': (122, 102, 82)}, [
        '........oo......',
        '.......olmo..t..',
        '.......olmdo.t.t',
        '......oldmdot.t.',
        '......olmmdtt...',
        '......oldmdo....',
        '.....olmdmo.....',
        '.....olmmdo.....',
        't...olldmdo.....',
        '.t..olmdmdo.....',
        '..ttolmmdo......',
        '.t.oldmmdo......',
        '...olmdmdo......',
        '...oldmddo......',
        '....odddo.......',
        '.....ooo........']),
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    os.makedirs(out, exist_ok=True)
    for name, (palette, rows) in ART.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != '.':
                    im.putpixel((x, y), palette[ch] + (255,))
        im.save(os.path.join(out, name + '.png'))
        print('wrote', name)


if __name__ == '__main__':
    main()
