# Armas: cómo decide DS2 qué ataque hacer

**Corrige `attack.md`.** El moveset **no** depende del grupo de animación (`a29` = espada recta, etc.): lo define
**cada arma** en la regulation, y un arma mezcla animaciones de varios grupos.

## Cadena de params
1. **`WeaponParam`** (índice de param `0x11`, slot `+0x420`). Fila de 0xbc bytes, id = id del objeto
   (nombre en `ItemName.fmg`, por ejemplo 1230000 = Broadsword).
   - `+0x0c`: id de `WeaponActionCategoryParam`.
   - `+0x10`: id de `WeaponTypeParam`.

   Leídos en `FUN_140395b40` mediante los getters `140359150` (WeaponParam) y `140359210` (WeaponTypeParam).
2. **`WeaponActionCategoryParam`** (`0x12`). Fila = **64 ids de `WeaponAttackMotionParam`**, uno por *slot de acción*.
3. **`WeaponAttackMotionParam`** (`0x13`). Fila de 0x50 bytes:
   - `+0x00`: id de TAE / animación (por ejemplo 21010010 = `KnifeAttack1HandLight1_R_Hit`).
   - `+0x04`: tipo (2 = normal, 7 = salto / caída, 13 = guardia).
   - `+0x08..+0x18`: cinco floats por ataque (ej. 1.1, 1.2, 1.0, 1.0, 0.2). **Significado sin confirmar en el código.**
   - `+0x28`: float 20.0.
   - `+0x2c`: su propio id.

## Del botón al slot (`FUN_140396680` → `FUN_140395b40` → `FUN_140397514`, ofuscado; desofuscado a mano)
`FUN_140396680` devuelve un **tipo de acción** según el botón y el estado:

| Tipo | Condición (función) |
|---|---|
| 1 | ligero por defecto |
| 2 | ligero mientras el ataque actual es de tipo 1, de la misma mano, dentro del combo (`FUN_1401f9d50(…,1)`: `chr.b8+0x4d4 ≠ 0`, `+0x188` = mano, `+0x18a` = tipo actual) |
| 3 / 4 | fuerte; **4 si el actual es 3, 3 si el actual es 4** (`FUN_1401f9d50(…,3/4)`) |
| 5 | ataque corriendo (`FUN_1401f9d90`: `chr.b8+8 == 4`) |
| 6 | ataque tras rodar (`FUN_1401f9f40`: flags `chr.c0+0x2f / +0x3d`) |
| 7 | fuerte con `FUN_1403961c0` |
| 8 | cayendo (`FUN_1401f9ea0`) |
| 9 | salto (`FUN_140396350`) |
| 10 | flag `chr.c0+0x30` (`FUN_1401f9f70`) |
| 0x0b–0x19 | especiales (guardia a dos manos, parry, powerstance, patada, riposte, backstab…) |

**El combo ligero es L1 → L2 → L1 → L2…, y el fuerte H1 → H2 → H1…**; la animación de cada paso la da el param del arma.

**Modo de agarre** (`FUN_140397a80`, según el estado de la mano `+0x10`):
- 2 o 6: una mano (6 si `param_2 == 0`);
- 3: dos manos;
- 4: dual;
- 5: estados 5/6;
- 1: sin arma.

**Slot** = base(agarre) + desplazamiento(tipo):
- **Base**: agarre 2 → 0, 3 → 10, 4 → 20, 5 → 30, 6 → 40. Tablas de salto en `0x140397894` / `86c` / `844` / `81c` / `7f4`.
- **Desplazamiento** para los tipos 1..10: `[0, 1, 2, 3, 4, 5, 7, 8, 9, 6]`.
- **Tipos 0x0b..0x19** → slots 50..63, sin importar el agarre (tabla en `0x1403977b8`).
- **Agarre 1** → fila 1 (puño).

Ejemplo, Dagger (categoría 1000), una mano:

| Slot | Ataque |
|---|---|
| 0 | `KnifeAttack1HandLight1` |
| 1 | `KnifeAttack1HandLight2` |
| 2 | `KnifeAttack1HandHeavy1` |
| 3 | `StraightSwordAttack1HandHeavy1_R_Hit_01` (de otro grupo) |
| 4 | Dash |
| 5 | `StraightSwordAttack1HandRolling` |

## Implementado (`tools/re/weapons.py` → `data/soulsmode/ds2/weapons.json`, `ds2/Weapons`, `ds2/Moveset`)
- **Objetos de Minecraft → arma de DS2** (elección del usuario):

  | Objeto | Arma de DS2 | Id |
  |---|---|---|
  | Espadas | Longsword | 1220000 |
  | Hachas | Battle Axe | 2010000 |
  | Picos | Pickaxe | 2610000 |
  | Mazo (mace) | Mace | 2410000 |
  | Palo | Club | 2400000 (×0.5 al ataque, ver abajo) |
  | Azadas | Silverblack Sickle (Hoz platinegra) | 2100000 |
  | Tridente | Spear | 2800000 |
  | Mano vacía y otros | Fists | 3400000 |

  Otras armas, por API (`SoulsWeapons`).
- **Reglas del usuario (2026-11-09), no DS2**:
  - El palo es su propio tier: pega como el Club con el ataque ×0.5 (`SoulsUpgrade.materialFactor`, también en la página
    de stats).
  - Las azadas usan la Hoz platinegra (`docs/research/scythes.md`: tipo 151, movimientos de hacha). Pesan lo de su
    receta mientras el usuario no decida si toman el peso DS2 (4).
  - Las herramientas con moveset siguen cumpliendo su función (`SoulsTools`): con el hacha, click derecho sobre un
    tronco / cobre lo descorteza, raspa o desencera; con la azada, ara la tierra, y el click izquierdo cosecha lo que
    se rompe al instante (cultivos, flores, pasto).
- **Slots por mano**: derecha a una mano = slots 0..3, izquierda = 40..43 (ligero, ligero de combo, fuerte, fuerte de
  combo). Que el 40 sea la mano izquierda lo respaldan los datos: en Fists (3400) y en la Scimitar (1600), el slot 42
  es el parry.
- **Combo** como `FUN_140396680`: L → L2 dentro de `AttackComboControl` con la misma mano; H → H2 → H.
- **Animaciones**: `ds2_<taeId>.json`, sacadas de los `.nsa` (`tools/re/ds2_anims.py`). La mano izquierda es el espejo.
- **Sin implementar**:
  - Ataque en caída (tipo 8).
  - Los multiplicadores de `WeaponAttackMotionParam`.

## Escudo y agarre a dos manos
- **Drangleic Shield** (11230000, categoría 11230), elegido para el escudo de Minecraft.
  - **Con una mano** (cualquier mano), los slots 0–3 y 40–43 son iguales:
    - ligero = `MiddleShieldGuard1Hand_L`: `WeaponAttackMotionParam` tipo 13, una guardia que se mantiene;
    - fuerte = `MiddleShieldAttack1HandParry_R`.
  - **A dos manos** (slots 10–13): guardia y parry a dos manos.
  - **Con los botones de la otra mano** (slots 50/51): `MiddleShieldAttack2HandLight2` / `Heavy1`, los golpes con el escudo.
  - El uso ofensivo es, por tanto, agarrarlo a dos manos.
- **Agarre** (`FUN_140396680`): con el arma de una mano a dos manos (estado 2/3):
  - los botones de esa mano usan el agarre 3 (slots 10–13);
  - los de la otra mano dan los tipos 0x0b/0x0c y usan el arma a dos manos (`FUN_140395b40` cambia de mano), slots 50/51.
- **Implementado**:
  - tecla `G` alterna dos manos en el arma derecha; con `G` agachado, en la izquierda;
  - la guardia dura mientras se mantenga el botón;
  - la animación de la guardia se reproduce en bucle.
- **Pendiente**:
  - **Qué hace la guardia con el daño.**
    - Pista: `FUN_140136570` clasifica el impacto. Con categoría 2 (impacto en guardia) y estamina ≤ 0 pasa a
      impacto normal con resultado 10, la rotura de guardia.
    - Probablemente `WeaponReinforceParam +0x50..` son los porcentajes de reducción (Drangleic: 100/70/55/55/35), pero
      **no está confirmado en el código; no se aplica**.
  - Ventana de parry y riposte.
- **Ventana de parry, encontrada** (`ChrMorphemeTimeActTrackParryCtrl`, `FUN_14016cd70`):
  - El evento TAE 1220 (0x4c4) sube `chr.b8+0x550` (flag 0x20), pero solo cuando la fracción transcurrida del evento es
    ≥ 1 − `WeaponParam +0x74` del arma de esa mano (Drangleic 0.68, Wooden 0.765, Longsword 0.45).
  - Drangleic, `a45_01_0290`: el evento 1220 va de 0.167 a 0.4 s, así que el parry está activo de 0.242 a 0.4 s.
  - El evento 1230 (0x4ce) sube `chr.b8+0x518` (sin identificar).
  - Qué produce el parry: ver "Parry (efecto)". (La pista anterior sobre `FUN_1401345c0` era errónea: ese bloque es un
    SpEffect de "sobrevivir con 1 PV".)

## Orientación del arma en la mano (`tools/re/ds2_anims.py`)
- **Origen**: los huesos `R_Weapon` / `L_Weapon` de DS2 pasan a los huesos `right_item` / `left_item` de la librería de
  animaciones. Por eso el escudo sigue al hueso del arma en vez de quedar fijo al final del brazo.
- **Cálculo**: rotación del objeto = (marco del brazo · marco vanilla del objeto)⁻¹ · hueso del arma · K⁻¹.
  - El marco vanilla del objeto lo pone `ItemInHandLayer`: X −90, Y 180.
  - La librería aplica el resultado como Y(−rotY) · Z(−rotZ) · X(−rotX).
- **Calibración**: K hace que la pose quieta de DS2 (a00_00_0000, t = 0) coincida con la pose vanilla del objeto en la
  mano. Es una calibración, no un dato.
- **Reflejo**: una animación se refleja cuando el arma está en la mano contraria a aquella para la que se hizo (el nombre
  de origen termina en `_L` o `_R`). DS2 usa la misma guardia `MiddleShieldGuard1Hand_L` en las dos manos.

## Antorcha (implementada, sin luz)
- **Tiempo de antorcha** (objeto `TorchTime`, ctor `FUN_1401bb9d0`, en `PlayerItemData+0x28`; se guarda con los objetos):
  - Recoger una Torch (ItemTypeParam `+0x18` = 11) no guarda un objeto: `FUN_1401bba90(torchTime, cantidad)`, una vez
    por antorcha. Desensamblado verificado: si el tiempo es ≤ 0 lo **fija** en `PlayerCommonParam` fila 0 u16 `+0xc8`
    (300); si no, le **suma** u16 `+0xca` (300).
  - Límite (`FUN_1401bbd70`): ±(u32 `+0xcc` = 359999 − FLT_EPSILON).
  - Solo se gasta encendida (`FUN_1401bbc90` → `FUN_1401bbcd0`: `time -= dt`).
- **Encender**: `a00_18_0220 ItemUseTorchMake` (TAE 180220, 2.167 s):
  - TAE 210099 a 0.267 s: `FUN_14034f300(chr, 0)` pone el arma 6000000 en la mano 0 (izquierda) como reemplazo temporal.
  - TAE 210100 a 0.667 s: nivel 1, encendida (`FUN_14016c9f0`).
  - nmb: `CancelControl_Avoid` y `CancelControl_AttackAll` / `_Guard` hasta 1.5 s. **No tiene `CancelControl_Move`**: no
    bloquea caminar.
- **Se apaga** (`FUN_140141d90`, escribe `b8+0x6dc`) y la antorcha apagada sale de la mano (`FUN_140141890`):
  - sin tiempo;
  - mojarse: `FUN_1401476b0` (ChrWetParam) la apaga cuando llega un evento de agua (`b8+0x750`) con nivel de mojado
    > 0.5 (`DAT_1410ac694`). La fórmula del nivel no está sacada;
  - cambiar el arma de esa mano (`FUN_14036b9d0`);
  - suelos de GroundMaterialParam `+0x11` (1 siempre, 2 al rodar). Los nombres de los materiales no se conocen.
- **Con la antorcha**: arma 6000000 → WeaponActionCategoryParam 6000. Izquierda ligero = `a20_32_0010 FistGuard1Hand_L`
  (guardia), fuerte = `a35_01_0010` / `a35_01_0020` (ataques de hacha). Guardia física 0.25 (WeaponReinforceParam).
- **Efectos no portados**: luz (PlayerLight fila 17, radio 16), revelar personajes a 27 m (`FUN_1401e6eb0`), daño y fuego
  del arma 6000000 (sin trazar).
- **Implementado** (`SoulsTorch`, tecla `V`):
  - Las antorchas de Minecraft siguen siendo objetos que se pueden colocar (elección del usuario, 2026-10-30; antes
    todas se convertían en tiempo al entrar al inventario). Si no queda tiempo, `V` gasta una antorcha del inventario
    y da el tiempo de DS2 para una antorcha recogida sin tiempo (`FUN_1401bba90`, `PlayerCommonParam +0xc8` = 300 s).
    Sin tiempo ni antorchas, `V` no hace nada.
  - `V` reproduce `a00_18_0220`; a 0.267 s la antorcha reemplaza el objeto de la mano izquierda (se dibuja una
    antorcha) y a 0.667 s queda encendida. Si la animación se corta antes, la antorcha sale de la mano.
  - Mientras está encendida descuenta 1/20 s por tick; se apaga sin tiempo, al tocar agua o al cambiar el objeto de
    la mano izquierda. `V` con la antorcha en la mano cuenta como ese cambio de arma.
  - Los ataques de mano izquierda usan la categoría 6000; el daño es el vanilla de una antorcha (como puño).
