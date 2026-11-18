# Sonidos de DS2 (efectos del jugador)

Herramienta: `tools/re/sounds.py` (extracción, catálogo, decodificación y exportación al mod). Staging (fuera del repo):
`D:\Reversing\DS2\extracted\sound\` (`catalog.json`, `tae_snd.json`, `samples.json`, `wav/`, `ogg/`).
Volcados de Ghidra: `scratchpad\sound\work\*.c`.

## 1. Dónde están los datos
- Cadenas del exe: `gamedata:/sound/frpg2.fevbnd.dcx`, `gamedata:/sound/frpg2_main.bnd`, `frpg2_main2.bnd`,
  `frpg2_smain.bnd`, `frpg2_c%04d.bnd`, `map:/sound/frpg2_ps%06d.bnd`, `gamedata:/sound/MagicOrchestra.ini`.
- `frpg2.fevbnd.dcx` (DCX DFLT + BND4) trae todos los proyectos FMOD Designer (`frpg2.fev`, `frpg2_c1..c9.fev`,
  `_m`, `_pm`, `_ps`, `_sm`, `_smain`, `_xm`, `_dlc`…) y los `.itl`. **`frpg2.fev`** es el proyecto común: 1156
  eventos, bancos `frpg2_main` (banco 0) y `frpg2_main2` (banco 1).
- `frpg2_main.bnd` / `frpg2_main2.bnd` (DCX + BND4) traen `frpg2_main.fsb` (212 muestras) y `frpg2_main2.fsb` (193).
- La carpeta suelta `Game\sound\` solo tiene música, voces y ambientes de mapa en streaming (`m*`, `v*`, `y*`, `a*`):
  **ningún efecto del jugador**.
- Motor: FMOD Ex **4.44.50** del propio juego (`Game\fmodex64.dll`, `fmod_event64.dll`; `FMOD_EventSystem_GetVersion`
  = `0x44450`).

## 2. Formatos
- **FSB5** v1, cabecera 0x3c, modo 15 = **Vorbis**; cabeceras de muestra en campos de bits de 64 bits; tabla de
  nombres. Solo 4 muestras estéreo (`f100000031`, `s000000147`, `indoor`, `outdoor`); el resto mono.
- **FEV**: `RIFF 'FEV '` → `LIST PROJ` → `LGCY` (datos FEV1 clásicos) + `STRR` (tabla de cadenas: el LGCY guarda
  índices). Definiciones de sonido (verificado en las 359 / 441 ondas): `u32 cuenta`; por definición `u32 nombre,
  u32 config, u32 nOndas`; por onda `u32 tipo (0 = wavetable, el único presente), u32 peso aleatorio, u32 largo +
  ruta .wav, u32 banco, u32 índice en el banco, u32 duración ms`. Cada onda nombra la muestra FSB del mismo nombre.
- Configs de definición (117 × 0x4a bytes antes de la tabla): `+0x10` f32 volumen, `+0x20` f32 factor mínimo de
  volumen aleatorio, `+0x24` f32 pitch, `+0x34` f32 aleatoriedad de pitch (unidades crudas: × 48 = semitonos).
  **Confirmado midiendo** el motor (sección 4); el resto de campos (modo de reproducción `+0x00`, spawn…) sin decodificar.

## 3. Modelo de eventos de sonido (`ChrMorphemeTimeActTrackSoundCtrl`, `FUN_1403278d0`)
El TAE del jugador con sonidos es **`gamedata:/TimeAct/chr/c000100_snd.tae`** (cadena `c%06d_snd.tae`; el `_pl.tae`
no tiene ninguno). Tipos `0xc9` = 201 (9072 eventos), `0xca` = 202 (461), `0xcb` = 203 (ninguno en el jugador).
Nombre final = prefijo + `%09d`; `FUN_140b15210` usa `"smacpoz"[tipo]`, `FUN_1404a4a70`/`FUN_1404a5c30` la tabla
`PTR_141586408` = `{c, h, c}`.

**201** (`FUN_1404a4a70`): parámetros `+0` u32 id, `+4` i32 fuente de variante, `+8` u8 voz (+1000 si `equip+0x2c`),
`+9` u8 categoría `s` (`s%09d` id + variante, `FUN_1404a5b60`), `+0xa` i16 dummy poly (−1 = personaje), `+0xc` u8,
`+0xd` u8 banco común, `+0xe` u8 condición (`vt+0x1b0`), `+0xf` u8 consulta de suelo (`FUN_1402e9fa0`), `+0x10` u16.
Nombre = `chr·100000 + id + variante (+1000)`; `chr` = (id de personaje / 10)·10 → **0 para c0001**. Ids 8002/8003
se silencian salvo un flag. Fuentes de variante (`FUN_14016e980…e9e0`, `chr+0xb0`): 1 = `+0x00` (o `+0x24`),
2..5 = `+0x10..+0x1c` = **`ArmorParam +0x48` de las piezas 0..3** (`FUN_14037e0f0` vía `FUN_140348690`, 1 sin pieza),
6 = `chr+0x248`.

**202** (`FUN_1404a5c30`): `+0` u32 golpe (0..5), `+4` u8 mano, `+8` i16 dummy, `+0xa` u8, `+0xb` u8 banco común.
Nombre = **`c(6000 + 10·tipo + golpe)`**, tipo = byte del personaje `+0x7b` (mano 0) o `+0x7a` (mano 1), invertido si
`+0x7c`. `FUN_14037e210` los llena con **`WeaponTypeParam +0x37`** del arma −1 (derecha) / −2 (izquierda); con agarre
2 / 3 ambos son la misma arma. Tipo 11 = `+110` y, si `chr+0xb8 +0x18b ≠ 12`, un segundo sonido con tipo 3.

**Golpes (`z`)**, `FUN_1404a5000` desde `FUN_1403ac600` ← `FUN_1403ac3b0` ← `FUN_1401353e0` ← `FUN_14013ab30`:
`DamageEffectSeParam` (regulation, 44 bytes por fila): `m = fila[(p4·10+p5)·1000][atacante]`,
`v = fila[(p4·10+p5)·1000 + m][víctima]`; nombre `z((p4 + 10m)·10 + p5)·10000 + v`. `p4` = 4 / 5 con dos flags
(`req+0x19` / `+0x1a`). La petición (`ChrDamageActionCtrl vt+0x90` `FUN_1401369a0`): atacante =
**`+0x50` de la fila de ataque del golpe** (para elementales 0x12/0x10/0xf/0x11), `p5` = byte de `golpe+0x10`,
materiales de la víctima de `vt+0x88` (sin analizar).

**Pisadas** (`FUN_14016f900` → `FUN_1404a4d90`): `c((mat·100 + paso)·1000 + suelo)`, paso 1..4; suelo vía
`FUN_1402e9ee0`.

## 4. Lo que aplica el FMOD del juego (medido en `fmod_event64.dll`, `sounds.py measure`)
Cada evento se arrancó 300 veces sin salida (`NOSOUND_NRT`):
- Grupo del evento: pitch = `pitch_semitones` + **un número entero de semitonos** `round(U(−N, N))`,
  N = `pitch_randomization_semitones` (visto en todas las mediciones; los extremos salen la mitad de veces); volumen
  = `volume · (1 − volume_randomization · u)`.
- Cada capa (un canal por definición de sonido): pitch y volumen en un rango continuo (config de la definición y
  efectos de capa). Se guardan el mínimo y máximo medidos.
- Propiedades por índice (orden del enum `FMOD_EVENTPROPERTY` del SDK 4.44, verificado con valores: conos 360,
  doppler 1, steal 10000, 1500 Hz, usuario en 53): `mode` 8 (2D) en los `c`, 16 (3D) en los `z`; rolloff
  `0x04000000` (curva propia en el parámetro `(distance)` 0..29); distancia máx. 29 m; propiedades de usuario
  `playDistance` / `stopDistance` 29 (14 en los `z`).
- Qué definiciones arranca cada evento: callback `SOUNDDEF_START` del propio FMOD (`catalog.json`). El nombre del
  evento no coincide con el de la definición (p. ej. `c000006000` → `/frpg2_main/c000006006`).

## 5. Tipos de golpe de las armas del mod (`WeaponTypeParam +0x37`)
Espadas rectas 0 · hachas 4 · maza, garrote, pico 5 · lanza 6 · escudo, puños, antorcha, llama 3 · bastón 10 ·
campana 11 · arco 8 · ballesta 9.

## 6. En el mod
- `tools/re/sounds.py mod` escribe:
  - `data/soulsmode/ds2/attack_sounds.json`: los eventos 201/202 de **cada animación de `weapons.json`** (133 con
    sonido), los tipos de golpe por arma y, por evento, sus capas y aleatoriedad medida (45 eventos).
  - `assets/soulsmode/sounds.json` y `assets/soulsmode/sounds/ds2/*.ogg` (66 archivos, ~1 MB): una entrada por
    capa (`ds2.<evento>.<n>`), sus ondas con el peso del FEV y `attenuation_distance` = distancia máx. del evento.
    El .ogg es la muestra decodificada por `fmodex64.dll`, remuestreada al pitch base (evento + centro de la capa)
    y codificada mono Vorbis (ffmpeg libvorbis q6), porque Minecraft limita el pitch a 0.5..2.
- `SoulsSounds` (flag `ENABLED`): registra los `SoundEvent` (rango fijo = distancia máx.) y, desde
  `SoulsAttack.tick`, reproduce en el servidor los eventos de la animación en curso cuyo tiempo TAE ya pasó, en el
  jugador (los oyen todos). 202: mano 0 = arma derecha, mano 1 = izquierda, a dos manos ambas = el arma empuñada.
  201: solo los que no dependen de nada en tiempo de ejecución (variante 0, sin voz, sin suelo, sin condición).
  Por cada arranque: volumen y pitch como en la sección 4.
- Cubre ataques ligeros/fuertes, ataques en movimiento, críticos (backstab/riposte: `c000007904`, `7905`, `7912`…),
  ataques con escudo/parry y arco/ballesta, cuando la animación es una de `weapons.json`.
- **Uso de objetos** (almas: `a00_18_0090` ItemUseDeamonsSoul, TAE 180090; Efigie humana: `a00_18_0190`
  ItemUseAbsorb, TAE 180190). Sus únicos 201 utilizables son de **armadura** (variante 3 = pecho, 4 = brazos):
  - 180090: `c(5200 + pecho)` a 0.0667 s, `c(5000 + brazos)` a 2.3667 s.
  - 180190: `c(5200 + pecho)` a 0.5667 s, `c(5000 + brazos)` a 1.3333 s, `c(5200 + pecho)` a 2.2333 s.
  - Material = `ArmorParam +0x48` de la pieza (slots `eq+0x298 + i·0x30` = cabeza, pecho, brazos, piernas,
    equipload.md) con los sets del mod (`weapons.py armor`): cuero 24, malla 2/2/24/2, hierro 2, oro 2/3/2/2,
    diamante y netherita 2, tortuga 2 (solo casco). Slot vacío → 1 y desnudo → 8: `c000005201`, `c000005008`,
    `c000005208` no tienen sonido en el FEV → silencio, igual que en DS2. Pieza de datapack → sin sonido.
  - Eventos nuevos: `c000005002`, `5003`, `5024`, `5202`, `5203`, `5224`. Los demás 201 de estas animaciones son
    5500 con consulta de suelo (no establecido).
  - El SpEffect del uso (TAE 220000 a 1.7 / 1.667 s) no trae sonido: almas → `100130[01]` → SpEffect 930
    (`101130[00]`), efigie → `100050[02]` (soul_items.md, hollowing.md); `c000100_sfx.tae` no tiene eventos en
    180090 / 180190. Un sonido de "absorción" propio del objeto **no está establecido** (no hay campo de sonido
    conocido en `ItemParam`). **Corregido abajo ("Sonido del objeto"): sí está establecido.**
- Test `SoundsDataTest`: cada sonido referido tiene entrada en `sounds.json` y cada archivo existe y es Ogg Vorbis mono.

## 7. Sin establecer / no implementado
- **Prefijo `c` vs `h`** (`chr+0xb0 +0x3e > 0` → `h`): no se encontró quién escribe ese byte. El mod usa `c`. Los
  eventos `h` usan las mismas muestras de golpe; cambian volúmenes en algunos (p. ej. `c000006001` 0.84 vs `h` 0.67).
- **`+0x7c`** (invierte la mano del 202): sin identificar; el mod no invierte.
- **Campana (tipo 11)**: el segundo sonido depende de `chr+0xb8 +0x18b`, sin identificar: no se reproduce.
- **Ropa/armadura (201 variantes 2..5) en ataques**: el mecanismo está implementado (sets del mod), pero solo se
  exporta para las animaciones de uso de objetos (`ITEM_USE_ARMOR` en `sounds.py`); en los ataques quedan fuera
  (1199 eventos) hasta que el usuario lo pida. Sin confirmar: `chr+0xb0 +0x24` (si es > 0 reemplaza los cuatro
  materiales; no se encontró quién lo escribe, el mod lo toma como 0).
- La víctima del golpe `z` (`vt+0x88` = `FUN_140139540`) usa el material de armadura del pecho y del arma de
  guardia; para mobs de Minecraft sigue sin haber material DS2.
- **Suelo (201 `+0xf`, pisadas, 5500 con suelo)**: material DS2 del suelo; no hay equivalencia de bloques → no.
- **Voces (8000–8003, 9000–9003)**: dependen del sexo (`equip+0x2c`) y de un flag → no.
- **Golpes `z` (carne, armadura, guardia, parry)**: falta el material de la víctima (`vt+0x88`) y el sentido de
  `p5` / flags de `p4`; para mobs de Minecraft no hay material DS2 → no se reproduce. Los sonidos que la animación
  del atacante trae en su TAE (p. ej. el backstab) sí suenan.
- **Hoguera**: las animaciones del jugador (76010010…76010060) solo tienen sonidos de armadura (variantes). El fuego
  de la hoguera viene de objeto/SFX: la muestra `campfire` la usan los eventos `s000000111`, `139`, `140`, `157`,
  `3060`, `5423`, `5424`; cuál usa la hoguera (FXSoundParam de su `.ffx` u objeto) **no está trazado**. Encender con
  antorcha (`70070010/20`) tiene `c000007902` a 1.333 s, pero el mod no reproduce esa animación.
- Curva de distancia: FMOD usa una curva propia en `(distance)` (0..29 m) que no se decodificó; Minecraft atenúa
  lineal hasta `attenuation_distance`. Los eventos `c` son 2D en FMOD; en el mod son posicionales.
- Modo de reproducción de las definiciones (config `+0x00`: 0 / 3): sin decodificar; Minecraft elige por peso.
- Uniformidad de los rangos continuos de capa: se tomó uniforme entre el mínimo y máximo medidos.
- Enemigos (`frpg2_c1..c9.fev`, bancos `frpg2_c%04d.bnd`): no extraídos.


## 8. Sonido del objeto (almas, efigie, calavera; investigación del 2026-11-10)
Archivos en `D:\Reversing\DS2\out\itemsfx\` (`ffx.py`, `tree2.py`, `p38.py`, `a75.py`, `unbnd.py`, `sfxslot.c`,
`sfxsound.c`, `hostsound.c`, escuchas `.wav` / `.ogg`).
- **`ItemParam +0x08 / +0x0c / +0x10` son ids de FFX** (no modelos): `FUN_14032f750` los copia a
  `b8+0x6f0/0x6f4/0x6f8`; el TAE **220200** (`FUN_14016b840`) lanza el de la ranura `params[1]` en la dummy
  `params[0]` (`FUN_1404c7ca0` → `FXCGSfxCtrl`). Al terminar el evento solo se detiene la ranura 0.
- Los FFX están en `Game\sfx\sfx9999.ffxbnd.dcx` (BND4, entradas zlib, formato DLsE). **La acción 75 del FFX es un
  sonido**: su primer parámetro (tipo 68) es el id de un evento `s` (489 de 503 valores son eventos `s%09d` del FEV;
  código: `FXCGDrawEntityHostSound`, `FUN_140a38740` → `FUN_140bef820` `SoundID(0, id)` → `"%c%09d"` con `s`). El
  sonido sigue a la dummy.
- | Uso | TAE 220200 | Dummy | FFX | Evento |
  |---|---|---|---|---|
  | Almas (ItemType 550: 6062xxxx..6072xxxx, 50920000, 64xxxxxx), clip 180090 | ranura 1, **1.6333 s** | 20 | `+0x0c` = 1051..1072 | **`s000000274`** (vol. 1.0) |
  | Efigie humana 60151000, clip 180190 | ranura 1, **1.5 s** | 26 | 871 | **`s000000273`** (vol. 0.578) |
  | Efigie humana | ranura 2, **1.5 s** | 26 | 872 | **`s000000872`** (vol. 0.397, −5 semitonos, muestra `s000000305`) |
  | Calavera seductora: impacto (BulletParam 60530000 `+0xbc`) | — | bala | 845 | **`s000000845`** (`846_skull`) |
  | Calavera seductora: señuelo (BulletParam 60530010 `+0x00`, 5 s) | — | bala | 846 | **`s000000846`** (`846_skull_loop`) |
  Los demás FFX de esas ranuras (1050, 870, el vuelo 844, el Hueso de regreso 863) no tienen la acción 75.
- **En el mod**: `sounds.py mod` agrega esos eventos (`ITEM_FFX_SOUNDS` a las animaciones 180090 / 180190 de
  `attack_sounds.json`; `BULLET_FFX_SOUNDS` → `bulletSounds`). Las almas y la efigie suenan en el jugador a su tiempo;
  la calavera suena en el impacto y el bucle del señuelo se repite cada 1.144 s (largo de la muestra) mientras dura.
- **Sin confirmar**: que la acción 75 llegue exactamente a `FUN_140a38740` (vínculo por datos + el adaptador); en qué
  momento del FFX suena (se toma al crearse); qué son el `f32 1.0` y el `i32` de la acción (0 en el impacto, 1 en el
  señuelo: ¿bucle?); que FMOD repita `846_skull_loop` (se toma del nombre); los FFX "Append" de SotFS (+10000, sin
  acción 75); si un `sfx%04d.ffxbnd` de mapa redefine esos ids.
