# Agilidad (AGL)

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj`. Volcados de esta nota en
`D:\Reversing\DS2\out\agl\` (`derive.c`, `pcalc.c`, `a4.c`, `f6240.c` ya existía en `out\`, `disp_deob*.txt`, …).
Datos: regulation (`PhysicalStatsPerLevelStatValuesParam` PSPL, `MenuStatsParam` MSP, `PlayerLackOfStatsParam` PLSP,
`ChrCommonParam` 100, `ChrMoveParam` 100, `ItemParam`) y `TimeAct__chr__c000100_pl.tae`.
Tabla exportada: `src/main/resources/data/soulsmode/ds2/agility.json` (`tools/re/agility.py`).

Resumen:
- La AGL **no es un atributo guardado**: es un índice `i = (Sintonía + 3·Adaptabilidad) / 4` sobre las stats
  efectivas, con el que se leen varias columnas de PSPL. El número que enseña el menú (85..120) sale de otra tabla
  (MSP) con el mismo índice y **no entra en ninguna de las fórmulas de esta nota**.
- Lo que hace: alarga la invulnerabilidad del roll, del paso atrás y del roll de aterrizaje (fracción del evento TAE
  **110300**), acelera el cambio de arma (`WeaponChangeSpeed`), el uso de objetos (`ItemUse_Speed`) y la subida de
  guardia (`GuardSpeed`, ya en weapon_arts.md §8.5).

## 1. El índice de AGL — `FUN_14038d790`
`FUN_14038d570` (stats.md) recalcula el bloque de stats; `FUN_14038d790(bloque+0x2c, efectivas)` llena las derivadas
desde la copia **efectiva** (la que pasa por `FUN_1401ffcc0`, sin analizar: equipo/anillos).

```
i = (stat[3] + 3·stat[8]) / 4          // slot 3 = Sintonía, slot 8 = Adaptabilidad (stats.md); división entera
si i ∉ 1..99: i = 1                    // no pasa: FUN_14038d570 rechaza stats fuera de 1..99
fila = PSPL[i]                         // thunk_FUN_141b7e213(gestor+0x18, i); fila = valor
```
- En el mod: `Attributes` índice 3 = Sintonía, índice 8 = Adaptabilidad.
- Rango del índice: 1 (1/1) a 99 (99/99).

Derivadas que usan este índice (desplazamiento dentro de las derivadas; bloque = derivadas + 0x2c):

| Derivada | Bloque | Valor | Getter | Uso (§) |
|---|---|---|---|---|
| `+0x68` | `+0x94` | PSPL f32 `+0x54` × 0.01 | `FUN_14038d250(b, 4)` | resistencia, §5 |
| `+0x7c` | `+0xa8` | PSPL f32 `+0x68` × 0.01 | `FUN_14038d280(b, 0)` | i-frames del paso atrás, §2 |
| `+0x80` | `+0xac` | PSPL f32 `+0x6c` × 0.01 | `FUN_14038d280(b, 1)` | i-frames del roll, §2 |
| `+0x84` | `+0xb0` | PSPL f32 `+0x70` × 0.01 | `FUN_14038d280(b, 2)` | i-frames de `JumpCtrl`, §2 |
| `+0x88` | `+0xb4` | PSPL f32 `+0x74` | `FUN_14038d500(b, 0)` | `WeaponChangeSpeed`, §3 |
| `+0x8c` | `+0xb8` | PSPL f32 `+0x78` | `FUN_14038d500(b, 1)` | `ItemUse_Speed`, §4 |
| `+0x90` | `+0xbc` | PSPL f32 `+0x7c` | `FUN_14038d500(b, 2)` | `GuardSpeed` (weapon_arts.md §8.5) |

- 0.01 = `DAT_1410acb08` (leído del exe: 0.00999999977).
- `FUN_14038d250(b, k)` = `b+0x84+4k`, `FUN_14038d280(b, k)` = `b+0xa8+4k`, `FUN_14038d500(b, k)` = `b+0xb4+4k`
  (`FUN_14038b990(chr+0x490)` devuelve el bloque). Llamadores (Xrefs): `d250` solo `FUN_140381350`; `d280` solo
  `FUN_140381d10`; `d500` solo `FUN_140381a60`, `FUN_140381ab0` y el de `GuardSpeed` (`0x14038f8bb`).
- Las demás derivadas de `FUN_14038d790` usan otros índices (Vigor, Sintonía para la casta, Int/Fe, …).

Valores (de `agility.json`; índice → valor):

| i | Menú | Paso `+0x68` | Roll `+0x6c` | Salto `+0x70` | Cambio de arma | Uso de objeto | Guardia |
|---|---|---|---|---|---|---|---|
| 1 | 85 | 0.20 | 0.25 | 0.25 | 1.20 | 0.975 | 1.00 |
| 5 | 85 | 0.32 | 0.362 | 0.362 | 1.20 | 0.97 | 1.00 |
| 10 | 90 | 0.44 | 0.475 | 0.475 | 1.20 | 0.97 | 1.01 |
| 15 | 95 | 0.54 | 0.568 | 0.568 | 1.21 | 1.08 | 1.03 |
| 20 | 100 | 0.64 | 0.662 | 0.662 | 1.22 | 1.20 | 1.05 |
| 25 | 105 | 0.70 | 0.718 | 0.718 | 1.24 | 1.20 | 1.08 |
| 30 | 110 | 0.76 | 0.775 | 0.775 | 1.26 | 1.20 | 1.12 |
| 40 | 111 | 0.80 | 0.812 | 0.812 | 1.31 | 1.20 | 1.22 |
| 50 | 112 | 0.84 | 0.85 | 0.85 | 1.36 | 1.20 | 1.32 |
| 70 | 115 | 0.92 | 0.925 | 0.925 | 1.40 | 1.20 | 1.40 |
| 99 | 120 | 1.00 | 1.00 | 1.00 | 1.45 | 1.20 | 1.50 |

Ojo: el menú se queda en 85 de i = 1 a 5, pero las fracciones siguen subiendo. **Hay que usar el índice, no el número
del menú.**

## 2. Invulnerabilidad de las esquivas

### 2.1 Quién quita la vida: el contador `b8+0x608`, no el `+0x5e8`
- `PlayerDamageActionCtrl` vt `+0xe0` = `FUN_140139b60` (vtable `0x1410c9ce8`, entrada `0x1410c9dc8`). En
  `FUN_1401345c0` (golpe al jugador) decide si se llama a `FUN_140135960` (aplicar el daño).
  - El daño entra **solo si `b8+0x608 == 0`** y el bit `0x400000000000` de `b8+0x4b8` está apagado.
- Quién sube `b8+0x608` (`FUN_140326240`, `ChrMorphemeTimeActTrackDamageActionCtrl`):
  - **110100** (`0x1ae14`): `+0x5f0` y `+0x608` mientras dura el evento.
  - **110300** (`0x1aedc`): `+0x5f0` y `+0x608` **durante una fracción del evento** (§2.2).
  - 110000 con `b8+0x18 == 3`.
- **Corrección (poise.md §5.3)**: `FUN_140136570` lee `+0x5e8` directamente: durante el 110500 un golpe que entra da
  **reacción 0, sin animación** (salvo `DCP+0x02 == 2`), no la reacción normal. Lo de abajo sobre `FUN_140139380` es
  otro camino (fuerza la reacción 1 por `DCP+0x2c`).
- **110500** (`0x1afa4`) sube `+0x5e8`, que **no impide el daño**. Solo lo lee `FUN_140139380`: el camino de "golpe
  aguantado por la estabilidad" (resultado 1 + `FUN_14036c310(…, 4, 0)`, enemies.md "Efecto") exige
  `+0x5e8 == 0` y `+0x5ec == 0`. O sea, durante el 110500 un golpe que entra provoca siempre la reacción normal,
  aunque haya estabilidad.
- **Corrección a animations.md** (tabla "Eventos de invulnerabilidad") y a `Dodge.java`: la ventana 110500 no es la
  de i-frames.

### 2.2 El evento 110300 y la fracción `b8+0xa4` — `FUN_140326240`, caso `0x1aedc`
```
t = tiempo actual del evento (ev+0x1c);  s = max(0, inicio ev+0x20);  e = fin ev+0x24
p = 0 si t ≤ s;  1 si t ≥ e;  (t − s)/(e − s) en medio
si el evento terminó (ev+0x29) o p ≥ b8+0xa4:  quitar (+0x5f0, +0x608 −1) si estaba puesto
si no, y no estaba puesto:                      poner (+0x5f0, +0x608 +1)
```
- Invulnerable en **[s, s + a·(e − s))**, con `a = b8+0xa4`. Con a ≥ 1, el evento entero; con a ≤ 0, nada.
- Los tiempos son los del reloj de la animación: si la esquiva se reproduce más lenta (equipload.md), la ventana
  también.

### 2.3 Quién escribe `b8+0xa4` (FindStoreF `MOVSS [R+0xa4]`)
- **Roll** — `PlayerDodgeCtrl` vt `+0x20` = `FUN_14038e440` (también fija la variante `b8+0x9f` y el vector `b8+0xb0`):
  - con la petición 7 activa (`thunk_FUN_141ca6c2e(…, 7)`): `a = 1.0` y coste 0;
  - si no: `coste = FUN_1403765c0(chr)` (coste de rodar, `ChrCommonParam+0x2c`, animations.md) y
    `a = calc vt+0xd0(coste)`.
- **Paso atrás** — `PlayerDodgeCtrl` vt `+0x28` = `FUN_14038e5d0`: `coste = FUN_1403766d0(chr)` (`+0x28`) y
  `a = calc vt+0xd8(coste)`. Sin excepción por la petición 7.
- **`PlayerJumpCtrl` vt `+0x48`** = `FUN_140330370` (vtable `0x1410e5438`): `a = calc vt+0x130()`.
- `ChrDodgeCtrl` / `EnemyDodgeCtrl` usan `FUN_140328b10` / `FUN_140328c20` con la calculadora de cada uno.

Calculadora del jugador (`PlayerGameParamCalculator`, vtable `0x1410e4ec8`):
- `vt+0xd0` = `FUN_140380280` → `vt+0x270(1, coste)`; `vt+0xd8` = `FUN_1403802a0` → `vt+0x270(0, coste)`;
  `vt+0x130` = `FUN_140380da0` → `vt+0x270(2, 0)`.
- `vt+0x270` = `FUN_140381d10(k, coste)`:
```
a = FUN_14038d280(bloque, k)                          // §1: k 0 paso, 1 roll, 2 salto
  × (1 − pen)                                         // vt+0x68 = FUN_14031e660 = 1 − vt+0x48
