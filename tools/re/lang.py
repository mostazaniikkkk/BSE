"""The mod's language files: its own strings (src/main/lang/<locale>.json, kept in the repository) plus DS2's (menu
texts, never kept in the repository), merged into assets/soulsmode/lang/<locale>.json.

DS2's strings are named in tools/re/lang_ds2.json: key -> [fmg, id], read from gamedata:/menu/Text/<language>/<fmg>.fmg
with en_us = English, es_mx / es_ar / es_cl = NeutralSpanish, es_es = Spanish.

Usage:
  lang.py build    merge into src/main/resources/assets/soulsmode/lang
  lang.py split    one-off: from the current assets/soulsmode/lang, every key whose value is a DS2 text at the same
                   (fmg, id) in every locale goes to lang_ds2.json; the rest to src/main/lang
"""
import json
import os
import sys

import ds2fs
import ds2paths

sys.path.insert(0, ds2paths.TOOLS)
import dump_fmg  # noqa: E402

LOCALES = {'en_us': 'English', 'es_mx': 'NeutralSpanish', 'es_ar': 'NeutralSpanish', 'es_cl': 'NeutralSpanish',
           'es_es': 'Spanish'}
FMGS = ['common', 'bofire', 'IngameSystem', 'charamaking', 'iconHelp', 'ingamemenu', 'itemname', 'SimpleExplanation',
        'DetailedExplanation', 'npcmenu', 'shop', 'staffroll', 'mapname', 'MapEvent', 'titlemenu', 'keyguide']
MAPPING = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'lang_ds2.json')
OWN = os.path.join(ds2paths.ROOT, 'src', 'main', 'lang')
OUT = os.path.join(ds2paths.ASSETS, 'lang')

_texts = {}


def texts(language, fmg):
    key = (language, fmg)
    if key not in _texts:
        _texts[key] = dump_fmg.read_fmg(ds2fs.read('gamedata:/menu/Text/%s/%s.fmg' % (language, fmg)))
    return _texts[key]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf8', newline=chr(10)) as f:
        json.dump(data, f, indent=1, ensure_ascii=False)


def build():
    mapping = json.load(open(MAPPING, encoding='utf8'))
    for locale, language in LOCALES.items():
        own = json.load(open(os.path.join(OWN, locale + '.json'), encoding='utf8'))
        out = dict(own)
        for key, (fmg, text_id) in mapping.items():
            out[key] = texts(language, fmg)[text_id]
        # The keys in the order of the mod's own file, DS2's after (order does not matter to Minecraft).
        write(os.path.join(OUT, locale + '.json'), out)
        print('wrote', locale, len(own), '+', len(mapping))


def split():
    current = {loc: json.load(open(os.path.join(OUT, loc + '.json'), encoding='utf8')) for loc in LOCALES}
    keys = list(current['en_us'])
    mapping = {}
    for key in keys:
        found = None
        for fmg in FMGS:
            ids = [i for i, t in texts('English', fmg).items() if t and t == current['en_us'][key]]
            for text_id in ids:
                if all(texts(lang, fmg).get(text_id) == current[loc].get(key) for loc, lang in LOCALES.items()):
                    found = [fmg, text_id]
                    break
            if found:
                break
        if found:
            mapping[key] = found
    write(MAPPING, mapping)
    for loc in LOCALES:
        write(os.path.join(OWN, loc + '.json'), {k: v for k, v in current[loc].items() if k not in mapping})
    print(len(mapping), 'DS2 keys of', len(keys))


if __name__ == '__main__':
    {'build': build, 'split': split}[sys.argv[1] if len(sys.argv) > 1 else 'build']()
