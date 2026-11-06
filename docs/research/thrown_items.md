# Objetos arrojadizos (Talismán de Lloyd / Bomba incendiaria)

Solo **datos** (params, TAE, nmb, menú); no se trazó código nuevo. El efecto en Minecraft es diseño del usuario, así que
aquí no se investiga qué hace cada objeto en DS2 más allá de lo que dicen los params.
Datos: regulation (`ItemParam`, `ItemTypeParam`, `ItemUsageParam`, `BulletParam`, `PlayerDamageParam`,
`SpEffectActiveItem.emevd`), `c0001.nmb`, `c000100_pl.tae`, textos `English` / `NeutralSpanish` / `Spanish`,
`GameDataEbl.bhd/.bdt`. Mismo método que [soul_items.md](soul_items.md), [resins.md](resins.md) y
[hollowing.md](hollowing.md). Lectura de params: `tools/re/params.py`; balas: `tools/re/spell_export.py` (`bullet()`);
animación: `tools/re/weapons.py` (`item_use()` / `thrown_use()`).

## Resumen
| | Talismán de Lloyd | Bomba incendiaria |
|---|---|---|
| ItemParam / icono (`+0x00`) / SpEffect (`+0x04`) | 60531000 | 60570000 |
| Modelo en mano (`+0x08`; `+0x0c/+0x10` = 0) | 824 | 806 |
| `+0x2c` / `+0x30` / `+0x34` (sin decodificar) | 307 / 1600 / 160 | 71 / 200 / 20 |
| `+0x38` f32 (velocidad de uso, `ItemUse_Speed`) | **0.7** | **0.85** |
| `+0x40` ItemTypeParam | 900 | 1000 |
| `+0x44` ItemUsageParam | 1700 | 1700 |
| `+0x4a` u16 (máximo, probable) | 99 | 99 |
| `+0x4c` / `+0x4d` (ranura / modo de SpEffect, ver resins.md) | 0 / 2 | 0 / 1 |
| Clip | **`a00_18_0100 ItemUseThrowOverhand`** (TAE 180100) | **`a00_18_0110 ItemUseThrowHeavyItem`** (TAE 180110) |
| Duración del clip | **2.667 s** | **3.0 s** |
| Sale la bala (TAE 220000) | **0.533 s** | **0.867 s** |
| Bloqueo de esquiva / ataque / guardia / **movimiento** | 0–**1.5 s** | 0–**1.833 s** |
| En escalera | no | no |

## 1. Animación de uso
### Cadena (igual que la efigie, hollowing.md)
`FUN_14032f750`: `ItemUseCategory = ItemTypeParam +0x10` (en escalera `+0x14`); `ItemUse_Speed = ItemParam +0x38 ×
factores`. El nodo del nmb se elige por correspondencia de números (`a00_18_0XXX`), igual que en los demás objetos: el
nodo selector del nmb **no está decodificado**.

| ItemTypeParam | `+0x08` f32 | `+0x0c` f32 | `+0x10` (categoría) | `+0x14` (escalera) | `+0x18..+0x1b` |
|---|---|---|---|---|---|
| 900 (Lloyd) | 0.0 | 0.5 | **100** | 100 | `08 00 00 02` |
| 1000 (Bomba; también la Bomba negra 60560000) | 0.0 | 0.5 | **110** | 110 | `08 00 00 02` |

- 100 → `a00_18_0100_ItemUseThrowOverh_48135958.nsa` (xmd `a00_18_0100_ItemUseThrowOverhand`), 2.6667 s.
- 110 → `a00_18_0110_ItemUseThrowHeavy_5e2b6607.nsa` (xmd `a00_18_0110_ItemUseThrowHeavyItem`), 3.0 s.
- Ninguno es versión izquierda. Los tiempos de abajo son del clip (a velocidad 1.0); qué hace exactamente
  `ItemUse_Speed` (0.7 / 0.85) dentro del nmb **no está confirmado** (igual que en soul_items.md).

