# El Ogro (c3000) en el golem de hierro

Pedido del usuario (2026-11-02): el golem de hierro pasa a ser el Ogro de DS2. Se portan **los ataques y su IA**, **la
reacción al daño** y **la locomoción** (quieto / caminar / trotar). **No** se portan el agarre (acción 13) ni los stats
de DS2: el golem conserva su daño vanilla (solo animaciones, tiempos, ventanas, alcances, ángulos e IA salen de DS2).

Script: `tools/re/ogre_anims.py` (usa el retarget de `enemy_anims.py`, c1020_anims.md). Salidas:
- `src/main/resources/assets/soulsmode/enemy_animations/c3000_<idTAE>.json` (17 clips, formato Bedrock / PAL);
- `src/main/resources/data/soulsmode/ds2/c3000_attacks.json` (acciones, pasos, golpes, trayectorias, locomoción, daño).

Uso: `python tools/re/ogre_anims.py src/main/resources/assets/soulsmode/enemy_animations src/main/resources/data/soulsmode/ds2/c3000_attacks.json`

## 1. Datos del juego
En `D:\Reversing\DS2\extracted\c3000\` (extraídos de `GameDataEbl.bdt` como los de c1020):

| Ruta del juego | Desempaquetado |
|---|---|
| `gamedata:/morpheme4/chr/c3000.anibnd.dcx` | `anibnd\` (115 `.nsa` + `c3000.nmb`) |
| `gamedata:/model/chr/c3000.bnd` | `model\` (`c3000.flv`) |
| `gamedata:/timeact/chr/c300000_pl.tae` | se lee directo con `D:\Reversing\DS2\tools\tae.py` |
| `gamedata:/ezstate/ai300000.esd` (inferior), `ai300010/30/40.esd` (superiores) | volcados a texto |

Params (regulation):
- `EnemyParam 300000` (también 300001 / 300002 / 6220 / 6260 / 6300: mismo modelo, ECP, EMP y EEWP): modelo 3000,
  `EnemyCommonParam` / `EnemyMoveParam` 300000, `EnemyEquipWeaponParam 30000000` → `+0x00` = **300000**, la fila de
  `EnemyBehaviorSecondParam` / `EnemyComboBehaviorParam` de abajo.
- `EnemyBehaviorSecondParam 300000` (entradas de 0x58; acción = 10 + índice): encendidas 11, 12, 13, **14**, **15**.
  - 14: EDP `130000610`, `EnemyAttackMoveParam` (`+0x18`) 7.
  - 15: EDP `130000710`, AMP 7.
  - 11 / 12 / 13 no los pide la tabla de ataques de abajo (13 es el agarre: EDP 130000310, segunda tabla 130000320).
- `EnemyComboBehaviorParam 300000` (entradas de 0xd8; acción = 50 + índice), golpes `+0x08 + 4k`, AMP `+0x3c + 4·paso`:

| Combo | `+0x00` | Golpes k = 0..3 | AMP |
|---|---|---|---|
| 50 | 10 | 130000010, 130000020, 130000030, −1 | 7, 7, 7 |
| 51 | 10 | 130000010, 130000020, 130000040, **130000050** | 7, 7, 7, 7 |
| 52 | 11 | 130000110, 130000120, −1, −1 | 7, 7 |

- `EnemyDamageParam` `+0x1c` (tasa de daño): 1.0 en todas las filas de golpe salvo **130000050 y 130000120, con 0**
  (también tipos y valores a 0): no hacen daño.
- `EnemyMoveParam 300000`: `+0x20` 3.2, `+0x2c` 4.5379 (260°/s), `+0x30` 4.8869 (280°/s) — el giro fuera de un
  ataque (enemy_ai.md §8.5).
- `EnemyAttackMoveParam 7`: `+0x08` / `+0x0c` 1.658 rad/s (95°/s), `+0x10` … `+0x14` 1.1 … 5.0, `+0x24` 1.2.
- `EnemyCommonParam 300000`: `+0x04` = **4** (columna de reacción), `+0x1ac` = 100° (frente / espalda de la reacción),
  `+0x30c..+0x318` = 45, 10, 20, 15 (Dmg_Rotation).

## 2. Esqueleto y retarget
- `c3000.nmb`: 79 huesos (humanoide + cola `Tail01..08`, cara y dedos; estos no se usan). Con 79 huesos la máscara de
  bits antes de los arreglos de la pose ocupa 3 palabras, así que los `+0x80` / `+0x88` fijos de `enemy_anims.EnemyRig`
  no valen: se siguen los punteros del transform buffer (`pose +0x40` → subestructura; `+0x18` / `+0x20` = arreglos de
  posiciones / cuaterniones, relativos a `pose +0x20`). El mapa canal → hueso del objeto tipo 2 está en su puntero `+0x20`.
- Altura de cadera: `Root` z = **1.7473 m**. Hombros (`L/R_UpperArm`) a ±0.92 m del centro.
- Golem (`IronGolemModel.createBodyLayer`, javap de 1.21.1): piernas con pivote y = 11 y cajas de 16 px (pies en
  y = 24), así que la cadera está a **13 px**; escala de la posición de la pelvis 13 / 1.7473 = 7.44 px/m. Brazos:
  pivote (0, −7, 0) y cajas en x −13..−9 (derecho) / 9..13 (izquierdo), 30 px de largo.
- Retarget geométrico igual al de c1020 (c1020_anims.md §3): `body` = rotación de `Spine1`; `head` relativa al pecho;
  miembros del hombro a la muñeca (cadera al tobillo) con la bisagra del codo / rodilla para el giro.
- **Pivote de los brazos**: el brazo del golem gira sobre el centro del torso (pivote x = 0) y su caja está a 11 px; con
  la abducción del hombro de DS2 la caja orbitaría el torso. Mientras suena un clip del Ogro, el cliente gira cada
  brazo sobre su hombro **S = (∓11, −7, 0)** (x = centro de la caja; y, z = pivote vanilla): como la caja queda fija en
  la parte, el pivote pasa a `P + d − R·d` (`d` = (∓11, 0, 0), `R = Rz·Ry·Rx` de `ModelPart.translateAndRotate`). El
  retarget no cambia: el eje +Y del brazo sale del hombro, igual que en `HumanoidModel`.

## 3. Animaciones exportadas
Duración: `.nsa +0x28`. Golpes: evento TAE 2200, `parámetro[0]` = ranura k → fila EDP de la entrada. Trayectoria:
`nsa.trajectory` (hit_reaction.md §12); todos los clips de abajo tienen la rotación de la trayectoria constante
(`+0x60` = 0, comprobado al exportar).

| Acción | Paso | TAE | Duración (s) | Golpes 2200 (s) → k → EDP (tasa) | Fin de `HitDuration` (s) | Raíz (izq., atrás) m |
|---|---|---|---|---|---|---|
| 14 | — | 65141010 | 4.0 | 1.1–1.4 → 0 → 130000610 (1) | 1.333 | 0, 0 |
| 15 | — | 65151010 | 4.0 | 1.1–1.3 → 0 → 130000710 (1) | 1.333 | 0, 0 |
| 50 / 51 | 0 | 65101010 | 3.167 | 0.867–1.033 → 0 → 130000010 (1) | **1.0** | 0, −2.8 |
| 50 / 51 | 1 | 65101020 | 2.833 | 0.5–0.7 → 1 → 130000020 (1) | **0.667** | 0, −1.823 |
| 50 | 2 | 65101030 | 3.5 | 1.233–1.367 → 2 → 130000030 (1) | 1.333 | 0, −1.516 |
| 51 | 2 | 65101130 | 4.667 | 1.567–2.033 → 2 → 130000040 (1); 2.0–2.1 y 3.0–3.1 → 3 → 130000050 (**0**) | 2.0 | −1.629, −0.156 |
| 52 | 0 | 65111010 | 4.5 | 1.2–1.533 → 0 → 130000110 (1); 1.5–1.6 → 1 → 130000120 (**0**) | 1.5 | 0, +1.0 |
| 52 | 1 | 65111020 | 4.667 | 1.633–1.833 → 1 → 130000120 (0) | 2.0 | 0, −3.833 |

- 65111010 tiene dos `.nsa`: `a65_11_1010_Attack_11_1_1_unti…` y `…_1_1_L_u…`, con las mismas pistas y la misma raíz.
  Cuál reproduce la red no está trazado: se usa la que no es `_L_`.
- Pistas de `c3000.nmb` por clip (nodos de animación con su evento `TimeAct`), p. ej. 65101010: `AttackStateControl`
  106 / 100 / 101 / 102 en 0–0.5 / 0.5–1.0 / 1.0–1.833 / 1.833–3.167; `HitDuration` (123) 0.5–1.0;
  `AttackCancelControl_Attack` (112) 0–2.333; `StateControl` (80) 2.433–3.167. Ninguna pista de combo (no hay
  `AttackComboControl` como en c0001).

Locomoción (`Wep01` = modo 0 de EnemyParam 300000, `M` = 1, como en c1020_anims.md §5; sin eventos de golpe):

| TAE | `.nsa` | Duración (s) | Raíz por ciclo | Velocidad |
|---|---|---|---|---|
| 7001 | `a00_00_7001_Wep01_Idle` | 2.0 | 0 | 0 |
| 37001 | `a00_03_7001_Wep01_Walk` | 1.333 | 2.0 m adelante | **1.5 m/s** |
| 47001 | `a00_04_7001_Wep01_Jog` | 1.0 | 3.9 m adelante | **3.9 m/s** |

Daño (§6): 160015 / 160016 / 160025 (Light, 1.167 s, sin raíz) y 160055 / 160056 / 160065 (Heavy, 1.167 s, 5.0 m
hacia atrás en F, hacia adelante en B).

## 4. IA: `ai300000` (inferior)
Los mapas 10 y 20 del inferior (grupos 10 y 20, pedidos por el superior con `5301`) llaman a los bucles de pelea
`2147483610` (mapa 10) y `2147483606` (mapa 20). Son iguales salvo la tabla: `2147483609` (mapa 10) tiene el agarre;
`2147483605` (mapa 20) no. Qué mapa pide el superior no se trazó; se porta el mapa 10, donde `f5901() == 10`.

**Bucle de pelea `2147483610`**
1. Estado 1: `r3 = RandInt(0, 10)` (`cmd 1(3, 0, 10)`); `f5070()` → estado 6 (no portado: sin identificar).
2. Estado 3: `2147483620(0.8, 1, 4, 50, 1.4, 1)` (acercarse). Mientras corre, pasa a la tabla si `f5050(2/3/17/13/7/14)
   == 0`, `f7080() == 0`, `f5061() == 0`, `−1 ≤ f7007() ≤ 2` (altura del objetivo − la propia), `0 < r3 < 5` y
   `f7001() < 8`; la otra entrada pide `f5080()`. Al volver, también a la tabla.
   - `2147483620` estado 0: `f5061()` → 4; `f7300()` → 2; `f3() ≥ 5` → fin. **Con `f7300` = 0 se queda quieto en el
     estado 0 hasta 5 s y vuelve.**
3. Estado 2: tabla `2147483609`; vuelve 1 (se rindió) → estado 1; si no → estado 4.
4. Estado 4: `2147483636(2, 4, 5, 0, 0, 4, 0.8)` (después del ataque): `f1(0) = RandInt(2, 4)`; si `f7001() ≥ 5` sale;
   si no, `5565(…)` hasta `f3() ≥ f1(0)·f5101(0)`, pero la última salida del estado 2 (`f5004() != 1` y los `f5050`,
   `f7080`, `f5061` a 0) **se cumple enseguida con las funciones sin identificar a 0**: sale al momento.

**Tabla `2147483609`** — `r0 = RandInt(0, 20)`, en este orden (la primera que se cumple):

| # | Condición | Acción |
|---|---|---|
| 1 | `f7002() ≥ 110`, `0 < f7001() < 3`, `f5018()` | `2147483616(61)` — **no portada** (`f5018` sin identificar; 61 se remapea por el arma, enemy_ai.md §5.3) |
| 2 | `f7001() < 4.5`, `f7002() ≥ 110` | combo 52 por `2147483633(52, 0, …)` |
| 3 | `f7001() < 2.5`, `0 < r0 < 5` | 14 por `2147483639(14, 2, 30)` |
| 4 | `f7001() < 2.5`, `0 < r0 < 10` | 15 por `2147483639(15, 2, 30)` |
| 5 | `0 < r0 < 5`, `f2(2) ≥ 20` | agarre `2147483639(13, 5, 30)` — **no portado** (pedido del usuario) |
| 6 | `0 < r0 < 15`, `f5901() == 10` | combo 51 por `2147483621(51, 3, 5, 6, 30, 90, 5, 0)` |
| 7 | resto | combo 50 por `2147483621(50, 3, 5, 6, 30, 90, 5, 0)` |

Sin las filas 1 y 5, sus casos caen a las siguientes (con la 5: a `d ≥ 2.5` y `0 < r0 < 5`, combo 51 en vez del agarre).

**Ataque simple `2147483639(acción, alcance, ángulo)`** (igual a `S2147483595` de c1020, enemy_ai.md §9.2)
1. Al entrar: `cmd 2(0)` (temporizador 0). Si `0 ≤ f7001() ≤ alcance` (`f7018(0, alcance)`), `f7002() < ángulo` y
   `f5000()` → `5204(acción, 0)`.
2. Dentro del alcance y fuera del ángulo → estado 2: gira (`5903`) y lanza cuando `f7002() < ángulo`, o cuando
   `f5622() != 1`, o a los 1.5 s.
3. `f7002() ≥ 90` → estado 3: gira hasta `f7002() < 45`, `f5622() != 1` o 1.5 s; luego camina o corre.
4. A menos de `alcance + 1.5` camina (`5505`), si no corre (`5535`); dentro del alcance lanza (o gira, estado 2); se
   rinde a `f2(0) ≥ 3` s caminando o `≥ 10` s corriendo (vuelve 1).

**Combo `2147483621(combo, 3, 5, 6, 30, 90, 5, 0)`** → `2147483632(5, 6, 5, 30, 3, 3, 90, 1)`: el mismo acercamiento con
alcance **5 m** y ángulo **30°** (`f7018(0, env(0))`, `f7002() ≥ env(3)`), sin `5204`; luego
`2147483628(combo, 6, 90, 0, 0, 0, 0, 0.5)`:
- `5204(combo, 0)`; a los 0.5 s (estado 2, `f3() ≥ 0.5`) pasa a mirar el paso (`f5008()`).
- En cada paso: corta el combo (`5208`) si `f7001() ≥ 6` o `f7002() ≥ 90` (o con `f5050(14/13/7)`, `f5643() != 1`;
  sin identificar). `RandInt(1, 100) < env(4..6)` = 0: nunca corta por azar.
- Al cortar, el paso actual termina y no sale el siguiente.

**Combo 52 `2147483633(52, 0, 0, 0, 0, 0, 0, 0)`**: `5204(52, 0)` donde está, sin acercarse; en el estado 2,
`f7001() ≥ env(2)` = 0 se cumple siempre → `5208`: **solo sale el primer paso** (65111010).

**Funciones sin identificar**: valen 0, como en `SoulsZombies` (`f5004`, `f5018`, `f5102`). Aquí: `f5018`, `f5050(n)`,
`f5061`, `f5070`, `f5080`, `f5092`, `f5103`, `f5212`, `f5213`, `f5620` (navmesh), `f5643`, `f7080`, `f7300`, `f5023`,
`f5022`. `f5101(0)` = 1.0 (npc_invaders.md §2.5). `f5622` se lee como "sigue girando" (`5903`): queda cubierto por
`f7002() < ángulo`.

## 5. Encadenado de los pasos del combo
- El exe no se trazó (`objeto+0x10`, `FUN_1403a7c80`, enemy_ai.md §8.3), y c3000 no tiene `AttackComboControl`.
- **Continuidad de la pose** (retarget del mod, suma de las diferencias de Euler de los 6 huesos, muestreo a 30 Hz):
  la primera pose de cada paso coincide con la pose del anterior **al final de su `HitDuration`** (= paso de
  `AttackStateControl` 100 → 101):

| Paso anterior → siguiente | Mejor coincidencia | Diferencia (°) | En el fin de `AttackCancelControl_Attack` | En el final del clip |
|---|---|---|---|---|
| 65101010 → 65101020 | 1.0 s (fin de `HitDuration` 0.5–1.0) | **4.8** | 313.9 | 374.5 |
| 65101020 → 65101030 | 0.667 s (fin de `HitDuration` 0.333–0.667) | 31.5 | 235.3 | 337.2 |
| 65101020 → 65101130 | 0.667 s | 31.5 | 235.3 | 337.2 |

- Así que el paso siguiente arranca en el fin de `HitDuration` del actual. Es una lectura de datos (las poses), **no
  del código de Morpheme**.

## 6. Reacción al daño
- `c3000.nmb` estado **0x284**: copia nodo por nodo del 0x61c de c1020 (hit_reaction.md §9): FeatherBlend2 (Spine y
  arriba, Light; pelvis y piernas, Light ↔ Heavy en Blend2 `DmgDistance` (0, 4)), switches `DmgDownOrUp` y
  `DmgDirection` (−180, 0, 180 → B / F / B). DmgCategory 30 y 40 llegan al mismo estado.
- Clips: los seis de §3, **1.1667 s**. Pistas (iguales en los seis): `DamageDuration` (250) **0–0.5**,
  `StateControl` (80) 0.9667–1.1667, `DieControl` 0.5–1.1667; 160025 / 160065 además `DamageControl` 200 / 201.
- Raíz: Light 0; Heavy F (160055 / 160065) 5.0 m atrás, Heavy B (160056) 5.0 m adelante.
- `Dmg_Rotation` 45 / 10 / 20 / 15, pero `MoveHit_Weight` = 0 en enemigos (hit_reaction.md §10): sin HipsIK.
- **Columna 4** (`ECP +0x04`): `DamageMotionWeightParam` con las filas de las armas del mod da 3040 → 2, 4040 → 2,
  4050 → 3 (Gyrm Axe), 4030 → 2 y **3030 → 1** (ligero de los puños, nivel 2). 2 y 3 van al mismo estado; la 1
  (DmgCategory 20) no se trazó en c3000.
- **En el mod**: la reacción se elige con la columna 2 del golpe (`reactionMotions`, la de los zombis), porque el golpe
  no lleva `DCP+0x10` ni el nivel para remapear con la columna 4. Solo cambia el ligero de los puños (en DS2, reacción 1).

## 7. En el mod
- **Datos**: `ogre_anims.py` → `c3000_attacks.json` (`actions`: acción, combo, alcance / ángulo, límites de
  continuación, pasos con golpes, tasa EDP, fin de `HitDuration` y trayectoria; `locomotion` con su velocidad de raíz;
  `damage` con `damageDuration` y trayectoria). Lo lee `ds2/C3000Attacks` (también la tabla `2147483609`).
- **Servidor** (`SoulsGolems`): un objetivo de IA de prioridad 0 (gana a la `MeleeAttackGoal` vanilla, mismas banderas)
  mientras el objetivo del golem es un jugador en modo Souls:
  - bucle de §4: `r3`, atajo a la tabla (`d < 8`, `0 < r3 < 5`, altura en [−1, 2]) o 5 s quieto; tabla; acercamiento
    (girar / caminar a 1.5 m/s / correr a 3.9 m/s, rendirse a 3 / 10 s); ataque; vuelta al bucle;
  - giro fuera del ataque a `lerp(260°, 280°/s, v / 3.2)` (EMP 300000), también al caminar (`OgreMoveControl` limita
    el giro de la `MoveControl` vanilla); **sin giro durante el ataque**;
  - golpe en cada ventana 2200 con tasa > 0: el `doHurtTarget` vanilla del golem si el objetivo está a menos del
    alcance (distancia − medio ancho del objetivo, como `SoulsZombies`) y dentro del ángulo; combo 52 con la condición
    de su fila (`< 4.5 m`, `≥ 110°`). Contra un jugador Souls, el golpe vanilla fuera de una ventana se cancela;
  - movimiento raíz de los ataques (trayectoria, en la orientación del lanzamiento);
  - combos: siguiente paso en el fin de `HitDuration`, corte por 6 m / 90° (§4, §5);
  - clips: `c3000_<TAE>` de ataque, `loop:c3000_7001 / 37001 / 47001` (quieto / caminar / trotar). El quieto no corta
    un clip de reacción que todavía suena.
- **Reacción** (`SoulsHitReaction`, `ReactionBlend.c3000Lower`): el golem salió de `no_hit_reaction`; `react:c3000:…`
  igual que c1020 sobre los clips del Ogro, `DamageDuration` 0.5 / v, raíz de sus trayectorias. El bloqueo es el de
  todos los mobs (0.9 / v, hit_reaction.md §7).
- **Cliente** (`ZombieAnimClient`, `IronGolemModelMixin`): los clips c3000 giran el modelo sobre la cadera del golem
  (punto (0, 11, 0) px) y los brazos sobre el hombro (§2); con cualquier otro clip o sin clip, pivotes vanilla.
- Unidades: metros de DS2 = bloques (la convención del mod, como c1020 y el invasor), aunque el golem mide 0.47 del
  Ogro (13 px = 0.81 bloques de cadera contra 1.747 m).

## Sin establecer / no portado
- **Agarre** (acción 13, fila 5 de la tabla): no portado por pedido del usuario. **`2147483616(61)`** (fila 1): no
  portado (`f5018` sin identificar y la remapeo de 61 sin confirmar).
- **Qué mapa** (10 o 20) pide el superior `ai300010/30/40`: no trazado; se usa el 10.
- **Funciones sin identificar** (§4): valen 0. Con eso, `2147483620` deja al golem quieto hasta 5 s y el
  "después del ataque" `2147483636` termina enseguida; **`5565`** (moverse alrededor) **no portado**.
- **Encadenado de combos** (§5): por la pose, no por código.
- **Giro durante el ataque**: EnemyAttackMoveParam 7 solo gira con `chr.b8+0x4ec` / `+0x4f0`, que no se sabe quién
  pone (enemy_ai.md §8.5); `TargetAngleControl` (126) de 65111010: sin identificar.
- **Volumen del golpe**: el mod usa alcance y ángulo de la subrutina (como `SoulsZombies`), no las cápsulas / esferas
  de las filas EDP (dummies 10 = `R_Forearm`, 20 = `L_Forearm`, 40 = `Pelvis` de `c3000.flv`; el eje de la cápsula
  sin confirmar, enemy_ai.md §8.4).
- **Enfriamiento por ranura** (`FUN_14039ec80`, enemy_ai.md §8.1): sin desglosar, no portado.
- **65111010 normal o `_L_`**: cuál reproduce la red, no trazado.
- **Reacción con la columna 4** (§6): el ligero de los puños sería reacción 1 (DmgCategory 20, estado sin trazar en
  c3000); el mod usa la columna 2.
- **Locomoción fuera de la IA del Ogro** (el golem paseando o peleando con mobs): sigue vanilla; con la IA vanilla no
  hay orden de DS2 que diga caminar o trotar.
- **Escala**: distancias, velocidades y raíz en metros de DS2 = bloques; el golem es más chico que el Ogro, así que la
  raíz y los alcances se ven largos para su modelo. Decisión del usuario si se escala.
- No probado en el juego (pendiente de QA).
