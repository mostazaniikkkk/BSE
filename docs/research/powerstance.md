# Powerstance (dos armas): elegibilidad, entrada, ataques y postura

Continúa [weapons.md](weapons.md), sección "Agarre a dos manos: cambio y postura" (estado de las manos
`handCtrl+0x10`: 1 una mano, 2 / 3 izquierda / derecha a dos manos, **4 dual**, **5 / 6 powerstance**).
Mano 0 = izquierda, mano 1 = derecha. `thunk_FUN_14014cbeb(handCtrl, −1 / −2)` = fila de `WeaponTypeParam` (WTP) de la
derecha / izquierda; `0x140349250` (`thunk_FUN_141c5212f`) = fila de `WeaponParam` (WP) de esa mano (spells.md §"Al
disparar", desofuscado).

Scripts y salidas de esta nota: `D:\Reversing\DS2\out\powerstance\` (`dump.py` / `dump2.py` slots 30..39 y 52..54 vía
`tools/re/weapons.py`, `pairs.py` parejas y stats, `tracks.py` pistas Morpheme, `d1.c` decompilación de
`FUN_140350170` / `FUN_14035db00` / `FUN_140395330`, `c00.txt` traza de `FUN_140393c00`); decompilaciones en `D:\Reversing\DS2\out\mvall.c`, `act.c`, `combo.c`,
`moveatk\d1.c`; desofuscado con `D:\Reversing\DS2\out\py\deob.py`; nodos Morpheme con `out\grip\grip_sm.py` y
`grip_stances.txt`.

## 1. Elegibilidad

### 1.1 Pareja de tipos — `DualWieldingPermissionParam`
- `FUN_14034d930(WTP izq, WTP der)` (llamada desde `FUN_14034f530(chr, 0)`, petición "dos manos con la izquierda") y
  `FUN_14034f470` (volver a una mano → estado 4 o 1) hacen **la misma prueba**:
  - fila = `paramMgr+0x490` (índice 0x18 = **`DualWieldingPermissionParam`**, `out\paramlist.c`, `local_298[0x18]`;
    getter `FUN_140358830`) con id = **`WTP+0x38` del arma derecha**;
  - `FUN_14034ede0(WTP+0x38 del arma izquierda, fila)` lee un byte de la fila según la categoría izquierda
    (switch): 0→`+0x00`, 10→`+0x01`, 20→`+0x02`, 30→`+0x03`, 40→`+0x04`, 50→`+0x05`, 55→`+0x1a`, 60→`+0x06`,
    70→`+0x07`, 80→`+0x08`, 85→`+0x19`, 90→`+0x09`, **100→`+0x0b`, 110→`+0x0a`** (cruzados en el código),
    120→`+0x0c`, 130→`+0x0d` … 240→`+0x18` (de 10 en 10). Otro valor → falso.
  - Byte ≠ 0 → la pareja vale.
- `WTP+0x38` es una **categoría de powerstance**, no el tipo de arma: 27 valores (0..240 de 10 en 10, más 55 y 85).
  Ningún flag de `WeaponParam` interviene.
- Lado de la powerstance (`FUN_14034d930`): devuelve 1 si `cat_izq > cat_der` y 2 si `cat_izq ≤ cat_der`;
  `FUN_14034f530` lo convierte en **estado 5 (1) o 6 (2)**. Con dos armas de la misma categoría: estado 6.
- Matriz de la regulation (fila = categoría derecha → categorías izquierdas aceptadas):

  | Der. | Izquierdas aceptadas |
  |---|---|
  | 0 | ninguna |
  | 10 | 20 |
  | 20 | 10, 20 |
  | 30 | 30, 40, 50, 55, 150, 160 |
  | 40 | 30, 40, 50, 55, 60, 150, 160 |
  | 50 | 30, 40, 50, 55, 70, 80, 85, 90, 150, 160 |
  | 55 | 30, 40, 50, 55, 70, 90, 150, 160 |
  | 60 | 60, 140 |
  | 70 | 50, 55, 70, 90, 110, 120 |
  | 80 / 85 | 50, 80, 85, 100, 110, 120 |
  | 90 | 50, 55, 70, 90, 110, 120 |
  | 100 | 80, 85, 100, 110, 120 |
  | 110 / 120 | 70, 80, 85, 90, 100, 110, 120 |
  | 130 | 130, 140 |
  | 140 | 60, 130, 140 |
  | 150 / 160 | 30, 40, 50, 55, 150, 160 |
  | 170 / 180 / 190 / 240 | solo la misma |
  | 200 / 210 / 230 | ninguna |
  | 220 | 230 |

### 1.2 Requisito de stats — `FUN_140350170` (asm, el decompilador pierde el final)
- Solo para los estados pedidos 4, 5 y 6 (`param_2 − 4 < 3`); otro estado se devuelve tal cual.
- Filas = `WeaponParam` de las dos manos (`0x140349250`, −2 / −1).
- `reqFue = min(99, (int)(max(WP_izq+0x18, WP_der+0x18) × 1.5))`,
  `reqDes = min(99, (int)(max(WP_izq+0x1a, WP_der+0x1a) × 1.5))`. `int` = truncado (`cvttss2si`).
  `1.5` = `DAT_1410bd0a8` (leído del exe). `+0x18` / `+0x1a` = requisito de Fuerza / Destreza (damage.md §3).
- Stats = `FUN_1402036f0` → `FUN_140203760`: los 11 `u16` **efectivos**; compara `+8` (slot 4 = Fuerza) y `+0xa`
  (slot 5 = Destreza) (stats.md). **Solo Fuerza y Destreza**; Int / Fe no.
- Si `Fuerza ≥ reqFue` y `Destreza ≥ reqDes` → devuelve el estado pedido. Si no: pedido 4 → **1**; pedido 5 / 6 →
  **2** (arma izquierda a dos manos).

### 1.3 Armas del mod (`WEAPONS` de `tools/re/weapons.py`)

| Arma | WP | `WTP+0x38` | Req. Fue / Des |
|---|---|---|---|
| Longsword, Broken Straight Sword, Puzzling Stone Sword, Shortsword, Drakekeeper's Sword, Red Rust Sword | 1220000, 1200000, 1250000, 1210000, 1370000, 1330000 | 50 | 10/9, 2/3, 7/6, 7/10, 16/8, 23/13 |
| Hand Axe, Infantry Axe, Bandit Axe, Dragonslayer's Crescent Axe, Battle Axe | 2000000, 2030000, 2020000, 2080000, 2010000 | 90 | 9/3, 9/1, 18/5, 18/12, 15/5 |
| Gyrm Axe | 2070000 | 110 | 28/6 |
| Mace, Club | 2410000, 2400000 | 70 | 12/5, 12/3 |
| Spear | 2800000 | 150 | 9/12 |
| Pickaxe | 2610000 | 120 | 22/8 |
| Fists | 3400000 | 10 | 0/0 |
| Drangleic Shield, antorcha 6000000, Sorcerer's Staff, Cleric's Chime, Pyromancy Flame | — | 0 | — |

Parejas aceptadas (por categorías; vale en cualquier orden salvo donde se indica):
- **espadas (50)**: con espadas, hachas 90, maza/garrote 70 y lanza 150;
- **hachas 90**: con espadas, hachas 90, Gyrm 110, maza/garrote 70 y pico 120;
- **Gyrm 110 / pico 120**: con hachas 90, Gyrm, maza/garrote y pico;
- **maza/garrote 70**: con espadas, hachas 90, Gyrm, maza/garrote y pico;
- **lanza 150**: con espadas y lanza;
- **nunca**: puños (10 solo acepta 20, y 20 no existe en el mod), escudo, antorcha y catalizadores (categoría 0).
- Estado: 6 si `cat_izq ≤ cat_der`, si no 5. Ej.: dos espadas → 6; espada izq. + maza der. → 6; maza izq. + espada
  der. → 5.
- Stats necesarios (ejemplos, `pairs.py`): dos Longsword **15 / 13**; dos Club **18 / 4**; dos Mace 18 / 7;
  Longsword + Mace 18 / 13; dos Spear 13 / 18; dos Red Rust Sword 34 / 19; dos Gyrm Axe 42 / 9.

## 2. Entrada y estados
- **Estado 4 (dual) es automático**: `FUN_14034f470` (vuelta a una mano, cambio de arma `FUN_14036b9d0`, antorcha
  `FUN_14034f300`, recarga del equipo en los estados 1/4) pone 4 si la pareja vale y se cumplen los stats; si no, 1.
- **Entrar**: petición 1 ("dos manos con el arma izquierda", `thunk_FUN_141b10376(entrada, 1)`) en el estado 4
  (`FUN_140395330`): cancela lo pendiente de las dos manos (`thunk_FUN_141b16348(chr, 0 / 1)`) y llama
  `FUN_14034f530(chr, 0)` **directo**: sin animación de guardado (`WTP+0x24` no se mira), sin la comprobación de manos
  libres `FUN_14034d9b0`. Resultado 5 / 6 (o 2 si fallan los stats, imposible desde 4 salvo cambio de stats).
- **Salir**: petición 3 ("volver a una mano") en 5 / 6 → `thunk_FUN_14034f470` directo → 4 (o 1). Sin animación.
- En 5 / 6 las peticiones 1 y 2 se ignoran (solo valen en 1 / 4).
- Petición 1 en el estado 1 (pareja que no vale, o stats insuficientes) = el agarre a dos manos normal con
  animación (weapons.md); no da powerstance.
- Al cambiar el estado: `handCtrl+0x10 = estado`, `*(handCtrl)+0x4f0 |= 1`, `handCtrl+0x35 = 1` y
  `FUN_1401628d0(chr, estado)` (sin identificar; probablemente red).
- **Botón físico**: el mismo de la petición 1 (en el mod, la tecla de agarre del arma izquierda, `G` agachado).
  Qué botón de DS2 genera cada petición está **sin confirmar** (capa de entrada, igual que en weapons.md).
- Bloqueos: solo los de los estados. Si atacar, rodar o estar en el aire impiden la petición: **sin confirmar**
  (`FUN_14038f200` solo exige `handCtrl vt+0xa8` y las dos filas).

## 3. Ataques

### 3.1 Tipo de acción — `FUN_1401f9ca0` / `FUN_140396680`
- `FUN_1401f9ca0` (asm verificado) en los estados 5 / 6: `+0x1d = +0x1b = (mano del botón == 0)`.
  `+0x1d` es el "flag powerstance" (`local_1b` en `FUN_140396680`): **solo para los botones izquierdos**.
- Grip (`FUN_140397a80`): estados 5 / 6 → **5** en las dos manos → bloque de slots **30..39**.
  Estado 4 → 4 (slots 20..29) solo si `b8+0x4d4 ≠ 0` (ventana de combo) y la mano del botón ≠ `b8+0x188` (la del
  ataque en curso); si no, 2 / 6 (una mano normal).
- **Botones izquierdos (mano 0)** en 5 / 6, en el orden de prueba de weapons.md:
  - ligero: caída 8 → tras rodar 6 → corriendo 5 → tras paso atrás 10 → patada 0x10 → combo 2 / 0x13 / 0x12 (no
    aplican) → **0x0d**, o **0x0e** si el ataque en curso es 0x0d de la misma mano y en ventana de combo
    (`FUN_1401f9d50(…, 0xd)`). Cadena 0x0d → 0x0e → 0x0d…
  - fuerte (`param_1[7] == 0`): caída 8 → salto 9 → tipo 7 → 0x14 → **0x0f**. Con `param_1[7] ≠ 0` solo 3↔4 / 0x15
    (sin identificar, weapons.md).
  - Tipos 0x0d / 0x0e / 0x0f → **slots 52 / 53 / 54** (tabla `0x1403977b8`).
- **Botones derechos (mano 1)**: los tipos normales 1..10 con grip 5 → slots 30..39. En todas las armas del mod los
  slots 30..39 son **los mismos `WeaponAttackMotionParam` que 0..9** (una mano derecha), y `WeaponStaminaCostParam`
  fila 10 tiene los mismos factores en 30..39 que en 0..9 (comprobado).
- Corrección a weapons.md ("Orden de prueba", "3 (0x0f a dos manos)"): el 0x0f no es de dos manos; `+0x1d` solo se pone
  en los estados 5 / 6.

### 3.2 De qué arma sale el ataque — `FUN_140395b40`
- Mano del arma (`iVar11`): tipos 0x0b / 0x0c → la otra mano; **tipos 0x0d..0x0f → la izquierda en el estado 5 y la
  derecha en el 6**; el resto → la mano del botón.
- Fila = `FUN_140349010(manos, iVar11 ? −1 : −2)` (`WeaponActionCategoryParam`) y el slot de `FUN_140397514(fila, tipo,
  grip)`.
- Consecuencia: el moveset de L1 / L2 sale del arma de **categoría mayor** (empate → la derecha). R1 / R2 → el arma
  derecha (slots 30..39); los ataques en movimiento con botón izquierdo (rodar, correr, salto, caída) → el arma
  **izquierda**, slots 34/35/36/38/39 de su fila.
- Rareza del código: en 5 / 6, `FUN_1401fa030` usa `+0x1b` (cruzado: botón derecho → fila izquierda) para comprobar si
  el slot está vacío, pero el ataque se arma con la fila de `iVar11`. Con las armas del mod no importa (todos los slots
  llenos).

### 3.3 Golpe de las dos armas — segunda descripción (`FUN_140392920`, desofuscado)
- `FUN_140393c00` (inicio del ataque) llama `FUN_140392b20(ctrl, desc, desc+0x18)` y después `FUN_140392920(ctrl, desc)`.
- `FUN_140392920`: solo si `desc+0x0c` (tipo de acción) ∈ 0x0d..0x0f. Arma una segunda descripción con
  `FUN_14038f4d0`:
  - copia los 0x78 bytes de la original (incluido el **coste `+0x24`**);
  - `+0 = (desc+0 == 0)`: la **otra mano**;
  - `+0x18 = +0x1c` = **0 si la nueva mano es la derecha, 5 si es la izquierda** (base de ranura);
  - `+0x48` = `WeaponParam` de esa mano (`0x140349250`), `+0x58` = su `WeaponActionCategoryParam`, `+0x60` =
    `FUN_140397510(fila, mismo tipo, mismo grip)` = **su propio slot 52 / 53 / 54**, `+0x50` = su WTP;
  - `+0x20` = estamina actual (`FUN_140333790`), `+0x29` / `+0x2a` = `FUN_140390ea0` / `FUN_140390f10` (sin identificar).
  - Si `+4` (grip) ≠ 0: `FUN_140392b20(ctrl, nueva, +0x18)` y `FUN_140392a60(ctrl, nueva, +0x1c)`.
- `FUN_140392b20` (desofuscado): para `i` = 0..2, ranura `base + i` ← PDP `WAMP+0x2c + 4i`; si el id ≠ 0, el valor de
  ataque con `FUN_140391fe0` (arma, refuerzo y stats de esa descripción; damage.md); coste de la ranura = `desc+0x24`
  (`FUN_1403748c0`).
- **Ranuras 0..4 = arma derecha, 5..9 = arma izquierda.** Los eventos TAE 2200 de los `…2Weapon…` usan la ranura 0
  (arma derecha) y la 5 (izquierda); la lanza también la 2 / 6 / 7.
- Daño de cada golpe = AR de **esa** arma × `PDP+0x1c` de **su** slot 52 / 53 / 54 × factor de mano de su WTP
  (`+0x60` derecha = 1.0, `+0x54` izquierda = 0.9 en las armas del mod; damage.md §1).
  La animación (TAE) es la del arma de `iVar11`.
- **Estamina**: el evento 2200 cobra el coste de su ranura cada vez que empieza uno con PDP ≠ 0 (stamina.md). Las dos
  descripciones llevan el mismo coste (el de la principal), así que **cada 2200 de la animación cobra una vez**: ×2 en
  casi todos, ×3 en el fuerte de maza / garrote / hachas (`33050030`, tres eventos). Derivado del código; **no
  verificado en el juego**.
- La descripción principal también pone su base en `desc+0x18` (`r8d = [rsp+0x68]`, `0x14072079f`); que siga la misma
  regla 0 / 5 se deduce de que los TAE usan 0 y 5: **sin confirmar en el código**.

### 3.4 Datos (slots 52..54; `WeaponStaminaCostParam` fila 10: 1.026 / 1.134 / 1.512)
Tiempos en segundos. "Golpes" = eventos 2200 `[inicio, fin, ranura]`. Bloqueos = fin de `CancelControl_*` de
`c0001.nmb`. Coste = base `WP+0x34` × WSC[slot], por evento.

**Dos Longsword (estado 6; moveset de la derecha):**

| Botón | Slot | WAMP | TAE / animación | Dur. | Golpes | PDP (`+0x1c`) | Coste / evento | Evitar | Mover | 1L/1H | 2L/2H | Combo | Sin regen |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| L1 | 52 | 10035000 | 29050010 `a29_05_0010 StraightSwordAttack2WeaponLight1_R_Hit` | 2.0 | 0.4667–0.6 (0), 0.4667–0.6 (5) | 10035000 (0.9) | 215.46 (×2 = 430.92) | 0.2667–1.3333 | 1.4333 | 0.8333 / 1.0 | 0.8333 / 0.8333 | 0–1.2063 | 0–1.5 |
| L1 combo | 53 | 10035100 | 29050020 `a29_05_0020 …2WeaponLight2_R_Hit` | 2.0 | 0.4667–0.6 (0), (5) | 10035100 (0.832) | 238.14 (×2 = 476.28) | 0.2667–1.3333 | 1.4333 | 0.8333 / 1.0 | 0.8333 / 0.8333 | 0–1.2063 | 0–1.5 |
| L2 | 54 | 10035200 | 29050030 `a29_05_0030 …2WeaponHeavy1_R_Hit` | 2.3333 | 0.6667–0.7667 (0), (5) | 10035200 (1.08) | 317.52 (×2 = 635.04) | 0.2667–1.5 | 1.6 | 1.0 / 1.0 | 1.0 / 1.0 | 0–1.5 | 0–1.8333 |
| R1 / R1 combo / R2 / R2 combo | 30 / 31 / 32 / 33 | = slots 0..3 | los de una mano (weapons.json `right`) | | | | 199.5 / 220.5 / 294 / 304.5 | | | | | | |
| correr / rodar / paso atrás / caída / salto | 34 / 35 / 36 / 38 / 39 | = 4 / 5 / 6 / 8 / 9 | R: arma derecha; L: arma izquierda | | | | 157.5 / 157.5 / 157.5 / 346.5 / 346.5 | | | | | | |

Las otras espadas rectas usan los mismos TAE y PDP (salvo Red Rust Sword, abajo); cambia el coste
(base 100 / 210 / 183 / 237 para Broken / Puzzling / Shortsword / Drakekeeper's).

**Dos Club (estado 6):** base 237.

| Botón | Slot | WAMP | TAE / animación | Dur. | Golpes | PDP (`+0x1c`) | Coste / evento | Evitar | Mover | 1L/1H = 2L/2H | Combo | Sin regen |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| L1 | 52 | 10049500 | 33050010 `a33_05_0010 HummerAttack2WeaponLight1_R_Hit` | 2.3333 | 0.6–0.7333 (5), 0.6667–0.8 (0) | 10049500 (0.9) | 243.162 (×2) | 0.2667–1.5 | 1.6 | 1.1667 | 0–1.5 | 0–1.8333 |
| L1 combo | 53 | 10049600 | 33050020 `a33_05_0020 …Light2_R_Hit` | 2.3333 | 0.6–0.7333 (5), 0.7–0.8333 (0) | 10049600 (0.832) | 268.758 (×2) | 0.2667–1.5 | 1.6 | 1.3333 | 0–1.5 | 0–1.8333 |
| L2 | 54 | 10049700 | 33050030 `a33_05_0030 …Heavy1_R_Hit` | 2.3333 | 0.5667–0.7 (0), 0.6667–0.8 (5), 1.0667–1.2 (0) | 10049700 (0.864) | 358.344 (×3) | 0.2667–1.8333 | 1.9333 | 1.6667 | 0–1.8333 | 0–2.0 |

Resto de armas del mod (mismo formato, `dump2.py`):
- Mace: igual que Club (mismos TAE / PDP), base 243.
- Red Rust Sword, Hand / Infantry / Bandit / Crescent / Battle Axe: los TAE `33050010/20/30` del Club; PDP propios
  (`10035010..210` la Red Rust; `10056400 / 500 / 600` las hachas) con 0.9 / 0.832 / 0.864.
- Gyrm Axe y Pickaxe: `34050010/20/30` `a34_05_00x0 LargeHummerAttack2Weapon…` (3.0 s, golpes 0.9667–1.1333 en 0 y 5;
  fuerte 0.9–1.1667), PDP 0.945 / 0.874 / 1.08 (Gyrm) y 0.945 / 0.874 / 1.134 (pico; PDP 1 ≠ PDP 0: `20036500` /
  `20036510`…).
- Spear: `25050010/20/30` `a25_05_00x0 SpearAttack2Weapon…` (2.0 / 2.0 / 2.3333 s), golpes en 0, 6, 2, 7 (2 y 7 tienen
  PDP 0 → no golpean ni cobran); PDP 0.864 / 0.799 / 1.037.
- Ninguno de los nueve TAE tiene evento 1220 (parry).

## 4. Postura (idle y locomoción)
- `FUN_14035db00`: estado 5 → CP `AttackStance_L_2Weapon` = `WTP+0x20` del arma **izquierda**; estado 6 →
  `AttackStance_R_2Weapon` = `WTP+0x1c` de la **derecha**. `TwoWeaponStance` (CP 0xac en `c0001.nmb`) = −2 / +2
  (`DAT_1410ada3c` / `DAT_1410acb14`). `TwoHandStance` = 0 → el SM 0x3bc sigue en la rama de una mano (0x400).
  Si `handCtrl+0xe8a` o `+0xe8b` = 0, todo se trata como estado 1 (significado sin sacar, weapons.md).
- SM 0x400 (`grip_sm.py 1024`): estados 0x401 (una mano), 0x442 (`TwoWeaponStance` = 2), 0x46a (= −2). Fundido
  **0.2 s** al entrar, al salir y entre 0x442 ↔ 0x46a.
- 0x442 (y 0x46a espejado, nodos 0x87): dos estados, ambos FeatherBlend2 (`BlendWeight_1`) de la locomoción 0x500 con
  la postura:
  1. 0x443: la animación **completa** de postura;
  2. 0x450, tras la condición 0x262 (canal 80, userData 42 = pista `ChangeStanceTwoWeaponPrepare`, 0.1; fundido
     0.2 s en 0x442, 0.1 s en 0x46a): un nodo 0x6d con el CP `SingleFrame_1` (valor por defecto **1.0**) sobre la
     misma animación → pose fija. Que 1.0 sea el último frame y que nadie cambie el CP: **sin confirmar**.
  - La pista `ChangeStanceTwoWeaponPrepare` cubre toda la animación (0..1). En `FUN_14036a6e0` el userData 42 da
    el índice 3 (efecto sin identificar).
  - **Cuándo** (condición 0x262, animations.md "Condición 0x262"): cuando al evento le queda ≤ 10 %, es decir al
    **90 % del clip** (un frame después). Se lee del estado 0x443 / 0x46b (switch por `AttackStance_*_2Weapon`), y el
    clip muestrea sus pistas él mismo porque el FeatherBlend2 0x449 / 0x474 no es sync (tabla de colas 9).

    | Clip | Duración | Inicio de la mezcla |
    |---|---|---|
    | `a21_45_0010` (21450010) | 0.5 s | 0.45 s |
    | `a25_45_0010` (25450010) | 0.5 s | 0.45 s |
    | `a36_45_0010` (36450010) | 0.6667 s | 0.6 s |

  - Mezcla: **0.2 s en 0x442 (transición 0x469) y 0.1 s en 0x46a (0x49f, la izquierda espejada = las variantes `_l`
    del mod)**. Modo 0, sin congelar: el clip sigue avanzando hasta su final mientras se mezcla con `SingleFrame_1`.
    Peso del destino = (1 − cos(π t / duración)) / 2, no lineal.
  - El espejo no cambia el momento: 80 / 42 no están en la tabla de espejo de eventos (animations.md).
- Postura por arma (`WTP+0x1c` = `+0x20`):

  | Valor | Armas del mod | Animación | Duración |
  |---|---|---|---|
  | 214610 | espadas rectas, hachas 90, maza, garrote | `a21_45_0010_KnifeStance2Weapon_R` | 0.5 |
  | 254610 | lanza | `a25_45_0010_SpearStance2Weapon_R` | 0.5 |
  | 364610 | Gyrm Axe, pico | `a36_45_0010_LargeAxeStance2Weapon_R` | 0.6667 |

- Alfas (lo no listado = 0; "dedos" = los 15 de esa mano):
  - 214610, fase 1: Neck / Head 0.2; Spine, Spine1, R_Clavicle, R_UpperArm, R_Forearm 0.5; todo el brazo izquierdo,
    R_Hand, dedos derechos y R_Weapon 1. Fase 2: Neck / Head / R_Clavicle / R_UpperArm 0.1; Spine 0.2; Spine1 0.25;
    L_UpperArm 0.5; L_Forearm 0.75; L_Clavicle 0.9; L_Hand, dedos izq., L_Weapon, R_Forearm, R_Hand, dedos der.,
    R_Weapon 1.
  - 254610: fase 1 igual que 214610; fase 2 igual que la fase 2 de 214610.
  - 364610, fase 1: Neck / Head 0.2; Spine / Spine1 0.5; los dos brazos enteros 1. Fase 2: Spine / Spine1 / Neck /
    Head 0.1; L_UpperArm / R_UpperArm 0.7; el resto de los dos brazos 1.
  - Rama izquierda (0x46a, estado 5): las mismas animaciones espejadas; alfas en `grip_stances.txt` (switch 0x46b /
    0x47e).
- Hay otros switches sobre `AttackStance_*_2Weapon` (contenedores 0x54a / 0x592, nodos 0x96 `FrontTarget` hacia 0x3bc
  / 0x537): su papel (¿fijado?) está **sin identificar**.
- No hay animaciones propias de andar / correr en powerstance: la locomoción es la normal (0x500) con esta capa.

## 5. Cambios de estado
- Entrar / salir: sin `WeaponChange…` ni coste; solo los fundidos de 0.2 s del SM 0x400 y la animación de postura
  de §4.
- Cambiar el arma de cualquier mano (`FUN_14036b9d0`): `FUN_14034f470` → 4 o 1 (sale de la powerstance).
- Encender la antorcha (`FUN_14034f300`): `FUN_14034f470` con la antorcha (categoría 0) → 1.
- Recarga del equipo (`FUN_14037d1f0` / `FUN_14037d380`): 5 / 6 → `FUN_14034f530(chr, 0)`: sigue en 5 / 6 si la
  pareja y los stats valen; si no, **2** (izquierda a dos manos).
- Muerte / reaparición: **sin confirmar**.

## 6. Otros sistemas
- **Guardia y parry**: en 5 / 6 los botones izquierdos dan 0x0d..0x0f (o ataques en movimiento); ningún camino de
  `FUN_140396680` llega a un slot de guardia (tipo 13) ni al parry (slot 51 = 0x0c, que solo sale con la otra arma a
  dos manos). **No hay guardia ni parry en powerstance.**
- Estabilidad (`FUN_14034d7b0`, stamina.md): estado 4 → `WTP+0xd8`, 5 / 6 → `+0xdc` (0 en todas las armas del mod).
- **Ventanas de cancelación** (`FUN_140396070`): en los estados 4..6 (`FUN_14034f050` = estado 4, `FUN_14034f060` =
  5..6), el botón de la **otra** mano respecto al ataque en curso (`b8+0x188`) mira `CancelControl_Attack2L` /
  `2H` (`b8+0x570` / `+0x574`) en vez de `1L` / `1H`.
- Falta de stats: la mitad de Fuerza solo es para 2 / 3 (damage.md); en 5 / 6 los stats ya superan 1.5× el requisito,
  así que la falta es 0.
- Esquiva y regeneración de estamina: no se encontró dependencia del estado de las manos en lo documentado
  (stamina.md); búsqueda no exhaustiva.

## 7. Sin confirmar
- Qué botón de DS2 da la petición 1 / 3, y si atacar, rodar o el aire las bloquean.
- Si `b8+0x188` guarda la mano del botón o la del arma: decide si la cadena 0x0d → 0x0e funciona en el estado 6
  (`FUN_1401f9d50` compara `b8+0x188` con la mano del botón).
- Base de ranura de la descripción principal (se deduce 0 / 5 de los TAE).
- El coste ×2 / ×3 por evento (derivado del código, no medido).
- Si el estado 5 espeja la animación del ataque (moveset del arma izquierda).
- `SingleFrame_1` = último frame; papel de los switches `FrontTarget`; el campo `+0x14` de la transición (0 aquí, 3 en
  la guardia).
- `param_1[7]` (el fuerte con combos 3↔4).
- Qué pasa al morir.

## 8. Exportado (`tools/re/weapons.py`, `tools/re/powerstance.py`, `tools/re/ds2_anims.py`)
- `weapons.json`, por arma:
  - `powerStanceCategory` (`WTP+0x38`);
  - `powerStanceStance` `{right: WTP+0x1c, left: WTP+0x20}`;
  - bloque `power_stance` `{light: slot 52, light_combo: 53, heavy: 54}`, con la misma forma que las otras manos.
    `hitBack` / `hitVolume` por golpe leen el PDP `ranura % 5` de la propia fila del arma.
- `weapons.json`, global:
  - `dualWielding` `{"<cat der>": {"<cat izq>": bool}}`, ya resuelto con el switch de `FUN_14034ede0`;
  - `powerStanceClips` `{"<stance>": {right|left: {animation, duration, blendIn, holdBlend, clipBones, holdBones,
    holdSuffix}}}`;
  - `animations[tae].hits` ya lleva la ranura (0..9) como tercer elemento.
- Pesos por hueso de Minecraft: el alfa del hueso DS2 del que sale cada miembro, igual que `GripClient`
  (cuerpo = Spine1, cabeza = Head, brazo = UpperArm, objeto = Weapon; piernas 0). Los alfas DS2 completos están en
  `powerstance.py` y coinciden con `c0001.nmb` en todos los huesos.
- Animaciones:
  - `ds2_21450010`, `ds2_25450010`, `ds2_36450010`, y las versiones `_l` (el mismo clip por el espejo; no hay clips
    `_L` propios);
  - las 12 de locomoción con la pose de mantener ya horneada: `<loco>_2w_<stance>[_l]`, con el último frame y los
    alfas de `hold`, como los `_2h_`;
  - los ataques `ds2_<tae>` de los slots 52..54.

## 9. Implementación de la entrada (2026-11-01, `PowerStanceClient`)
- Entrada: el clip de postura con `clipBones` sobre la locomoción a una mano, fundido 0.2 s con (1 − cos)/2; a 0.9 del
  clip (condición 0x262) empieza el paso a la pose mantenida (`holdBlend`: 0.2 s derecha, 0.1 s izquierda), con el clip
  siguiendo. La mezcla por hueso es locomoción + (clip − loc) × clipBones × (1 − w) + (pose − loc) × holdBones × w; al
  terminar, la locomoción pasa a `_2w_` (pose horneada).
- Salida: la pose mantenida con `holdBones` se funde 0.2 s hacia la locomoción a una mano.
- Cambio de lado (2026-11-01): transición 0x4a7 / 0x4a8 de 0.2 s del lado mostrado (siga en el clip o ya en la pose
  mantenida) al otro, que entra por su clip desde el principio; los dos extremos siguen avanzando (modo 0, sin
  congelar, animations.md). Dos ranuras con su muestreador de clip y de pose; mezcla
  locomoción + w × Δ(destino) + (1 − w) × Δ(origen).
- Salir cambiando de arma: se guarda el último lado, así que el fundido de 0.2 s (0x4a3 / 0x4a4) se hace aunque el
  arma que daba la postura ya no esté (antes se cortaba porque se buscaba el clip con el arma nueva).
- Sin confirmar: el campo `+0x14` = 3 de estas transiciones; las transiciones encadenadas (cambiar de lado otra vez
  durante un cambio: el mod toma el lado de destino como origen y suelta el anterior).
