# Estamina

## Recurso
En el chr: `+0x1ac` actual, `+0x1b0` mínimo, `+0x1b4` máximo (floats). El controlador está en `chr+0xe0 → +0xa8`:
`{chr*, f32 +0x08 retardo de regen, f32 +0x0c timer2, f32 +0x10 timer de agotamiento}` (ctor `FUN_1403335b0`).

- **Máximo**: `FUN_14038be90` hace `chr+0x1b4 = chr.b8+0x3dc × estado+0x38`. `estado+0x38` es la derivada `+0x04`
  (Endurance), así que la estamina de [stats.md](stats.md) queda **confirmada como máximo de este recurso**.
  Con el mismo patrón confirma vida = `estado+0x34` → `chr+0x170`.
- **Agotado — `FUN_1403337c0`**: `actual <= 0`.

## Consumir — `FUN_1403335d0(ctrl, coste, tipo)`
1. Si `tipo == 0`: `coste *= chr.b0+200` (multiplicador **sin identificar**; tomado como 1).
2. Si `|coste| > 1.19e-7`: `nuevo = actual - coste`.
   - Si `nuevo <= 0`: `nuevo = clamp(nuevo, ChrParam+0x38, ChrParam+0x34)` y luego se acota a `[mín, máx]`.
     Además `timer2 = ChrCommonParam+0x60`, y si `ChrCommonParam+0x10 > 0`, `timerAgotamiento = ChrCommonParam+0x10`
     (la primera vez suma 1 a `chr.b8+0x5e0`: pose de cansancio, ver "Agotamiento").
   - Si no: acota a `[mín, máx]`.
3. Si `retardo <= 0` y `DAT_14157f380 > 0`: suma 1 a `chr.b8+0x558`. **Siempre** `retardo = DAT_14157f380` (= 0.1, solo lectura).

## Tick — `FUN_1403337e0(ctrl, dt)`
Descuenta los tres timers. Cuando el retardo llega a 0, resta 1 a `+0x558`; cuando el de agotamiento llega a 0, resta 1 a `+0x5e0`.
Luego llama a la regen.

## Regenerar — `FUN_140333900(ctrl, dt)`
Solo si `chr.b8+0x558 == 0`: `actual += FUN_140333880() × ChrParam+0x30 × dt`, acotado a `[mín, máx]`.
`FUN_140333880` = (`chr.b8+0x14 ≠ 0` ? `chr.b8+0x264` : 1) × (vfunc +0x130, por defecto 1) × efecto especial `0x1d`.
Hay además una suma fija de `ChrParam+0x2c` con `chr.b8+0x500 ≠ 0` (StabControl 262), solo para no jugadores; no se portó.

## Drenajes continuos (bloquean la regen mientras están activos: `+0x558` += 1 / −= 1)
Coste por segundo = `ChrParam+0x3c × ChrCommonParam+X × efecto especial`:

| Función | Condición | X | Valor/s |
|---|---|---|---|
| `FUN_140311110` | `chr.c0+0x2a & 2` y estado `chr.b8+8 == 4` | `+0x14` (o `+0x18` si `chr+0x3a0`→vfunc+0x20 ≠ 0) | 200 (232.75) |
| `FUN_140311210` | `chr.c0+0x36` bit 1 | `+0x1c` | 266 |
| `FUN_1403112b0` | `chr.c0+0x28 == 2` | `+0x20` | 0 |

**Qué acción es cada una: sin identificar.**

## Gastos puntuales (identificados por la clase en RTTI)
| Consumidor | Clase | Coste |
|---|---|---|
| `FUN_140330510` (tipo 3) | `ChrJumpCtrl` | calculado al iniciar (`FUN_140330370` → `FUN_1403764a0`) con factor `ChrCommonParam+0x30` (1.2) |
| `FUN_140328d10` (tipos 1/2) | `ChrDodgeCtrl` | `FUN_140328b10` → factor `+0x2c` (1.0); `FUN_140328c20` → factor `+0x28` (0.7) |
| `FUN_140368e70` evento TAE `0x6b` | `ChrEventTrackActionAttack` | consume `chr.b8+0x1ac` (coste de ataque calculado en otro sitio; ahí entra `WeaponStaminaCostParam`) |
| `FUN_140136220` | `ChrDamageActionCtrl` / `PlayerDamageActionCtrl` | daño recibido / guardia (sin leer) |
| `FUN_1403269e0` | `ChrMorphemeTimeActTrackDamageCtrl` | sin leer |
| `FUN_14015c2d0` (tipo 4) | sin RTTI | consume `+0x148` de un objeto (sin identificar) |
| drenajes A/B/C | `CharacterActionCtrl` (`FUN_14030eb60`) | ver tabla de arriba |

**Fórmula de coste de acción — `FUN_140376770`**: `efecto(0x13) × ChrCommonParam+0x24 (300) × factor × k`, donde
`k = +0x38 (1.25)` si `chr.c0+0x2f` o `+0x31` tienen el bit 2, si no `k = +0x3c (1.25)` si `chr.c0+0x54 ≠ 0`, y si no `k = 1`.
Con `k = 1`: salto 360, dodge 300 y 210. **Qué representan esos bits de `chr.c0` aún no está confirmado.**
`ChrDodgeCtrl` tiene dos variantes y aún no sé cuál es la rodada y cuál el paso.

## Parámetros del jugador
`ChrParam` y `ChrCommonParam` de la regulation tienen una sola fila (100). `FUN_140358620` / `FUN_140358530` usan esa
tabla para ids `< 1000` y otra para ids `≥ 1000`. Fila asignada en `FUN_140315b20`: `chr+0x38` ChrParam, `chr+0x40`
ChrCommonParam, `chr+0x48` ChrMoveParam.

| Campo | Valor |
|---|---|
| ChrParam+0x30 regen/s | 519 |
| ChrParam+0x34 / +0x38 rango al agotarse | −25 / −150 |
| ChrParam+0x3c base de drenaje | 100 |
| ChrCommonParam+0x10 timer de agotamiento | 8.5 s |
| ChrCommonParam+0x60 timer2 | 0.1 s |
| Retardo de regen (`0x14157f380`) | 0.1 s |

