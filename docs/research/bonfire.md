# Hoguera: descansar, punto de reaparición y animaciones

Fuentes: Ghidra (`DarkSoulsII.exe`), `SpEffectWideUse.emevd` / `SpEffectMustSub.emevd` de la regulation, `c0001.nmb`,
`c000100_pl.tae` y textos `DetailedExplanation.fmg` / `iconHelp.fmg`. Salidas en `D:\Reversing\DS2\out\bf_*`.

## Cadena al interactuar
- Despachador de acciones de objeto `FUN_140451e50`: el caso `0xe` → `FUN_140452790` → `FUN_1401cb950`
  (`MapObjBonfireComponent`).
- `FUN_1401cb950`: si el que interactúa es el jugador (`DAT_1416148f0+0xd0`) y `FUN_1401cb3d0(hoguera)` lo permite,
  llama `FUN_14017dc40(EventBonfireManager, idHoguera)`. Si no, envía el mensaje `0x453` con la posición de la hoguera.
- `EventBonfireManager` (ctor `FUN_14017daa0`, en `eventMgr+0x58`; `eventMgr = DAT_1416148f0+0x70`).
  `FUN_14017dc40` solo arranca si no hay sesión activa (`+0x38 == 0`), y deja el estado en 1 y llama `FUN_14017f610`.

## Qué hace al sentarse — `FUN_14017f610`
- Marca en `chr+0xc8`: `+0xfc |= 0x1000` y `+0xec = referencia de la hoguera` (`FUN_14017bc20`).
- **Aplica el SpEffect `110000010`** a través de `FUN_1402027b0(chr)` (struct `{0x68E778A, 1, -1.0f, …}` → `chr vt+0x130`).
- Activa el event flag `106020` (`FUN_140452240(0x19e24, 1)`).
- `chr+0xd8` bit 0 = 1 (se limpia al salir) y `FUN_140423050(DAT_1416148f0+0x28, 1)` (contador +1; uso sin identificar).
- `FUN_140198680(…, idHoguera)` → `FUN_140199e40(…, 10)` (modo 10 de un objeto de `+0x22e0`; sin identificar).

> **Regla del usuario (2026-11-09): descansar en la hoguera no repara la armadura ni las armas** (rompe la reparación
> de Minecraft): `100110[02]` no se aplica. Sí se aplican los usos de hechizos y quitar veneno / fuego.

## SpEffect 110000010 (`SpEffectWideUse.emevd`)
Los comandos se resuelven con la tabla `{banco, id, handler}` en `0x1415711e0` y los grupos del gestor de SpEffect
(ctor `FUN_1402317f0`). Los bancos `100050[01]` / `100070[00]` (`FUN_14022a730` / `FUN_1402295a0`) solo llaman al
evento indicado en `SpEffectMustSub.emevd`.

