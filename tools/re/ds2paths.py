"""Where the DS2 files are, for every tool in tools/re (tools/ds2_build.py sets them up).

- GAME: the DS2 SotFS 'Game' folder (DarkSoulsII.exe, *.bhd / *.bdt, enc_regulation.bnd.dcx). From the environment
  variable DS2_GAME, else the first line of tools/ds2_game.txt, else Steam's default folder.
- WORK: staging folder for what is extracted from the game (default build/ds2; environment variable DS2_WORK).
- EXTRACTED: WORK/extracted, laid out the way the exporters read it (regulation/, tae/, morpheme/c0001/, c1020/...).
- TOOLS: tools/ds2, the archive / format helpers (decrypt_bhd.py, extract_bdt.py, nmb.py, tae.py...).
- RESOURCES: src/main/resources, where the exporters write (environment variable DS2_RESOURCES for another folder).
"""
import os
import shutil

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
TOOLS = os.path.join(ROOT, 'tools', 'ds2')
RESOURCES = os.environ.get('DS2_RESOURCES') or os.path.join(ROOT, 'src', 'main', 'resources')
DATA = os.path.join(RESOURCES, 'data', 'soulsmode', 'ds2')
ASSETS = os.path.join(RESOURCES, 'assets', 'soulsmode')


def _game():
    if os.environ.get('DS2_GAME'):
        return os.environ['DS2_GAME']
    cfg = os.path.join(ROOT, 'tools', 'ds2_game.txt')
    if os.path.exists(cfg):
        line = open(cfg, encoding='utf8').readline().strip()
        if line:
            return line
    return r'C:\Program Files (x86)\Steam\steamapps\common\Dark Souls II Scholar of the First Sin\Game'


GAME = _game()
EXE = os.path.join(GAME, 'DarkSoulsII.exe')
WORK = os.environ.get('DS2_WORK') or os.path.join(ROOT, 'build', 'ds2')
EXTRACTED = os.path.join(WORK, 'extracted')
REGULATION = os.path.join(EXTRACTED, 'regulation')
C0001 = os.path.join(EXTRACTED, 'morpheme', 'c0001')
TAE_PL = os.path.join(EXTRACTED, 'tae', 'TimeAct__chr__c000100_pl.tae')
MODELS = os.path.join(EXTRACTED, 'models')
SOUND = os.path.join(EXTRACTED, 'sound')
TEXT = os.path.join(EXTRACTED, 'text')
ICONS = os.path.join(EXTRACTED, 'icons')
# Decrypted archive headers (decrypt_bhd.py), one per game archive.
BHD = os.path.join(EXTRACTED, 'bhd')


def ffmpeg():
    """ffmpeg from the environment variable FFMPEG or the PATH."""
    return os.environ.get('FFMPEG') or shutil.which('ffmpeg')
