# IA de enemigos: EzState, órdenes del motor, params de comportamiento

Fuente: `DarkSoulsII.exe` (SotFS) en Ghidra headless (copia privada del proyecto) y los archivos de `GameDataEbl`.
Herramientas y salidas fuera del repo:
- `D:\Reversing\DS2\tools\esd.py`: lector del formato `fsSL` y desensamblador de expresiones (este documento, §2).
- `D:\Reversing\DS2\out\ai\`: `ai102000.txt` / `ai102010.txt` (Hollow Soldier, legibles), `names.txt` (los 487 ESD con su
  nombre interno), `jt.json` (tablas de salto id → código), `search_query_7xxx.c`, `common_query_1_6.c`,
  `common_cmd_timers.c`, `bd*.txt` (bloques de cada caso) y `d14/d15/d18/d22.c` (funciones citadas abajo).

## Resumen
- **DS2 no usa Lua ni HavokScript para la IA.** No hay `.luabnd` ni cadenas de Lua de IA en el exe (la única `.lua` es una
  cadena ancha suelta en `0x1411ec3b8`). La IA son **máquinas de estados EzState** (`.esd`, "EzState v2.25.0", cadena en
  `0x14116b3c0`), las mismas que usan los diálogos y eventos.
- Cada enemigo tiene **dos ESD**: uno **superior** (上位, "qué hacer": esperar, patrullar, buscar, pelear, volver) y uno
  **inferior** (下位, "cómo hacerlo": acercarse, elegir el ataque, esquivar, girar). Se comunican por 8 variables de un
  byte. El superior elige qué "mapa de estados" del inferior corre.
- El ESD no nombra animaciones. Pide **un número de acción** (`5204(id)`). El motor lo convierte en una ranura de
  `EnemyBehavior{,Second,Third}Param` (acciones 0..29) o una entrada de `EnemyComboBehaviorParam` (acciones 50..56).
  La entrada tiene los ids de `EnemyDamageParam` de los golpes.
- La "tabla de ataques" de un enemigo **está escrita en el ESD**: un número al azar `RandInt(0..N)` y una cascada de
  umbrales, cada uno ligado a una distancia y un ángulo (§7.3). No hay una tabla de ataques con peso, alcance y
  enfriamiento en los params.

## 1. Archivos
- Ruta: `FUN_14048b3f0` arma `gamedata:/ezstate/ai%06d.esd` (o `dlc_data:` / `ezstate:` según `FUN_14048ce40` /
  `FUN_14048cd60`); id < 0 → `ezstate:/ezstate/ai009999.esd` (no existe en `GameDataEbl`).
- Hay **487** archivos `ai%06d.esd` en `GameDataEbl` (probados los 10⁶ nombres contra el BHD, `extract_bdt.py`).
  Cada uno trae su nombre interno en japonés (`names.txt`). Ejemplos:
  - `ai102000` `王国兵士（亡者）` ("soldado del reino (hueco)") y `ai102010` `上位：王国兵士（亡者）：汎用`
    ("superior: … : genérico");
  - variantes del superior: `ai102052` `…：死体擬態（グループ無）` (finge ser un cadáver), `ai102055`
    `…：攻撃待ち（検証用）` (espera para ser atacado, de prueba);
  - `ai132100` `上位：スケルトン：ボス用` (esqueleto, para jefe), `ai625000` / `ai625010` Ornstein.
- Patrón: `aiXXXX00` = inferior (los jefes lo marcan `下位：`), `aiXXXX10`..`aiXXXX99` = superiores.
- **Enemigo de referencia**: Hollow Soldier (EnemyParam 102000, c1020; enemies.md §11). Inferior `ai102000.esd`
  (202 768 bytes), superior `ai102010.esd` (45 728 bytes).

## 2. Formato `fsSL` (64 bits)
Verificado contra los 487 archivos (485 se leen enteros; `ai703370` y `ai803370` fallan en el lector y no se miraron).
- Cabecera (0x6C bytes de enteros de 32 bits): `fsSL`, 1, 2, 2, 0x54, tamaño de datos, 6, 0x48, 1, 0x20, nº de grupos,
  0x48, nº de estados, 0x38, nº de condiciones, 0x18, nº de órdenes, 0x10, nº de argumentos, …
- Los datos empiezan en `+0x6C`; todos los offsets son relativos a ese punto (enteros de 64 bits):
  - `+0x18` tabla de grupos, `+0x20` nº de grupos, `+0x28` / `+0x30` nombre UTF-16 (offset, largo).
  - Grupo (0x20): `id, estados, nº, estados`.
  - Estado (0x48): `id, condiciones (tabla de punteros), nº, entry, nº, exit, nº, while, nº`.
  - Condición (0x38): `estado destino (-1 = ninguno), órdenes al pasar, nº, subcondiciones, nº, expresión, largo`.
  - Orden (0x18): `i32 banco, i32 id, argumentos, nº`. Argumento (0x10): `expresión, largo`.
- Expresiones (pila; lo que se ve en los 487 archivos):
  - `0x00..0x7F` entero `b − 64`; `0x80` float32; `0x81` float64; `0x82` int32; `0xA5` cadena UTF-16; `0xA1` fin.
  - `0x84..0x8A`: llamada a función con `b − 0x84` argumentos; el id va en la pila antes de los argumentos.
  - `0x8C` + … `0x99`: operadores (`+ − * / <= >= < > == != && ||`) y `0x9A` `!`.
  - `0xB8` (`env(n)` en las salidas): argumento `n` con el que se llamó al grupo actual. Se ve en grupos llamados con
    argumentos (`env(0)` se usa como id de acción en `5204(env(0), …)`).
  - `0xB9` / `0xBA`: después de llamar a un grupo, `B9` es lo que devolvió (`if (B9 == 1)`); `B9 != BA` es el caso por
    defecto. **Lectura por uso, sin confirmar en el motor.**
- Bancos de órdenes vistos:
  - **1**: órdenes del motor (§5, §6).
  - **5**: los mismos ids que el banco 1, con un primer argumento extra que es una condición
    (`5:5300((f5065() != 0) && …, 4, 4)`). **Que se ejecute solo si la condición es verdadera sale del uso; el código del
    banco 5 no está trazado.**
  - **6**: llamar a otro grupo del mismo archivo; el id de la orden es el id del grupo (`2147483xxx`), los argumentos
    son los `env(n)`.
  - **7**, id −1: devolver un valor al grupo que llamó (estados finales `pass cmd 7:-1(n)`).

## 3. Carga y clases del motor
- **Ids de lógica por enemigo**: `GeneratorRegistParam_<mapa>` (fila de 0x10 bytes):
  `+0x00` EnemyParam, **`+0x04` ESD superior, `+0x08` ESD inferior**.
  - `FUN_140412970`: si `*(gen+0x60)+8 ≥ 0` y `+4 ≥ 0`, precarga los dos ESD con `FUN_140422e30` (AiManager).
  - m10_10 fila 10200000: `102000, 102010, 102000`; fila 10200020: `102000, 102052, 102000` (superior "cadáver").
  - `EnemyDefaultLogicParam` (0x10 bytes) tiene la misma forma: 102000 → `+4` 102010, `+8` 102000, `+0xC` 10
    (102010 → `+0xC` 20). **Su lector no está trazado**: no se sabe si es el respaldo sin generador ni qué es `+0xC`.
- `AiManager` (`DAT_1416148f0+0x28`, vtable `0x1410edb58`):
  - `+0x1248`: árbol id → ESD cargado, con contador de referencias (`FUN_140422e30` carga, `FUN_140422880` busca).
  - `FUN_1404230c0` carga `param:/LogicEzStateSharedParam.param` en `+0x1228` y `param:/LogicComParam.param` en `+0x1238`
    (nombres en `FUN_14048b480`).
- `ChrAiBase::init` (`FUN_140428090`, vtable `0x1410edd08` [1]): guarda los ids en `+0x48` (superior) y `+0x4c`
  (inferior), busca los dos ESD (`FUN_140422880`) en `+0x18` y `+0x28`, y crea la entrada lógica (`FUN_14010e4c0`,
  0x70 bytes) en `+0x40`. `ChrAi` (vtable `0x1410edb78`) es la clase que usan los enemigos.
- Quién corre el ESD: **`ChrEzStateCtrl`** (vtables `0x1410be978` y `0x1410be9e8`), hija de `CommonEzStateCtrl`
  (`0x1410f43d8`).
  - Orden (`FUN_14011b610`, vtable [1]): si el delegado `+0x78` la acepta (vt `+0x28`), la manda a él (vt `+0x30`);
    si no, el switch propio.
  - Función (`FUN_14010f020`, segunda vtable [1], this = objeto + 8): igual con el delegado (`+0x38` / `+0x40`);
    si no, el switch propio; lo que no conoce va a `CommonEzStateCtrl` (`FUN_140490a90`).
  - `+0x70` = el `ChrAi`: las funciones 5xxx llaman a métodos de `ChrAi` (vt `+0x88`, `+0x90`, `+0xf8`, …) sobre
    `[this+0x70]`.
  - `+0x78` = **`SearchEzStateCtrl`** (vtable `0x1410edf48`): funciones 7xxx (`FUN_1404319b0`, [8]) y órdenes 7xxx
    (`FUN_1404395e0`, [6]). Es el módulo de objetivo y percepción. `+0x10` = el `ChrAi`, `+0x20` = objetivo actual.
- Tablas de salto (exe, base `0x140000000`; decodificadas en `jt.json`):
  - funciones: 5001..5214 (bytes `0x11b2f0`, offsets `0x11b1b4`), 5600..5713 (`0x11b478` / `0x11b3c8`),
    5901..6008 (`0x11b528` / `0x11b4ec`); además 5, 5000, 5310 y 5900 sueltos. 139 ids.
  - órdenes: 90..95 (`0x11c788`), 5204..5438 (`0x11c854` / `0x11c7a0`), 5466..5703 (`0x11c97c` / `0x11c940`),
    5751..5999 (`0x11cac4` / `0x11ca6c`); además 5000, 5465 y 5750. 111 ids.
- Los 487 ESD usan 186 ids de función y 311 órdenes distintas (contadas con `esd.py`).
  **Las funciones 6000..6008 (salvo 6008) y 7300..7801 no se ubicaron en estos switches**; su manejador está sin trazar.

## 4. Funciones y órdenes básicas (`CommonEzStateCtrl`)
Desplazamientos sobre `CommonEzStateCtrl`. La función recibe `this + 8`, por eso su código lee 8 bytes menos.

| Id | Tipo | Qué hace | Fuente |
|---|---|---|---|
| `f1(i)` | entero | valor `i` (0..3) guardado por `cmd 1` | `FUN_140490a90` caso 1 |
| `f2(i)` | float | temporizador `i` (0..3) en segundos | caso 2; avanza en `FUN_140491320` |
| `f3()` | float | **segundos dentro del estado actual** (`+0x50`, 0 al entrar) | caso 3; `FUN_140491290`, `FUN_140491320` |
| `f4(a, b)` | float | `a + (b − a)·u`; `u` se sortea **una vez al entrar en el estado** (`+0x54`, `[0, 1)`) | caso 4; `FUN_140491290` |
| `f5(fila)` | float | valor de `LogicEzStateSharedParam[fila]` (`FUN_140422a10`) | `FUN_14010f020` id 5 |
| `f6(bandera)` | bool | bandera de evento (`FUN_140474230`) | caso 6 |
| `cmd 1(i, a, b)` | — | `f1(i) = RandInt(a, b)` (xorshift, `FUN_140491430`, ambos incluidos) | `FUN_1404910b0` |
| `cmd 2(i)` | — | temporizador `i` a 0 y en marcha | ídem |
| `cmd 3(i)` / `cmd 4(i)` | — | reanudar / parar el temporizador `i` | ídem |
| `cmd 90(n, texto)` | — | mensaje de depuración (`cmd 90(2, "下位ロジックに存在しない下位ステートマップIDが指定されました。")`) | uso |

## 5. Funciones del motor más usadas
"Llamadas" = veces en los 487 ESD. Los métodos `ChrAi` son la vtable `0x1410edb78`. "Sin confirmar" quiere decir que
el código está ubicado pero no se sabe qué representa el campo que lee.

### 5.1 Variables lógicas y mapas de estados (confirmadas)
| Id | Llamadas | Qué hace | Fuente |
|---|---|---|---|
| `f5900(i)` | 27 019 | byte `i` (0..7) de `ChrAi+0x3a1`; −1 si `i ≥ 8` | `FUN_140425100` |
| `cmd 5300(i, v[, avisar])` | 11 999 | escribe ese byte; con el tercer argumento avisa (`FUN_14042a110`) | `FUN_1404265c0` |
| `f5901()` | 7 071 | mapa de estados que corre el inferior (`ctrl+0x68`) | `FUN_140490a80` |
| `cmd 5301(id)` | 3 510 | pide ese mapa al inferior (`ChrAi+0x368`); se aplica en `FUN_1404272b0` | caso 5301 |
| `f5700(i)` / `cmd 5700(i, v, avisar)` | 3 560 / 618 | byte `i` (0..3) compartido por el **grupo** de enemigos (`ChrAi+0x9a0 → +0x10 → +0x18+i`); avisar recorre el grupo | `FUN_14042aaf0`, `FUN_14042b9b0` |
| `cmd 5750(i, v)` / `cmd 5751(v)` | 672 / 1 783 | `ChrAi+0x9f0`: byte `+0x7c+i` / entero `+0x6c`. **Sin confirmar** qué son | `FUN_140430a10`, `FUN_140430a20` |
| `f5101()` | 1 475 | `LogicEzStateSharedParam[20000 + b]` o `[20100 + b]`, `b = ChrAi+0x3ab` | caso 5101 |

### 5.2 Objetivo y percepción (`SearchEzStateCtrl`, `FUN_1404319b0`)
| Id | Llamadas | Qué devuelve | Fuente |
|---|---|---|---|
| `f7001()` | 16 416 | **distancia al objetivo** (objetivo vt `+0x78` con la posición propia); 9999 sin objetivo | `FUN_14043d980`, `DAT_1410bf330` |
| `f7002()` | 8 179 | **ángulo al objetivo en grados, valor absoluto** (giro entre el frente propio y la dirección al objetivo, envuelto a ±π, × 57.2958) | caso `0x1b5a`, `DAT_1410bea14`, `DAT_1410aca00` |
| `f7007()` | 4 064 | altura del objetivo − altura propia | caso `0x1b5f` |
| `f7018(min, max)` | 6 283 | `min ≤ d ≤ max`, con `d` = distancia de `FUN_1404316d0` (corrige por el cuerpo según `chr+0x2b8+0x3c`; detalle sin confirmar) | caso `0x1b6a` |
| `f7020(a, b, c)` | 1 374 | hay alguno de los 5 candidatos (`+0x100`, paso 0x20) a una distancia en `[a, b]`; `c` filtra por bits. **Qué candidatos son: sin confirmar** | `FUN_14043a7a0` |
| `f7060()` | — | distancia > `chr+0x2b8 +0x1c` (50 por defecto, `DAT_1410e066c`) | caso `0x1b94` |
| `cmd 7009` | — | borra el objetivo (`FUN_14010c9e0(+0x18, 0)`) | `FUN_1404395e0` |
| `cmd 7010` / `7011` | — | guarda / restaura el objetivo (`+0x18..+0x28` ↔ `+0x30..+0x40`) | ídem |
| `cmd 7050` / `7051` | — | congela la posición del objetivo (`+0xad` = 1, copia en `+0xb0`) / la suelta; `FUN_140431910` usa la copia | ídem |
| `cmd 7000..7008`, `7012`, `7013`, `7014` | — | casos con su función (`FUN_14043c450`, `FUN_14043d1c0`, …; 7012 pone como objetivo una entidad de `DAT_1416148f0+0xd0`). **Sin confirmar** | ídem |

### 5.3 Acción y estado del personaje
| Id | Llamadas | Qué hace | Fuente |
|---|---|---|---|
| `cmd 5204(acción, comprobar)` | 2 528 | **pide la acción** (§8). Con `comprobar ≠ 0` mira antes si el objetivo es alcanzable por la navmesh (`ChrAi+0x690`, `FUN_14042dfa0`, `FUN_14042e360`). 60 y 61 se cambian por valores del arma (`FUN_14010e7e0` / `FUN_14010e720`; **sin confirmar**) | caso 5204, `FUN_1404264a0` |
| `f5000()` | 4 569 | `ChrAi` vt `+0x88` (`FUN_140424bb0`): 1 si `f5001()` = 0, `chr.b8+0x580` = 0, `chr.b8+0x298` = 0 y `f5005()` = 0. Se usa como condición para lanzar `5204`; "libre para actuar" es lectura por uso, **sin confirmar** | ídem |
| `f5001()` | 8 144 | vt `+0x90` (`FUN_140425680`): 1 si `ChrAi+0x350` bit 0 (acción pedida) o `chr.b8+0x7e4` / `+0x210`. La subrutina de ataque espera a que deje de valer 1 (§9.2). **Qué son los dos bytes: sin confirmar** | ídem |
| `f5005()` | 2 837 | vt `+0xa8`: `chr.b8+0x7e6` (byte). **Sin confirmar** | `FUN_140425710` |
| `f5100()` / `cmd 5210(m)` | 6 050 / 2 397 | modo `m` (0..3) del personaje (`chr.b8+0x2e8`). Tras `5210`, `f5100()` sigue devolviendo el modo anterior 0.4 s (`ChrAi+0x361` / `+0x364`, `FUN_1404267e0`). **El modo elige el juego de comportamientos** (§7.1) | `FUN_140424fe0`, `FUN_1404264b0` |
| `f5050(n)` / `f5054(n)` | 9 009 / 3 245 | vt `+0xc8` / `+0xd0` → `FUN_140426d00(objetivo / uno mismo, n)`. Por el uso, `n` es un estado del personaje (2, 3, 7, 13, 14, 17, 18…). **Sin confirmar** | `FUN_140425490`, `FUN_140425480` |
| `f5600`..`f5646` | — | navmesh (`ChrAi+0x690`, `ChrAiNavimeshCtrl`): p. ej. 5603 = distancia al siguiente punto de la ruta (`FUN_14042c680`), 5643 actualiza la ruta (`FUN_14042db30`). Resto **sin confirmar** | casos 56xx |
| `cmd 5405 / 5505 / 5535 / 5565 / 5465` | — | todas van a `FUN_140123480`; `5406 / 5506 / 5536 / 5566 / 5436` a `FUN_140120970`. Por los argumentos (velocidades, tiempos), movimiento hacia un punto; **sin confirmar** | casos |
| `cmd 5800 / 5801` | — | `FUN_14042e810` / `FUN_14042e890` (navmesh). **Sin confirmar** | casos |
| `cmd 5900..5906` | — | `FUN_1401213b0`, `FUN_140121310`, `FUN_140121dd0`, `FUN_140122110`, `FUN_140121ac0`, `FUN_1401217c0`. Por el uso (estados "girar hasta mirar al objetivo" con `f7002`), giros; **sin confirmar** | casos |
| `cmd 5207 / 5208 / 5262 / 5271 / 5421` | — | ponen bits en `ChrAi+0x350` / `+0x354` (2, 4, 0x10000000, 0x800000, 0x20), que `FUN_140427750` pasa a la entrada lógica como pulsaciones 6, 7, 0x17, 1 y 0x10. **Qué botón es cada una: sin confirmar** | casos, `FUN_140427750` |

El resto de ids (5003..5024, 5060..5094, 5200..5214, 5700..5713, 5902..5913, 6000..6008, 7080..7801 y las órdenes no
listadas) tiene la dirección de su caso en `jt.json` y `bd.txt`. **Su significado no está decodificado.**

## 6. Cómo una petición se vuelve entrada lógica — `FUN_140427750` (ChrAi vt `+0x150`)
- `cmd 5204(a)` → `ChrAi` vt `+0x80` (`FUN_1404264a0`): `+0x350 |= 1`, `+0x35c = a`.
- En el siguiente tick, si `+0x350` bit 0:
  - `a = 62` (0x3e) se cambia por `FUN_14010e820(ChrAi+0x2c8)` (**sin confirmar**);
  - `50 ≤ a ≤ 56` → entrada lógica tipo **4**, subíndice `a − 50` (`input+0x54`);
  - otro `a` → tipo **3**, id `a` (`input+0x50`).
- `FUN_1404272b0`: `ChrAi+0x3ac = (a < 10)`. `FUN_140427ac0` cuenta las acciones 20..29 en `+0x3c1`
  (**para qué: sin confirmar**).
- El resto de bits de `+0x350` / `+0x354` se pasan como otras pulsaciones (tabla de §5.3).

## 7. Params de comportamiento
### 7.1 Del enemigo a sus filas — `FUN_1403a8f40`, `FUN_14039f910`
- `id = EP[+0x4c + 4·modo]`, con `EP = chr+0x38` (EnemyParam, enemies.md §1) y `modo = chr.b8+0x2e8` (`f5100`).
  **Esto da lector a `EP+0x4c..+0x58`**, que enemies.md tenía como "sin lector".
- `FUN_140358580(id)`: id ≥ 100 000 → **`EnemyEquipWeaponParam`** (slot `+0x380`); menor → `ChrEquipWeaponParam`
  (`+0x340`). Su `+0x00` es el **id del juego de comportamientos**.
- `FUN_14039f910(set, id)` guarda cuatro filas con ese id: `EnemyBehaviorParam` (slot `+0x3a0`),
  `EnemyBehaviorSecondParam` (`+0x3b0`), `EnemyBehaviorThirdParam` (`+0x3c0`) (`FUN_1403583e0`) y
  `EnemyComboBehaviorParam` (`+0x3d0`, `FUN_1403586c0`).
- Hollow Soldier: `EP 102000 +0x4c` = 10200000 (`+0x50..+0x58` = 0); `EnemyEquipWeaponParam 10200000 +0x00` = 102000.

### 7.2 Disposición (lectores en `FUN_14039f9e0..FUN_14039fb20`, `FUN_14039ebe0`)
- `EnemyBehavior*Param`: 880 bytes = **10 entradas de 0x58**. Acción `a`:
  `a < 10` → Behavior[`a`], `a < 20` → Second[`a − 10`], `a < 30` → Third[`a − 20`].
  - `+0x00 / +0x04 / +0x08`: ids de **`EnemyDamageParam`** de las ranuras de ataque 0 / 1 / 2 (−1 vacío;
    `FUN_14039f620`, §8.1). El evento TAE 2200 elige la ranura con su parámetro (§8.3).
  - `+0x0c / +0x10 / +0x14`: otra tabla de 3 ids (`FUN_14039f740`, §8.1). **Qué es: sin confirmar.**
  - `+0x18`: fila de `EnemyAttackMoveParam` (giro durante el ataque, §8.5).
  - `+0x1e` bits 0 / 1: leídos por `FUN_14039fad0` / `FUN_14039fa70`. **Sin confirmar** qué son.
  - `+0x1f`: **4 = entrada apagada** (`FUN_1403a8fb0` no la lanza).
  - `+0x20 / +0x24 / +0x28`: ids de la segunda tabla de ataque (`FUN_14039ee00`, la del evento 2300, §8.1);
    `FUN_14039fb20` los busca con `thunk_FUN_141b75ce3` y comprueba que `+0x4c` sea > 0. **Qué param es: sin confirmar.**
  - `+0x1c`, `+0x2c`, `+0x30..+0x48`: se copian al objeto de acción (§8.1); **uso sin confirmar** (Hollow Soldier:
    `+0x30..` = 50 / 0.6..1.4 / 1.1..1.6 / 0.3 / 0 / 5 / 2).
  - Temporizador por ranura: `FUN_14039ec80` lo carga al lanzar (§8.1); **fuente sin desglosar**.
- `EnemyComboBehaviorParam`: 1512 bytes = **7 entradas de 0xd8** (acciones 50..56).
  - `+0x07`: 4 = apagada (`FUN_1403a9870`).
  - `+0x08 / +0x0c / +0x10`: ids de `EnemyDamageParam` de las ranuras 0 / 1 / 2 (`FUN_14039f380`), una por paso.
  - `+0x3c / +0x40 / +0x44`: fila de `EnemyAttackMoveParam` de cada paso (`FUN_14039f090`, §8.5).
  - `+0xcc` bits 0..3 (`FUN_14039f9e0`). **Sin confirmar.**
  - `+0x00` (11, 12 en el Hollow Soldier): **sin lector trazado**; coincide con el `NN` de las animaciones del combo
    (§8.2).

### 7.3 Hollow Soldier 102000: acciones y golpes
Las acciones que pide `ai102000.esd` caen justo en las entradas encendidas:

Llamadas de la tabla de ataques `S2147483585` (§9.2); `S2147483595(acción, alcance, ángulo, mov, x)`:

| Acción | Entrada | Golpes (EDP, enemies.md §4.2) | Alcance / ángulo en `S2147483585` |
|---|---|---|---|
| 10 | Second[0] | — (encendida, sin EDP) | 2.8 / 70° |
| 13 | Second[3] | 110200310 | 4 / 180° |
| 14 | Second[4] | 110200410 | 2.8 / 40° (y `S2147483597(14)`) |
| 15 | Second[5] | 110200510 | 4.1 / 20° |
| 16 | Second[6] | 110200610 + 110200620 | 4 / 25° (`x` = 1) |
| 17 | Second[7] | 110200710 | 5.1 / 20° |
| 50 | Combo[0] | 110200110 / 120 / 130 (3 golpes) | 2.9 / 20° |
| 51 | Combo[1] | 110200210 / 220 / 230 | 3.8 / 20° (y `S2147483602(51, 5, 10, 10, 20)`) |

- `EnemyBehaviorParam 102000` (acciones 0..9) y `EnemyBehaviorThirdParam 102000` (20..29) están todas apagadas
  (`+0x1f` = 4). El ESD también usa 20 y 22 como argumento de `S2147483607`, que no llega a `5204` en esos estados
  (**sin confirmar**). 30 y 31 se piden con `5204(30, 0)` / `5204(31, 0)`, pero `FUN_1403a86c0` las descarta como
  ranura (`a − 30 < 2`), así que no son ataques de la tabla (**qué son: sin confirmar**).

### 7.4 Otros params de lógica
- `LogicEzStateSharedParam` (4 bytes por fila, 233 filas): valores que el ESD lee con `f5(fila)` y `f5101()`.
- `LogicComParam` (0x80 bytes, 347 filas; la fila 102010 es la del superior). **Lector no trazado**: los valores de
  102010 son floats que parecen radios y ángulos de percepción (`+0x04` 20, `+0x08` 13, `+0x10` 1.047 rad = 60°,
  `+0x14` 0.785 rad = 45°, `+0x18` 70, `+0x1c` 50, …), pero **qué es cada campo no está confirmado**.
- `EnemyAttackMoveParam` (0x38 bytes, filas 0..122): giro durante el ataque, `FUN_1403a28b0` (§8.5). `+0x08 / +0x0c`
  velocidades de giro (rad/s), `+0x10 / +0x14` tramo de la interpolación, `+0x24` multiplicador. Resto **sin lector**.

## 8. De la acción a la animación y al golpe
Salidas: `out\ai\tae_c1020.txt` (todos los eventos del TAE), `out\ai\c1020_anims.txt` (ids y duraciones de los `.nsa`).
Archivos: `gamedata:/timeact/chr/c102000_pl.tae` (cadena `gamedata:/TimeAct/chr/c%06d_pl.tae` en `0x1410c0a00`; 74 016
bytes, 226 animaciones) y `gamedata:/morpheme4/chr/c1020.anibnd.dcx` (235 `.nsa` + `c1020.nmb`).

### 8.1 Lanzar la acción — `FUN_1403a86c0`, `FUN_1403a8fb0`, `FUN_1403a9870`
1. ESD inferior: `5204(a)` (§6).
2. `FUN_1403a86c0` lee la entrada lógica (`chr+0xe8 → +0x18`):
   - canal 0 o 2 (vt `+0x110`) → `FUN_1403a8fb0(a)` (ranura de comportamiento; 30 y 31 se descartan), o `FUN_1403aa020(a)`
     si `chr.b8+0x4c8` bit 2;
   - canal 1 o 3 (vt `+0x118`) → `FUN_1403a9870(c)` (combo `c`);
   - si arranca, escribe `chr.b8+0x10` = `a + 1`, o `c + 0x21` en un combo.
3. `FUN_1403a8fb0` (ranura `a`):
   - sale si la entrada está apagada (`+0x1f == 4`) o si el temporizador de la ranura (`objeto+0x170 + 4a`) es > 0; si no,
     lo carga con `FUN_14039ec80` (**enfriamiento por ranura; de dónde sale el valor no está desglosado**);
   - **golpes**: para `k = 0..2`, `id = entrada[+0x00 + 4k]` (`FUN_14039f620`). Vacía la ranura de ataque `k` del
     personaje (`chr+0x230`, ranuras de 0xc0 en `+0xf08`, `FUN_140373860`) y, si `id ≥ 0`, la llena con ese id de
     `EnemyDamageParam` (`thunk_FUN_141bf1319`) y su poder de ataque (`FUN_1403a79e0`, §8.4);
   - `entrada[+0x0c + 4k]` (`FUN_14039f740`) va a otra tabla (`chr+0xe0 → +0xd8`, `FUN_1406f40ff`). **Qué es: sin
     confirmar** (vacía en el Hollow Soldier);
   - `entrada[+0x20 + 4k]` con `entrada[+0x00 + 4k]` (`FUN_14039ee00`) va a la segunda tabla (0xc4 bytes, `+0x760`,
     `thunk_FUN_141b3e578`), la que usa el evento TAE 2300 (attack.md). Vacía en el Hollow Soldier;
   - copia al objeto de acción `+0x18` = `entrada+0x18` (`FUN_14039eb90`: fila de `EnemyAttackMoveParam`, §8.5) y varios
     campos más (`+0x1c`, `+0x2c`, `+0x30`, `+0x34`, `+0x38`, `+0x3c`, `+0x40`, `+0x44`, `+0x48`, bits de `+0x1e`).
     **Qué hace cada uno: sin confirmar**;
   - `chr.b8+0x284 = ((chr.b8+0x2e8 + 80)·100 + a)·10000 + (chr+0x58 → +0x1c)`. **Para qué: sin confirmar**; no es el id
     de la animación (con modo 0 y `a` = 13 da 8013xxxx).
4. `FUN_1403a9870` (combo `c`) hace lo mismo con la entrada de 0xd8: golpes `+0x08 / +0x0c / +0x10` (`FUN_14039f380`),
   fila de `EnemyAttackMoveParam` de cada paso `+0x3c / +0x40 / +0x44` (`FUN_14039f090`).

### 8.2 Qué animación sale (correspondencia de datos)
- La animación la elige la red Morpheme. **En el exe no se encontró una fórmula.** Pero los nombres de los `.nsa` y los
  params coinciden en todas las variantes de c1020:
  - `.nsa` `a65_NN_MSSS_Attack_NN_…` = id de TAE `65·10⁶ + NN·10⁴ + M·10³ + SSS`;
  - `NN` = ranura de comportamiento (10, 13..18, 20..22) o `+0x00` de la entrada de combo (11, 12);
  - `M` = modo + 1 (el índice de `EP+0x4c` que dio el juego de comportamientos, `f5100`; ver `a00_00_700M_Wep0M_Idle`);
  - `SSS` = 010 / 020 / 030 por paso del combo. Los `…11` son `BladeDa…` y no los usa la 102000.
- Comprobación con las 5 variantes (`EnemyParam` → modo → ranuras encendidas):

| EnemyParam | Modo (`M`) | Ranuras y combos encendidos | Animaciones que existen |
|---|---|---|---|
| 102000 | 0 (1) | 10, 13–17; combos 11, 12 | `65101010`, `6513..65171010`, `651110x0`, `651210x0` |
| 102010, 102011, 102040 | 1 (2) | 10, 13–16, 22; combos 11, 12 | `65132010`..`65162010`, `65222010` (Shot), `651120x0`, `651220x0` |
| 102020 | 2 (3) | 10, 13; combos 11, 12 | `65133010`, `651130x0`, `651230x0` |
| 102030 | 3 (4) | 10, 17, 18; combos 11, 12 | `65174010`, `65184010`, `651140x0`, `651240x0` |

  - Cada ranura encendida tiene su `.nsa` con su `M`, y las apagadas no tienen. **Es correspondencia de datos: el código
    Morpheme que la hace no está trazado.**
  - La ranura 10 no tiene `.nsa` para `M` = 2..4 (solo `65101010`). **Sin confirmar** qué hace en esas variantes.

### 8.3 Ventana de golpe: evento TAE 2200 (confirmado)
- Es el mismo evento que el del jugador (`ChrMorphemeTimeActTrackDamageCtrl`, `FUN_1403269e0`, attack.md):
  `parámetro[0]` = ranura de ataque `k`; `FUN_140373c50` solo crea el golpe si la ranura tiene id ≠ 0.
- Por tanto **el golpe `k` usa `entrada[+0x00 + 4k]`** (combo: `+0x08 + 4k`). En el TAE, cada paso de un combo lleva su
  `k` (0, 1, 2) y la 65161010 lleva dos golpes con `k` = 0 y uno con `k` = 1.
- Hollow Soldier 102000 (tiempos del TAE; duración del `.nsa +0x28`):

| Acción | Animación | Duración (s) | 2200 (s) | Fila EDP |
|---|---|---|---|---|
| 10 | 65101010 | 2.667 | 0.933–1.100 (`k` 0) | **ninguna** (`+0x00` = −1: no crea golpe) |
| 13 | 65131010 | 3.000 | 0.867–1.033 | 110200310 |
| 14 | 65141010 | 2.667 | 0.900–1.033 | 110200410 |
| 15 | 65151010 | 4.333 | 1.400–1.533 | 110200510 |
| 16 | 65161010 | 4.667 | 0.867–1.033 y 1.600–1.767 (`k` 0); 2.267–2.400 (`k` 1) | 110200610; 110200620 |
| 17 | 65171010 | 4.333 | 1.400–1.533 | 110200710 |
| 50 (combo 11) | 65111010 / 020 / 030 | 2.167 / 2.167 / 2.667 | 0.533–0.700 en los tres (`k` 0 / 1 / 2) | 110200110 / 120 / 130 |
| 51 (combo 12) | 65121010 / 020 / 030 | 2.333 / 2.333 / 3.000 | 0.533–0.700 / 0.533–0.700 / 0.867–1.033 | 110200210 / 220 / 230 |

- Cómo pasa el combo de un paso al siguiente (el índice `objeto+0x10` < 4 que lee `FUN_1403a7c80`): **sin confirmar**.
- Otros eventos del TAE de c1020, sin significado buscado: 280100 (203 veces), 110000 (46), 120200 (44), 290002,
  280000, 111400, 2600, 2300 (en 87002 y los Shot), 2105, 2501, 1220.

### 8.4 Volumen del golpe — `FUN_140316a30` (confirmado)
- Al crear el golpe, `FUN_140316a30` lee **la fila del golpe** (`thunk_FUN_141b0fe63(id)`; en el enemigo, la fila EDP.
  PDP, EDP y SDP comparten disposición, casting.md §3):
  - `+0x30` = 0 → dummy `+0x31` del **modelo del personaje** (`FUN_140312750`, `chr+0xf0`, búsqueda por id en el FLVER);
    `+0x30` = 1 → dummy `+0x31` de una parte equipada (`FUN_140312ce0`, `chr+0x378`); otro valor → la matriz del personaje;
  - `+0x33` = 1 cápsula, 0 esfera; radio = `+0x34 · ranura+0x08` (1.0); largo = `+0x38 · ranura+0x0c` (1.0);
  - `+0x3c` y los bits 2..5 de `+0x40` también pasan al golpe (**uso sin confirmar**).
- El golpe se registra en el gestor de ataques (`FUN_1403186a0` → `FUN_1403b09d0`), el mismo de los bullets
  (casting.md §7.4). El segundo extremo de la cápsula va a `(0, 0, −largo)` en el espacio de la dummy; **el eje exacto
  sin confirmar**.
- Hollow Soldier (`c1020.flv`, `flver_dummy.py`):

| Filas | Forma | Dummy | Hueso padre | Dummy: posición / adelante |
|---|---|---|---|---|
| 110200110..130, 210..230, 310, 510, 610, 620, 710 | cápsula r 0.4, largo 1.8 | 120 | `R_weapon` (46) | (0.0004, −0.0814, 0.0023) / (0, −1, 0) |
| 110200410 (acción 14) | cápsula r 0.5, largo 0.4 | 130 | `L_shield` (21) | (−0.0208, −0.4046, −0.1199) / (0, 1, 0) |
| 110200010 (ninguna acción de 102000) | esfera r 0.5 | 1 | — | — |

  - La dummy 121 (`R_weapon`, y −1.351) marca la punta, así que 120 + 1.8 cubre la espada. **Que el eje de la cápsula sea
    el "adelante" de la dummy no está confirmado.**
- Poder de ataque del golpe (`FUN_1403a79e0` → `FUN_1403a9f80`): los 13 valores de la fila (mismo reparto que
  `FUN_140170710`, enemies.md §4.1) × `EP+0xd8` × `chr.b8+0x420`, más los SpEffects (`vt+0x130`). Deja `+0x78` = 1.0.

### 8.5 Giro del enemigo — `FUN_1403a28b0` (`EnemyMovementCtrl`, vtable `0x1410e6978` [13], confirmado)
Resultado en rad/s (la rama de `chr.c0+0xf4` bit 3 convierte `chr.b8+0x6d0` con π/180, `DAT_1410ac9f0`).
- **Fuera de un ataque** (`chr.b8+0x7e4` = 0 y `chr.c0+0x2b` bit 1 = 0):
  - normal: `lerp(EMP+0x2c, EMP+0x30, clamp(v / EMP+0x20))`. `v` es el argumento; que sea la velocidad sale de que
    `FUN_1403a2770` compara `objeto+0x120` con `EMP+0x20` (**sin confirmar**);
  - estado `objeto+0xb0 == 3`: `lerp(EMP+0x54, EMP+0x58, clamp(v / EMP+0x48))`;
  - con `objeto+0x124`: `EMP+0x78` (estado 3: `lerp(+0x9c, +0xa0)` entre `+0x84` y `+0xa8`);
  - todo × `FUN_14031c790` = producto de `chr.b8+0x138..+0x14c` (**valores sin leer**).
- **Durante un ataque**: fila de **`EnemyAttackMoveParam`** (slot `+0x720`, `FUN_140358860`) = `objeto+0x18` (ranura) o
  `objeto+0x18 + 4·paso` (combo), `FUN_1403a7c80`:
  - con `chr.b8+0x4ec` ≠ 0: `lerp(+0x08, +0x0c)` según `objeto+0x120` entre `+0x10` y `+0x14`;
  - con `chr.b8+0x4f0` ≠ 0: lo mismo × `+0x24`;
  - sin ninguno de los dos: **0, no gira**. Quién pone `+0x4ec` / `+0x4f0`: **sin confirmar** (probablemente la animación).
- Hollow Soldier:
  - `EnemyMoveParam 102000`: `+0x20` 3.2, `+0x2c` 4.538, `+0x30` 4.887 → **260°/s quieto, 280°/s a 3.2**; `+0x78` 4.712
    (270°/s). Corrige enemies.md §8, que tomaba `+0x34 / +0x38` como velocidades: esta función no los lee.
  - Filas de ataque: ranura 10 → 41, 13 → 40, 14 → 34, 15 → 40, 16 → 8, 17 → 40; combo 11 → 40 en cada paso, 12 → 34.

| `EnemyAttackMoveParam` | `+0x08` → `+0x0c` (rad/s) | `+0x10` … `+0x14` | `+0x24` |
|---|---|---|---|
| 8 | 2.094 → 1.047 (120 → 60°/s) | 1.0 … 2.0 | 1.35 |
| 34 | 1.571 → 1.047 (90 → 60°/s) | 1.0 … 2.0 | 1.35 |
| 40 | 3.142 → 2.094 (180 → 120°/s) | 1.0 … 2.0 | 1.35 |
| 41 | 3.142 → 2.094 | 1.0 … 2.0 | 2.25 |

### 8.6 Animaciones de daño, guardia y muerte de c1020
- Ninguna tiene eventos en `c102000_pl.tae`. Duración (`.nsa +0x28`):

| Id | Nombre (`.nsa`) | s |
|---|---|---|
| 160015 / 160016 / 160025 | DmgKnockBackLight | 1.000 |
| 160055 / 160056 / 160065 | DmgKnockBackHeavy | 1.000 |
| 160115 / 116 / 125 / 155 / 156 / 165 | DmgExtraHeavySlash | 3.000 |
| 160210 / 160220 | DmgBlowAway FaceDown / FaceUp | 1.000 |
| 160610 / 160630 | DmgFall FaceDown / FaceUp (inicio) | 0.667 |
| 160710 / 720 / 730 | DmgDown FaceDown inicio / bucle / fin | 0.667 / 3.000 / 2.000 |
| 160740 / 750 / 760 | DmgDown FaceUp inicio / bucle / fin | 0.667 / 3.000 / 2.000 |
| 161010 / 020 / 030 | DmgParryBreak inicio / bucle / fin | 1.667 / 1.333 / 2.000 |
| 161110 / 120 / 130 | DmgBleed inicio / bucle / fin | 0.500 / 1.333 / 0.833 |
| 161230 | DmgStagger_B | 1.000 |
| 16700M | WepM HitBack_A | 1.000 |
| 9700M / 9701M / 9702M | WepM Guard / Guard_HitLight / Guard_HitHeavy | 2.000 / 1.000 / 1.000 |
| 170010 / 170020 | DieNormalSlash_F / _B | 5.333 |
| 79020010..50 / 79030010 / 79040010 | CatchBackStab… / CatchParryBreak / CatchGuardBreak | 4.0–4.667 / 3.0 / 3.5 |

- La red (`c1020.nmb`) tiene los parámetros `DmgCategory`, `DmgDirection`, `DmgDuration`, `DieCategory`, `GuardStance`
  y `GuardCategory`, y los estados `BT_Damage|DamageFullBody`, `SM_HitBack`, `SM_Die` y `Die_A/B/C`.
  **Qué valor de `DmgCategory` (enemies.md §7.3) lleva a cada animación: sin confirmar** (hay que decodificar las
  transiciones de la red).

### 8.7 Guardia y estamina del enemigo
- **Regeneración** (`FUN_140333900`, genérica sobre `chr+0x38`): `estamina += FUN_140333880() · EP+0x30 · dt` si
  `chr.b8+0x558` == 0. **Esto da lector a `EP+0x30`**: Hollow Soldier **125 por s** (máximo `EP+0x2c` 1240 → ~10 s de
  0 a lleno). Con `chr.b8+0x500` ≠ 0 y no jugador suma `EP+0x2c` de una vez. Cada gasto pone un retardo de 0.1 s
  (stamina.md). Es el mismo controlador que el del jugador; **que el tick corra igual en el enemigo no está verificado
  en el juego**.
- **Bloqueo**: la IA solo empuja entradas lógicas. `cmd 5206(a, b)` pone `ChrAi+0x350 |= 0x40` (pulsación 9) con
  `a ≠ 0` y `b ≠ 0`. El inferior del Hollow Soldier la usa (`5206(1, 0)`, `5206(1, 1)`, `5:5206(f5004() != 0, 0, 0)`).
  **Que esa pulsación sea la guardia, y qué hace la red con ella (`GuardStance`, `MoveGuard`), sin confirmar.** Las
  animaciones 9700M no tienen eventos de TAE.
- **Daño que entra bloqueando**: igual que enemies.md §7.3 y stamina.md "Guardia" (`EP+0x78..` = 80 %, estabilidad
  `EP+0x74` = 50); el uso exacto de las tasas en `FUN_140138d50` sigue **sin releer**.

## 9. Cómo está escrito un enemigo (Hollow Soldier)
### 9.1 Superior `ai102010` (`out\ai\ai102010.txt`)
- Grupo 10 (raíz): espera `f5621()` y `f3() ≥ 1`; si `f5900(7) == 66` entra en `S2147483631`, si no en
  `S2147483634` (variante de pelea).
- `S2147483634` (27 estados) es el bucle de decisión:
  - pide un mapa del inferior con `5301(10/20/…/60)` y mira `f5901()` hasta que lo confirma (si no existe, `cmd 90`);
  - le da órdenes al inferior escribiendo `5300(0, 15)`, `5300(0, 16)`;
  - cambia de rama por percepción (`f5903`, `f5201`, `f5700(1)`, `f5705`, `f7020`, `f7310`, `f7320`, `f7091`, `f7092`)
    y por tiempo (`f3() ≥ 3`, `f5908()`).
- `S2147483635`: elige el modo de arma `5210(0..3)` y el mapa `5301(10..60)` del inferior según `f5900(1)` / `f5900(5)`.
- `S2147483633`: busca con giros (`5905`, `5906`) y tiempos al azar (`f4(2.2, 3.3)`, `f4(5, 10)`).

### 9.2 Inferior `ai102000` (`out\ai\ai102000.txt`)
- **Mapas de estados** 10, 20, 30, 40, 50, 60: uno por modo de pelea, los que pide el superior. Leen `f5900(0)` (orden del
  superior: 10, 11, 12, 13, 14, 17, 18, 20…) y llaman al grupo de esa conducta.
- Grupos 100..116, 200..250, 310..410: conductas concretas (patrulla, búsqueda, volver, girar, ataque desde emboscada).
- Grupos `2147483573..2147483640`: subrutinas con argumentos. La que lanza un ataque de la tabla es
  **`S2147483595(acción, alcance, ángulo, mov, x)`**:
  1. al entrar: `5400(1)`, `5420()`, `5232()`, temporizador `cmd 2(0)`;
  2. si `f7001() < alcance`, `f7002() < ángulo` y `f5000()` → `5204(acción, 0)` y espera a que `f5001()` deje de
     valer 1 → devuelve 0;
  3. dentro del alcance pero fuera del ángulo (o `f7002() ≥ 90`): gira (`5903`) hasta 1.5 s (`f3()`);
  4. fuera del alcance: camina (`5505(…, mov, mov, …)`) si está a menos de `alcance + 1.5`, si no corre (`5535`);
     si `f5620()`, sigue la navmesh (`5800`, `5506`, `5536`);
  5. se rinde si `f2(0) ≥ 3` s caminando o `≥ 10` s corriendo, o si la navmesh falla → devuelve 1.
  - `x` no se lee en los estados de `S2147483595` que se ven arriba (**sin confirmar** para qué está).
  - `S2147483601(acción, ?, d1, d2, ángulo, ?, ?, d)` es parecida con `f7018(0, d)` y `f7002() ≥ ángulo`.
  - Los nombres de los argumentos salen de cómo se usan; **ningún param los describe.**
- **La tabla de ataques** (`S2147483585`, llamada desde el bucle de pelea `S2147483586`):
  1. al entrar: `cmd 1(0, 0, 20)` → `r0 = RandInt(0, 20)`; `cmd 1(1, 0, 2)`;
  2. `f7001() < 3.5` → estado de cerca; si no, de lejos;
  3. de cerca, en este orden: modo 0 y `f5102(0)` → acción 10; `f7002() ≥ 110`, `0 < f7001() < 2.8` y `f5018()` →
     `S2147483597(14)`; `f7002() ≥ 110` y `0 < f7001() < 3` → 13; `f5204()` y `f5050(17)` → `S2147483602(51, …)`;
     `0 < r0 < 10` → 14; `0 < r0 < 15` → 13; `f5004()` → 50; si no → 51;
  4. de lejos: modo 0 y `f5102(0)` → 10; `0 < r0 < 10` y `f7001() < 6` → `S2147483602(51, …)`; `< 17` → 50 si
     `f5004()`, si no 51; `< 18` → 16; `< 19` → 15; `< 20` → 17; si no → 13;
  5. cada rama llama a `S2147483595` con el alcance y el ángulo de la tabla de §7.3.
  - Como las condiciones son `0 < r0 < N` en cascada, el peso de cada rama es el ancho de su intervalo (`r0 = 0` cae al
    final). Es lo que se lee del ESD; DS2 no lo llama "peso".
- En el mismo bucle hay entradas sin azar: `f5103(0)` + `f5009(5)` → esquiva / retroceso (`S2147483617`), `f5202()`
  → `S2147483632(1)`, `f5070()` → `S2147483598(4, 6, 4, 6, 9, 12, -1, 20)`.
- **Enfriamiento**: no hay un campo de enfriamiento. Lo que hace de pausa son los tiempos de estado (`f3() ≥ x`,
  `f4(a, b)`) y los temporizadores `cmd 2` / `f2(i)` de cada grupo.

## 10. Qué corresponde a qué, para la API del mod
Esto es solo la correspondencia con DS2, sin diseño nuevo:
- "Ataque" = acción `a` → entrada de `EnemyBehavior*Param` / `EnemyComboBehaviorParam` con sus filas EDP.
- "Tabla de ataques" = `RandInt(0..N)` + umbrales, con reglas de distancia (`f7001`, `f7018(min, max)`) y ángulo
  (`f7002`) antes del azar, y alcance / ángulo propios de cada ataque (argumentos de la subrutina de ataque).
- "Condiciones" = funciones del motor (§4, §5): distancia, ángulo, altura, tiempo en el estado, variables lógicas,
  estado del objetivo.
- "Combos" = entradas de `EnemyComboBehaviorParam` (una petición, varios golpes).
- "Fases" o conductas = mapas de estados del inferior, elegidos por el superior con `5301` / `f5901` y órdenes por
  `5300` / `f5900`.
- Jefes: usan el mismo esquema (`ai625000` / `ai625010` Ornstein, `ai6xxxxx`). **No hay otro lenguaje de script para
  ellos.**

## Sin confirmar / pendiente
- Código del banco 5 (orden condicional) y de los opcodes `0xB9` / `0xBA`; solo se ve el uso.
- Significado de la mayoría de las funciones 50xx..52xx, 56xx, 59xx y 7080..7801, y de las órdenes de movimiento y giro
  (§5.3, última fila).
- Manejador de las funciones 6000..6007 y 7300..7801 (no están en los switches de `ChrEzStateCtrl` ni en el de
  `SearchEzStateCtrl`).
- Lector de `EnemyDefaultLogicParam` (y su `+0xC`) y de `LogicComParam`.
- Campos de `EnemyBehavior*Param` `+0x0c..+0x14`, `+0x1c`, `+0x1e`, `+0x20..+0x28` y `+0x2c..+0x48`;
  `EnemyComboBehaviorParam +0x00` y `+0xcc`; fuente del temporizador por ranura (`FUN_14039ec80`).
- Acción → animación: la regla `65·NN·M·SSS` de §8.2 es correspondencia de datos; el código Morpheme no está trazado.
- Paso de un combo al siguiente; para qué es `chr.b8+0x284`.
- Eje de la cápsula de golpe respecto a la dummy; uso de `+0x3c` y `+0x40` de la fila del golpe.
- Quién pone `chr.b8+0x4ec` / `+0x4f0` (giro en ataque) y los multiplicadores `chr.b8+0x138..+0x14c` del giro normal.
- Qué `DmgCategory` lleva a cada animación de daño de c1020; si la pulsación 9 (`cmd 5206`) es la guardia.
- Significado del resto de eventos del TAE de c1020 (280100, 110000, 120200, …).
- Acciones 30, 31, 60, 61, 62 y los 20 / 22 del Hollow Soldier; qué hace la ranura 10 (sin golpe) en las variantes
  sin animación `6510M010`.
- `ai703370` y `ai803370` no se pudieron leer con `esd.py`.