| Comando | Efecto (código) | Confirmación |
|---|---|---|
| `100050[01] (1, 851, 100.0, 0, 1)` → evento 851 = `101050[01]` | Curación del grupo `HealSub` (`+0x58`), `caseD_1a` `14022b310`: `cantidad = X+0x174 × (100.0 / 100.0) × modificadores`, duración 0 | El campo `+0x174` se lee de `[[owner+0x228]]` (`FUN_14016a470`). Es el 4.º float del bloque de vida `+0x168..+0x174`. **No está confirmado que sea la vida máxima.** |
| `100060[00] (100.0, 1)` | Grupo `Spell` (`+0x18`), `caseD_6` `14022cf60`: `valor = 100.0 × factor` | Lo usan Amber Herb (35), Twilight Herb (50), Rouge Water (50)… El texto 60040000 dice "Restores a small number of spell uses". Por lo tanto: **restaura el 100 % de los usos de hechizos**. |
| `100110[02]` | `FUN_140230d70`: por cada objeto equipado cuyo valor actual (`+0x20` f32) sea > 0, lo fija al máximo de su param. Categorías 3/4 = `WeaponParam +0x28` (Broadsword 60.0), 5 = param del slot `+0x4a0` `+0x38`, 6 = slot `+0x530` `+0x04` | El texto 909324 dice: "Durability is restored by resting at a bonfire, but a broken item must be repaired first". → **repara la durabilidad, excepto lo roto (valor 0)**. Qué params exactos son los slots `+0x4a0` / `+0x530`: sin verificar. |
| `100070[00] (1, 871, k)` × 12 → evento 871 = `101070[01]` | `caseD_1d` `140229d30`: busca `k` en la tabla `0x1415710c0` y da por terminada la entrada del grupo `AbnormalStateSub` (`+0x60`, `FUN_140222ec0`, bit `0x10`) | Con k = 0..11 limpia: 0 毒 veneno, 1 出血 sangrado, 2 武器破壊 rotura de arma, 3 呪 maldición, 4 燃焼 quemadura, 5 スペル封印 sello de hechizos, 6 水濡れ mojado, 7 血濡れ ensangrentado, 8 猛毒 veneno tóxico, 9 油濡れ aceite, 10 石化 petrificación, 11 毒濡れ empapado de veneno. **No** limpia 12–15 (煤け, 焦げ, 雪濡れ, 水濡れ:即乾燥). |
| `100090[08]` (sin args) | Entrada en el grupo `Status` (`+0x28`), `caseD_c` `14022d1e0` | **Efecto sin identificar.** |
| `1000[01] (i, 3.0f, 100.0)`, i = 0..6 | `キャラステータス倍率設定` (ctor `FUN_14021bd70`, `SpEffectActionImpl_ChrStatus_Rate`): `obj+0x3bc+i·4 *= 100.0` | Qué representan los índices 0..6: **sin identificar** (los índices 7 y 8 serían `+0x3d8` / `+0x3dc`, los multiplicadores de vida/estamina de stats.md). |

- El SpEffect `100000020` hace casi lo mismo (sin `100090[08]` ni `1000[01]`). No se sabe quién lo aplica.
- `FUN_1402027b0` también se llama desde `FUN_140419610` (creación del jugador al cargar, si se cumple una condición de
  reaparición) y desde `FUN_1402b8ee0` (estado 5 de un objeto de red). Es decir, el mismo SpEffect 110000010 se aplica
  al reaparecer.

## Tras la espera — `FUN_14017ed90` (actualización del manager, desde `FUN_1404503e0`)
- Estado 1 → 2 cuando el temporizador `+0x48` llega a `+0x4c` = **2.9 s** (`0x4039999a`, ctor). Entonces `FUN_14017fd70`:
  - `FUN_140417210(DAT_1416148f0+0x40)`: recorre los personajes del gestor y llama `FUN_140419460` / `FUN_140316010`.
    Probablemente regenera enemigos, pero **no está confirmado**.
  - `FUN_1403c1b50` → `FUN_1403c27f0(0, 0)`: recorre los objetos de mapa de tipo `+0xa2 == 4`. Efecto sin identificar.
  - `FUN_14044f880(eventMgr)`: limpia listas; `FUN_140474730` actúa sobre los flags cuyo id cumple `% 100 == 2`.
    Efecto sin identificar.
- Estado 2 = menú. Subestados 1–4 con esperas `+0x50` / `+0x54` / `+0x58` / `+0x5c` = **2.0 s** y salida tras
  `1.0 s` (`DAT_1410ac698`) o 2.9 s (`0x1410c2d48`):
  - 1: quemar Bonfire Ascetic → `FUN_14017ffa0`: intensidad `+1`, con tope `FUN_14017f280` (≤ 99).
  - 2: `FUN_14017f310` / `FUN_14017f980`.
  - 3: `FUN_14017f420` / `FUN_14017fae0` (`inventario.FUN_1401ac5a0(cat 1, n+1)`).
  - 4: viajar → `FUN_14017fdb0`.
- Estado 3 (levantarse): `chr+0xc8+0xfc |= 0x2000` y se limpian `chr+0xd8` bit 0 y el contador de `+0x28`.

