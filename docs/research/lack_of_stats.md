# Falta de stats: qué le pasa al ataque (además del daño)

Pregunta: en DS2 SotFS, si no se cumplen los requisitos del arma, ¿el ataque cambia de velocidad, de animación o de
otra cosa? Esta nota responde con lo que sale del exe y de los datos. Lo que no se pudo cerrar está en "Sin confirmar".

**Fuentes**
- Exe `DarkSoulsII.exe`. Código ofuscado (Arxan) seguido con `D:\Reversing\DS2\out\py\deob.py` y
  `tools/re/disasm.py`; búsqueda de escrituras con `scripts\FindStoreF.java` y un barrido de bytes de las dos `.text`.
  Salidas en `D:\Reversing\DS2\out\lack\`.
- Regulation: `tools/re/params.py` (`PlayerLackOfStatsParam` PLSP, `WeaponAttackMotionParam` WAMP, `WeaponParam` WP).
- Red Morpheme `c0001.nmb`: `tools/ds2/nmb.py` (pistas) y `out\grip\grip_nodes.py` / `grip_nodenames.py` (nodos).

## 0. Resumen
- **Sí: la falta de Fuerza/Destreza frena el ataque.** No hay otra animación, ni rebote, ni rechazo del arma: el mismo
  clip se reproduce más despacio dentro de dos ventanas de la pista `AttackSpeedControl`.
- La velocidad sale de dos filas de PLSP: **fila 7** (ventana 150, el inicio del golpe) y **fila 8** (ventana 151, el
  barrido). También entra la falta de estamina para el coste del ataque.
- Solo cuentan **Fuerza y Destreza** (modo 0, `slot+0x8c`). Inteligencia y Fe no afectan a la velocidad.
- Fuera de las dos ventanas, la velocidad es 1.0.
- **Bloqueo**: en la ventana 150, el exe multiplica por `b8+0x200`, y el único valor que se le encontró escrito es 0.
  Eso congelaría el ataque, cosa que no pasa en el juego (ver §1.5 y "Sin confirmar").
- Otras filas que actúan al empezar el ataque: 13, 14 y 27 (§2). Las 13 y 14 dan 0 con los datos actuales.
  Lo que hacen 27 y su lector: sin confirmar.

## 1. Velocidad del ataque — CP `Attack_SwingSpeed`

### 1.1 Quién escribe el CP: `FUN_14035d580`
- Es el índice 4 del control de ataque: la lista de nombres de `FUN_14035c770` (`local_30[4]` =
  `"ControlParameters|Attack_SwingSpeed"`).
- Corre cada frame desde `FUN_14035cf40`, el actualizador de CP; lo llama `FUN_14035c710`.
- Escribe con `FUN_1404e2890(ctl+0x40, 4, v)`. Leído en el ensamblador (`0x14035d59d..0x14035d616`):
```
c0 = chr+0xc0,  b8 = chr+0xb8
si c0+0xa0 ≠ 0:        v = b8+0x200 × b8+0x1f8      // ventana 150
si no, si c0+0xa4 ≠ 0: v = b8+0x1fc                 // ventana 151; 0 si el byte b8+0x195 ≠ 0
si no:                 v = 1.0                      // DAT_1410ac698
```
- En el exe, el único nombre de velocidad de ataque es `Attack_SwingSpeed` (`0x1410e1bc8`).
  - `Attack_BackSwingSpeed` (nodo CP `0x1e`) y `Attack_FollowSwingSpeed` (nodo CP `0x1f`) solo existen en la red.
  - Ninguna de las dos aparece como cadena en el exe, y ningún nodo de la red las usa como entrada.
  - Así que no tienen efecto (ver "Sin confirmar").

### 1.2 Las ventanas: pista `AttackSpeedControl`
- Es la pista del canal 100 (`ChrEventTrackActionAttack`, `FUN_140368e70`). Tiene eventos con userData 150 y 151.
- `FUN_140368e70` lleva un contador por evento: suma 1 al entrar (`param_2+8`) y resta 1 al salir (`param_2+9`).
  - caso `0x96` (userData 150) → `c0+0xa0`;
  - caso `0x97` (userData 151) → `c0+0xa4`.
- Cuántas pistas lo tienen (recorrido de `c0001.nmb`): 273 llevan los dos eventos, 5 solo el 150 y 11 solo el 151.
- **En los ataques, el 150 va al principio del clip y el 151 más tarde.** Valores crudos (inicio, duración) de la
  pista, en las mismas unidades que el resto de pistas en `nmb.py`:

| Clip | 150 | 151 |
|---|---|---|
| `a29_01_0030` StraightSwordAttack1HandHeavy1 | 0.0, 0.171 | 0.371, 0.129 |
| `a31_01_0010` LargeSwordAttack1HandLight1 | 0.0, 0.186 | 0.4, 0.1 |
| `a35_01_0010` AxeAttack1HandLight1 | 0.0, 0.167 | 0.417, 0.217 |
| `a28_01_0010` KatanaAttack1HandLight1 | 0.05, 0.117 | 0.5, 0.083 |
| `a33_01_0030` HummerAttack1HandHeavy1 | 0.0, 0.257 | 0.329, 0.314 |
| `a42_01_0010` CrossbowAttack1HandLight1 | 0.078, 0.179 | 0.357, 0.5 |

- Las duraciones más comunes del 150 son 0.167 (252 eventos) y **1.0 (204)**; las del 151, 0.167 (212) y 0.143 (144).

### 1.3 Dónde se usa en la red
- El nodo CP `0x6f` (`Attack_SwingSpeed`) es la entrada 0 de **171 nodos de tipo `0x7d` (PlaySpeedModifier)**, y de
  nada más. El tipo y sus funciones del runtime ya están en poise.md y equipload.md.
- Ejemplo: `0x64f` (bajo `0x665`) envuelve el switch `0x656` sobre `AttackID_Category`, que elige el clip de ataque.
  Es decir: **el clip entero del ataque corre a la velocidad `Attack_SwingSpeed`**.

### 1.4 Los valores: `FUN_140393350`, llamado al empezar el ataque
- Lo llama `FUN_140393c00` (inicio del ataque) en `0x140394034`.
  - Va **después** del coste (`FUN_14038fb10` en `0x140393f9a`, que deja el coste en `desc+0x24`, `rsp+0x74`).
  - Otras llamadas en `0x14039419d`, `0x1403943db` y `0x14039461d`: no se siguieron.
- Desofuscado (`out\lack\deob_93350.txt`). Con `desc` = la petición del ataque (stamina.md): `+0x00` mano, `+0x0c` tipo
  de acción, `+0x20` estamina, `+0x24` coste, `+0x60` fila WAMP, `+0x70` otra fila:
```
si desc+0x60 == 0: no escribe nada
base1, base2 = (desc+0x70 ≠ 0) ? (fila+0xe0, fila+0xe4) : (WAMP+0x08, WAMP+0x0c)
g  = FUN_140376510(desc+0x20, desc+0x24)       // 0 si estamina ≥ coste; (coste−est)/coste; 1 si est ≤ 0
base2 ×= calc vt+0x68({fila 8, f, g})          // FUN_14031fcb0(…, mano desc+0, modo 0, g) → f = slot+0x8c
si desc+0x0c == 5 (ataque corriendo):
    base1 ×= lerp(P+0x88, P+0x8c, t)           // P = thunk_FUN_141cf7120 = PlayerCommonParam fila 0
    t = 0 si |v| ≤ P+0x84; (|v| − P+0x84)/(M+0x48 − P+0x84) si |v| < M+0x48; 1 si no
