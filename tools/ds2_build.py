"""Builds every DS2-derived file of the mod from your own copy of Dark Souls II: Scholar of the First Sin, so the jar
can be compiled without any DS2 data in the repository (docs/build.md).

  python tools/ds2_build.py [--game <DS2 Game folder>] [--out <resources folder>] [--only <step,...>] [--check]

Steps, in order (each script is in tools/re and documents where its data comes from):
  extract    decrypt and unpack the needed archives into build/ds2/extracted (ds2fs.py)
  params     small regulation tables (basic_params.py, lockon.py, agility.py)
  weapons    weapons.json (weapons.py, then cast_origins.py)
  enemies    c1020 / c3000 / c3096 clips and attack indexes (enemy_anims.py, ogre_anims.py, last_giant_anims.py)
  player     the player's animations (ds2_anims.py)
  poise      poise.json (poise.py)
  sounds     DS2 sounds: .ogg, sounds.json, attack_sounds.json (sounds.py, uses the game's FMOD and ffmpeg)
  icons      item textures from the menu icons (icons.py)
  lang       language files: the mod's own strings plus DS2's menu texts (lang.py)
  blocks     the titanite block texture from Minecraft's netherite block (titanite_block.py; reads the Minecraft client
             jar Fabric Loom downloads, or MINECRAFT_JAR)

The DS2 folder comes from --game, else the DS2_GAME environment variable, else tools/ds2_game.txt, else Steam's
default folder. Requirements: Python 3.10+, pip install -r tools/requirements.txt, ffmpeg on the PATH (or FFMPEG).
"""
import argparse
import importlib.util
import os
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RE = os.path.join(ROOT, 'tools', 're')
STEPS = ['extract', 'params', 'weapons', 'enemies', 'player', 'poise', 'sounds', 'icons', 'lang', 'blocks']
GAME_FILES = ['DarkSoulsII.exe', 'GameDataEbl.bhd', 'GameDataEbl.bdt', 'GameDataKeyCode.pem', 'enc_regulation.bnd.dcx',
              'fmodex64.dll', 'fmod_event64.dll']


def check(env):
    """Everything the steps need; returns the problems found."""
    problems = []
    if sys.version_info < (3, 10):
        problems.append('Python 3.10 or newer is needed (this is %s)' % sys.version.split()[0])
    for module, package in (('Crypto', 'pycryptodome'), ('PIL', 'Pillow')):
        if importlib.util.find_spec(module) is None:
            problems.append('missing Python package %s: pip install -r tools/requirements.txt' % package)
    game = env['DS2_GAME']
    for name in GAME_FILES:
        if not os.path.exists(os.path.join(game, name)):
            problems.append('not found in the DS2 folder: %s (folder: %s)' % (name, game))
    if not (env.get('FFMPEG') or shutil.which('ffmpeg')):
        problems.append('ffmpeg not found: put it on the PATH or set FFMPEG to its .exe (needed for the sounds)')
    if os.name != 'nt':
        problems.append('the sounds step loads the game\'s FMOD DLLs: it needs Windows')
    return problems


def run(env, script, *args):
    print('  >', script, *args, flush=True)
    subprocess.run([sys.executable, os.path.join(RE, script), *args], cwd=RE, env=env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--game', help='the DS2 SotFS "Game" folder')
    parser.add_argument('--out', help='resources folder to write (default src/main/resources)')
    parser.add_argument('--only', help='comma-separated steps (default: all)')
    parser.add_argument('--check', action='store_true', help='only check the requirements')
    a = parser.parse_args()

    env = dict(os.environ)
    if a.game:
        env['DS2_GAME'] = a.game
    if a.out:
        env['DS2_RESOURCES'] = os.path.abspath(a.out)
    sys.path.insert(0, RE)
    os.environ.update(env)
    import ds2paths  # noqa: E402 (reads the environment set above)
    env['DS2_GAME'] = ds2paths.GAME
    res = ds2paths.RESOURCES
    data = os.path.join(res, 'data', 'soulsmode', 'ds2')
    assets = os.path.join(res, 'assets', 'soulsmode')

    problems = check(env)
    steps = a.only.split(',') if a.only else STEPS
    unknown = [s for s in steps if s not in STEPS]
    if unknown:
        parser.error('unknown steps: %s' % ', '.join(unknown))
    # ffmpeg and Windows only matter to the sounds step.
    blocking = [p for p in problems if 'sounds' in steps or not ('ffmpeg' in p or 'Windows' in p)]
    if problems:
        print('Problems:')
        for p in problems:
            print(' -', p)
    if blocking or a.check and problems:
        sys.exit(1)
    if a.check:
        print('Everything needed is there. DS2 folder:', ds2paths.GAME)
        return
    for d in (data, os.path.join(assets, 'player_animations'), os.path.join(assets, 'enemy_animations')):
        os.makedirs(d, exist_ok=True)

    for step in steps:
        start = time.time()
        print('[%s]' % step, flush=True)
        if step == 'extract':
            subprocess.run([sys.executable, '-c', 'import ds2fs; ds2fs.extract_all()'], cwd=RE, env=env, check=True)
        elif step == 'params':
            run(env, 'basic_params.py', data)
            run(env, 'lockon.py', os.path.join(data, 'lockon.json'))
            run(env, 'agility.py', os.path.join(data, 'agility.json'))
        elif step == 'weapons':
            run(env, 'weapons.py', os.path.join(data, 'weapons.json'))
            run(env, 'cast_origins.py', os.path.join(data, 'weapons.json'))
        elif step == 'enemies':
            out = os.path.join(assets, 'enemy_animations')
            run(env, 'enemy_anims.py', out, os.path.join(data, 'c1020_attacks.json'))
            run(env, 'ogre_anims.py', out, os.path.join(data, 'c3000_attacks.json'))
            run(env, 'last_giant_anims.py', out, os.path.join(data, 'c3096_attacks.json'))
        elif step == 'player':
            run(env, 'ds2_anims.py', os.path.join(assets, 'player_animations'), os.path.join(data, 'weapons.json'))
        elif step == 'poise':
            run(env, 'poise.py', os.path.join(data, 'poise.json'))
        elif step == 'sounds':
            subprocess.run([sys.executable, '-c', 'import ds2fs; ds2fs.archive()'], cwd=RE, env=env, check=True)
            # One process per step: FMOD's event system cannot be created twice in one process.
            for cmd in ('extract', 'catalog', 'tae', 'decode', 'ogg', 'mod'):
                run(env, 'sounds.py', cmd)
        elif step == 'icons':
            run(env, 'icons.py', os.path.join(assets, 'textures', 'item'))
        elif step == 'lang':
            run(env, 'lang.py', 'build')
        elif step == 'blocks':
            run(env, 'titanite_block.py')
        print('  done in %.0f s' % (time.time() - start), flush=True)
    print('All DS2 files are in', res, '- now: gradlew build')


if __name__ == '__main__':
    main()