Port: `ds2/Stamina.java`, datos en `data/soulsmode/ds2/player_stamina.json`.

## Integración con Minecraft — costes activos (`SoulsStamina.COSTS_ENABLED = true`, pedido del usuario 2026-10-29)
- **`SoulsStamina`** (servidor): una `Stamina` por jugador en Modo Souls con clase.
  - Se avanza cada tick con `dt = 0.05`; el máximo sale de Endurance (`SoulsStats.apply`).
  - Al reaparecer arranca llena. No se guarda en disco.
- **Sincronización**: `StaminaPayload` (actual, máx, sprint bloqueado); `max = 0` oculta la barra.
- **HUD**: barra de 81 px en la fila de los corazones; `GuiMixin` sube corazones y armadura 6 px.
- **Ataques** (`SoulsAttack`):
  - con estamina ≤ 0 no se acepta la petición (`FUN_140367a80`);
  - cada evento 2200 (2201 en críticos) con ranura llena cobra `staminaCost` al empezar
    (`weapons.json`, `tools/re/weapons.py` `stamina_cost`);
  - el evento TAE 101100 de la animación detiene la regen (`regenBlock`).
- **Esquiva** (`SoulsRoll`):
  - con estamina ≤ 0 no se esquiva;
  - coste 300 (rodada) / 210 (paso atrás), ×1.25 si la rodada anterior sigue sonando o si se cancela un ataque;
  - se cobra al empezar; 101100 de 120020 / 110011 detiene la regen.
- **Sprint**: drenaje A a 200/s con la curva de carga `0x1e` y sin regen mientras dura. No se puede sprintar con 0;
  al agotarse se corta, y al volver a > 0 hay 3.25 s sin sprint. El cliente lo respeta con `LocalPlayer.canStartSprinting`.
- **Guardia**: con estamina ≤ 0 un golpe en guardia entra entero (rotura de guardia; la reacción 10 no está portada).
- **No portado**:
  - **coste de estamina al bloquear**: fórmula encontrada (ver "Guardia"), sin portar;
  - **tasa de sprint fijado** (232.75/s): el servidor no conoce el lock-on del cliente;
  - **salto**: el salto de DS2 solo existe a velocidad de dash (≥ 4.4 m/s) y cuesta 360; el salto de Minecraft es
    otra acción, así que no se cobra;
  - los 0.27 s de botón mantenido para empezar el dash (Minecraft usa su propio sprint);
  - el factor de falta de stats (`PlayerLackOfStatsParam`): no hay requisitos de stats todavía.

## Investigación del sprint (en curso)
- `chr+0xc0` es un struct de 0xfc bytes sin vtable (ctor `FUN_1403229a0`, alojado en `FUN_140312dd0`).
  Sus bytes son flags por acción con patrón de 2 bits: `& 2` = activo, `& 6 == 6` = disparo.
  Ejemplos: `+0x2f` dodge, `+0x30` dodge alternativo, `+0x31` salto (ver `ChrDodgeCtrl` / `ChrJumpCtrl`).
  **No encontré quién escribe `+0x2a` / `+0x36` / `+0x28`**: no hay escrituras directas con esos desplazamientos,
  así que probablemente se llenan en bloque desde Morpheme/TAE.
- `ChrMorphemeTimeActTrackStaminaCtrl` (`FUN_1403287b0`): el evento Morpheme `0x18aec` bloquea la regen mientras dura
  (`+0x558` += 1 al empezar, −= 1 al terminar).
- TAE del jugador: `gamedata:/TimeAct/chr/c000100_pl.tae` (id de personaje 100, igual que ChrParam). Ya se puede extraer
  (`extract_bdt.py`), pero el formato TAE está sin analizar.

## Resuelto en la segunda pasada
- **Mínimo `chr+0x1b0` = 0**: el constructor de `CharacterCtrl` (`FUN_1403114f0`) pone a cero `+0x1ac..+0x1b8`, y no hay
  otro escritor en el código del personaje (`FUN_14008ca00` copia un struct de menú, no el chr). Consecuencia: al agotarse,
  el valor pasa por el rango `[−150, −25]` y luego se acota a `[0, máx]`, así que **el jugador queda en 0**, no en negativo.
- **Multiplicador de coste tipo 0 (`chr.b0+0xc8`)**: vale 1.0 al construirse (`FUN_14031cbc0`); lo cambia `FUN_140385fe0` (sin portar).
- **Multiplicador de regen `chr.b8+0x264`**: solo se aplica si `chr.b8+0x14 ≠ 0`, y este vale 0 al construirse (`FUN_14031bcf0`).
  Ambos se copian juntos desde un struct en `FUN_1403931ce`, que está ofuscado.
- **"Efecto especial" de regen y drenajes = carga de equipo.** `FUN_14031e640` devuelve `1 − PlayerGameParamCalculator::vfunc+0x30(id)`
  (`FUN_140320130`), que interpola en `ChrEquipLoadParam` el **ratio de carga** `chr.b0+0x30` (peso / carga máxima, escrito
  por `FUN_14037e430`) × 100 (`FUN_140320230` / `FUN_1403203e0`), y multiplica por 0.01.
  - Umbrales (fila 1): 0, 30, 70, 100 y 120 %.
  - Regen (fila `0x1d`): penalización de 0 / 7.5 / 35 / 50 / 60 % → multiplicador de 1.0 a 0.4.
  - Drenaje A (fila `0x1e`): 0.665 / 0.499 / 0.278 / 0.111 / 0 %, apenas cambia nada.
  - Drenajes B y C (filas `0x1f` / `0x20`): 0 hasta el 100 %, y 42 % al 120 %.
- **Multiplicador del coste de acción** (`FUN_140376770` → `vfunc+0x60` id `0x13`): **no es carga de equipo**. Es
  `1 + PlayerLackOfStatsParam(0x13)` según un valor de "falta de stats" (`chr+0x378`→`+0x350`); vale 0 si no hay ese objeto,
  así que el multiplicador queda en 1.

