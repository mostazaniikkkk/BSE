# Ballestas: agarre, apuntado, disparo con recarga y virote

Arma de referencia: **Light Crossbow 4600000** (WeaponParam; categoría 4600, WeaponTypeParam 380, modelo `WP_4600_M`).
Munición de referencia: **Wood Bolt 60910000** (ArrowParam).

Usa el mismo código que el arco: [bow.md](bow.md). Aquí solo se documenta lo propio de la ballesta, que sale de los
mismos lectores, más los datos. Herramientas y convenciones como en bow.md. Modelo extraído en
`D:\Reversing\DS2\extracted\models\model__parts__weapon__WP_4600_M\`.

## 1. Botones, agarre y slots

| Slots (WeaponActionCategoryParam 4600) | WAMP | TAE | Tipo de movimiento |
|---|---|---|---|
| 0–4, 6 (una mano, derecha); 20–26, 30–36 (agarres 4 / 5); 40–44, 46 (una mano, izquierda) | 20007900 | 42210010 | **10** |
| 10–14, 16 (dos manos) | 20007300 | 42230010 | **10** |
| 50 / 51 (botones de la otra mano) | 20007400 | 20010010 (relleno) | **16** |
| 52–54 (powerstance) | 20008100 | 42250010 | **11** |
| 8 / 9, 18 / 19, 28 / 29, 38 / 39, 48 / 49 | 20006900 / 20007000 | 20010250 / 20010240 (puño, caída / salto) | 7 |
| 55 / 56 | 20007200 / 20007800 | 20010230 | 2 (patada) |

- El tipo 10 (y el 11) entra en el caso de disparo `0x1403942eb` de `FUN_140393c00`, igual que el 8 del arco (tabla de
  salto `0x14039475c`: 8..11 → disparo). El tipo 16 solo pide la cámara de apuntado (bow.md §1.5 y §2).
- **La ballesta sí dispara a una mano**, con cualquiera de las dos manos. El arco no puede: sus slots de una mano son
  de tipo 15.
- WTP 380 `+0x35 = 3`: `FUN_140396540` trata el fuerte como el ligero (2 → 1, 4 → 3), como en el arco.
  - R2 dispara igual que R1, pero toma el segundo hueco de virotes (§4.3).
- **Botones según el agarre**:
  - **Una mano, ballesta en la derecha**: R1 / R2 disparan (sin la cámara de apuntado). L1 / L2 son del arma de la
    izquierda, que usa su propia fila. No hay forma de apuntar.
  - **Una mano, ballesta en la izquierda**: L1 / L2 disparan (slots 40.., animaciones `_L` propias); R1 / R2 son del
    arma derecha.
  - **A dos manos**: los botones de su mano disparan (slots 10..). Los de la otra mano dan los tipos 0x0b / 0x0c
    (slots 50 / 51, tipo 16) y **solo apuntan** mientras se mantienen, como con el arco.
  - **Powerstance** (dos ballestas, slots 52–54, tipo 11): animaciones `a42_25` (2Weapon). Solo se documenta.

## 2. Modo de apuntado
- Fila de `CameraSubjectiveParam` = WTP `+0x134` = **30**. Es **idéntica byte a byte a la fila 10 del arco**
  (comparado en la regulation):
  - FOV 25°; ojo 1.5 m arriba y 0.4 m adelante;
  - pitch −45°..+60°; giro 3 → 5 rad/s;
  - el mismo ratón, la misma entrada y la misma salida (bow.md §2).
- Solo se llega a dos manos (slots 50 / 51).

## 3. Disparo y recarga

### 3.1 Red (`c0001.nmb`, SM `0x150f` bajo `BT_MoveAttack`)
- Entradas por `AttackID_Number` (condición 0x273 sobre un rango):
  - 210010..210019 → una mano;
  - 230010..230019 → dos manos;
  - 250010..250019 → dos armas.
- Pseudoestados `[0]` / `[2]` → estados 4 / 5 / 6, con clips `_R`. `[1]` / `[3]` → estados 7 / 8 / 9:
  - el 8 (una mano) reproduce los clips propios `a42_22_*_L`;
  - el 7 y el 9 (dos manos / dos armas) reproducen los `_R` bajo nodos espejo 0x87 (`0x15ed`, `0x15f8`, `0x162a`, …).
  - Por eso **`[0]` / `[2]` es la derecha y `[1]` / `[3]` la izquierda**; se lee de los clips, no del código.
- Dentro (p. ej. `0x1515`, una mano derecha) la estructura es la del arco (bow.md §3.2):
  - Prepare → espera, condición 0x262 sobre `AttackDuration` con 0.2: empieza al 80 % de la Prepare (evento 0–1; en
    `a42_22_0010`, 0.32 s de 0.4 s), mezcla 0.2 s no lineal (animations.md, "Condición 0x262");
  - espera → CF, condición 0x270 sobre `IsMoveAttackHold` con valor 1;
  - CF → salida, condición 0x25a.
- **Diferencia en la espera**: el nodo SingleFrame (0x6d `0x1552`) usa el CP **`SingleFrame_095`** (0x17), y el del
  arco `SingleFrame_1` (0x19). La pose de espera es la Prepare al **95 %** (0.38 s). La fracción sale del nombre del CP;
  su valor no se leyó. **(sin confirmar)**
- Cada clip cuelga de un switch 0x83 sobre `AttackID_Number` con dos hijos: `…_0010` / `…_0020`, y `…_0011` /
  `…_0021`.
  - Las variantes `_01` son de la **Sanctum Repeating Crossbow** 4680000: su fila WAMP usa 42210011 / 42230011 /
    42250011, y su CF `_01` tiene tres TAE 2300 (0.1333, 0.3, 0.4667).
  - La Light Crossbow (y la Heavy, la Shield y la Sanctum) usan `…_0010`.

### 3.2 Animaciones y tiempos (Light Crossbow)
| | Prepare (0.4 s) | CF (bala + recarga) | NoBullet |
|---|---|---|---|
| Una mano, derecha | `a42_21_0010` (42210010) | `a42_21_0020` (42210020), 1.8333 s | `a42_21_5030` (42215030), 2.0 s |
| Una mano, izquierda | `a42_22_0010` (42220010) | `a42_22_0020` (42220020), 1.8333 s | `a42_22_5030` (42225030), 2.0 s |
| Dos manos (izquierda espejada) | `a42_23_0010` (42230010) | `a42_23_0020` (42230020), 1.8333 s | ¿42215030? **(sin confirmar)** |
| Dos armas (izquierda espejada) | `a42_25_0010` (42250010) | `a42_25_0020` (42250020), 2.6667 s, virotes en los slots 0 y 5 | ídem |

- **Virote**: TAE 2300, slot 0, a **0.1333 s del CF**, en todas las variantes.
- **Prepare**:
  - `ShootStateControl` 0–0.4: el coste se cobra **al empezar** (en el arco, a 0.3667 s);
  - `CancelControl_Attack*` y `CancelControl_Guard` 0–0.4;
  - `GuardDuration` / `GuardMotionDuration` (canal 500) 0.2–0.4;
  - sin regen de −0.1 a 0.5;
  - sin `CancelControl_Move` ni `CancelControl_Avoid`.
  - La Prepare de dos manos tiene además un **TAE 2300 en el slot 1 a 0.1333 s**. Solo lanza si ese slot tiene bala
    (casting.md §2), y qué slot llena el disparo (`desc+0x1c`) **no está confirmado**.
- **CF** (fin de cada bloqueo; inicio 0):

| | Ataques | Esquiva | Guardia | Sin regen | `ShootStateControl` | Otras |
|---|---|---|---|---|---|---|
| Una mano der. | 1.2333 | 1.3333 | 1.3667 | 0–1.3333 | 0–0.2708 | `OverwriteControl` (u171) 0.5–1.2857, `AttackSpeedControl` 0.6–0.833, `ChangeStance` 1.4– |
| Una mano izq. | 1.3333 | 1.3333 | 1.4 | 0–1.3333 | 0–0.2708 | `OverwriteControl` 0.5–1.2857 |
| Dos manos | 1.3333 | 1.3333 | 1.3333 | 0–1.3333 | 0–0.2708 | |
| Dos armas | 2.0 | 2.0 | 1.6667 | 0–2.1667 | 0–0.3939 | |

- **Recarga**: en `c0001` no hay ninguna animación ni estado de recarga aparte.
  - El CF sigue 1.7 s después del virote, con el arma bloqueada hasta 1.23–1.33 s; el del arco dura 1.333 s y desbloquea
    a 0.333 s.
  - Que ese tramo sea la recarga visual es una lectura de la animación (más el `OverwriteControl` de una mano): **sin
    confirmar**.
  - No hay estado "cargado / descargado" en el código leído: cada disparo es Prepare → CF completo.
- **Movimiento**: ni la Prepare ni el CF tienen `CancelControl_Move`, así que se anda (como con el arco).
- La esquiva queda bloqueada durante casi todo el CF (1.333 s, frente a 0.667 s del arco).
- Mantener el botón: misma lógica que el arco (bow.md §3.1–3.2). El ramal "mantenido" no empieza otro disparo.
- `GuardDuration` y las animaciones de guardia de ballesta (`a42_31/32/34_*Guard*_Hit*`): la Light Crossbow no tiene
  ningún slot de guardia (tipo 13). Su uso: **sin confirmar**.

### 3.3 Estamina (`FUN_14038fb10`, stamina.md)
- `WeaponParam +0x38` = 410:
  - una mano der. ×0.95 = **389.5**;
  - una mano izq. ×1.026 = **420.66**;
  - dos manos ×1.187 = **486.67**.
  - Con el remapeo, R2 cuesta lo mismo que R1.
- **Posible doble cobro**: la Prepare y el CF tienen `ShootStateControl`, y `FUN_140368e70` caso 0x6b cobra
  `b8+0x1ac` al entrar sin ponerlo a 0 (`cast_368e70.c`, l. 31–38). En el arco solo la Prepare lo tiene. **(sin
  confirmar)**

## 4. El virote

### 4.1 Bala por agarre (`FUN_140392ce0`)
- Grupo de ArrowParam según `desc+0x04`:
  - 2 y 6 (una mano) → `+0x04` = **60910000**;
  - 3 (dos manos) → `+0x14` = **60910020**;
  - 4 → `+0x24` = 60910040;
  - 5 → `+0x34` = 60910060.
  - Ramas `0x140392d2b` (2 / 6), `0x140392de4` (3), `0x140392ddb` (4), `0x140392dd2` (5).
- Bala = grupo +0, daño = grupo +8, como en el arco (orden **sin confirmar**).
- Para el Wood Bolt las cuatro filas de BulletParam existen. Las flechas solo tienen la `…20`: el arco nunca dispara a
  una mano, así que esto cuadra con el código.

### 4.2 BulletParam 60910000 / 60910020 (iguales en todo lo exportado)
| Campo | Valor |
|---|---|
| vida | **1.4 s** |
| v0 / vmáx | **30 / 50** |
| aceleración | −10 desde 1.0 s |
| gravedad | 1.0 × 1.8 desde 0.7 s |
| giro al fijado | ±30° / 2°, sin adelanto |
| homing | 0.1745 rad/s, 45°, para a 2 m; solo con objetivo |
| `+0xb0..+0xb3` | 0, 0, 0, 1 |
| origen | dummy 200 del arma (PDP `+0x30 = 1`, `+0x31 = 200`), desplazamiento (0, 0, −0.5) |

- **Alcance** con multiplicadores 1.0: 30 m en el primer segundo + 11.2 m en los 0.4 s siguientes = **≈ 41.2 m**
  (arco: ≈ 58.75 m).
- Multiplicadores de los tipos 10–11: `b8+0x450` (velocidad), `+0x454` (retardo de gravedad) y `+0x44c` (vida)
  (casting.md §7.1). Valen 1.0 por el constructor `FUN_14031bcf0` (casting.md §8).

### 4.3 Daño y munición
- **PDP 60910000 / 60910020**:
  - **daño plano `+0x0c` = 200** (elemento `+0x19` = 0, físico); las flechas tienen 0;
  - `+0x18` = 34, tasa `+0x1c` = 0.392, daño a la estamina 270;
  - cápsula de radio 0.05 m y largo 1.0 m; solo hostiles (`0xf2`).
  - 60910060 (agarre 5) tiene tasa 0.247.
  - Las filas `…0010` / `…0030` solo cambian el byte `+0x5f`.
- AR de la ballesta: físico 125 → 250; requisitos Fue 13 / Des 4; **sin escalado por Fue ni Des** (coeficientes
  WeaponStatsAffectParam 0 / 0).
- Cómo se suman el AR, el plano del PDP y ArrowParam `+0x46` (70 en el Wood Bolt): **sin confirmar** (bow.md §4.6).
- **Munición** (`FUN_14034b7f0`): tipo 3 → huecos de virotes 2 / 3 del equipo, y se exige ArrowParam `+0x56 = 4`
  (Wood Bolt: 4).
  - R1 (códigos 1 / 3) → primer hueco; R2 (2 / 4) → segundo hueco.

## 5. Modelo
`WP_4600_M.flv`: huesos `Crossbow_Grip`, `move_poit`, `move_poit01`, `Crossbow_R/L_Limb01/02`,
`Crossbow_NockingPoint`.
- **Dummy 200** (origen del virote): en `Crossbow_Grip`, (0, 0.1147, −0.3826) m, adelante (0, 0, −1).
- **Dummy 201** (encajado): en `Crossbow_NockingPoint`, en (0, 0.1148, −0.2663) m de `Crossbow_Grip` por la cadena
  de huesos.
- Dummies 1 (agarre), 2, 100 y 110.
- `move_poit` / `move_poit01` hacen pensar en piezas móviles (¿la cuerda en la recarga?). El modelo trae `.acb`, sin
  animación propia extraída. **(sin confirmar)**
- Carcaj o aljaba para los virotes: igual que en el arco, no hay ningún dato.

## 6. Diferencias con el arco
1. **Dispara a una mano**, con cualquier mano (tipo 10 en los slots de una mano). El arco no hace nada a una mano.
2. Clips de **una mano izquierda propios** (`a42_22_*_L`). El arco y la ballesta a dos manos usan el espejo. Los del
   arco son `_L` y los de la ballesta `_R`.
3. **Prepare de 0.4 s** (arco 0.833 s); espera a la fracción 0.95 (arco: último fotograma); estamina al empezar la
   Prepare (arco: a 0.3667 s).
4. **CF de 1.833 s con la recarga dentro**: ataques bloqueados hasta 1.23–1.33 s y esquiva hasta 1.333 s (arco:
   1.333 s, 0.333 s y 0.667 s). Posible segundo cobro de estamina en el CF.
5. **Virote más lento y corto**: v0 30, vida 1.4 s, ≈ 41 m (flecha: 40, 1.5 s, ≈ 59 m). **Daño plano 200** en el
   PDP. Sin escalado de atributos.
6. **Bala distinta por agarre** (una mano 60910000, dos manos 60910020).
7. Munición: huecos de virotes 2 / 3 y tipo 4; multiplicadores `b8+0x450 / +0x454 / +0x44c`.
8. Igual que el arco: la cámara de apuntado (la fila 30 = la 10), el remapeo de R2, apuntar solo a dos manos con la otra
   mano, el giro al fijado y el homing, y el disparo de cadera (con los factores de mira WTP `+0xac..` = 1.1, 1.4, 1.0,
   1.0).

## Sin confirmar
- La condición 0x270 (heredado del arco), y el valor de `SingleFrame_095`. (0x262: resuelta, arriba.)
- El slot 1 del TAE 2300 en la Prepare de dos manos, y qué slot llena el disparo.
- El posible doble cobro de estamina por `ShootStateControl` en el CF.
- Que el tramo del CF tras el virote sea la recarga (no hay estado ni animación de recarga aparte), y las piezas
  móviles del modelo.
- Qué NoBullet usan las dos manos y las dos armas.
- El uso de `GuardDuration` y de las animaciones de guardia de ballesta.
- El orden bala / daño de ArrowParam, y la suma de AR + plano del PDP + ArrowParam `+0x46`.
- Cuándo se descuenta el virote.
- La composición de la dummy 201 (rotaciones de los huesos ignoradas).

## Exportado (`tools/re/weapons.py`, `cast_origins.py`, `ds2_anims.py`)
- **`weapons.light_crossbow`** (4600000): mismos campos que `short_bow`.
  - Slots `right`, `left`, `two_handed` y `power_stance` con `motionType` 10 / 11 y `bowAction: "shoot"`;
    `two_handed_other` con 16, `"aim"`.
  - Costes por slot (R2 en el juego = el ligero, §3.3).
  - `dummyPolys` / `dummyPolysLeft` (200 y 201 en `R_Weapon` / `L_Weapon`) y `hitDummies`.
- **`crossbowAnimations`**: 42210010/20, 42215030, 42220010/20, 42225030, 42230010/20 y 42250010/20, con los campos de
  `castAnimations`. `bulletBones` en el lado como está hecha (`leftAuthored`) y `bulletBonesMirror` en el otro.
- **`crossbow`**: la forma de `bow` (`weapon`, `aimCamera` con la fila 30, `arrow`, `lockOn`, `hipFire`), con estos
  cambios:
  - `arrow.bullets`: las cuatro balas por agarre.
  - `arrow.bulletByGrip{"2","3","4","5","6": {bullet, damage}}`; `arrow.bullet` = la de una mano.
  - `arrow.damageRow.flat` (200).
  - `arrow.multipliers.fields` (los offsets `b8` de los tipos 10–11).
  - `staminaCost{oneHandRight, oneHandLeft, twoHand, maybeChargedTwice: true}` (**sin confirmar** el doble cobro).
  - `animations{mirrorSuffix "_mirror", holdSuffix "_hold", holdFraction 0.95 (sin confirmar), sequenceConfirmed false,
    stances{oneHand, twoHand, twoWeapon}, oneHandRight / oneHandLeft / twoHand / twoWeapon: {prepare, cf, noBullet,
    leftMirrored}}`. El NoBullet de dos manos y dos armas está **sin confirmar**.
  - `reload{<agarre>: {boltAt, cfDuration, attackLock, avoidLock, guardLock, regenBlock}, separateAnimation: false,
    confirmed: false}`.
- **Animaciones**:
  - `ds2_<tae>` y `ds2_<tae>_mirror` para cada clip de `crossbowAnimations` y para las posturas 42410010, 42430010 y
    42450010 (en bucle);
  - `ds2_<prepare>_hold` y `_mirror_hold`, a la fracción 0.95;
  - `ds2_<loco>_2h_424310` y `_l`: la postura a dos manos sobre la locomoción, con los pesos ya existentes de la
    ballesta (switch `0x3d5`, valor 424310).
