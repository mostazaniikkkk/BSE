# Invasores NPC y fantasmas NPC (espíritus oscuros / blancos controlados por la IA)

Fuente: `DarkSoulsII.exe` (SotFS) en Ghidra headless (copia privada de solo lectura del proyecto), los params de la
regulation (`tools/re/params.py`), los `GeneratorRegistParam_*` de mapa y los ESD `ai8100xx` / `ai8200xx`
(`D:\Reversing\DS2\tools\esd.py`). Salidas y scripts fuera del repo, en `D:\Reversing\DS2\out\npcinv\`:
- `ai810000..ai820030.txt`: los 24 ESD de fantasma decodificados; `ai8200xx_map20.txt`: el cierre de llamadas del mapa 20
  (arma cuerpo a cuerpo sin escudo) de cada versión.
- `npc_all.txt` / `npc_gen.txt`: cada fila de `EnemyParam` con `+0x04 = 100`, su `NpcPlayerStatusParam`, nombre,
  equipo y generadores (`py/dump_npc.py`). `py/closure.py`: extrae el cierre de grupos de un mapa del ESD.
- `g1.c` … `pad2.c`, `h6000.c`, `cons.c`, `pr.c`, `f5050.c`: funciones citadas abajo.
- Textos: `D:\Reversing\DS2\extracted\npctext\` (`npcMenu.fmg` y `IngameSystem.fmg`, inglés y `NeutralSpanish`).

Lo ya documentado no se repite: formato EzState y órdenes del motor ([enemy_ai.md](enemy_ai.md)), `EnemyParam`
([enemies.md](enemies.md)), del botón al slot de ataque ([weapons.md](weapons.md)), stats del jugador ([stats.md](stats.md)).

## Resumen
- **Los invasores y fantasmas NPC son personajes de tipo jugador** (modelo c0001, clase `PlayerIns`) creados desde una
  fila de **`EnemyParam`** con `+0x04 = 100`. Su equipo, apariencia y stats salen de **`NpcPlayerStatusParam`**
  (`EP+0x10`). Su vida, estamina, defensa, almas y multiplicador de ataque son **fijos por fila de `EnemyParam`**.
- **No hay escalado por nivel.** Los 117 `NpcPlayerStatusParam` tienen los 11 stats en 99. La vida es la de la fila
  (y sus tablas de NG+), como cualquier enemigo.
- **La IA es una pareja de ESD genérica**, una para todos los rojos (`ai820010..820030` superior / `ai820000..820004`
  inferior, "闇霊汎用", espíritu oscuro genérico) y otra para todos los blancos (`ai810010..810024` / `ai810000..810004`,
  "白霊汎用"). Lo que cambia entre NPCs es la versión del ESD (base, DLC1..3, "DC") y **el tipo de arma que llevan en
  cada mano**, que elige el "mapa de estados" de pelea.
- **No usan la tabla de acciones de enemigo** (`5204` / `EnemyBehaviorParam`). Pulsan los **botones del jugador** con
  las órdenes 6000..6005 (`PlayerAi`, un mando virtual). Por eso usan el moveset del arma del jugador (slots de
  `WeaponActionCategoryParam`).
- `LogicComParam` y `LogicEzStateSharedParam` no diferencian a un NPC de otro: las filas 8100xx / 8200xx son iguales
  entre sí, y el único valor de dificultad que lee el ESD (`f5101(0)`) vale 1.0 en todas las filas.

## 1. Quiénes son y cómo se representan
### 1.1 Creación — `FUN_140355930` (también `FUN_1403560a0`)
- Id < 1000 → `ChrParam` (slot `+0x310`); 1000..999 999 → **`EnemyParam`** (slot `+0x350`).
- Si `id == 100` o **`EP+0x04 == 100`**, se crea un personaje de jugador (`FUN_14037ebe0`, objeto de 0x4a0 bytes) en
  una de las 6 ranuras de jugador (`param_1+0x2d0`, `FUN_14035b670`); si no, un enemigo normal (`FUN_1403114f0`).
- En ese caso, `EP+0x10` es la fila de **`NpcPlayerStatusParam`** (slot `+0x750`) y de **`NpcPlayerSkinParam`**
  (`+0x760`), que se aplican con `FUN_14035a3d0`.
- `EP+0x14` (byte) → tipo pasado al personaje: 0 → 1, 1 → 2, 2 → 4, otro → 3. **En los datos: 0 en todos los fantasmas
  blancos (ESD `ai810…`), 1 en todos los rojos (`ai820…`) y 2 en los mercaderes que pelean (`ai7xxxxx`).** Que el valor
  sea "blanco / rojo" sale de esa coincidencia; el lector del tipo no está trazado.
- El nombre del NPC es `npcMenu.fmg[EP+0x10]` (8975 = "Dennis el armero" / "Armorer Dennis"). El mensaje de invasión lo
  pone con `%s` (§4).
- Los slots de param salen del orden de la tabla de nombres del exe (`0x10f15c0`…): slot = `0x310 + 0x10·índice`. Se
  comprobó con los slots ya conocidos (`EnemyBehaviorParam` `+0x3a0`, `EnemyAttackMoveParam` `+0x720`).

### 1.2 `NpcPlayerStatusParam` (0xd4 bytes) — `FUN_14035a3d0`, `FUN_140154a30`
| Offset | Uso | Fuente |
|---|---|---|
| `+0x06`, `+0x07` | `FUN_1402039d0` / `FUN_140203e10` / `FUN_140202f10` (**qué son: sin confirmar**; 0 en los invasores) | `FUN_14035a3d0` |
| `+0x08..+0x12` | 11 stats (u8) → bloque de atributos (`FUN_14038aca0`). Orden de slot: `+8, +9, +0xb, +0xa, +0xc, +0xd, +0xe, +0xf, +0x10, +0x11, +0x12` → slots 0..10 (Vigor, Endurance, Vitality, Attunement, STR, DEX, INT, FTH, ADP…, stats.md) | ídem |
| `+0x14` | f32 × 0.01 (`DAT_1410acb08`) → `chr+0xb0 +0x30`. **Qué es: sin confirmar** (30 / 40 / 50 / 71 según el NPC) | ídem |
| `+0x1c..+0x40` | Objeto 0..9 (`FUN_14034e2d0`) | `FUN_140154a30` |
| `+0x44..+0x68` | Hechizo 0..9 (`FUN_14034e300`) | ídem |
| `+0x6c / +0x70 / +0x74` | **Mano derecha 1/2/3** (`FUN_14034e310(1/3/5)`) | ídem |
| `+0x78 / +0x7c / +0x80` | **Mano izquierda 1/2/3** (`FUN_14034e310(0/2/4)`; ranura 0 = L_hand1, classes.md) | ídem |
| `+0x84..+0x90` | Cabeza, torso, brazos, piernas (`FUN_14034e2e0(0..3)`) | ídem |
| `+0x94..+0xa0` | Anillos 0..3 (`FUN_14034e2f0`) | ídem |
| `+0xa4..+0xb0` | Flechas / virotes (`FUN_14034e2b0(0..3)`) | ídem |
| `+0xb4..+0xd0` | Gestos 0..7 (`FUN_14034e2c0`) | ídem |

- **Las 117 filas tienen `+0x08..+0x12` = 99** (todos los stats a 99).
- Las armas son casi siempre **filas de `WeaponParam` propias del NPC** (ids 7xxxxx / 8xxxxx, sin nombre). Su `+0x04`
  es el modelo del arma de jugador (887000 → modelo 2410 = Maza; 894000 → 1280 = Llama azul) y su `+0x0c` apunta a
  una `WeaponActionCategoryParam` propia. Comparadas con el arma de jugador del mismo modelo (887000 vs 2410000,
  741000 vs 1800000): **63 de 64 slots dan la misma animación** (`WeaponAttackMotionParam +0x00`); difiere el slot 51 y
  los floats del slot 20. Muchos llevan además 837000 (modelo 1210 = Espada corta) en la mano derecha 2 o 3.
- Las armaduras usan ids `1xxxxxxx` de `ArmorParam`; su nombre está en `itemname.fmg` con id + 10 000 000
  (11610100 → 21610100). **Es correspondencia de datos.**

### 1.3 Otros params
- `EnemyParam` de estas filas: `+0x00` = 1 (c0001), `+0x08` ECP 800000/800001/800002, `+0x0c` EMP 800000/800010,
  `+0x24` fila de `ChrMultiplayParam` (60 / 160 / 50 …), `+0x28` PV y `+0x140..+0x158` PV en NG+, `+0x2c` estamina,
  `+0xd0` almas, `+0xd8` multiplicador de ataque, `+0x118` poise, `+0x134` lote de drop (enemies.md §2).
- `NPCGenerateParam` (72 filas, slot `+0x5e0`) es otro sistema (equipo de NPCs de diálogo y de los `9xxx` de prueba);
  ninguna fila de invasor la usa.
- `EnemyDefaultLogicParam` 8100xx / 8200xx repite los ESD del generador (`+4` superior, `+8` inferior). Su lector sigue
  sin trazar (enemy_ai.md).
- `LogicComParam` 810010..810024 y 820010..820030: los 32 floats son **idénticos** dentro de cada color; solo cambian
  `+0x54` / `+0x58` (blanco 10 / 5, rojo 20 / 10) y `+0x44..+0x4c` en 820030. Lector sin trazar.
- `LogicEzStateSharedParam` 20000..20104 = 1.0 (todas): es lo que devuelve `f5101(0)`, el único multiplicador de tiempo
  que leen los ESD de fantasma (`20 · f5101(0)` s, `f1(1) · f5101(0)` s…). **En los datos no hay NPC más "difícil"
  por param.**

### 1.4 Lista: invasores rojos (generadores de mapa, `GeneratorRegistParam +0/+4/+8`)
`*` = arma propia del NPC (modelo del arma de jugador indicado). PV y almas en NG. AI = superior/inferior.

| EnemyParam | NPS | Nombre es_mx (EN) | PV | Est. | Almas | Ataque × | Derecha / Izquierda | AI |
|---|---|---|---|---|---|---|---|---|
| 814009 | 8140 | Melinda la Carnicera (Melinda the Butcher) | 1860 | 3260 | 4004 | 0.90 | Greataxe*, Shortsword* / — | 820024/820000 |
| 820000 | 8200 | Vorgel el Pecador (Vorgel the Sinner) | 1550 | 3260 | 3069 | 0.78 | Winged Spear*, Shortsword* / Hollow Soldier Shield | 820024/820000 |
| 821000 | 8210 | Despiadada Roenna (Merciless Roenna) | 1540 | 3260 | 6134 | 0.75 | Bone Scythe*, Shortsword* / Cursed Bone Shield | 820024/820000 |
| 822003 | 8220 | Peculiar Kindalur (Peculiar Kindalur) | 2280 | 3260 | 5334 | 1.03 | Black Witch's Staff*, Shortsword* / Cleric's Parma | 820024/820000 |
| 823000 | 8230 | Villard Diente de Dragón (Dragonfang Villard) | 2310 | 3260 | 9600 | 1.07 | Black Dragon Greatsword*, Shortsword* / Black Dragon Shield | 820024/835000 |
| 824000 | 8240 | Aslatiel de Mirrah (Aslatiel of Mirrah) | 2300 | 3260 | 8534 | 1.06 | Mirrah Greatsword*, Shortsword* / Mirrah Shield | 820024/820000 |
| 849000 | 8490 | Usurpador sin nombre (Nameless Usurper) | 2180 | 1740 | 5200 | 1.32 | Bandit's Knife*, Shortsword* / ?* | 820024/820000 |
| 849001 | 8490 | Usurpador sin nombre (Nameless Usurper) | 2290 | 1740 | 5600 | 1.35 | Bandit's Knife*, Shortsword* / ?* | 820024/820000 |
| 849200 | 8492 | Rhoy el Explorador (Rhoy the Explorer) | 1420 | 1740 | 6934 | 1.04 | Shotel*, Shortsword* / Heavy Crossbow | 820024/820000 |
| 853000 | 8530 | Arquero Guthry (Bowman Guthry) | 1730 | 1740 | 7483 | 1.17 | Avelyn*, Shortsword* / Avelyn* | 820024/820000 820024/820004 |
| 860000 | 8600 | Baldyr Escudo de Roca (Rockshield Baldyr) | 3620 | 1240 | 6040 | 1.11 | Winged Spear*, Shortsword* / Stone Parma | 820024/820001 |
| 861000 | 8610 | Bufón Thomas (Jester Thomas) | 4630 | 1740 | 6692 | 1.15 | Pyromancy Flame, Shortsword* / Pyromancy Flame | 820024/820001 |
| 862000 | 8620 | Rhoy el Explorador (Rhoy the Explorer) | 2540 | 1740 | 0 | 1.25 | Dragonslayer Greatbow*, Estoc*, Shortsword* / — | 820011/820001 |
| 863000 | 8630 | Stewart el Explorador (Stewart the Explorer) | 3310 | 2485 | 0 | 1.25 | Dragon Tooth*, Shortsword* / Havel's Greatshield | 820011/820001 |
| 864000 | 8640 | Courh la Exploradora (Courh the Explorer) | 3550 | 1740 | 0 | 1.25 | Berserker Blade*, Shortsword* / Berserker Blade*, Pyromancy Flame | 820011/820001 |
| 870000 | 8700 | Rachel Espada Rápida (Quicksword Rachel) | 3760 | 1240 | 5200 | 1.15 | Bastard Sword*, Shortsword* / Hollow Soldier Shield | 820024/820002 |
| 871000 | 8710 | Maldron el Asesino (Maldron the Assassin) | 4100 | 1240 | 4800 | 1.11 | Heide Greatlance*, Shortsword* / Rebel's Greatshield | 820024/820002 |
| 871100 | 8711 | Castañeteante Dan (Teeth-chattering Dan) | 4270 | 1240 | 2700 | 1.15 | Heide Greatlance*, Shortsword* / Rebel's Greatshield | 820013/820003 |
| 872000 | 8720 | Vástago Evangelista Waslo (Evangelist Scion Waslo) | 1400 | 1740 | 1750 | 1.25 | Chime of Want*, Shortsword* / Cleric's Parma | 820012/820002 |
| 872009 | 8720 | Vástago Evangelista Waslo (Evangelist Scion Waslo) | 1530 | 1740 | 2625 | 1.25 | Chime of Want*, Shortsword* / Cleric's Parma | 820012/820002 |
| 873000 | 8730 | Bravucón Matelas (Swashbuckling Matelas) | 1570 | 1740 | 1800 | 1.11 | Notched Whip*, Shortsword* / Crimson Parma | 820012/820002 820013/820003 |
| 877000 | 8770 | Bruja Desterrada Donna (Castaway Witch Donna) | 2320 | 1240 | 6000 | 1.15 | Staff of Amana*, Shortsword* / Grand Spirit Tree Shield | 820024/820003 |
| 878000 | 8780 | Brujo Nicholai (Hexer Nicholai) | 2590 | 1740 | 5700 | 1.15 | Chime of Want*, Shortsword* / Cleric's Small Shield | 820024/820003 |
| 879000 | 8790 | Caballero Sagrado Aurheim (Holy Knight Aurheim) | 2900 | 1240 | 4300 | 1.11 | Ruler's Sword*, Shortsword* / Mirrah Shield | 820024/820003 |
| 880000 | 8800 | Caballero Rondor (Drakeblood Knight Rondor) | 2040 | 1240 | 1400 | 1.15 | Drakeblood Greatsword*, Shortsword* / Spirit Tree Shield | 820011/820001 |
| 887000 | 8870 | Habitante Alcantarilla (Gutter Denizen) | 1690 | 1740 | 2600 | 1.00 | Black Witch's Staff* / — | 820024/820004 |
| 896000 | 8960 | Victor, niño del bosque (Woodland Child Victor) | 2360 | 1240 | 6400 | 0.77 | Giant Stone Axe* / Orma's Greatshield | 820024/820004 |
| 896500 | 8965 | Gully, niño del bosque (Woodland Child Gully) | 2360 | 1240 | 6400 | 0.77 | Reeve's Greatshield / Giant Stone Axe* | 820024/820004 |
| 897000 | 8970 | Sharron la esgrimista (Fencer Sharron) | 2760 | 1520 | 8800 | 0.83 | Spider Fang* / Puzzling Stone Sword* | 820025/820004 |
| 897001 | 8970 | Sharron la esgrimista (Fencer Sharron) | 3910 | 1520 | 12000 | 1.09 | Spider Fang* / Puzzling Stone Sword* | 820024/820004 |
| 897500 | 8975 | Dennis el armero (Armorer Dennis) | 1870 | 930 | 4800 | 0.72 | Blue Flame* / Grand Spirit Tree Shield | 820024/820004 |
| 897501 | 8975 | Dennis el armero (Armorer Dennis) | 2570 | 930 | 8800 | 0.83 | Blue Flame* / Grand Spirit Tree Shield | 820025/820004 |
| 898000 | 8980 | Oliver el coleccionista (Oliver the Collector) | 2870 | 1740 | 8000 | 0.85 | Santier's Spear*, Bone Fist*, Smelter Hammer* / Majestic Greatsword*, Puzzling Stone Sword*, Ricard's Rapier* | 820025/820004 |

Además: **Forlorn** (Abandonado; EP 694000..694009 y 695001..695008, NPS 6940 / 6950, Gran espada de los Abandonados*
900000 / 910000, PV 1680..4630, 16 000 almas, `ai820024` o `ai820025` o `ai820030` / `ai820004`). Los rojos sin
nombre (EP 831000..854000, 868100, 881000..888000) son invasores de evento/mob con `ai820010` o `ai820030`.
"Pate el apacible" (7440) es un NPC de diálogo: no tiene fila de invasor.

**Fantasmas blancos con nombre** (`EP+0x14 = 0`, `ai810010..810024` / `ai810000..810004`): Indomable Glencour (Masterless Glencour); Lobo solitario Schmidt (Lone Hunter Schmidt); Peregrina Bellclaire (Pilgrim Bellclaire); Felicia la Valiente (Felicia the Brave); Melinda la Carnicera (Melinda the Butcher); Bufón Thomas (Jester Thomas); Caballero de Ceniza Boyd (Ashen Knight Boyd); Abadesa Feeva (Abbess Feeva); Aflis el Arruinado (Ruined Aflis); Ellie Corazón de Acero (Steelheart Ellie); Trascendente Edde (Transcendent Edde); Rapaz Andrei (Rapacious Andrei); Carhillion del Redil (Carhillion of the Fold); Lorrie Voluntad de Hierro (Steel-willed Lorrie); Espadachín Errante Aidel (Drifter Swordsman Aidel); Espigada Shei (Twiggy Shei); Mercenario Luet (Sellsword Luet); Devota Scarlett (Devotee Scarlett); Cazadora O'Harrah (Manhunter O'Harrah); Ray el tímido (Bashful Ray); Bradley, vieja guardia (Bradley of the Old Guard).

## 2. La IA
### 2.1 Archivos y versiones
| Inferior | Nombre interno | Grupos | Superior que lo acompaña | Quién lo usa |
|---|---|---|---|---|
| `ai820000` | 下位：闇霊汎用 | 70 | `ai820010`, `ai820024` | rojos del juego base |
| `ai820001` | …（DLC） | 98 | `ai820011`, `ai820024` | DLC1 (m50_35) |
| `ai820002` | …（DLC2） | 105 | `ai820012/14/15/24` | DLC2 (m50_36) |
| `ai820003` | …（DLC3） | 125 | `ai820013`, `ai820024` | DLC3 (m50_37) |
| **`ai820004`** | **…（DC）** | **133** | **`ai820024` (DC), `ai820025` (DC: 帰還なし "sin volver"), `ai820030` (DC: MOB用)** | **los invasores añadidos en SotFS: Dennis, Sharron, Oliver, Forlorn, Guthry (m10_10), Victor/Gully…** |
| `ai810000..810004` | 下位：白霊汎用 (…DLC/DLC2/DLC3/DC) | 48..107 | `ai810010..810024` | fantasmas blancos |

### 2.2 Superior → mapa de pelea según el arma (`ai820024` grupo `2147483639`)
- **`f6008(mano)`** = `PlayerAi` vt `+0x1a8` = **`FUN_140125e90`**: clase del arma de la mano (0 izq., 1 der.;
  weapons.md "mano 0 = izquierda"). Lee una fila de 0x13c bytes del arma (`thunk_FUN_14014cbeb`):
  `[0]` o `[3]` ≠ 0 → **4**; `[2]` → **5**; `[1]` o `[0x53]` → **6**; `[0x36]` en 23..25 → **3**; en 26..28 → **2**;
  otro `[0x36]` ≠ 0 → **1**; sin arma → **0**.
  - En `WeaponTypeParam` (0x13c bytes, `WeaponParam +0x10`) esos bytes dan: bastón `[0]=[3]=1` → 4, llama de
    piromancia `[2]=1` → 5, campana `[1]=[0x53]=1` → 6, arco corto 23 / gran arco 24 / ballesta 25 → 3, parma 26 /
    escudo de cometa 27 → 2, espadas/mazas/garrote → 1. **Que la fila sea `WeaponTypeParam` sale de esa coincidencia.**
- Tabla (`r1 = f6008(1)` derecha, `r2 = f6008(0)` izquierda), orden del ESD:

| Derecha | Izquierda | Mapa | | Derecha | Izquierda | Mapa |
|---|---|---|---|---|---|---|
| 1 cuerpo a cuerpo | 2 escudo | 21 | | 3 arco | 2 escudo | 31 |
| 1 | 1 cuerpo a cuerpo | 22 | | 3 | 3 arco | 32 |
| 1 | 3 arco | 23 | | 3 | otro | 30 |
| 1 | otro (vacío…) | **20** | | 4/5/6 catalizador | 2 escudo | 41 |
| 0 nada | — | 10 | | 4/5/6 | otro | 40 |

  Con `70 < f5901() < 499` (salvo 200) no cambia de mapa (mapas especiales 100..202 que pone el evento).
- El superior manda la conducta con `5300(0, n)` (`f5900(0)`): 10 = pelear (cada mapa llama a su grupo de pelea),
  13 / 14 / 17 = otras conductas (grupos `2147483639/37/38` del inferior). La percepción y el paso a "pelear" están en
  `2147483640` (estados 6..19): pasa a pelear con `f3() ≥ 0.5` y `f7001() < 10`, o `≥ 1 s` y `< 20`, o `≥ 3 s` sin
  importar la distancia (si `f5001()` no está ocupado). **El resto del superior (búsqueda, `f5700`, `f7091`…) no está
  decodificado.**

### 2.3 Funciones del jugador-IA (`PlayerAi`, vtable `0x1410beb08`; `PlayerEzStateCtrl` `0x1410bea78`)
`PlayerEzStateCtrl` (`FUN_140124f20`) desvía 6000..6008 a su `+0xe8` (funciones, `FUN_140124fc0`) y a `+0xf0`
(órdenes, `FUN_140125740`). Las órdenes rellenan una petición en el `PlayerAi` que `FUN_1401261d0` pasa cada tick al
**mando virtual** `+0xa80`:

| Id | Qué hace | Fuente |
|---|---|---|
| **`cmd 6000(mano, tipo, comprobar)`** | **pulsa ataque** con esa mano: `FUN_1401fc0b0` → `FUN_1401fc830` elige el tipo de acción de weapons.md: **tipo 0 → ligero** (1, o 2 en combo, 5 corriendo, 6 tras rodar, 8 cayendo, 0xb a dos manos con la otra mano…); **1 → fuerte** (3 / 4 en combo, 0xc, 0xf, 0x14, 0x15…); **2 → patada** (0x10, 0x11 a dos manos); **3 → salto** (9). Con `comprobar ≠ 0` mira antes la navmesh (`FUN_14042dfa0`) | `FUN_140125740`, `FUN_1401fc0b0`, `FUN_1401fc830` |
| `cmd 6001(i, v)` | `pad+0x60+i = v` y bit `0xb+i` de `pad+0xd` (`FUN_1401fc260`). **Qué botón: sin confirmar** | ídem |
| `cmd 6002(mano, a, b)` | `FUN_1401fc4d0` (acción 1 o 3, `FUN_1401fac60` con la categoría 0xc). Se usa en los mapas de catalizador; **probablemente lanzar hechizo, sin confirmar** | ídem |
| **`cmd 6003(mano, dos)`** | **cambia de agarre**: `dos ≠ 0` → bit `(mano≠0)+1` de `pad+0xd` (tomar a dos manos esa arma); `dos = 0` → bit 3 (volver a una mano) | `FUN_1401fc3a0` |
| `cmd 6004(i)` | **usar el objeto** de la ranura `i` (`FUN_1401fc3e0`, ranuras `FUN_14034e2d0`) | ídem |
| `cmd 6005(i)` | **gesto** `i` (`FUN_1401fc2a0`, ranuras `FUN_14034e2c0`) | ídem |
| `cmd 5206(1, 0)` | bit 6 de `ChrAi+0x350` → botón 9 del mando (`FUN_1401fc610` caso 9: `pad+0x62 = 1`). Por el uso, **subir la guardia; sin confirmar** | enemy_ai.md §8.7, `FUN_1401261d0` |
| **`f6000(mano, pesado)`** | 1 si **ahora se puede atacar** con esa mano: `b8+0x298` (agarre) y `b8+0x7e6` (reacción) a 0 y el contador `CancelControl_Attack1L/1H/2L/2H` (`b8+0x568..+0x574`, animations.md) que toca a 0. Es la **ventana de combo** | `FUN_140125bd0` |
| `f6002(mano)` | 1 si en esa mano no hay arma o es catalizador / campana / llama | `FUN_140125d30` |
| `f6003(mano)` | `f6000(mano, 0)` | `FUN_140125d20` |
| **`f6004(mano, dos)`** | agarre actual (`handCtrl+0x10`, weapons.md): 2/5 → izquierda a dos manos, 3/6 → derecha a dos manos, resto → una mano. `f6004(1, 1)` = "la derecha va a dos manos", `f6004(1, 0)` = "va a una mano" | `FUN_140125d90` |
| `f6005()` / `f6006()` | `vt+0xd0(0xf)` / `chr+0xc0 +0x45` bit 1. **Sin confirmar** | `FUN_140125fe0`, `FUN_140125fc0` |
| `f6007(i)` | hay objeto en la ranura `i` | `FUN_140125f50` |

Estado del objetivo / propio: **`f5050(n)` (objetivo) y `f5054(n)` (uno mismo)** = `FUN_140426d00` (tabla en
`0x140426f6c`), con `b8 = chr+0xb8`:

| n | Lee | Significado | | n | Lee | Significado |
|---|---|---|---|---|---|---|
| **0** | `b8+0x7e4` | **en ventana `AttackDuration`** (el objetivo está atacando; casting.md, enemy_ai.md §8.5) | | 13 | `b8+0x298` | agarre / crítico en curso (weapons.md) |
| 1 | `b8+0x54c ≠ 0` | flag de estado 0x1f. **Sin confirmar** (el ESD lo usa para patear: ¿guardia?) | | 14 | `b8+0x28d` | `AttackMasterBind` |
| 3 | `b8+0x7e6` | en reacción de daño | | 15 | `b8+0x520` | flag 0x14, sin confirmar |
| 4 | velocidad > umbral y `FUN_14031c840` | moviéndose (sin confirmar) | | 17 | `c0+0x40` bit 1 | sin confirmar |
| 7 | `b8+0x299` | en el suelo (`DownDuration`) | | 19 | `b8+0x548` | flag 0x1e, sin confirmar |

- `f7005()` (`SearchEzStateCtrl` caso `0x1b5d`): como `f7002` pero con **el frente del objetivo** (`FUN_140431580`) en
  vez del propio: ángulo, en grados y en valor absoluto, entre el frente del objetivo y la dirección objetivo → yo.
  `f7005 ≥ 110` = estar a la espalda del objetivo. (Que `FUN_140431580` sea el frente del objetivo: por contraste con
  `f7002`, que usa `vt+0x240` propio; **sin confirmar** en el getter.)
- `cmd 1(i, a, b)`: `f1(i) = a + rnd % (b − a + 1)` (`FUN_140491430`, ambos incluidos). Los registros `f1` empiezan en 0
  (`FUN_1404908e0`).
- Movimiento: `5505` / `5535` / `5565` / `5405` / `5465` / `5221`. Por el uso: 5505 caminar, 5535 correr, 5565 rodear
  (strafe), `5221(1, dirs…)` rodar, `5221(0, 180, …)` paso atrás. **Sin confirmar en el motor** (enemy_ai.md §5.3).

### 2.4 Pelea con arma cuerpo a cuerpo y sin escudo — mapa 20 de `ai820004` (`ai820004_map20.txt`)
Es el caso del garrote solo. Grupos: pelea `2147483602`, tabla de ataques `2147483601`, ataque `2147483600`, rodada
`2147483598`, paso atrás `2147483614`, espalda `2147483607`, acercarse `2147483612`, reposicionarse `2147483595`.
Distancias en m (`f7001`), ángulos en grados (`f7002`), tiempos en s (`f3` = tiempo en el estado).

**Bucle de pelea `2147483602`**
1. **Agarre** (estado 2): cada **20 s** (`f2(2) ≥ 20·f5101(0)`, libre y sin los SpEffects 150000000 / 170100000 de
   `f5072` — lectura por uso) sortea `RandInt(1, 10)`: si va a una mano y sale ≥ 5 (**60 %**) → `6003(1, 1)` (toma la
   derecha a dos manos); si va a dos manos → `6003(1, 0)` (vuelve a una). Espera a `f6004` o 0.5 s.
2. **Acercarse** (estado 13): `f1(0) = RandInt(1, 100)` y `2147483612(1, 1)`: rodea (`5565`) `f4(0.5, 1)` s, luego
   corre (`5535`) mientras `d ≥ 2`, o camina (`5405`) hasta 3 s. Sale a atacar (estado 12) si:
   - `d < 5`, `|Δaltura| ≤ 1.5`, `f5080()` y `1 < f1(0) < 50` (**48 %** por entrada), o
   - `d < 2.5` y `|Δaltura| ≤ 1.5`.
3. **Esquivar el ataque del objetivo** (estados 12, 13 y 16): mientras el objetivo está en `AttackDuration`
   (`f5050(0)`) se sortea `f1(3) = RandInt(1, 100)` y al terminar se pone a 0. Si `1 < f1(3) < 5` (en el estado 16,
   `< 10`), `d < 4`, `f5050(0)`, `f5050(19)` y uno mismo libre (`f5003`, `f5001`, `f6005`, `f5072`, `f5061` a 0) →
   **rodada** `2147483598(0, 30, 330, …)` y, si vuelve 1, otra `2147483598(180, 115, 255, …)`. La misma rodada sale con
   `f5052(4, 0, 0)` en vez de `d < 4`/`f5050(0)`.
   - **Literalmente**, el sorteo solo ocurre con `f1(3) < 0` y el reinicio deja `f1(3) = 0`; como los registros
     empiezan en 0, **cuándo se vuelve a sortear no está claro: sin confirmar**. Si se sortea una vez por ataque del
     objetivo, la probabilidad es 3 / 100 (8 / 100 en el estado 16).
4. **Tabla de ataques** (estado 12 → `2147483601`); al volver: con 1 → estado 1; si no → estado 10: si está libre →
   **reposicionarse** `2147483595(2, 1, 1, 40, 3, 7, 0.5)`; si no, a los 1.5 s y con `f5003` → `5236`.
5. Con `f5202()` (**sin confirmar**; por el uso, "recibió daño") y sin `f5061` → estado 5: `RandInt(1, 100)`; `< 15` (**14 %**) → estado 8: cuando vuelve a
   estar libre, rodada `(0, 30, 330)`; espera hasta 5 s.

**Tabla de ataques `2147483601`** — `r0 = RandInt(0, 20)` (21 valores); se evalúa en este orden:

| # | Condición | Prob. | Acción (`2147483600(t0, t1, t2, alcance, seguidos, máx, ángulo, nav)`) |
|---|---|---|---|
| 1 | `f7005 ≥ 110` (a su espalda), `d < 2.5`, `r0 < 15`, `!f5003` | 15/21 | **intento de backstab** `2147483607`; si no logra el agarre (vuelve 1) → ligero ×2 `(0,0,0, 3, 1, 4, 90°, 1)` |
| 2 | `f5050(1)` y no `f5050(0)`, `r0 < 5` | 5/21 | **patada → ligero → ligero** `(2,0,0, 2, 3, 4, 60°)` |
| 3 | `d ≥ 4.5`, `r0 < 10` | 10/21 | **salto → ligero** `(3,0,0, 4.5, 1, 5.5, 60°)` |
| 4 | `r0 < 5` | 5/21 | **ligero → fuerte → ligero** `(0,1,0, 2.5, 2, 3.5, 60°)` |
| 5 | `r0 < 10` | 5/21 | **fuerte → fuerte → ligero** `(1,1,0, 2.5, 2, 3.5, 60°)` |
| 6 | `r0 < 15` | 5/21 | **ligero ×3** `(0,0,0, 2.5, 3, 3.5, 60°)` |
| 7 | resto (`r0` 15..20), libre | 6/21 | **rodada** `2147483598(0, 60, 300, −1, 1, 4, 0.5)`; si vuelve 0 → ir hacia él (gira hasta `ángulo < 45°` y `d < 3`, o camina 1.5 s hasta `< 20°` y `< 3`) → ligero ×2 `(0,0,0, 3, 1, 4, 90°)` |

- Después de las filas 2, 4, 5 y 6: `RandInt(1, 100)`; si `d < 3` y `< 25` (**24 %**), libre y sin `f5054(17)` →
  **paso atrás** `2147483614(0.3)` (`5221(0, 180, …)` y 0.3 s).
- Las probabilidades son las del orden de la cascada (una fila solo se mira si las anteriores fallan).

**Ataque `2147483600`** (los botones son `6000(1, t)`, mano derecha)
1. Libre (`!f5005`, `!f5001`) → suelta la guardia (`5206(0, 0)`).
2. Si está a `d < alcance`, `ángulo < ángulo` y `f6000(1, t0)` → pulsa `t0`.
   Si no: camina (`5505`) si está a menos de `alcance + 1.5` (`f7018`), si no corre (`5535`); se rinde a los 5 s.
3. **Encadenar**: pasa al siguiente botón cuando `f6000(1, t_siguiente)` (ventana de combo abierta), `f3() ≥ 0.5` y
   `seguidos ≥ 1` (segundo) / `≥ 2` (tercero). Corta si `d ≥ máx` o `ángulo ≥ ángulo`, o cuando `f5001()` deja de
   valer 1 tras 0.5 s.
- `seguidos` = cuántos botones más puede pulsar: 1 → 2 golpes, 2 o 3 → 3 golpes.

**Backstab `2147483607`**: rodea (`5565`) hasta 2 s o hasta que el objetivo entra en reacción (`f5050(3)`); luego se
pega (`5538` / `5508` / `5568`, más lento al acercarse) hasta `d < 1.4` y pulsa **ligero** `6000(1, 0, 0)` (el motor
lo convierte en backstab si la posición vale, weapons.md). Si el objetivo entra en agarre (`f5050(13)`) espera hasta
3.5–5 s; si no, sale a los 0.5–3 s.

**Reposicionarse `2147483595(2, 1, 1, 40, 3, 7, 0.5)`**: `f1(1) = RandInt(3, 7)` s.
- `f5212() > 1` → rodada `(0, …)`; `f5213() > 1` → rodada `(90, 270)` (o `(0)` si `d ≥ 3`). **`f5212` / `f5213`: sin
  confirmar.**
- Si no: `RandInt(1, 10) < 2` (**10 %**) → rodada hacia atrás `(180, 150, 210)`; las ramas de paso atrás (`< 1`) y de
  guardia (`< 1`) **no pueden salir con estos argumentos** (`RandInt(1, 10)` nunca es < 1).
- Con la guardia (estado 2, solo si se llega por otra ruta): `5206(1, 0)` y rodea (`5565`) `f1(1)` s; con `d ≥ 5`
  rodea más cerca y rueda de lado (90/270) con `RandInt(1, 100) < 40` cuando `f5052(10,4,0)`, `(8,3,0)`, `(6,2,0)` o
  `(4,1,0)`. **`f5052`: sin confirmar.**

**Rodada `2147483598(d1, d2, d3, d4, x, y, t)`**: `5221(1, d1..d4, 0, y, 0)` y espera `t` s (0.5). Se cancela si
`f5061()` o el objetivo con `f5050(17)`. Los `dN` son direcciones en grados (−1 = sin usar); **respecto a qué
(objetivo o cámara) y cómo elige entre ellas: sin confirmar.**

### 2.5 Comparación entre versiones y la "más alta"
Cierre del mapa 20 en cada inferior (`py/closure.py`):

| Inferior | Reacción al ataque del objetivo (`f5050(0)`) | Backstab (`f7005 ≥ 110`) | Tabla cuerpo a cuerpo | Notas |
|---|---|---|---|---|
| `ai820000` (base) | **no** (1 lectura, sin esquiva) | sí | otra (entra a atacar a `d < 5` con `RandInt(1,10) < 5`) | — |
| `ai820001..820003` | **sí**, 10 lecturas, sorteo `f1(3)` | sí | la de §2.4 | — |
| **`ai820004`** (DC) | **sí**, igual que DLC | sí, y además exige `!f5003` | la de §2.4 | la entrada directa a ligero ×2 comprueba la navmesh (`nav = 1`); es el más grande (133 grupos) |
| `ai810000`, `ai810004` (blancos) | no | sí | la corta | aliados |

- **Objetivamente**, los inferiores 820001..820004 son los únicos que **reaccionan al ataque del jugador** (esquiva
  disparada por la ventana `AttackDuration` del objetivo) y tienen la tabla de 7 filas con backstab, patada, salto y
  combos de 3. Entre ellos, `ai820004` es el más reciente (el de los invasores que añadió SotFS) y el más completo.
- No hay ninguna IA con tiempos de reacción más cortos por param: el sondeo es cada tick en los cuatro, y
  `f5101(0)` = 1.0 en todas.

## 3. Stats y escalado
- **DS2 no escala a los invasores por nivel.** Cada fila de `EnemyParam` fija PV (`+0x28`), PV de NG+1..7
  (`+0x140..+0x158`), estamina (`+0x2c`), defensas (`+0x9c..`), poise (`+0x118`), almas (`+0xd0`) y multiplicador de
  ataque (`+0xd8`). El mismo NPC tiene varias filas para distintos sitios (Dennis 897500: 1870 PV / 4800 almas;
  897501: 2570 / 8800).
- `NpcPlayerStatusParam` pone los 11 stats a 99 en todos (§1.2). **Si el personaje de tipo jugador calcula su vida
  con esos stats (stats.md, `FUN_14038e1e0`) o usa `EP+0x28`, no está trazado** (sin confirmar). Los datos de NG+
  sugieren `EP+0x28` (es la misma tabla por ciclo que la de los enemigos), pero falta el lector para este caso.
- Rangos de los rojos con nombre en NG: PV 1400..4630, estamina 930..3260, ataque ×0.67..×1.35, almas 0..16 000 (Rhoy 862000, Stewart y Courh, de DLC1, dan 0).
- **Para "nivel al azar 1..nivel del jugador" lo único que sale de DS2 es la fórmula del jugador**: SL = Σ stats(0..8)
  − 53 (stats.md) y las derivadas de `PhysicalStatsPerLevelStatValuesParam` (vida por Vigor, estamina por Endurance,
  carga por Vitality; ya portadas en `ds2/DerivedStats`). **Cómo reparte DS2 los puntos de un NPC entre los stats no
  existe** (todos son 99), ni hay tabla de equipo por nivel: eso habría que decidirlo (excepción a la regla 1).

## 4. Flujo de la invasión (breve)
- El NPC está **colocado por el mapa**: `GeneratorParam` → `GeneratorRegistParam` (`+0x00` EnemyParam, `+0x04` /
  `+0x08` ESD). **Qué evento lo activa (señal, entrada, distancia) está en los scripts de evento del mapa, que no se
  decodificaron**: sin confirmar.
- Textos del sistema (`IngameSystem.fmg`, es_mx):
  - 1210006 "¡Invadido por el espíritu oscuro %s!" (EN "Invaded by dark spirit %s!");
  - 1210001 "Conjuraste el espectro %s." (fantasma blanco);
  - 1221104 "El espíritu oscuro %s fue vencido." / 1221101 "El espectro %s fue vencido.";
  - del lado del fantasma: 1220001 "Te vencieron.\nRegresando a tu mundo...", 1221001 "El dueño del mundo %s
    murió.\nRegresando a tu mundo...", 1219001 "Regresando a tu mundo...".
  **Que el juego use estos ids también para los NPC: sin confirmar** (son los del multijugador; el `%s` coincide con
  el nombre de `npcMenu.fmg`).
- Recompensa: almas `EP+0xd0` y lote `EP+0x134` (`ItemLotParam2_Chr`; Dennis 89750000), como cualquier enemigo
  (enemies.md §5–6). Los blancos tienen 0 almas.
- Al morir el anfitrión / al terminar: los superiores `ai820024` y `ai820025` (帰還なし, "sin volver") se distinguen por
  una conducta de vuelta; **cuándo desaparece el NPC: sin confirmar** (evento de mapa).

## Sin confirmar / pendiente
- Lector de `EP+0x14` (blanco/rojo) y si la vida del NPC de tipo jugador sale de `EP+0x28` o de sus stats (99).
- `NpcPlayerStatusParam +0x06/+0x07/+0x14`; `NpcPlayerSkinParam` (4 campos, sin lector trazado).
- Que la fila que lee `f6008` / `f6002` sea `WeaponTypeParam` (coincidencia de bytes).
- `f5050(1)` (`b8+0x54c`), `f5050(19)` (`b8+0x548`), `f5052(a,b,c)`, `f5003`, `f5061`, `f5072(id)`, `f5080`, `f5202`,
  `f5212`, `f5213`, `f5643`, `f6005`, `f6006`, `cmd 6001`, `cmd 6002`, `cmd 5236`, `5231`, `5232`, `5230`.
- Las órdenes de movimiento (5505/5535/5565/5405/5221) y el marco de las direcciones de rodada.
- El re-sorteo de `f1(3)` en la esquiva (§2.4 punto 3).
- `5206(1, 0)` = guardia.
- El superior (búsqueda, `f5700`, `f7091`, `f5908`) más allá del paso a pelear.
- Los eventos de mapa que lanzan la invasión y la retirada.

## 5. Esquivas, estamina y backstab (2026-11-01; volcados `scratchpad\invader\` d1..d13.c, e1..e6.c)

### Esquivas: `5221` es la petición de rodar / paso atrás del jugador (`FUN_140122cd0`)
- Argumentos: 1 tipo (1 rodada, 0 paso atrás); 2..5 direcciones en grados (−1 = sin usar); 6 desfase (0); 7 distancia
  de sondeo (4 m rodada, 3 m paso atrás); 8 opcional (usar el frente propio). El `x` del grupo no se pasa.
- **La base es la dirección al objetivo** (horizontal; sin objetivo, el frente propio). Elige una al azar entre las
  válidas (xorshift global). La que falla el sondeo de navmesh (`FUN_14042dfa0` + `FUN_14042ddc0`) se descarta y se
  vuelve a sortear. Si no queda ninguna: `ctrl+0x94 = 1` (**f5620**).
- Vector = dirección × 1.0 (rodada) o × 0.3 (paso atrás) → pad virtual → bits de petición 5 / 9 (los del jugador) →
  `ChrDodgeCtrl` (`FUN_140328b10` / `FUN_140328c20`: `b8+0xb0` = ese vector).
  - **Misma animación, i-frames y coste que el jugador.**
  - La rodada gira hacia el vector (`FUN_1403851d0` → `FUN_14031c9b0`); el paso atrás va hacia atrás de su frente.
- **Distancia**: el largo del vector (1.0 / 0.3) queda por debajo del primer punto de la mezcla Short / Long, así que
  sale el clip corto (3.2 m / 0.5 m; el Blend2 recorta, `FUN_140636cc0`).
- **f5202** = "le pegaron" (`ChrAi+0x375`, `FUN_140426fc0`); no se levanta durante la rodada (`5230(0, 6)`).
  **f5003** = esquiva pendiente o en curso. **f5061** ≈ locomoción especial (escalera). f5212 / f5213: daño de
  fuentes sin identificar.
- **f1(3)**: el sorteo exige `f1(3) < 0` y nada lo pone negativo, así que **la esquiva ante el ataque del objetivo no
  ocurre**. Es la lectura literal del bytecode; el evaluador de DS2 no se localizó.

### Estamina
- Misma lógica que el jugador, con los números de su fila: máximo `EP+0x2c`, regeneración `EP+0x30` (125/s),
  agotamiento ECP 800000..2 `+0x10` (4.5 s), coste de esquiva igual (300 / 210 × 1.25).
- **La IA nunca mira la estamina**: ataca y rueda con 0.
- La vida es `EP+0x28` (resuelve §3: los NPC no usan sus stats).
- **Decisión del usuario**: el invasor del mod usa la estamina del jugador por su Aguante (como la vida), con las
  reglas del jugador. No bloquea acciones (como en DS2); sin estamina, sus golpes pierden poder de ataque (k1,
  damage.md).

### Backstab
- El mismo test que el jugador (`FUN_140393a30` / `FUN_140390a50`; cadena ofuscada, sin comprobación del tipo de
  atacante encontrada): límites del ChrCommonParam 100 de la víctima, víctima tipo jugador, la misma animación de
  víctima.
- Diferencia: los NPC de DS2 hacen el daño con filas de EnemyDamageParam de sus armas propias. El invasor del mod lleva
  armas de jugador, así que usa la ranura 63 de su arma (×1.4). Qué constructor de golpe usa un NPC: sin trazar.

### En el mod (`SoulsInvader`, `SoulsAttack.npcBackstab`)
- Fila 7: rodada (0 / 60 / 300) antes de acercarse y hacer ligero ×2. Sin dirección libre, la fila termina.
- Tras las filas 4..6: 24 % de paso atrás (180) si está a menos de 3 m.
- Golpeado (sin rodar): 14 % de rodada (0 / 30 / 330) cuando queda libre, hasta 5 s.
- Fila de espalda: el primer ligero se vuelve backstab si pasa el test; si no, ligero ×2.
- El sondeo de navmesh se sustituye por "sin bloques en el camino y suelo al final". El empuje de la rodada usa el
  mismo reparto en el tiempo que el del jugador (provisional, ya listado).
- No portado: la reposición (f5212 / f5213 sin identificar), la fila de patada y la cancelación de la rodada por
  f5061 / f5050(17).