## Pendiente
- **Qué acciones consumen y cuánto**: ataques (`WeaponStaminaCostParam`), rodar/paso (`BT_StepStaminaEmpty`,
  `DodgeStaminaEmpty`), guardia, y a qué acción corresponde cada drenaje continuo. Puntos de partida (llamadores de
  `FUN_1403335d0`): `FUN_14015c2d0`, `FUN_1403269e0`, `FUN_140136220`, `FUN_140368e70`, `FUN_140330510` (tipo 3),
  `FUN_140328d10` (tipos 1/2).
- Qué modifica `chr.b0+0xc8` (`FUN_140385fe0`) y `chr.b8+0x14`/`+0x264` (`FUN_1403931ce`, ofuscado).
- El factor de `vfunc+0x130` de la regen (por defecto 1).
- Resuelto: el temporizador de agotamiento de 8,5 s (`chr.b8+0x5e0`) es la pose de cansancio (ver "Agotamiento").
- Qué representa la "falta de stats" (`chr+0x378`→`+0x350`).

## Costes de ataque y guardia
Desofuscado con `D:\Reversing\DS2\out\py\deob.py` (sigue los saltos `push X / ret` y `cmov` de Arxan). Salidas en `out/st_*`.

### Coste de un ataque normal — calculado al empezar el ataque
- **Dónde**: `FUN_140393c00` (inicio de ataque) arma un descriptor en pila y llama a `FUN_14038fb10` (`0x140393f9a`). El
  resultado va a `descriptor+0x24`, y `FUN_140392b20` lo copia como coste de **cada** slot de golpe (0..2) con
  `FUN_1403748c0` (escribe `slot+0xb8`, es decir `chr+0x230 → +0xfc0 + slot·0xc0`).
- **Descriptor** (lo llena `FUN_140393c00` desde la petición que arma `FUN_140395b40`): `+0x04` agarre (2..6), `+0x0c`
  tipo de acción (1..25, tabla de [weapons.md](weapons.md)), `+0x10` tipo de movimiento = `WeaponAttackMotionParam +0x04`,
  `+0x20` estamina actual, `+0x24` 0 (luego el coste), `+0x48` fila de `WeaponParam`, `+0x60` fila de WAMP.
- **Fórmula — `FUN_14038fb10`**: `coste = base × WSC[i] × (1 + falta)`.
  - **`WeaponStaminaCostParam`** (índice `0x15`, getter `0x1403591b0`): fila = `WeaponParam +0x3c` (10 en todas las
    armas probadas). 64 floats, **uno por slot de acción**, con el mismo índice que `WeaponActionCategoryParam`
    (`FUN_14038fa50` → `FUN_140390260`): agarre 2/3/4/5/6 → bloque 0/10/20/30/40; tipos 1..10 → desplazamiento
    `[0,1,2,3,4,5,7,8,9,6]`; tipos 11..24 → índice 50..63 (25 también → 63). Agarre fuera de 2..6 → coste 0.
  - **Base** (`FUN_14038fa50`): tipo de movimiento 1..7 → `WeaponParam +0x34`; 8..12 → `+0x38`; otro (0, 13 guardia…) → 0.
  - **Falta de stats**: `PlayerGameParamCalculator vt+0x58` (`FUN_14031efd0` = `1 + vt+0x48` `FUN_1403811f0`) con
    `PlayerLackOfStatsParam` fila 11 = (30, 50, 0, 0): `+0.01·(30 + 20·falta)` si falta > 0, acotado a [0, 1]. Con los
    requisitos cumplidos vale 1. La parte de `FUN_140376510(estamina, 0)` da 0 (columnas 2/3 de la fila también son 0):
    **la estamina actual no cambia el coste**.
- **Cuándo se cobra**: el evento TAE 2200 (`FUN_1403269e0`) cobra `slot+0xb8` con `FUN_1403335d0(…, 4)` (tipo 4: sin el
  multiplicador `chr.b0+0xc8`) **cada vez que empieza un 2200 cuyo slot tiene PDP ≠ 0**. Casi todos los ataques tienen
  uno solo; el fuerte 2 a dos manos del pico (`34030031`) tiene dos slots llenos → se cobra dos veces.
- **Críticos** (backstab/riposte, `FUN_1403947b0`): mismo `FUN_14038fb10`, guardado en `chr.e0→+0xd8 +0x148` y cobrado por
  el evento 2201 en `FUN_14015c2d0`.
- **Armas 4670000 / 4680000**: el coste además se escribe en `chr.b8+0x1ac`, que consume el evento TAE `0x6b`
  (`FUN_140368e70`). No afecta a las armas del mod.

| Arma (`+0x34`) | 1M der. L1 / L2 / F1 / F2 | 2M L1 / L2 / F1 / F2 | 1M izq. L1 / L2 / F1 / F2 |
|---|---|---|---|
| Longsword 1220000 (210) | 199.5 / 220.5 / 294 / 304.5 | 249.3 / 275.5 / 367.5 / 380.5 | 215.5 / 238.1 / 317.5 / 328.9 |
| Battle Axe 2010000 (241) | 228.9 / 253 / 337.4 / 349.5 | 286.1 / 316.2 / 421.8 / 436.7 | 247.3 / 273.3 / 364.4 / 377.4 |
| Pico 2610000 (225) | 213.7 / 236.2 / 315 / 326.3 | 267.1 / 295.2 / 393.8 / 407.7 | 230.9 / 255.1 / 340.2 / 352.3 |
| Mace 2410000 (243) | 230.8 / 255.1 / 340.2 / 352.4 | 288.4 / 318.8 / 425.2 / 440.3 | 249.3 / 275.6 / 367.4 / 380.5 |
| Spear 2800000 (222) | 210.9 / 233.1 / 310.8 / 321.9 | 263.5 / 291.3 / 388.5 / 402.3 | 227.8 / 251.7 / 335.7 / 347.7 |
| Fists 3400000 (151) | 143.4 / 158.5 / 211.4 / 219 | 179.2 / 198.1 / 264.2 / 273.6 | 154.9 / 171.2 / 228.3 / 236.5 |
| Drangleic Shield 11230000 (236) | guardia 0 / 0 / parry 330.4 / 342.2 | guardia 0 / 0 / parry 413 / 427.6 | 0 / 0 / 356.8 / 369.6 |