### TAE (`c000100_pl.tae`)
| Evento (args) | Lloyd 180100 (s) | Bomba 180110 (s) |
|---|---|---|
| 220100 (`b8+0x53c` += 1 mientras dura, hollowing.md; uso sin identificar) | 0–1.5 | 0–1.833 |
| 280100 (args 0; sin identificar, no aparece en los otros usos de objeto) | 0–1.5 | 0–2.333 |
| 310000 (arg 150000000; como en los demás objetos) | 0–2.167 | 0–2.5 |
| 220200 `ChrItemCtrl`: modelo 0 (`+0x08`) visible en la mano, en la dummy del arg | 0.167–**0.6**, dummy **26** | 0.267–**0.9**, dummy **20** |
| **220000: aplica el SpEffect de `ItemParam +0x04`** (`FUN_14016b840` → `FUN_14032f870`) | **0.533** (hasta 0.633) | **0.867** (hasta 1.0) |

**Qué suelta el objeto**: el TAE 220000 aplica el SpEffect del objeto, y ese SpEffect es una sola instrucción
`100120[00]` que lanza la bala (§3). El modelo en mano se oculta 0.067 s (Lloyd) / 0.033 s (Bomba) después.

### Pistas del nmb (fracción × duración)
| Pista | Lloyd (s) | Bomba (s) |
|---|---|---|
| `CancelControl_Avoid`, `_AttackAll`, `_Guard` | 0–**1.5** | 0–**1.833** |
| **`CancelControl_Move`** | 0–**1.5** | 0–**1.833** |
| `InputControl`, `ItemUseInputControl` | desde 0.767 | desde 1.333 |
| `ItemUseAdjustControl` | 0–1.233 | 0–1.0 |
| `ToMoveswitchFoot` | 1.309–2.655 | 1.473–2.986 |
| `ItemUseCancelControl`, `StateControll_1` | desde 1.5 | desde 1.833 |
| `StateControll` | 2.303–2.667 | 2.591–3.0 |
| `ItemUseChangeHide` | 0.121–1.5 | 0.136–1.833 |
| `UsingArm` | 0–2.467 | 0–2.767 |
| `ItemUseDuration`, `IK`, `TimeAct` | 0–2.667 | 0–3.0 |

- **A diferencia de la efigie, las resinas y las almas, los dos lanzamientos sí tienen `CancelControl_Move`**: el
  jugador no se mueve hasta 1.5 / 1.833 s. Por eso `weapons.py` tiene `thrown_use()` (= `item_use()` + `moveLock`),
  exportado a `data/soulsmode/ds2/weapons.json` como `lloyds_talisman` y `firebomb`.
- Qué habilita `ItemUseCancelControl` / `ItemUseAdjustControl` (¿girar mientras se apunta?) **no está trazado**.

### Escalera
ItemUsageParam 1700 = `ff ff ff ff 07 fc 07 00`: `+5` bit 0 = 0 → **no se puede usar en escalera** (`FUN_14032f450`,
igual que la resina, que usa la misma fila). Resto de bytes sin decodificar.

## 2. Textos
`gamedata:/menu/Text/<idioma>/{itemname,SimpleExplanation,DetailedExplanation}.fmg`, leídos con
`D:\Reversing\DS2\tools\dump_fmg.py` (`out\hollow\text\*.fmg`). `\n` del original = salto de línea; aquí `/` marca
párrafo.

### NeutralSpanish (= es_mx)
- **60531000 "Talismán de Lloyd"** — corto: "Bloquea recuperación de estus en un área limitada."
  Largo: "Talismán usado por los caballeros clérigos.\nBloquea la recuperación de estus en un\nárea limitada. /
  Dicen que los caballeros clérigos usaban estos\ntalismanes para cazar a seres no muertos. / Los caballeros clérigos
  luchaban con orgullo.\nAl bloquear la recuperación de los no muertos,\ntambién podían luchar con total impunidad."
- **60570000 "Bomba incendiaria"** — corto: "Al explotar causa daño de fuego."
  Largo: "Urna de terracota llena de polvo negro.\nAl explotar causa daño de fuego. / Esta urna es un arma tosca y
  común,\npero su uso continuado se debe a su\nsimplicidad y a lo efectiva que resulta\nen diversas situaciones."

### Spanish
Igual que NeutralSpanish salvo el largo de Lloyd, segundo párrafo: "**Se dice que** los caballeros clérigos usaban
estos\ntalismanes para **dar caza** a seres no muertos."