pen = FUN_1403811f0({fila 0x12, f, g}):
  f = falta de stats del equipo (objeto chr vt+0x118 → vt+0x68, +0x350; FUN_14031fc10 modo 2)
  g = FUN_14031fcd0 → thunk_FUN_141ca621f(estamina chr+0x1ac, coste):
      0 si coste ≤ 0 o estamina ≥ coste;  (coste − estamina)/coste si 0 < estamina < coste;  1 si estamina ≤ 0
  pen = clamp(0.01·[ (30 + 20·f) si f > 0 ] + 0.01·[ 50·g si g > 0 ], 0, 1)   // PLSP fila 18 = (30, 50, 0, 50)
```
- `thunk_FUN_141ca621f`, desofuscado con `out\bs_scripts\deob.py`: es la misma función que `FUN_140376510`
  (damage.md).
- Con el equipo sin falta y la estamina ≥ coste, `pen = 0` y `a` es la columna de PSPL.
- `f` (`FUN_14034a830`) = clamp(Σ de 4 piezas `FUN_14034d150`, 0, 1). Cada pieza da
  clamp(Σ max(0, 1 − stat/req), 0, 1) sobre Fuerza, Destreza, Int y Fe (stats 4..7), con `req` = u16 `+0x2c..+0x32`
  de la fila de la pieza; si `req` = 0, esa stat no cuenta. Qué param es esa fila: sin confirmar.
- Para el salto, `coste = 0`, así que `g = 0`.

### 2.4 Ventanas por animación (TAE `c000100_pl`; clip en segundos)

| Animación | 110100 | 110300 | 110500 (no es i-frame) | Invulnerable |
|---|---|---|---|---|
| 120020/21 roll, 120030/31 roll Tired | −0.1667..0.1333 | 0.1..0.5667 | 0.1..0.6667 | [0, 0.1333) ∪ [0.1, 0.1 + 0.4667·a) |
| 120040 roll Failed | — | — | — | nada |
| 110011/31 paso atrás | — | 0.1333..0.5 | 0.1667..0.3333 | [0.1333, 0.1333 + 0.3667·a) |
| 110051/71 paso atrás Tired | — | 0.3333..0.6667 | 0.3333..0.5 | [0.3333, 0.3333 + 0.3333·a) |
| 110081 paso atrás Failed | — | — | — | nada |
| 140020 `a00_14_0020 RollLanding` | — | 0.0..0.4333 | — | [0, 0.4333·a) |

- Roll: `a` = columna `+0x6c`. Como 0.1 + 0.4667·0.25 > 0.1333, sin penalización la ventana es una sola:
  **[0, 0.1 + 0.4667·a)**, de 0.217 s (i = 1) a 0.567 s (i = 99).
- Paso atrás: `a` = columna `+0x68`, de 0.207 s a 0.5 s de final (normal).
- La variante Tired o Failed no cambia `a`: la fórmula es la misma, y Failed no tiene 110300.
- El 110100 del roll empieza antes del clip, así que vale desde el primer frame.
- En todo `c000100_pl.tae`, solo estos 9 clips tienen 110300 (los 8 de esquiva de la tabla y el 140020).

## 3. Cambio de arma — `WeaponChangeSpeed`
- `FUN_14035dfe0` escribe el CP de índice `0x1d` de su control = `calc vt+0x210` = `FUN_140381a60` =
  `FUN_14038d500(bloque, 0)` = **PSPL[i] `+0x74`** (1.20 → 1.45). Sin penalización.
- Es el `WeaponChangeSpeed` (CP `0xcf`) de weapons.md "Animación del cambio": las animaciones `a00_08_00x0` (0.5 s)
  se reproducen a esa velocidad. Esto resuelve el "sin sacar" de weapons.md.

## 4. Uso de objetos — `ItemUse_Speed`
- `FUN_14032f750` (al empezar el uso, desde `FUN_14032f450`) y `FUN_14032fa00` (estado 2, si `ctrl+0x24 == 0`)
  escriben `b8+0x6e4 = calc vt+0x200 × calc vt+0x208 × ItemParam f32 +0x38`.
- `FUN_14015fbf0` lo pasa al CP 1 de su control; los nombres los registra `FUN_14015f9f0`: 0 `ItemUseCategory`,
  **1 `ItemUse_Speed`**, 2 `IsDrinkUp`, 3 `IsItemUseLoop`.
- `vt+0x200` = `FUN_140381ab0`:
```
v = |vector de chr+0x480 vt+0x1c8|
t = clamp(v / ChrMoveParam+0x20, 0, 1)                 // 4.295
vt+0x200 = lerp(ChrCommonParam+0x2fc, +0x300, t) × PSPL[i] +0x78     // 1.0 → 0.6
```
- `vt+0x208` = `FUN_140380ca0` = `vt+0x70(0x1a, f)` = 1 − pen, con PLSP fila 26 = (0, 20, 0, 0):
  `pen = 0.01·20·f` si `f > 0`. `f` es la falta de stats del equipo de §2.3.
- `ItemParam +0x38`: 1.0 en 1122 de 1275 filas, 0.7 en 132 (por ejemplo, Efigie humana 60151000), y valores
  sueltos de 0.55 a 1.3.
- PSPL `+0x78`: 0.975 (i = 1), 0.97 (2..10), y sube hasta 1.2 a partir de i = 20.
- Qué nodo de `c0001.nmb` usa `ItemUse_Speed` como velocidad: no está decodificado (ver Sin confirmar).

## 5. Resistencia con el mismo índice
- `FUN_140381350` (calc `vt+0x1d8`, vector de 10 resistencias del jugador) suma `FUN_14038d250(b, 4)` =
  PSPL[i] `+0x54` × 0.01 (0.10 → 0.30) al elemento 9, que va a `salida+0x24`.
- El menú enseña con el mismo índice MSP `+0x2e` en el renglón 909051 "RES maldi." (§6). Que el elemento 9 sea la
  maldición es una deducción por el índice compartido: sin confirmar.
- No es parte de la AGL del juego; se anota porque usa el mismo índice.

## 6. Menú
- **Texto**: `common.fmg` 909038 = **"Agilidad"** (es_mx) / "AGL" (EN) (classes.md).
- Lo usan `FUN_1400cc620` (pantalla de subir nivel: columna 1, renglón `0xb`; valor actual y valor previsto),
  `FUN_1400a23e0` y `FUN_1400c8740` (FindScalar `0xddeee`).
- **Valor**: el renglón lee el campo `+0x3a` de la estructura que arma `FUN_1402005a0` (→ `0x14038c1f0`, ofuscado;
  seguido con `deob.py`, `out\agl\disp_deob_v.txt`):
  - `0x141b5ad8b..0x141b5ad9a`: `j = (stat[3] + 3·stat[8]) / 4`, con `edi = 3·[rbp−0x30]` (`0x141b77252`) y
    `[rbp−0x50]` = slot 3;
  - `0x141ca247e`: `+0x3a` = **MSP[j] u16 `+0x32`** (`FUN_140358cf0` = gestor `+0x5b0`, MenuStatsParam, regulation.md).
- Con el mismo índice salen también `+0x36` = MSP `+0x2e` (renglón "RES maldi.") y `+0x38` = MSP `+0x30`.
- Valores de MSP `+0x32`: 85 (i ≤ 5), y luego +1 por índice hasta 110 (i = 30). A partir de ahí sube despacio:
  111 (40), 112 (50), 114 (60), 115 (70), 117 (80), 118 (90), 120 (99). Lista completa en `agility.json` (`shown`).
- Las stats del menú pasan por `FUN_1401ffcc0` (llamado dentro de `0x14038c1f0`), igual que las derivadas.

## 7. Lo que la AGL no toca
- Las 7 derivadas con el índice de AGL (§1) son todas las que tiene `FUN_14038d790`, y cada getter tiene los
  llamadores listados.
- Llamadas a `vt+0x200` / `vt+0x210` (FindStoreF `CALL [R+0x200]` / `[R+0x210]`, `0x140100000..0x140700000`):
  - desde `chr+0x398` solo salen las de §3 y §4 (`FUN_14035dfe0`, `FUN_14032f750`, `FUN_14032fa00`);
  - las demás (`FUN_14031ad70`, `FUN_140160230`, `FUN_1401602b0`, `FUN_140323960`, `0x1405…`) pasan argumentos que
    la calculadora no recibe. Por eso parecen de otras clases, aunque no se comprobó una por una.
- **No dependen de la AGL** (ninguna usa esas derivadas):
  - la velocidad de casteo: el índice es `(2·Sintonía + Int + Fe)/4`, PSPL `+0x34` (casting.md §6);
  - la recarga de ballesta: va dentro del clip de disparo (crossbow.md).
- Las escaleras (CP `Ladder*Speed`): no se buscó quién escribe esos CP.
- **El número del menú (MSP) no entra en ninguna de las fórmulas de arriba.** No se buscaron otros lectores de
  MenuStatsParam.

## 8. Exportado — `agility.json` (`tools/re/agility.py <salida>`)
- `rows[i−1]`: `index`, `shown` (MSP `+0x32`), `stepIframeFraction` (`+0x68`·0.01), `rollIframeFraction`
  (`+0x6c`·0.01), `jumpIframeFraction` (`+0x70`·0.01), `weaponChangeSpeed` (`+0x74`), `itemUseSpeed` (`+0x78`),
  `guardSpeed` (`+0x7c`, el mismo que `guardLayer.guardSpeedStat` de weapons.json), `resist9` (`+0x54`·0.01).
- `dodgeLackRow` PLSP 18 = [30, 50, 0, 50]; `itemUseLackRow` PLSP 26 = [0, 20, 0, 0].
- `itemUseMoveSpeed` = [`ChrCommonParam+0x2fc`, `+0x300`] = [1.0, 0.6]; `itemUseMoveRef` = `ChrMoveParam+0x20` = 4.295.
- `tae`: ventanas 110100 / 110300 / 110500 de 120020/21/30/31/40, 110011/31/51/71/81 y 140020.

Para el mod:
- i-frames de cada esquiva = 110100 ∪ la parte `a` del 110300 (§2.4), en vez de la ventana 110500.
- `a = fracción[i] × (1 − pen)`. Sin requisitos de stats en armaduras, `f = 0`.
- El término de estamina `g` solo cuenta si la esquiva empieza con estamina por debajo del coste (y > 0: con
  estamina ≤ 0 no hay esquiva, animations.md).

## Sin confirmar
- **Qué es la petición 7** del roll, que da `a = 1.0` y coste 0. animations.md: "la 7 depende de `chr.b8+0x52c`".
- **Cuándo corre `PlayerJumpCtrl` vt `+0x48`**, y si su `b8+0xa4` es el que sigue vigente en `RollLanding` (140020).
  No está trazado el orden de los escritores de `+0xa4`.
- **Escaleras**: no se comprobó si sus velocidades (`LadderMoveUpSpeed`, …) dependen de algo de la AGL.
- **Qué param es la fila de cada pieza** que usa la falta de stats del equipo (`FUN_14034d150`, u16 `+0x2c..+0x32`):
  ArmorParam no tiene esos valores en esos desplazamientos.
- **Qué hace la red con `ItemUse_Speed`**: no se decodificó el nodo de `c0001.nmb` que lo usa como velocidad.
- **`v` de `vt+0x200`** (`chr+0x480 vt+0x1c8`): por el uso es la velocidad del personaje, no está comprobado.
- **Quién lee `b8+0x5f0`**, que sube junto con `+0x608` en 110100 / 110300.
- **Elemento 9 de `vt+0x1d8` = resistencia a maldición**: deducido por el índice, sin lector comprobado.
- **El campo MSP `+0x30`** (display `+0x38`): no se vio en qué renglón sale.
- **`FUN_1401ffcc0`**: qué ajusta en las stats efectivas antes de calcular la AGL (stats.md).
