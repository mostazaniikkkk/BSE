# Compilar BSE desde el código

El repositorio no trae nada de Dark Souls II: ni animaciones, ni datos, ni sonidos, ni íconos, ni textos del juego.
Todo eso lo genera `tools/ds2_build.py` a partir de **tu propia copia** de Dark Souls II: Scholar of the First Sin
(Steam, PC). Después se compila con Gradle como cualquier mod de Fabric.

## Requisitos
- **Dark Souls II: Scholar of the First Sin** instalado (la carpeta `Game`, la que tiene `DarkSoulsII.exe`).
- **Windows** para el paso de sonidos (usa el FMOD que trae el juego); el resto funciona en cualquier sistema.
- **Python 3.10 o más nuevo** y sus paquetes: `pip install -r tools/requirements.txt` (pycryptodome, Pillow).
- **ffmpeg** en el PATH, o la variable `FFMPEG` con la ruta a `ffmpeg.exe` (para pasar los sonidos a .ogg).
  Tiene que tener `libvorbis` y leer WAV en float (ffmpeg 2.8 no los lee). Sirve el de `pip install imageio-ffmpeg`.
- **JDK 21 o más nuevo** para Gradle (el proyecto se prueba con JDK 25).

## Pasos
1. Revisa que esté todo:
   ```
   python tools/ds2_build.py --check --game "C:\ruta\a\Dark Souls II Scholar of the First Sin\Game"
   ```
   Si tu juego está en la carpeta por defecto de Steam no hace falta `--game`. También puedes dejar la ruta en
   `tools/ds2_game.txt` (una línea) o en la variable `DS2_GAME`.
2. Genera los archivos de DS2 (unos minutos; los sonidos son lo más lento):
   ```
   python tools/ds2_build.py --game "C:\ruta\...\Game"
   ```
   Lo que se extrae del juego queda en `build/ds2/` (variable `DS2_WORK` para usar otra carpeta) y lo generado en
   `src/main/resources`.
3. Compila:
   ```
   gradlew build
   ```
   El jar queda en `build/libs/`. Si faltan los archivos de DS2, Gradle se detiene y dice qué correr.

## Qué hace cada paso
Se puede correr uno solo con `--only <paso>` (o varios separados por coma).

| Paso | Qué genera | Script (`tools/re`) |
|---|---|---|
| `extract` | Descifra el índice de `GameDataEbl` y la regulation, y saca del juego lo necesario (animaciones `.nsa`, `c0001.nmb`, TAE, modelos, textos) | `ds2fs.py` |
| `params` | Tablas de stats, subida de nivel, estamina, carga, clases, fijado, agilidad | `basic_params.py`, `lockon.py`, `agility.py` |
| `weapons` | `weapons.json` (armas, ataques, tiempos, puntos de origen) | `weapons.py`, `cast_origins.py` |
| `enemies` | Animaciones y ataques del Hollow Soldier, el Ogro y el Último Gigante | `enemy_anims.py`, `ogre_anims.py`, `last_giant_anims.py` |
| `player` | Animaciones del jugador | `ds2_anims.py` |
| `poise` | `poise.json` | `poise.py` |
| `sounds` | Sonidos `.ogg`, `sounds.json`, `attack_sounds.json` | `sounds.py` |
| `icons` | Texturas de las almas, desde los íconos del menú (los demás ítems son pixel art propio, `tools/item_sprites.py`) | `icons.py` |
| `lang` | Idiomas: los textos propios del mod (`src/main/lang/`) más los del juego | `lang.py` |
| `blocks` | Textura del bloque de titanita, a partir del bloque de netherita de Minecraft (lee el jar del cliente que baja Fabric Loom, o `MINECRAFT_JAR`) | `titanite_block.py` |

Las herramientas de formato de archivo (cifrado de los archivos, BND, DCX, FMG, TAE, NMB, FLVER) están en `tools/ds2/`.
De dónde sale cada dato está explicado en el propio script y en `docs/research/`.

## Para quien desarrolla
- Los textos nuevos del mod van en `src/main/lang/<idioma>.json`, no en `assets/soulsmode/lang` (esa carpeta se
  genera). Los textos que vienen de DS2 se nombran en `tools/re/lang_ds2.json` (clave → archivo FMG e id).
- Lo generado está en `.gitignore`: no se sube al repositorio.
- Las carpetas generadas se pueden borrar y volver a crear con `tools/ds2_build.py` cuando cambie un exportador.