## Punto de reaparición
- Registro en `eventMgr+0x164` (mapa), `+0x168` (tipo) y `+0x16c` (id de hoguera). Se escribe en `FUN_14044fe30`.
  - Se llama desde `FUN_1401cb950` al interactuar con la hoguera, con `{mapa de la hoguera, 0, idHoguera}`.
    Se escribe tras ambas ramas (también si no se pudo descansar). Solo para el jugador y si
    `DAT_1416148f0+0x22f0 == 0 || FUN_1405135f0() == 0`.
  - También se llama desde `FUN_14017fdb0` al viajar.
  - Valor por defecto (`FUN_14044f910`, nuevo ciclo): `m10_02_00_00`, tipo 2. Esa función también apaga todas las
    hogueras (`FUN_14017ead0`).
- `FUN_14044ed40` convierte el registro en un destino de warp: tipo 0 → `{3, …, mapa, …, id}`; tipo 1 → 4; tipo 2 → solo
  el mapa. `FUN_14044fde0` lo envía con `DAT_1416148f0 vt+0x40`. Lo llaman las funciones virtuales `FUN_140190920` y
  `FUN_140190950`.
- Textos: 60350000 Homeward Bone, 60355000 Aged Feather y 60360000 Darksign ("Return to last bonfire rested at"), y
  `common` 30000001.
- **No se trazó** qué vía exacta usa la muerte para llegar a `FUN_14044fde0`.

## Animaciones (`c0001.nmb`, anibnd `c0001`, TAE `c000100_pl.tae`)
| Archivo `.nsa` | TAE | Duración `.nsa+0x28` | Eventos TAE (tipo, inicio–fin s) |
|---|---|---|---|
| `a76_01_0010_BonfireStart` | 76010010 | 2.1667 | 110100 / 111400 0–2.667, 2202 0–1.0 |
| `a76_01_0020_BonfireLoop` | 76010020 | 2.0 | 110100 / 111400 0–2.333 |
| `a76_01_0030_BonfireEnd` | 76010030 | 1.8333 | 110100 / 111400 0–2.5 |
| `a76_01_0040_BonfireMake` (encender) | 76010040 | 3.6667 | 210000 0.9–1.067 |
| `a76_01_0050_BonfirePour` | 76010050 | 5.5 | 110100 / 111400 0–5.833 |
| `a76_01_0060_BonfirePray` | 76010060 | 5.5 | 110100 / 111400 0–5.833 |
| `a70_07_0020_TorchMakeBonfire` | 70070020 | 3.6667 | 210100 / 210000 1.333–1.5 |

- Red de Morpheme: estado `StateMachine_Main|BT_Bonfire`; parámetros `ControlParameters|IsBonfire` y `|BonfireCategory`
  (`FUN_1403603c0`); requests `Bonfire` y `BonfireMenu` (`FUN_140360540`); duraciones `BonfireDuration` y
  `BonfireMenuDuration` en el nmb.
- **Sin trazar**: qué valor de `BonfireCategory` elige cada clip, y quién traduce `chr+0xc8+0xfc` `0x1000` / `0x2000` a
  esos requests. `ChrEventKindleBonfireActionCtrl` (vtable `0x1410bf628`) existe, pero no se analizó.

## Hueso de regreso (Homeward Bone, ItemParam 60350000)

