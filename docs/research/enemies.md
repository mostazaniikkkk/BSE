# Enemigos: parámetros, ataques, reacciones, muerte

Fuente: `DarkSoulsII.exe` (SotFS) en Ghidra headless y `out\py\deob.py`. Salidas y scripts en `D:\Reversing\DS2\out\enemies\`:
- `calc_vt.c` y `a1.c`: vtable de `ChrGameParamCalculator` (`0x1410dfc18`), que es la calculadora de los enemigos.
- `death1.c`: init del personaje `FUN_140312dd0`. `souls*.c` / `death*.c`: almas. `lot*.c`: drops.
- `poise.c`: `ChrPoiseCtrl` y el segundo medidor. `hit1.c` / `edc.c`: proceso del golpe. `f170710.txt`: fila de daño del enemigo.
- Scanners: `py/scanptr.py` y `py/scanpat.py`.

Datos:
- Regulation: `EnemyParam` EP (0x168), `EnemyCommonParam` ECP (0x38c), `EnemyMoveParam` EMP (0x304),
  `EnemyDamageParam` EDP (0x7c), `DamageCtrlParam` DCP (0xc0), `ItemLotParam2_Chr` (0x7c), `ChrRoundDropSoulParam`,
  `ChrMultiplayParam`, `DeadTypeParam`.
- **Params de mapa nuevos**: `D:\Reversing\DS2\extracted\mapparam\` (167 archivos). Se sacaron de `GameDataEbl` con
  `tools\extract_bdt.py`, rutas `gamedata:/param/<Tipo>_mXX_YY_00_00.param` (cadenas `%sparam/GeneratorParam_%s.param`
  y similares en `0x1410e78c0`).

Lo que ya está en otras notas y no se repite aquí:
- [damage.md](damage.md) §4: fórmula de la víctima, μ/D del enemigo (`DamageAdjustParam`), defensa y absorciones.
- [stamina.md](stamina.md): orden del golpe, estructura `A`, daño de estamina, rotura de guardia.
- [weapons.md](weapons.md): parry, críticos, ángulos de `CommonParam`, reacciones 9–13.
- [speffects.md](speffects.md) §11.3: buffs de defensa del enemigo.

## 1. Qué param usa cada enemigo — `FUN_140315b20`
- `chr+0x38` = `EnemyParam[id]` (`FUN_140358620`; ids < 1000 van a `ChrParam`).
- `chr+0x40` = `EnemyCommonParam[EP+0x08 > 0 ? EP+0x08 : id]` (`FUN_140358530`). **Esto resuelve el pendiente de
  damage.md: el campo que elige la fila de ECP es `EP+0x08`.**
- `chr+0x48` = `EnemyMoveParam[EP+0x0c > 0 ? EP+0x0c : id]` (`FUN_1403585d0`, slot `+0x370`; ids < 1000 van a
  `ChrMoveParam`).
- `EnemyParam` tiene la misma disposición que `ChrParam` (fila 100 del jugador): los mismos lectores usan `chr+0x38` para
  los dos (`FUN_140145a40`, `FUN_140312dd0`). `EnemyMoveParam` comparte el prefijo con `ChrMoveParam`.
- **Quién pone el id**: el generador del mapa.
  - `GeneratorParam_<mapa>` `+0x08` = id de `GeneratorRegistParam_<mapa>` (149 de 149 filas en m10_10).
  - `GeneratorRegistParam` (0x10 bytes) `+0x00` = id de EnemyParam. Lo leen `FUN_140161e10` y `FUN_140180af0`:
    `**(gen+0x60)` → `FUN_140358620`.
  - El resto de campos de las dos tablas **sin decodificar**, salvo `GeneratorParam +0x88` (lote, §6).

## 2. EnemyParam — campos confirmados

| Offset | Tipo | Uso (función) | Hollow Soldier 102000 |
|---|---|---|---|
| `+0x00` | s32 | id de modelo (c1020). `FUN_140164630` lo trata aparte | 1020 |
| `+0x08` / `+0x0c` | s32 | fila de ECP / EMP (§1) | 102000 / 102000 |
| `+0x18` | s32 | = 4 → almas de tipo 2 en `FUN_1402024c0` | 1 |
| `+0x1c` / `+0x20` / `+0x70` / `+0xc0` | s32 | copiados a `chr+0xb0` `+0xc / +0x48 / +0x20 / +0x14` (`FUN_140312dd0`); uso **sin confirmar** | 8 / 0 / 2 / 2 |
| `+0x24` | u8 | fila de `ChrMultiplayParam` (§3, §5) | 10 |
| **`+0x28`** | s32 | **PV en NG** (§3) | **320** |
| **`+0x2c`** | f32 | **estamina máxima** (`chr+0x1ac`; la guardia se rompe a ≤ 0, stamina.md) | **1240** |
| **`+0x30`** | f32 | **regeneración de estamina** por s (`FUN_140333900`, lee `chr+0x38 +0x30`; enemy_ai.md §8.7) | **125** |
| `+0x40` / `+0x44` | s32 | `DeadTypeParam` para la muerte de tipo 1 / 2 (`FUN_140164630` → `FUN_14016da90`, slot `+0x6b0`) | 13000 / 13000 |
| **`+0x4c..+0x58`** | s32 ×4 | id de `EnemyEquipWeaponParam` por modo 0..3 (`chr.b8+0x2e8`, `FUN_1403a8f40`); su `+0x00` es el juego de comportamientos (enemy_ai.md §7.1) | 10200000, 0, 0, 0 |
| `+0x48`, `+0x5c`, `+0x60` | s32 | **ningún lector encontrado** | 0, 0, 0 |
| **`+0x74`** | s32 | **estabilidad de guardia** ×0.01 (`vt+0x1f0` = `FUN_14031efa0`) | **50** |
| **`+0x78..+0x98`** | s32 ×9 | **% de daño bloqueado** ×0.01 × `b8+0x3ec..+0x40c` (`vt+0x1e0` = `FUN_14031e420`). Salida [0..4] = físico/magia/rayo/fuego/oscuro (`+0x78..+0x88`), [6..9] = `+0x8c..+0x98`, [10] = `+0x84`, [11] = `+0x8c` | **80** en todos |
| **`+0x9c`** | s32 | **defensa física** (damage.md) | **317** |
| **`+0xa0..+0xac`** | s32 | **absorción** magia/rayo/fuego/oscuro % (damage.md) | **15 / 30 / 30 / 30** |
| `+0xb0..+0xbc` | s32 | absorción de los índices 6..9 (`FUN_14031f570`; [10] = `+0xa8`, [11] = `+0xb0`). Qué estado es cada uno: **sin confirmar** | 30 / 15 / 30 / 100 |
| **`+0xd0`** | s32 | **almas que da** (§5) | **90** |
| **`+0xd8`** | f32 | **multiplicador de ataque**: multiplica los 13 valores del golpe (§4) | **1.0** (102011: 1.1) |
| `+0xdc` / `+0xe0` / `+0xe4` | f32 | `vt+0x178` (`FUN_14031e360`) según el tipo 1/2/4 de otro objeto; **sin identificar** | 1.0 |
| `+0xfc` / `+0x100` | f32 | máximo / regeneración por s del **segundo medidor** `chr+0x1b8` (§7.2) | 4400 / 2.2 |
| `+0x104` / `+0x108` | f32 | lo mismo con `b8+0x614 ≠ 0` | 1467 / 2.2 |
| `+0xf8` | u8 | ≠ 0: el segundo medidor nunca se rompe (`FUN_1401468e0`) | 0 |
| **`+0x118`** | f32 | **poise base** (§7.1) | **0** |
| **`+0x11c`** | f32 | **regeneración de poise** por s | **1.47** |
| `+0x124` | f32 | regeneración con `b8+0x610 ≠ 0` | 0 |
| `+0x130` | u8 | ≠ 0 → no crea los objetos de `+0xdd..` (`FUN_140312750`, bucle de 9); **sin identificar** | 1 |
| `+0x133` | u8 | fila de `ChrRoundDropSoulParam` (§5) | 2 |
| **`+0x134`** | s32 | **lote de drop por defecto** (`ItemLotParam2_Chr`, §6) | **10200000** |
| `+0x138` | s32 | lote alternativo si `DAT_1416148f0 vt+0x58` es verdadero (`FUN_1401e2580`). Qué modo es: **sin confirmar** | 5000000 |
| **`+0x140..+0x158`** | s32 ×7 | **PV en el ciclo 2..8** (NG+1..NG+7) | **672, 807, 941, 1008, 1076, 1143, 1277** |
| `+0x15c` | s32 | id de texto, categoría FMG 0xc (`FUN_140252ae0`): **nombre**. 0 en el Hollow Soldier | 0 |
| `+0x164` | f32 | ≠ 0 → evento al crear (`FUN_140312dd0`, `thunk_FUN_141b3cbe1`); **sin identificar** | 0 |

- **Sin lector encontrado**: `+0x10`, `+0x14`, `+0x10c`, `+0x110`, `+0x12c`, `+0x13c`.
- `ChrParam` 100 en esos mismos offsets: `+0x28` = 300, `+0x2c` = 800, `+0xfc` = 500, `+0x100` = 7.0, `+0x118` = 0,
  `+0x11c` = 0.56. Para el jugador la vida sale de los stats (stats.md).

## 3. Vida, estamina y medidores al crear — `FUN_140312dd0` → `FUN_14031ba90(chr+0x168, …)`
- **PV** (`FUN_14016e740`):
  `vida = trunc(tabla[clamp(ciclo, 1, 8)] × mp)`, con `tabla = [EP+0x28, EP+0x140, …, EP+0x158]`.
  - `ciclo` = `chr+0x45d` (byte `+0xb2` del bloque de creación).
  - `mp` = 1.0. Si `chr+0x45e` (byte `+0xb3`) = 1 / 2 / ≥3, `mp` = u16 `+0x00 / +0x0c / +0x18` × 0.01 de
    `ChrMultiplayParam[EP+0x24]`. La fila 10 tiene 100 en los tres.
  - Se guarda en `chr+0x168` (actual), `+0x16c` = −100000 (mínimo), `+0x170` (máximo, `vt+0xc8`) y `+0x174`.
- Si el bloque de creación trae `+0xa4 ≥ 0`, la vida inicial es ese valor recortado a [mín, máx]. Viene del
  generador; **sin trazar**.
- **Estamina**: `chr+0x1ac` = `EP+0x2c`, con mínimo −9999 y máximo en `+0x1b4`. Regeneración del enemigo: **no trazada**.
- **Segundo medidor**: `chr+0x1b8` = `EP+0xfc`, mínimo 0, máximo `+0x1c0` (§7.2).
- **Poise**: `chr+0x218` = `EP+0x118`, mínimo `+0x21c` = 0, máximo `+0x220` (§7.1).
- **Estados**: 7 tríos `(0, 0, 100)` desde `chr+0x1c4` (acumulado, mín., máx.). `FUN_140145c60` suma la acumulación del
  golpe y dispara el estado al llegar a 100 (`DAT_1410acb18`). Los tipos son 0..6, del resultado `+0x18..+0x30`
  (`FUN_140136220`). Qué estado es cada tipo: **sin confirmar**.

## 4. Ataque del enemigo
### 4.1 Fila del golpe
- El id del golpe se resuelve en el gestor de daño (`mgr+0xb40`): de 1e8 a 2e8 es `EnemyDamageParam` (stamina.md).
- `FUN_140170710(slot, id)` (`out\enemies\f170710.txt`) suma al slot (13 floats) hasta tres pares (tipo, valor) de la
  fila. **PDP, EDP y SDP comparten esta disposición**:
  - `slot[u8 +0x19] += (float) s32 +0x0c`;
  - `slot[u8 +0x1a] += (float) s32 +0x10`;
  - `slot[u8 +0x1b] += (float) s32 +0x14`.
  - Cada tipo debe ser < 13: 0 físico, 1 magia, 2 rayo, 3 fuego, 4 oscuro, 5 sin identificar, 6..12 los 7 estados (§3).
  - Además, `slot+0x8c = DCP+0x0c` del `DamageCtrlParam` `fila+0x00`.
- `FUN_140391e60` (`out\crit_391e60.txt`):
  1. llama a `FUN_140170710`;
  2. si el enemigo tiene SpEffects (`vt+0x130`), suma el código 7 (`FUN_14014b990`) a los slots 0..4, 6..9 y 12;
  3. multiplica los 13 slots por **`EP+0xd8`**.
  - **No hay escalado por stats**: el poder de ataque es el valor plano de la fila × `EP+0xd8`.
- El resto del golpe pasa por el mismo camino que el del jugador: estructura `A` (`FUN_1401373b0`) y víctima
  (`FUN_140138d50`, damage.md §4).
  - De la fila EDP salen `+0x18` (piso), `+0x1c` (tasa), `+0x20` (factor X), `+0x24` u16 (base del daño de estamina) y
    `+0x26` u16 (× `A+0x88` → resultado `+0x44`, **sin identificar**).
  - De la fila DCP salen `+0x00` (subtipo físico → φ), `+0x02` (§7.1), `+0x03` (parry), `+0x08`, `+0x10` / `+0x12`
    (reacción), `+0x18` (tiempo en el suelo) y `+0xa4..+0xb8` (caída por distancia).
- **Fórmula contra el jugador** (NG, sin red ni buffs, ≤ distancia de caída, daño.md §5):
  `físico = max(piso, 10·V·EP+0xd8 − def) / 12 · tasa`, y `elemento = max(piso, 6·V)/6 · (1 − abs) · tasa`.
- **Atacante enemigo en la víctima**: `vt+0x168` = `DamageAdjustParam +0xf0..+0x10c` (1.0); `vt+0x188` (sumando del
  daño de poise) = 0 (`FUN_1401f9bb0`).

### 4.2 Filas del Hollow Soldier (c1020)
- Hay 47 filas EDP `1102 0v xxx`. Las de cada variante son las que llevan las entradas de su juego de comportamientos
  (`EP+0x4c..` → `EnemyEquipWeaponParam` → `EnemyBehavior*Param` / `EnemyComboBehaviorParam` 10200v0; enemy_ai.md §7.1).
- **Qué animación usa cada fila y cuándo pega**: enemy_ai.md §8.3 (evento TAE 2200, ranura `k` = fila `k` de la entrada).
- Todas las filas: piso 34, tasa 1.0, `+0x20` = 1.2, `+0x26` = 4.

Variante 0 (102000):

| EDP | Físico | Estamina `+0x24` | DCP | DCP `+00` subtipo / `+03` / `+10` / `+12` |
|---|---|---|---|---|
| 110200010 | 118 | 240 | 540103102 | 2 / 0 / 2 / 4 |
| 110200110, 110200210 | 118 | 240 | 541002101 | 1 / 0 / 2 / 4 |
| 110200120, 110200220, 110200610 | 118 | 240 | 541002102 | 1 / 0 / 2 / 4 |
| 110200130, 110200230 | 137 | 240 | 541002102 | 1 / 0 / 2 / 4 |
| 110200310 | 146 | 365 | 541003103 | 2 / 0 / 3 / 4 |
| 110200410 | 128 | 1090 | 544301103 | 2 / **1** / 3 / 4 |
| 110200510 | 165 | 725 | 541003103 | 2 / 0 / 3 / 4 |
| 110200620 | 146 | 240 | 541002102 | 1 / 0 / 2 / 4 |
| 110200710 | 174 | 725 | 541003103 | 2 / 0 / 3 / 4 |

- Otras variantes:
  - 110201710: físico 100, DCP 200300020 (`+0x18` = 1.75 s en el suelo).
  - 110201711: **fuego 207** (tipo 3).
  - 110203410: DCP con reacción 13 (agarre), `+0x02` = 2, `+0x03` = 3.
  - 110203420: DCP 590030000, reacción 23.
- Uso de tipos en las 7497 filas: físico 6023, magia 232, rayo 131, fuego 308, oscuro 132, 5 → 7, 6..12 → 111 / 652 / 69 /
  48 / 27 / 45 / 37.

### 4.3 Ejemplo: 110200010 (118 físico) contra el jugador
- Defensa 0: `1180/12 = 98.3` → **98**.
- Desnudo con Vigor/Aguante del usuario (`def` = 0.5·78 = 39, damage.md §7): `(1180 − 39)/12 = 95.1` → **95**.
- Knight +0 (`def` = 342.74): `(1180 − 342.74)/12 = 69.8` → **69**.
- Bloqueando: daño de estamina `240 · (1 − estabilidad)` (stamina.md); el daño de vida bloqueado no está releído.
- Sin contar `Y` (`golpe+0x83`, jugador 0.8 en `+0x34c`), que damage.md deja sin identificar.

## 5. Almas al morir
- **Quién paga**: `FUN_14013d560` (muerte del personaje) → `FUN_140202420(jugadorLocal, almas, tipo, chr)` →
  `FUN_140202610` → `FUN_14038ab40` (souls.md).
  - Tipo 0 en enemigos normales; 2 / 3 / 4 según la clase del personaje (`DAT_1410bfff1` y `FUN_14016f0a0` /
    `FUN_14016f010`).
  - No paga si el jugador tiene `b8+0x4c8` bit 14.
- **Base** (`FUN_14013d8f0`): `EP+0xd0`. Los personajes de tipo jugador (`DAT_1410bfff3`) usan otra fórmula por nivel
  (`FUN_14038d140`).
- **Multiplicador** (`FUN_140203f60`): `m = f · a[tipo] · r · jugador.b8+0x434`.
  - `a[tipo]` = 1.0. En red se sustituye por los campos `+0x44 / +0x5c / +0x3f..+0x42` de otra fila (`FUN_14016f540`),
    **sin identificar**.
  - `f` = 1.0, salvo fantasmas (`FUN_14016f800`, tipo 3): `FUN_14016f560` (`FUN_140358d80`).
  - `r` = `ChrRoundDropSoulParam[EP+0x133]` en `[ciclo]` × 0.01, con la tabla `[100, u16 0, u16 1, …, u16 7]` e índice
    `min(ciclo, 8)` (`DAT_1410c3b20` = 8). El ciclo es `chr+0x45d` del muerto.
  - `b8+0x434` = factor de almas del jugador (SpEffect); su valor sin efectos **no está verificado** (se espera 1.0).
- `almas = (s64)(m × base)`.
- **Hollow Soldier**: fila 2 = `[100, 200, 250, 275, 300, 325, 350, 400]` → NG (ciclo 0/1) **90**, NG+ (2) 180, ciclo 8 360.
- Al matar, el atacante recupera `b8+0x317` PV (`FUN_1403148d0`). Es un SpEffect; 0 sin efectos.
- `FUN_1402024c0` es otro camino que paga `EP+0xd0` sin personaje cargado (desde `FUN_140161e10`).

## 6. Drop — `ItemLotParam2_Chr`
- **Lote** (`FUN_1401e2580`), con `c = min(ciclo, 8)` (`FUN_140412570`, ≥ 1):
  - Si existe `GeneratorParam+0x88 + c − 1`, se usa esa fila; si no, se prueba bajando el ciclo.
    `+0x88` vale 0 en 110 de 149 filas de m10_10; las demás traen lotes concretos como 1100100.
  - Si no se encontró ninguna, se hace lo mismo con **`EP+0x134`** (`FUN_1401e2b40`).
  - Con `DAT_1416148f0 vt+0x58` verdadero se usa `EP+0x138` en lugar de los dos.
  - Bono: `FUN_1401e2e40(c)` = float `+0x110 + 4(c−1)` de la fila de `thunk_FUN_141cf7120` + `FUN_1401e2ec0()` +
    `FUN_1401e2ff0()`. Si la fila existe con el ciclo exacto, usa `c = 1`. Las tres fuentes están **sin identificar**
    (¿descubrimiento de objetos?).
- **Fila** (lector `FUN_1401e3690` / `FUN_1401e2c60` / `FUN_1401e2930`; despachador `FUN_1401e2820`):
  - `+0x00` u8 = modo: 0 → `FUN_1401e3690`, 1 → `FUN_1401e30b0`, 3 → `FUN_1401e34f0`.
  - `+0x01` u8 = número de tiradas.
  - `+0x04[10]` u8 = cantidad (0 = hueco vacío).
  - `+0x0e[10]` / `+0x18[10]` u8 = dos bytes que pasan al objeto soltado (`+0xe` / `+0xf`). **Nombre sin confirmar**
    (¿nivel de mejora / infusión?).
  - `+0x22[10]` u8 = bandera.
  - `+0x2c[10]` s32 = id de objeto. `+0x54[10]` f32 = probabilidad en %.
- **Tirada, modo 0** (el de los enemigos). Por cada tirada, hasta 8 objetos en total:
  - Peso de cada hueco: `w_i = (int)(p_i·100) + (int)(bono·100)`, solo si:
    - `p_i > 0` y `cantidad_i > 0`;
    - no salió ya en este drop (máscara `+0xe`);
    - no se cumple a la vez `bandera_i = 0` y su bit en la máscara `+0xc` (**origen sin trazar**);
    - `FUN_1401ac180(id)` es falso (consulta al inventario, **sin identificar**).
  - `W = Σ w_i`; si `W = 0`, termina.
  - `r = xorshift % (max(W, 10000) + 1)` (RNG en `DAT_1416148f0+0xf4..+0x100`). Si `r ≤ W`, sale el primer hueco cuyo
    peso acumulado llega a `r`.
  - Así **cada hueco tiene `p_i` % por tirada** mientras `W ≤ 10000`, y la tirada no da nada con el resto.
- **Hollow Soldier 10200000** (modo 0, 1 tirada):

| Hueco | Objeto | % |
|---|---|---|
| 0–3 | Hollow Soldier Helm / Armor / Gauntlets / Leggings | 1.5 cada uno |
| 4 | Winged Spear | 1.0 |
| 5 | Hollow Soldier Shield | 1.0 |
| 6 | Lifegem | 4.0 |
| 7 | Radiant Lifegem | 1.0 |

  Total 14 %: 86 % sin drop. Nombres de `itemname.txt`.
- El drop se crea en `FUN_14016dd70` (llamada desde `FUN_140161e10`), en la posición del muerto, con
  `FUN_1401e39e0` / `FUN_1401de530`.

## 7. Reacción al golpe
### 7.1 Poise (`ChrPoiseCtrl`, vtable `0x1410bfc58`, `chr.e0+0x108`)
- **Máximo** (`FUN_140145a40`, cada frame) = `vt+0x1f8` + `EP+0x118` + `(s8) b8+0x305` + `b8+0x3a0`.
  - `vt+0x1f8` es la poise de equipo: 0 en el enemigo (`FUN_1401f9a70`); en el jugador es la armadura
    (speffects.md §11.4).
  - Si el máximo cambia, la poise actual se recorta.
- **Corrección (poise.md §4, §10)**: el daño de estabilidad es `DamageCtrlParam +0xa0` (el 2.º argumento de
  `FUN_140139160` es la fila DCP, no el golpe), y `vt+0x188` es de la **víctima** (jugador: `PoiseDamageParam`, 0).
- **Daño** (`FUN_140145970`): `poise −= resultado+0x40`, recortada a [0, máx]. Se aplica en `vt+0xf0`
  (`FUN_140136220`), salvo con `b8+0x4b8` bit 52.
  - `resultado+0x40` (`FUN_140137aa0`) = `vt+0x120 · (1 + 0.01·DCP+0xb8·caída)`, × `b8+0x7d0` si es > 0.
  - `vt+0x120` (`FUN_140139160`) = `(golpe+0xa0 · A+0x74 + atacante.vt+0x188 + (s8) A+0x70) · u16 [A+0x90 + 8]·0.01`,
    solo en categoría 1. `A+0x70` = `golpe+0xce`; `A+0x74` = `golpe+0xb4` (1.0).
  - **`golpe+0xa0` en el ataque de un enemigo = 1.0.** `golpe+0x48..` es la copia de la ranura desde `ranura+0x1c`
    (`FUN_14016d070`, stamina.md), así que `golpe+0xa0` = `ranura+0x1c+0x58`. El constructor del bloque
    (`FUN_141b7102e`, leído con `out\py\deob.py`) pone `+0x00..+0x33` = 0, **`+0x34..+0x67` = 1.0**, `+0x68..+0x80` = 1.0,
    `+0x84` = 0, `+0x8c` = −1, `+0x94 / +0x98` = 1.0. En el camino del enemigo nadie toca `+0x58`:
    `FUN_1403a9f80` escribe `[0..12]` y `+0x78`; el reparto de la fila (`FUN_140170710`, `0x141c51d8f` en `deob.py`)
    escribe 3 de los 13 valores y `+0x8c`; `FUN_1403a79e0` suma los SpEffects a `[0..4, 6..9, 12]`.
  - Por tanto, para el enemigo: `vt+0x120 = (1.0 · A+0x74 + 0 + A+0x70) · m` con `A+0x74` = `golpe+0xb4` = 1.0
    (`+0x6c` del bloque), `A+0x70` = `golpe+0xce` = 0 (`+0x86`), y `m` = u16 `+8` × 0.01 de la entrada de
    `ChrMultiplayParam` de `A+0x90` (1.0 si es nula). **Daño de poise de cada golpe del enemigo = 1.0 · m**, × caída
    (`DCP+0xb8`) y × `b8+0x7d0` si es > 0.
  - Que ningún paso posterior (gestor de ataques, `FUN_14016d070`) cambie `+0x58`: **sin confirmar**.
  - En el jugador `golpe+0xa0` sale de un temporal de `FUN_140391fe0` (`r14+0x58`): **sigue sin trazar**.
- **Regeneración** (`FUN_140145ba0`), solo si `b8+0x7e6 == 0` (fuera de la reacción, weapons.md):
  - si en el frame anterior estaba en reacción y la poise es ≤ 0, **se rellena al máximo de golpe**;
  - si no, `poise += EP+0x11c · dt` (`+0x124` con `b8+0x610 ≠ 0`).
- **Efecto** (`FUN_140136570`, categoría 1):
  - `conPoise = poise > 0` (`FUN_1401459d0`). Se anula si `DCP+0x02 == 2`, o si `DCP+0x02 == 1` y `b8+0x7d0 ≤ 0`
    (salvo la condición `b8+0x640`).
  - Con poise y sin `b8+0x5ec` / `b8+0x5e8`:
    - **enemigo: reacción 0, sin animación de daño**;
    - personaje de tipo 2 (`FUN_140203c10`, el jugador): reacción 1 + `FUN_14036c310(…, 4, 0)`.
  - Sin poise: la reacción normal (abajo). El daño se aplica igual en los dos casos.
  - **Hollow Soldier**: poise 0 → **cada golpe produce reacción**.
  - `PoiseDamageParam` (1 fila): **ningún lector encontrado**.

### 7.2 Segundo medidor (`chr+0x1b8`, objeto `chr.e0 vt+0x80`)
- Máximo = `EP+0xfc` + `vt+0x1f8` (`+0x104` con `b8+0x614 ≠ 0`). Regenera `EP+0x100` por s (o `+0x108`)
  (`FUN_140146920` / `FUN_140146ad0`).
- **Daño** = `resultado+0x3c` = `vt+0x118 · (1 + 0.01·DCP+0xb4·caída)`, con `vt+0x118` (`FUN_140139340`) =
  `A+0x6c · u16 [A+0x90 + 10]·0.01` si `cat+0x10 == 0`.
  - No se aplica con `b8+0x5ec`, ni en reacción 11, ni con `b8+0x4b8` bit 51.
  - `A+0x6c` = producto con u16 `+0x14` de `golpe+0x10` (`0x14013784a`); **sin desglosar**.
- Al llegar a 0 (si `EP+0xf8 == 0`) queda roto (`+0xc`). En el siguiente golpe normal fuerza **reacción 10**
  (`FUN_140136570`, `uVar8 = 10`) y en el siguiente update se rellena.
- Nombre: **sin confirmar**. Se comporta como un aguante contra roturas tipo guardia: 4400 en el Hollow Soldier,
  500 en el jugador.

### 7.3 Tipo de reacción
- La reacción por defecto es `DCP+0x12` (también `+0x10`, stamina.md / weapons.md).
- `FUN_140138a40` la remapea con una tabla del gestor de daño.
  - Fila = `FUN_140139690() + FUN_1401399b0(A+0x40)`.
  - La tabla depende de flags: `FUN_1403b55b0` / `55e0` / `5610` / `5840`.
  - Columna = `vt+0x40` de la víctima = **`ECP+0x04`** en el enemigo (`FUN_14031ef90`; Hollow Soldier 3, como el jugador).
  - Reacción 7 con `ECP+0x1a4 == 0` pasa a 4.
  - Qué param es esa tabla: **sin identificar** (no es PDP / EDP / SDP / DCP / DAP).
- La reacción va a la red de animación como `DmgCategory = FUN_140138930(reacción)`:
  0→10, 1→20, 2→30, 3→40, 4→50, 5/6→60, 7→80, 8→90, 9→140, 10→150, 11→160, 12→170, 13→180, 14→190, 15→200, 16→210,
  17→310, 18→320, 19→95, 20→120, 21→100, 22→121.
  - El enemigo la interpreta con **su propia red Morpheme**. Qué animación es cada valor en c1020: **sin confirmar** (§9).
- **Rotura de guardia del enemigo**: estamina `chr+0x1ac ≤ 0` en guardia → reacción 10 (stamina.md).
  - Cuánto daño bloquea: `EP+0x78..` (80 % con escudo).
  - Su estabilidad: `EP+0x74` (0.5).
  - Si el enemigo está bloqueando lo decide su animación / IA: la IA solo empuja la pulsación 9 (`cmd 5206`); que sea la
    guardia, **sin confirmar** (enemy_ai.md §8.7).
- **Daño en partes (jefes)**: `FUN_14017a900` resta también de una parte de `chr+0x168` según
  `BossPartsParam[chr+0x28·100 + parte]` (slot `+0x670`). Al Hollow Soldier no le afecta.

### 7.4 Críticos sobre el enemigo
- Límites de backstab / riposte / rotura de guardia: `ECP +0x100..+0x12c` (weapons.md).
  - Hollow Soldier: backstab 30° / 32° / 0.5 m / 2.0 m; riposte 30° / 45° / 0.5 m / 2.5 m; rotura 25° / 32° / 0.5 m / 2.5 m.
  - Parry: 70° / 70° (`+0x98` / `+0x9c`).
- Otros campos: `ECP+0x17c` = 2.0 s, `+0x184` = 2.0 s, `+0xed` = 1, `+0x308` = 0 (pulsaciones en el suelo), `+0x304` = 1.0.
- Giro hacia el que parrea: `ECP+0x1a0` = 6.283 rad/s (360°/s).
- φ por subtipo físico (`ECP +0x270/+0x274/+0x278`): **1.0 / 1.35 / 1.0**.
- La víctima de un agarre usa su propio anibnd (weapons.md). **Tiempos del enemigo sin extraer.**

## 8. Otros campos usados del enemigo
**`EnemyCommonParam`** (`chr+0x40`):
- `+0x04` columna de reacción (§7.3).
- `+0x84` / `+0xac` / `+0xb0` = `vt+0xa0` (`FUN_14031ef10`): `+0x84 == 1` → 0; si no, `max(+0xac, +0xb0)` = 2.0.
- `+0x16c` = `vt+0x138` (`FUN_14031ef60`): `x − 0.01·+0x16c·x` = 0.
- `+0x19c` = `vt+0x218` ×0.01 = 0.
- `+0x78 / +0x7c / +0x80` = `vt+0x20`: factor por fracción de vida (`FUN_14031f480`); 0 en el Hollow Soldier.
- `+0x94` (copiado a `chr+0xb0+0x44`).
- Qué representan: **sin identificar**.

**`EnemyMoveParam`** (`chr+0x48`), lectores de la calculadora. Nombres **sin confirmar** salvo que comparten offset con
`ChrMoveParam`:

| Campo | Lector | Hollow Soldier |
|---|---|---|
| `+0x120` | `vt+0x100` = `FUN_14031fae0` (× `DAT_1410b0d38` si `vt+0x28`; o `b8+0x3a4` con `b8+0x4b8` bit 5) | 8.0 |
| `+0x124` | `vt+0x108` | 12.0 |
| `+0x128` | `vt+0x110` | 5.6 |
| `max(+0x13c, +0x140)` | `vt+0x120` (o `b8+0x3b0/+0x3b4`) | 2.0 |
| `max(+0x144, +0x148)` | `vt+0x128` | 2.0 |
| `+0x1f0` | `vt+0x78` | 1.5 |
| `+0x1f4..+0x200` | umbrales de carga de `vt+0x88` (`FUN_14031ed40`, `ChrEquipLoadParam`) y `vt+0x80` | 7 / 10 / 15 / 19 |
| `+0x104` / `+0x108` / `+0x110` / `+0x114` | distancias de paso y rodada (`vt+0xe0..+0xf8`, equipload.md) | 3.2 / 0 / 0 / 0 |
| `+0x2c` / `+0x30` con `+0x20` | **giro**: `lerp(+0x2c, +0x30, v / +0x20)` rad/s (`FUN_1403a28b0`, `EnemyMovementCtrl` vt `+0x68`, enemy_ai.md §8.5). `+0x34 / +0x38` no los lee esa función | 4.538 → 4.887 rad/s entre 0 y 3.2 |
| `+0x54` / `+0x58` con `+0x48`; `+0x78` | giro en el estado 3 / con `+0x124` (misma función) | 4.538 → 4.887 entre 0 y 6.5; 4.712 |

- La velocidad real sale del movimiento raíz de sus animaciones (movement.md), igual que en el jugador.

## 9. Animaciones del enemigo
Todo está en [enemy_ai.md](enemy_ai.md) §8:
- TAE real: `gamedata:/timeact/chr/c102000_pl.tae`; anibnd `gamedata:/morpheme4/chr/c1020.anibnd.dcx` (235 `.nsa`).
- **Ventana de golpe** = evento 2200; `parámetro[0]` = ranura `k`, y la ranura `k` lleva la fila EDP `k` de la entrada
  de comportamiento (§8.3). Tiempos de cada ataque del Hollow Soldier en la tabla de §8.3.
- **Volumen**: cápsula o esfera de la fila EDP (`+0x30..+0x38`) en una dummy del modelo (§8.4).
- Animaciones de daño y muerte con su duración (§8.6). Qué `DmgCategory` va a cada una: **sin confirmar**.
- Los grupos `1102 0v xxx` sí van por variante: la variante `v` usa el juego de comportamientos `10200v0`, cuyas
  entradas llevan esas filas (enemy_ai.md §8.2, tabla de variantes).

## 10. Muerte
- Orden en `FUN_140161e10`: se elige y crea el drop (`FUN_14016dd70`); si `FUN_14010c720` (sin identificar) devuelve nulo,
  se pagan las almas por `FUN_1402024c0` y se lanza el efecto de `DeadTypeParam`; luego `FUN_1403148d0` (curación del atacante).
- Por la otra vía, `FUN_14013d560` paga las almas y `FUN_140202fe0` lanza el efecto `DeadTypeParam+0x2c`
  (13000 → 290000120) con `FUN_1404415a0`.
  - Fila de muerte de tipo 1/2: `EP+0x40/+0x44`. Si el id es 100010: `FUN_140164da0·100 + 100010`.
- Resto de `DeadTypeParam` (floats `+0x00..+0x20`): **sin decodificar**.
- Desaparición del cuerpo (tiempo, disolución): **sin trazar**.

## 11. Enemigo de referencia: Hollow Soldier 102000 (c1020, variante 0)
- **Identificación**: el lote `EP+0x134` = 10200000 suelta el set "Hollow Soldier" y el "Hollow Soldier Shield"
  (`itemname.txt`), y el generador 10200000 de m10_10 apunta a EnemyParam 102000.
  - **La regulation no lo nombra** (`EP+0x15c` = 0).
  - Las otras variantes de c1020 cambian el arma del lote:

| Variante | Armas del lote |
|---|---|
| 102010 | Bastard Sword |
| 102020 | Halberd |
| 102030 | Estoc + escudo |
| 102040 | Bastard Sword + Long Bow |

- **Filas para el datapack (NG, sin red)**:
  - `EnemyParam 102000`: PV 320 (NG+1..7: 672, 807, 941, 1008, 1076, 1143, 1277), estamina 1240.
    - Defensa física 317; absorción magia 15 %, rayo / fuego / oscuro 30 %.
    - Guardia: estabilidad 50, bloquea el 80 % de todo.
    - Poise 0 (regen 1.47/s); segundo medidor 4400 (regen 2.2/s).
    - Ataque ×1.0; almas 90 (`ChrRoundDropSoulParam` 2); lote 10200000.
    - `ChrMultiplayParam` 10; `DeadTypeParam` 13000 / 13000.
  - `EnemyCommonParam 102000`: columna de reacción 3; φ 1.0 / 1.35 / 1.0; ángulos de crítico y parry de §7.4.
  - `EnemyMoveParam 102000`: §8.
  - `EnemyDamageParam 110200010..110200710` + sus `DamageCtrlParam` (§4.2).
  - `ItemLotParam2_Chr 10200000` (§6).
  - `DamageAdjustParam` de enemigo (damage.md §4): μ físico 6, D físico 6, elementos 6.
- **Daño del jugador al Hollow Soldier** (damage.md §5, Longsword +0 con Fue 16 / Des 11, AR 158.25, ligero 1, piso 60):
  - subtipo 0 (φ 1.0): `(949.5 − 317)/6 = 105.4` → **105**;
  - con un DCP de subtipo 1: ×1.35 → 142.
  - Con 320 PV: 4 golpes en NG.
- **Daño del Hollow Soldier al jugador**: §4.3 (98 / 95 / 69 con 118 de físico).

## Sin confirmar / pendiente
- Daño de poise del golpe del **jugador** (`golpe+0xa0`, `FUN_140391fe0`). El del enemigo es 1.0 por construcción (§7.1),
  falta confirmar que nada lo cambie después. `PoiseDamageParam` sin lector.
- Nombre y propósito del segundo medidor (`chr+0x1b8`, `EP+0xfc..+0x108`), y el desglose de su daño (`A+0x6c`).
- Tabla de remapeo de reacciones (`FUN_1403b5610` y compañía) y qué animación de c1020 corresponde a cada `DmgCategory`.
- Cuándo bloquea el enemigo (la pulsación 9 de la IA y la red Morpheme); la regeneración ya está (`EP+0x30`).
- Qué estado es cada tipo 5..12 del golpe y cada absorción `EP+0xb0..+0xbc`.
- Flags del drop: `+0x22` (bandera), máscara `+0xc`, `FUN_1401ac180`, y el bono (`FUN_1401e2e40`). Bytes `+0x0e` / `+0x18`.
- `EP+0x138` / `DAT_1416148f0 vt+0x58` (modo que cambia de lote).
- `b8+0x434` (factor de almas) sin efectos; tabla de red de `FUN_140203f60`.
- Campos de EP sin lector: `+0x10`, `+0x14`, `+0x30`, `+0x48..+0x60`, `+0x10c`, `+0x110`, `+0x12c`, `+0x13c`. Campos de
  `GeneratorParam` / `GeneratorRegistParam` salvo `+0x08` / `+0x00` / `+0x88`.
- Desaparición del cadáver y resto de `DeadTypeParam`.
- Qué `DmgCategory` lleva a cada animación de daño de c1020 (enemy_ai.md §8.6).
