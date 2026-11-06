# Reacción al golpe del personaje (c0001): animación, duración y bloqueos

Objetivo: cuánto dura la reacción de daño **del jugador de DS2** a un golpe normal, y cuándo vuelve a poder moverse y
atacar, para usarlo como tiempo de aturdimiento de los mobs en el Modo Souls.

Salidas y scripts: `D:\Reversing\DS2\out\hitreact\` (Ghidra headless, solo lectura: `d1.c`…`d7.c`, `vt*.txt`,
`p391fe0.txt`). Los scripts de análisis están en el scratchpad de la sesión; usan `tools/re/weapons.py`, `params.py`,
`D:\Reversing\DS2\tools\nmb.py` y `out\plunge\grip_nodes.py` / `out\grip\grip_nodenames.py`.

## 1. Cadena completa, del golpe a la animación

1. **Reacción base** (`FUN_140136f80` = `ChrDamageActionCtrl vt+0x58`):
   `reacción = FUN_140138a40(DCP+0x10, nivel)`, con `nivel = info+0x40`.
   - `info` lo llena `FUN_140136ab0` (`vt+0x50`): `info+0x40 = golpe+0xd4`.
   - `golpe+0xd4` es `+0x8c` del bloque de la ranura (`golpe+0x48..` = ranura+0x1c, stamina.md).
   - En el jugador lo escribe `FUN_140391fe0` (`p391fe0.txt`, `0x1400de604`): `bloque+0x8c = [rdi+0xc]`, con
     `rdi = [rsp+0x50]` = la fila de `DamageCtrlParam` que devolvió `FUN_1403b5500(mgr, PDP+0x00)` (`0x141b4acc1`).
   - **Por tanto `nivel = DCP+0x0c`**, el mismo campo que la columna de `HitBackMatrixParam` (wall_bounce.md).
   - Con `nivel < 0` no hay remapeo (`FUN_140138a40`, `test r8d / jns`).
2. **Corrección a enemies.md §7.3**: `DCP+0x12` solo se usa con la víctima **muerta** (`FUN_140136570`, rama
   `vt+0x1b0 == 0`). Con la víctima viva manda `DCP+0x10`.
3. **Tabla de remapeo, identificada**: `FUN_140138a40` lee una tabla del gestor `DAT_1416148f0+0x10 → +0xb40`.
   Los nombres salen de `FUN_14048ba80` (índice i → `+0x10·(i+1)`):

   | Getter | Gestor | Param | Cuándo |
   |---|---|---|---|
   | `FUN_1403b5610` | `+0xd0` (12) | **`DamageMotionWeightParam`** | víctima viva (`vt+0x1b0`) |
   | `FUN_1403b5840` | `+0xe0` (13) | `DeadDamageMotionWeightParam` | víctima muerta |
   | `FUN_1403b55b0` | `+0x100` (15) | `DamageMotionLadderWeightParam` | `b8+0x4c8` bit 2 |
   | `FUN_1403b55e0` | `+0x110` (16) | `DamageMotionRopeWeightParam` | `chr+0xc0 +0x4e` bit 1 |

   - Fila = `FUN_140139690(reacción) + FUN_1401399b0(nivel)` = `1000·(reacción+1) + 10·(nivel+1)`.
   - Columna = `vt+0x40` de la calculadora de la víctima (`chr+0x398`). Cada fila tiene 8 bytes, y el byte de la columna
     es la reacción nueva. Si sale 7 y `CommonParam+0x1a4 == 0`, pasa a 4.
   - **Columna del jugador**: `PlayerGameParamCalculator vt+0x40` = `FUN_140380b30`.
     - Calcula `t = (chr.b0+0x28 − CCP+0xa4) / (CCP+0xa8 − CCP+0xa4)`, acotado a [0, 1].
     - Devuelve 2 si `t < CCP+0x08`, 3 si `t < CCP+0x0c` y 4 en otro caso.
     - Con `ChrCommonParam 100` (0, 200; 20, 40), **siempre 2**.
   - Columna de un enemigo: `ECP+0x04` (enemies.md). En la regulation:

     | `ECP+0x04` | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
     |---|---|---|---|---|---|---|---|---|
     | Filas | 3 | 3 | 9 | 117 | 57 | 8 | 20 | 8 |

   - Las columnas 0 y 5–7 dan 0 en las filas de golpes normales: en DS2, esos enemigos no reaccionan.
4. **Velocidad y distancia** (`FUN_14013b4d0`, desensamblado `0x14013b7b6..0x14013b9ef`). `rbx` = resultado del golpe,
   `[rbx+0x10]` = fila PDP, `[rbx+0x18]` = DCP, `rbp` = `CommonParam` de la víctima.
   - `b8+0x104` (CP `DmgDuration`):
     - reacciones 0–3, 5–7, 15 y 16: `calc vt+0x140(PDP+0x2c)`;
     - 8, 18 y 19: `DCP+0x18`;
     - 9 y 17: `vt+0x140(CCP+0xf0)`;
     - 4 y 11: `vt+0x140(CCP+0x174)`, y además `b8+0x100 = CCP+0x178`;
     - 10 y 14: `vt+0x140(CCP+0x174)`, y además `b8+0x100 = CCP+0x184`;
     - 12 y 13: `vt+0x140(PDP+0x2c)`.

     Sale de la tabla de saltos en `0x14013ba9c`.
   - `vt+0x140` del jugador (`FUN_1403807c0`):
     `x + clamp(P+0x164 · n − lerp(P+0x174, P+0x178, t), 0, P+0x168)`.
     - `t` va de 0 a 1 cuando `chr.b0+0x28` pasa de `P+0x16c` a `P+0x170`.
     - `n` = `b8+0x10d`: lo incrementa cada golpe con `resultado+0x31 ≠ 0` (`0x14013b8ef`). Se pone a 0 en `0x140135d24`
       fuera de la reacción (`[r9+0x3e]` bit 1 a 0 y sin daño pendiente, `b8+0x4c0` bit 49).
     - Con `P` = `PlayerCommonParam` fila 0 (0.15, 1.5, 40, 60, 0.0, 0.2), el valor es
       `x + clamp(0.15·n − 0.2·t, 0, 1.5)`.
     - El primer golpe (n = 0) da `x`.
   - `vt+0x140` del enemigo (`FUN_1401f9b10`) devuelve `x` tal cual.
   - `b8+0x718` (CP `DmgDistance`) = `vt+0x138(PDP+0x28) · (1 + 0.01 · DCP+0xbc · resultado+0x2c)` + un extra si ya
     estaba en reacción. Con la reacción 4, 10, 11 o 14 se usa `CCP+0x170` en lugar de `PDP+0x28`. `vt+0x138` es
     `x − 0.01·CCP+0x16c·x`, y con el jugador (`CCP+0x16c` = 0) queda igual.
   - Otros CP: `DmgDirection` = `b8+0x714 · 57.2958` (grados); `DmgSlashOrThrust` = 1 si `b8+0x710 == 1`
     (`PDP+0x32`, o el subtipo `DCP+0x00` si vale 0xff); `DmgDownOrUp` = `b8+0x70c` (`resultado+0x28`).
   - `DmgCategory` = `FUN_140138930(reacción)` (enemies.md §7.3). Todos los CP los publica `FUN_14035e820`
     (`ChrDamageMotionCtrl`, nombres en `FUN_14035ef90`): índice 0 `DmgCategory`, 1 `DmgDirection`, 2 `DmgDistance`,
     3 `DmgDuration`, 6 `DmgDownOrUp`, 7 `DmgSlashOrThrust`.
   - `DmgDuration` se publica en **cada frame**. Vale:
     - `b8+0x100` con `b8+0x4c8` bit 6 (pista 221);
     - `1/b8+0x724` con `b8+0x720`;
     - `b8+0x104` en los demás casos.
5. **Red `c0001.nmb`**:
   - El SM principal `0xd2` entra en `BT_Damage` (`0x1ae7`) con el mensaje 16 y la condición 0x271
     `DmgCategory ∈ [a, b]` (transiciones `0x2094..0x20a2` y `0x20cf`).
   - **Cada transición lleva un atributo 0x3e con el sub-estado de destino** dentro de `SM_Damage` (`0x18c2`). Por eso
     ningún nodo lee `DmgCategory`: lo leen las condiciones de transición. Esto resuelve el "sin confirmar" de
     stamina.md.
   - Condiciones y destinos leídos de los datos (`mainsm.py`, `tr.py`):

| `DmgCategory` (reacción) | Sub-estado | Clips | Velocidad por `DmgDuration` |
|---|---|---|---|
| 20 (1, poise aguanta, solo jugador) | no es `BT_Damage`: SM `0x5c7` / `0x187d` sobre la locomoción | `DmgKnockBackLight*` (nodo 0x97 con `DmgDuration`) | sí (por la forma, sin confirmar) |
| **30 (2)** | `0x1924` | KnockBack Light ↔ Heavy, ver §2 | **sí** |
| **40 (3)** | `0x1a92` | KnockBack Light ↔ Heavy, ver §2 | **sí** |
| 50 (4), 160 (11), 190 (14) | `0x1925` | `DmgExtraHeavy*Short/Long` (3.0 s) | sí |
| 60 (5/6) | `0x1967` | `DmgBlowAwayFaceDown/Up` (1.0 s) → `DmgDown*Start` | no |
| 80 (7) | `0x1978` | `DmgBreathStart/Loop` | no |
| 90 (8) | `0x197c` | `DmgTumbleFaceDown/Up` (0.667 s) | no |
| 95 (19) | `0x1ad5` | `DmgCollapseFaceDown` (2.5 s) | no |
| 100 (21), 320 (18) | `0x1a1b` | `DmgFall*` | no |
| 140 (9), 310 (17) | `0x1980` | `DmgParryBreak_Start` (1.667 s) | no |
| 150 (10, rotura de guardia) | `0x1984` | `DmgExtraHeavy*` | sí |
| 180 (13) | `0x1982` | `DmgStagger` (1.0 s) | no |
| 200–210 (15/16) | `0x1a05` | `FallLoop` + KnockBack | no |
| mensaje 15 | `0x1a93` | `SM_HitBack` (wall_bounce.md) | sí (`DmgHitBackDuration`) |

- **`DmgDuration` es una velocidad, no una duración**, a pesar del nombre.
  - El nodo que la lee es de tipo **0x7d** con un hijo, y en la cabecera tiene `+0x38 = 13`.
  - Es el mismo tipo y la misma tabla de colas que equipload.md verificó en código: `nodePlaySpeedModifierQueueUpdateTime`,
    tarea `TaskScaleUpdateTime`, Δt del hijo = max(0, CP)·Δt.
  - Las pistas de eventos y el TAE siguen el reloj de la animación (equipload.md), así que **todas las ventanas se
    dividen por la velocidad**.
  - Concuerda con wall_bounce.md (`DmgHitBackDuration` 0.6 = más lento) y con los valores de `PDP+0x2c` (0.6–1.45).

## 2. Las animaciones de la reacción normal (DmgCategory 30 y 40)

Árbol (`tree.py`):

```
0x1924 (0x81: Dmg_Position, Dmg_Rotation)            0x1a92 (igual)
└ 0x1923 (0x72, peso BlendWeight_1, rango 0..1)       └ 0x1a71 (0x72, BlendWeight_1)
  ├ 0x1902 (0x7d, DmgDuration = velocidad)              ├ 0x1a70 (0x7d) → mismo árbol Light↔Heavy
  │ └ 0x1901 switch DmgSlashOrThrust                    └ 0x1a91 (0x7d) → solo Heavy
  │   └ switch DmgDownOrUp
  │     └ Blend2 (0x6b) por DmgDistance, rango 0..5: Light (0) ↔ Heavy (5)
  │       └ BlendN (0x6c) por DmgDirection: −225 BR, −135 BL, −45 FL, 45 FR, 135 BR, 225 BL (grados)
  └ 0x1922 (0x7d) → solo Light (mismo switch/BlendN)