Pedido del usuario (2026-11-10). Investigación de un agente de solo lectura (2026-11-10); archivos en
`D:\Reversing\DS2\out\homeward\` (`h1.c`..`h6.c`, `x1.txt`, `x2.txt`, `texts.json`, icono).

### ItemParam 60350000 (comparado con la Pluma envejecida 60355000 y el Signo oscuro 60360000)
| Campo | Hueso | Pluma | Signo oscuro |
|---|---|---|---|
| `+0x08/+0x0c/+0x10` modelos | 0 / 863 / 0 | -1 / 897 / -1 | 0 / 864 / 0 |
| `+0x38` velocidad de uso | 0.7 | 0.7 | 0.7 |
| `+0x40` ItemTypeParam | **250** | 250 | 260 |
| `+0x44` ItemUsageParam | **1800** (`+5` bit 0 = 0: **no en escalera**) | 2210 | 2200 |
| `+0x4a` máximo | **99** | 1 | 1 |
| `+0x52` | 0x0c (bit 0 apagado: **se gasta**, por datos) | 0x0d (reutilizable) | 0x0d |

### Animación: `a00_18_0080 ItemUsePray` (TAE 180080)
- ItemTypeParam 250 / 260 → `+0x10` = **210**. No existe `a00_18_0210`: el selector del nmb (nodo 0x177b, CP 165
  `ItemUseCategory`, pesos {200, 210, 220, 230, 240, 250} en `0x4ea478`) manda 210 al nodo 0x1775 =
  `a00_18_0080_ItemUsePray_untit_6ab531d9.nsa`, **8.0 s** (`out\plunge\grip_nodes.py`).
- TAE: 220200 modelo 0.167–2.167 (dummy 26) y 2.167–2.333 (dummies 9 / 249, modelo 863); **220000 a 2.333 s**: aplica
  el SpEffect 60350000.
- nmb: `CancelControl_Avoid` / `_AttackAll` / `_Guard` 0–**7.2 s**; **sin `CancelControl_Move`** (se puede caminar).
  Exportado como `weapons.json → homeward_bone` (`item_use(180080)`), clip `ds2_180080`.

### Efecto (`SpEffectActiveItem.emevd`)
| SpEffect | Instrucciones |
|---|---|
| 60350000 (hueso) | `100130[00] (0, -1, -1)` + `1000[03] (24, 5.0)` |
| 60355000 (pluma) | igual |
| 60360000 (signo oscuro) | igual + `100130[01] (1, 931, 1, 100)` (pierde las almas) |

- El hueso **conserva las almas**.
- `100130[00]` → `0x14022c8c0` (filtro `FUN_14022d7e0`: byte de contexto `+0x50` ∈ {2, 5, 6}, sin identificar): quita
  el SpEffect 754 y crea una entrada `SpEffectEach_Special` (grupo `SpEffectGroup_Special`).
- El viaje: `FUN_14044fde0` (destino del registro de arriba, "Punto de reaparición") lo llaman solo `FUN_140190920` /
  `FUN_140190950` desde el stub `0x14018ffcf`, que no tiene referencias (código ofuscado). **El enlace entrada
  Special → viaje no está trazado**; que el hueso vaya al registro lo dicen el texto y ese destino.
- Sin hoguera de descanso: el registro por defecto es `m10_02_00_00` tipo 2 (mapa inicial, sin hoguera).

### Textos
- es (NeutralSpanish = Spanish): "Hueso de regreso" / "Regresa a la última hoguera en la que descansaste." Diálogo
  `common` 30000001: "¿Volver a la última hoguera?".
- en: "Homeward Bone" / "Return to last bonfire rested at".

### Port al mod (`SoulsHomeward`, 0.5.0)
- Animación y tiempos de DS2; a los 2.333 s gasta un hueso y lleva al jugador a la última hoguera donde descansó o a la
  que viajó (`soulsmode:last_bonfire`, escrito donde DS2 escribe el registro), de pie y mirando al fuego, como el viaje.
  No funciona en una escalera. Conserva las almas (la experiencia).
- Sin hoguera de descanso, o si la hoguera ya no está o está apagada: **spawn del mundo** (decisión del usuario,
  2026-11-10).
- Receta (usuario): un hueso y cualquier alma (`#soulsmode:souls`).

### Diálogo, invulnerabilidad y restricciones (segunda investigación, 2026-11-10; `out\homeward2\`)
- **Diálogo** (`ItemUseCheckDialogParam`, cargado por `FUN_14048c770`; filas de 0x14 bytes: `u8 cond[4]`, `u32 msg[4]`):
  `FUN_140500c40` (desde `ChrItemCtrl` al apretar usar) toma la primera condición que se cumple (0 nunca, **1 siempre**,
  2..4 estados de red) y pregunta `msg[i]` (`common.fmg`) con **SÍ / NO** (`common` 200 / 201) **antes** de la
  animación; **el cursor empieza en NO** (7.º argumento 0 → la respuesta que no usa el objeto). Solo SÍ pone el flag
  0x800 que arranca el uso; NO o cancelar no gastan nada. El hueso (y la Pluma) preguntan 30000001 "¿Volver a la
  última hoguera?"; las almas de gigante y de jefe (50920000, 64xxxxxx) 30000010 "¿Usar %s?". 57 filas en total.
