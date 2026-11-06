"""Extraction from the DS2 SotFS install into ds2paths.EXTRACTED, with the archive helpers of tools/ds2:
- the GameDataEbl archive (every file the exporters read is in it): its header decrypted once with
  GameDataKeyCode.pem (decrypt_bhd.py), files read by path from the .bdt (extract_bdt.py);
- DCX(DFLT) containers inflated and BND4 binders unpacked (unpack_bnd.py, unpack_bnd4_ex.py, unpack_bnd4_any.py);
- the regulation (enc_regulation.bnd.dcx) decrypted (decrypt_regulation.py) and unpacked into regulation/.
Everything is skipped when its output already exists.
"""
import os
import subprocess
import sys

import ds2paths

sys.path.insert(0, ds2paths.TOOLS)
import decrypt_bhd  # noqa: E402
import decrypt_regulation  # noqa: E402
import extract_bdt  # noqa: E402
import unpack_bnd  # noqa: E402
import unpack_bnd4_ex  # noqa: E402

_archive = None


def archive():
    """The GameDataEbl archive, its header decrypted into ds2paths.BHD on first use."""
    global _archive
    if _archive is None:
        bhd = os.path.join(ds2paths.BHD, 'GameDataEbl.bhd')
        if not os.path.exists(bhd):
            os.makedirs(ds2paths.BHD, exist_ok=True)
            n, e = decrypt_bhd.load_pem(os.path.join(ds2paths.GAME, 'GameDataKeyCode.pem'))
            data = decrypt_bhd.decrypt(open(os.path.join(ds2paths.GAME, 'GameDataEbl.bhd'), 'rb').read(), n, e)
            open(bhd, 'wb').write(data)
        _archive = extract_bdt.Archive(bhd, os.path.join(ds2paths.GAME, 'GameDataEbl.bdt'))
    return _archive


def read(path):
    """A file of the archive by its game path (gamedata:/...)."""
    data = archive().read(path)
    if data is None:
        raise FileNotFoundError(path)
    return data


def fetch(path, dest):
    """Writes the archive file {@code path} to {@code dest} (if missing) and returns dest."""
    if not os.path.exists(dest):
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        open(dest, 'wb').write(read(path))
    return dest


def undcx(data):
    return unpack_bnd.undcx(data) if data[:4] == b'DCX\0' else data


def regulation():
    """regulation/*.param and the SpEffect *.emevd, from Game/enc_regulation.bnd.dcx."""
    if os.path.exists(os.path.join(ds2paths.REGULATION, 'WeaponParam.param')):
        return
    os.makedirs(ds2paths.REGULATION, exist_ok=True)
    dcx = decrypt_regulation.decrypt(open(os.path.join(ds2paths.GAME, 'enc_regulation.bnd.dcx'), 'rb').read())
    unpack_bnd.unbnd4(undcx(dcx), ds2paths.REGULATION)


def anibnd(chr_id, folder):
    """gamedata:/morpheme4/chr/<chr>.anibnd.dcx: <folder>/<chr>.anibnd and its .nsa / .nmb unpacked in <anibnd dir>."""
    os.makedirs(folder, exist_ok=True)
    bnd = os.path.join(folder, chr_id + '.anibnd')
    if not os.path.exists(bnd):
        open(bnd, 'wb').write(undcx(read('gamedata:/morpheme4/chr/%s.anibnd.dcx' % chr_id)))
    return bnd


def unpack_anibnd(bnd, out):
    if not os.path.exists(out) or not os.listdir(out):
        os.makedirs(out, exist_ok=True)
        unpack_bnd4_ex.unpack(bnd, out)


def unpack_any(bnd, out):
    """A BND4 with 0x24-byte entries and UTF-16 or Shift-JIS names (models)."""
    if not os.path.exists(out) or not os.listdir(out):
        os.makedirs(out, exist_ok=True)
        subprocess.run([sys.executable, os.path.join(ds2paths.TOOLS, 'unpack_bnd4_any.py'), bnd, out], check=True,
                       stdout=subprocess.DEVNULL)


def model(game_path, name):
    """A model binder (gamedata:/model/...bnd) unpacked into MODELS/<name>; returns the folder."""
    folder = os.path.join(ds2paths.MODELS, name)
    bnd = fetch(game_path, os.path.join(ds2paths.MODELS, name + '.bnd'))
    unpack_any(bnd, folder)
    return folder


def text(lang, name):
    """gamedata:/menu/Text/<lang>/<name>.fmg into TEXT/<lang>/<name>.fmg."""
    return fetch('gamedata:/menu/Text/%s/%s.fmg' % (lang, name), os.path.join(ds2paths.TEXT, lang, name + '.fmg'))


def icon(icon_id):
    """gamedata:/menu/tex/icon/IC_<id>.tpf into ICONS."""
    return fetch('gamedata:/menu/tex/icon/IC_%010d.tpf' % icon_id, os.path.join(ds2paths.ICONS, 'IC_%010d.tpf' % icon_id))


def extract_all():
    """Everything the exporters read, laid out as ds2paths says."""
    regulation()
    unpack_anibnd(anibnd('c0001', os.path.join(ds2paths.EXTRACTED, 'morpheme')), ds2paths.C0001)
    fetch('gamedata:/TimeAct/chr/c000100_pl.tae', ds2paths.TAE_PL)
    for chr_id, tae in (('c1020', 'c102000'), ('c3000', 'c300000'), ('c3096', 'c309600')):
        folder = os.path.join(ds2paths.EXTRACTED, chr_id)
        unpack_anibnd(anibnd(chr_id, folder), os.path.join(folder, 'anibnd'))
        fetch('gamedata:/timeact/chr/%s_pl.tae' % tae, os.path.join(folder, 'timeact__chr__%s_pl.tae' % tae))
    model('gamedata:/model/chr/c0001.bnd', 'model__chr__c0001')
    for lang in ('English', 'NeutralSpanish', 'Spanish'):
        for name in ('itemname', 'SimpleExplanation', 'DetailedExplanation', 'common', 'bofire', 'IngameSystem'):
            try:
                text(lang, name)
            except FileNotFoundError:
                pass