- Multiplicadores WSC fila 10: 1M der. 0.95 / 1.05 / 1.4 / 1.45; 2M 1.1875 / 1.3125 / 1.75 / 1.8125; 1M izq.
  1.026 / 1.134 / 1.512 / 1.566; corriendo y tras rodar 0.75 (2M 0.9375).
- **L3 = L1** y el combo fuerte alterna F1/F2 (tipos 1/2 y 3/4).
- **Botones de la otra mano con un arma a dos manos** (tipos `0x0b`/`0x0c`, slots 50/51, ×1.282 / ×1.417): golpe con el
  escudo de Drangleic 302.6 / 334.4. En las demás armas el slot 50 es guardia (0) y el 51 cuesta 297.6 (Longsword),
  341.5 (Battle Axe), 318.8 (pico), 344.3 (Mace), 214 (Fists); la lanza tiene guardia en los dos (0).
- **Fists**: los valores de la tabla suponen agarre 2/3/6. Si la mano vacía usa agarre 1 ("sin arma",
  `FUN_140397a80`), `FUN_14038fa50` devuelve 0. **No confirmé qué agarre usa la mano vacía.**

### ¿Se puede atacar con 0 de estamina? No.
- `FUN_140367b60` arma la petición de cada mano (`FUN_140395b40`) y solo la acepta (flags 8 / 9 de
  `FUN_140365f80`) si `FUN_140367a80` devuelve verdadero. Ese llama a `FUN_1403337c0` (`actual <= 0`) y **rechaza el
  ataque con la estamina en 0**.
- Excepciones (no hace falta estamina): tipo de movimiento `0x10`; tipos 8..11 cuando el byte `+9` de la petición no es
  1 ni 2; tipo `0x0c` con el byte `+0x56` = 10.
- No hay comparación con el coste: basta con estamina > 0. Si el coste supera la estamina, el consumo deja la barra en 0
  (ver "Consumir").

### Guardia
Salidas: `D:\Reversing\DS2\out\guardstam\g*.c` (Ghidra) y `deob.py` sobre los thunks ofuscados.

**Orden dentro de un golpe** (`FUN_1401345c0` = `vt+0x28` del jugador; `FUN_140134eb0` igual):
`vt+0x58` categoría → `vt+0x68` (`FUN_1401373b0`, arma la estructura `A`) → `vt+0x70` (`FUN_140137aa0`, calcula el
resultado) → `vt+0xf0` (`FUN_140136220`, **aplica vida y estamina**) → `vt+0x80` (`FUN_140136570`, **rotura de guardia**).
- La estamina se cobra **antes** de mirar `chr+0x1ac <= 0`, así que el golpe que deja la barra en 0 (el consumo acota a
  0, ver "Consumir") **rompe la guardia en ese mismo golpe**.
- `vt+0xf0` solo corre si `vt+0xc8` (`FUN_140139b40`) es verdadero (categoría ∉ {0, 3, 5}) y el byte 1 de `vt+0x38`
  (autoridad de red) es 1.
- La categoría 2 (`FUN_140138510`) **no mira la estamina**: aunque ya esté en 0, el golpe se clasifica como guardia, la
  vida se aplica en `vt+0xf0` con esa categoría, y después `vt+0x80` lo pasa a categoría 1 con reacción 10.
  Por tanto **el golpe que rompe la guardia hace el daño de golpe bloqueado**, no el entero: las tasas de guardia van en
  el bloque que `vt+0x1e0` llena para la categoría 2 y que recibe `vt+0x100`. El uso exacto de cada tasa en
  `FUN_140138d50` no está releído. El port actual (golpe entero) **no coincide**.

**Daño de estamina** = `resultado+0x38` = `s · (1 + 0.01 · DCP[+0xb0] · caída)` (`FUN_140137aa0`).
- **`s`** (`FUN_140139210`) = `A+0x68 · (1 − estabilidad) · A+0x90 · A+0x84 + 10 · (A+0x8c o A+0x8d)`.
  Es 0 salvo categoría 2, `DCP+0x16 ≠ 0` (59 filas) o `b8+0x4c8 & 4`.
- **Fila del golpe**: el id del golpe (`hit+0`) se resuelve en el gestor `DAT_1416148f0+0x10 → +0xb40`. Tablas
  (`FUN_14048ba80`): 0 PlayerDamageParam `+0x10`, 1 EnemyDamageParam `+0x20`, 2 SystemDamageParam `+0x30`,
  3 DamageCtrlParam `+0x40`, 4 DamageAdjustParam `+0x50`.
  - Rangos de id (`FUN_141ca38d5`): `< 1e8` PDP, `1e8..2e8` EDP, luego SDP. `fila+0` = id de DamageCtrlParam (DCP).
- **Campos de `A`** (`FUN_1401373b0`):

| Campo | Origen | Valor base |
|---|---|---|
| `+0x68` | `(float) u16 [fila PDP/EDP/SDP + 0x24]` (base) | Longsword L1/L2 620, F1/F2 710, slot 10 870 |
| `+0x84` | `vt+0x168` del atacante × u16 `[1]` de `ChrRoundDamageParam[atacante ChrParam+0x69]` × u16 `[0]` (`+0x30..`) de `ChrRoundDamageParam[víctima]` × `hit+0xc0`; los u16 ×0.01 | 1 (NG, jugador) |
| `+0x90` | `ChrMultiplayParam[víctima ChrParam+0x24]`, entrada 0..3 según `FUN_1403587e0` (0 = nula → factor 1); u16 `+6` ×0.01 | 1 |
| `+0x8c` / `+0x8d` | bytes `hit+0xcc` / `hit+0xcd`; `+0x8d` si la víctima es de tipo 1 o 2 (`DAT_1410bfff3[chr+0x54·5] ≠ 0`) | 0 |

  - `ChrRoundDamageParam` es por ciclo (`chr+0x45d`: 0/1 → columna 0; 2..7 → `+6·(n−1)`; otro → `+0x2a`). Fila 0
    (jugador): todo 100.
  - `vt+0x168`: jugador `FUN_140380390` = 1.0; enemigo `FUN_14031ec50` = `DamageAdjustParam +0xf0..+0x10c` (todos 1.0).