- **`1000[03] (24, 5.0)` = 5 s sin recibir daño**: el único lector del bit 24 de `b8+0x4b8` es `FUN_14016d830`, usado por
  `FUN_14013a620` (`vt+0x48` de los `DamageActionCtrl`): con el bit no se aplican PS, estamina, estabilidad ni estados
  (salvo PDP `+0x65` bit 1 o golpe `+0x83`). 5.0 es la duración en segundos (`FUN_14021ded0` / `FUN_14021e060` le
  restan dt). Lo mismo usa el milagro Repeler (34080000, 1.75 s).
- **Restricciones**: solo la escalera (ItemUsageParam `+5` bit 0). No hay chequeo de jefe ni de mapa en lo trazado; con
  sesión en línea y `DAT_1410c0050[idx].byte1 ≠ 0` no viaja localmente (`FUN_1402c9220`), sin identificar qué rol es.
  Ningún mensaje de "no se puede usar" en este camino.
- **En el mod**: diálogo SÍ / NO con el cursor en NO (`SoulsUseCheck`, `UseCheckClient`) para el hueso y las almas de
  gigante; 5 s sin daño desde el uso (`SoulsHomeward.isInvulnerable`, en `ALLOW_DAMAGE`).

### Sin confirmar (Hueso de regreso)
- El camino entrada Special → `0x14018ffcf` → `FUN_14044fde0`; qué permite el contexto `+0x50`; si funciona en una pelea
  de jefe, invadido o en línea (rama `DAT_1416148f0+0x22f0`).
- `1000[03] (24, 5.0)`: qué hace el bit 24 de `b8+0x4b8` y si 5.0 es una duración.
- Fundido, tiempo de carga y si llega sentado o de pie.
- Bits de ItemUsageParam 1800 salvo `+5` bit 0; byte 0 de `ItemUseCheckDialogParam`; quién aplica el SpEffect 754.
- Que se gaste (por datos) y cuándo; qué hace `ItemUse_Speed` 0.7 con el clip de 8 s.

## No encontrado
- **Recarga del Estus**: no está en el SpEffect 110000010 ni en la cadena de la hoguera analizada.
  - El texto 60155000 dice "Fill it with Estus at a bonfire" / "replenish at bonfires".
  - `EstusFlaskLvDataParam`: fila n → objeto `60155000+n-1`, valor n (1..20).
  - No se encontró el código que rellena las cargas.
- **Estamina**: no hay un comando explícito.
- **Hollowing / humanidad**: no aparece en esta cadena.
- **Guardado**: no está trazado.
- **Primer encendido** (kindle): no está trazado. Solo se sabe la animación (`a76_01_0040`).

## Implementado (`SoulsBonfire`, `client/bonfire/*`)
- **Hoguera = fogata encendida** (elección del usuario). Se descansa con clic derecho apuntándola o, con la tecla de usar,
  estando a ≤ 2,5 bloques de una (elección: en tercera persona la cámara Souls no apunta a bloques bajos; el alcance
  de DS2 no está rastreado).
- **Al sentarse** (`FUN_14017f610`), de inmediato:
  - se repara todo el equipo puesto (manos y armadura), como `100110[02]`;
  - se quitan veneno y fuego, los estados de 0..11 que tienen equivalente en Minecraft;
  - el punto de reaparición pasa a donde está parado el jugador junto a la hoguera (`FUN_14044fe30`; la posición exacta es elección);
  - animación `a76_01_0010` BonfireStart y luego `a76_01_0020` BonfireLoop; el jugador no se mueve.
