# Arcos: agarre, apuntado, disparo y flecha

Arma de referencia: **Short Bow 4200000** (WeaponParam; categoría 4200, WeaponTypeParam 361, modelo `WP_4200_M`).
Flecha de referencia: **Wood Arrow 60760000** (ArrowParam / ItemParam).

Salidas de Ghidra (solo lectura, headless): `D:\Reversing\DS2\out\bow\` (`subj_ctor.c`, `subj_vt.c`, `subj_upd.c`,
`pad*.c`, `f396680.txt`, `arrow_get.c`, `bit30.c`…). Trazas desofuscadas con `out\py\deob.py` (citadas por dirección).
Trazas previas reutilizadas: `out\st_deob_3c00.txt`, `out\case_1403942eb.txt`, `out\case_14039450e.txt`,
`out\case_14039454b.txt`. Datos: regulation (`tools/re/params.py`), `c000100_pl.tae`, `c0001.nmb`
(`out\plunge\sm.py`, `out\plunge\grip_nodes.py`), modelos extraídos en `D:\Reversing\DS2\extracted\models\`.
Convenciones (`CancelControl_*`, slots, agarres, manos 0 = izquierda / 1 = derecha) como en
[weapons.md](weapons.md), [casting.md](casting.md) y [stamina.md](stamina.md).

## 1. Botones, agarre y slots

### 1.1 Del pad a la petición (`FUN_140395d80`, cada frame)
- Botón de la mano izquierda (hand 0): pulsado (`pad+0x128` bit 1 / bit 3) → código **1 / 2**; mantenido
  (`pad+0x12c` bit 1 / bit 3) → código **3 / 4**. Mano derecha: bits 0 / 2, mismos códigos.
  Llama a `FUN_140395b40(ctrl, mano, código)`.
- **Código: 1 = ligero pulsado, 2 = fuerte pulsado, 3 = ligero mantenido, 4 = fuerte mantenido.**
  Con `b8+0x4c0` bit 8 (salto) se fuerza el código 1.
- `FUN_140396070` (bloqueo por `CancelControl_Attack*`): solo los códigos 1 y 2 miran `b8+0x568` / `+0x56c`.
  **Los códigos 3 / 4 (mantenido) no pasan por los bloqueos** (rama `0x14039618b`: solo exige un arma en la mano).

### 1.2 Remapeo de los arcos (`FUN_140396540`)
- Si el arma de la mano (o la de la otra mano cuando esa está a dos manos) tiene `WeaponTypeParam +0x35` en 1..3
  (1 arco, 2 gran arco, 3 ballesta), el fuerte se trata como el ligero: **2 → 1 y 4 → 3**.
- Short Bow: WTP 361 `+0x35 = 1`.
- El código original sigue en la petición (`desc+0x08`) y elige la flecha (§4.3).

### 1.3 Tipo de acción con el arma de la otra mano a dos manos (`FUN_140396680`, traza `bow\f396680.txt`)
- `sil = 1` cuando la otra mano está a dos manos (mano 0 con estado 3, o mano 1 con estado 2; estados en weapons.md).
- Con `sil`: ligero (1 o 3) → tipo **0x0b**, fuerte (2 o 4) → **0x0c** (bloques `0x1403967c2`, `0x140396b33`,
  `0x140396b5e`), es decir, slots **50 / 51** del arma a dos manos.

### 1.4 Slots del Short Bow (WeaponActionCategoryParam 4200 → WeaponAttackMotionParam)
| Slots | Uso | WAMP | TAE (+0x00) | Tipo de movimiento (+0x04) |
|---|---|---|---|---|
| 0–6 (una mano, derecha) y 40–46 (una mano, izquierda) | ligero, fuerte, carrera, tras rodar… | 20005200 | 20010010 (relleno) | **15** |
| 8 / 9, 18 / 19, 28 / 29, 38 / 39, 48 / 49 | caída / salto | 20005300 / 20005400 | 20010250 / 20010240 (`FistAttack1HandFallLoop` / `Jump`) | 7 |
| 10–14, 16 (dos manos) | ligero, combo, fuerte, carrera, tras paso atrás | 20005500 | 40230010 | **8** |
| 50 / 51 (botones de la otra mano) | | 20005800 | 20010010 (relleno) | **16** |
| 55 / 56 | patada | 20005600 / 20006200 | 20010230 | 2 |

### 1.5 Qué hace cada tipo de movimiento: switch de `FUN_140393c00`
Tabla de salto en `0x14039475c` (índice = tipo, `r15` = `movsx [rbp-0x24]`, `st_deob_3c00.txt` l. 455–476):

| Tipo | Caso | Qué es |
|---|---|---|
| 1–7 | `0x140393f7b` | ataque normal |
| **8–11** | `0x1403942eb` | **disparo de arco / ballesta** (§3) |
| 12 | `0x140394148` | hechizo |
| 13 | `0x140394261` | guardia |
| **15** | `0x140394729` | **la salida de la función: no hace nada** |
| **16** | `0x14039450e` | **solo pide la cámara de apuntado** (§2) |
| 17 | `0x14039454b` | apuntado y además un estado de animación (no lo usa el Short Bow) |

### 1.6 Conclusión (respuesta al punto 2 de QA)
- **Con una mano el arco no dispara**: los botones de su mano caen en el tipo 15, que no hace nada. Solo salen la
  patada y los ataques de puño en salto o caída (tipo 7, animaciones `a20`).
- **A dos manos**:
  - los dos botones de la mano del arco (R1 y R2) disparan (tipo 8);
  - **los dos botones de la otra mano (L1 y L2) solo apuntan** (tipo 16). No tensan el arco, no disparan y no
    tienen animación: el caso 16 no llama a `FUN_140391ab0`.
- **Sí hay disparo sin apuntar** ("de cadera"): el tipo 8 funciona con o sin la cámara de apuntado. La dirección
  cambia según el modo (§4.2).
- **Estamina**: el tipo 16 no necesita estamina (excepción `0x10` de `FUN_140367a80`, stamina.md). Los tipos 8–11
  sí la necesitan al pulsar, pero no en las peticiones mantenidas (byte `+9` distinto de 1 o 2, stamina.md).
- Si la petición de agarrar a dos manos se hace sola al pulsar R1 con el arco a una mano: **sin confirmar**
  (no hay nada en este camino).

## 2. Modo de apuntado (cámara subjetiva)

### 2.1 Entrar y salir
- **Entrar**: `FUN_140390f90` (llamado en el caso 16 `0x140394526` y en el 17):
  - si `FUN_140203be0(chr)` es verdadero, pone `ctx+0x12 = 1`;
  - si `[chr+0x480]+0x10 == 0`, pide entrar (`+0x11 = 1`) y escribe `b8+0x9c` según WTP `+0x35` (6 → 1, 7 → 2,
    otro → 0; arco = 0) y `b8+0x98 = WTP +0x134`.
- `FUN_14030eb60` (update de `PlayerActionCtrl`): con `+0x11` y sin modo activo, `FUN_1404922f0(cámaras,
  {modo 2, fila = b8+0x98})` → `IngameCameraOperator+0x400` = **`SubjectiveCameraOperator`** (vtable `0x1410f4c98`).
  Mientras dura: `+0x10 = 1` y `b8+0x4c8` bit 30.
- **Salir**: `FUN_140393a30` (update del control de ataque) pide `FUN_140393c00` para las dos manos.
  - Si en ese frame **nadie puso `ctx+0x12`** y el modo está activo, pide salir (`+0x12 = 1` en
    `[chr+0x480]`, bloque `0x141afbd7b` → `0x14015a0c7`).
  - Después borra `ctx+0x12`.
- Como mantener L1 genera la petición 3 (tipo 0x0b, slot 50, tipo 16) en cada frame, **el apuntado dura lo que se
  mantenga el botón y termina el primer frame sin petición**.
- **Al salir** (`FUN_14030eb60`): el personaje gira hacia el adelante de la cámara (`cámara+0x190`). La cámara normal
  recibe el yaw de ese adelante (`FUN_140492080` con `{1, ángulo}`) y se cierra el modo (`FUN_140492300`).
- **Al entrar** (`FUN_1404a3710`, vt[9]): yaw = `atan2(adelante.x, adelante.z)` y pitch = `−atan2(adelante.y,
  |adelante.xz|)` de la cámara anterior. El apuntado empieza mirando a donde miraba la cámara.

### 2.2 Fila de `CameraSubjectiveParam`
- Fila = WTP `+0x134`. Los 6 arcos tienen **10**, los grandes arcos 20, las ballestas 30, los catalizadores 40 y
  una fila 50.
- Las filas 10–100 son iguales, salvo el u16 `+0x02` de la 50.
- Getter: `FUN_140491c70`, `+0x3d0 → +0xc8`. Lo lee `FUN_1404a3450` (vt[11], inicio) y queda en `op+0xb8`.

| Campo (fila 10) | Valor | Lector | Uso |
|---|---|---|---|
| `+0x01` u8 | 0 | `FUN_1404a3bf0` | Pivote. Con 0, `vt+0x148` del personaje enganchado (`op+0xb0`). Qué punto da `vt+0x148`: **sin confirmar** (¿los pies?) |
| `+0x04` | **0.43633 rad (25°)** | `FUN_1404a3450` / `FUN_1404a3860` | **FOV** (mínimo 1°, `DAT_1410ac9f0`). Near 0.1, far 5000; aspecto de la pantalla |
| `+0x14 / +0x18 / +0x1c` | **0 / 1.5 / 0.4** | `FUN_1404a3ea0` | Ojo = pivote + `+0x14`·lado + `+0x18`·arriba + `+0x1c`·adelante (girado por el yaw): **1.5 m sobre el pivote y 0.4 m por delante**. Es una vista en primera persona (la fila 0, la genérica, está a −5.6 m) |
| `+0x08 / +0x0c / +0x10` | 0 / 1.5 / 4.0 | `FUN_1404a3cf0` | Punto mirado = pivote + R(yaw)·Rx(pitch)·(0, 1.5, 4.0). Con pitch 0 queda a la altura del ojo: la vista es horizontal |
| `+0x20 → +0x24` | **3 → 5 rad/s** | `FUN_1404a4050` | Velocidad máxima del yaw con el stick: lerp por la opción "velocidad de cámara" (`cámaras+0x3b0` / 100) |
| `+0x38 → +0x3c` | **3 → 5 rad/s** | ídem | Velocidad máxima del pitch |
| `+0x30 → +0x34` / `+0x48 → +0x4c` | 50 / 50 | ídem | Frenado de la velocidad angular al soltar el stick (rad/s²) |
| `+0x28 / +0x2c`, `+0x40 / +0x44` | 2 / 2, 2 / 4 | `FUN_1404a3fb0` / `FUN_1404a4000` | Aceleración hacia la velocidad pedida. La fórmula de esas dos funciones: **sin leer** |
| `+0x50 / +0x54` | 0.7854 / 0.5236 | `FUN_1404a4540` | **Pitch acotado a [`+0x50` − π/2, π/2 − `+0x54`] = [−45°, +60°]** |
| `+0x58 / +0x59`, `+0x5c / +0x60` | 3 / 3, 0.2 / 0.2 | `FUN_1404a33e0` (vt[7]) | Parámetros de la transición; probablemente fundido de entrada y salida de 0.2 s. El consumidor **no está rastreado** |

- **Signo del pitch**: positivo = mirar hacia abajo. Sale de la conversión de entrada (`−atan2(y, h)`), no de una
  prueba en juego. Con eso el apuntado sube como mucho 45° y baja 60°.
- **Colisión** (`FUN_1404a3a70`): rayo desde (pivote.x, ojo.y, pivote.z) hasta el ojo, filtro 0x1f. Con un
  impacto, el ojo se acerca al punto de contacto.
- **Ratón (PC)** (`FUN_1404a4050` / `FUN_1404a4540`):
  - si `|Δ| > 0.1`, el delta del ratón (`DAT_1416751f8+0x60 → +8 → +0x18`) × 0.01745 (1° por unidad);
  - se suaviza con `v += (objetivo − v)·0.5` por frame;
  - se suma al yaw y al pitch × `(0.1 + 0.019·sens) × 0.24`, con `sens` = byte `+0x14` de las opciones (0..100;
    `DAT_1410acb08` 0.01, `DAT_1410f4b68` 1.9, `DAT_1410f4d10` 0.24);
  - no se multiplica por `dt`;
  - inversión de ejes: `cámaras+0x3b4` (X) y `+0x3b5` (Y; con 0 se invierte la Y del stick).
- **Origen del delta** (2026-11-01, `scratchpadowmouse\`): `DAT_1416751f8` = `MainApp` → `+0x60` gestor de entrada →
  ratón `+0x18/+0x1c`, copiado cada frame (`FUN_140b0c950`) del `DLUID::MouseDevice` (DirectInput8, eje relativo:
  `SetProperty(DIPROP_AXISMODE, DIPROPAXISMODE_REL)`; `GetDeviceState(DIMOUSESTATE2)` en `FUN_140f074a0` →
  `(float)lX / lY`). Conversor identidad (`FUN_140f1a9d0`, sin escala): **cuentas crudas del ratón de ese frame**.
  Por eso el umbral 0.1 solo significa "se movió". Se aplica al delta crudo, no al suavizado.
- **Sensibilidad**: byte `+0x14` de las opciones (`(DAT_1416148f0+0xa8)+0xc8`), **por defecto 50** (`FUN_14019cb30`;
  página de teclado `FeGroupInGameSystemSettingKeyboard`). Con 50: 0.0174533 × 1.05 × 0.24 = **0.252° por cuenta**.
  Nombre en el menú y rango: sin establecer. `CameraManager+0x3b0` (stick, 5 por defecto) es otro valor.
- **Ritmo**: una vez por frame de juego; limitador de 60 fps (`FUN_140b4a380`, dt en [1/60, 0.05]). Suavizado por eje.
- La cámara normal usa la misma fórmula con **0.36** (`DAT_1410f4b70`) y corta la cola si los dos ejes ≤ 0.01.
- **En el mod** (`BowAim.turn`): las cuentas crudas de Minecraft (el argumento de `Entity.turn` sin su curva de
  sensibilidad), pasos de 1/60 s, sensibilidad fija en el 50 de DS2: el deslizador de Minecraft no afecta al apuntado.
- **Zoom**: 25° frente a los 44° de la cámara normal (camera.md), un 1.82× (`tan 22° / tan 12.5°`).
- **Retícula**: no la encontré (el HUD no está rastreado). **Sin confirmar.**

### 2.3 El personaje mientras apunta
- `b8+0x4c8` bit 30 desactiva el bit 39 (objetivo automático de bullets sin fijar, casting.md §7.3) en
  `FUN_14030f410`.
- El CP de la red **`IsPrecisionShoot`** = `[chr+0xe0]+0x10` (`FUN_14035da30`, `0x14035da6d`). Ningún nodo ni
  condición de `c0001.nmb` lo lee (búsqueda por CP 0x92). Su efecto: **sin confirmar**.
- El caso 16 no pone animación: la locomoción y la postura del arco siguen. **Si se puede andar mientras se apunta, y
  cómo gira el cuerpo, no está confirmado** (no encontré un lector de `+0x10` en el movimiento).

## 3. Tensar y disparar

### 3.1 Inicio (caso `0x1403942eb`, tipos 8–11)
- **Pulsado (código 1 o 2)**, en este orden:
  1. `FUN_14038fe90`, `FUN_140392df0`.
  2. **Coste**: `FUN_14038fb10` → `b8+0x1ac`.
  3. `FUN_140392ce0` (bala, §4.1).
  4. Estado **0x29 / 0x2a** (`FUN_140391ab0`): `c0+0x00` = TAE de WAMP = 40230010 → `AttackID_Number = 230010`.
  5. `b8+0x204` = (WTP `+0x35`, id de la flecha) (§4.3).
  6. `FUN_1403497e0` (inventario).
- **Mantenido (código 3 o 4)**, solo si se cumplen las tres condiciones (`0x14039431d`):
  - el tipo de movimiento actual `b8+0x18b` está en 8..11 o 16..17;
  - la mano actual `b8+0x188` es la de la petición;
  - `b8+0x4c8` bit 40 coincide con "código 4".

  Entonces pide el estado **0x2b / 0x2c**. `FUN_14035cf40` lo convierte en el CP **`IsMoveAttackHold` = verdadero**
  (`0x14035d142`). Ese CP se pone a falso en cada frame con `AttackDuration` activa (`0x14035cfc0`).
- El ramal "mantenido" no empieza un disparo nuevo: **mantener R1 no repite disparos**, hace falta pulsar otra vez.

### 3.2 Red (`c0001.nmb`)
- SM **`0x1132`** (`…|BT_MoveAttack`): las transiciones con `AttackID_Number = 230010` (condición 0x273) llevan a
  `0x1138`, y a `0x1163` desde los pseudoestados que la usan.
  - `0x1163` tiene los mismos estados con nodos espejo 0x87.
  - Las animaciones son `_L` (hechas con el arco en la mano izquierda). En `0x1163` se reproducen espejadas.
  - Qué pseudoestado corresponde a cada mano: **sin confirmar**.
- Dentro de `0x1138`:
  - **[0] `0x1139`** Prepare (`a40_24_0010`). Con `IsMoving` es un FeatherBlend2 (`0x113a`) de la locomoción
    (`0x1745`) y la Prepare: **se anda mientras se tensa**.
  - **[0]→[3]**, mezcla 0.2 s, condición 0x262 sobre la pista `AttackDuration` (canal 100, userData 120) con 0.2.
    Semántica (animations.md, "Condición 0x262"): la mezcla empieza cuando al evento le queda ≤ 20 %.
    `AttackDuration` cubre toda la Prepare (0–1) → **al 80 % de `a40_24_0010` (0.667 s de 0.833 s)**, un frame
    después. Mientras se mezcla (peso (1 − cos(π t / 0.2)) / 2), la Prepare sigue avanzando.
  - **[3] `0x1159`** espera: la Prepare en un nodo 0x6d (el último fotograma, como en casting.md) con
    `AdditiveStand` / `AdditiveBreathe`.
  - **[3]→[1]**, 0.001 s, **condición 0x270 sobre `IsMoveAttackHold` con valor 1**.
  - **[1] `0x1145`** CF (`a40_24_0020`).
  - **[1]→[2]**, 0.3 s, condición 0x25a con userData 0x65.
  - [2] `0x114a` salida.
- Los nodos 0x7a de la Prepare, la espera y el CF llevan el CP **`AimTarget`** (y `AimBlendWeightBody`): el torso
  apunta. Quién escribe `AimTarget`: **sin confirmar**.
- **Lectura** (si 0x270 es "CP igual al valor", **sin confirmar**):
  - con R1 mantenido, la flecha sale en cuanto el arco llega a la espera;
  - si se suelta antes, el personaje se queda con el arco tensado en la pose de espera hasta que se vuelve a
    mantener el botón.
  - Hay que comprobarlo en DS2 antes de portarlo.

### 3.3 Animaciones y tiempos (`.nsa +0x28`, pistas de `c0001.nmb`, TAE)
| | Prepare `a40_24_0010_BowShoot2HandLight1_L_Prepare` (TAE 40240010) | CF `a40_24_0020_BowShoot2HandLight1_L_CF` (TAE 40240020) | `a40_21_5030_BowShootNoBullet_R` (40215030) |
|---|---|---|---|
| Duración | 0.8333 s | 1.3333 s | 2.0 s |
| Bala | — | **TAE 2300, slot 0, a 0.1333 s** | — |
| `ShootStateControl` (u107) | **0.3667–0.5667** (al entrar se cobra `b8+0x1ac`, casting.md §6.1) | — | — |
| Sin regen (TAE 101100) | −0.1–1.0 (toda) | 0–0.6667 | — |
| `CancelControl_Avoid` | — (se puede rodar) | 0–0.6667 | 0.2667–0.9333 |
| `CancelControl_Move` | — | — (se anda) | — |
| `CancelControl_Attack1L..2H` | 0–0.8333 | 0–0.3333 | `AttackAll` 0–2.0 |
| Otras pistas | `AttackDuration` 0–0.833, `BowSetDuration` (u124) 0–0.833, `AttackSpeedControl` (u150) 0.333–0.733, `InputControl` 0.6–0.833 | `AttackStateControl` 100 / 101 / 102 (0 / 0.167 / 0.5), `AttackComboControl` 0–0.4, `ChangeStance` 0.833– | `CancelControl_Guard` 0–1.3 |

- Posturas: `a40_41_0010_BowStance1Hand_R`, `a40_43_0010_BowStance2Hand_R`.
- `a40_04_0030_BowAttack2HandHeavy1_L_Hit` está en `0x11a5` / `0x11d0` (AttackID 30030). El Short Bow no tiene
  ningún slot con ese id.
- **Tiempo mínimo** con R1 mantenido: Prepare entera (si la espera llega al final de la Prepare, 0.833 s) + 0.1333 s
  del CF ≈ **0.97 s** hasta la flecha. **Que mantener más tiempo cambie la flecha (daño, velocidad): no hay nada en
  el código leído**; la espera no tiene temporizador ni carga.
- Cuándo se elige `NoBullet` (sin flechas): **sin confirmar** (no localicé el escritor de 40215030).

### 3.4 Estamina (`FUN_14038fb10`, stamina.md)
- `coste = WeaponParam +0x38 (321) × WSC fila 10 [slot]`.
- Tipo 8 → base `+0x38`; R1 y R2 dan el tipo 1 → slot 10 → ×1.187 = **381.0** (unidades de stamina.md).
- Se cobra al entrar en `ShootStateControl` (0.3667 s de la Prepare), no al disparar.

## 4. La flecha

### 4.1 Bala y daño (`FUN_140392ce0`, `desc+0x68` = fila de ArrowParam)
- Grupo de 16 bytes según el agarre `desc+0x04`: 2 → `+0x04`, 3 → `+0x14`, 4 → `+0x24`…
- `FUN_140391bf0(ctx, desc, slot desc+0x1c, bala, daño = grupo+8)` arma la ranura de bala.
- Wood Arrow, grupo de dos manos `+0x14`: (60760020, 60760030, 60760020, 60760030).
  - BulletParam solo tiene **60760020**, y PDP tiene 60760020 y 60760030 (solo cambia el byte `+0x5f`, 0 / 1).
  - Bala 60760020, daño 60760020: **el orden exacto de los dos ids está sin confirmar** (un argumento sale de la pila
    sin resolver).
- Luego `FUN_1401fa410(…, ArrowParam +0x00, bala, 0xc9 = 201, mano)`: probablemente la flecha encajada en la dummy
  201 del arco (§5). Ofuscado, **sin confirmar**.

### 4.2 Dirección (casting.md §7.2 y §7.7)
- **Origen**: PDP `+0x30 = 1`, `+0x31 = 200` → dummy **200 del arco** (§5). BulletParam `+0x08/+0x0c/+0x10`
  = (0, 0, −0.5).
- **Apuntando** (`[chr+0xe0]+0x10 ≠ 0`): `FUN_14043fa80`, rayo desde la cámara activa × **100 m** contra la colisión
  (filtro 0x1b). La flecha va al punto de impacto o al final del rayo.
- **Sin apuntar**: la mirada de `PlayerLookAtCtrl+0x50`, girada por la matriz del personaje.
  - Sin fijar: recto al frente del cuerpo.
  - Durante el disparo `c0+0x54` (`AttackDuration`) está activo, así que entra la excepción de casting.md §7.7:
    la mirada toma la Y del adelante de la cámara. Esa Y es 0 entre −sin 25° y sin 5° (PlayerCommonParam fila 0,
    `+0xc4` = 0.4363 y `+0xc0` = 0.0873).
  - Límites con `c0+0x54`: ±90° / ±20°, multiplicados por `+0xac..` del arma (WTP 361: 0.5, 1.0, 0.7, 1.0; qué
    factor va con qué eje está **sin confirmar**).
  - La mirada se suaviza.
- **Con objetivo fijado** (casting.md §7.2 paso 5):
  - gira hacia el punto de fijado como mucho **±30° en horizontal** (`+0x1c` = 0.5236) y **2° en vertical**
    (`+0x20` = 0.0349);
  - sin adelanto (`+0x2c` = `+0x2d` = 0);
  - BulletParam `+0x07 = 0`: sin objetivo automático si no hay fijado.

### 4.3 Qué flecha y munición
- Se elige con `FUN_1403486e0` → `FUN_14034b7f0(equipo, tipo = WTP +0x35, cuál)`.
  - `cuál` = 0 con los códigos 1 / 3 (ligero) y 1 con 2 / 4 (fuerte): `dec cl; test cl, 0xfd; setne`.
  - **R1 dispara la flecha del hueco 1 y R2 la del hueco 2.**
- Huecos de munición (`FUN_140349c10`): 4, en `equipo + 0x358 + i·0x18` (id), `+0x360` (fila de ArrowParam) y
  `+0x368`. Tipo 1 y 2 (arco, gran arco) → huecos 0 / 1; tipo 3 (ballesta) → 2 / 3. Coincide con Arrow1 / Arrow2 de
  classes.md.
- La flecha debe tener **ArrowParam `+0x56`** = 1 (flecha), 2 (gran flecha) o 4 (virote) según el arco. Si no, el
  resultado es negativo: −3 tipo incorrecto, −4 vacío.
- Wood Arrow `+0x56 = 1`.
- **Cuándo se descuenta la flecha**: `FUN_1403497e0` (al empezar) llama a `FUN_1401ac460` (inventario, ofuscado).
  Que reste 1 ahí o al crear la bala: **sin confirmar**.
- Tope de 999 por pila en el tipo de objeto 18 (soul_items.md, por datos).

### 4.4 BulletParam 60760020 (vocabulario de `spell_export.py`)
| Campo | Valor | Efecto |
|---|---|---|
| vida `+0x48` | 1.5 s (× multiplicador de la petición) | |
| v0 `+0x4c` / vmáx `+0x58` | **40 / 50** (× multiplicador) | |
| aceleración `+0x54` desde `+0x50` | **−10 desde 1.0 s** | frena de 40 a 35 en el último medio segundo |
| gravedad `+0x5c`·`+0x64` desde `+0x60` | **1.0 × 1.8 = 1.8 /s² desde 0.7 s** (× multiplicador de retardo) | |
| homing `+0x98` / `+0x94` / `+0x12c` / `+0x90` | 0.1745 rad/s / 45° / 2 m / 0 | solo con objetivo |
| abanico / dispersión | 0 | |
| `+0xb0..+0xb3` | 0, 0, 0, 1 | muere al tocar a un personaje u objeto |

- **Alcance con multiplicadores 1.0**: 40 m en el primer segundo + 18.75 m en el último medio = **≈ 58.75 m**.
  Caída por gravedad en ese tiempo: 0.5 · 1.8 · 0.8² ≈ **0.58 m**.
- Para los arcos (tipos 8 / 9 / 10–11) los multiplicadores de velocidad, retardo de gravedad y vida salen de
  `b8+0x43c / +0x440 / +0x438` (casting.md §7.1).
  - Están dentro del array de multiplicadores `b8+0x3bc..` (índices 31..43), que el constructor `FUN_14031bcf0` llena
    con 1.0 (casting.md §8): **valen 1.0 por defecto**.
  - Qué los cambia después (anillos, efectos) no está rastreado: no hay `movss` directo a esos offsets.

### 4.5 Golpe (PDP 60760020)
- Cápsula de radio 0.05 m y largo 1.0 m.
- Valores de la fila: `+0x18` 70, tasa `+0x1c` 1.176, daño a la estamina `+0x24` 370.
- Bandos `+0x40 = 0xf2`: solo hostiles.
- Sin SpEffect.

### 4.6 AR: arco + flecha
- El bloque de ataque de la ranura sale de `FUN_140391fe0` / `FUN_140391e60` al llenarla (casting.md §7.1,
  damage.md §1): **es el AR del arco**.
  - Short Bow: físico 65 → 130 (nivel 0 → 10), requisitos Fue 7 / Des 12.
  - WeaponStatsAffectParam con coeficientes nivel 0 (0.2, 0.5, 0.35…); factor de mano 0.9 / 0.9.
- **La flecha**: por el patrón de los datos, ArrowParam guarda u16 por elemento:
  - `+0x46` físico (Wood 50, Iron 85, gran flecha de hierro 200);
  - `+0x48` magia (160), `+0x4a` rayo (140), `+0x4c` fuego (160), `+0x4e` oscuro (160);
  - `+0x50` veneno (300), `+0x52` sangrado (300), `+0x54` (250 en la Destructive Greatarrow).
  - Cada flecha elemental tiene su elemento en el campo que le toca, pero **no localicé el lector**.
- **Cómo se suman arco y flecha: sin confirmar.** No implementar hasta trazarlo (¿en `FUN_140391fe0` con
  `b8+0x204`?).

## 5. Modelo: dummies del arco y carcaj
`WP_4200_M.flv` (extraído de `GameDataEbl`, `gamedata:/model/parts/weapon/WP_4200_M.bnd`). Huesos: `Bow_Grip`,
`Bow_UpperLimb01/02`, `Bow_LowerLimb01/02`, `Bow_NockingPoint` (en (0, 0, 0.1647) m de `Bow_Grip`).

| Dummy | Hueso | Posición (m, marco del hueso) | Adelante / arriba | Uso |
|---|---|---|---|---|
| **200** | `Bow_Grip` | (0.0429, 0.0067, 0.0362) | (0, 0, −1) / (0, −1, 0) | **origen de la flecha** (PDP `+0x31`) |
| **201** | `Bow_NockingPoint` | (−0.0001, 0.0001, 0.0018) | (0, 0, −1) / (0, 1, 0) | `0xc9` en `FUN_1401fa410`: la flecha encajada (**sin confirmar**) |
| 1 / 2 | `Bow_Grip` / `Bow_NockingPoint` | ≈ 0 | | |
| 100 / 110 | `Bow_Grip` | (0, 0.006, 0.033) / (0, 0.239, 0.033) | | como en las demás armas (rastro / golpe) |

- **Carcaj**: no encontré ningún dato ni código que dibuje flechas en el cuerpo.
  - El arco no tiene una dummy para eso.
  - Las 53 dummies de `c0001` no tienen un uso de carcaj identificado.
  - ArrowParam no tiene un campo de modelo o dummy reconocible: `+0x00` = 1230… no es el modelo `WP_1230`, que es
    la Broadsword.
  - Lo único que se engancha es la flecha encajada en la dummy 201 al disparar.
  - **El carcaj del mod (tres flechas en abanico sobre el hombro) no sale de DS2, y ponerlo "al costado" tampoco.**
    Si DS2 no lo dibuja en absoluto: **sin confirmar**, porque no encontrarlo no prueba que no exista.

## 6. Otros detalles que dan la sensación
- Con fijado, el giro limitado (±30° / 2°) y el homing suave (10°/s, corta a 45° o a 2 m) hacen que las flechas
  "busquen" al objetivo fijado. Sin fijado vuelan rectas.
- El apuntado es en **primera persona** y con **zoom**: ojo a 1.5 m, FOV 25°.
- Andar mientras se tensa y se dispara (sin `CancelControl_Move`); rodar se puede durante la Prepare, pero no en los
  primeros 0.667 s del CF.
- El fuerte no es otro ataque: elige la segunda flecha.
- No hay indicador de alcance ni retícula rastreados.

## Sin confirmar (no implementar hasta resolverlo)
- Si pulsar R1 con el arco a una mano lo pasa a dos manos.
- La semántica de la condición 0x270 (`IsMoveAttackHold` = 1 → CF), y con ella qué pasa con un toque corto de R1. (0x262
  resuelta: espera al 80 % de la Prepare.)
- Qué pseudoestado de `0x1132` corresponde a cada mano, y quién escribe `AimTarget`.
- Andar y girar el cuerpo durante el apuntado; uso de `IsPrecisionShoot`.
- El punto `vt+0x148` del pivote de la cámara subjetiva y el signo del pitch.
- Las fórmulas de aceleración `FUN_1404a3fb0` / `FUN_1404a4000` y el consumidor de `+0x58..+0x60` (fundido).
- La retícula.
- El orden bala / daño de ArrowParam y qué distingue PDP 60760020 de 60760030 (`+0x5f`).
- Qué cambia los multiplicadores `b8+0x438 / +0x43c / +0x440` (por defecto 1.0, del constructor).
- Cómo se suman el AR del arco y el de la flecha (lector de ArrowParam `+0x46..+0x54`).
- Cuándo se descuenta la flecha y cuándo sale `NoBullet`.
- Si DS2 dibuja un carcaj.

## Exportado (`tools/re/weapons.py`, `cast_origins.py`, `ds2_anims.py`)
Salida: `src/main/resources/data/soulsmode/ds2/weapons.json` y `src/main/resources/assets/soulsmode/player_animations/`.
Lo marcado **(sin confirmar)** no se debe tratar como dato cerrado.

- **`weapons.short_bow`** (4200000): mismos campos que las demás armas.
  - Cada slot de tipo 8 / 15 / 16 lleva `motionType` y `bowAction` = `shoot` / `none` / `aim`, `staminaCost`,
    `hitBack: null` y `hitVolume: null`. Su `taeId` es de relleno (40230010 no tiene nodo; 20010010).
  - Los costes de los slots 11–16 son los de cada slot (WSC). En el juego R2 se remapea al tipo 1 (§1.2), así que
    cuesta lo del slot 10 (`bow.staminaCost`).
  - `dummyPolys` / `dummyPolysLeft`: dummies 200 y 201 del arco en el marco de `R_Weapon` / `L_Weapon` (rig, m).
    - Compuestas como las de los catalizadores (dummy 1 del arco sobre la 20 / 21 de c0001; ids de enganche de WTP 361
      `+0x25..+0x2e`, por datos).
    - La 201 se desplaza por la traslación de `Bow_NockingPoint`; las rotaciones de los huesos (~0) no se aplican.
      **(sin confirmar)**
  - `hitDummies` (dummy 100, para el salto con el arco) como en las demás armas.
- **`bowAnimations`** (40240010 / 40240020 / 40215030): mismos campos que `castAnimations` (`bullets` =
  [tiempo, slot] del TAE 2300, `shootState`, bloqueos, `regenBlock`).
  - `bulletBones` (como está hecha: `L_Weapon`) y `bulletBonesMirror` (por el espejo: `R_Weapon`) en el tiempo de
    la flecha.
  - `40215030` está hecha para la derecha (`_R`), al revés que las otras dos. Cuándo se usa: **(sin confirmar)**.
- **`bow`**:
  - `weapon`.
  - `aimCamera`: `cameraRow`, `fov`, `near`, `far`, `pivotMode`, `eye{side,up,forward}`, `look{side,up,forward}`,
    `pitchMin` / `pitchMax` / `pitchPositiveIsDown`, `yawSpeed` / `pitchSpeed` / `yawBrake` / `pitchBrake` /
    `yawAccel` / `pitchAccel` ([a, b] para la opción 0..100), `mouse{…}`, `blend{types,times,confirmed}`,
    `holdToAim`, `rayLength`.
    - **(sin confirmar)**: el punto del pivote (`vt+0x148`), el signo del pitch, la fórmula de `yawAccel` /
      `pitchAccel` y `blend`.
  - `arrow`:
    - `arrowId`; `bullet` + `bullets`, con la misma forma que los bullets de hechizos de `Bullets.java`;
      `bulletConfidence`;
    - `damageRow{id,rate,floor,staminaDamage}`;
    - `multipliers` (1.0, del constructor);
    - `arrowParam{ammoKind (código), physical, elements (patrón de datos, sin lector), …Confidence}`;
    - `originDummy`, `ammoSlotByButton{light: 0, heavy: 1}`.
    - **(sin confirmar)**: el orden bala / daño, cómo se suman el AR del arco y la flecha, y cuándo se gasta la
      flecha.
  - `lockOn{maxYaw, maxPitch, leadHeight, leadHorizontal, homingTurn, homingAngle, homingStopNear, homingDistance,
    homeWithoutLock}`.
  - `hipFire{pitchDeadZoneUp 5°, pitchDeadZoneDown 25° (rad), lookLimits{horizontal, vertical, speeds},
    weaponFactors, weaponFactorsConfirmed: false}`. **(sin confirmar)**: qué factor va con qué eje, y el
    suavizado.
  - `staminaCost` (381.027).
  - `animations{prepare, cf, noBullet, mirrorSuffix "_mirror", holdSuffix "_hold", sequenceConfirmed: false,
    stances{oneHand, twoHand}}`. **(sin confirmar)**: la condición 0x270 y qué entrada del SM es cada mano (0x262: ver §3.2).
- **Animaciones nuevas**:
  - `ds2_40240010`, `ds2_40240010_hold`, `ds2_40240020` y `ds2_40215030`, cada una también con `_mirror` (la hold
    como `ds2_40240010_mirror_hold`).
  - `ds2_40410010` y `ds2_40430010` (posturas en bucle), también con `_mirror`.
  - Locomoción con la postura a dos manos `ds2_<loco>_2h_404310` y `_l`. Pesos del FeatherBlend2 del switch `0x3d5`,
    valor 404310: los mismos que el hacha grande (`grip_stances.txt`).
  - La postura a una mano (`a40_41_0010`, switch `0x402`, casi todo en la mano) se exporta como clip, pero no se
    mezcla con la locomoción, como con las demás armas a una mano.