### English
- **Lloyd's Talisman** — "Blocks Estus recovery within a limited area" / "Talisman used by cleric knights.\nBlocks Estus
  recovery within a limited area. / It is said that the cleric knights used these\ntalismans to hunt down accursed
  Undead. / Cleric knights fight with pride,\nand by blocking the recovery of the Undead,\nthey can also fight with
  impunity."
- **Firebomb** — "Explodes, inflicting fire damage" / "Bisque urn filled with black powder.\nExplodes, inflicting fire
  damage. / Though this urn is a crude and commonplace\nweapon, it owes its continued usage to its\nsimplicity and
  effectiveness in a variety\nof situations."

Nota: los textos hablan del Estus (efecto de DS2). El efecto del mod es del usuario; si el texto del mod se cambia, es
decisión del usuario, no de DS2.

## 3. Bala (SpEffect → BulletParam)
`SpEffectActiveItem.emevd` (decodificado con `out\hollow\evd.py`), una instrucción cada uno:
| SpEffect | Instrucción |
|---|---|
| 60531000 | `100120[00] (60531000, 60531000, 0, 1.0)` |
| 60570000 | `100120[00] (60570000, 60570000, 5, 0.65)` |
| (comparación) Bomba negra 60560000 / 60580000 / 60590000 | `(…, …, 4, 0.65)` / `(…, …, 6, 0.65)` / `(…, …, 2, 0.26)` |

- Args 1 y 2 = (id de BulletParam, id de PlayerDamageParam): **por datos** (las dos filas existen con ese id, como el par
  (bullet, daño) de los hechizos, casting.md §4). El handler de `100120[00]` no está trazado.
- Args 3 (0 / 5 / 4 / 6 / 2) y 4 (1.0 / 0.65 / 0.26): **sin decodificar**.

### Bomba incendiaria — campos (significado de cada campo: casting.md §3 y §8)
**Bala principal BulletParam 60570000 + PDP 60570000**
| Campo | Valor |
|---|---|
| Vida `+0x48` | **5.0 s** |
| Velocidad inicial `+0x4c` | **10.0** u/s (× `petición+0x50`, sin trazar para objetos) |
| Aceleración `+0x50/+0x54` | 0 / 0 |
| Velocidad máxima `+0x58` | 50.0 |
| Gravedad `+0x5c × +0x64` | **5.0 × 2.0 = 10.0 u/s²**, desde `t ≥ +0x60 (0.0) × petición+0x54` |
| Ángulo vertical `+0x14` | **−0.2618 rad (−15°)**; positivo = abajo → sale **15° hacia arriba** |
| Abanico / dispersión | 1 bala, 1 disparo, sin dispersión |
| Origen `+0x04` / orientación `+0x05` | 0 = dummy `PDP+0x31` = **20** del dueño / 1 = dirección de apuntado del jugador |
| Desplazamiento `+0x08/+0x0c/+0x10` | 0 / 0 / 0 |
| Apuntado con objetivo `+0x1c/+0x20` | 0.1745 / 0.1222 rad; adelanto horizontal `+0x2d` = 1 |
| Homing `+0x98/+0x90/+0x94` | 0.1745 rad/s, hasta 10.0 u, ángulo 1.5708 |
| Contacto `+0xb0..+0xb3` | 0 / 0 / 0 / 2 → **se destruye al tocar personaje o escenario** |
| `+0x106` | 0 |
| Hijos `+0xec` / `+0xed` | **al impactar y al acabar la vida**: 60570010 y 60570011 |
| SFX `+0x00` / impacto `+0xbc` | 807 / 808 |
| Golpe directo (PDP 60570000) | cápsula radio 0.25, largo 1.5; **100 fuego** (`+0x0c`, código `+0x19` = 3), piso 70, tasa 1.0, estamina 310, equipos `+0x40` = 0xF2 |

**Explosión BulletParam 60570010 + PDP 60570010** (origen 2 = matriz de la bala padre, orientación 3)
- Vida **0.3 s**, sin movimiento.
- Esfera de radio **0.5 → 1.3** en **0.25 s** (`PDP +0x34 → +0x74` en `+0x78`).
- **100 de fuego** (plano `+0x0c`, código 3), piso 70, tasa 1.0, daño a la estamina en guardia 310.
- Equipos `+0x40` = 0xF0 (bit 0x10 hostil; sin 0x04 propio ni 0x08 aliado; bits 0x20..0x80 sin identificar).
- Reacción (DCP): nivel 2 → reacción 2 (golpe directo: 3), velocidad 1.15 (`spell_export.damage`).

