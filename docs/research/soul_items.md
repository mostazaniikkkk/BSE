# Almas consumibles (Alma de no muerto perdido / soldado anónimo)

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj` (corridas headless de solo lectura).
Salidas en `D:\Reversing\DS2\out\souls_items\` (`ca60.c`, `soul930.c`, `f520.c`, `gain.c`, `x235a00.txt`,
`x22f520.txt`, `tpf2png.py`, iconos).
Datos: regulation (`ItemParam`, `ItemTypeParam`, `ItemUsageParam`, `SpEffectActiveItem.emevd`, `SpEffectMustSub.emevd`),
`c0001.nmb`, `c000100_pl.tae`, textos `English` / `NeutralSpanish` / `Spanish`, `GameDataEbl.bhd/.bdt`.
Mismo método que la efigie ([hollowing.md](hollowing.md)) y las resinas ([resins.md](resins.md)).

## Resumen
| | Alma de no muerto perdido | Gran alma de no muerto perdido | Alma de soldado anónimo | Gran alma de soldado anónimo |
|---|---|---|---|---|
| ItemParam / SpEffect (`+0x04`) / icono (`+0x00`) | 60630000 | 60640000 | 60650000 | 60660000 |
| **Almas** (`SpEffectActiveItem.emevd`) | **200** | **400** | **800** | **1000** |
| Suma a Soul Memory (`+0xf4`) | sí | sí | sí | sí |
| Modelos en mano (`+0x08/+0x0c/+0x10`) | 1050/1051/1052 | 1050/1051/1052 | 1053/1054/1055 | 1053/1054/1055 |
| `+0x2c` | 702 | 703 | 704 | 705 |
| Máximo (`+0x4a`, probable) | 99 | 99 | 99 | 99 |

Comunes a las cuatro: `+0x30` = 100, `+0x34` = 10, `+0x38` f32 **1.0** (velocidad de uso; la efigie y las resinas
tienen 0.7), `+0x40` ItemTypeParam **550**, `+0x44` ItemUsageParam **1900**, `+0x48` u16 0xffff, `+0x4c` = 0,
`+0x4d` = 1.

- `+0x2c` **no es** el ItemUsageParam (ese es `+0x44`). 702..708 siguen el orden de las almas
  (60630000..60690000); la efigie tiene 2 y las resinas 203..206. Significado **sin decodificar** (¿orden?).
- `+0x30` / `+0x34`: sin decodificar (en la efigie 3000/300, en las resinas 1500/150: siempre `+0x34 = +0x30 / 10`).

## 1. Almas al usar
### SpEffect del objeto (`SpEffectActiveItem.emevd`, decodificado con `out\hollow\evd.py`)
Cada uno es una sola instrucción `100130[01] (1, 930, 0, N)`:
| SpEffect | Instrucción | N |
|---|---|---|
| 60630000 | `100130[01] (1 930 0 200)` | 200 |
| 60640000 | `100130[01] (1 930 0 400)` | 400 |
| 60650000 | `100130[01] (1 930 0 800)` | 800 |
| 60660000 | `100130[01] (1 930 0 1000)` | 1000 |

Las demás almas usan la misma forma (Alma tenue 60625000 = 50, Caballero orgulloso 2000 / 3000, Guerrero valiente 5000,
60700000..60720000 = 8000 / 10000 / 20000, almas de jefe 64xxxxxx). La Señal oscura (60360000) usa `(1 931 1 100)`.

### Cadena en el exe
1. **`100130[01]` → `FUN_14022ca60`** (tabla de instrucciones directas del exe, archivo `0x156f8c0`).
   Es un salto a **`FUN_14022c730`**: lee los argumentos de la instrucción; con el primero = 1 llama
   `FUN_14022f2b0(…, {id = 930, …}, args+1, 4, …)`, que lanza el **SpEffect 930** sobre el personaje y le pasa como
   argumentos los que siguen (`args+1` o `args+2` según `+0x18` del destino) → **(0, N)**.
2. **SpEffect 930** (`SpEffectMustSub.emevd`): `101130[00] (0 0)` (valores por defecto, reemplazados por los pasados).
   `101130[00]` → **`caseD_1f` @ `FUN_14022ccf0`** (tabla, archivo `0x156f9d0`):
   - `cantidad = (float) u32@+4 × m` (`u32@+4` = N; `m` de `FUN_14022d950`, por defecto 1.0, **no trazado**, el mismo
     multiplicador que deja sin trazar resins.md).
   - Crea una entrada `SpEffectEach_SpecialSub` (`FUN_14022ced0` → ctor `FUN_140222600`) en el grupo `+0x68` del
     personaje (`FUN_140235a00`).
   - `FUN_140222780`: `+0x50 = cantidad`, negada si el byte `+0` vale 1 (aquí 0 → **suma**).
   - Para comparar: el SpEffect 931 (`101130[01]` → `caseD_20` @ `FUN_14022cb80`, Señal oscura) usa
     `FUN_1402226e0`: `almasActuales × (valor / DAT_1410acb18)`, con signo negativo si el byte es 1.
3. **Consumo** (update del SpEffect del chr, código ofuscado): `FUN_14022f520` → `FUN_140225db0` suma el `+0x50` de
   **todas** las entradas listas del grupo y las marca como gastadas. Desde el bloque ofuscado
   (`141b75c18` → llamada en `14014c53a` → `141b5a6d4`):
   - total ≥ 0 → `FUN_14023c880` (solo si el chr es el jugador local, lista de `FUN_14037fb90`) →
     **`FUN_140202610(chr, total, 6)`**;
   - total < 0 → `FUN_140203e60(chr, −total, 6)` (solo baja `+0xec`, piso 0; respeta el bloqueo `+0xf0`).
4. **`FUN_140202610`**: si `b8+0x4c8` bit 14 está apagado, existe `chr+0x490` y la cantidad ≠ 0 →
   **`FUN_14038ab40(chr+0x490, cantidad)`**, `b8+0x704 = 1` y `FUN_1402e7410(…, 9)` (aviso a la UI).
   Es la misma función que paga las almas de los enemigos ([enemies.md](enemies.md) §5).
5. **`FUN_14038ab40`** ([souls.md](souls.md)): suma a `+0xec` (almas en mano), **`+0xf4` (Soul Memory, probable)** y
   `+0xfc`, cada uno con tope 999 999 999 y respetando su byte de bloqueo; no hace nada si `b8+0x4b8` bit 59.

**Respuesta**: se ganan exactamente N almas (200 / 400 / 800 / 1000) y **cuentan para Soul Memory** por el mismo camino
que matar enemigos. El factor de almas del jugador (`b8+0x434`, `FUN_140203f60`) **no** se aplica: ese es solo de las
almas de enemigos; aquí el único factor es `m` de `FUN_14022d950`.

## 2. Animación de uso — `a00_18_0090 ItemUseDeamonsSoul` (TAE 180090)
- ItemTypeParam 550: `+0x10` = **90**, `+0x14` (escalera) = 90, `+0x08` f32 0.2, `+0x0c` 0.5, `+0x18` = 8, `+0x1b` = 2
  (efigie 500: 190 / 0.8; resinas 600: 60 / 0.2). `FUN_14032f750` → `ItemUseCategory` = 90 → nodo `a00_18_0090`
  (misma correspondencia por número que la efigie y las resinas; el nodo selector del nmb **no está decodificado**).
- **No es la de la efigie (0190) ni la de las resinas (0060).** Es la animación de "usar alma" (el nombre del clip dice
  `DeamonsSoul`, con la errata).
- `.nsa`: `a00_18_0090_ItemUseDeamonsSoul__57ec13.nsa`, **4.0 s**, no es versión izquierda.
- `ItemUse_Speed` = **1.0** × factores (`ItemParam +0x38`, `FUN_14032f750`). Los tiempos de abajo son del clip.
- Herramienta: `tools/re/weapons.py item_use(180090)` (su campo `events` guarda solo el último evento de cada tipo;
  la tabla sale de `weapons.TAE[180090]`).

| Evento / pista (nmb: fracción × 4.0 s) | Tiempo (s) |
|---|---|
| TAE 220200 modelo 0 (args `26, 0`) | 0.267–1.667 |
| TAE 220200 modelo 1 (args `20, 1`) y modelo 2 (args `7, 2`) | 1.633–1.967 |
| **TAE 220000: aplica el SpEffect de `ItemParam +0x04`** (`FUN_14016b840` → `b8+0x6de`, `FUN_14032f870`) | **1.7** (hasta 1.867) |
| TAE 220100 / 310000 | 0–3.333 / 0–3.5 |
| `CancelControl_Avoid`, `_AttackAll`, `_Guard` | 0–**3.0** |
| `InputControl`, `ItemUseInputControl` | desde 1.967 |
| `ToMoveswitchFoot` | desde 1.964 |
| `ItemUseCancelControl`, `StateControll_1` | desde 3.0 |
| `StateControll` | 3.455–4.0 |
| `ItemUseChangeHide` | 0.182–3.273 |
| `UsingArm` | 0–3.486 |
| `ItemUseDuration`, `IK`, `TimeAct` | 0–4.0 |

- **No hay `CancelControl_Move`**: se usa andando, como la efigie y las resinas.
- Las almas entran cuando el update de SpEffect consume la entrada (paso 3 de arriba), después de 1.7 s; si es el mismo
  frame o el siguiente **no está confirmado**.
- ItemUsageParam 1900 = `ff ff ff ff 07 fe 07 28`: `+5` bit 0 = 0 → no se puede usar en escalera (`FUN_14032f450`,
  igual que la efigie 2140 y la resina 1700). El resto de bytes: sin decodificar.

## 3. Máximo y uso repetido
- `ItemParam +0x4a` u16 = **99** en las cuatro. En toda la tabla: 1 en 1097 filas (armas, armaduras…), 99 en 160
  (consumibles), 999 en 18 (flechas y virotes). Que sea el máximo en inventario es **por datos, no por código**
  (igual que en hollowing.md): **sin confirmar**.
- Uso repetido: cada uso crea **una entrada nueva** de SpecialSub (`FUN_14022ced0`) y `FUN_140225db0` las suma todas,
  así que no se pisan entre sí. No se encontró enfriamiento. El siguiente uso queda limitado por la animación:
  `ItemUseCancelControl` desde 3.0 s (qué habilita exactamente esa pista **no está trazado**).
- `+0x4c` / `+0x4d` (ranura / modo, ver resins.md) = 0 / 1. El modo lo consulta `FUN_14022d8a0` en las instrucciones
  `100080` (propiedades), **no** en `101130`; para las almas no influye según lo leído.

## 4. Iconos
- Regla de hollowing.md: `icon:/tex/Icon/IC_%010d.tpf` con `ItemParam +0x00`. Extraídos con
  `D:\Reversing\DS2\tools\extract_bdt.py` (`gamedata:/menu/tex/icon/IC_00606x0000.tpf`, 8373 bytes cada uno).
- TPF de una textura (`+0x10` offset 0x35, tamaño 0x2080, nombre `IT_IC_00606x0000`) → DDS **DXT5 64×128** → PNG
  recortado al alfa (`out\souls_items\tpf2png.py`; el mismo método reproduce byte a byte el PNG de la efigie).
- **El normal y el grande tienen el mismo icono** (DDS idénticos: 60630000 = 60640000, 60650000 = 60660000).

| Objeto | PNG recortado | Recorte |
|---|---|---|
| 60630000 | `D:\Reversing\DS2\out\souls_items\soul_of_a_lost_undead_icon.png` | 40×61, (12,33)-(52,94) |
| 60640000 | `D:\Reversing\DS2\out\souls_items\large_soul_of_a_lost_undead_icon.png` | 40×61, (12,33)-(52,94) |
| 60650000 | `D:\Reversing\DS2\out\souls_items\soul_of_a_nameless_soldier_icon.png` | 64×65, (0,31)-(64,96) |
| 60660000 | `D:\Reversing\DS2\out\souls_items\large_soul_of_a_nameless_soldier_icon.png` | 64×65, (0,31)-(64,96) |

Completos (64×128) y DDS: `out\souls_items\icon\IC_00606x0000_full.png` / `.dds`.

## 5. Textos
Archivos: `gamedata:/menu/Text/{English,NeutralSpanish,Spanish}/{itemname,SimpleExplanation,DetailedExplanation}.fmg`
(volcados en `out\hollow\text\`; los `.txt` perdieron los acentos, leer los `.fmg` con `tools\dump_fmg.py`).
NeutralSpanish (= es_mx) y Spanish son iguales para estos cuatro.

| Id | English | Corto (en) | Español | Corto (es) |
|---|---|---|---|---|
| 60630000 | Soul of a Lost Undead | Use to acquire souls | Alma de no muerto perdido | Se usa para conseguir almas. |
| 60640000 | Large Soul of a Lost Undead | Use to acquire souls | Gran alma de no muerto perdido | Se usa para conseguir almas. |
| 60650000 | Soul of a Nameless Soldier | Use to acquire souls | Alma de soldado anónimo | Se usa para conseguir almas. |
| 60660000 | Large Soul of a Nameless Soldier | Use to acquire souls | Gran alma de soldado anónimo | Se usa para conseguir almas. |

Largos (`DetailedExplanation`; `/` = salto de párrafo):
- 60630000 en: "Soul of a lost Undead who has long ago gone Hollow. / Use to acquire souls. / The soul is the source of all
  life, and even in undeath, or hollowing, the mind seeks souls."
  es: "El alma de un no muerto que se volvió Hueco hace ya mucho tiempo. / Se usa para conseguir almas. / El alma es la
  fuente de toda vida. Incluso estando no muerta, o Hueca, la mente busca almas."
- 60640000 en: "Large soul of a lost Undead who has long ago gone Hollow. / Use to acquire souls. / (igual)"
  es: "Gran alma de un no muerto que se volvió Hueco hace ya mucho tiempo. / Se usa para conseguir almas. / (igual)"
- 60650000 en: "Soul of a nameless Undead soldier who has long ago gone Hollow. / Use to acquire souls. / (igual)"
  es: "El alma de un soldado no muerto anónimo que se volvió Hueco hace ya mucho tiempo. / Se usa para conseguir almas. /
  (igual)"
- 60660000 en: "Large soul of a nameless Undead soldier who has long ago gone Hollow. / Use to acquire **many** souls. /
  (igual)"
  es: "Gran alma de un soldado no muerto anónimo que se volvió Hueco hace ya mucho tiempo. / Se usa para conseguir
  almas. / (igual)"

## Sin confirmar
- El multiplicador `m` de `FUN_14022d950` (por defecto 1.0; qué efectos lo cambian no está trazado).
- `+0xf4` como Soul Memory sigue siendo "probable" (souls.md); las almas de estos objetos van por `FUN_14038ab40`
  igual que las de enemigos, así que suman a lo mismo.
- Qué valor de `ItemUseCategory` elige qué nodo del nmb (90 → `a00_18_0090` por correspondencia de números).
- Qué hace `ItemUse_Speed` (1.0 aquí, 0.7 en la efigie) dentro del nmb.
- Si las almas entran en el mismo frame que el TAE 220000 o en el siguiente update de SpEffect.
- `+0x4a` = 99 como máximo en inventario (por datos).
- Cuándo se descuenta el objeto del inventario (igual que en la efigie y las resinas).
- `+0x2c`, `+0x30`, `+0x34` de ItemParam y los bytes de ItemUsageParam 1900 salvo `+5` bit 0.
- Qué hace `ItemUseCancelControl` (si permite encadenar otro uso desde 3.0 s).

# Alma de un gigante (Soul of a Giant)

Fuente: mismas herramientas y datos que arriba. Salidas en `D:\Reversing\DS2\out\soul_giant\` (`load.c`, `b.c`, `a.c`,
`strrefs.txt`, `texts.txt`, `text\*common.fmg`) e iconos en `D:\Reversing\DS2\out\items\`.

## 0. Identificación
- `itemname` **50920000** = "Soul of a Giant" / "Alma de un gigante". **ItemParam 50920000** (icono `+0x00` y
  SpEffect `+0x04` = 50920000).
- **No es el alma de jefe del Último gigante.** Esa es **64010000** "Soul of the Last Giant" / "Alma del último gigante"
  (NeutralSpanish; Spanish: "Alma de último Gigante"). `ItemLotParam2_Other` fila **309600** (= BossParam 309600 del
  gigante, [last_giant.md](last_giant.md)) contiene 64010000 en `+0x2c`. 50920000 aparece en las filas 600000,
  60001000 (`+0x30`) y 60004000 (`+0x2c`) de `ItemLotParam2_Other`, no en la 309600. El formato de `ItemLotParam2`
  **no está decodificado** (búsqueda del u32 en las filas), así que qué lote suelta qué es **por datos**.
  Que el jefe del mod suelte el Alma de un gigante es decisión de diseño del usuario, no de DS2.

## 1. ItemParam 50920000
| Campo | Valor | Comparación |
|---|---|---|
| `+0x00` icono | 50920000 | |
| `+0x04` SpEffect | 50920000 | |
| `+0x08/+0x0c/+0x10` modelos en mano | 1059 / 1060 / 1061 | iguales a las almas de jefe 64xxxxxx |
| `+0x2c` | 2200 | sin decodificar (Giant's Kinship 50900000 = 2202) |
| `+0x30` / `+0x34` | 0 / 0 | 64010000: 1000 / 100 |
| `+0x38` f32 velocidad de uso | **0.7** | almas consumibles 1.0; **todas** las almas de jefe 0.7 |
| `+0x40` ItemTypeParam | **550** | igual que las almas de §1–3 |
| `+0x44` ItemUsageParam | **2000** = `ff ff ff ff 07 fe 03 28` | lo comparte con todas las almas de jefe 64xxxxxx |
| `+0x48` | 0xffff | |
| `+0x4a` máximo (probable) | **99** | |
| `+0x4c` / `+0x4d` / `+0x4e` | 0 / 1 / 0 | iguales a las demás almas |

En `ItemParam` la fila es igual a la de 64010000 salvo icono, SpEffect, `+0x2c`, `+0x30` y `+0x34`: es un alma con los
parámetros de uso de un alma de jefe.

## 2. Almas al usar: **10 000**
### Qué emevd tiene el SpEffect — por rango de id
`SpEffectActiveItem.emevd` **no tiene** el evento 50920000. Está en **`SpEffectPassiveItem.emevd`**:
`50920000: 100130[01] (1 930 0 10000)` (decodificado con `out\hollow\evd.py`).

El archivo no lo elige el origen del efecto sino el **rango del id**:
- `FUN_14023d2a0` carga los 11 `SpEffect*.emevd` en un contenedor (ranuras de 0x10 bytes): `+0x08` AbnormalState,
  `+0x18` Weapon, `+0x28` Armor, `+0x38` Spell, `+0x48` Ring, **`+0x58` PassiveItem**, **`+0x68` ActiveItem**,
  `+0x78` Enemy, `+0x88` WideUse, `+0x98` Condition, `+0xa8` MustSub.
- `FUN_14022f2b0` (lanzar SpEffect) busca el evento con `thunk_FUN_141b9e344(contenedor, id)` (ofuscado, seguido con
  `out\py\deob.py 0x141b9e344`), que elige la ranura por rango:

| Rango de id | Ranura |
|---|---|
| 900 000–999 999 | `+0x08` AbnormalState |
| 1 000 000–19 999 999 | `+0x18` Weapon |
| 20 000 000–29 999 999 | `+0x28` Armor |
| 30 000 000–39 999 999 | `+0x38` Spell |
| 40 000 000–49 999 999 | `+0x48` Ring |
| **50 000 000–59 999 999** | **`+0x58` PassiveItem** |
| 60 000 000–69 999 999 | `+0x68` ActiveItem |
| 90 000 000–99 999 999 | `+0x78` Enemy |
| 100 000 000–999 999 999 | `+0x88` WideUse |
| 600–799 | `+0x98` Condition |
| 800–999 | `+0xa8` MustSub |

  Luego busca el evento en esa ranura (`FUN_140b26da0` sobre `+0xc8` del emevd). Así 50920000 → PassiveItem,
  64010000 / 6063xxxx → ActiveItem, 930 → MustSub. El nombre "PassiveItem" es solo el del archivo de los ids 5xxxxxxx.
- El TAE 220000 lanza el SpEffect de `ItemParam +0x04` igual que en §2 (`FUN_14016b840` → `b8+0x6de` →
  `FUN_14032f870`, petición `{+0x04, cuenta, −1.0, ranura(+0x4c), +0x4d/+0x4e}`).

### Resultado
`100130[01] (1, 930, 0, 10000)` es la misma instrucción que las almas de §1: SpEffect 930 → entrada SpecialSub →
`FUN_140202610` → `FUN_14038ab40`. Se ganan **10 000 almas** que suman a almas en mano y a Soul Memory (probable, igual
que arriba). El multiplicador `m` de `FUN_14022d950` tampoco está trazado aquí.
(Para comparar: 64010000, Alma del último gigante, da `(1 930 0 6000)` = 6000, en `SpEffectActiveItem.emevd`.)

### Diálogo de confirmación
`ItemUseCheckDialogParam` tiene fila **50920000** (también 64010000 y las almas de jefe; **no** 60630000):
`+0x00` = 1, `+0x04` = 30000010. En `common.fmg` el id 30000010 es "Use %s?" / NeutralSpanish "¿Usar %s?" /
Spanish "¿Usar\n%s?". Que `+0x04` sea el id del mensaje y `+0x00` = 1 active el diálogo es **por datos** (la lectura
del param en el exe **no está trazada**).

## 3. Animación de uso
- ItemTypeParam 550 → `+0x10` = 90 → **`a00_18_0090 ItemUseDeamonsSoul`** (TAE 180090), la misma que §2 de arriba
  (`weapons.py item_use(180090)`): `.nsa` `a00_18_0090_ItemUseDeamonsSoul__57ec13.nsa`, **4.0 s**, no izquierda.
- **TAE 220000 (aplica el SpEffect) a 1.7 s** (hasta 1.867). `CancelControl_Avoid` 0–**3.0**, `_AttackAll` y `_Guard`
  hasta **3.0**. **Sin `CancelControl_Move`**. El resto de pistas: tabla de §2 de arriba.
- Diferencia: `ItemUse_Speed` = **0.7** (`ItemParam +0x38`), no 1.0. Qué hace dentro del nmb (si es velocidad de
  reproducción y los tiempos pasan a ÷0.7) **sigue sin confirmar**; los tiempos de arriba son los del clip.
- ItemUsageParam 2000: `+5` = 0xfe, bit 0 = 0 → no se puede usar en escalera (`FUN_14032f450`), igual que 1900. Difiere
  de 1900 solo en `+6` (0x03 en vez de 0x07): significado **sin decodificar**.

## 4. Textos
`gamedata:/menu/Text/{English,NeutralSpanish,Spanish}/{itemname,SimpleExplanation,DetailedExplanation}.fmg`
(`out\hollow\text\`, leídos con `tools\dump_fmg.py`). NeutralSpanish (= es_mx) y Spanish son iguales.

| | English | NeutralSpanish / Spanish |
|---|---|---|
| Nombre | Soul of a Giant | Alma de un gigante |
| Corto | The soul possessed by a Giant | El alma poseída por un gigante. |

Largo (`\n` = salto de línea, `/` = línea en blanco):
- en: "The soul of a Giant who came to conquer Drangleic. / Will the Giants' resentment for the King\nbe pacified in
  death, or only emboldened? / Can be used to acquire souls, only..."
- es: "El alma de un gigante que quiso conquistar\nDrangleic.\n¿El rencor que sienten los gigantes hacia el\nrey se
  apaciguará en la muerte o solo se\nintensificará? / Se puede usar para conseguir almas, pero..."

## 5. Icono
- `gamedata:/menu/tex/icon/IC_0050920000.tpf` (8373 bytes) → `D:\Reversing\DS2\out\items\menu__tex__icon__IC_0050920000.tpf`
  → DXT5 64×128 (`IT_IC_0050920000`) → `out\items\icon\IC_0050920000.dds` / `_full.png`.
- Recortado al alfa (`out\souls_items\tpf2png.py`): **`D:\Reversing\DS2\out\items\soul_of_a_giant_icon.png`**, 63×90,
  (0,18)-(63,108).
- Textura 16×16 (`tools/re/icon16.py`, Lanczos, 11×16 centrado):
  `src/main/resources/assets/soulsmode/textures/item/soul_of_a_giant.png`.
- Para comparar, el icono de 64010000 (`ItemParam +0x00` = 64000000, compartido con 64000000) es otro:
  `out\items\soul_of_the_last_giant_icon.png`, 58×91.

## Sin confirmar (Alma de un gigante)
- Lo mismo que las almas de arriba: `m` de `FUN_14022d950`, Soul Memory (`+0xf4`) probable, frame exacto en que entran
  las almas, `+0x4a` = 99 como máximo, cuándo se descuenta el objeto, qué habilita `ItemUseCancelControl`.
- Qué hace `ItemUse_Speed` = 0.7 en el nmb (si alarga la animación).
- `ItemUseCheckDialogParam` (diálogo "¿Usar %s?"): por datos, no trazado en el exe.
- `ItemParam +0x2c` (2200), `ItemUsageParam 2000 +6`.
- Formato de `ItemLotParam2` (de dónde sale cada alma en DS2).