- **El golpe (`hit`)** lo arma `FUN_14016d070`: `hit+0x48..` es la copia del slot a partir de `slot+0x1c`.
  - El slot lo construye `FUN_140392b20`: con el atacante de tipo 1/2, `FUN_140391fe0`; si no, `FUN_140391e60`
    (enemigos).
  - El constructor (`FUN_141b7102e`) deja `+0x78..+0x80` = 1.0, `+0x84` = 0 y `+0x94/+0x98` = 1.0 (desplazamientos
    desde `slot+0x1c`).
  - **Jugador** (`FUN_140391fe0`): `hit+0xc0` = `WeaponParam+0x84` (Longsword 1.0, Drangleic 1.1) ×
    `DamageAdjustParam+0xc` (1.0) × `WeaponTypeParam` `+0x58` (mano izquierda, 0.9) o `+0x64` (derecha, 1.0) ×
    (1 − falta).
    - Falta = `PlayerLackOfStatsParam` fila 1 (30, 80, 0, 20) por `FUN_1403811f0`. Con los requisitos cumplidos y la
      estamina ≥ coste vale 0.
    - Con la estamina del atacante por debajo del coste, `FUN_140376510` da (coste − estamina) / coste (1 con ≤ 0), lo
      que resta hasta un 20 %.
    - `hit+0xcc/+0xcd` = `chr.b8+0x31a/+0x31b` del atacante. Los pone a 0 `FUN_14031c580`; el escritor no se encontró
      (sin efectos = 0).
  - **Enemigo** (`FUN_140391e60` → `FUN_140170710`): solo suma el daño plano de la fila; `hit+0xc0` queda en 1.0 y
    `hit+0xcc/+0xcd` en 0.
- **No hay valor genérico por enemigo**: la base es siempre `+0x24` de la fila EDP del golpe.
  - 0 en 1008 de 7497 filas (sin daño de estamina).
  - Mediana de las filas ≠ 0: 590. Las más comunes: 295, 370, 490, 590, 735.
  - Las filas de EDP no tienen nombre en la regulation: **no puedo decir cuál es la de un Hollow Soldier**.
- **Estabilidad** (`vt+0x1f0` = `FUN_140380c60` → `FUN_14034d7b0` → `FUN_141b5a938`), ×0.01:
  `(extra + WeaponTypeParam[estado] + WeaponReinforceParam+0x74+4·nivel) · FUN_14034cc80(4, falta)`, acotado a [0, 100].
  - Mano: `chr.b8+0x14 == 0` o `== 2` → izquierda (−2); otro → derecha (−1).
  - `[estado]` = `manos+0x10`: 1 (una mano) `+0xd0`, 2/3 (a dos manos) `+0xd4`, 4 `+0xd8`, 5/6 `+0xdc`.
    Drangleic (WTP 430): 0 / 10 / 0 / 10.
  - `extra` = `FUN_14014d170` → `FUN_14021f721`: suma de los efectos activos de tipo 7..9 (`vt+0xa0`, categoría 5/6
    según la mano). Sin anillos ni hechizos = 0.
  - `FUN_14034cc80(4, f)` = `1 − 0.01·(30 + 50·f)` con falta de stats f > 0 (`PlayerLackOfStatsParam` fila 4); sin
    falta, 1.
  - Durabilidad ≤ 0 → estabilidad 0 (salvo las armas 3250000 / 3251000).
  - **Drangleic +0: 55 a una mano, 65 a dos manos.**
- **Caída por distancia** (`FUN_140136ab0`, `golpe+0x78`). Si la distancia 3D víctima–atacante `d` supera
  `CommonParam+0x35c` de la víctima (jugador 0):
  - `caída` = 0 si `d ≤ hit+0xdc·DCP+0xa4`, 1 si `d ≥ hit+0xe0·DCP+0xa8`, lineal entre medias.
  - En cuerpo a cuerpo `hit+0xdc/+0xe0` = 1.0 (constructor).
  - DCP del Longsword (14011100/02/09): 1.4 m / 2.0 m y `+0xb0` = −70 → **el daño de estamina baja hasta un 70 % entre
    1.4 y 2.0 m**.
  - Las filas EDP con base ≠ 0 tienen casi siempre `+0xa4/+0xa8/+0xb0` = 0 (sin caída).
  - La posición del atacante sale de `vt+0x138` (fila de traslación de su matriz, **sin confirmar**).
- **Ejemplos** (NG, sin red, víctima jugador con Drangleic +0 a una mano, `d ≤ 1.4 m`):

| Atacante | `s` = daño de estamina |
|---|---|
| Longsword L1/L2 con la derecha (PDP 10031800 / 10032000) | 620 · 0.45 = **279** (a ≥ 2 m: 83.7) |
| Longsword F1/F2 (PDP 10032400 / 10032410) | 710 · 0.45 = **319.5** |
| Longsword L1 con la izquierda | 620 · 0.45 · 0.9 = **251.1** |
| Mismo L1, Drangleic a dos manos | 620 · 0.35 = **217** |
| Enemigo, fila EDP con base 590 (mediana) | 590 · 0.45 = **265.5** |

**Rotura de guardia** (`FUN_140136570`, `vt+0x80`):
- Categoría 2 y `chr+0x1ac <= 0` → categoría 1, reacción 10.
- Además, un golpe con `DCP+0x10 == 10` (PDP 1000 / 6010 / 7010) contra una guardia rompe la guardia si
  `CommonParam+0xed ≠ 0` (jugador 1) y no están `b8+0x5ec` / `b8+0x5e8`.
- Reacción 10 → `b8+0x708 = 150` (`FUN_140138930`) → `FUN_14035e820` pone el CP `DmgCategory` y manda `DamageFullBody`
  (`BT_Damage` → SM `0x18c2` de `c0001.nmb`).