- **Decisiones y supuestos (no salen de DS2)**:
  - "Tocar agua" (`isInWater`) reemplaza el nivel de mojado > 0.5, cuya fórmula falta.
  - La compuerta para usar un objeto no está decodificada: se usan los bloqueos de ataque de la acción en curso.
  - Usarla con 0 de tiempo: el código la apaga en el primer tick tras encenderse; no está confirmado si el menú lo
    impide.
  - El mensaje con el tiempo restante es interfaz, no de DS2.

## Pendiente
- Significado de los floats `+0x08..+0x18` de `WeaponAttackMotionParam`. **No son multiplicadores de daño**: `+0x08` →
  `b8+0x1f8` y `+0x0c` → `b8+0x1fc` (`FUN_1403947b0`, `FUN_140393350`; en ataques normales escalados por las consultas
  de SpEffect 7 y 8), y nada de la cadena de daño los lee. Su consumidor no se encontró.
- Qué mano es `param_2 == 0` (izquierda o derecha): define si el agarre 6 es la mano izquierda.
- Cada objeto de Minecraft tiene que corresponder a un arma concreta de DS2 (una fila de `WeaponParam`). **Esa
  correspondencia es una decisión del usuario.**

Herramientas: `tools/re/params.py`, `tools/re/slots.py <categoría>`, `tools/re/disasm.py`, `tools/re/trace.py`.