- **Menú** a los 2,9 s (`+0x4c`): Subir de nivel, Viajar, Levantarse. Levantarse reproduce `a76_01_0030` BonfireEnd
  (el jugador queda quieto los 1,83 s del clip, elección).
- **Subir de nivel en la hoguera**: elección del usuario (en DS2 lo hace la Guardiana Esmeralda).
- **Viajar** (`FUN_14017fdb0`): lista de hogueras donde el jugador ya descansó (attachment `soulsmode:bonfires`).
  Viajar teletransporta junto a la otra hoguera, mirándola, y la fija como punto de reaparición. Si la fogata ya no existe
  o está apagada, se borra de la lista. Los nombres "Hoguera N (x, y, z)" son propios.
- **No portado**:
  - curación de PS (la cantidad sale de un campo sin confirmar como vida máxima);
  - Estus (no existe aún);
  - usos de hechizos;
  - reaparición de enemigos (sin confirmar);
  - qué pasa en DS2 al llegar de un viaje (sentado o de pie: sin rastrear).

## Activación de hogueras (regla del usuario, 2026-11-02; no es de DS2)
- Hacer una fogata es barato en Minecraft, así que una fogata encendida solo funciona como hoguera (descansar, menú,
  viajar) después de que un jugador gaste **1 nivel de experiencia** en activarla (el primer uso la activa; en
  creativo no cuesta). Activada, sirve para todos los jugadores (`ActiveBonfires`, guardado en el mundo).
- Si el bloque de la fogata desaparece, la activación se pierde; apagarla y volver a encenderla la conserva.
- Viajar a una hoguera no activada no funciona (se quita de la lista como una apagada).
- **Costo creciente (usuario, 2026-11-08)**: cada hoguera que un jugador enciende le cuesta un nivel más que la anterior
  (1, 2, 3…; contador por jugador `soulsmode:bonfires_kindled`, se conserva al morir). Usar una hoguera ya activa sigue
  siendo gratis.
- **Partículas (usuario, 2026-11-08)**: las hogueras activas a menos de 48 bloques de un jugador sueltan chispas
  (`end_rod`) cada medio segundo, para distinguirlas de una fogata a simple vista. Partícula y radio: elección de
  implementación.

## Imagen de cada hoguera en el menú de viaje (diseño del usuario, 2026-11-02, a la manera del menú de viaje de DS2)
- El primer jugador que activa una hoguera (o descansa en una que aún no tiene imagen) saca una captura: el siguiente
  frame del mundo, antes de dibujar el HUD, recortada a 16:9 y reducida a 320 × 180 (`BonfireShotClient`).
- Se sube al servidor y se guarda con el mundo (`<mundo>/soulsmode/bonfire_shots/`, `BonfireShots`); solo se acepta un
  PNG de hasta 512 KB, de una hoguera activa, de un jugador a menos de 8 bloques, y la primera gana.
- El menú de viaje muestra, al lado de la lista, la imagen de la hoguera bajo el ratón (o la primera); el cliente la
  pide al servidor la primera vez. Si la fogata desaparece, su imagen se borra.

## Nombres de hogueras (diseño del usuario, 2026-11-03; no es de DS2)
- Botón "Renombrar" en el menú de la hoguera (`RenameScreen`): campo de hasta 32 caracteres. El servidor
  (`SoulsBonfire.onRename`) exige que el jugador esté descansando ahí con el menú abierto y limpia el texto
  (`BonfireNames.sanitize`: quita códigos de formato `§x` y caracteres de control, recorta espacios, corta a 32; vacío =
  sin nombre). Se guarda en `ActiveBonfires` (por dimensión + posición, compartido) y se borra cuando la fogata
  desaparece (mismo sitio que la activación y la imagen).
- El payload `Open` lleva los nombres en una lista paralela a las hogueras conocidas. El menú de viaje muestra el nombre
  en vez de "#n (x, y, z)" (las coordenadas pasan al tooltip) y el menú de la hoguera lo usa de título.
- Quemar la Llama de la adversidad: ver [llama_adversidad.md](llama_adversidad.md).