- **Animación: sin confirmar.** Ningún nodo ni condición de transición de `c0001.nmb` lee `DmgCategory` (escaneo
  completo), así que no sé cómo elige el estado.
  - El SM `0x18c2` tiene, entre otros, `a00_16_1010/1020/1030 DmgParryBreak_Start/Loop/End`, `a00_16_1210 DmgStagger`
    y `a00_16_1410 DmgCollapseFaceDown`.
  - El TAE tiene además 161110 / 161120 / 161130 (1.5 / 2.0 / 1.233 s, solo con 101100), **sin `.nsa` ni nodo en
    c0001.nmb**.
  - Pistas de los candidatos en `c0001.nmb`:
    - `DmgParryBreak_Start` (TAE 161010; 101100 de 0 a 1.867 s): `CancelControl_Avoid/Move/AttackAll` toda la
      animación; `StabControl` 262 en el 0–40 % y 261 en el 40–100 %.
    - `Loop` (1.333 s): cancelaciones y 261 toda la animación.
    - `End` (2.0 s): `CancelControl_Avoid` hasta el 58.3 %, `Move` / `AttackAll` hasta el 75 %.
    - `DmgStagger` (161210, 1 s): `Avoid` / `AttackAll` toda la animación, 101100 de 0 a 1 s.
  - El crítico de rotura de guardia (`FUN_140390700`) exige `b8+0x500 ≠ 0` en la víctima (StabControl 262); la riposte
    (`FUN_1403908a0`) exige `b8+0x4fc ≠ 0` (261). 262 también está en las 32 `DmgExtraHeavy*` (0–66.7 %).
  - Si la guardia se suelta: no verificado (el golpe pasa a ser normal y entra `BT_Damage`).
- **Coste continuo por mantener la guardia: no encontrado.**
  - `ChrGuardActionCtrl` (`FUN_14032eb30`) y `ChrEventTrackActionGuard` (`FUN_140369f60`: eventos `0x208/0x209/0x212/0x21c`
    → `c0+0x78/+0x7c/+0x80`, `b8+0x550`) no tocan `+0x1ac` ni `+0x558`.
  - Ninguno de los drenajes A/B/C lee esos campos.
  - Indicio sobre el drenaje A: su condición `chr.b8+8 == 4` es el mismo estado que `FUN_1401f9d90` usa para el ataque
    corriendo, así que A sería el drenaje del sprint (**sin confirmar**).

### Agotamiento (estamina a 0)
- **No hay pausa de regen propia de llegar a 0.** La regen (`FUN_140333900`) solo mira `b8+0x558`. Tras **cualquier**
  consumo, incluido el que deja la barra en 0, la pausa es la misma: `DAT_14157f380` = 0.1 s, más el TAE 101100 o los
  drenajes que estén activos.
- **`timer2`** (`ctrl+0x0c` = `ChrCommonParam+0x60` = 0.1 s): solo lo lee `FUN_140333790` (`FUN_141b122c1`), que
  devuelve 0 mientras `timer2 > 0` y si no `chr+0x1ac`.
  - Lo usan `FUN_140393c00` (`descriptor+0x20`, inicio de ataque), `FUN_14038f587` y `FUN_140394dc6`: es la estamina
    que entra en la penalización de `FUN_140376510` del ataque.
  - No afecta a la regen.
- **Temporizador de agotamiento** (`ctrl+0x10` = `ChrCommonParam+0x10` = 8.5 s) y contador `chr.b8+0x5e0`:
  - El único lector es `ChrConditionMotionCtrl` (`FUN_14035e490`): pone el CP Morpheme `BlendWeight_Tire` = 1 si
    `+0x5e0 ≠ 0` (nombres en `FUN_14035e280`). Es **la pose de cansancio**.
  - En la red, `EventBlend_Tire` mezcla `a00_50_0020_AdditiveTireIdle` (nodo `0x50f`); el enlace entre los dos CP no
    está trazado.
  - También sube `+0x5e0` `FUN_14037e430` mientras `calculadora vt+0x28` es verdadero (exceso de carga,
    **sin confirmar**).
  - No bloquea acciones ni la regen.
- `b8+0x500` de la regen = StabControl 262 (ventana de crítico de rotura de guardia). Con él activo, los tipos sin
  `DAT_1410bfff3` (no jugadores) suman `ChrParam+0x2c` (800) por llamada, sin `dt`.

### Bloqueo de regen durante el ataque — confirmado
- TAE 101100 = `0x18aec` → `ChrMorphemeTimeActTrackStaminaCtrl` (`FUN_1403287b0`): al empezar el evento hace
  `chr.b8+0x558 += 1`, y al terminar (`+0x29`) `−= 1`.
- La regen (`FUN_140333900`) solo corre con `+0x558 == 0`: la estamina **no se regenera** de 0 a 1.5 s en los ligeros
  ni de 0 a 1.833 s en los fuertes del Longsword.

## Sprint, esquiva y salto: reglas de estamina
Salidas de Ghidra: `D:\Reversing\DS2\out\sp_*` (`sp_pnode*.c`, `sp_manip.c`, `sp_pmove.c`, `sp_mvbase.c`, `sp_dodgein.c`,
`sp_cost.c`, `sp_jump.c`, `sp_c28.c`, `sp_cac.c`).

### Qué es `chr+0xc0` (flags de nodos Morpheme)
- Cada frame `FUN_1403647c0` pone a cero `chr.c0+0x24..+0x50` y llama a `PlayerMorphemeNodeCtrl` vfunc `+0x28`
  (`FUN_1403982e0`). Este escribe `chr.c0[0x24 + slot]` con el estado del nodo de la red (`FUN_1403648d0` / `FUN_1403649c0`).
- Nombres de nodo: lista de `FUN_140397f80` (índice = posición × 8). Slot → nodo, sacado del ensamblador de `FUN_1403982e0`:

| Byte | Nodo Morpheme |
|---|---|
| `+0x24` | `BlendTree_BasicMove` |
| `+0x28` | `SM_MoveGuard\|SM_Guard` |
| `+0x2a` | `…\|SM_MoveOrAttackGuard\|BT_Move` (locomoción) |
| `+0x2b` | `BT_MoveAttack` (+ selectores, `FUN_140397d50`) |
| `+0x2f` | `SM_Dodge` / `BT_DodgeTakeoff*` (`FUN_140397ed0`) |
| `+0x30` | `BT_Step` / `BT_StepStaminaEmpty` / `BT_StepFailed` |
| `+0x31` | `SM_Jump` |
| `+0x32` | `BT_JumpTakeoff` o `BT_StandJumpTakeoff` |
| `+0x36` | `SM_Ladder\|SM_LadderDash` |