si no:
    base1 ×= FUN_14038f6a0(ctx, desc, 7, 0)     // = calc vt+0x68({fila 7, f modo 0, g})
b8+0x1f8 = base1;   b8+0x1fc = base2;   b8+0x200 = 0     // [rsp+0x48] = 0 (0x140049bf3)
```
- `FUN_14038f6a0` desofuscado: arma `{fila, 0, 0}`, calcula `g = FUN_140376510(desc+0x20, desc+0x24)`, llama a
  `FUN_14031fcb0(…, desc+0, modo, g)` y devuelve `calc vt+0x68` = `1 − pen` (`FUN_14031e660` → `FUN_1403811f0`).
- `pen` (`FUN_1403811f0`, damage.md) = `clamp(0.01·[c0 + (c1−c0)·f si f>0] + 0.01·[c2 + (c3−c2)·g si g>0], 0, 1)`:
  - **fila 7 = (10, 40, 0, 30)**: `pen7 = 0.01·(10 + 30·f) [f>0] + 0.30·g [g>0]`;
  - **fila 8 = (10, 30, 0, 20)**: `pen8 = 0.01·(10 + 20·f) [f>0] + 0.20·g [g>0]`.
- `f` = `slot+0x8c`, la falta de Fuerza/Destreza (`FUN_14034ce60`, damage.md §3):
  `clamp(Σ max(0, 1 − stat/req), 0, 1)` sobre Fuerza y Destreza; a dos manos, el requisito de Fuerza se divide entre 2.
  Modo 0 = `FUN_140349100` (weapon_arts.md).
- El ataque corriendo (tipo 5 de weapons.md) **no** aplica la fila 7, solo la 8.
- `P+0x84 / +0x88 / +0x8c` = 4.5 / 0.85 / 1.1 (PlayerCommonParam fila 0).
- `M` = `[[chr]+0x48]`, el mismo bloque que da 4.3–6.15 m/s en stamina.md. Que sea ChrMoveParam: sin confirmar.
- `WAMP +0x08 / +0x0c`, las combinaciones más comunes de las 3608 filas: (1.0, 1.0) 985, (1.2, 1.2) 199,
  (1.0, 0.8) 84, (1.25, 0.5) 78, (1.4, 1.4) 62.
- Las 144 filas de WTP tienen `+0xe0 / +0xe4` = 1.0. Qué fila es `desc+0x70`: sin confirmar.

**Resultado en la ventana 151** (confirmado): velocidad = `WAMP+0x0c × (1 − pen8)`.
- Con falta mínima (f → 0⁺) y estamina suficiente: × 0.90. Con f = 1: × 0.70.
- Sin estamina para el coste (g = 1): −20 % más.
- Si `b8+0x195 ≠ 0`, vale 0 (qué es ese byte: sin confirmar).

### 1.5 La otra escritura y el problema de `b8+0x200`
- `FUN_1403947b0` llama a `FUN_140393c00` solo si `ctx+0x48 == 4` (`0x140394b00`).
- En los demás casos usa el camino del chunk `0x141b3fe25` (`0x141b3ff3b..0x141b3ff5f`):
  - `b8+0x1f8 = R14+0x08` y `b8+0x1fc = R14+0x0c`, **sin falta de stats**;
  - `b8+0x200 = [rbp+0x188] = 0`.
  - Escribe además el mismo bloque `b8+0x1bc..+0x1f4` que `FUN_140392df0`.
  - Qué es `ctx+0x48` y qué fila es `R14`: sin confirmar.
- **Quién más escribe `b8+0x200`**: nadie más.
  - Se buscaron `MOVSS/MOV [r+0x200]` en todo el exe, con Ghidra y con un barrido de bytes de las dos `.text`, y
    también `LEA`, accesos indexados y escrituras de 8/16 bytes.
  - Solo aparecen 0x141b3ff5f y 0x141b94487 (las dos escriben 0) y el constructor `FUN_14031bcf0` (`0x14031be9b`,
    pone 0).
- Por lo tanto, según el código estático, **en la ventana 150 la velocidad es `0 × b8+0x1f8` = 0**.
  - El clip no avanzaría y la ventana no terminaría nunca: en el juego eso no pasa.
  - Lo más probable es que falte un escritor que el análisis estático no ve (código Arxan no desensamblado o
    descifrado en ejecución). La fórmula de la ventana 150 queda **sin confirmar**.

## 2. Otras filas que se calculan al empezar el ataque (`FUN_140393c00`, desofuscado en `out\lack\`)
- **Fila 13** — `FUN_1403935e0`:
  - `b8+0x198 = min(0.01·WAMP+0x20, calc vt+0x48({13, f modo 0, g=0}))`, y `b8+0x19c = 0`;
  - sin WAMP, el primer término vale 1.0 (`DAT_1410ac698`);
  - fila 13 = (40, 60, 40, 60).
- **Fila 14** — `FUN_140393520`: igual, con `WAMP+0x24` → `b8+0x1a0`, y `b8+0x1a4 = 0`. Fila 14 = (60, 90, 60, 90).
  - **`WAMP+0x20` y `+0x24` valen 0 en las 3608 filas**, así que `b8+0x198` y `b8+0x1a0` quedan en 0 aunque falten
    stats. `FUN_1403947b0` también los pone a 0 en `0x14037f114` / `0x14037f125`.
  - Quién los lee: sin confirmar.
- **Fila 27** (`0x1b`):
  - `0x14039407b`: `b8+0x1b0 = calc vt+0x48({27, f modo 2, g = xmm7})` (`pen`, no `1 − pen`);
  - también en `FUN_1403932e0`, con `g = 0`, llamado desde `0x14039389b` (camino del hechizo, casting.md);
  - fila 27 = (30, 80, 0, 30);
  - qué es `xmm7` y quién lee `b8+0x1b0`: sin confirmar.
- **Fila 11**: coste de estamina (`FUN_14038fb10`, stamina.md). Se confirma que corre antes que el cálculo de velocidad.

## 3. Mapa de filas de PLSP → consumidor
| Fila | Valores (c0, c1, c2, c3) | Consumidor | Modo de `f` | Efecto |
|---|---|---|---|---|
| 0 | 30, 80, 0, 10 | `FUN_140391fe0` vía `FUN_14038f6a0` (`0x1403921a1`) | 2 | daño `k1` (damage.md) |
| 1 | 30, 80, 0, 20 | `FUN_140391fe0` `0x140392668` | 2 | `slot+0x78` (`hit+0xc0`) = WP+0x84 × … × (1 − pen), daño de estamina (stamina.md) |
| 2 | 30, 80, 0, 20 | `FUN_140391fe0` `0x140392707` | 2 | `slot+0x7c` = WP+0x88 × `[rbx+8]` × (1 − pen); qué es: sin confirmar |
| 3 | 30, 80, 0, 20 | `FUN_140391fe0` `0x140392779` | 2 | `slot+0x80` = WP+0x94 × (1 − pen); qué es: sin confirmar |
| 4 | 30, 80, 0, 0 | `FUN_14034cc80` | — | estabilidad de guardia (stamina.md) |
| 5 | 30, 80, 0, 0 | `FUN_14034fbc0` (`0x14034fc9e`) | ? | multiplica por `1 − (c0 + (c1−c0)·f)·xmm10` un vector de 10 valores del arma (`FUN_14014ce60`); qué vector es: sin confirmar |
| 6 | 30, 80, 0, 0 | `FUN_14038f940` | 2 | `b8+0x264` = WTP+0xcc × (1 − pen) (weapon_arts.md); en stamina.md es el multiplicador de regeneración con `b8+0x14 ≠ 0` |
| **7** | 10, 40, 0, 30 | `FUN_140393350` → `FUN_14038f6a0` (`0x14039341b`) | 0 | **`b8+0x1f8`, velocidad en la ventana 150** (§1) |
| **8** | 10, 30, 0, 20 | `FUN_140393350` (`0x1403933e9`) | 0 | **`b8+0x1fc`, velocidad en la ventana 151** (§1) |
| 9 | 10, 30, 0, 0 | `FUN_14038f870` | 0 | `GuardSpeed` (weapon_arts.md) |
| 10 | 50, 100, 0, 50 | `FUN_14038f9d0` (`0x1403938eb`) | 1 | tiempo de lanzamiento (casting.md) |
| 11 | 30, 50, 0, 0 | `FUN_14038fb10` | — | coste de estamina del ataque (stamina.md) |
| 13 | 40, 60, 40, 60 | `FUN_1403935e0` | 0 | `b8+0x198` (§2; 0 con los datos) |
| 14 | 60, 90, 60, 90 | `FUN_140393520` | 0 | `b8+0x1a0` (§2; 0 con los datos) |
| 15 | 30, 50, 0, 0 | `FUN_140380070` (calc `vt+0x1d0`) → `FUN_140381c20` | por pieza | defensa de cada pieza de armadura × (1 − 0.01·(30 + 20·f_pieza)) |
| 16 | 30, 50, 0, 0 | `FUN_140381350` (calc `vt+0x1d8`) → `FUN_140381c20` | por pieza | los 5 primeros valores del vector de resistencias de cada pieza |
| 17 | 30, 50, 0, 0 | `FUN_140381350` → `FUN_140381c20` | por pieza | los valores 5..9 de ese vector |
| 18 | 30, 50, 0, 50 | calc `vt+0x270` `FUN_140381d10` | 2 | i-frames de esquiva (agility.md) |
| 19 | 30, 50, 0, 50 | calc `vt+0x60` (`FUN_140376770`) | ? | multiplicador del coste de acción (stamina.md) |
| 26 | 0, 20, 0, 0 | calc `vt+0x208` `FUN_140380ca0` | `+0x350` | `ItemUse_Speed` (agility.md) |
| 27 | 30, 80, 0, 30 | `FUN_140393c00` `0x14039407b`, `FUN_1403932e0` | 2 | `b8+0x1b0` (§2) |
| 28 (`0x1c`) | 30, 80, 0, 30 | `FUN_14037fdd0` | por pieza | poise de la armadura (poise.md) |

- `FUN_140381c20(ctx, pieza, fila)` = `1 − 0.01·(c0 + (c1−c0)·f)` con `f = equipo+0x2b8 + pieza·0x30` (4 piezas);
  1.0 si f = 0. No usa `g`.
- **Sin consumidor encontrado: 12, 20, 21, 22, 23, 24 y 25.**
  - Se revisaron las llamadas directas a `FUN_14031fcb0`, `FUN_14031fc10`, `FUN_14038f6a0`, `FUN_14038f9d0` y al
    getter de PLSP `FUN_140358b30`, y todas las entradas de la vtable de `PlayerGameParamCalculator` (`0x1410e4ec8`).
  - Pueden entrar por calc `vt+0x268` (`FUN_140381cb0`, que recibe la fila como argumento), por `vt+0x48` / `+0x58` /
    `+0x60` / `+0x68` / `+0x70` con la fila en un registro, o por llamadas virtuales que el barrido no ve.
- **Mapeados nuevos en esta nota: 2, 3, 5, 7, 8, 13, 14, 15, 16, 17 y 27.**

## 4. Qué NO cambia con la falta de stats (en lo revisado)
- No hay otra animación, otro AttackID, otro TAE, rebote ni rechazo del arma:
  - `FUN_140393350` solo escribe velocidades;
  - `AttackID_Category` / `AttackID_Number` salen del TAE de WAMP (`c0+0x00`, bow.md) sin pasar por la falta.
- **No es una prueba de ausencia**: no se recorrió todo `FUN_140393c00` (el desofuscador para en una tabla de saltos
  en `0x14000c60b`).
- Poise, hiper armadura, ventanas de combo y parry: no aparece ninguna fila de PLSP en esos caminos dentro de lo
  revisado. Las filas 13, 14 y 27 son las únicas sin efecto conocido en el ataque.

## Sin confirmar
- **`b8+0x200`** (multiplicador de la ventana 150): solo se le ven escrituras de 0. Hace falta leerlo en memoria con
  el juego en marcha durante un ataque, o encontrar el escritor que falta. Hasta entonces, la velocidad de la ventana
  150 (y si la fila 7 llega a notarse) no está confirmada.
- `b8+0x195` (pone a 0 la velocidad de la ventana 151): quién lo escribe y qué es.
- `Attack_BackSwingSpeed` / `Attack_FollowSwingSpeed`: no se les ve ni escritor en el exe ni consumidor en la red. Si
  el runtime de Morpheme los usa de otra forma, no se encontró.
- Las unidades de las pistas (segundos o fracción del clip) en la tabla de §1.2.
- Qué es `ctx+0x48` en `FUN_1403947b0` (con 4, ataque normal con falta de stats; con otro valor, velocidades sin
  falta). Qué filas son `desc+0x70` y `R14`.
- Que `[[chr]+0x48]` sea ChrMoveParam (el `M+0x48` del ataque corriendo).
- Las otras tres llamadas a `FUN_140393350` (`0x14039419d`, `0x1403943db`, `0x14039461d`): en qué caso corren y con
  qué `desc`.
- Qué son `slot+0x7c` / `+0x80` (filas 2 y 3), el vector de la fila 5 (y el valor de `xmm10`), `b8+0x198`, `b8+0x1a0` y
  `b8+0x1b0`, y quién los lee.
- El modo de `f` de las filas 5 y 19.