## Parry (efecto)
- **Corrección**: `thunk_FUN_141ca218b(b8, i)` **no** lee el contador `b8+0x4d0+i·4`. Prueba el bit `i` del bitset<62>
  en `b8+0x4b8` (desensamblado en `141b9e48c`: `bt [b8+0x4b8+(i>>6)*8], i&63`; `i ≥ 0x3e` → "invalid bitset<N>
  position"). Esos bits los ponen los SpEffects (`FUN_140219b50`, `FUN_14021d070`).
  - Por eso el bloque de `FUN_1401345c0` / `FUN_140134eb0` con el bit `0x20` **no es el parry**: si el golpe deja la
    vida ≤ 0 (`vt+0x1b0` = `FUN_140314440`, vida `chr+0x168 > 0`) y `DamageCtrlParam+0x13 == 0`, `FUN_14016a560` fija
    la vida en 1 (acotada a `+0x16c..+0x170`) y marca `b8+0x4c8` bit 56. Es un "sobrevivir con 1 PV" de SpEffect.
- **Estado "parreando"** = contador `b8+0x550 ≠ 0` (evento TAE 1220). Lo lee `ActionCtrl vt+0x198` (`FUN_14030e1d0`).
- **Clasificación del golpe** (`FUN_140138510`, `ChrDamageActionCtrl vt+0x98`): si el que recibe está parreando, pasa a
  `FUN_140139f40` (`vt+0xb0`). Si pasa → **categoría 5**. Si no pasa → categoría 1 (golpe normal; la guardia **no** se
  comprueba mientras el flag de parry está activo).
  - Solo se comprueba si el byte 0 de `vt+0x38` (`FUN_14013ae90` / jugador `FUN_1401f7a20`) es 1. Es una marca de
    autoridad de red (`chr+0xe8+0x28`); su valor fuera de línea no está verificado en ejecución.
- **Condiciones de `FUN_140139f40`**:
  - `DamageCtrlParam +0x03` del ataque ∉ {1, 3}. En la regulation: 0 → 1569 filas, 1 → 1156, 3 → 501, 2 → 2.
  - Tipo de parry del arma del que parrea, byte `+0x52` de la fila que da `thunk_FUN_14014cbeb` (mano según
    `b8+0x188`); en no jugadores, `ChrParam +0x6b`.
    - 0 = no parrea; 1 = cualquier ataque; 2 = solo si el atacante **no** es de la clase `FUN_140448050`;
      3 = solo si lo es.
    - La clase `FUN_140448050` parece ser la de proyectiles (`FUN_14013a3f0` compara su `+0x2c` con 33200000), pero
      **no está confirmado**.
    - **Sin confirmar**: que la fila sea `WeaponTypeParam`. El getter está ofuscado; `WeaponParam +0x52` vale 0 en las
      473 filas, y `WeaponTypeParam +0x52` vale 2 en 136 filas, 0 en 4 (460, 465, 466: grandes escudos), 1 en 3
      (420, 440, 580) y 3 en 1 (461).
  - Ángulo (`FUN_140144b00`, con el `CommonParam` del que parrea, `chr+0x40`):
    - ángulo horizontal entre el frente y el vector del golpe ≤ `+0x9c`;
    - componente vertical ≤ `+0x98` (aproximación polinómica, constantes en `0x1410bfc00`).
    - Jugador (`ChrCommonParam`): 90° y 90°. Enemigos: 70° y 70° (189 de 225 filas; 36 filas tienen 0 = no parrean).
- **Daño al que parrea**: **ninguno**. `vt+0xc8` (`FUN_140139b40`) devuelve falso para las categorías 0, 3 y 5
  (máscara `0x29`), y sin eso no se llama a `vt+0xf0` (aplicar daño). Tampoco hay reacción: `vt+0x140`
  (`FUN_14013b0a0`) no trata la categoría 5.
- **Tras el parry** (`vt+0x150` = `FUN_14013b390`): `ChrParryActionCtrl` (`dmgCtrl+0x18`, ctor `FUN_140144a70`) guarda
  `+0x20` = atacante (`hit+0x58`) y `FUN_140144cc0` pone `+0x10 = 1`, `+0x14 = 0`.
  - El evento TAE 1230 (`b8+0x518`) usa ese objetivo: `FUN_140387f40` devuelve la velocidad de giro
    `CommonParam+0x1a0` (jugador 18.85 rad/s = 1080°/s) para girar hacia el atacante.
- **Reacción del atacante: sin confirmar.** No se encontró el código que pone al atacante en `DmgParryBreak`.
  - `ChrParryActionCtrl` no tiene update propio (`vt+0x18` = `FUN_140144cd0`, vacío).
  - Pista: `b8+0xfc` = tipo de reacción. Lo escribe `FUN_14013b4d0` (`14013b9ba`) y se pone a 0 cuando
    `b8+0x7e6 == 0` (`FUN_140135dd0`).
    - El tipo sale de `DamageCtrlParam +0x10`, remapeado en `FUN_140138a40`.
    - 10 = rotura de guardia: `FUN_140136570`, y PDP 1000 / 6010 / 7010.
    - 13 = agarre / crítico: categoría 4 en `FUN_140138510`, y PDP 400–1440 (`WeaponParam +0x50..+0x64`).
    - 9: PDP 2400 / 2420 / 2440 (`WeaponParam +0x68..+0x70`, DC 99933130) y `SystemDamageParam` 210270020.
    - 11: PDP 5010.
    - 12: ninguna fila; solo por código.
  - Tipo 12: `FUN_140135b70` suma dt en `dmgCtrl+0x59c`. Al llegar a `CommonParam+0x17c` (jugador 1.0 s, enemigos
    2.0 s), activa `b8+0x4c0` bit 14 y `b8+0x4c8` bit 23. Es el único temporizador de reacción encontrado, pero **no
    está confirmado** que corresponda a `DmgParryBreak_Loop`.
- **Ventanas en la víctima** (`c0001.nmb`):
  - `a00_16_1010` Start: 1.667 s (TAE 101100 de 0 a 1.867 s). `StabControl` (262 → `b8+0x500`, estocada tras romper
    guardia) de 0 a 0.667 s; 261 (`b8+0x4fc`, riposte) de 0.667 a 1.667 s.
  - `a00_16_1020` Loop: 1.333 s, con 261 todo el tiempo.
  - `a00_16_1030` End: sin 261 ni 262.
  - El bucle termina con los mensajes 22 / 23 `SitUp_*` (ver «Riposte y estocada tras romper guardia»).
- **Segunda búsqueda de la reacción del atacante** (salida en `D:\Reversing\DS2\out\pa_*`). **Sigue sin confirmar.**
  - El resultado del golpe (`dmgCtrl vt+0x28` / `vt+0x30`: `[0]` categoría, `[1]` daño, `+0x17` reacción) lo consumen
    `ActionCtrl vt+0x130` (jugador `FUN_14037cfb0`, enemigo `FUN_14039e240`) y `vt+0x138`. Solo mueven al que recibe;
    ninguno toca al atacante.
  - Un escaneo de todo el binario no encontró ninguna comparación con 5 del resultado tras esas llamadas virtuales.
    `ChrParryActionCtrl+0x20` (el atacante) solo lo leen `FUN_140387f40` y `FUN_140385fe0` (giro).
  - `b8+0xfc` solo lo escriben:
    - `FUN_14013b0a0`, para las categorías 1 y 4;
    - `FUN_14013bc10`, réplica de red (`FUN_1401fd520`, paquete `FUN_1401fe0b0`).
  - No se encontró el despachador del golpe cuerpo a cuerpo que llama a `vt+0x130` desde el lado del atacante. Lo que
    se sabe de él:
    - `ChrAttackDamageCtrl` está en `chr+0x230` (ctor `FUN_140373560`);
    - sus árboles de llamadas solo construyen volúmenes de golpe;
    - el callback de colisión está en otro sitio (física u ofuscado).
  - Reacción 9: solo `DamageCtrlParam` 99933130 (`+0x03 = 3`, no se puede parrear; lo usan PDP 2400/2420/2440 =
    `WeaponParam +0x68..+0x70`) y 200640010 (`SystemDamageParam` 210270020).
  - De reacción a animación: `FUN_14013b4d0` escribe `b8+0x708 = FUN_140138930(reacción)`. Luego `FUN_14035e820`
    (`ChrDamageMotionCtrl` update) pone `ControlParameters|DmgCategory` y envía el mensaje `DamageFullBody`.
    Correspondencia 9→140, 10→150, 11→160, 12→170, 13→180.
  - Final del tipo 12: al aplicarse, `dmgCtrl+0x59c = 0` y se limpia el bit 87 de `b8+0x4c0` (`FUN_1401265d0`).
    `FUN_140135b70` acumula dt hasta `CommonParam+0x17c` (jugador 1.0 s, enemigos 2.0 s) y entonces activa el bit 14
    (y el 87, que lo bloquea).
    - El único lector del bit 14 es `FUN_14035e820`, que envía el mensaje Morpheme del slot 6 de `FUN_14035f1c0`:
      **"SitDown"** (entrada 27 de la tabla de mensajes de `c0001.nmb`).
    - **Sin confirmar**: que el tipo 12 sea el estado de parreado, y que la transición `a00_16_1020` → `1030` se
      dispare con ese mensaje (falta leer las condiciones de transición de la NetworkDef).
  - Siguientes pasos: leer las condiciones de transición de Morpheme (`CondOnMessage` 27) alrededor de `a00_16_1020`, y
    encontrar el callback de contacto que llama a `vt+0x130`.

- **Implementado** (`SoulsAttack.parries`):
  - ventanas TAE 1220 por animación y `WeaponParam +0x74` por arma (`tools/re/weapons.py`);
  - un golpe cuerpo a cuerpo dentro de esa ventana y de los ángulos del jugador (90° / 90°) no hace daño ni reacción;
  - **supuestos**: solo cuerpo a cuerpo (se asume que el tipo 2 excluye proyectiles; sin confirmar). Los ataques de
    Minecraft no tienen `DamageCtrlParam +0x03`, así que todos se pueden parrear;
  - **no implementado**: el efecto en el atacante (aturdimiento) y, por eso, la riposte.

## Críticos: backstab (implementado), riposte y estocada tras romper guardia (solo investigados)
- **Decisión de cada frame** (`FUN_140393a30`):
  1. `FUN_140391370` busca víctima y tipo de crítico;
  2. `FUN_140390670` es la compuerta; entre otras condiciones exige que no esté activo el flag de combo `b8+0x4d4`;
  3. `FUN_1403947b0` inicia el crítico.
- **Tipos**: backstab = tipo 1, acción 0x18, slot 63. Guard-break = 2 / 0x17 / slot 62. Riposte = 3 / 0x16 / slot 61.
  - Se buscan con el agarre 2, la mano derecha.
  - El slot debe tener tipo de movimiento ≠ 0. El Drangleic Shield no tiene backstab.
- **Prueba del backstab** (`FUN_140390a50`), con los valores del `CommonParam` de la víctima:
  - distancia horizontal ≤ `+0x10c`;
  - |dy| ≤ `+0x108`;
  - |dy| / |d| ≤ |sin(`+0x104`)|;
  - el atacante mira a la víctima: ángulo ≤ π/4;
  - la víctima da la espalda: |ángulo − π| ≤ `+0x100` + `WeaponTypeParam +0x4c`;
  - clase de la víctima `+0x00` entre 1 y 3.
  - Jugadores: `ChrCommonParam` 100, con 45°, 45°, 1.0 y 1.5 m.
  - Mobs de Minecraft: los valores más comunes de `EnemyCommonParam` (81 de 225 filas): 30°, 32°, 0.5 y 2.0 m.
    Que los mobs usen esa fila es una decisión.
- **Animación del backstab**: no tiene pistas `CancelControl_Attack1L/1H/2L/2H` ni `AttackComboControl`, pero **sí**
  `CancelControl_Avoid` / `Move` / `AttackAll` (ver «Lado de la víctima»). Hoy el port la trata como comprometida
  entera (`weapons.py`, rama `critical`).
  - El daño llega en cada evento TAE 2201 (`0x899`).
  - Durante el TAE 110100 el atacante es invulnerable (`FUN_140139b60`).
- **Implementado**:
  - un ataque ligero con la derecha y una mano, sin otro ataque en curso, se convierte en backstab si hay una víctima en
    rango;
  - la víctima queda retenida en su sitio y no puede hacer daño mientras dura la animación (el agarre);
  - cada evento 2201 le hace el daño normal del arma.
- **Lado de la víctima** (scripts y salidas en `D:\Reversing\DS2\out\backstab\`):
  - **Elección de la animación**: no la da ningún param. La da Morpheme con 3 control params que escribe
    `ChrGrabMotionCtrl` (`FUN_1403607c0`) desde `b8+0x284` / `b8+0x288`, junto con el mensaje `CatchDamage` (id 33):
    - `CatchDmgCategory` (cp 0x7c) = `b8+0x284 / 10000`; `CatchDmgModelID` (0x7d) = `b8+0x284 % 10000`;
    - `CatchDmgHitCategory` (0x7e) = `(float) b8+0x288`.
  - Los escribe el atacante al iniciar el crítico (`FUN_1403947b0`, tramo ofuscado `141ca9dc7..dd2`):
    - `b8+0x284` = categoría · 10000 + `[chr+0x2c]`. Categoría por tipo de crítico (`ctx+0x48`): 1 backstab → 10,
      2 rotura de guardia → 30, 3 riposte → 20, 4 → 40.
    - `b8+0x288` = `(chr.c0+0x20 / 1000000) % 100`. `chr.c0+0x20` lo pone `FUN_1403929a0` (`[[req+0x60]]`) justo antes.
      Es el prefijo `aXX` de la animación del atacante (29 = StraightSword, 36 = LargeAxe…). **Que `c0+0x20` sea el id
      de animación sale del formato** (los umbrales del switch son esos prefijos), no de leer quién lo carga.
  - El gestor de agarres (`FUN_14015b340`, lista en `mgr+0xd70`) copia `0x284` / `0x288` del atacante a la víctima
    (`FUN_14032c7d0`) al pasar de la fase 2 a la 3.
  - Red `c0001.nmb` (`out\grip\grip_sm.py 0x1eb0`): la SM `0x1eb0` entra por el nodo `0x1ec2` y elige por
    `CatchDmgCategory` (condición 0x271): 10 → switch `0x1ebb`, 20 → `a79_03_0010_CatchParryBreakSlash`,
    30 → `a79_04_0010_CatchGuardBreakSlash`.
  - Switch `0x1ebb` sobre `CatchDmgHitCategory` (atributo 0x9: umbrales 21, 25, 29, 32, 36):
    21 Knife → `a79_02_0020_CatchBackStabThrust`, 25 Spear → `…0050_HardThrust`, 29 StraightSword → `…0010_Slash`,
    32 LargestSword → `…0030_WideSlash`, 36 LargeAxe → `…0040_StrikeDown`.
    - En `c0001` solo hay 6 backstabs de atacante: a20, a21, a25, a29, a32, a36. **El puño (20) no tiene umbral**: cae por
      debajo del primero, así que sería `Thrust` con cualquier modo de selección. El modo del switch no está decodificado.
  - **Secuencia (víctima jugador)**, tiempos desde el inicio de su `a79`:
    - `a79_02_*`: toda con `CancelControl_Avoid` y `DamageDuration`; sin pistas Move/Attack. Sale al 0.9 de su
      duración (condición 0x25b sobre `0x1ebb`, valor 0.9; mezcla 0.2 s) hacia `SM_CatchDamagePC` (`0x1eb1`).
    - `SM_CatchDamagePC` empieza en el estado 1 = `a00_16_0720_DmgDownFaceDownLoop` (3.0 s, pista `DownDuration` →
      `b8+0x299`). Sale a `a00_16_0730_DmgDownFaceDownEnd` con el mensaje 22 `SitUp_FaceUp` o 23 `SitUp_FaceDown`
      (mezcla 0.001 s).
    - El mensaje lo pide el control de derribo (`FUN_140133700`): bit 40 de `b8+0x4c0` cuando
      tiempo en el suelo ≥ `+0x14`, sin agarre activo (`b8+0x298 == 0`). Al soltar el agarre, `FUN_14032e000` pone
      `+0x14 = 0` (`FUN_1401336e0(…, 0.0)`, `XORPS` en `14032e157`). Así que el bucle dura un frame o dos.
    - `DmgDownFaceDownEnd` (2.0 s): Avoid bloqueado 0–0.833, Move 0–1.133, AttackAll 0–1.5; TAE 110100 0–1.833.
    - Al salir del suelo (`b8+0x299` 1 → 0): 3.0 s con `b8+0x618 += 1` (`FUN_140133700`, constante `0x40400000`). La
      búsqueda de víctima de críticos salta candidatos con `+0x618 ≠ 0` (`141b9e50a`). No se le puede volver a hacer un
      crítico en 3 s. (La TAE 111400 usa el mismo contador: el atacante tiene 0–4.167 en su backstab.)

| `a79` | Dur. | Sale | Rueda | Se mueve | Ataca | Fin | TAE 110100 |
|---|---|---|---|---|---|---|---|
| `0010` Slash | 4.0 | 3.6 | 4.433 | 4.733 | 5.1 | 5.6 | 2.0–3.6 y 3.6–5.433 |
| `0040` StrikeDown | 4.0 | 3.6 | 4.433 | 4.733 | 5.1 | 5.6 | 2.333–… y 3.6–5.433 |
| `0050` HardThrust | 4.0 | 3.6 | 4.433 | 4.733 | 5.1 | 5.6 | 2.333–… y 3.6–5.433 |
| `0030` WideSlash | 4.0 | 3.6 | 4.433 | 4.733 | 5.1 | 5.6 | 2.333–… y 3.6–5.433 |
| `0020` Thrust | 4.667 | 4.2 | 5.033 | 5.333 | 5.7 | 6.2 | 2.667–… y 4.2–6.033 |

  - Precisión: ±0.2 s. No está confirmado si `DownDuration` y el mensaje se procesan durante la mezcla de 0.2 s.
  - **Lado del atacante** (cancelaciones de su `aXX_01_0300`, 4.0 s): queda libre (rodar / moverse / atacar) al acabar
    `CancelControl_Avoid` / `Move` / `AttackAll`. `AttackMasterBind` (userData 170 → `b8+0x28d`, `FUN_140368e70`)
    marca el agarre: al terminar, `FUN_14032e000` suelta los bloqueos de la víctima y el gestor cierra el agarre
    (entrada `+0x11` → `+0x12`).

| Arma (TAE) | Víctima | Fin del agarre | Atacante libre | i-frames atacante | Ventaja (rodar / atacar) |
|---|---|---|---|---|---|
| Longsword (29010300) | Slash | 1.867 | 2.967 | 2.333–3.667 | 1.467 / 2.133 |
| Battle Axe, Mace, Club, Pickaxe (36010300) | StrikeDown | 1.7 | 3.167 | 2.333–3.667 | 1.267 / 1.933 |
| Spear (25010300) | HardThrust | 1.2 | 3.5 | 2.333–3.667 | 0.933 / 1.6 |
| Fists (20010300) | Thrust (sin umbral) | 2.167 | 2.833 | 2.333–3.667 | 2.2 / 2.867 |

  - La ventaja supone que la `a79` empieza a la vez que la animación del atacante.
  - **Desfase atacante → víctima: sin constante.** La animación del atacante empieza en `FUN_1403947b0`.
    `CatchDamage` sale cuando el gestor pasa de la fase 2 a la 3, con las dos partes alineadas (`grabCtrl+0x140`).
    - Fase 1: coloca las posiciones de golpe (`FUN_14032d2e0`).
    - Fase 2: giro con velocidad (`FUN_14032e330` / `FUN_14032dde0`).
    - El retardo depende del giro. Límites: 10 s en fases < 3 y 30 s en la 3 (`DAT_1410ad5e8`, `DAT_1410bdc88`).
  - **Durante el agarre** (`FUN_14032c7d0`), la víctima tiene `+0x55c` (Avoid), `+0x5bc`, `+0x598`, `+0x5f0` y `+0x5d4`
    `+= 1`. No toca `+0x608`, así que solo es inmune (`FUN_140139b60`) dentro de los 110100 de la tabla.
    - `+0x5f0` lo lee `FUN_1403486f3` → `141b0de00`, junto con `+0x5f8` / `+0x5fc`; el significado está sin confirmar.
    - Antes del 110100 (0–2.0 s en Slash), terceros pueden golpearla según estos datos. Sin probar en el juego.
  - **Enemigos como víctima**: los mismos control params van a la red del enemigo. Las animaciones y tiempos están en
    su propio anibnd / TAE, que no está extraído (solo hay `c0001` y `c000100_pl.tae`).
    - Ningún campo de `EnemyCommonParam` / `GrabParam` da una duración genérica. `CommonParam +0x304` (escala del
      tiempo en el suelo) vale 1.0 en todas las filas, pero tras un agarre ese tiempo es 0 de todos modos.
    - **No hay número genérico para mobs.** Usar la secuencia de `c0001` (víctima humanoide) sería una decisión del
      usuario.
- **Sin confirmar o pendiente**:
  - qué fila de `GrabParam` usa el backstab. Las filas 10100 / 10300 / 10500 (y 2xxxx / 3xxxx) tienen el patrón de
    tipo de crítico · 10000, pero no se trazó quién elige el id. La entrada del gestor guarda el id en `+0x08`, y
    `FUN_14032cd30` lo resuelve con `thunk_FUN_141b6b500`;
  - el significado de los campos de `GrabParam` (`+0x30` decide qué parte recibe `+0x628` y `FUN_14036dac0`; el resto
    sin decodificar);
  - la semántica exacta de las condiciones Morpheme 0x25b (fracción de duración) y 0x271 (rango de un control param
    entero), sacada de los valores;
  - el modo de selección del switch `0x1ebb`, que afecta solo al puño;
  - la sincronización de posiciones de `GrabParam`;
  - los estados de víctima que no existen en Minecraft;
  - la riposte y la estocada tras romper guardia, que necesitan que el parry y la rotura de guardia produzcan sus estados
    en la víctima.

## Daño por golpe: multiplicador de `PlayerDamageParam` (implementado)
- **Slots de ataque**: al empezar un ataque, `WeaponAttackMotionParam +0x2c/+0x30/+0x34` dan los ids de
  `PlayerDamageParam` (PDP) de los slots 0..2; 0 = slot vacío.
  - Ataques normales: `FUN_140392b20` construye cada golpe con `FUN_140391fe0` y lo guarda en `chr+0x230`.
  - Críticos: `FUN_1403947b0` (acciones 0x18 / 0x17 / 0x16) hace lo mismo con `FUN_14032ce30`.
  - El golpe se construye **una vez**, al empezar la animación.
- **Eventos TAE**: el `param[0]` de cada evento es el slot que golpea.
  - 2200 (`FUN_140326e80`): `FUN_1403741c0` copia el slot (`chr+0x230 + 0xf08 + slot·0xc0`). El golpe solo empieza si su
    id PDP ≠ 0.
  - 2201 (`FUN_1403269e0` → `b8+0x290`; `FUN_14015c2d0`): cada evento aplica el golpe **entero**; no se reparte entre
    eventos. El backstab del Longsword tiene dos 2201, así que golpea dos veces.
  - 2203 (`b8+0x294` = id PDP): un golpe solo con el daño plano del PDP (`FUN_140170710`, `+0x0c..+0x14` en los tipos
    `+0x19..+0x1b`). Backstab del Longsword: PDP 490, 15 de daño plano a 0.333 s. **No portado** (daño plano en
    unidades de DS2).
- **Fórmula de la víctima** (`FUN_140138d50`, `ChrDamageActionCtrl vt+0x100`), por tipo de daño t:
  `d_t = abs_t · max(atk_t − def_t, piso) / D_t · PDP+0x1c · …`; el total se suma y luego se añade el daño plano.
  - **`PDP +0x1c`** es el único multiplicador propio del golpe. Se aplica después de restar la defensa.
  - Longsword: ligero 1 = 1.0 (PDP 10031800), ligero 2 = 0.925, fuerte = 1.2, backstab = 1.4 (21013000), riposte = 1.54
    (23013000), estocada tras romper guardia = 1.54 (22013000). Parry del Drangleic Shield = 0.
  - `X` = `PDP+0x20 · WeaponTypeParam+0x6c` (1.1 en Longsword) cuando la víctima tiene `b8+0x630 ≠ 0` o
    `c0+0xf4 & 0x20` (¿contraataque?). **Sin confirmar**; no portado.
- **Implementado** (`tools/re/weapons.py` `damageRates` + slot por evento; `Attack.damageRate`; `SoulsAttack.damage`):
  - el daño vanilla del arma (atributo + encantamientos) se multiplica por `PDP+0x1c` del slot del evento;
  - se aplica antes de la armadura de Minecraft. La armadura escala casi proporcionalmente, así que el resultado es
    parecido a aplicarlo después;
  - el resto de la fórmula de DS2 (AR, escalado, defensas, absorciones) no está portado.
  - **Supuesto**: un evento cuyo slot está vacío deja el multiplicador en 1, porque no está confirmado si los slots se
    vacían entre ataques. Afecta a la lanza: el segundo golpe del combo ligero, y la extensión del fuerte combo.

## Agarre a dos manos: cambio y postura
Scripts y salidas: `D:\Reversing\DS2\out\grip\grip_*` (nodos Morpheme: `grip_nodes.py`, `grip_sm.py`, `grip_stances.py` →
`grip_stances.txt`; pistas: `grip_events.py`).

### Estado de las manos
- `handCtrl+0x10` (`chr vt+0x120 → vt+0x70`, el que lee `FUN_140397a80`): 0 sin arma, 1 una mano, **2 arma izquierda a
  dos manos**, **3 arma derecha a dos manos**, 4 dual, 5 / 6 powerstance (izquierda / derecha).
  - 2 = izquierda y 3 = derecha: `FUN_14035db00` toma la fila izquierda en el estado 2 y la derecha en el 3, y el
    código 7 (abajo) pone 2 al guardar el arma derecha.
- **Mano 0 = izquierda, mano 1 = derecha.** `thunk_FUN_141b16348(chr, mano)` (ofuscado, trazado): 0 → `b8+0x80 = 0` y
  borra el bit `0x20` de `b8+0x4c0`; 1 → `b8+0x84 = 0` y borra el `0x40`. Resuelve el pendiente "qué mano es
  `param_2 == 0`": la izquierda.
- `thunk_FUN_14014cbeb(handCtrl, -1 / -2)` devuelve la fila de **`WeaponTypeParam`** del arma derecha / izquierda.
  Confirmado por los datos: los ids de postura solo existen en `WeaponTypeParam`, y `+0x24` coincide con las animaciones
  de guardado. Esto también confirma que el tipo de parry `+0x52` es de `WeaponTypeParam`.

### La petición (`FUN_140395330`, llamada desde `FUN_14038f200`)
- `FUN_14038f200` solo la llama si `handCtrl vt+0xa8` es verdadero y existen las dos filas.
- Las peticiones son bits de `(chr+0xe8)+0x18`, byte `+5` (`thunk_FUN_141b10376`).
- **Petición 2, dos manos con el arma derecha** (solo en los estados 1 y 4):
  - cancela lo pendiente de la derecha;
  - si el arma izquierda tiene `WTP+0x24 ≠ 0`: `b8+0x80 = 8` y bit `0x20` (cambio pendiente de la mano izquierda), y
    nada más;
  - si no, y las dos manos están libres (`FUN_14034d9b0`), `FUN_14034f530(chr, 1)` pone el estado 3 en el acto.
- **Petición 1, dos manos con el arma izquierda**:
  - en el estado 1: cancela lo pendiente de la izquierda; si la derecha tiene `+0x24 ≠ 0`, `b8+0x84 = 7` y bit `0x40`;
    si no, `FUN_14034f530(chr, 0)`, que da el estado 2, o 5 / 6 si `FUN_14034d930` acepta la powerstance;
  - en el estado 4: `FUN_14034f530(chr, 0)` directo.
- **Petición 3, volver a una mano**:
  - estado 3: si la izquierda tiene `+0x24 ≠ 0`, `b8+0x80 = 10`;
  - estado 2: si la derecha lo tiene, `b8+0x84 = 10`;
  - si no, `FUN_14034f470` (estado 1, o 4 si `FUN_14034ede0` acepta la pareja); en los estados 5 / 6, directo.
- En la regulation **todas** las filas de `WeaponTypeParam` tienen `+0x24` = 10, 20 o 30. En la práctica el cambio
  siempre pasa por la animación.
- **Sin sacar**: qué botón (tocar o mantener Y) da cada petición; está en la capa de entrada.

### Animación del cambio
- `FUN_14035cf40` (ChrAttackMotionCtrl): el bit `0x20` envía el mensaje Morpheme `ChangeStance_L` (id 14 en la tabla de
  mensajes de `c0001.nmb`) y el `0x40`, `ChangeStance_R` (13).
- `SM_ChangeWeapon_L` (nodo 0x4ca; nombre en `FUN_140397f80`, índice 13) entra con el mensaje 14 (fundido 0.1 s) y elige
  por `WeaponStorage_L` (CP 0x36 = `WTP+0x24` del arma izquierda, `FUN_14035e090`):

  | `+0x24` | Animación | TAE |
  |---|---|---|
  | 10 | `a00_08_0010 WeaponChangeToBackTop` | 80010 |
  | 20 | `a00_08_0020 WeaponChangeToBackBottom` | 80020 |
  | 30 | `a00_08_0030 WeaponChangeToHipFar` | 80030 |

- `SM_ChangeWeapon_R` (0x4e0) es igual, con el mensaje 13, `WeaponStorage_R` (CP 0x35) y las mismas animaciones
  **espejadas** (nodos tipo 0x87).
- Duran 0.5 s y se reproducen a `WeaponChangeSpeed` (CP 0xcf). Su valor sale de `chr+0x398 vt+0x210` = PSPL[AGL] `+0x74`
  (1.20 → 1.45 con la Agilidad; agility.md §3, portado 2026-11-03 en `SoulsGrip` / `GripClient`).
- **Son capa del torso**: FeatherBlend2 (nodo 0x72) sobre `BT_Move`, peso `BlendWeight_1` (1.0) × alfa por hueso. Lo que
  no se lista vale 0 (piernas, pelvis, raíz, dedos 0–2):
  - versión izquierda (sin espejo): Spine 0.8, Spine1 0.75, Neck / Head 1, brazo izquierdo 1 (L_Clavicle..L_Hand,
    L_Finger3x / 4x, L_Weapon), R_Clavicle / R_UpperArm 0.2, R_Forearm 0.4;
  - versión derecha (espejo): la simétrica, salvo Spine1, que vale 1.
- **Se puede andar mientras cambia**: la capa no toca las piernas y las pistas no tienen `CancelControl_Move`, `_Avoid`,
  `_Attack*` ni `_Guard`.
- Pistas de la versión sin espejo (`c0001.nmb`). La espejada cambia los userData con la tabla de espejo de `0xb71cc`
  (pares 10↔11 = Leftsteps / Rightsteps, 9810↔9811, 52↔53, 75↔76, 58↔59, 62↔63):

  | Pista (userData) | ToBackTop / Bottom | ToHipFar | Efecto |
  |---|---|---|---|
  | WeaponChangeStart_L (58) | 0 | 0 | `c0+0xf0 \|= 1` (lo usa la red) |
  | CancelControl_Itemuse (43) | 0–0.333 | 0–0.367 | bloquea usar objetos |
  | WeaponChangeDuration (52) | 0–0.267 | 0–0.367 | contador `c0+0x90` |
  | UsingArm (75) | 0–0.333 | 0–0.367 | contador `c0+0x84` |
  | InputControl (37) | 0.067–0.5 | igual | flag 0x02 |
  | **UpperBody (9810)** | **0.233–0.5** | igual | **aplica el cambio** |
  | WeaponChangeCtrl (62) | 0–0.5 | igual | sin efecto en `FUN_14036a6e0`; aparece en la salida del SM |

- Los TAE 80010 / 80020 / 80030 no tienen eventos (tampoco de estamina).
- **Cuándo cambia el agarre** (`ChrEventTrackActionUpperBody`, `FUN_14036b930`): al empezar el evento 9810 se llama a
  `FUN_14036bb70(mano 0, b8+0x80)`; con el 9811, a la mano 1 con `b8+0x84`. Es decir, a los **0.233 s** (7 frames).
  Códigos:
  - 7 → estado 2; 8 → estado 3 (`FUN_14036bcd0`); 10 → `FUN_14034f470` (una mano);
  - 3–5 → `FUN_14036b9d0` (cambio al slot de arma `código − 3`);
  - 1, 2, 6 y 9 son otros cambios (de arma, antorcha, ocultar).
- **Qué animación sale en cada caso**:
  - dos manos con la derecha: se guarda el arma izquierda, con `ChangeStance_L` y la animación sin espejo de su `+0x24`;
  - dos manos con la izquierda: `ChangeStance_R`, animación espejada del `+0x24` del arma derecha;
  - volver a una mano: la misma animación del arma guardada. No hay animación propia de "sacar". **Sin confirmar** si se
    reproduce al revés; no se encontró velocidad negativa.
- Salida del SM: fundido de 0.2 s, condición tipo 0x262 con canal 80, userData 62 (`WeaponChangeCtrl`) y 0.1: sale
  cuando al evento `WeaponChangeCtrl` le queda ≤ 10 % (animations.md, "Condición 0x262").
- **Estamina**: ningún acceso en `FUN_140395330` ni en `FUN_14036bb70`, y no hay eventos TAE. No hay coste (búsqueda no
  exhaustiva).
- **Refutadas como cambio de agarre**:
  - `a00_00_0010 SwitchStanceToIdle_L`: está en `SM_Idle` y la dispara `IsSwitchStance` (CP 0x93 = `b8+0x10f`,
    `FUN_14035d9e0`); es el cambio de pie en reposo;
  - `a00_08_0100 WeaponChangeHide`: SM 0x626 de la rama de ataque, condicionado por `IsAttackChangeHide` (CP 0xab).
- **Cuándo se rechaza, solo lo que está en el código**:
  - estados: la petición 2 solo en 1 / 4, la 1 en 1 / 4, la 3 en 2 / 3 / 5 / 6;
  - `FUN_14034d9b0`, solo en el camino sin animación: la mano 0 está libre si `c0+0x90`, `c0+0x84` y `b8+0x59c` valen 0
    (la mano 1: `c0+0x94`, `c0+0x88`, `b8+0x5a0`); además exige `c0+0x8c == 0` y condiciones de `c0+0x2a` / `c0+0x24`.
  - **Sin sacar**: si atacar, rodar o estar en el aire impiden la petición. El SM de cambio está dentro de `BT_Move`; si
    el mensaje llega con Morpheme en otro estado, no habría evento 9810 ni cambio (deducción, sin confirmar).
- **El arma guardada**: el nombre de la animación dice dónde va (espalda arriba, espalda abajo, cadera). **No se encontró**
  el punto del modelo donde se dibuja.

### Postura
- `FUN_14035db00` (ChrAttackMotionCtrl, desde `FUN_14035dfe0`) llena los CPs con la `WeaponTypeParam` de cada mano.
  Formato del id: `GGSSNN` → `aGG_SS_00NN` (204110 = `a20_41_0010`).

  | Campo WTP | CP | Cuándo |
  |---|---|---|
  | `+0x0c` | AttackStance_LR | estado 3 (arma derecha) |
  | `+0x10` | AttackStance_LR | estado 2 (arma izquierda) |
  | `+0x14` | AttackStance_R | siempre; 204110 si estado 2 o `handCtrl+0xe8b == 0` |
  | `+0x18` | AttackStance_L | siempre; 204110 si estado 3 o `+0xe8a == 0` |
  | `+0x1c` | AttackStance_R_2Weapon | estado 6 |
  | `+0x20` | AttackStance_L_2Weapon | estado 5 |
  | `+0x24` (u8) | WeaponStorage_R / _L | `FUN_14035e090` |

  - `TwoHandStance` = +1 en el estado 3, −1 en el 2 y 0 en el resto. `TwoWeaponStance` = +2 en el 6 y −2 en el 5
    (`DAT_1410ac698` 1.0, `6a4` −1.0, `acb14` 2.0, `ada3c` −2.0). `IsTwoHand` = `TwoHandStance ≠ 0`.
  - Si `+0xe8a` o `+0xe8b` vale 0, todo se trata como una mano. Significado de esos bytes sin sacar.
- **SM de postura** 0x3bc (en `BT_Move`), por `TwoHandStance` (condiciones sobre el CP 0x3c en [v, v]):
  - 0 → 0x400 (una mano), +1 → 0x3d6 (dos manos), −1 → 0x3fb (dos manos, espejo);
  - fundido de 0.2 s entre estados (atributo 0x3b `+0x08`).
- **Dos manos**: interruptor (nodo 0x83) sobre `AttackStance_LR` → FeatherBlend2(locomoción 0x500, postura).
  - Peso `BlendWeight_Stance` (CP 0xb6) = 1.0 (`FUN_14035cf40`, `DAT_1410ac698`).
  - Vale 0 si `c0+0x28` tiene el bit 1 y no cumple `(c0+0x28 & 0xa) == 0xa`. Significado sin sacar.
- **Una mano: sí hay capa de postura.** FeatherBlend2 0x401 (peso 1) junta dos cadenas:
  - la derecha, con un interruptor sobre `AttackStance_R`;
  - la izquierda, sobre `AttackStance_L`; de ella salen Spine, Spine1 y todo el brazo izquierdo;
  - cada cadena es FeatherBlend2(locomoción, postura) con `BlendWeight_1` = 1.0;
  - la izquierda usa la postura `_R` espejada (nodo 0x87), salvo LargeAxe, que tiene `a36_42_*_L`.
- Las posturas son **poses de un frame** (`.nsa` de 0.0333 s).
- El tipo 0x72 = FeatherBlend2 sale de sus alfas por hueso (atributo 0x57, 59 = huesos del rig) y de que el exe tiene
  `TaskFeatherBlend2*`. **Sin confirmar** si interpola o suma.
- **Interruptor**: los valores del atributo 0x09 van en el mismo orden que los hijos. Los valores de las armas del mod
  coinciden exactamente en la rama sin espejo. En la espejada, la espada recta figura como 294311, no 294310. Los datos
  solo encajan con "el valor más cercano"; **sin confirmar** en el código.

| Arma | WTP | Dos manos (`+0x0c` = `+0x10`) | Una mano (`+0x14` = `+0x18`) | Izquierda | Guardado (`+0x24`) |
|---|---|---|---|---|---|
| Longsword 1220000 | 141 | 294310 `a29_43_0010 StraightSwordStance2Hand_R` | 214110 `a21_41_0010 KnifeStance1Hand_R` | espejo | 30 HipFar |
| Battle Axe 2010000 | 271 | 214310 `a21_43_0010 KnifeStance2Hand_R` | 214110 | espejo | 10 BackTop |
| Mace 2410000, Club 2400000 | 204 | 214310 | 214110 | espejo | 30 HipFar |
| Spear 2800000 | 100 | 254310 `a25_43_0010 SpearStance2Hand_R` | 254110 `a25_41_0010` | espejo | 20 BackBottom |
| Pickaxe 2610000 | 241 | 364310 `a36_43_0010 LargeAxeStance2Hand_R` | 364110 `a36_41_0010` | `a36_42_0010 …1Hand_L` | 10 BackTop |
| Drangleic Shield 11230000 | 430 | 454310 `a45_43_0010 MiddleShieldStance2Hand_R` | 454110 `a45_41_0010` | espejo | 20 BackBottom |
| Fists 3400000 | 10 | 204310 `a20_43_0010 FistStance2Hand_R` | 204110 `a20_41_0010` | espejo | 20 BackBottom |

- **Alfas por hueso** (lo no listado = 0; "dedos" = los 15 de esa mano):
  - Dos manos:
    - Fist, Knife, Spear: Spine / Spine1 / Neck / Head 0.2; clavículas 0.75; UpperArm 0.8; Forearm, Hand, dedos y
      Weapon 1 (los dos lados);
    - StraightSword: Spine 0.2; Spine1 / Neck / Head 0.5; clavículas 0.8; el resto de los brazos 1;
    - LargeAxe: Spine 0.2; Spine1 / Neck / Head 0.3; brazos enteros 1;
    - MiddleShield: Spine 0.2; Spine1 / Neck / Head 0.3; clavículas 0.75; UpperArm 0.8; resto 1.
  - Una mano, derecha:
    - Fist: R_Hand, dedos y R_Weapon 1;
    - Knife: además Spine / Spine1 / Neck / Head 0.1;
    - Spear: 0.1 en la columna, R_UpperArm / R_Forearm 0.2, R_Clavicle 0.5;
    - LargeAxe: 0.1 en la columna, R_Clavicle / R_UpperArm 0.7, R_Forearm 0.8;
    - MiddleShield: Spine / R_UpperArm / R_Forearm 0.2, Spine1 0.25, R_Clavicle 0.5.
  - Una mano, izquierda: lo mismo en el lado izquierdo, salvo:
    - Spear: Spine / L_UpperArm / L_Forearm 0.2, Spine1 0.25, L_Clavicle 0.5;
    - MiddleShield: Spine / Spine1 / Neck / Head / L_Forearm 0.1, L_UpperArm 0.2, L_Clavicle 0.3.

### Qué devuelve el agarre a una mano
- Cambiar el arma de cualquier mano (`FUN_14036b9d0`): si el estado no es 1, `FUN_14034f470`.
- Encender la antorcha (`FUN_14034f300`, el reemplazo temporal 6000000): empieza llamando a `FUN_14034f470`.
- La recarga del equipo (`FUN_14037d1f0` / `FUN_14037d380`) **no** lo quita, solo lo revalida: 1 / 4 → `f470`;
  2 / 5 / 6 → `f530(chr, 0)`; el 3 se queda.

### Fuerza ×1.5 a dos manos
- **No encontrada.** El único ×1.5 cerca de esto (`DAT_1410bd0a8`) es el de la powerstance: `FUN_140350170`, estados
  4–6, multiplica el mayor de los u16 `+0x18` / `+0x1a` de las dos filas: los requisitos de Fuerza / Destreza de
  `WeaponParam` (getter `0x140349250`, confirmado). Detalle en [powerstance.md](powerstance.md) §1.2.

### Sin confirmar (resumen)
- Qué botón da cada petición y qué estados (ataque, rodar, aire) la bloquean antes de `FUN_140395330`.
- El valor de `c0+0x28` (`BlendWeight_Stance` = 0). (`WeaponChangeSpeed`: resuelto, agility.md §3.)
- Si "volver a una mano" reproduce la animación al revés.
- La regla del interruptor para valores ausentes (294310 en la rama espejada).
- El modo del FeatherBlend2 (interpolar o sumar).
- Dónde se dibuja el arma guardada.
- El ×1.5 de fuerza a dos manos.

### Implementado (`SoulsGrip`, `client/anim/GripClient`, `tools/re/ds2_anims.py`)
- `weapons.json` lleva por arma `twoHandStance` (`WTP+0x0c`) y `storage` (`WTP+0x24`).
- **Cambio de agarre** (tecla `G`; con `G` agachado, el arma izquierda; `G` a dos manos vuelve a una mano):
  - se reproduce `a00_08_00x0` según el guardado del arma que se guarda (espejado si es la derecha) y el agarre cambia a
    los 0.2333 s; con guardado 0, al instante;
  - se puede andar durante el cambio; `CancelControl_Itemuse` bloquea la antorcha 0.333 s (0.367 s en la cadera);
  - cambiar el objeto de cualquier mano o encender la antorcha vuelve a una mano al instante.
- **Animación del cambio**: capa del torso. Cada miembro de Minecraft toma el alfa del hueso de DS2 del que lo arma el
  retarget (cuerpo = Spine1, cabeza = Head, brazo = UpperArm, objeto = Weapon; la posición del cuerpo = Pelvis, 0). Es
  una simplificación del retarget: los miembros de Minecraft son un solo hueso.
- **Postura a dos manos (arma derecha)**: `ds2_<locomoción>_2h_<postura>.json`, las 12 animaciones de locomoción con la
  pose de postura mezclada por hueso en el esqueleto de DS2 (interpolación; el modo de FeatherBlend2 está sin
  confirmar) y luego retargeteadas.
- **El arma guardada no se dibuja** (no se sabe dónde la dibuja DS2).
- **Decisiones (sin fuente)**: no se puede pedir el cambio durante un ataque o un roll, y un cambio cortado por un
  ataque o un roll antes de los 0.2333 s no ocurre (el SM de cambio está en `BT_Move`); al reaparecer tras morir se
  vuelve a una mano.
- **Sin portar**: la postura del arma izquierda a dos manos (falta espejar la pose en el esqueleto de DS2), la capa de
  postura a una mano (casi toda en mano y dedos, que Minecraft no tiene), y el ×1.5 de fuerza.

## Ataques en movimiento (tipos 5–10)
Salidas y scripts: `D:\Reversing\DS2\out\moveatk\` (`d1.c`..`d9.c`, `slots_dump.txt`, `table.txt`, `tracks.py`, `evs.py`).
"Ligero" / "fuerte" son las dos ramas de `FUN_140396680` (`iVar4 == 1` / `2`).

### Orden de prueba en `FUN_140396680`
- Cada candidato pasa solo si el slot del tipo tiene movimiento: `thunk_FUN_141ca0bab(fila de WeaponActionCategoryParam,
  tipo, agarre)` devuelve el id de `WeaponAttackMotionParam` del slot (el caso 0x10 lee `fila+0xdc` = slot 55, 0x11
  `+0xe0`, 0x12 `+0xe4`, 0x14 `+0xec`), o 1 si no hay arma o el tipo es 0. **Un slot con id 1 = vacío**: se salta y se
  prueba el siguiente.
- **Ligero**: caída (8) → tras rodar (6) → corriendo (5) → tras paso atrás (10) → salto (9, solo si el botón era el
  fuerte remapeado por `FUN_140396540`, que pide `WeaponTypeParam +0x35` en 1..3; vale 0 en todas las armas del mod) →
  patada 0x10/0x11 → combo (2) → … → 1.
- **Fuerte** (con `param_1[7] == 0`; con `≠ 0` solo hay 3↔4 / 0x15 o nada; qué es `param_1[7]` no está identificado):
  caída (8) → salto (9) → tipo 7 → 0x14 → 3 (0x0f con el botón izquierdo en powerstance, estados 5 / 6; ver
  [powerstance.md](powerstance.md)).
  **No hay fuerte corriendo, tras rodar ni tras paso atrás**: dan el fuerte normal.
- En escalera (`c0+0x35` = `SM_Ladder`) solo 1 / 3. Con `b8+0x4c0` bit 8 y la misma mano → 8 directamente.
- Antes, `FUN_140396070` rechaza el botón si su `CancelControl_Attack*` está activo (`b8+0x568..+0x574`), como en los
  demás ataques.

### Nodos de `chr.c0` que faltaban (asm de `FUN_1403982e0`: `R8D` = slot, `R9D` = índice en la lista de `FUN_140397f80`)
| Byte | Nodo |
|---|---|
| `+0x35` | `SM_Ladder` |
| `+0x3c` | `SM_Fall` |
| `+0x3d` | `SM_Fall\|BT_RollLanding` (rodada al aterrizar de una caída) |
| `+0x48..+0x4b` | `BT_Attack1HandJump_R/L_Hit`, `BT_Attack2HandJump_R/L_Hit` |

Bits: 1 existe, 2 activo, 8 = `FUN_1404e3540` (sin significado). Los tipos 6 y 10 exigen activo y no `& 0xa == 0xa`.

### Tipo 5 — ataque corriendo (slot +4, `…Dash_R_Hit`)
- Ligero, `FUN_1401f9d90`: `chr.b8+8 == 4` (modo dash, ver [stamina.md](stamina.md): botón de esquiva mantenido
  ≥ `PlayerCommonParam+0x8` = 0.27 s con el stick fuera del centro; fijado, también 0.15 s tras soltar) **y** la
  velocidad del controlador (`chr+0xe0` vfunc `+0x1c8`) **y** la física (`FUN_140312ad0 +0x30`) ≥
  `PlayerCommonParam+0x84` (4.5 m/s) × `b8+0x11c` (`FUN_14031c7d0(b8, 0)`; valor sin leer).
- El trote llega a 4.295 m/s (`ChrMoveParam+0x20`): solo sale a velocidad de dash.

### Tipo 6 — ataque tras rodar (slot +5, `…Rolling_R_Hit`)
- Ligero, `FUN_1401f9f40`: `SM_Dodge` (`c0+0x2f`) o `BT_RollLanding` (`c0+0x3d`) activos.
- Ventana (pistas de `a00_12_0020`): `CancelControl_Attack1L` bloquea de 0 a 0.5 s; después, mientras siga `SM_Dodge`
  (hasta 1.5333 s; si la red sale antes por `StateControll` no está verificado). El fuerte está bloqueado hasta 0.9 s y
  luego es el fuerte normal.

### Tipo 10 — ataque tras paso atrás (slot +6)
- Ligero, `FUN_1401f9f70`: `c0+0x30` = `SM_Step|BT_Step*` activo. `CancelControl_AttackAll` del paso (`a00_11_0011`)
  bloquea de 0 a 0.3333 s; luego, hasta el fin del paso (1.1667 s).
- **En todas las armas el slot +6 es el mismo movimiento que el +4**: el ataque tras paso atrás es el ataque corriendo.

### Tipo 9 — ataque con salto (slot +9, `…Jump_R_Hit`, tipo de movimiento 7)
- Fuerte con el flag `manipulador+0x175` (`FUN_140396350`). Es el **empujón del stick hacia delante** de
  `FUN_140396e60` (cada frame; valores de `PlayerCommonParam` fila 0):
  1. stick exactamente en 0 durante ≥ `+0x74` = 0.1 s (arma `+0x184`; se desarma si pasa > 0.1 s fuera de 0);
  2. luego, magnitud ≥ `+0x70` = 0.8 habiendo salido de ≤ 0.1 (`DAT_1410ac9f4`) en menos de `+0x6c` = 3 / 30 = 0.1 s
     (historial de 16 muestras);
  3. dirección (stick en espacio de cámara) a ≤ `+0x78` = 45° del frente del personaje (`ChrGameParamCalculator`
     vfunc `+0x18` = `FUN_14031f8d0`: ángulo con signo respecto a `chr` vfunc `+0x240`).
  - Pone `+0x174` (latch hasta que la magnitud baje de 0.8) y `+0x175`, que se borra a los `+0x68` = 0.05 s.
- No es el salto de movimiento (`SM_Jump`): no pide dash.
- **Impulso** (`ChrJumpAttackCtrl`, vtable `0x1410bfb28`, en `chr.e0+0xe0`): la pista Morpheme `AttackStateControl_01`
  (userData 103 = 0x67, `FUN_140368e70`) pone `ctrl+0x20 = 1` a los **0.5333 s** (en los 4 `…_01_0240`). Entonces
  `FUN_140141230` aplica una velocidad (`chr+0xe0` vfunc `+0x120`) calculada en `FUN_140140d40`:
  - `d` = distancia al objetivo fijado (0 sin objetivo). Rama "a nivel" si el objetivo no está más de
    `ChrCommonParam+0x280` (0.5 m) por debajo.
  - Horizontal = `vmin..vmax` interpolado por `d` entre `near..far` (todo × `k`), en la dirección del frente;
    vertical = `vy × (1 − a)`. Factores = `WeaponTypeParam +0x74..+0x9c` (1.0 en todas las armas del mod) ×
    `ChrMoveParam` 100:

    | Rama | vy | near | far | vmin | vmax |
    |---|---|---|---|---|---|
    | a nivel (`+0x248..+0x258`) | 3 | 4 | 6 | 4 | 8 |
    | objetivo abajo (`+0x25c..+0x26c`) | 3 | 3 | 8 | 2 | 8 |

  - `a` y `k = 1 − b` = `PlayerGameParamCalculator` vt+0x30 (`0x1410e4ec8+0x30` = `thunk_FUN_140320130` @ `140381bc0`):
    `FUN_140320230(ratio de carga, fila 1, fila id) × 0.01` (`DAT_1410acb08` = 0.01). La fila se busca por **id de fila**
    (`FUN_140358920` → `FUN_14003e8f0`, búsqueda binaria sobre el id; `ChrEquipLoadParam` en `paramMgr+0x400`).
    - Rama a nivel: ids 0x21 (`a`) / 0x22 (`b`), y `140140d40` multiplica **otra vez** × 0.01 (`140140f69`, `140140f78`).
      Rama objetivo abajo: 0x23 / 0x24, sin el segundo × 0.01.
    - **`ChrEquipLoadParam` filas 33..36 (0x21..0x24) = `[0, 0, 0, 0, 0]`** → `a = b = 0` a cualquier carga: vertical = `vy`,
      `k = 1`. **El salto no depende de la carga de equipo.**
  - `d` = distancia **horizontal** (máscara XZ `0x141137170` = `ff..,0,ff..,0`) al punto del objetivo fijado (su dummy
    `plVar[8]+0x2a4` si existe, `FUN_140312750`). La comprobación "abajo" usa `y_objetivo − y_chr`: objetivo por encima
    o sin objetivo → rama a nivel.
  - `t = 0` si `d ≤ near`, `1` si `d ≥ far`, si no `(d − near)/(far − near)`; horizontal = `vmin + (vmax − vmin)·t`
    (`140140d40`, final). Dirección = `chr` vt+0x240 (frente). Resultado (igual a 0 / 30 / 70 / 100 / 120 % de carga):

    | Objetivo | Horizontal (m/s) | Vertical (m/s) |
    |---|---|---|
    | ninguno | 4.0 | 3.0 |
    | a nivel, d = 4 / 5 / 6 / 8 m | 4.0 / 6.0 / 8.0 / 8.0 | 3.0 |
    | > 0.5 m abajo, d = 3 / 4 / 6 / 8 m | 2.0 / 3.2 / 5.6 / 8.0 | 3.0 |

  - **Aplicación (un solo frame)**: `chr+0xe0` es `PlayerActionCtrl` (vtable `0x1410e4968`; el ctrl lo crea
    `FUN_14030dce0` en `+0xe0`). Su vt+0x120 (`FUN_14037d320`) → `PlayerMovementCtrl` (`+0x148`) vt+0x20
    (`FUN_140335840`): guarda la velocidad en `mov+0x40` y `mov+0x92 = 1`. En el update de movimiento (`FUN_140385fe0`,
    `140386e18..140386e4a`): si `+0x92`, copia `+0x40` → velocidad `mov+0x30` (`FUN_140335860`) y borra la bandera.
    (El salto normal, `ChrJumpCtrl` `FUN_140330510`, usa vt+0x118 → vt+0x18, que escribe `+0x30` directamente.)
  - **Estados del ctrl** (`FUN_140141230`, vt+0x18, cada frame):
    - 0 → al ver `ctrl+0x20`: guarda la posición de despegue (`ctrl+0x10`), `b8+0x61c += 1`, pide la velocidad; pasa a 1
      y en la misma llamada a 2.
    - 2 (frame siguiente): `b8+0x61c −= 1`, `fall+0xd = 1` (`chr.e0+0xb0`; `FUN_140372620` lo toma como "en el aire":
      `fall+8 = 1`, `b8+0x61c += 1`). Pasa a 3.
    - 3: mientras algún `BT_Attack*Jump` (`c0+0x48..+0x4b`) esté activo: si `y < y_despegue − 0.5` → bits de ataque en
      caída y fin; si no, termina al tocar suelo (`FUN_1403144c0` → `FUN_14036da20`, el mismo test de suelo que usa
      `FUN_140372620` para aterrizar). Sin esos nodos → 0.
  - **En el aire** (`b8+0x61c ≠ 0`, `FUN_140385fe0` `140386c3f..`):
    - Tope de velocidad horizontal (solo XZ; `y` se conserva): `mov+0x158` = **8.5 m/s** con `BT_Attack*Jump` activo
      (`+0x160` = 2.0 es para `BT_MoveAttack` sin salto, `+0x15c` = 3.5 rodar/paso, `+0x164` = 20 con `BT_Damage`
      `c0+0x3e`). Sube hacia el tope a `+0x154` = 10 m/s². Valores del ctor `FUN_140385b40` (`140385c04..140385c2c`;
      no encontré otras escrituras). Como `vmax` = 8, **no recorta el impulso**.
    - Primer frame en el aire (`mov+0x1a8 = 0`): `+0x30 = (vx, 0, vz)` de la física (`FUN_140312ad0 +0x40`); el impulso
      se aplica después en el mismo update.
    - Sin otra orden: `FUN_140388f00(dt, 0, ChrMoveParam+0x14c = 1.0)` frena la horizontal **1.0 m/s²** hacia 0.
    - Excepción: si `FUN_140335af0` (bits 1 o 3 de `(chr+0xe8)+0x18`, grupo `+9`) → `FUN_140335de0`: velocidad desde la
      animación (bit 5) o **cero**. Qué pone esos bits: **sin identificar**.
    - `FUN_140335b40`: en el aire `b8+0x6a0 = mov+0x30`; `FUN_140320bc0` pide la posición `pos + dt · b8+0x6a0` mientras
      `b8+0x61c ≠ 0`.
    - **Gravedad: no está en este camino.** Ni `PlayerMovementCtrl` ni `FUN_140320bc0` tocan la `y` de la velocidad
      (las únicas escrituras de `+0x30` son `FUN_140335860`). Se aplicará en la física (`thunk_FUN_141c5a440`, ofuscada):
      **sin sacar**, ni su valor.
  - **Trayectoria de la animación** (`out/jumpatk/trajfull.py`, mismo formato que `out/equipload/traj.py`; solo eje
    adelante, sin vertical; `traj_a29.txt` / `traj_a25.txt` / `traj_a20.txt`). a20 / a21 / a25 / a29 `…_01_0240`
    (2.8333 s): **+3.41 m adelante** en total.
    - 0–0.30 s: −0.50 m (atrás); hasta 0.5333 s: neto +0.46 m.
    - 0.5333–1.39 s: +2.7 m (pico 6.5 m/s en 0.45–0.55 s, ~3.4 m/s hasta 1.05 s, 5 m/s en 1.09 s); 1.39–1.84 s: −0.13 m;
      1.84–2.44 s: +0.37 m.
    - Si esa trayectoria se aplica también en el aire (junto al impulso) depende de la rama `FUN_140335af0`:
      **sin confirmar**. Para no contarlo dos veces en el mod: o el impulso o la trayectoria desde 0.5333 s.
  - Después (estado 3): si sigue en `BT_Attack*Jump` y ha bajado más de 0.5 m (`DAT_1410ac694`) bajo el punto de
    despegue, pone `b8+0x4c0` bit 8 y `FUN_1401fcd50` pide el tipo 8 con la misma mano: **saltar desde un borde
    convierte el ataque en el de caída**.
- Golpe TAE 2200 de 1.1667 a 1.3333 s; `CancelControl_Move` hasta 2.1667 s.

### Tipo 8 — ataque en caída (slot +8, `…FallLoop_R_Hit`, tipo de movimiento 7)
- Ligero o fuerte, `FUN_1401f9ea0`: `b8+0x4c8` bit 12 **y** caída desde el punto más alto ≥ `ChrCommonParam+0x27c`
  (0.5 m). La caída es `FUN_140372560` = `fall+0x24 − y actual`, con `fall` = `chr.e0+0xb0` (lo actualiza
  `FUN_140372620`).
- El bit 12 (`FUN_140372620`): en el aire, bajando (vel. y ≤ 0) y con caída ≥ `ChrMoveParam+0x1f0` (0.5 m; vía
  `PlayerGameParamCalculator` vfunc `+0x78` = `FUN_140381a90`) o con `SM_Jump` activo. Mientras sube, el punto de
  partida sigue al personaje.
- **CP `IsFallAttack`** (CP 0x94, índice 0x26 de `FUN_14035c770`) vale 1 mientras el bit 12 (`FUN_14035cf40`).
- **SM `0x6c7`** (derecha; `0x871` espejo). Scripts y volcados: `D:\Reversing\DS2\out\plunge\` (`sm.py`, `cond.py`,
  `tr.py`, `trn.py`; decompilados `reg.c`, `cond.c`, `cond2.c`, `tr.c`, `sync.c`).
  - Tipos de condición registrados en `FUN_140608700` (`FUN_1405f6770(gestor, tipo, …, init, update, …)`):
    - **0x260** (`FUN_140659f70`): CP float (`+0x48` nodo, `+0x4a` pin) frente a `+0x4c`; verdadera si `CP < +0x4c`,
      o si `CP == +0x4c` con `+0x50 ≠ 0`. En el SM: `IsFallAttack ≤ 0` → **ha aterrizado**.
    - **0x26a** (`FUN_14065b030`): busca en el buffer de eventos muestreados del estado (atributo 0x0d,
      `FUN_1404dd580`) uno con userData de pista `+0x54` y de evento `+0x58` (`FUN_1405fbd10`) y valor ≠ 0;
      XOR con `+0x02`. Aquí 80 / 95 = la pista `StateControl1` de 0260, que empieza en **0.5333 s** (a20, a25, a29).
  - Inicio del destino (`FUN_14064fa60`, atributo 0x3b de la transición: `+0x10` mezcla, `+0x14` método, `+0x18`
    fracción, `+0x1c` evento sync): 0 = fracción `+0x18`; 2 = evento sync `+0x1c`; 3 = posición sync actual del
    origen (índice + fracción); 1 / 4 = `+0x1c` + fracción / índice del origen. Se envuelve con el número de eventos
    (`FUN_1405fd340`).
  - **Pista sync = `Footsteps`**: dato, no código. Es la primera pista discreta en las 4702 animaciones que la tienen,
    y su evento 2 cae en el 2200 de aterrizaje.
- **Secuencia** (s; duración = atributo 0x15 `+0x88` = `.nsa`: 0260 = 2.3333, 0250 = 1.0):
  1. Entrada desde el SM `0x64a` (condición 0x273: CP `0x1d` = 10250). Dos pseudoestados (tipo 0x7e):
     - `0x64b`: mezcla 0.1 s, 0260 desde 0 (método 0);
     - `0x64c`: mezcla 0.2 s, desde el evento sync 1 (0.2 s; método 2).
     Cuál se usa **sin confirmar**. En el SM padre `0x645` los equivalentes se llaman `Selector_Out_*` / `Selector_In_*`.
  2. Estado 0: 0260 de 0 a 0.5333 s. Luego mezcla 0.1 s (método 3) al estado 1.
  3. Estado 1: 0250 **en bucle**.
     - Bucle = bool del atributo 0x00 del nodo de animación: 1 en 0250, 0 en 0260. Ese bool vale 1 en 40 de 43
       animaciones `*Loop*` y en 5 de 794 de ataque (dato, no código).
     - Empieza en la posición sync del origen: 0.5333 s está al 90.9 % del evento sync 1 de 0260 (0.2–0.5667). La
       0250 tiene un solo evento, así que empieza en ≈0.91 s de su ciclo de 1.0 s.
  4. Estado 2 (condición 0x260): **0260 desde el evento sync 2 hasta el final**.
     - Desde el estado 1 mezcla 0.0001 s; desde el 0 (aterriza antes de 0.5333 s), 0.001 s.
     - Empieza en **0.5667 s** (a25 / a29; dura 1.7667 s). Puños (a20): 0.6 s.
     - El nodo del estado 2 (`0x6e7`, tipo 0x72) mezcla la 0260 con el SM `0x120` vía el nodo `0x6de` (tipo 0x8e):
       **sin decodificar**.
- **Golpes** (TAE; `param[0]` = slot; los slots se construyen al pedir el tipo 8 con la fila del slot +8):
  - a29 / a25: FallLoop 2200 slot 0 de −0.1333 a 1.1667 (cubre todo el ciclo: golpea mientras cae). 0260 2200 slot 0
    de 0.5667 a 0.6667: 0.1 s desde el primer cuadro del aterrizaje.
  - a20 (Puños): FallLoop igual. En 0260 hay dos 2200:
    - slot 0 de 0.4 a 0.5667: cae en la fase de inicio (estado 0 hasta 0.5333 s, más 0.1 s de mezcla);
    - slot 1 de 0.5667 a 0.6667: la fila 10004014 tiene el **slot 1 vacío** (PDP 10004014 / 0 / 0), así que
      `FUN_1403269e0` no lo empieza. Además, el aterrizaje empieza en 0.6.
  - 2300 (otra tabla, `chr+0x230 + 0x760`, `FUN_1403740e0`) en a29: FallLoop slot 1 de 0 a 0.1667; 0260 slot 1 de
    0.3333 a 0.5 y slot 0 de 0.5667 a 0.6667. **Sin identificar**.
  - Otros eventos sin analizar: 120100 (`b8+0x4d0`, `FUN_140325690`), 120200 (`b8+0x590`), 111500 (`b8+0x5c0`) y 110200.
- **Daño**: `PDP+0x1c` = **1.512** en el slot +8 de todas las armas del mod. Filas: Longsword 10032900, Battle Axe
  10054400, Mace / Club 10047600, Spear 10019000, Pickaxe 20034900, Fists 10004014.
  - +18 (dos manos) usa las mismas filas, y el salto (+9) también da 1.512.
  - El ligero 1 da 1.0 (Spear 0.96, Pickaxe 0.945).
  - `PDP+0x24`: 1095 / 1255 / 1150 / 1150 / 875 / 1530 / 480.
  - **No hay bonus por altura ni por velocidad**. Llaman a `FUN_140372560` el disparador (`FUN_1401f9ea0`), el
    control de caída (`FUN_140372620`) y `FUN_140361090`. Este último, al aterrizar con el bit 12, llama al getter
    `PlayerGameParamCalculator` vt+0x110 (`FUN_14031fac0`) y **descarta** el resultado (`1403611ff`).
  - De los lectores del bit 12 (`out\plunge\bit12.txt`), el único en el camino del golpe es `FUN_140136ab0`, y mira
    si la **víctima** está cayendo. El "mucho daño" es el ×1.512.
- **Estamina**: se cobra al empezar cada 2200 (`FUN_1403269e0` → `FUN_1403335d0(coste, 4)`), solo si el slot tiene
  PDP. Coste del slot +8: ver «Coste de estamina y combos». Dos manos (+18): Longsword 433.02, Battle Axe 496.94, Mace 501.07,
  Club 488.69, Spear 457.76, Pickaxe 463.95, Fists 311.36.
  - Activaciones: a29 / a25 = FallLoop + aterrizaje (2). Puños = inicio + FallLoop (el aterrizaje no cobra: slot vacío).
  - Si la vuelta del bucle reinicia el 2200: **sin confirmar** (driver TAE no leído; la ventana −0.133..1.167
    sobresale del ciclo de 1.0 s por los dos lados).
- **Daño por caída propio: no cambia**. Al aterrizar, `FUN_140372620` → `FUN_140372c00` → calc vt+0x88
  (`FUN_140380540`, umbrales `ChrMoveParam +0x1f4..+0x200`). Ninguno mira el bit 12, `IsFallAttack` ni el tipo de
  ataque.
  - Lo anulan `fall+0xf` (viene de `chr+0xd8 +4` bit 3; origen sin identificar), `chr+0x1a8`, `b8+0x4c8` bit 14 y
    `b8+0x5fc`.
- **Área**: el 2200 solo lleva el slot (resto de parámetros 0); el TAE no da forma ni AoE. El volumen sale del arma
  (sin decodificar).
- **Salto → caída** (bit 8 de `b8+0x4c0`, ver tipo 9): pide otra vez el tipo 8 con la misma mano, con golpes nuevos
  del slot +8 (×1.512). En el port el salto se hace en el sitio, así que solo ocurre al saltar desde un borde.
- Solo hay 0250 / 0260 en `a20`, `a21`, `a25` y `a29`; las demás armas usan los de `a29`.

### Tipo 7 — fuerte contra un objetivo derribado (slot +7)
- Fuerte, `FUN_1403961c0`: hay objetivo fijado (`chr+0x3a0` vfunc `+0x20`), su `b8+0x299 ≠ 0` y la distancia² es
  menor que `CommonParam+0xfc` del objetivo.
- `b8+0x299` lo pone la pista Morpheme **`DownDuration`** (userData 270 = 0x10e, `ChrEventTrackActionDamage`,
  `FUN_140369890`). En c0001 está en `DmgDownFaceDown/UpLoop`, `DmgCollapseFaceDown`, `DmgParryBreak_Loop` y
  `DmgBreathStart/Loop`.
- **Inalcanzable con estos datos**: `+0xfc` vale 0 en `ChrCommonParam` 100 y en las 225 filas de `EnemyCommonParam`, y
  las animaciones `…_01_0270` no están en `c0001` (sin `.nsa` ni nodo; sí existen los TAE 20010270 / 29010270).
  Longsword y Spear ponen ahí su `Light1`.

### Patada (relacionado: tipos 0x10 / 0x11 → slots 55 / 56)
- Ligero con el mismo flag de empujón (`FUN_1403962f0`, también `+0x175`): 0x11 si el agarre es 2..3, si no 0x10.
- Slot 55 = `FistAttack1HandGuardBreak` (pico: `LargestSword…GuardBreak`), 56 = `…2HandGuardBreak`. En el escudo están
  vacíos.

### Slots → TAE (mano derecha a una mano; `table.txt` tiene dos manos y la izquierda)
| Arma | +4 corriendo / +6 paso atrás | +5 rodando | +7 | +8 caída | +9 salto |
|---|---|---|---|---|---|
| Longsword | 21010210 KnifeDash | 29010220 | 29010010 Light1 | 29010250 | 29010240 |
| Battle Axe | 29010210 StraightSwordDash | 33010220 HummerRolling | 29010270 (sin anim.) | 29010250 | 29010240 |
| Mace / Club | **33010210 (sin TAE, `.nsa` ni nodo)** | 33010220 | 29010270 (sin anim.) | 29010250 | 29010240 |
| Spear | 25010210 | 25010221 | 25010010 Light1 | 25010250 | 25010240 |
| Pickaxe | 34010210 LargeHummerDash | 34010220 | 29010270 (sin anim.) | 29010250 | 29010240 |
| Drangleic Shield | 45320010 (guardia) | guardia | 45010270 (sin anim.) | guardia | 20010240 FistJump |
| Fists | 20010210 | 20010220 | 20010270 (sin anim.) | 20010250 | 20010240 |

- A dos manos (+4 / +5 en los slots 14 / 15): Longsword `22030210` / `22030220` (Rapier), Battle Axe `29030210` /
  `31030220`, Mace y Club `33030210` / `33010220` (aquí sí existe el dash), Spear `25030210` / `25030221`, Pickaxe
  `34030210` / `34030220`, Fists `20030210` / `20030220`. Los +7..+9 son los de una mano. La izquierda (44..49) = la
  derecha.
- Antorcha: +7 vacío (id 1); +4 / +5 / +6 / +8 son su guardia.

### Coste de estamina y combos
- Coste = base (`WeaponParam +0x34`, tipos de movimiento 1..7) × `WeaponStaminaCostParam[slot]`, cobrado en cada 2200
  (`tools/re/weapons.py stamina_cost`). Mano derecha a una mano (+4/+5/+6 y +7/+8/+9): Longsword 157.5 / 346.5,
  Battle Axe 180.75 / 397.65, Mace 182.25 / 400.95, Club 177.75 / 391.05, Spear 166.5 / 366.3, Pickaxe 168.75 / 371.25,
  Fists 113.25 / 249.15. Ataque en caída: ver tipo 8 (al menos 2 cobros; por vuelta, sin verificar).
- **Combo**: el tipo 2 exige que el ataque actual sea el 1 (`FUN_1401f9d50(…, 1)` compara `b8+0x18a`), y 3↔4 que sea
  3 / 4. Tras un 5..10, el ligero siguiente es otra vez el 1 (o un 5 / 6 / 10 si se cumple su condición) y el fuerte,
  el 3. Las pistas `AttackComboControl` de Dash / Rolling existen, pero ningún tipo encadena desde 5..10.

### Para `tools/re/weapons.py animation()`
- Sirve tal cual para Dash, Rolling, Jump y Light1.
- **Falla** en FallLoop (`…0250`) y Fall (`…0260`): no tienen `AttackComboControl`. Además, 0260 no está en ningún slot.
- Sin datos: 33010210 (Mace / Club a una mano) y todos los `…_01_0270`.
- **Desplazamiento**: `nsa.py` no decodifica el canal de trayectoria (`Character`, `Master` y `Root` salen fijos, incluso
  en la rodada). El avance del dash y del rolling no está sacado; el del salto es el de `ChrJumpAttackCtrl`.

### Sin confirmar
- `b8+0x11c` (factor del umbral de 4.5 m/s), `param_1[7]`, el bit 8 de los nodos, y si `SM_Dodge` / `SM_Step` acaban
  antes del final de su animación.
- Salto con ataque: la gravedad durante el salto (física ofuscada), qué pone los bits 1 / 3 / 5 de
  `(chr+0xe8)+0x18` (+9) que deciden si en el aire manda la animación, y el orden del update del ctrl frente al de
  movimiento en el frame del impulso.
- Caída: qué pseudoestado de entrada (`0x64b` / `0x64c`) se usa, si el bucle reinicia el 2200, el nodo `0x6e7`
  del aterrizaje y la pista sync (`Footsteps`, deducida de los datos).
- Qué hace DS2 con un slot cuyo TAE no tiene nodo (Mace / Club +4 / +6).
- El TAE 2300 con parámetro 1 en Jump (0.1–0.333 s), FallLoop (0–0.167 s) y Fall (0.333–0.5 s): lo trata
  `FUN_140325880` con los slots de ataque de `chr+0x230`; sin analizar.

### Implementado (`SoulsAttack.movementType`, `FlickInput`; `weapons.json`: `dash`, `rolling`, `backstep`, `jump`, `kick`)
- Orden de `FUN_140396680`, cada uno solo si su slot tiene ataque (vacío = id 1, o sin animación en c0001):
  - **ligero**: tras rodar (la rodada sigue: hasta 1.5333 s; el bloqueo de 0.5 s ya lo pone el roll), corriendo
    (sprint = modo dash), tras paso atrás (hasta 1.1667 s), patada (empujón del stick);
  - **fuerte**: salto (empujón del stick).
- **Empujón** (`FlickInput`): las teclas de movimiento sueltas ≥ 0.1 s y luego presionadas, a ≤ 45° del frente; el
  flag dura 0.05 s. Con teclado: tecla de avanzar y el botón de ataque casi a la vez (avanzar primero).
- **Sin portar / decisiones**:
  - ataque en caída (8): SM decodificado (tipo 8), falta portarlo;
  - el impulso del salto: ya sacado (no depende de la carga), falta la gravedad; el ataque se hace en el sitio;
  - el umbral de 4.5 m/s × `b8+0x11c` del dash: basta con estar corriendo (sprint);
  - el avance del dash y del rolling (trayectoria sin decodificar);
  - Mace / Club a una mano no tienen ataque corriendo ni tras paso atrás (su TAE no tiene animación): dan el ligero.

### Implementado: ataque en caída (`SoulsAttack.tryFall`, `Moveset.Fall`)
- Cualquier botón en el aire, bajando y con `fallDistance` ≥ 0.5 m. Parte aérea: inicio de 0260 hasta StateControl1
  (0.5333 s) y bucle 0250 hasta tocar suelo; golpea todo lo que alcance durante la bajada (2200 del bucle, una ventana,
  un cobro). Aterrizaje: 0260 desde el evento 2 de Footsteps, golpe 0–0.1 s, bloqueos y regen relativos al aterrizaje.
- Daño ×1.512 (`PlayerDamageParam +0x1c`) para todas las armas; **sin bonus por altura** (DS2 no lo tiene). El daño de
  caída del jugador no cambia. Puños: el aterrizaje no golpea (slot vacío).
- Decisiones: entrada por 0x64b (0260 desde 0); un solo cobro en el aire; la parte aérea se corta en agua o planeando;
  se reproduce sin espejo con la mano izquierda.

### Implementado: lado de la víctima del backstab (`SoulsAttack.Held`)
- El atacante ya no queda bloqueado los 4 s: `CancelControl_Avoid` / `Move` / `AttackAll` de su animación (Longsword
  2.967 s, LargeAxe 3.167 s, Spear 3.5 s, Fists 2.833 s).
- La víctima (mob) usa los tiempos de la víctima jugador de c0001 (**elección del usuario**: los mobs no tienen
  animación de DS2): quieta hasta poder moverse (4.733 s; puños 5.333 s) e inofensiva hasta poder atacar (5.1 s; puños
  5.7 s), sin girar. No se le puede volver a hacer un crítico hasta 3 s después de levantarse.
- Partículas de golpe crítico girando sobre la cabeza mientras está aturdida (indicador visual pedido por el usuario;
  el aspecto es decisión de diseño).
- Invulnerabilidad de la víctima (TAE 110100 de la animación de captura hasta su salida y de la de levantarse):
  rechaza cualquier golpe, de quien sea. Espada 2.0–5.433 s; LargeAxe y Spear 2.333–5.433 s; puños 2.667–6.033 s.

## Riposte y estocada tras romper guardia
Scripts y salidas en `D:\Reversing\DS2\out\riposte\` (`tae_crit.txt`, `victim_parry.txt`, `stab_tracks.txt`,
`all_sm.txt`, `d_down.c`, `x_down.txt`). Nada de esto está portado.

### Disparo (lado del atacante)
- Mismo camino que el backstab: `FUN_140391370` prueba, por este orden, backstab (`FUN_140390a50`) → estocada tras romper
  guardia (`FUN_140390700`) → riposte (`FUN_1403908a0`); gana la primera que pase (`ctx+0x48` = 1 / 2 / 3, asm
  `140391798..1403917de`). Mismo botón y misma compuerta que el backstab (ataque ligero derecho a una mano en el port).
- Las dos piden la acción en el agarre 2 (`FUN_140397510`, 0x17 / 0x16) y que su fila tenga tipo de movimiento ≠ 0
  (`[rax+4]`). El Drangleic Shield (y la antorcha) no tienen ninguna de las dos.
- Geometría (`rsp+0x60`, la misma estructura del backstab): `+0x8` distancia horizontal, `+0xc` |dy|, `+0x10` |dir.y|
  normalizado, `+0x14` |ángulo| del frente del atacante a la víctima, `+0x18` |ángulo| del frente de la víctima al
  atacante. Límites del `CommonParam` de la víctima (`chr+0x40`):

| Prueba | Riposte (`FUN_1403908a0`) | Rotura de guardia (`FUN_140390700`) |
|---|---|---|
| Estado de la víctima | `b8+0x4fc ≠ 0` (StabControl 261) y reacción `b8+0xfc` ∈ {9, 12} | `b8+0x500 ≠ 0` (262) y reacción ∈ {4, 9, 10, 11, 12} |
| Atacante | sin reacción propia (`b8+0x7e6 == 0` del atacante) | — |
| Clase víctima `+0x00` | 1..3 | 1..3 |
| Distancia | ≤ `+0x11c` | ≤ `+0x12c` |
| \|dy\| | ≤ `+0x118` | ≤ `+0x128` |
| Pendiente | \|dir.y\| ≤ \|sin(`+0x114`)\| | ≤ \|sin(`+0x124`)\| |
| Atacante mira a la víctima | ≤ π/4 (`0x1410bc2dc`, la misma constante del backstab) | ≤ π/4 |
| La víctima **mira** al atacante | ≤ `+0x110` + `WeaponTypeParam +0x44` | ≤ `+0x120` + `WeaponTypeParam +0x48` |

- Valores: jugador (`ChrCommonParam` 100) 45°, 45°, 1.0 m, 1.5 m en las dos. Fila típica de enemigo (la que ya usa
  `weapons.json`): riposte 30°, 45°, 0.5 m, 2.5 m; rotura de guardia 25°, 32°, 0.5 m, 2.5 m. `WeaponTypeParam +0x44 /
  +0x48` = 0 en todas las armas del mod.
- A diferencia del backstab, aquí la víctima tiene que estar **de frente** (sin el `− π`).

### Estado del parreado y del roto (jugador víctima, `c0001.nmb`)
- **Corrección**: `a00_16_1010 DmgParryBreak_Start` dura **1.667 s** (`.nsa +0x28`), no 1.867 (eso es el final del TAE
  101100). Las ventanas son fracciones: 262 (`b8+0x500`) de 0 a 0.667 s, 261 (`b8+0x4fc`) de 0.667 a 1.667 s.
- `StabControl` 261 solo aparece en `a00_16_1010` (40–100 %) y `a00_16_1020 DmgParryBreak_Loop` (todo). 262 en `1010`
  (0–40 %) y en las `a00_16_01xx DmgExtraHeavy*` (0–2.0 s de 3.0) (`stab_tracks.txt`). Así que contra un jugador la
  riposte solo es posible durante `DmgParryBreak_Start/Loop`.
- SM `0x18c2` (`BT_Damage`): `0x1980` (`1010`) sale al 100 % (cond. 0x25b, valor 1) hacia `0x1a16`; ahí `1020` hace bucle
  hasta el mensaje 22 `SitUp_FaceUp` o 23 `SitUp_FaceDown` (mezcla 0.001 s), que pasa a `1030 DmgParryBreak_End`.
  **Corrige** la pista anterior del mensaje 27 `SitDown`.
- Esos mensajes los pide el control de derribo (`FUN_140133700`) porque `1020` tiene la pista `DownDuration` (270 →
  `b8+0x299`): con `b8+0x299` activo acumula tiempo en `+0x10` y, cuando `+0x10 ≥ +0x14` sin agarre (`b8+0x298 == 0`),
  pone el bit 40 de `b8+0x4c0`.
  - `+0x14` = `DamageCtrlParam +0x18` del golpe que dio la reacción (`FUN_14013b4d0` → `FUN_1401336e0`, `14013b6db`).
  - Cada pulsación (`chr+0xe8+0x18 vt+0xd8`) le resta `CommonParam +0x308` (jugador 0.1 s), o `+0xf4` (0.01 s) con
    reacción 9.
- `1030 End` (2.0 s): `CancelControl_Avoid` 0–1.167, `Move` / `AttackAll` 0–1.5, TAE 110100 0–1.667; sin 261/262.
- **Quién y con qué reacción entra en `DmgParryBreak` tras un parry: sin confirmar.** Pistas:
  - Reacción 9 (`b8+0xfc`): solo `DamageCtrlParam` 99933130 (`+0x18` = 2.0 s), usada por PDP 2400/2420/2440 =
    `WeaponParam +0x68..+0x70` (iguales en las 473 armas). Quién lee esos campos no está trazado.
  - Reacción 12: ninguna fila; `FUN_140138a40` puede remapear la reacción con una tabla por nivel (`FUN_1403b5610`, sin
    identificar). Con 12, `FUN_14013b4d0` pone `dmgCtrl+0x59c = 0` y `FUN_140135b70` cuenta hasta `CommonParam +0x17c`
    (jugador 1.0 s, enemigos 2.0 s) y activa el bit 14; el bit 14 no saca de `1020` (sus transiciones son 22/23).
  - Rotura de guardia (reacción 10): `b8+0x100 = CommonParam +0x184` (jugador 1.5 s, enemigos 2.0 s); su uso no está
    trazado. Ya portada con `a00_16_1010` por elección del usuario (stamina.md «Guardia»).
- **Si** el parreado fuera la reacción 9 de PDP 2400 (sin confirmar): `1010` 0–1.667 (262 hasta 0.667), `1020` desde
  1.667 durante 2.0 s sin pulsaciones (261 todo), `1030` desde 3.667 (rueda 4.833, se mueve / ataca 5.167, fin 5.667).
  Ventana de riposte 0.667–3.667 s.

### Animaciones del atacante (slots 61 / 62, `weapons.json` → `critical.riposte` / `critical.guard_break`)
- Existen en `c0001` (`a20/25/29/36_01_0310 …ParryBreakStab` y `…_0320 …GuardBreakStab`), 4.0 s todas. Daño en dos TAE
  2201 seguidos (slot 0); PDP con `+0x1c` = 1.54 (el backstab 1.4), `DamageCtrlParam` con reacción 23 y `+0x03 = 3`.
  También 2202 (700) en los mismos tramos, 2203 (491) en 0.667 en las 0320, y 111400 0–4.167 como el backstab.
- Bloqueos: `CancelControl_Avoid` / `Move` / `AttackAll` (terminan juntas). Agarre: fin de `AttackMasterBind`.
  i-frames: TAE 110100. Regen bloqueada: TAE 101100.

| Arma (TAE riposte / rotura) | 2201 riposte | 2201 rotura | Suelta (rip. / rot.) | Libre (rip. / rot.) | 110100 | 101100 |
|---|---|---|---|---|---|---|
| Longsword (29010310 / 29010320) | 1.633, 1.733 | 1.633, 1.7 | 2.2 / 2.033 | 2.967 / 2.967 | 2.333–3.667 ambas | 0–4.0 |
| Battle Axe, Mace, Club, Pickaxe (36010310 / 36010320) | 1.633, 1.7 | 1.667, 1.767 | 2.333 / 1.933 | 3.167 / 3.167 | 2.333–3.667 ambas | 0–3.5 / 0–4.0 |
| Spear (25010310 / 25010320) | 1.633, 1.7 | 1.667, 1.733 | 2.967 / 2.4 | 3.033 / 2.933 | 2.333–3.667 ambas | 0–4.0 |
| Fists (20010310 / 20010320) | 1.6, 1.667 | 1.667, 1.733 | 2.3 / 2.1 | 3.033 / 2.933 | riposte 2.333–3.667; rotura **ninguno** | 0–3.867 / 0–4.0 |

- Cada 2201 dura 0.067–0.1 s. Fila de movimiento: riposte 230xxxxx, rotura 220xxxxx (Longsword 23013000 / 22013000,
  Battle Axe 23019000 / 22019000, Mace y Club 23017000 / 22017000, Pickaxe 23018020 / 22018020, Spear 23008000 /
  22008000, Fists 23001000 / 22001000).
- Estamina: la fórmula de `stamina_cost` da Longsword 210, Battle Axe 241, Mace 243, Club 237, Spear 222, Pickaxe 225,
  Fists 151 (igual que el backstab). **Sin confirmar** que se cobre: el cobro conocido es por TAE 2200 y estas
  animaciones solo tienen 2201.
- `weapons.py` ya las exporta, pero les pone `victim: backstab_victim(...)` (una `a79_02`), que es **incorrecto** para
  estos dos slots: la víctima es fija (abajo).

### Víctima (`a79_03_0010` / `a79_04_0010`)
- SM `0x1eb0`: `CatchDmgCategory` 20 → `a79_03_0010_CatchParryBreakSlash` (3.0 s), 30 → `a79_04_0010_CatchGuardBreakSlash`
  (3.5 s), sin switch por arma. Las dos salen al 0.9 (cond. 0x25b, mezcla 0.2 s) a `SM_CatchDamagePC` → `0720` (un
  frame tras soltar el agarre, como en el backstab) → `0730 DmgDownFaceDownEnd`.
- Pistas: las dos con `CancelControl_Avoid` y `DamageDuration` todo el tiempo. TAE: `79030010` **no tiene eventos**
  (sin 110100); `79040010` tiene 110100 de 2.333 a 3.5.

| `a79` | Dur. | Sale | Rueda | Se mueve | Ataca | Fin | TAE 110100 | Sin crítico hasta |
|---|---|---|---|---|---|---|---|---|
| `03_0010` ParryBreakSlash | 3.0 | 2.7 | 3.533 | 3.833 | 4.2 | 4.7 | 2.7–4.533 (solo el de levantarse) | 7.7 |
| `04_0010` GuardBreakSlash | 3.5 | 3.15 | 3.983 | 4.283 | 4.65 | 5.15 | 2.333–3.15 y 3.15–4.983 | 8.15 |

- Ventaja del atacante (rodar / atacar de la víctima − atacante libre), suponiendo que empiezan a la vez:
  riposte Longsword 0.567 / 1.233, LargeAxe 0.367 / 1.033, Spear 0.5 / 1.167, Fists 0.5 / 1.167; rotura Longsword
  1.017 / 1.683, LargeAxe 0.817 / 1.483, Spear y Fists 1.05 / 1.717.
- Mismo desfase atacante → víctima que el backstab (sin constante, depende del giro del gestor de agarres).

### Para el port (mobs)
- El parry del mod ya anula el golpe del mob. Para la riposte faltaría un estado «parreado» en el mob con
  `b8+0x4fc` abierto y reacción 9/12, y para la rotura de guardia un estado con `b8+0x500` y reacción 4/9–12.
- **Los tiempos de DS2 para enemigos no están extraídos** (su anibnd / TAE). Usar la secuencia `a00_16_1010/1020/1030`
  de c0001 (ventanas 262 0–0.667, 261 desde 0.667) en los mobs sería decisión del usuario, como con la víctima del
  backstab; la duración del bucle depende de la reacción, sin confirmar.
- La víctima del crítico puede usar la tabla de arriba igual que `SoulsAttack.Held` usa la del backstab.

### Sin confirmar
- Qué reacción y animación recibe el atacante parreado, y cuánto dura `1020` (confianza alta en que es
  `DmgParryBreak`, porque es la única que abre 261; sin código que lo pruebe).
- Qué lee `WeaponParam +0x68..+0x70` (PDP 2400…, reacción 9) y qué tabla remapea a 12.
- Si las animaciones de crítico cobran estamina.
- El uso de `b8+0x100` (`CommonParam +0x184` en la rotura de guardia).
- Qué hace el TAE 2203 (490 / 491).

### Implementado: riposte (`SoulsAttack.tryRiposte` / `onParried`)
- Parry acertado contra un mob: queda retenido como víctima parreada de c0001 (**elección del usuario**: Start 1.667 s,
  bucle 2.0 s de la reacción 9 —no confirmada como la del parry—, End): ventana de riposte de 0.667 a 3.667 s, quieto e
  inofensivo hasta 5.167 s, invulnerable 3.667–5.333 s; partículas de aturdimiento.
- Ligero con la derecha a una mano, tras fallar el backstab (orden de `FUN_140391370`), dentro de los límites de riposte
  de la fila de enemigo más común (30°, 45°, 0.5 m, 2.5 m) y con la víctima mirando al atacante: slot 61 (×1.54), y la
  víctima pasa a `a79_03_0010` (moverse 3.833 s, atacar 4.2 s, invulnerable 2.7–4.533 s, sin crítico hasta 7.7 s).
- Estocada tras romper guardia: ver «Implementado: multijugador (víctimas jugador)».

### Implementado: multijugador (víctimas jugador)
- Víctima jugador de backstab / riposte / estocada: el servidor corta su ataque y su rodada, la retiene y rechaza sus
  pedidos de ataque, rodada y uso de objeto hasta `attackAt`. Su cliente y los que la ven reproducen la animación de
  agarre de c0001 (`victim.taeId` de cada animación crítica: a79_02_00x0 / a79_03_0010 / a79_04_0010) y desde
  `getUpAt` el levantarse `a00_16_0730`; no se mueve hasta `moveAt`.
- Jugador parreado: `a00_16_1010` DmgParryBreak_Start (1.667 s), bucle `a00_16_1020` (2.0 s, la elección del usuario
  ya usada para mobs) y `a00_16_1030` End desde 3.667 s; quieto hasta 5.167 s. Abierto a riposte (StabControl 261).
- Estocada tras romper guardia (slot 62, ×1.54): ligero derecho a una mano, tras fallar el backstab, sobre un jugador
  con la guardia rota dentro de StabControl 262 (0–0.667 s de `a00_16_1010`), límites de la fila jugador
  (45°, 45°, 1.0 m, 1.5 m) y mirando al atacante. Víctima `a79_04_0010` (moverse 4.283 s, atacar 4.65 s, invulnerable
  2.333–4.983 s). El orden backstab → estocada → riposte sigue `FUN_140391370`.

### Implementado: salto que se vuelve ataque en caída
- Desde los 0.5333 s se guarda la altura de despegue; en el aire, más de 0.5 m (`DAT_1410ac694`) por debajo pasa al
  ataque en caída (tipo 8) con la misma mano (`FUN_1401fcd50`).