- Bits: `1` el nodo existe; `2` activo (`FUN_1404db4e0`); `4` = bit 0 del estado del nodo (`FUN_1404e3430`).
  `& 6 == 6` es activo y con ese bit; por cómo se usa, parece "recién entrado". **Su significado exacto no está confirmado.**
- `chr.c0+0x54` **no es un nodo**: es un contador que sube y baja la pista Morpheme `AttackDuration` (userData 120 = `0x78`,
  `FUN_140368e70`). Vale ≠ 0 dentro de la ventana de ataque.

### Drenajes continuos: qué es cada uno
- **A = sprint (dash).** Condiciones: `chr.c0+0x2a & 2` (nodo `BT_Move`) y `chr.b8+8 == 4`.
  - `chr.b8+8` lo escribe `FUN_140335b80`: vale 4 solo con el modo de movimiento `PlayerMovementCtrl+0xb0 = 3`.
    Ese modo usa la velocidad `ChrMoveParam+0x44..+0x48` (4.3–6.15 m/s), o `+0x8c..+0x90` (5.0–6.5) fijado.
  - Coste: `100 × ChrCommonParam+0x14 (2.0)` = **200/s**. **Con objetivo fijado** (`chr+0x3a0` = `PlayerLockTargetCtrl`,
    vfunc `+0x20` ≠ 0) usa `+0x18 (2.3275)` = **232.75/s**. Se multiplica por la fila de carga `0x1e`.
  - Mientras está activo, la regen está bloqueada (`+0x558`).
- **B = `SM_LadderDash`** (escalera rápida): `ChrCommonParam+0x1c (2.66)` = **266/s**.
- **C no es `chr.c0`**. `FUN_1403112b0` lee `CharacterActionCtrl+0xc0`, un controlador de interacción con objetos
  (ctor `FUN_140332660`, update `FUN_1403330b0`). Se activa en su subestado `+0x28 == 2`, que se fija al mantener la
  petición 0 (botón de sprint) y pone velocidad 1.5. Coste `ChrCommonParam+0x20 = 0`: **0/s**.
  La tabla de arriba lo atribuía mal a `chr.c0+0x28`.
- **La guardia no tiene drenaje continuo.** `chr.c0+0x28` (`SM_Guard`) no lo lee ningún drenaje. Andar fijado tampoco.

### Petición de sprint (manipulador → máquina de dash)
- **Bit de petición 0** (`ChrPadManipulator`, `FUN_140367ea0`): `entrada+0x145 ≠ 0` o `entrada+0x12c` bit 5 (botón de
  esquiva **mantenido**), y además `chr.b8+0x588 == 0`.
  - Las entradas del botón son `+0x128` = pulsado, `+0x12c` = mantenido, `+0x130` = soltado (ver `FUN_14038ea50`).
  - `entrada+0x145` y el flag `chr.b8+0x588` (índice `0x2e`): **significado sin identificar**.
- **Máquina de dash** en `PlayerMovementCtrl+0x1a0` (`FUN_140385560`, llamada desde `FUN_140385fe0`):
  - Recibe `dt`, la magnitud del stick, el bit 0 y "stick ≈ 0".
  - Lee `PlayerCommonParam` fila 0 a través de `thunk_FUN_141cf7120(paramMgr)`, que usa el slot `+0x390`
    (índice 8 de `FUN_14048b620` = `PlayerCommonParam`).
- **Puede sprintar** (`+0x10`, en `FUN_140385620`): estamina > 0 (`FUN_1403337c0` falso) **y** enfriamiento `+0x24 <= 0`
  **y** `chr.b8+0x588 == 0`.
- **Estado 0 → 1 (dash)** (`FUN_1403859d0`): con permiso, botón mantenido y stick > ε, suma `dt`.
  Al llegar a `PlayerCommonParam+0x8` = **0.27 s** pasa a dash. Sin permiso, el contador vuelve a 0.
  - Es el mismo campo que la ventana de toque de la esquiva (`FUN_14038ea50`): si se suelta antes de 0.27 s, es
    rodada o paso atrás (al soltar); si se mantiene más, es dash.
- **Estado 1 → 2**: al soltar el botón o centrar el stick.
  - Sin fijar: solo el estado 1 es dash (`movectrl+0x8a` bit 2). En el frame siguiente `chr.b8+8 ≠ 4` y vuelve a 0.
  - Fijado: los estados 1 y 2 son dash. El 2 dura `PlayerCommonParam+0xc` = **0.15 s** (`FUN_140385960`).
  - El estado 3 (`FUN_1403858d0`) exige `chr.b8+8 == 5`, valor que `FUN_140335b80` nunca escribe. **No sé quién lo escribe.**
- **Prioridades** (`FUN_140335b80`): el modo dash solo sale si `chr.c0+0x54 == 0` (no hay ventana de ataque), si
  `chr.e0+0x10 == 0` y si `movectrl+0x8d & 0x20` y `+0x8e & 0x40` están a cero.

### Sin estamina durante el sprint (`FUN_140385a80`)
- **No se puede empezar a sprintar con estamina ≤ 0.**
- Si la estamina llega a ≤ 0 durante el dash, la máquina **vuelve al estado 0 en ese momento**. El modo pasa a
  trote, `chr.b8+8` deja de valer 4 y el drenaje A se para.
- Cuando la estamina vuelve a ser > 0, se arma un enfriamiento `+0x24 = PlayerCommonParam+0x10` = **3.25 s**.
  Mientras dure, no se puede sprintar.
- Pasado el enfriamiento, hay que acumular otra vez 0.27 s con el botón mantenido y el stick fuera del centro.
  **No hace falta volver a pulsar.**
- El temporizador de 8.5 s (`ChrCommonParam+0x10`, `chr.b8+0x5e0`) **no lo lee el código del dash**. Su uso sigue
  sin encontrarse.

### Esquiva y paso atrás
- **Coste calculado al empezar la acción** (`ChrDodgeCtrl` vfunc `+0x20` `FUN_140328b10` / `+0x28` `FUN_140328c20`).
  Se guarda en `ctrl+0x18`.
  - Rodada: `FUN_1403765c0`, factor `+0x2c`. Paso atrás: `FUN_1403766d0`, factor `+0x28`.
  - Si está la petición 7 (rodada con `chr.b8+0x52c ≠ 0`), el coste de la rodada es 0.