```

- La reacción 2 y la 3 usan **los mismos 32 clips**. Solo cambia la rama de mezcla: la 2 tira hacia Light, la 3 hacia
  Heavy.
- Sus tiempos son idénticos, así que la duración no depende de si el golpe fue ligero o fuerte. Depende de la velocidad
  `PDP+0x2c` de cada ataque.
- **Dirección**: no hay clips puros de frente, espalda o lados. Hay cuatro cuadrantes (FL, FR, BL, BR) y se mezclan
  según el ángulo.
  - 0° = 50 % FL + 50 % FR; ±180° = BL + BR.
  - Qué signo es la derecha: **sin confirmar**.
- Variantes: `SlashDown` / `SlashUp` / `ThrustDown` / `ThrustUp`. Las elige `DmgSlashOrThrust` y `DmgDownOrUp`.

| TAE ids | Nombre | Duración (`.nsa`) |
|---|---|---|
| 160010–160013 / 160020–23 / 160030–33 / 160040–43 | `a00_16_00x0..x3_DmgKnockBackLight{SlashDown,SlashUp,ThrustDown,ThrustUp}_{FL,FR,BL,BR}` | 1.3333 s |
| 160050–53 / 160060–63 / 160070–73 / 160080–83 | `a00_16_00x0..x3_DmgKnockBackHeavy…` | 1.3333 s |

Pistas (`c0001.nmb`), iguales en los 32 clips (segundos a velocidad 1, fracción entre paréntesis):

| Pista | Light | Heavy | Efecto |
|---|---|---|---|
| `CancelControl_Avoid` | 0–0.767 (0.575) | igual | no puede rodar ni dar un paso atrás |
| `CancelControl_AttackAll` | 0–0.9 (0.675) | igual | **no puede atacar** |
| `CancelControl_Guard` | 0–0.9 (0.675) | igual | no puede cubrirse |
| `CancelControl_Move` | **no tiene** | 0–0.9 (0.675) | no puede moverse |
| `StateControl_1` (81) | 0.833–1.333 (0.625) | igual | salida de `BT_Damage` si se mueve |
| `StateControl` (80) | 1.0–1.333 (0.75) | igual | salida de `BT_Damage` sin moverse |
| `InputControl` (37) | 0.5–1.333 | igual | sin identificar |
| `DamageDuration` (250) | 0–1.333 | igual | `b8+0x7e6 = 1` ("en reacción", `FUN_140369890`) |
| TAE 101100 | 0–0.267 | igual | sin regeneración de estamina |

- **Salida a la locomoción** (SM `0xd2`):
  - `0x20b5`, mezcla de 0.2 s: dentro del evento 80..81 y condición 0x273 invertida sobre `FB_Speed` (CP 1) = 0, es
    decir, **con el stick empujado → desde 0.625**;
  - `0x20b4`, mezcla de 0.5 s: dentro del evento 80 y `FB_Speed` = 0, es decir, **quieto → desde 0.75**.
  - Que 0x273 sea "CP en rango" y el byte 1 del encabezado lo invierta: **lectura del formato**, sin confirmar en código.
- Así que **se mueve otra vez** en 0.625 (Light) o en 0.675 (si pesa la pista `CancelControl_Move` de Heavy). **Ataca
  otra vez** en 0.675, y la animación termina en 1.0.
- Cómo combina el Blend2 las pistas que solo tiene una de las dos ramas (el `Move` de Heavy) cuando la mezcla es
  parcial: **sin confirmar**.

## 3. Reacción de un golpe normal del jugador con las armas del mod

- Todas las armas del mod tienen `DCP+0x10` = **2 en los ligeros y 3 en los fuertes**. La Gyrm Axe tiene 3 en todo, y
  las patadas 10 (rotura de guardia).
- El nivel `DCP+0x0c` es 3; vale 4 en la Gyrm Axe y 2 en los puños.
- Con la columna del jugador (2) la tabla no cambia nada (filas 3040 = `0 3 2 2 2 0 0 0`, 4040 = `0 3 3 3 2 0 0 0`,
  4050 = `0 4 3 3 3 0 0 0`, 3030 / 4030 col. 2 = 2 / 3). El ligero queda en 2 (DmgCategory 30) y el fuerte en 3 (40).
- Con un mob de columna 3 (el caso más común) sale lo mismo. Con la columna 4, el fuerte baja a 2 (sin efecto en el
  tiempo). Con la columna 1, el ligero sube a 3.
- **Tiempos con el primer golpe** (n = 0; para un enemigo DS2 no suma nada).
  - Velocidad = `PDP+0x2c` del ataque.
  - Total = 1.3333 / v; ataca = 0.9 / v; rueda = 0.767 / v; se mueve = 0.833 / v (stick) o 0.9 / v (pista Move de
    Heavy); sale quieto = 1.0 / v.
  - Tabla completa (generada con `weapons.py` + `params.py`):

| Arma | Ataque | PDP | DCP +0x10 / +0x0c | Reacción (col. 2) | Velocidad PDP +0x2c | Total | Ataca | Rueda | Sale andando | Move (Heavy) | Sale quieto |
|---|---|---|---|---|---|---|---|---|---|---|---|
| longsword | R1 | 10031800 | 2 / 3 | 2 | 1.25 | 1.067 | 0.720 | 0.613 | 0.667 | 0.720 | 0.800 |
| longsword | R1 combo | 10032000 | 2 / 3 | 2 | 1.45 | 0.920 | 0.621 | 0.529 | 0.575 | 0.621 | 0.690 |
| longsword | R2 | 10032400 | 3 / 3 | 3 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| longsword | R2 combo | 10032410 | 3 / 3 | 3 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| longsword | 2M R1 | 10033300 | 2 / 3 | 2 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| longsword | 2M R2 | 10033830 | 3 / 3 | 3 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| broken_straight_sword | R1 / R1 combo / R2 / R2 combo | como longsword | | | 1.25 / 1.45 / 1.15 / 1 | | | | | | |
| broken_straight_sword | 2M R2 | 10033800 | 3 / 3 | 3 | 0.8 | 1.667 | 1.125 | 0.958 | 1.042 | 1.125 | 1.250 |
| puzzling_stone_sword | R1 | 10031600 | 2 / 3 | 2 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| puzzling_stone_sword | R1 combo | 10031900 | 2 / 3 | 2 | 1.4 | 0.952 | 0.643 | 0.548 | 0.595 | 0.643 | 0.714 |
| puzzling_stone_sword | R2 | 20089600 | 3 / 3 | 3 | 0.8 | 1.667 | 1.125 | 0.958 | 1.042 | 1.125 | 1.250 |
| puzzling_stone_sword | R2 combo | 20089700 | 3 / 3 | 3 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| puzzling_stone_sword | 2M R1 / 2M R2 | 10033510 / 20089800 | 2 / 3, 3 / 3 | 2 / 3 | 1.15 / 0.8 | 1.159 / 1.667 | 0.783 / 1.125 | | | | |
| shortsword | R2 | 10032200 | 3 / 3 | 3 | 0.8 | 1.667 | 1.125 | 0.958 | 1.042 | 1.125 | 1.250 |
| shortsword | R2 combo | 10032420 | 3 / 3 | 3 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| shortsword / drakekeepers_sword | resto | como longsword (drakekeepers 2M R2 = 0.8) | | | | | | | | | |
| red_rust_sword | R1 / R1 combo | 10031610 / 10031910 | 2 / 3 | 2 | 1.25 | 1.067 | 0.720 | 0.613 | 0.667 | 0.720 | 0.800 |
| red_rust_sword | R2 | 10032430 | 3 / 3 | 3 | 0.725 | 1.839 | 1.241 | 1.058 | 1.149 | 1.241 | 1.379 |
| red_rust_sword | R2 combo | 10032440 | 3 / 3 | 3 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| red_rust_sword | 2M R1 / 2M R2 | 10033520 / 10033840 | 2 / 3, 3 / 3 | 2 / 3 | 1 / 0.75 | 1.333 / 1.778 | 0.900 / 1.200 | | | | |
| hand_axe, infantry_axe, bandit_axe, battle_axe | R1 | 10053300 | 2 / 3 | 2 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| (mismas hachas) | R1 combo | 10053500 | 2 / 3 | 2 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| (mismas hachas) | R2 | 10053800 | 3 / 3 | 3 | 0.7 | 1.905 | 1.286 | 1.095 | 1.190 | 1.286 | 1.429 |
| (mismas hachas) | R2 combo | 10054010 | 3 / 3 | 3 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| (mismas hachas) | 2M R1 / 2M R2 | 10054800 / 10055200 | 2 / 3, 3 / 3 | 2 / 3 | 1 / 0.6 | 1.333 / 2.222 | 0.900 / 1.500 | | | | |
| dragonslayers_crescent_axe | R1 / R2 | 10053400 / 10053700 | 2 / 3, 3 / 3 | 2 / 3 | 1.15 / 0.7 | 1.159 / 1.905 | 0.783 / 1.286 | | | | |
| gyrm_axe | R1 | 10056800 | 3 / 4 | 3 | 0.6 | 2.222 | 1.500 | 1.278 | 1.389 | 1.500 | 1.667 |
| gyrm_axe | R1 combo | 10057110 | 3 / 4 | 3 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| gyrm_axe | R2 | 10057300 | 3 / 4 | 3 | 0.7 | 1.905 | 1.286 | 1.095 | 1.190 | 1.286 | 1.429 |
| gyrm_axe | R2 combo | 10057310 | 3 / 4 | 3 | 0.8 | 1.667 | 1.125 | 0.958 | 1.042 | 1.125 | 1.250 |
| gyrm_axe | 2M R1 / 2M R2 | 10058410 / 10059310 | 3 / 4 | 3 | 0.6 | 2.222 | 1.500 | 1.278 | 1.389 | 1.500 | 1.667 |
| mace | R1 | 10046510 | 2 / 3 | 2 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| mace | R1 combo | 10046710 | 2 / 3 | 2 | 1.4 | 0.952 | 0.643 | 0.548 | 0.595 | 0.643 | 0.714 |
| club | R1 / R1 combo | 10046520 / 10046720 | 2 / 3 | 2 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| mace, club | R2 | 10046910 | 3 / 3 | 3 | 0.6 | 2.222 | 1.500 | 1.278 | 1.389 | 1.500 | 1.667 |
| mace, club | R2 combo | 10046920 | 3 / 3 | 3 | 0.95 | 1.403 | 0.947 | 0.807 | 0.877 | 0.947 | 1.053 |
| mace, club | 2M R1 / 2M R2 | 10048010 / 10048410 | 2 / 3, 3 / 3 | 2 / 3 | 1 / 0.7 | 1.333 / 1.905 | 0.900 / 1.286 | | | | |
| spear | R1 | 10017810 | 2 / 3 | 2 | 0.9 | 1.481 | 1.000 | 0.852 | 0.926 | 1.000 | 1.111 |
| spear | R1 combo | 10018010 | 2 / 3 | 2 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| spear | R2 | 10018220 | 3 / 3 | 3 | 0.7 | 1.905 | 1.286 | 1.095 | 1.190 | 1.286 | 1.429 |
| spear | R2 combo | 10018200 | 3 / 3 | 3 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| spear | 2M R1 / 2M R2 | 10019405 / 10019820 | 2 / 3, 3 / 3 | 2 / 3 | 0.75 / 0.8 | 1.778 / 1.667 | 1.200 / 1.125 | | | | |
| pickaxe | R1 | 20034100 | 2 / 3 | 2 | 0.8 | 1.667 | 1.125 | 0.958 | 1.042 | 1.125 | 1.250 |
| pickaxe | R1 combo | 20034200 | 2 / 3 | 2 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| pickaxe | R2 | 20034300 | 3 / 3 | 3 | 1.15 | 1.159 | 0.783 | 0.667 | 0.725 | 0.783 | 0.870 |
| pickaxe | R2 combo | 20034400 | 3 / 3 | 3 | 0.8 | 1.667 | 1.125 | 0.958 | 1.042 | 1.125 | 1.250 |
| pickaxe | 2M R1 / 2M R2 | 20035300 / 20035500 | 2 / 3, 3 / 3 | 2 / 3 | 0.7 / 0.6 | 1.905 / 2.222 | 1.286 / 1.500 | | | | |
| fists | R1 | 10004001 | 2 / 2 | 2 | 1 | 1.333 | 0.900 | 0.767 | 0.833 | 0.900 | 1.000 |
| fists | R1 combo | 10004004 | 2 / 2 | 2 | 1.25 | 1.067 | 0.720 | 0.613 | 0.667 | 0.720 | 0.800 |
| fists | R2 | 10004006 | 3 / 2 | 3 | 0.6 | 2.222 | 1.500 | 1.278 | 1.389 | 1.500 | 1.667 |
| fists | R2 combo | 10004007 | 3 / 2 | 3 | 0.7 | 1.905 | 1.286 | 1.095 | 1.190 | 1.286 | 1.429 |
| fists | 2M R1 / 2M R2 | 10004020 / 10004022 | 2 / 2, 3 / 2 | 2 / 3 | 1.15 / 0.8 | 1.159 / 1.667 | 0.783 / 1.125 | | | | |

- Otros ataques del mod, todos reacción 2 salvo el salto (3):
  - Longsword: carrera / paso atrás 1.15, rodando 1.25, salto 0.8 (`PDP 10032510 / 10032600 / 10032800`);
  - hachas: carrera 1.15, rodando 0.9.
- **Corrección (poise.md §5.1)**: sin guardia, `FUN_140136570` cambia la patada a reacción 2; la 10 es solo contra
  quien se cubre.
- **Patada** (`DCP+0x10` = 10): reacción 10 → DmgCategory 150 → `0x1984` (`DmgExtraHeavy*`, 3.0 s).
  - Velocidad `vt+0x140(CCP+0x174 = 0.85)`, o `CCP+0x184` = 1.5 si el clip activa la pista 221.
  - Es otra animación; no entra en "golpe normal".

## 4. Qué reacción usar para "mob golpeado por un ataque normal del jugador"

- **Reacción**: la de DS2 para la víctima jugador (columna 2): ligero → 2 (DmgCategory 30), fuerte → 3 (40). Las dos
  son la familia `DmgKnockBack` (§2), con los mismos tiempos.
  - Si se prefiere usar la columna del propio mob (`ECP+0x04`), los mobs de columna 0 o 5–7 no reaccionarían en DS2.
  - Coincide con la decisión de dejar fuera a los mobs grandes, pero qué mob de Minecraft tiene qué columna es una
    **decisión del usuario**.
- **No depende de ligero o fuerte**, sino de la velocidad `PDP+0x2c` de cada ataque (tabla de §3).
- **Ventanas** (velocidad v = `PDP+0x2c`; `n` = golpes ya recibidos en la misma reacción, 0 en el primero):
  - velocidad efectiva = `v + clamp(0.15·n − 0.2·t, 0, 1.5)` (solo en el jugador víctima; en un enemigo, `v`);
  - quieto (sin moverse ni atacar) hasta **0.9 / v** (fin de `CancelControl_AttackAll`; con Heavy también `Move`);
  - el movimiento puede volver antes, en 0.833 / v, si se toma la salida "con stick" (`StateControl_1`);
  - sin rodar hasta 0.767 / v;
  - animación completa hasta 1.3333 / v.
- Ejemplo, Longsword R1 (v = 1.25): ataca de nuevo a 0.72 s, se mueve a 0.667–0.72 s, fin a 1.067 s.
  Longsword R2 (v = 1.15): 0.783 s / 0.725–0.783 s / 1.159 s.

## 5. Exportar los clips (opcional): no hecho

- No se tocó `ds2_anims.py` ni `src/main/resources`.
- La reacción real es una mezcla en tiempo de ejecución (Light ↔ Heavy por `DmgDistance`, cuadrantes por
  `DmgDirection`, rama por `BlendWeight_1`).
- Exportar los 32 clips sueltos es posible, pero elegir uno o mezclarlos en Minecraft es una decisión de diseño que no
  sale de DS2. Además faltan datos (§6).

## 6. Sin confirmar / pendiente

- **Cómo combina el Blend2 las pistas de eventos** cuando una rama tiene `CancelControl_Move` (Heavy) y la otra no
  (Light). Por eso el final del bloqueo de movimiento queda entre 0.833 / v y 0.9 / v.
- **`BlendWeight_1`** (CP 0x32), el peso entre la rama mixta y la rama solo-Light o solo-Heavy: no se encontró quién lo
  escribe.
- **Mezcla por `DmgDistance`**: que el Blend2 (0x6b) mapee 0..5 linealmente a Light → Heavy sale del formato del
  atributo 0x9. `resultado+0x2c` (el factor de `DCP+0xbc`) no está identificado. El extra con la víctima ya en
  reacción tampoco (`FUN_14013b4d0`: 0 / 9999.9 / restante).
- **Condición de salida 0x273** (FB_Speed = 0 y su inversión): lectura del formato, no del código.
- **`P` = `PlayerCommonParam` fila 0** en `FUN_1403807c0`: el getter (`thunk_FUN_141cf7120`) está ofuscado. Los offsets
  +0x164..+0x178 y la comprobación de versión (> 0x16; la cabecera del param dice 0x1b) concuerdan, pero no se trazó.
  Las unidades de `chr.b0+0x28` (carga en %) tampoco están confirmadas.
- **`b8+0x10d`**: qué marca `resultado+0x31` y qué es `[r9+0x3e]` bit 1 en el reinicio.
- **Signo de `DmgDirection`** (izquierda o derecha) y cómo se calcula `b8+0x714`.
- **Prioridad entre reacciones** con la víctima ya en reacción (`FUN_1401397d0`, tabla de `FUN_1403b56a0` y
  `PDP+0x65` bit 0): no se leyó qué tabla es.
- Reacción 1 (poise aguanta, jugador): decodificada en poise.md §6 (el nodo 0x97 es ScaleToDuration).
- `InputControl` (37) y `DamageSpeedControl` (220): sin lector identificado.

## 7. Implementación en Minecraft (2026-11-01)

- Datos: `weapons.json` → `reactionSpeeds` por slot (PDP+0x2c por índice de PDP, alineado con `damageRates`) y
  `hitReaction` (bloqueos a velocidad 1, leídos de c0001.nmb `a00_16_0050`). Generado por `tools/re/weapons.py`.
- Cada ventana de golpe lleva la velocidad de su PDP (`Attack.reactionSpeed`; power stance: la fila de la mano que
  golpea). Un golpe de un jugador en modo Souls que entra (`hurt` = true) bloquea al mob `moveLock / v` y
  `attackLock / v` (0.9 / v los dos): `Mob.serverAiStep` se cancela (sin objetivos, navegación ni ataques) y su
  ataque en curso se corta (zombi c1020, illagers, invasor NPC).
- Bloqueo de movimiento: se usa 0.9 (rama Heavy); la mezcla real queda entre 0.833 / v y 0.9 / v (s.6, sin confirmar).
- Excluidos: `soulsmode:no_hit_reaction` (devastador, warden, wither, dragón: decisión del usuario; el golem de hierro
  salió el 2026-11-02 y reacciona como el Ogro, c3000_ogre.md §6),
  los enemigos de datapack (`SoulsEnemy`, ya tienen su reacción con poise, enemies.md s.7.1) y la víctima de un
  backstab / riposte (está agarrada).
- Los mobs vanilla no tienen poise: todo golpe los hace reaccionar (decisión del usuario: "la duración del jugador").
- Falta: la animación de reacción en el mob (el mob queda quieto con el parpadeo rojo vanilla). Hechizos y flechas
  la aplican desde 2026-11-01 con su propio PDP (§8).

## 8. Reacción de los proyectiles (flechas y hechizos) (2026-11-01)
Volcados: `scratchpad\bulletreact\` (`dump.py`, `arrow.py`, `dcp2.py`, `types.c`, `c28.txt`).
- **Un proyectil lleva su propia fila de PlayerDamageParam**: la pareja (bala, daño) de SpellParam `+0x0c` / `+0x10`
  o de ArrowParam. `FUN_140391bf0` → `FUN_140391fe0` resuelve el PDP (`FUN_1403b56d0`) y su DCP (`PDP+0x00`,
  `FUN_1403b5500`) y escribe el nivel `block+0x8c = DCP+0x0c` (`0x1400de604`). La bala guarda el PDP en `+0x30` y el
  bloque en `+0x80` (`FUN_140445020`); al golpear, `FUN_140446270` registra el golpe con `FUN_1403b09d0`, el mismo
  registro que el cuerpo a cuerpo. Desde `FUN_1403b1870` → víctima `vt+0x28` (`FUN_1401345c0`) la cadena es la de §1:
  reacción = `FUN_140138a40(DCP+0x10, nivel)`, velocidad `PDP+0x2c` para las reacciones 0..3.
- Los hijos (explosiones, chispas) copian el bloque del padre (`FUN_140445ec0`), así que usan **el nivel del padre**
  con su propio `DCP+0x10`.
- Valores (columna 2, como los mobs del mod):

| Proyectil (PDP) | Reacción | v = PDP+0x2c |
|---|---|---|
| Flecha de madera del arco corto (60760020) | 2 | 1.15 |
| Virote de ballesta a dos manos (60910020) | 2 | 1.15 |
| Virote a una mano (60910000 / 40 / 60; DCP 68012103 `+0x10` = 1) | **1** | 1.15 |
| Flecha del alma (31010000) | 2 | 1.15 |
| Lanza de rayo (32140000) / hijo 32140010 / chispas 32140030-31 | 3 / 2 / 2 | 1.15 / 1.15 / 1.4 |
| Bola de fuego (33010000) / explosión 33010001 (nivel 4 del padre) | 3 / 3 | 1.15 |
| Apoyo (curar, arma mágica, sudor, piel de hierro: DCP 99986021) | 23 (solo golpean al lanzador) | — |

- En el mod: `spell_export.py` exporta `reactionSpeed`, `reaction` y `reactionLevel` en cada bloque `damage` (lo usan
  también las flechas de `weapons.json`); un golpe de bala que entra bloquea al mob 0.9 / v **solo con reacción 2 o 3**
  (`Bullets.Damage.knockBackSpeed`).
- Sin establecer:
  - **reacción 1** (DmgCategory 20, el virote a una mano): SM `0x5c7` / `0x187d` sobre la locomoción, sin decodificar
    (§6); **el mod no aplica bloqueo**;
  - **reacción 14 forzada** (DmgCategory 190, `DmgExtraHeavy`): `FUN_140136ab0` pone `info+0x84` con atacante bala,
    `CP+0xec ≠ 0`, `DCP+0x95 == 0` (flechas y virotes; los hechizos tienen 1), `b8+0x4b8` bit 0 libre y
    `golpe+0xfc == 0`. Qué son `golpe+0xfc` / `+0x104` y el bit 29 de `b8+0x4b8`: sin identificar, así que no se sabe
    cuándo una flecha la provoca. El mod usa la reacción normal (2);
  - un segundo golpe durante la reacción (impacto directo + explosión de la bola de fuego, `FUN_1401397d0`).
- Poise: el mismo código que el cuerpo a cuerpo; el daño de poise de las balas cae con la distancia
  (`DCP+0xb8`, entre `+0xa4` y `+0xa8`). Los mobs vanilla no tienen poise.

## 9. Animación de la reacción en los mobs (2026-11-01)
Volcados: `scratchpad\react\` (`nx.py`, `smx.py`, `qtx.py`, `tab.md`, `bn*.c`, `d1..d6.c`, `t2.c`, `h1.c`).

### Quién escribe los CP (`ChrDamageMotionCtrl` vt+0x18 `FUN_14035e820`, con `b8+0x4c0` bit 49)
- `DmgCategory` = `b8+0x708`; `DmgDownOrUp` = `b8+0x70c`; `DmgSlashOrThrust` = 1 si `b8+0x710 == 1`;
  `DmgDirection` = `b8+0x714` × 57.29578; `DmgDistance` = `b8+0x718`; `Dmg_Rotation` aleatorio (CommonParam
  `+0x30c..+0x318`). Los llena `FUN_14013b4d0` desde el resultado de `FUN_14013b0a0`.
- **Dirección** (`FUN_140138150`, modo `PDP+0x41` = 0 en el mod; `FUN_14031f8d0`): ángulo horizontal con signo entre
  el frente de la víctima y el atacante, **positivo = atacante a su derecha**.
  - Víctima tipo jugador (`FUN_140203c10`: `DAT_1410bfff0[chr+0x54·5] == 2`) con `DCP+0x20 ≤ 0`: el ángulo entero.
  - Si no: 0 o π según `|ángulo| < CommonParam+0x1ac` (100°).
  - **El invasor NPC** (`chr+0x54 = 4`, `FUN_140355930` con `EP+0x04 = 100`) **es tipo jugador**: ángulo entero.
- **Corte / estocada**: `PDP s8 +0x32`, o `DCP+0x00` si vale 0xff (todas las del mod).
- **Arriba / abajo** (`FUN_140138cc0`, `PDP+0x43`): 0 abajo, 1 arriba, 2 arriba si el punto del golpe está a menos de
  CommonParam `+0x1a8` (0.8 m) sobre el origen de la víctima (`vt+0x148` = `chr+0x90`, los pies).
  - Quién da el punto en cuerpo a cuerpo (golpe `+0x110` con `+0x140`): **sin establecer**. Sin punto, da siempre
    arriba.
  - **Decisión del usuario: modo 2 = arriba.**
- **Distancia**: `PDP+0x28` × (1 + 0.01·`DCP+0xbc`·t), t = 0..1 entre `DCP+0xa4` y `+0xa8` según la distancia 3D
  atacante–víctima. El extra con la víctima ya en reacción (la distancia anterior no recorrida) tiene el acumulador sin
  trazar: no portado.
- **`BlendWeight_1`**: nadie lo escribe (el nombre no está en el exe); vale su defecto **1.0**.
- `0x1924` / `0x1a92` son HipsIK (`Dmg_Position`, `Dmg_Rotation`): su efecto no está decodificado, **no portado**.

### c0001 (el invasor)
- BlendN (`FUN_1406387e0`, sin vuelta): BR, BL, FL, FR, BR, BL en −225, −135, −45, 45, 135, 225; lineal entre
  vecinos. Blend2 (`FUN_14063d320`): `clamp((cp − w0)/(w1 − w0))`. Switch: el valor más cercano.
- FeatherBlend2 0x1923 / 0x1a71 (`BlendWeight_1` = 1): de Spine1 hacia arriba, el conjunto **puro** Light (30) o
  Heavy (40); pelvis y piernas, Light ↔ Heavy a `DmgDistance / 5`. Variante: (Slash 0 / Thrust 1) × (Down 0 / Up 1).

### c1020 (zombis e illagers)
- `BT_Damage` 0x779 → estado **0x61c** para DmgCategory 30 y 40. FeatherBlend2 0x632: Spine y arriba, Light; pelvis
  y piernas, Light ↔ Heavy a `DmgDistance / 4`. Switches `DmgDownOrUp` y `DmgDirection` (−180, 0, 180 → B / F / B).
  Sin estocada ni lados.
- Clips (1.0 s): 160015 Light Down F, 160016 Light B, 160025 Light Up F, 160055 Heavy Down F, 160056 Heavy B,
  160065 Heavy Up F. Hollow Soldier: columna 3 y poise 0, así que el ligero da 30 y el fuerte 40.

### En el mod
- `weapons.json`: `reactionMotions` por PDP (reacción, estocada, modo arriba/abajo, distancia, cerca, lejos, caída).
  `c1020_attacks.json`: `damage`. Clips nuevos: `enemy_animations/c1020_1600x5` y `player_animations/ds2_1600x0..x3`.
- `SoulsHitReaction` calcula los CP y manda `react:c1020:…` / `react:c0001:…`. `ZombieAnimClient` arma la mezcla por
  hueso a la velocidad del golpe: el cuerpo de Minecraft (Spine1) va con la parte de arriba; las piernas y la posición
  del cuerpo, con la de abajo.
- Los mobs sin clips de DS2 solo se quedan quietos (§7).
- Sin establecer o no portado: el punto del golpe (modo 2), HipsIK, el extra de distancia y cómo se juntan las pistas
  de duración en mezclas parciales (no afecta: el bloqueo es el de §7).

## 10. Punto del golpe, HipsIK y distancia extra (2026-11-02; `scratchpad\react\` k1/k2/hk1/hk2/mh/h1..h6.c)

### Punto del golpe (`DmgDownOrUp` modo 2)
- La víctima arma su golpe en `FUN_140314520` (chr vt+0x28) desde el registro `R` de `FUN_1403b1870`:
  `golpe+0x110 ← R+0x130` y `golpe+0x140 = 1` **solo si `R+0x168` bit 0**.
- `R+0x130..+0x19f` es una entrada de contacto copiada de un resultado de colisión de 0x80 bytes (punto `+0x10`,
  banderas `+0x48`). Los candidatos se ordenan por distancia al volumen del ataque (`PXBulletPhantom`, `FUN_1403af3f0`).
- **Sin establecer**: quién llena esos resultados (consulta del phantom de PX/Havok, sin decodificar). No se sabe si
  el cuerpo a cuerpo pone la bandera ni cómo es el punto. Sigue la decisión del usuario: modo 2 = arriba.

### HipsIK (`TaskHipsIKTransforms`, tarea 0x47, `FUN_14062ba30` → `FUN_14062bd20`)
- Entradas: 0 `Dmg_Position` (desplazamiento, 0), 2 `Dmg_Rotation` (**grados Euler**, `FUN_14062d040`:
  q = q_y · q_z · q_x), 3 peso del IK de piernas (c1020 `FootTurnWeight`, 1.0, nadie lo escribe), 4 peso del
  desplazamiento `w` (c0001 `BlendWeight_1` = 1; c1020 `MoveHit_Weight`).
- Hueso: Pelvis (4). Desplazamiento en espacio del modelo (X izquierda, Y atrás, Z arriba): rotación de la pelvis =
  pelvisQ · slerp(1, Q, w). Después, IK de dos huesos por pierna (Thigh, Calf, Foot, Toe0; `FUN_140c62640`) que
  devuelve los pies a su sitio. **Constante durante todo el estado** (sin fundido propio).
- `Dmg_Rotation` aleatorio por golpe (`FUN_14035e820`, xorshift128), con D, A, B, C = `+0x30c`, `+0x310`, `+0x314`,
  `+0x318` y p = la y anterior: x = −A + 2A·r; z = −B + 2B·r; y: si |p| ≤ C, t = (2r − 1)(D − C), y = t − C (t ≤ 0) o
  t + C; si p > C, (C + D)·r − D; si p < −C, (C + D)·r − C.
  - CCP 100 (jugador): 15, 10, 15, 8. ECP 102000 (Hollow Soldier) y 800000..2 (invasores NPC): 45, 10, 20, 15.
- **c1020**: `MoveHit_Weight` vale 0 salvo con `chr.e0+0xb8` tipo 4 (`ChrPassiveActionMotionCtrl`, rama de poise
  aguantado del jugador), así que **en el Hollow Soldier no hay desplazamiento**; solo el IK de pies, sin efecto.
- **En el mod**: solo el invasor. El servidor sortea la rotación y la manda en `react:c0001:…`; el cliente la aplica a
  la pelvis (cuerpo) y gira las piernas al revés sobre la cadera. Esto último aproxima el IK porque las piernas de
  Minecraft son rígidas (no doblan la rodilla): límite del motor.
- Sin establecer: el interior del IK (`FUN_140c62640` / `FUN_140c62bc0`) y los campos `+0x10..+0x1c`, `+0x24` del
  atributo 0x21.

### Distancia extra al recibir otro golpe en la reacción
- `+0x540` = la distancia base anterior (sin su extra; 0x14013ba00). `+0x580` = suma del desplazamiento de raíz
  (trayectoria de Morpheme) de la reacción, cada frame mientras `b8+0x7e6` (`FUN_140135dd0`); se pone a 0 en cada
  reacción nueva.
- Extra = clamp(`+0x540` − |`+0x580`|xz, 0, 9999.9), solo con la víctima ya en reacción, viva y `b8+0x720 == 0`.
- **DmgDistance no empuja**: solo elige la mezcla Light ↔ Heavy. El desplazamiento es el de raíz del clip.
- **Prioridad** (`FUN_1401397d0`, `DamageOverWriteParam` fila 0): reacciones 2 y 3 = 30. El golpe nuevo se descarta
  solo si `PDP+0x65` bit 0 es 0 y la prioridad anterior es mayor. En el mod, ligero y fuerte nunca se bloquean.
- "En reacción" (`DamageDuration`): c0001 0..1.3333 s; c1020 0..0.6667 s (la FeatherBlend2 sync 0x632 junta Light
  0..0.6667 y Heavy 0..0.5; en unión o a peso 1 termina en 0.6667). Dividido por la velocidad.
- **En el mod**: la distancia extra resta lo recorrido por la raíz de la reacción anterior (§11). Cada golpe vuelve a
  empezar la animación.
- Sin establecer: si DS2 reinicia el clip al volver a entrar al mismo estado (la transición "desde cualquier estado"
  se dispara, pero no se trazó si reconstruye el subárbol).

## 11. Desplazamiento de raíz de la reacción (2026-11-02)
- Trayectoria del `.nsa` (equipload.md §6, `tools/re/nsa.py trajectory`): tasa, muestras, offset, escala y claves
  de 11 / 11 / 10 bits. Espacio del modelo: X izquierda, Y atrás, metros.
- **c0001** (20.25 muestras/s, 28): las 16 Light **no se mueven** (clave constante 0). Las 16 Heavy recorren
  3.5535 m en X y 3.5535 m en Y (5.03 m en diagonal), alejándose del atacante según el cuadrante: FL (−X, +Y, atrás
  a la derecha), FR (+X, +Y), BL (−X, −Y), BR (+X, −Y). Los cuadrantes concuerdan con "positivo = atacante a la
  derecha" (§9).
- **c1020** (30 muestras/s, 31): las Light no se mueven; Heavy F (160055 / 160065) 4.24 m hacia atrás, Heavy B
  (160056) 4.24 m hacia delante.
- Así, el desplazamiento crece con `DmgDistance`: la Blend2 Light ↔ Heavy (0..5 en c0001, 0..4 en c1020) lleva la
  raíz de 0 a ~5 m / 4.24 m.
- La raíz sale de la parte de abajo del FeatherBlend2 (alfa 0 en Root/Pelvis): la mezcla Light ↔ Heavy de los
  cuadrantes o de F / B. La mezcla de la trayectoria está en §12 (entre cuadrantes no es lineal).
- Sin giro: la trayectoria de estos clips no rota.
- **En el mod** (`ds2/ReactionBlend`, `SoulsHitReaction.tick`): cada tick el servidor mueve al mob (`move`, con
  colisiones) lo que avanza la mezcla de trayectorias a la velocidad del golpe, en la orientación que tenía al
  recibirlo. Lo recorrido es el `+0x580` de la distancia extra (§10). Datos: `weapons.json hitReaction.trajectories`
  y `c1020_attacks.json damage[].trajectory`.
- El empuje vanilla de Minecraft al recibir daño sigue sumándose (está en el backlog: "empuje del golpe sin el salto").

## 12. Trayectoria y su mezcla, confirmadas en código (2026-11-02; `scratchpad\traj\`)

### Decodificador (`TaskUpdateDeltaTrajectoryFromTrajectorySourceNSA`, tarea 8)
- `AnimSourceNSA` locate `FUN_140609b30`: el `.nsa +0xb8` apunta al bloque; `TrajectorySourceNSA::locate`
  `FUN_14065ed10`: claves de posición en `+0x58` (u32) y de rotación en `+0x60` (3 × u16); cantidad **u32** en `+0x24`.
- Posición (`FUN_14065edc0`): X = `k >> 21`, Y = `(k >> 10) & 0x7ff`, Z = `k & 0x3ff`, valor = q·escala + offset
  (`+0x34` / `+0x28`), sin sesgo; lineal entre muestras. **El formato del mod era correcto.**
- Rotación (`FUN_14065ef00`): constante en `+0x40..+0x4c` (x, y, z, w) si `+0x60 == 0`; si no, offset `+0x40`, escala
  `+0x4c`, claves 3 × u16 → v, q = (2v, 1 − |v|²) / (1 + |v|²), fastSlerp. 18 clips de c0001 la usan (giros de
  locomoción, etc.); **los 32 DmgKnockBack y los 6 de c1020 tienen rotación identidad** (comprobado).
- Muestreo (`FUN_14065ebc0`): f = t·tasa, i = trunc(f), a = f − i; en la última muestra i = n − 2, a = 1.
- **Salida = delta del frame** (`FUN_140616980`): Δp = rot(conj(q_prev), p(t) − p(t_prev)), Δq = conj(q_prev)·q(t)
  (más el caso de vuelta del bucle con las transformadas de inicio y fin del SourceAnim). Sin rotación, Δp = p(t) −
  p(t_prev).

### Mezcla
- Blend2 / BlendN (tabla 3, la BlendN 0xf6 de la reacción): tarea 0x66 `…SlerpTraj` (`FUN_14061af00`): rotación con
  slerp; **posición con `FUN_1405ea3f0`**: lineal si algún vector mide < 1e-6 o son paralelos; si no, la dirección del
  primero gira hacia el segundo acos(dot)·w y el largo se interpola. Con un solo hijo activo (peso < 1e-4 o > 0.9999)
  se copia ese hijo. Otras tablas: 0x6b `…InterpTraj` (lineal).
- **Corrección**: entre dos cuadrantes Heavy (direcciones a 90°) no es una suma lineal: de frente (FL + FR a medias)
  el mob retrocede los 5.03 m enteros hacia atrás, no 3.55 m. Light ↔ Heavy sí queda lineal (los Light valen 0).
- FeatherBlend2 (0x1923, 0x1a71, c1020 0x632; tarea 0x82): trayectoria con peso w·alfa[idx]; en todos ellos idx 2 con
  alfa 0, así que **la trayectoria es exactamente la del hijo 0** (la mezcla Light ↔ Heavy).
- Transiciones (tabla 7): mezcla lineal de los deltas con el peso de la transición; tabla 24: solo el destino.

### Aplicación al personaje (`FUN_1404db030` / `FUN_1404daf70` / `FUN_1404d9eb0`)
- Δp del modelo (X, Y, Z) → juego (−X, Z, −Y), es decir (derecha, arriba, delante) locales; Δq igual con w negado.
- pos += rot(orientación actual, Δp); orientación = normalizar(orientación ⊗ Δq). **Sin escala**: la velocidad solo
  entra por el tiempo.
- Sin trazar: el paso final al proxy de Havok; quién pone `net+0xf4` (rama de la semántica 7); la mezcla aditiva y la
  de los dead blends; quién llena las transformadas de inicio / fin del SourceAnim.

### En el mod
- `nsa.trajectory` (u32 en `+0x24`); `ReactionBlend.delta`: delta de cada clip entre dos tiempos, BlendN y Blend2 con
  `blendPosition` (`FUN_1405ea3f0`), aplicado cada tick en la orientación del mob (sin giro: los clips no rotan).
