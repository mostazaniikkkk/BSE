# Stats, Soul Level y coste de nivel

Fuente: `DarkSoulsII.exe` (SotFS, PE timestamp 1663233821), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj\DS2.gpr`.
Datos: `enc_regulation.bnd.dcx` → ver [regulation.md](regulation.md).

## Bloque de atributos — `FUN_14038d570`

- Entrada: 11 `short` consecutivos (slots 0–10).
- Si **cualquier** slot está fuera de `1..99`, la función sale sin recalcular nada.
- Guarda dos copias: base en `[0..10]` y "efectiva" en `[0xb..0x15]`. La efectiva pasa por `FUN_1401ffcc0`,
  que aún no está analizada (posiblemente equipo o anillos).
- Las stats derivadas se calculan desde la copia **efectiva** con `FUN_14038d790`.

### Soul Level
```c
sum = Σ base[0..10];
SL  = max(1, (short)((short)sum - base[10] - base[9]) - 0x35);
```
O sea: los slots 9 y 10 no cuentan, y SL = (suma de los slots 0..8) − 53.
Port: `ds2/Attributes.soulLevel()`.

### Qué slot es qué atributo — pantalla de level-up
- `FUN_1400c7a80` (init de la pantalla) escribe en `+0x1db4..+0x1dbc` la tabla fila→slot:
  `00 01 02 03 04 05 08 06 07`.
- `FUN_1400ccff0` recorre 10 filas. La fila 0 es el nivel (`+200` del bloque, el mismo offset donde
  `FUN_14038d570` guarda el SL). Para la fila `i ≥ 1` lee el slot `tabla[i-1]` con `FUN_14054b440`
  (`*(short*)(stats + slot*2)`), y le asigna estos textos de ayuda (`menu/Text/English/iconHelp.fmg`):

| Fila | Texto (id) | Slot |
|---|---|---|
| 1 | Vigor (909004) | 0 |
| 2 | Endurance (909005) | 1 |
| 3 | Vitality (909006) | 2 |
| 4 | Attunement (909007) | 3 |
| 5 | Strength (909008) | 4 |
| 6 | Dexterity (909009) | 5 |
| 7 | Adaptability (909012) | 8 |
| 8 | Intelligence (909010) | 6 |
| 9 | Faith (909011) | 7 |

Los textos salen de `GameDataEbl.bdt` → ver [archives.md](archives.md).

### Pendiente
- **Slots 9 y 10**: existen y valen 1..99, pero no salen en la pantalla de level-up ni suman al SL.

## Coste de subir de nivel — `FUN_14038d140`

`PlayerLevelUpSoulsParam` (índice de param `0x27`, slot `+0x580` en el param manager, accessor por índice de fila `FUN_140358b90`).

Fila (12 bytes): `u16 level, u16 pad, i32 slope, i32 base`.

```c
row = rowAt(level);                       // por índice
if (row && row.level == level) return row.base;
while (!row || level < row.level) row = rowAt(index >>= 1);
// avanzar mientras row.level < level, quedándose con la anterior
return (level - prev.level) * prev.slope + prev.base;
```

Datos de la regulation actual: filas 0..850 densas (todas `slope = 0`), más una fila suelta `999 → 100000`
que la búsqueda por índice nunca alcanza. Para niveles > 850 el coste queda en `1328037`.
Tabla exportada en `src/main/resources/data/soulsmode/ds2/player_level_up_souls.json`.
Port: `ds2/LevelUpSoulsTable.cost()`.

### Flujo de level-up en el menú — `FUN_1401fb970` / `FUN_1401fb800`
Objeto del menú: `+0xe4` short paso, `+0xe6` short valor del stat, `+0xe8` int niveles pendientes,
`+0xf0` int nivel, `+0xf8` int almas disponibles, `+0xfc` int coste actual, `+0x100` flag (modo sin coste).

Subir (`+0x100 == 0`): requiere `coste <= almas` y `nivel < 1000`. Entonces:
`almas -= coste; nivel += 1; stat += paso; coste = cost(nivel)`.
Bajar (`FUN_1401fb800`) deshace: `nivel -= 1; coste = cost(nivel); almas += coste`.

**Conclusión: el coste de pasar de nivel L a L+1 es `cost(L)`.**

### Pantalla de level-up — `FUN_1400c9e80` (+) / `FUN_1400ce180` (−) / `FUN_1400c9f50` (confirmar)
Objeto en `menu+0x1cb0`, iniciado por `FUN_1401fbac0`: copia el bloque de stats (0xcc bytes) y pone `+0xe4` paso = 1,
`+0xe6` = 0, `+0xe8` = 0, `+0xec`/`+0xf0` = SL, `+0xf4`/`+0xf8` = almas, `+0xfc` = `cost(SL)`.
- `+0xcc + slot*2`: puntos añadidos a cada slot; `+0xe2`: total asignado; `+0xe6`: niveles comprados sin asignar.
- **+** sobre una fila: si `+0xe6 == 0` y se puede comprar (`FUN_1401fb7b0`: `coste <= almas`, nivel < 1000), compra un nivel
  (`FUN_1401fb970`). Luego `FUN_1401fba30` lo asigna si el slot vale < 99, `+0xe6 > 0` y el slot no es 10. Si no se pudo
  asignar, devuelve el nivel comprado (`FUN_1401fb800`).
- **−** (`FUN_1401fb6e0` / `FUN_1401fb890`): solo si el slot tiene puntos añadidos en este menú y vale > 1; quita el punto
  y devuelve el nivel (`FUN_1401fb800`).
- **Confirmar**: exige `+0xe6 == 0` (`FUN_1401fb730`) y aplica con `FUN_14038b1a0`; el menú se reinicia con los valores nuevos.
- Orden de filas: nivel y luego slots `0 1 2 3 4 5 8 6 7` (`FUN_1400c7a80`).

Port: `ds2/LevelUpMenu`; pantalla `client/bonfire/LevelUpScreen` (el diseño visual es propio). El servidor repite los "+"
con `LevelUpMenu` antes de aplicar (`SoulsPlayer.levelUpTo`).

## Stats derivadas — portadas (`ds2/DerivedStats.java`)

Estructura: el objeto de estado del player está en `chr+0x490` (creado en `FUN_14037f3d0`). `FUN_14038b990` devuelve
`obj+8` = bloque de atributos. Las derivadas están en `bloque+0x2c` (`FUN_14038d570` llama `FUN_14038d790(bloque+0x2c, efectivos)`).

| Campo | Fuente en `FUN_14038d790` | Consumidor que fija su significado |
|---|---|---|
| `+0x00` u16 **vida máx.** | `FUN_14038e1e0`: `fila(Vigor).s16@+4 + Σ fila(slot 1..8).u8@+0x8a` | `FUN_14037e8a0`: `chr+0x170 = (int)(derivada+0 × chr.b8+0x3d8)` y fija `chr+0x168` entre `+0x16c` y `+0x170` (recurso entero) |
| `+0x02` u8 **slots de sintonía** | `fila(relate[0]).u8@+2`; `relate[0] = 4` → Attunement | Texto 909017: "Attunement slots… increased by raising attunement" |
| `+0x04` f32 **estamina máx.** | `(float) fila(relate[3]).s16@+6`; `relate[3] = 2` → Endurance | `FUN_14037e8a0`: `chr+0x1b4 = chr.b8+0x3dc × derivada+4` y fija `chr+0x1ac` entre `+0x1b0` y `+0x1b4` (recurso float) |
| `+0x08` f32 **carga máx.** | `fila(relate[4]).f32@+8`; `relate[4] = 3` → Vitality | `FUN_14037e430`: `ratio = peso equipado / (mult × derivada+8)` |

Cómo se asigna HP/estamina a cada recurso: la vida sale de Vigor y es entera; la estamina sale de Endurance y es float.
Los textos del propio juego dicen "Vigor… determines HP" (909004) y "Endurance… determines your overall stamina" (909005).

Sin resolver:
- Los multiplicadores `chr.b8+0x3d8` / `+0x3dc` (probablemente efectos como hollowing o anillos).
- Las cotas mínimas `+0x16c` / `+0x1b0`.
- La unidad de la estamina (fila 1 = 820, fila 99 = 2000).
- La copia "efectiva" (`FUN_1401ffcc0`) está ofuscada con chunks. Por ahora efectiva = base.

Datos exportados: `src/main/resources/data/soulsmode/ds2/physical_stats_per_level.json`.

## Resto de stats derivadas — `FUN_14038d790` (sin portar)

Usa `PhysicalStatsPerLevelStatValuesParam` (99 filas, ids 1..99, 0x94 bytes), indexado por **valor del stat**
(clamp: fuera de 1..99 → 1), y `RelatePhysicalStatToLevelStatParam` (1 fila de bytes: qué slot alimenta cada stat derivada,
vía `FUN_14038e280`: 1..11 → slot 0..10, otro → 0).

`FUN_14038e1e0` (primer campo derivado, `u16`):
`row(slot0).s16@+4 + Σ_{slot 1..8} row(slot_i).u8@+0x8a`.

Hace falta identificar qué representa cada campo derivado (vida, estamina, carga, etc.) siguiendo a sus consumidores
antes de portarlos.

## Excepción del usuario: subir de nivel con niveles de experiencia (2026-10-31)
- Autorizada por el usuario: 500 almas de DS2 no equivalen a 500 puntos de experiencia de Minecraft y el grind era
  demasiado pesado. **Cada nivel de alma cuesta 1 nivel de experiencia de Minecraft**
  (`LevelUpSoulsTable.ONE_LEVEL_EACH`), en lugar de `PlayerLevelUpSoulsParam` (FUN_14038d140), que sigue cargado pero
  ya no se usa para subir de nivel.
- Las almas siguen siendo los puntos de experiencia para todo lo demás (mancha de sangre, memoria de almas).

## Pantalla de subir nivel: vista previa (investigación del 2026-11-10; `D:\Reversing\DS2\out\levelup\`)
- `FUN_1400cc620(menu, statsActuales)` llena **una sola página**: rejilla de **3 columnas × 13 renglones** (39 celdas,
  `FUN_1400cd9d0` entero / `FUN_1400cdbb0` `%0.1f`). Cada celda muestra **"actual ⇒ previsto"** (`bofire` 20300 = "⇒").
  Actual = stats reales; previsto = la copia del menú (`menu+0x1cb0`) con el mismo equipo (`FUN_14008c700`).
- **Columna 0** (MenuStatsParam por índice, `FUN_140358cf0`; fuera de 1..99 → fila 1): PS (PSPL, como `DerivedStats`),
  Energía MSP[AGU]+0x02, Peso MSP[VIG]+0x38 (f32), Ranuras MSP[APR]+0x06 (0..14), Vel. hechizo MSP[(2 APR+INT+FE)/4]+0x08
  (más un ajuste `FUN_14014d130` sin decodificar), ATQ Fue MSP[FUE]+0x0a, ATQ Des MSP[DES]+0x0c, bonos magia MSP[INT]+0x0e,
  fuego MSP[(INT+FE)/2]+0x10, rayo MSP[FE]+0x12, oscuro MSP[min(INT,FE)]+0x14, veneno MSP[(3 DES+ADP)/4]+0x16, sangrado
  MSP[(3 DES+FE)/4]+0x18.
- **Columna 1** (solo stats, sin armadura): DEF física MSP[(AGU+VIG+FUE+DES)/4]+0x1c, mágica MSP[INT]+0x1e, fuego
  MSP[(INT+FE)/2]+0x20, rayo MSP[FE]+0x22, oscura MSP[min]+0x24, veneno MSP[(3 ADP+VIG)/4]+0x26, sangrado
  MSP[(3 ADP+FE)/4]+0x28, petrificación MSP[(3 ADP+VIT)/4]+0x2c, maldición MSP[(3 ADP+APR)/4]+0x2e, Agilidad +0x32,
  Estabilidad PSPL[min(AGU,ADP)]+0x90. Renglones 0 y 8 sin texto (MSP[FUE]+0x1a, MSP[ADP]+0x2a).
- **Columna 2** (con equipo): ATQ de las armas der. 1..3 / izq. 1..3 = **un número: físico + magia + rayo + fuego +
  oscuro** (`FUN_1402019f0`, con escalado y falta de stats de los stats previstos); Golpe / Corte / Empujón = Σ piezas
  ArmorReinforceParam subtipo 2 / 0 / 1 + Σ ArmorParam +0x1c × DEF física de stats; Estabilidad con armadura.
- Color del previsto: igual → `'g'`, sube → 0x99, baja → 0x98 (qué color es cada código no está en el código). Marca
  0x12 de `LevelUpStatusCalcParam`: resalta las celdas que dependen del atributo con el cursor.
- (Nota: la DEF física de la pantalla es MSP+0x1c; la de combate es PSPL+0x80; no siempre coinciden.)
- **En el mod** (`LevelUpScreen.grid`, `ds2/MenuStats`; `weapons.json → menuStats`): la rejilla completa con "actual ⇒
  previsto". Simplificaciones: un arma por mano (der. 1 = mano principal, izq. 1 = mano secundaria; der./izq. 2 y 3 no
  existen en Minecraft); el previsto que cambia va en azul (color de diseño); sin las marcas de `LevelUpStatusCalcParam`
  (el mod no tiene cursor de atributo); Vel. hechizo sin el ajuste de `FUN_14014d130`; la estabilidad con armadura usa
  la del mod (con la falta de stats), DS2 en este menú no la aplica.
- **Sin confirmar**: los colores de `'g'` / 0x98 / 0x99, si las celdas sin texto se ven, los modificadores de contexto
  `FUN_14014cf80` y familia (se toman como 1), el ATQ a dos manos en el menú.
