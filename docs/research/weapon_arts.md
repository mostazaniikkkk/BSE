# "Weapon arts" en DS2: qué existe de verdad

Pregunta de QA: "DS2 tiene ataques especiales en algunas armas". Esta nota dice qué hay, sacado solo de DS2.
No se usa nada de las weapon arts de DS3.

**Fuentes**
- Exe: `DarkSoulsII.exe` (SotFS). Código ofuscado seguido con `D:\Reversing\DS2\out\py\deob.py` y `tools/re/disasm.py`.
- Regulation: `tools/re/params.py`, `tools/re/slots.py`.
- TAE: `c000100_pl.tae`. Animaciones: `c0001.nmb` / `.nsa`, leídos con `tools/re/weapons.py`.
- Nombres: `menu_text/itemname.txt`. SpEffects: `out/spfx/chainspell.py` (emevd).
- Scripts y salidas en `D:\Reversing\DS2\out\weaponarts\`:

  | Archivo | Qué hace |
  |---|---|
  | `group.py` | agrupa las armas por el contenido de unos slots → `g5051.txt` |
  | `special.py` | busca animaciones por nombre |
  | `wbullets.py` / `wb3.py` | filas de WAMP con balas, y su cadena bala → PDP → SpEffect → `wbv.txt`, `v3.txt` |
  | `detail.py` | tiempos, estamina y PDP de cada slot especial → `detail.txt` |
  | `scan18b.py` | lectores del tipo de movimiento `b8+0x18b` → `s18b.txt` |

## 0. Conclusión
**DS2 no tiene "weapon arts" en el sentido de DS3.** No hay posturas que se mantienen, ataques cargados ni una
acción especial genérica por clase de arma.
- Los tipos de movimiento que usan las filas de WAMP son 0, 1, 2, 4, 5, 7–13, 15, 16 y 17 (§1.2). Ninguno es una
  postura ni una carga.
- `c0001.nmb` no tiene ninguna animación de ataque con `Charge`, `Hold` o `Stance` en el nombre. Las únicas con
  `Stance` son las poses de agarre (weapons.md).
- "Stance" en los nombres de DS2 = la pose de agarre (`AttackStance_*`, weapons.md), no un ataque.

Lo que sí hay, y es lo que QA llama "ataques especiales":
1. **Botones de la otra mano con el arma a dos manos** (L1 / L2 → tipos de acción 0x0b / 0x0c → slots 50 / 51).
   - Casi todas las clases ponen ahí **guardia + parry**.
   - Los escudos ponen golpes de escudo, los arcos y ballestas el apuntado, los prismáticos el zoom.
   - **14 armas únicas** ponen un ataque o un **buff propio** en L2 (§3).
2. **Atacar sin bajar la guardia** (tipos 0x12 / 0x13 → slots 57 / 58, tipos de movimiento 4 / 5). Solo lanzas,
   parte de las alabardas y los estoques. **La Spear del mod lo tiene** (§2).
3. **Armas únicas con balas o SpEffects en sus ataques normales**, por los campos `WAMP +0x38..+0x4c` (§4).
   Ejemplos: Moonlight Greatsword, Channeler's Trident, Ice Rapier, Spitfire Spear.
4. Ataques propios en powerstance (slot 54, Warped Sword / Sorcerer's Twinblade). Ya en [powerstance.md](powerstance.md).

## 1. Mecánica común

### 1.1 Del botón al slot (repaso + lo nuevo)
- `FUN_140396680` da el tipo de acción y `FUN_140397514` el slot ([weapons.md](weapons.md)). Los tipos 0x0b..0x18
  van a los slots 50..63 en cualquier agarre: tabla `0x1403977b8`, 16 entradas; la 14ª y la 15ª apuntan al mismo
  caso, así que 0x19 → 63.

  | Tipo | Slot | Cuándo (`FUN_140396680`) |
  |---|---|---|
  | 0x0b / 0x0c | 50 / 51 | ligero / fuerte de la mano libre con la otra arma a dos manos (weapons.md) |
  | 0x0d / 0x0e / 0x0f | 52 / 53 / 54 | powerstance (powerstance.md) |
  | 0x10 / 0x11 | 55 / 56 | patada |
  | **0x12 / 0x13** | **57 / 58** | **ligero con la otra mano en guardia** / su combo (§2) |
  | **0x14 / 0x15** | **59 / 60** | **fuerte con la otra mano en guardia** / su combo. **Vacíos en todas las armas**: el fuerte con guardia cae al fuerte normal (3) |
  | 0x16 / 0x17 / 0x18 | 61 / 62 / 63 | riposte / estocada de rotura de guardia / backstab |

- **Slot vacío = id de WAMP 1.** `thunk_FUN_141ca0bab` / `FUN_140397530` lo devuelven, y `FUN_140396680` pasa al
  siguiente candidato.

### 1.2 Qué hace cada tipo de movimiento (`WAMP +0x04`): switch de `FUN_140393c00`
Tabla de salto en `0x14039475c`, leída del exe; 18 entradas, y los tipos > 0x11 van al caso `0x1403946f8`. Amplía la
tabla de [bow.md](bow.md) §1.5.

| Tipo | Caso | Qué es | Uso en las armas (filas de categoría) |
|---|---|---|---|
| 0 | `0x140394729` (salida) | nada | slots vacíos |
| 1–7 | `0x140393f7b` | ataque normal (`FUN_140392b20` golpes, `FUN_140392a60` balas, `FUN_140392920`…) | 1: 1958, 2: 15081, 4: 20, 5: 62, 7: 4287 |
| 8–11 | `0x1403942eb` | disparo de arco / ballesta | |
| 12 | `0x140394148` | hechizo | |
| 13 | `0x140394261` | guardia (`FUN_14038fd30` decide si puede empezar) | 2957 |
| 14 | `0x1403946f8` | arma un texto y lo descarta (`FUN_14038ee80` / `FUN_140029690`): nada | sin uso |
| 15 | salida | nada | arcos a una mano |
| 16 | `0x14039450e` | solo pide la cámara de apuntado | arcos y ballestas, slots 50 / 51 |
| 17 | `0x14039454b` | apuntado + estado de animación | prismáticos (`FistAttack1HandFieldGlasses_R`) |

- **No hay ningún caso de buff, postura ni carga.** Los "especiales" de DS2 son ataques normales (1–7) con una
  animación propia, a veces con balas que aplican un SpEffect (§3, §4).
- Diferencias entre los tipos 1–7:
  - 4 y 5 dejan seguir en guardia (§2.2);
  - 7 es el de salto y caída ([weapons.md](weapons.md));
  - 1 frente a 2: **sin confirmar**. El switch los trata igual.

## 2. Atacar sin bajar la guardia (slots 57 / 58)

### 2.1 Entrada (`FUN_140396680`, ramas desensambladas en `0x1403968fd..0x1403969be`)
- Rama ligera, después de la patada. Orden de prueba:
  1. combo 2 (`FUN_1401f9d50(ctx, 1)`);
  2. **0x13**, si el ataque actual es 0x12 dentro de su `AttackComboControl` (`FUN_1401f9d50(ctx, 0x12)`);
  3. **0x12**, si `FUN_1401f9f00(ctx)`;
  4. powerstance 0x0d / 0x0e;
  5. 1.
- Rama fuerte: `FUN_1401f9f00` → 0x14, y el combo 0x14 → 0x15 (`0x140396a47`). Sus slots están vacíos en todas las
  armas.
- **`FUN_1401f9f00`** (`0x1401f9f00`, leído entero):

  ```c
  ctx+0x18 == 1  &&  ((b8+0x14 == 2 && ctx+0x1a == 1) || (b8+0x14 == 1 && ctx+0x1a == 0))
  ```

  - `ctx` lo arma `FUN_1401f9ca0`. `ctx+0x18` = estado de las manos (`FUN_14034e6f0()+0x10`): 1 = las dos a una mano.
    `ctx+0x1a` = mano del botón.
  - `b8+0x14` = **mano en guardia: 1 = derecha, 2 = izquierda, 0 = sin guardia (confirmado)**.
    - El caso de guardia (`0x140394276`, deobfuscado) escribe `ctrl+0x24 = 1` si la mano de la petición (`desc+0x00`)
      es 1 y `= 2` si es 0.
    - `0x1403931ce` lo copia: `b8+0x14 = ctrl+0x24`, `b8+0x264 = ctrl+0x2c`, `b8+0x268 = ctrl+0x30`,
      `b8+0x26c = ctrl+0x34`. Después pone `ctrl+0x24 = 0`.
    - Por eso la guardia dura lo que dure la petición de cada frame.
    - Mano 0 = izquierda y 1 = derecha (weapons.md, "Estado de las manos").
  - `ctx+0x18 == 1` excluye el estado 4 (dual: un par que la powerstance acepta) y los de dos manos. Con lanza +
    escudo el estado es 1.
- **Qué botón da cada tipo**:
  - **0x12** (slot 57): el ligero de cualquier mano mientras la **otra** mano está en guardia. Botón de la derecha
    con `b8+0x14 == 2`, o de la izquierda con `== 1`.
  - **0x13** (slot 58) **no es la otra mano: es el combo.** Sale con el ligero de la misma mano mientras el ataque
    actual es 0x12 y su `AttackComboControl` está abierto.
    - `FUN_1401f9d50(ctx, 0x12)`, leído entero: `b8+0x4d4 ≠ 0 && b8+0x188 == ctx+0x1a && b8+0x18a == 0x12`.
    - **No vuelve a pedir la guardia.**
  - Tras 0x13, el siguiente ligero no cumple ninguna de las dos pruebas de combo, así que vuelve a 0x12 si hay
    guardia, o a 1 si no la hay.
  - Las dos usan la fila de categoría del arma que ataca, sea de la mano que sea.
- En resumen: con **las dos armas a una mano**, un ligero de una mano **mientras la otra mano está en guardia**
  usa el slot 57 del arma que ataca. Lo normal es R1 con el escudo en L1.

### 2.2 La guardia sigue durante el ataque
- `FUN_14038fd30` es la condición para empezar o mantener la guardia; la llama el caso 13 en `0x140394269`.
  Deobfuscado:

  ```c
  if (b8+0x10 != 0 && !(b8+0x18b - 4 <= 1)) return 0;
  ```

  Con una acción en curso, la guardia solo vale si el tipo de movimiento actual es 4 o 5.
- La otra lectura de `b8+0x18b ∈ {4, 5}` (`0x141aff3db`, update de la guardia) salta la rama que la cierra
  (`s18b.txt`).
- Por eso los slots 57 / 58 usan los tipos 4 / 5: el arma ataca y la guardia del escudo sigue activa.
  **Diferencia entre 4 y 5: sin confirmar.**

### 2.3 Quién lo tiene (`group.py 57,58,59,60`)

| Tipo | Armas |
|---|---|
| **5** (28 + 3 DLC) | **Spear (mod)**, Winged Spear, Pike, Partizan, Stone Soldier Spear, Spitfire Spear, Silverblack Spear, Heide Spear, Pate's Spear, Channeler's Trident, Heide Lance, Heide Greatlance, Grand Lance, Chariot Lance, Rampart Golem Lance, Lucerne, Halberd, Mastodon Halberd, Blue Knight's Halberd, Dragonrider's Halberd, Black Knight Halberd, Syan's Halberd, Roaring Halberd, Wrathful Axe, Pilgrim's Spontoon, Yorgh's Spear, Gargoyle Bident, Dragonslayer Spear |
| **4** (11) | Estoc, Mail Breaker, Chaos Rapier, Spider's Silk, Espada Ropera, Rapier, Black Scorpion Stinger, Ricard's Rapier, Ice Rapier |

El resto (309 categorías) tiene los slots 57–60 vacíos. Las alabardas sin el ataque son Helix Halberd, Santier's,
Old Knight Halberd y Old Knight Pike. Con esas armas, atacar con la guardia puesta da el ataque normal, y la
guardia cae porque `FUN_14038fd30` falla con el tipo 2.

### 2.4 Datos (`detail.txt`)
Todas usan la animación **`a25_01_0011` `SpearAttack1HandLight1_R_Hit_01`** (TAE 25010011), también los estoques.

| | Spear 2800000, slot 57 | Spear, slot 58 (combo) | Rapier 1500000, slot 57 | Halberd 3220000, slot 57 |
|---|---|---|---|---|
| WAMP / tipo | 10017910 / 5 | 10017920 / 5 | 10007710 / 4 | 10060710 / 5 |
| Golpe (TAE 2200, slot 0) | 0.5–0.6 s | igual | igual | igual |
| PDP: tasa `+0x1c` / piso `+0x18` / daño a estamina `+0x24` | 0.672 / 70 / 320 | 0.672 / 70 / 320 | 0.906 / 70 / 245 | 0.432 / 70 / 545 |
| Estamina (`FUN_14038fb10`) | 274.2 | 303.0 | 247.0 | 389.0 |

Comunes a todas:
- duración 2.0 s;
- `CancelControl_Avoid` 0.2667–1.1, `Move` hasta 1.1667;
- `Attack1L` 1.0, `Attack1H` 1.0667, `Attack2L` 1.0, `Attack2H` 1.0667;
- `AttackComboControl` 0–1.2063;
- sin regen (TAE 101100) de −0.0667 a 1.5 s.

El TAE 2300 a 0.5333 s pide la bala del slot 0, pero estas filas no tienen bala (`WAMP +0x38` = 0): no sale nada.

## 3. Botones de la otra mano a dos manos (slots 50 / 51)

### 3.1 Grupos (`g5051.txt`, armas base de `WeaponParam`; tipo:animación)

| # | L1 (50) | L2 (51) | Clases / armas |
|---|---|---|---|
| 66 | 13 `SpearGuard2Hand_R` | 13 `SpearGuard2Hand_R` | lanzas, lanzas de caballería, alabardas, guadañas, **bastones** (Sorcerer's Staff…), **twinblades** (salvo la Sorcerer's), Drakekeeper's Warpick, Archdrake Mace. **Las dos teclas son guardia** |
| 57 | 13 `LargeAxeGuard2Hand_R` | 2 `LargestSwordAttack2HandParry_R` | greatswords, **ultra greatswords**, **curved greatswords** (Murakumo, Arced Sword, Curved Dragon), greataxes, great hammers, **Gyrm Axe (mod)** |
| 57 | 13 `StraightSwordGuard2Hand_R` | 2 `MiddleShieldAttack1HandParry_R` | espadas rectas, **katanas**, hachas, mazas, **látigos**. Mod: espadas, hachas, Mace, Club |
| 30 | 2 `MiddleShieldAttack2HandLight2_R_Hit` | 2 `MiddleShieldAttack2HandHeavy1_R_Hit` | escudos medianos (**Drangleic, mod**): golpes de escudo |
| 25 | 13 `StraightSwordGuard2Hand_R` | 2 `KnifeAttack1HandParry_L` | dagas, **estoques** |
| 21 | 2 `MiddleShieldAttack2HandLight2_R_Hit` | 2 `FistAttack2HandLight1_R_Hit` | escudos pequeños / parmas |
| 18 | 16 | 16 | arcos, ballestas, **Avelyn**: solo apuntar (bow.md) |
| 17 | 2 `LargeShieldAttack2HandLight1_R_Hit` | 2 `LargeShieldAttack2HandHeavy1_R_Hit` | grandes escudos |
| 16 | 13 | 13 `StraightSwordGuard2Hand_R` | campanas, llamas, antorcha 6000000 |
| 11 | 13 | 2 `SmallShieldAttack1HandParry_R` | puños (**Fists, mod**), garras, Caestus |
| 10 | 13 | 2 `ScimitarAttack1HandParry_R` | espadas curvas |
| 2 | 17 | 17 | Binoculars |
| 1 | 13 `StraightSwordGuard2Hand_R` | 2 `LargestSwordAttack2HandParry_R` | **Pickaxe (mod)** |
| 1 | 13 `LargeAxeGuard2Hand_R` | 2 `ScimitarAttack1HandParry_R` | Shotel |
| 1 | 13 `SpearGuard2Hand_R` | 2 `MiddleShieldAttack1HandParry_R` | Black Knight Greataxe |

- Las animaciones `…Parry…` llevan el **evento TAE 1220** (ventana de parry, weapons.md):
  - Longsword y Battle Axe: 0.1667–0.4 s de 1.5 s;
  - la de UGS: 0.3333–0.6667 s de 2.333 s.

  Son el parry de DS2, no un ataque especial.
- **El "parry" de UGS a veces golpea.**
  - Zweihander y Pickaxe: WAMP 10046000, PDP con tasa 0.84, piso 70 y daño a estamina 955, golpe 0.4667–0.8 s.
  - Claymore (10042100) y Gyrm Axe (10060100): tasa 0, sin daño.
  - Es decir, el golpe sale del PDP de cada arma.

### 3.2 Armas únicas con su propio L2 (slot 51) o 2H fuerte (slot 12)

| Arma (id) | Slot | Animación (TAE) | Qué hace (WAMP +0x38.. → bala → PDP → SpEffect) | Estamina | Duración / bloqueos |
|---|---|---|---|---|---|
| **Defender Greatsword** 1960000 | 51 | `LargeSwordAttack2HandSpecial1_R_Hit` 31030037 | **Buff propio.** El TAE 2300 a 0.667 s lanza la bala 1960010, una esfera r 0.3 en la dummy 249, PDP `+0x40 = 0x04` (solo el usuario). Aplica el SpEffect **1960000**: `100080[01] (45, 2, 50)` + `100090[02] (45, a=2, b=70)` + SFX | 460.5 | 4.0 s; Avoid 0.533–2.588; Move 2.82 |
| **Watcher Greatsword** 1970000 | 51 | igual | Igual, con la bala 1970010 → SpEffect **1970000**: `100080[01] (45, 1, 70)` + `100090[02] (45, a=1, b=10)` | 376.9 | igual |
| **Possessed Armor Sword** 1260000 | 51 | igual | Bala 20229000 (0x06, solo el usuario) → SpEffect **1260010**: `100090[02] (25, a=3, b=50)` + SFX de arma | 351.4 | igual |
| **Bewitched Alonne Sword** 1790000 | 51 | `KatanaAttack2HandSpecial2_R_Hit` 28030038 | Golpe propio (PDP 20088900: tasa 1.382, 0.8–0.967 s). Bala 1 (0–0.333 s) → SpEffect 1790015 (SFX de arma + `100700[04] (3)`). Bala 2 (0.8 s) → cadena 20088911 → 19 → 20 → SpEffect **1790020**: `100120[00] (20088921…)`, `1000[15] (1, 1.6, 1.6, 30)`, SFX | 422.3 | 4.0 s; Avoid 0.518–3.167; Move 3.33 |
| **Wrathful Axe** 3370000 | 51 | `LargeSwordAttack2HandSpecial2_R_Hit` 31030038 | Tres balas: 20123000 (0.333 s), 20123001 (1.167 s), y 20123005 (1.733 s): esfera r 1.0 con daño, piso 70, tasa 1.2, filtro 0xb0 | 456.3 | 3.0 s; Avoid 0.4–2.5 |
| **Sacred Chime Hammer** 2750000 | 12 (2H R2) | la misma 31030038 | Bala 20039900 disparada (`+0x05 = 1`, v0 0.5, vida 5 s, piso 31, tasa 0.42, dummy 200) + 20039901 | 472.5 | 3.0 s |
| **Channeler's Trident** 2890000 | 12 (2H R2) | `SpearAttack2HandSpecial2_R_Hit` 25030038 | La "danza". Bala 1990000 a 0.333 s, sin efecto. A 4.667 s, la bala 1990010: esfera **r 3.0**, filtro **0x0c (usuario + aliados)** → SpEffect **1990010**: `1000[01] (25, 20, 1.05)` + SFX | 449.8 | **6.9 s**; Avoid 0.92–6.33 |
| **Sorcerer's Twinblade** 5370000 | 51 | `DoubleEdgeAttack2HandSpecial1_R_Hit` 24030037 | La misma animación que el 2H R2 normal de las twinblades (slot 12). Golpe con PDP 20093800: tasa 1.44, 0.767–1.0 s | 410.9 | 2.833 s |
| Smelter Hammer 2960000 | 51 | `a26_03_0031` (`LanceAttack2HandHeavy1_R_Hit_01`) | Cinco golpes; PDP 20091610 / 11 / 12, tasas 0.36 / 0.189 / 1.21 | 610.7 | 5.33 s |
| Curved Nil Greatsword 5050000 | 51 | `LargeSwordAttack2HandDash_R_Hit` 31030210 (tipo 1) | ataque normal (tasa 0.699) | 515.8 | 2.33 s |
| Fume Ultra Greatsword 5250000 | 51 | `LargestSwordAttack2HandHeavy1_R_Hit_02` 32030032 (tipo 1) | ataque (tasa 1.512, golpe a 2.0–2.3 s) | 500.2 | 4.33 s |
| Majestic Greatsword (variante 1831000) | 51 | `LargeSwordAttack2HandSpecial3_R_Hit` 31030039 (**tipo 7**) | ataque (tasa 1.512) | 830.0 | 3.83 s |
| Bow of Want 4280000 | 51 | 40030030 (sin nodo en c0001) | dispara la bala 20071900 (v0 35, piso 70, tasa 1.2) | 250.8 | — |
| Sanctum Crossbow / Repeating 4670000 / 4680000 | 51 | `CrossbowShoot2HandLight1_R_Prepare` / `_01` | bala 20090100 (piso 255, tasa 7.2) / 20090000 (piso 130, tasa 1.85) | 751.1 / — | sin pistas Avoid / Move |
| Sanctum Shield 11150000 | 51 | `MiddleShieldAttack2HandLight1` | otro golpe de escudo | 289.1 | 2.33 s |

**De dónde sale cada columna**
- **Balas**: `FUN_140392a60`, deobfuscado. Lo llama el caso de ataque normal en `0x140393ff6`.
  - Si `desc+0x60` (fila de WAMP) ≠ 0 y `desc+0x29` = `desc+0x2a` = 0, recorre i = 0..2:
    `FUN_140391bf0(ctx, desc, base + i, WAMP[+0x38 + 8i], WAMP[+0x3c + 8i], 1)`.
    Cada par es **(bala, PDP)** de la ranura de balas `base + i`.
  - Si `WAMP+0x38` ≠ 0, también lo guarda en `ctx+0x14 + mano·4`.
  - El evento TAE **2300** `param[0]` = esa ranura (casting.md §2) la lanza.
  - Los datos cuadran con eso: Bewitched trae balas solo en las ranuras 1 y 2, y su TAE tiene 2300 con `param` 1 y 2.
- **Lectura de los SpEffects** (speffects.md §8 y §11):
  - `100090[02] (t, a, b)` = **+b de ataque** del tipo `a` durante t s. Solo cuenta si el reparto `M[a] > 0`.
  - `100080[01] (t, casilla, v)` = **+0.01·v al reparto** de esa casilla.
  - Defender: +0.50 de reparto en la casilla 2 y +70 de ataque del tipo 2, 45 s.
  - Watcher: +0.70 en la casilla 1 y +10 de ataque del tipo 1, 45 s.
  - Possessed Armor: +50 de ataque del tipo 3 durante 25 s, sin entrada de reparto: solo suma si el arma ya tiene
    reparto de ese tipo.
  - **Qué elemento es cada índice de `a`** (0 físico, 1 magia, 2 rayo, 3 fuego…): sin confirmar por código
    (speffects.md §8).
- **Estamina**: `FUN_14038fb10` (tipos 1–7: `WeaponParam +0x34` × `WeaponStaminaCostParam[slot]`). **No se cobra en
  el mod** (regla 2, detrás del flag).

### 3.3 Lo que pedía la lista de QA, comprobado

| Supuesto | Qué dicen los datos |
|---|---|
| Postura / ataque cargado de UGS, curved greatswords | **No existe.** L1 guardia y L2 parry (`LargestSwordAttack2HandParry`; en algunas golpea, §3.1). Excepciones: Fume UGS y Curved Nil, que en L2 tienen un ataque más |
| Lanzas | L1 y L2 = guardia. Lo propio es **atacar sin bajar la guardia** (§2) |
| Katanas | Guardia + parry (grupo de espadas rectas). Solo la Bewitched Alonne tiene L2 propio. Blacksteel, Berserker y Bewitched cambian el 2H R2 por `KatanaAttack2HandSpecial1` (slot 12/13), que es un ataque normal |
| Twinblades | L1 y L2 = guardia. Su "especial" `DoubleEdgeAttack2HandSpecial1` es el **2H R2 normal** (slot 12). La Sorcerer's lo tiene además en L2 |
| Estoques | Guardia + parry de daga. Además, el ataque con guardia (tipo 4, §2) |
| Látigos | Guardia + parry, nada propio |
| Mastodon (Greatsword / Halberd / Greatshield) | **Nada propio**: su grupo estándar y sin balas |
| Triple disparo del Avelyn | No está en L1/L2 (tipo 16, apuntar). Es su **disparo normal**: todas sus filas usan el CF `_01` con tres TAE 2300 (crossbow.md) |
| Armas Black Knight | Nada propio. El Greataxe usa en L2 `MiddleShieldAttack1HandParry` en vez del parry de su clase |

## 4. Armas con balas o SpEffects en sus ataques normales (`WAMP +0x38..`, `wbv.txt`, `v3.txt`)
No usan ningún botón especial: el arma lanza algo en algunos de sus ataques normales.

| Arma | Slots | Qué lanza |
|---|---|---|
| Moonlight Greatsword (1871000) | 2, 3, 12, 13 | bala disparada (v0 30, vida 1 s; piso 70, tasa 1.0–1.2) + sub-bala r 1.5. El 2H R2 usa `LargestSwordAttack2HandSpecial3` |
| Ice Rapier 1580000 | 2, 3, 12, 13 | bala disparada (v0 15, vida 2 s; tasa 1.2 / 1.44) |
| Spider Fang 1670000 | 2, 3, 12, 13 | bala (v0 20) sin daño; aplica el SpEffect 1670010 |
| Spitfire Spear 2850000 | 2, 12, 54 | bala disparada (v0 15) con hija |
| Thorned Greatsword 1860000 | 2, 3, 12, 13 | bala disparada (v0 30; tasa 1.0 / 1.2) |
| Eleum Loyce 1630000 | 2, 12 | esfera con SpEffect 1630010 / 1630020 + golpe (tasa 0.96 / 1.152) |
| Iron King Hammer, Smelter Sword, Aged Smelter Sword, Curved Dragon, Drakewing UGS, Dragonslayer Spear, Ivory King UGS, Ivory Straight Sword, Bone Fist | varios | balas propias (detalle en `wbv.txt` / `v3.txt`) |
| Cleric's Parma, Magic Shield, Spirit Tree Shield, etc. | parry (2, 12) | una bala en la ranura 1 del parry (sin analizar) |

## 5. Armas del mod (`WEAPONS` de `tools/re/weapons.py`)

| Arma del mod | L1 / L2 a dos manos (50 / 51) | Ataque con guardia (57 / 58) | Otros |
|---|---|---|---|
| Longsword y espadas por material (Broken Straight, Puzzling Stone, Shortsword, Drakekeeper's, Red Rust) | guardia 13 + parry `MiddleShieldAttack1HandParry` | — | El R2 a una mano del Longsword es `StraightSwordAttack1HandSpecial2`, ya exportado como su fuerte normal |
| Hachas (Hand, Infantry, Bandit, Dragonslayer's Crescent, Battle) | guardia + parry | — | |
| Gyrm Axe | `LargeAxeGuard` + parry UGS (sin daño) | — | |
| Mace, Club | guardia + parry | — | |
| **Spear** | guardia + guardia | **sí, tipo 5** (§2.4) | |
| Pickaxe | guardia + **parry UGS con golpe** (tasa 0.84) | — | |
| Drangleic Shield | golpes de escudo (Light2 / Heavy1) | — | |
| Fists | guardia + parry de escudo pequeño | — | |
| Sorcerer's Staff | guardia + guardia | — | |
| Cleric's Chime, Pyromancy Flame, antorcha | guardia + guardia | — | |
| Short Bow, Light Crossbow | apuntar (16) | — | |

- **Ya portado**: los slots 50 / 51 (guardia, parry y golpes de escudo). Se exportan como `two_handed_other`
  (`weapons.py` → `Moveset.OTHER_LIGHT/OTHER_HEAVY`).
- **Lo único nuevo que aplica a armas que ya están**: el ataque de la Spear con la guardia puesta (slots 57 / 58).
  `weapons.py` no exporta esos slots.
- **Ningún arma del mod** tiene un L2 propio, un buff ni balas en sus ataques.

### 5.1 Clases que habría que añadir para mostrar algo propio (un arma por clase)

| Mecánica | Arma de ejemplo | Por qué esa |
|---|---|---|
| Buff propio en L2 (balas + SpEffect) | **Watcher Greatsword** 1970000 (o Defender 1960000) | el caso más limpio: una bala y un SpEffect de buff de arma, que usa el mismo motor que Magic Weapon (ya portado en speffects.md) |
| Ataque sin bajar la guardia, tipo 4 | **Rapier** 1500000 | la clase del estoque; mismo motor que la Spear |
| Buff de área para aliados | Channeler's Trident 2890000 | 2H R2, esfera r 3 con filtro de aliados |
| Ataque con balas en L2 | Wrathful Axe 3370000 | tres balas con daño |
| Balas en los ataques normales | Moonlight Greatsword 1871000 | proyectil en R2 |
| L2 propio sin balas | Bewitched Alonne Sword 1790000 / Sorcerer's Twinblade 5370000 | — |

## 6. Recomendación (orden)
1. **Spear: atacar con la guardia puesta** (slots 57 / 58).
   - Es el único "especial" que ya aplica a un arma del mod (tridente + escudo).
   - Todo está en los datos: animación `.nsa`, golpe, PDP, combo y bloqueos.
   - El motor ya tiene la guardia, el combo y el slot por mano: falta exportar los slots 57 / 58 y la condición de
     `FUN_1401f9f00` (las dos armas a una mano y la otra mano en guardia).
   - La guardia no se suelta (`FUN_14038fd30`, tipos 4 / 5).
   - La pose del escudo durante el ataque sale de la capa de guardia de `c0001.nmb` (§8), ya exportada.
2. **Watcher / Defender Greatsword**, si el usuario elige añadir un arma nueva (decisión suya).
   - Es el ejemplo más claro de "ataque especial de arma" de DS2.
   - Reusa piezas ya portadas: `SoulsBullet` y `SoulsSpEffects` con `100080[01]` / `100090[02]` (speffects.md §11).
3. El resto (Channeler's, Wrathful Axe, Moonlight…) después: cada una necesita su arma nueva y balas propias.

## 7. Sin confirmar
- **Tipo 4 frente a 5**: los dos mantienen la guardia; no se encontró otro lector que los distinga. Igual con 1
  frente a 2.
- Que la mano de la petición del caso de guardia sea `desc+0x00` (cuadra con la mano 1 → `ctrl+0x24 = 1`).
- Guardia en capa (§8):
  - que el CP de `SingleFrame` (0.95) sea una fracción de la duración (como en powerstance.md);
  - la capa aditiva de respiración del estado fijo (`0x2124`: `AdditiveStand` / `AdditiveBreathe`), que no se
    exporta;
- **El elemento de cada índice** de `100090[02]` / `100080[01]` (speffects.md §8).
- **Índice 25 de `1000[01]`** (Channeler's, ×1.05 durante 20 s, `b8+0x420`): lector sin identificar.
- **`1000[15]`, `100120[00]` y `100700[04]` de la Bewitched Alonne**: sin analizar.
- Para qué sirven las balas de la ranura 1 en los parry de algunos escudos.
- El significado de `desc+0x29` / `+0x2a`, que anulan las balas en `FUN_140392a60`.
- El Bow of Want (TAE 40030030 sin nodo en c0001) y las ballestas Sanctum (sin pistas Avoid / Move): cómo los anima
  DS2.

## 8. La guardia durante el ataque: es una capa (c0001.nmb)
Scripts: `D:\Reversing\DS2\out\guardatk\` (`chain.py` cadenas de padres, `users.py` / `down.py` árbol de entradas) y
`out\grip\grip_sm.py` / `grip_alpha.py`.

**Respuesta: la animación de ataque no trae la guardia. DS2 la pone con una capa de guardia por encima de toda la red
del cuerpo, ataques incluidos.**

### 8.1 Estructura
- Del SM raíz `0x23d4` cuelga el SM **`0x210e`**, con dos estados:
  - **`0x210f`**, sin guardia: la red del cuerpo `0x233e`, que tiene las posturas de agarre y dentro
    `StateMachine_Main` `0xd2`. El ataque con guardia (nodo `0xb6c`, `a25_01_0011`) está ahí, en
    `SM_MoveOrAttackGuard|BT_Attack_MoveGuard|MoveAttack_Ref|BT_MoveAttack`, como cualquier ataque.
  - **`0x2110` `SM_MoveGuard`**.
- **Entrada**: CP `GuardStance` (0x3f) = +1 o −1, fundido 0.1 s (transiciones `0x232a` / `0x232b` / `0x232e` /
  `0x232f`). Lleva además una condición 0x26a.
- **Quién escribe `GuardStance`**: el actualizador de CP en `0x14035d82c`, leído del exe.
  - Toma `b8+0x14`: 1 → +1.0, 2 → −1.0, otro → 0 (`DAT` 1.0 / −1.0). Con el bit 28 de `b8+0x4c8`, 0.
  - En el mismo actualizador: `GuardSpeed` = `b8+0x268`, `GuardCancelSpeedCategory` = `b8+0x26c`,
    `GuardID_Category` = (`c0+0x1c` / 1e6) % 100 y `GuardID_Number` = `c0+0x1c` % 1e6. `c0+0x1c` es el TAE de la
    guardia.
- **En `SM_MoveGuard`** (`grip_sm.py 0x2110`):
  - `GuardStance` −1 con `GuardID_Number` 310010 / 320010 → **`BT_Guard1Hand_L` (`0x216c`)**; 330010 / 340010 →
    `Guard2Hand_L`.
  - +1 con los mismos números → `BT_Guard1Hand_R` (`0x21d5`) / `Guard2Hand_R`.
- **Dentro, el SM `0x211f`** (o `0x2180` en la derecha):
  - `0x2120`: FeatherBlend2(`0x233e`, clip), con `BlendWeight_1` = 1.0 y pesos (0, 1). El clip pasa por un
    PlaySpeed con `GuardSpeed` y por un switch sobre `GuardID_Category` (atributo 0x09 = 20 / 44 / 45 / 46):
    - **izquierda**: `a20_32_0010 FistGuard1Hand_L`, `a44_32_0010`, **`a45_32_0010 MiddleShieldGuard1Hand_L`**,
      `a46_32_0010`;
    - **derecha**: 44 / 45 / 46, los **mismos `_L`** a través de nodos espejo 0x87 (`0x21b6..0x21b8`).
  - Tras la condición 0x262 (0.1 s), `0x2126`: el mismo FeatherBlend2 sobre un **SingleFrame** (`0x2113`, CP
    `BlenWeight_095`, valor por defecto **0.95**, leído del nodo CP `0x30`) del mismo clip, más una capa aditiva
    (`0x2124`: `AdditiveStand` / `AdditiveBreathe`).
  - Sale de la guardia con fundidos de 0.3 / 0.5 / 0.8 / 1.1 s según `GuardCancelSpeedCategory` (10 / 20 / 30 /
    40..100).
  - Las guardias a dos manos (`Guard2Hand_R` / `_L`) tienen el mismo fijo: SingleFrame 0x21fa / 0x2286 con
    `BlenWeight_095` (wall_bounce.md, "Otros cambios del exportador").

### 8.2 Por qué la guardia queda puesta durante el ataque
1. El botón de guardia sigue pidiendo el tipo 13 en cada frame.
2. `FUN_14038fd30` lo acepta porque el tipo de movimiento del ataque es 4 o 5.
3. `ctrl+0x24` → `b8+0x14` se mantiene, y `GuardStance` también.
4. `0x210e` sigue en `SM_MoveGuard`, que mezcla el clip de guardia sobre `0x233e`, donde corre el ataque.

Con un ataque de tipo 2, `FUN_14038fd30` falla: `b8+0x14` pasa a 0, `GuardStance` a 0, y la capa sale.

### 8.3 Pesos por hueso (atributo 0x57 de `0x2120` / `0x2126`; `0x2181` / `0x2187` en la derecha; iguales en clip y fijo)

| Hueso DS2 | Alfa |
|---|---|
| Spine | 0.2 |
| Spine1, Neck, Head | 0.3 |
| Clavicle del lado en guardia | 0.75 |
| UpperArm | 0.8 |
| Forearm | 0.9 |
| Hand, dedos, Weapon | 1.0 |
| resto (piernas, brazo contrario) | 0 |

Pasados al rig de Minecraft con el mismo mapeo que la powerstance (`powerstance.MC_BONES`: cuerpo = Spine1,
cabeza = Head, brazo = UpperArm, objeto = Weapon):

| Lado | `body` | `head` | `left_arm` | `left_item` | `right_arm` | `right_item` | piernas |
|---|---|---|---|---|---|---|---|
| izquierda | 0.3 | 0.3 | 0.8 | 1.0 | 0 | 0 | 0 |
| derecha | 0.3 | 0.3 | 0 | 0 | 0.8 | 1.0 | 0 |

### 8.3b Guardias a dos manos (`BT_Guard2Hand_R` / `_L`; `out\grip\grip_alpha.py`, `guardatk\chain.py`)
- SM 0x2110: `GuardStance` +1 con `GuardID_Number` 330010 / 340010 → `0x2253` → SM `0x2210`; −1 → `0x22ed` → SM
  `0x22a8` (transiciones 0x2318..0x2328).
- Clip: `0x2211` (derecha) / `0x22a9` (izquierda), PlaySpeed `0x21fb` / `0x2287` con `GuardSpeed`, switch `0x21fc` /
  `0x228a` sobre `GuardID_Category`: 25 / 29 / 36 → `aCC_33_0010` (hechos `_R`), 44 / 45 / 46 → `aCC_34_0010` (hechos
  `_L`). La derecha pone los `_R` tal cual y los `_L` por nodos espejo 0x87; la izquierda al revés.
- Fijo: `0x2217` / `0x22af`, SingleFrame `0x21fa` / `0x2286` (`BlenWeight_095`).
- **Los alfas del clip y del fijo son distintos** (en una mano son iguales):

| Estado | Spine | Spine1 | Neck / Head | L_Clav | L_UpperArm | L_Forearm | L_Hand… | R_Clav | R_UpperArm | R_Forearm | R_Hand… |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `0x2211` clip der. | 0.2 | 0.4 | 0.5 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| `0x2217` fijo der. | 0.2 | 0.5 | 0.5 | 0.8 | 0.8 | 0.9 | 1 | 0.8 | 0.9 | 0.9 | 1 |
| `0x22a9` clip izq. | 0.2 | 0.4 | 0.4 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| `0x22af` fijo izq. | 0.2 | 0.5 | 0.5 | 0.8 | 0.9 | 0.9 | 1 | 0.8 | 0.8 | 0.9 | 1 |

  Piernas y pelvis: 0. En Minecraft (`body` = Spine1, `head` = Head, brazo = UpperArm, objeto = Weapon):
  `weapons.json guardLayer.bones2h`.
- El switch de una mano a la derecha (`0x21b5`) solo tiene 44 / 45 / 46. **Con la antorcha (20) elige `0x21b6`, el
  espejo de `a44_32_0010_SmallShieldGuard1Hand_L`**; el del fijo `0x21bc` igual (`0x21bd`).
  - Switch 0x83 (`nodeSwitchUpdateConnections` `FUN_14064bc70` → `FUN_14064bad0`): lista = atributo semántica 0x22
    (tipo 0x09, u16 n `+0x10`, floats `+0x18`); opciones = semántica 0x25 (tipo 0x39): `+0x10` cuándo evalúa (0 al
    activarse, 1 cada actualización, 2 al activarse o al dar la vuelta el hijo), `+0x14` regla (0 el más cercano,
    corte en el punto medio; 1 piso; 2 techo). El valor se recorta al primero / último de la lista. Un solo hijo
    activo, sin interpolar; al cambiar borra la instancia del anterior (corte seco).
  - `0x21b5` / `0x21bc`: (44, 45, 46), 0x39 = 1 / 0. `0x2118`: (20, 44, 45, 46), 0 / 0.
  - `a44_32_0010` (anim 973): `GuardDuration` 0.5..1.0, `GuardMotionDuration` 0..1, 0.3333 s, sin bucle → la mezcla
    al fijo empieza al 0.95 como las demás.
  - `tools/re/weapons.py` aplica la regla a todas las guardias (`switch_nearest`); `guardLayer.clips` lleva el clip
    elegido y su `duration`.

### 8.4 Paso del clip a la pose fija (condición 0x262; animations.md, "Condición 0x262")
- Las cuatro transiciones (`0x2141` / `0x21a2` una mano, `0x222e` / `0x22c6` dos manos) usan la misma condición:
  pista 500, evento 520 (`GuardDuration`), umbral 0.1, leída del estado del clip (`0x2120` / `0x2181` / `0x2211` /
  `0x22a9`).
- `GuardDuration` va de 0.5 a 1.0 del clip en los seis clips del mod. En `a20_32_0010` va de 0.5 a 1.05, pero el clip
  no hace bucle (atributo 0 del nodo, byte `+0x10` = 0; nodos 0x2119 / 0x211e), así que se corta en 1.0
  (`FUN_1406016c0`). Región de los seis: [0, 1].
- **La mezcla empieza al 95 % del clip** (le queda el 10 % del evento), un frame después por leer el frame anterior.
  Con el reloj del clip (÷ `GuardSpeed`, que lo escala con el nodo de velocidad):

| TAE | Clip | Duración | Inicio de la mezcla |
|---|---|---|---|
| 20320010 | `a20_32_0010` | 0.6667 s | 0.6333 s |
| 45320010 | `a45_32_0010` | 0.3333 s | 0.3167 s |
| 29330010 | `a29_33_0010` | 0.3333 s | 0.3167 s |
| 36330010 | `a36_33_0010` | 0.3333 s | 0.3167 s |
| 25330010 | `a25_33_0010` | 0.3333 s | 0.3167 s |
| 45340010 | `a45_34_0010` | 0.3333 s | 0.3167 s |

- **La mezcla** (0.1 s, atributo 0x3b `+0x10`; modo 0, sin congelar):
  - el clip de origen sigue avanzando (de 0.95 hacia 1.0);
  - el destino es el SingleFrame al 0.95 (más la capa aditiva, no exportada);
  - peso del destino = (1 − cos(π t / 0.1)) / 2: **no lineal**;
  - pose: posición lineal y rotación fastSlerp, como el Blend2.
- **Espejo (lado derecho, nodos 0x87)**: no cambia nada. Los userData 500 y 520 no están en la tabla de espejo de
  eventos (animations.md, "Espejo de Morpheme").
- **El clip sin bucle se queda en su último frame** al pasar de 1.0 durante la mezcla. Bajo FeatherBlend2 (no genera
  la semántica 1) y la transición (su tabla 0x140650c2c da 0xffff para la 1) el clip encola la tarea 0
  `TaskAnimSyncEventsUpdateTimePos` (`FUN_14060ec00`); `FUN_1406667d0`: `cur = prev + d`; si `cur + 1e-4 ≥ 1` sin
  bucle, `cur = 1.0` (con bucle `fmodf`). Bandera de bucle: byte `+0x10` del atributo tipo 0x00 del clip, 0 en todos
  los clips de guardia. Δt del clip = `max(0, GuardSpeed)·Δt` (`TaskScaleUpdateTime`, `FUN_140612640`).

### 8.5 `GuardSpeed` y `GuardCancelSpeedCategory`: de dónde salen
Leído con `out\bs_scripts\deob.py` (caso `0x140394276` de `FUN_140393c00`, `out\case_140394261.txt`). La petición de
guardia (`desc`) llena:
- `ctrl+0x2c` (f32) = `FUN_14038f940` → `b8+0x264`;
- `ctrl+0x30` (f32) = `FUN_14038f870` → `b8+0x268` → CP `GuardSpeed`;
- `ctrl+0x34` (u8) = `FUN_14038ffb0` → `b8+0x26c` → CP `GuardCancelSpeedCategory` (CP 0x8c, entero: el actualizador
  `0x14035d82c` lo escribe con el setter entero `FUN_1404e28b0`; `GuardSpeed` con el de float `FUN_1404e2890`).
- `desc+0x00` = mano (0 izq., 1 der.), `desc+0x50` = fila WTP del arma, `desc+0x60` = fila WAMP del slot (damage.md,
  §3 de este archivo).

**`FUN_14038ffb0`** (`GuardCancelSpeedCategory`):
```
f = FUN_140349100(equipo, (desc+0 != 0) − 2)   // equipo = chr vt+0x120 → vt+0x70
c = WTP u8 +0x51
si f > 0:  10 → 20, 20 → 30, 30 → 40, 40 → 40, otro → c   // 0x140201743..0x14015bff8
si no:     c
```
- `FUN_140349100`: `FUN_14034e6a0(equipo, −1 der. / −2 izq.)` da el slot activo de la mano y devuelve
  `slot+0x8c` = **falta de Fuerza/Destreza** del arma (`FUN_14034ce60`, damage.md §3; Fuerza ÷ 2 a dos manos). Con
  el arma temporal de la mano (`equipo+0x200+mano·0x48 ≠ −1`, la antorcha), lee `+0x23c`.
- `WTP+0x51` en las armas del mod: 20, salvo Fists (WTP 10) y Pyromancy Flame (WTP 11): 10.

**`FUN_14038f870`** (`GuardSpeed`):
```
sin fila WAMP (desc+0x60 == 0): 1.0 (DAT_1410ac698)
si no: WAMP f32 +0x08 × FUN_14038d500(FUN_14038b990(chr+0x490), 2) × calc vt+0x68({id 9, modo 0, g})
```
- `FUN_14038d500(bloque, 2)` = `bloque+0xbc` = derivada `+0x90` = `PSPL[(stat3 + 3·stat8) / 4].f32@+0x7c`
  (`FUN_14038d790`, `0x14038e0f7`; slot 3 = Sintonía, 8 = Adaptabilidad, stats.md; índice fuera de 1..99 → 1).
  Vale 1.0 (índice 1..9) → 1.5 (99).
- `calc vt+0x68` = `1 − pen` (`FUN_14031e660`, damage.md) con `PlayerLackOfStatsParam` fila 9 = (10, 30, 0, 0).
  - `FUN_14031fcb0` elige la falta por el modo: 0 → `FUN_140349100` (`slot+0x8c`, Fuerza/Destreza), 1 →
    `FUN_140349170` (`+0x90`, Int/Fe), 2 → `FUN_1403491e0` (`+0x88`, los cuatro).
  - `pen = 0.01·(10 + 20·f)` si `f > 0`. Las columnas 2/3 valen 0, así que la estamina (`g`) no cuenta.
  - Con los requisitos cumplidos: `GuardSpeed = WAMP+0x08 × PSPL[…]+0x7c`.
- `WAMP+0x08` de los slots de guardia del mod: escudo 1.3 (una y dos manos), antorcha 1.3 (una mano) / 1.4 (slot
  50), espadas, hachas (salvo Gyrm), maza, garrote, Fists, campana y llama 1.7, Gyrm Axe 1.1, Sorcerer's Staff 1.2,
  Spear y Pickaxe 1.0.

**`FUN_14038f940`** (`b8+0x264`): `WTP f32 +0xcc × calc vt+0x68({id 6, modo 2, g})` (fila 6 = (30, 80, 0, 0)).
Su lector no está identificado (el actualizador de CP no lo lee).

**Salidas 0x2110 → 0x210f** (condiciones leídas del `.nmb`; tipos decompilados del registro `FUN_140608700`):
- **0x273** (`FUN_140659e80`): CP float (`+0x48` nodo, `+0x4a` pin) en `[+0x4c, +0x50]`, ambos incluidos, XOR el
  byte `+0x02`.
- **0x271** (`FUN_14065a170`): lo mismo con CP entero.

| Transición | Fundido | `GuardStance` (0x3f) | `GuardCancelSpeedCategory` (0x8c) | `GuardID_Category` (0x3d) |
|---|---|---|---|---|
| 0x2329 | 0.3 s | = 0 | **0..10** | ≠ 42 (invertida) |
| 0x232c | 0.5 s | = 0 | 20 | ≠ 42 |
| 0x232d | 0.8 s | = 0 | 30 | ≠ 42 |
| 0x2330 | 1.1 s | = 0 | 40..100 | ≠ 42 |

- La categoría 0 cae en 0.3 s. Fuera de esos rangos (negativos, 11..19, 21..29, 31..39, > 100) ninguna de las cuatro
  vale, y la capa solo sale por las otras transiciones (0x2331..0x233d, sin estudiar). Las armas del mod solo dan 10,
  20 o 30.
- `b8+0x26c` no se borra al soltar (`0x1403931ce` solo pone `ctrl+0x24 = 0`): la salida usa la categoría de la última
  petición de guardia.
- Sin confirmar en código: que las condiciones de una transición se combinen con Y (es la lectura de todo el
  documento); quién escribe la falta `slot+0x8c` con el arma temporal (`+0x23c`).

## 9. Exportado (`tools/re/weapons.py` → `weapons.json`, `ds2_anims.py`)
- **Por arma: bloque `guard_attack`**, con la misma forma que las otras manos.
  - Claves: `light` (slot 57, tipo 0x12), `light_combo` (58, 0x13), `heavy` (59, 0x14), `heavy_combo` (60, 0x15).
  - `null` = vacío. Solo la Spear del mod tiene `light` / `light_combo` (WAMP 10017910 / 10017920, TAE 25010011,
    tipo 5).
  - Los slots valen para la mano que ataque. La condición de entrada está en §2.1.
- **Global: `guardLayer`**:
  - `clips`: TAE de guardia de una mano → `{left|right: {animation, mirror}}`. Solo para los TAE que usan las armas
    del mod: 45320010 (escudo, los dos lados; derecha espejada) y 20320010 (antorcha, solo izquierda).
  - `bones`: pesos por hueso de Minecraft, por lado (§8.3).
  - `enterBlend` 0.1, `holdBlend` 0.1, `holdFrame` 0.95, `exitBlend` por categoría.
    `exitBlend` `"10"` vale para 0..10 (§8.5).
  - `guardSpeedStat`: `PSPL[i+1].f32@+0x7c` (99 valores; índice = `(Sintonía + 3·Adaptabilidad) / 4`, §8.5).
  - `guardSpeedLackRow`: `PlayerLackOfStatsParam` fila 9.
- **Por slot de guardia (tipo 13)**: `guardSpeed` = `WAMP+0x08`; `guardCancelCategory` = `WTP+0x51`;
  `guardCancelCategoryLacking` = la categoría con falta de Fuerza/Destreza (§8.5).
- **Animación nueva**: `ds2_25010011.json`. Los clips de guardia `ds2_45320010` / `ds2_20320010` ya existían.
- **No exportado**:
  - la capa aditiva de respiración;
  - los clips de guardia 44 / 46, porque ningún arma del mod los usa.
- `guardLayer.clips` incluye las guardias a dos manos (`twoHanded`: true) y `bones2h` sus alfas (§8.3b).

## 10. Implementación (2026-11-01, `GuardLayerClient`)
- **Toda guardia es la capa** (SM 0x210e por encima de la red del cuerpo), no una acción: la locomoción sigue debajo
  (se camina con la guardia arriba con la locomoción normal, como en DS2) y los ataques con guardia también.
  - Prioridad 1600, encima de la capa de acciones (1500). **Corrección**: la capa de la entrega anterior tenía
    prioridad 25, debajo de las acciones, así que durante el ataque con guardia no se veía.
- Por hueso: base + ((clip − base) × alfas del clip × (1 − h) + (fijo − base) × alfas del fijo × h) × envolvente.
  - `h`: mezcla de 0.1 s al fijo, desde 0.95 × duración / `GuardSpeed` (la condición 0x262 lee el reloj del clip,
    que va a `GuardSpeed`; la mezcla es tiempo real).
  - Envolvente: entrada 0.1 s × (1 − salida por `GuardCancelSpeedCategory`), curva (1 − cos)/2.
  - El clip corre a `GuardSpeed` (SpeedModifier); pasado su final queda en su último frame (`_end`, sin confirmar
    §8.4); el fijo es `_hold` (0.95).
- El servidor manda antes de cada guardia (`AttackPayloads.GuardLayer`): fundido de salida y `GuardSpeed`, calculados
  con los stats del jugador y la ranura de la guardia (`Weapons.guardSlot` / `guardSpeed` / `guardCancelCategory`).
  La falta es solo de Fuerza/Destreza (`Ds2Damage.strDexLack`, `FUN_14034ce60`); antes se usaba la de los cuatro
  stats.
- Exportador: los clips de guardia ya no llevan la mezcla horneada: clip limpio + `_hold` (0.95) + `_end` (último frame).
- Armas de datapack: la capa con los alfas de su agarre, velocidad 1 y sin fundido de salida (no tienen fila WAMP ni
  categoría); su `_hold` hace de pose fija y de último frame.
- **Volver a subir la guardia mientras sale** (breakout; volcados `scratchpad\morpheme3\`):
  - La SM evalúa las salidas del destino durante una transición (`FUN_1405fac70` → `FUN_1405fb3f0`): 0x210f tiene
    `+0x20` = 16 y las transiciones de la guardia el flag 0x200 (flags `def+4` 0x0608; 0x2338 / 0x2339 = 0x0408 no
    permiten breakout). Condiciones con Y (`FUN_1405f9830`). 0x26a (`FUN_14065b030`) = evento 500 / 530
    `GuardMotionDuration` del frame anterior ≠ 0, XOR invertir; con `GuardStance` ±1 una de cada par se cumple.
  - Nueva transición de 0.1 s: origen = la salida en curso, destino = 0x2110 **borrado y desde cero**
    (`FUN_1405fa200` → `FUN_1405ec610`): la subida se repite entera.
  - La salida vieja pierde 0x2110 y pasa a **dead blend** (atributo 0x39 `+0x19` = 1): sigue con su reloj desde t0 y
    su largo L (tarea 0xad `FUN_140632820`, `PLAYBACK_POS_INIT`/`NODE_SPECIFIC_DEF +0x10`) y da
    Blend2(pose muerta, 0x210f, w(t)) (tarea 0xb4 `FUN_140632460` → `FUN_14066c6e0`).
  - Pose muerta (`FUN_140634ba0`): el caché propio (tarea 0xb2, etiquetado con el frame f) no se encuentra en el frame
    f+1 (búsqueda por frame exacto, `FUN_1404dd580`), así que toma la salida de 0x210e (o de la raíz) de los frames f
    y f−1, **ya mezclada con w0**, y la vuelve a mezclar con w(t): no es continua.
  - Con dos frames, las rotaciones siguen girando a su velocidad angular (`FUN_140635610`, `FUN_140631d20`;
    posiciones congeladas); con uno, congelada. **Sin establecer** si el frame f−1 sigue vivo: el mod la deja
    congelada (no extrapola).
  - Si se suelta la guardia otra vez durante esa entrada, el mod funde todo con la salida nueva: no se trazó.