**Segundo hijo BulletParam 60570011 + PDP 60570011**: esfera de radio 1.2, vida 0.3 s, **sin daño** (plano 0, tasa 0) y
equipos 0. Para qué sirve **sin identificar** (¿objetos del mapa?).

Unidades: las de mundo de DS2, integradas con `dt` en segundos (casting.md §3).
**Daño final** (cómo se arma el golpe de un objeto, sin arma ni catalizador; defensa de la víctima): **no trazado**.

### Talismán de Lloyd (solo referencia)
Bala 60531000: misma física que la bomba (vida 5.0, velocidad 10, gravedad 5.0 × 2.0 **desde 0.25 s**, ángulo vertical
0), PDP sin daño; al impactar (no al expirar) crea 60531010: esfera radio 1.0, vida 0.5 s, sin daño, equipos 0x30, aplica
el **SpEffect 60531010** al que toca (`100040[01]` SFX y `1010[00]`, sin decodificar). No se usa: el efecto del mod es del
usuario.

## 4. Iconos
- Instalación de DS2: **`D:\Programas\Steam\steamapps\common\Dark Souls II Scholar of the First Sin\Game\`**
  (`GameDataEbl.bdt`, 13 136 248 552 bytes). El `.bhd` descifrado es `D:\Reversing\DS2\extracted\GameDataEbl.bhd`.
- `extract_bdt.py` → `gamedata:/menu/tex/icon/IC_0060531000.tpf` y `IC_0060570000.tpf` (8373 bytes cada uno) → TPF de
  una textura (`IT_IC_00605x1000`/`…70000`) → DDS **DXT5 64×128** → PNG recortado al alfa (`out\souls_items\tpf2png.py`).

| Objeto | PNG recortado | Recorte | 16×16 del mod |
|---|---|---|---|
| 60531000 | `D:\Reversing\DS2\out\items\lloyds_talisman_icon.png` | 64×69, (0,32)-(64,101) | `textures/item/lloyds_talisman.png` (15×16 centrado) |
| 60570000 | `D:\Reversing\DS2\out\items\firebomb_icon.png` | 64×72, (0,32)-(64,104) | `textures/item/firebomb.png` (14×16 centrado) |

- Completos y DDS: `out\items\icon\IC_00605x0000_full.png` / `.dds`; TPF en `out\items\`.
- 16×16: `tools/re/icon16.py` — lado mayor a 16 con **PIL Lanczos**, proporción conservada, centrado en un lienzo
  transparente (mismo método que el ascua de la adversidad). Sin retoque a mano.

## Sin confirmar
- Qué nodo del nmb elige cada `ItemUseCategory` (100 → `a00_18_0100`, 110 → `a00_18_0110` por correspondencia de
  números, como en los demás objetos).
- Qué hace `ItemUse_Speed` (0.7 / 0.85) dentro del nmb.
- Significado de los args 3 y 4 de `100120[00]` y de `petición+0x50/+0x54` (factores de velocidad y de inicio de la
  gravedad) para balas lanzadas por objetos.
- Qué es el TAE 280100 (solo en los lanzamientos) y para qué sirve el hijo sin daño 60570011.
- Cómo se calcula el daño final del golpe de la bomba (100 de fuego plano, piso 70, tasa 1.0 son datos del PDP; la
  fórmula para objetos no está trazada) y si el golpe directo y la explosión pueden pegar ambos al mismo objetivo.
- Bits 0x20..0x80 de los equipos del PDP (si la explosión puede dañar al lanzador: el bit 0x04 está apagado).
- `+0x4a` = 99 como máximo en inventario (por datos), cuándo se descuenta el objeto, y `+0x2c/+0x30/+0x34`.
- Qué habilitan `ItemUseCancelControl` e `ItemUseAdjustControl`.
- Los bytes de ItemUsageParam 1700 salvo `+5` bit 0.