- **Se cobra al entrar en el estado Morpheme**: en `FUN_140328d10`, cuando `chr.c0+0x2f & 6 == 6` (Dodge) o
  `+0x30 & 6 == 6` (Step), se llama a `FUN_1403335d0(coste, 1 / 2)` y se pone `ctrl+0x18 = 0`. No hay evento TAE de coste.
- **Multiplicador k** (`FUN_140376770`): se evalúa al calcular el coste, antes de que la red entre en el nuevo estado.
  - Esto se deduce del flujo: `FUN_140328b10` escribe `chr.b8+0x9f`, que `FUN_14035fbc0` necesita para enviar la petición.
    **No encontré quién llama a `FUN_140328b10`.**
  - `k = ChrCommonParam+0x38 = 1.25` si sigue activo `SM_Dodge` (`c0+0x2f & 2`) o `SM_Jump` (`c0+0x31 & 2`).
    Es decir, encadenar desde una rodada o un salto: **rodada 375, paso atrás 262.5, salto 450**.
  - Si no, `k = +0x3c = 1.25` dentro de la ventana `AttackDuration` (`c0+0x54 ≠ 0`): esquivar cancelando un ataque
    cuesta lo mismo.
  - El estado `Step` (`c0+0x30`) **no** entra en k: rodar justo después de un paso atrás cuesta 300.
- **Sin estamina**: `FUN_140367ea0` no envía ninguna petición de esquiva ni de salto (`bVar9`).
  - Las variantes `StaminaEmpty` / `Failed` **no salen de la estamina**, sino de la carga (`FUN_14038e770`):
    70–120 % → bit 8 → `b8+0x9f = 2` (StaminaEmpty); > 120 % → bit 6 → `3` (Failed).
- **Bloqueo de regen por TAE 101100** (`ChrMorphemeTimeActTrackStaminaCtrl`): el evento empieza antes de la animación,
  así que bloquea desde el inicio.

| Animación | Fin del bloqueo |
|---|---|
| 120020 / 120021 DodgeRolling | 0.9333 s |
| 120030 / 120031 / 120040 | 1.1667 s |
| 110011 / 110031 StepBack | 0.8333 s |
| 130020 JumpTakeoff | 1.8667 s |
| 130021 StandJumpTakeoff | 0 a 1.2667 s |
| 50010 Dash, 40010 Jog | sin bloqueo |

### Salto
- **Petición 0xd** (`FUN_140367ea0` → `FUN_14038e810`), requiere todo esto:
  - `entrada+0x128` bit 6: **flanco de pulsación** de un botón distinto del de esquiva (bit 5). Cuál es en el mando no está identificado.
  - `FUN_14038eb10`: velocidad del controlador **y** velocidad física ≥ `PlayerCommonParam+0x1c` (**4.4 m/s**) ×
    `chr.b8+0x11c` (o `b8+0x3b8` si `b8+0x4b8 & 0x20`).
  - `FUN_1403144c0` → `FUN_14036da20`: sin leer.
  - Estamina > 0.
- **El código no comprueba el estado de dash.** Pero el trote llega como máximo a `ChrMoveParam+0x20` = 4.295 m/s,
  menos que 4.4, así que con los params del jugador **solo se puede saltar a velocidad de dash**.
- **Coste** (`ChrJumpCtrl`):
  - vfunc `+0x48` `FUN_140330370`: factor `+0x30` (1.2) → **360**.
  - vfunc `+0x40` `FUN_140330450`: factor `+0x34` (1.0) → **300**. **No está confirmado** si este es `StandJumpTakeoff`
    ni qué lo dispara.
  - Se cobra en `FUN_140330510` cuando `c0+0x31 & 6 == 6` (entrada en `SM_Jump`), con tipo 3.

### Sin encontrar
- Quién llama a `ChrDodgeCtrl` `+0x20` / `+0x28` y a `ChrJumpCtrl` `+0x40` / `+0x48`.
- Qué son `entrada+0x145`, `chr.b8+0x588`, el bit `4` de los flags de nodo y el uso de `chr.b8+0x5e0` (resuelto: `BlendWeight_Tire`, ver "Agotamiento").
- Quién escribe `chr.b8+8 = 5`.

## Implementado: guardia y retardo de regen (2026-10-30)
- **Daño de estamina al bloquear** (`SoulsAttack.guardStaminaDamage`): base × (1 − estabilidad/100) × factor del atacante.
  - Atacante jugador en Modo Souls: base = `PlayerDamageParam` u16 `+0x24` de la ventana de golpe actual, × `WeaponParam
    +0x84` × `WeaponTypeParam +0x64` (derecha) / `+0x58` (izquierda).
  - Mobs de Minecraft (sin fila de DS2): `data/soulsmode/mob_guard_stamina.json`, **elección del usuario** entre los valores
    más comunes de `EnemyDamageParam +0x24`: zombis 590, proyectiles 295, chicos/rápidos 490, pesados 735; el resto 0.
  - Estabilidad: `WeaponReinforceParam +0x74` + `WeaponTypeParam +0xd0` (una mano) / `+0xd4` (dos manos), nivel 0.
  - Sin portar: atenuación por distancia (`DamageCtrlParam +0xa4..+0xb0`), falta de stats, efectos, filas de multijugador.
- **Orden** como `FUN_1401345c0`: se aplica el daño bloqueado y se cobra la estamina; si queda ≤ 0, rotura de guardia en
  ese mismo golpe.
- **Rotura de guardia**: se suelta la guardia y se reproduce `a00_16_1010 DmgParryBreak_Start` con sus bloqueos (sin
  moverse, rodar ni atacar 1.667 s) y su TAE 101100 (sin regen hasta 1.867 s). **La animación es elección del usuario**
  entre las candidatas: cómo la elige `BT_Damage` no está decodificado.
- **Retardo de regen tras cada gasto: 1 s** (`SoulsStamina.REGEN_DELAY`), **pedido por el usuario** en lugar de los 0.1 s de
  DS2 (`DAT_14157f380`). Excepción autorizada.
