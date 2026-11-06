# Hechizos: animaciones de lanzamiento y bullets (proyectiles)

Salidas de Ghidra: `D:\Reversing\DS2\out\cast_*.c` / `cast_*.txt`. Datos: `SpellParam`, `BulletParam`,
`PlayerDamageParam` (regulation), `c000100_pl.tae`, `c0001.nmb`. Convenciones de TAE y de las pistas
`CancelControl_*` como en [weapons.md](weapons.md) y [animations.md](animations.md): mientras una pista
`CancelControl_X` está activa, X está bloqueado.

## 1. Qué animación lanza cada hechizo

### El arma: tipo de movimiento 12
- El catalizador es un arma normal. Su `WeaponActionCategoryParam` pone en los slots de ligero (0/1, 10/11, 40/41…)
  filas de `WeaponAttackMotionParam` de **tipo 12** con un TAE de relleno (20010010).
  - Sorcerer's Staff 3800000 (categoría 3800), Pyromancy Flame 5400000 (5400) y los chimes (4010000…) igual.
  - El fuerte (slots 2/3) es un golpe físico normal (tipo 2; bastón: `a24_01_0010/0020`).
- La animación real del hechizo no sale del arma: sale de `SpellParam`.

### `SpellParam` (índice 0x23, slot `+0x540`, getter `FUN_140359030`)
Fila de 0x108 bytes, id = id del hechizo (nombre en `ItemName.fmg`, por ejemplo 31010000 = Soul Arrow).
- **8 entradas de 0x14 bytes** en `+0x14 + i·0x14`: `(f32, f32, i32 TAE de preparación, i32 TAE de lanzamiento, f32)`.
  - `FUN_1401feab0` y `FUN_1401fdc50` (la versión de red) guardan `entrada+0x08` en `+0x13c` y `entrada+0x0c` en
    `+0x140`. Qué id es la preparación y cuál el lanzamiento lo dice el nombre de los `.nsa` (`_Prepare_` / `_CF_`).
  - **Elección de la entrada**: getter en `0x140397bab` (ofuscado; desofuscado con `out\py\deob.py`).
    Recibe un código `c` (byte `+0x0d` de la petición) y un selector `s` (3 o 4, según el byte `+0x0e`):
    - `c` = 2 o 6 → entradas 0/1; `c` = 3 → 2/3; `c` = 4 → 4/5; `c` = 5 → 4/5 si el byte `fila+0x04` = 0, si no 6/7;
      otro valor → ninguna;
    - `s` = 1 o 3 → la entrada par; 2 o 4 → la impar.
    - **Confirmado** (sección 6): `c` = `desc+0x04` (agarre 2..6, el mismo campo de stamina.md) y `s` = `desc+0x08`
      (el caso de `FUN_140393c00` solo entra con `desc+0x08` = 1 o 2). Qué es `desc+0x08`: sin confirmar.
  - **En los datos, las 8 entradas son iguales en todos los hechizos del jugador** (31…–35…). La única excepción es la
    fila 71210000, que no es del jugador. Así que, para el jugador, cada hechizo tiene **una** preparación y **un**
    lanzamiento, cojas el catalizador como lo cojas.
  - Floats de cada entrada (Soul Arrow 0.25 / 0 / 0.8; Heal 1.0 / 0.5 / 2.5; Lightning Spear 0.575 / 0 / 1.5):
    **lector sin localizar**.
- Otros campos, con su lector cuando lo hay:
  - `+0x00` u8: escuela. 0 = hechicería (31…), 1 = milagro (32…), 2 = piromancia (33…), 3 = hex (34…), 4 = hex (35…).
    Es un dato; el lector no está localizado.
  - `+0x08` / `+0x0a` u16: Soul Arrow 10 / 0, Heal 0 / 12, Dark Orb 12 / 10, Lightning Spear 0 / 22, Fireball 0 / 0.
    Parecen los requisitos de Int / Fe. **Lector sin localizar.**
  - `+0x0c`, `+0x10`, `+0x100`, `+0x104` i32: los cuatro valen el id del hechizo. BulletParam y PlayerDamageParam
    tienen filas con ese mismo id (sección 4). **Resuelto**: `FUN_140390050` lee `+0x0c` = id de BulletParam y
    `+0x10` = id de PlayerDamageParam (catalysts.md §3, sección 6). `+0x100` / `+0x104`: lector sin localizar.
  - `+0xf0..+0xfb` u8: tabla de usos. `FUN_1401ab750`:
    `usos = floor(SpellParam[+0xf0 + k] · chr.b8+0x484)`, con `k` = byte `+0x2f` del objeto de `FUN_14038b990()`.
    - Soul Arrow: `[1, 30, 31, 32, 33, 34, 35, 36, 37, 38, 40, 0]`.
    - Qué es `k` (nivel de algo) y qué es `b8+0x484` (multiplicador): sin confirmar.

### Las animaciones de c0001
Solo hay dos grupos de lanzamiento: **a38 = `StickSpell` (bastón)** y **a39 = `BellSpell` (campana)**.
- No existe un grupo propio para la llama de piromancia ni para los hexes. Las piromancias usan
  `a38_11_11xx…17xx` y los hexes usan animaciones de a38 o de a39, según el hechizo.
  **La animación la elige el hechizo, no el catalizador.**
- Todas están hechas para la mano derecha (`_R`). La izquierda es el espejo, como en las armas.
  - **Confirmado** (sección 6): la mano izquierda reproduce el mismo `.nsa _R` bajo un nodo espejo (tipo 0x87).
- Ninguna Prepare es un bucle (bool del atributo 0x00 del nodo = 0; convención en weapons.md, "Caída"). **No hay
  hechizos que se carguen manteniendo el botón.** Prepare → CF: ver sección 6.

| Preparación → lanzamiento | Escuela | Hechizos |
|---|---|---|
| 38110010 → 38110020 | hechicería / hex | Soul Arrow (todas las variantes), Soul Spear, Crystal Soul Spear, Soul Geyser, Yearn, Focus Souls · Dark Orb, Dark Hail, Dark Fog, Dead Again |
| 38110010 → 38110120 | hechicería / hex | Hush, Fall Control, Repair, Chameleon · Repel, Twisted Barricade |
| 38110010 → 38110220 | hechicería | Soul Greatsword |
| 38110010 → 38110420 | hechicería / hex | Homing Soulmass, Homing Crystal Soulmass, Shockwave, Soul Shower, Soul Bolt, Cast Light, Unleash Magic, Soul Flash · Affinity, Numbness, Recollection |
| 38110010 → 38110520 | hechicería / hex | Soul Vortex · Whisper of Despair |
| 38110010 → 38110720 | hechicería / hex | Magic / Great Magic / Crystal Magic Weapon, Hidden Weapon · Dark Weapon |
| 38110010 → 38110820 | hechicería | Soul Spear Barrage |
| 38110010 → 38110920 | hechicería | Strong Magic Shield |
| 38110010 → 39110220 | hex | Dark Greatsword |
| 38111110 → 38111120 | piromancia | Fireball, Fire Orb, Great Fireball, Great Chaos Fireball |
| 38111110 → 38111220 | piromancia | Poison Mist, Toxic Mist, Acid Surge, Lingering Flame, Warmth |
| 38111110 → 38111320 | piromancia | Flash Sweat, Iron Flesh, Immolation |
| 38111110 → 38111420 | piromancia | Flame Weapon |
| 38111110 → 38111520 | piromancia | Outcry |
| 38111110 → 38111720 | piromancia | Fire Whip |
| 38111510 → 38111520 | piromancia | Firestorm, Fire Tempest, Chaos Storm |
| 38111510 → 38110620 | piromancia | Fire Snake |
| 38111610 → 38111620 | piromancia | Flame Swathe, Forbidden Sun, Dance of Fire |
| 39110010 → 38111620 | piromancia | Combustion, Great Combustion |
| 39110010 → 39110020 | milagro | Great Heal Excerpt, Heavenly Thunder, Blinding Bolt |
| 39110010 → 39110520 | milagro / hex | Replenishment, Resplendent Life, Great Magic Barrier, Homeward, Guidance, Unveil, Perseverance, Denial · Promised Walk of Peace |
| 39110010 → 39110620 | milagro / hex | Sunlight Blade · Resonant Weapon |
| 39110010 → 38110020 | hex | Resonant Soul, Great Resonant Soul, Climax |
| 39110110 → 39110120 | milagro / hex | Heal, Med Heal, Great Heal, Soothing Sunlight, Bountiful Sunlight, Caressing Prayer · Scraps of Life, Resonant Flesh, Lifedrain Patch |
| 39110110 → 39110320 | milagro / hex | Wrath of the Gods, Soul Appease · Darkstorm, Profound Still |
| 39110110 → 39110420 | milagro | Magic Barrier, Sacred Oath |
| 39110710 → 39110720 | milagro | Lightning Spear, Great Lightning Spear, Sunlight Spear, Splintering Lightning Spear |
| 39110810 → 39110820 | milagro / hex | Force · Dark Dance |
| 39110910 → 39110920 | milagro | Emit Force |

- **Ningún hechizo usa**:
  - las variantes a dos manos (`38130010` / `38130020`, `39130010`) ni a dos armas (`38150010`, `39150010`). Tienen
    nodo en la red, pero nada en `SpellParam` las referencia. **Cómo se llega a ellas: sin confirmar** (¿un
    interruptor `IsTwoHand` de la red?);
  - la preparación `38110610`.
- **TAE sin `.nsa` ni nodo**: 38111010 / 38111020 / 38111810 / 38111820.
- **Lanzamiento fallido**:
  - c0001 tiene `a38_11_5030 StickSpellNoBullet_R` (TAE 38115030, 2.0 s, sin eventos TAE).
  - `a00_18_0170 ItemUseFailed` (2.667 s) es la de los objetos.
  - **NoBullet, confirmado** (spells.md, `FUN_140395930`): cualquier fallo de `FUN_1403963b0` (sin usos, sin
    Int/Fe, catalizador sin la escuela) pide el id fijo `0x24596d6` = 38115030. Vale para cualquier catalizador: no
    hay variante de campana. Detalle en la sección 6.
  - Con estamina 0: el ataque normal se rechaza (`FUN_140367a80`, stamina.md). En `FUN_1403963b0` no hay
    comprobación de estamina (spells.md). Que la petición de hechizo pase por `FUN_140367a80` **no está
    confirmado**.

## 2. Tiempos de cada animación (TAE + `c0001.nmb`)
- **Duración**: `.nsa +0x28`.
- **Bullet**:
  - **TAE 2300** (`0x8fc`, `ChrMorphemeTimeActTrackBulletCtrl` = `FUN_140325880`). `param[0]` = slot 0..9 de la tabla
    de bullets de `ChrAttackDamageCtrl` (`chr+0x230 → +0x760 + slot·0xc4`).
    - Al empezar el evento: `FUN_1403740e0` copia el slot; si su `+0x00` (id de bullet) ≠ 0, lo lanza:
      `FUN_140373c00` → `FUN_140373880`, que lo encola en `+0x150`, como mucho 4 a la vez. También sube el bit 0 de
      `chr.b8+0x4c0`.
    - No lo lanza si `chr.b8+0x5cc ≠ 0`.
    - Al terminar, o si se cancela (bit 14 de `b8+0x4c8` y `FUN_140164d30`), lo libera (`FUN_140374790`).
    - **No cobra estamina**, a diferencia del 2200.
  - **TAE 2301** (`0x8fd`): bullet directo, `param` = (id de bullet, id de daño). Arma la petición sin pasar por la
    tabla y llama a `FUN_140373880`. Solo aparece en `39110220` (34300022 / 34300022, Dark Greatsword).
  - **Cola → mundo**: `FUN_1403751c0` → `FUN_140337fb0` → `FUN_1404415a0(gestor de bullets = DAT_1416148f0+0x68,
    {id de bullet, id de daño})`.
- Casi todos los lanzamientos tienen **dos 2300 a la vez, slot 0 y slot 5**. Excepciones: `38110220` y `38111720`
  solo tienen el slot 0; `38111520` tiene un slot 0 extra a 1.333 s. **Qué slot llena el hechizo, y cuándo se llena
  la tabla `+0x760`, está sin confirmar.**
  - Los slots se rellenan en un trozo ofuscado del inicio de ataque. La llamada a `thunk_FUN_141b3e578`
    (`0x1403747b0`) en `0x140391e1e` viene de `0x141c388d9`: `rdx` = slot, `rcx` = ctrl, `r8` = petición.
    No está desofuscado.
- **Regen de estamina bloqueada**: TAE 101100, hasta la columna "101100". Sin regen mientras dura.
- **Coste de estamina**:
  - `FUN_14038fa50` (stamina.md) incluye el tipo de movimiento 12 en la base `WeaponParam +0x38`, multiplicada por
    `WSC[slot]`. Sorcerer's Staff: 233 × 0.95 = 221.35 a la derecha, 239.06 a la izquierda. Cleric's Sacred Chime:
    219 → 208.05. Pyromancy Flame: 315 → 299.25.
  - **Resuelto en la sección 6**: el coste se multiplica por `SpellParam` `bloque+0x10` y se cobra al empezar la
    pista `ShootStateControl` (userData 107) de la Prepare.
- **Otros eventos sin significado**: 110200, 111600, 120100, 120200 (todos los lanzamientos) y 2202 (`param` 12010) en
  las preparaciones de piromancia 38111110 / 38111510 / 38111610.
- **Pistas de la red** (sin lector confirmado): `SpellDuration` (u122), `ShootStateControl` (u107) en Prepare y
  `AttackStateControl` (u100/101/102).

Tiempos en segundos (fracción de la pista × duración). "Atk" = bloqueado hasta. `sN@t` = evento 2300, slot N, a los t s.

| TAE | Animación | Dur. | Bullet | 101100 hasta | Avoid | Move | Atk 1L/1H | Atk 2L/2H | Guard / AttackAll | InputControl | ShootState | StateControll |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 38110010 | Stick Prepare_00 | 0.5 | — | 0.6 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.333–0.5 | — |
| 38110020 | Stick CF_00 | 2.1667 | s0, s5 @0.567 | 1.5 | 0–1.4 | 0–1.4 | 1.2 / 1.2 | 1.133 / 1.133 | — | 0.567– | — | 1.5– |
| 38110120 | Stick CF_01 | 2.6667 | s0, s5 @0.6 | 2.0 | 0–2.267 | 0–2.267 | 2.167 | 2.167 | — | 0.933– | — | 2.167– |
| 38110220 | Stick CF_02 | 2.3333 | s0 @0.167 | 1.833 | 0–1.667 | 0–1.733 | 1.5 | 1.5 | — | 0.867– | — | 1.833– |
| 38110320 | Stick CF_03 | 4.3333 | s0, s5 @0.333 | 3.0 | 0–3.667 | 0–3.667 | 3.167 | 3.167 | — | 1.517– | — | 3.833– |
| 38110420 | Stick CF_04 | 3.0 | s0, s5 @0.833 | 2.167 | 0–2.0 | 0–2.067 | 2.0 | 2.0 | — | 1.05– | — | 2.233– |
| 38110520 | Stick CF_05 | 3.3333 | s0, s5 @0.967 | 2.5 | 0–2.167 | 0–2.333 | 2.0 | 1.933 | — | 1.167– | — | 2.567– |
| 38110610 | Stick Prepare_06 | 0.5 | — | 0.667 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.333–0.5 | — |
| 38110620 | Stick CF_06 | 3.0 | s0, s5 @0.833 | 2.5 | 0–2.333 | 0–2.333 | 2.167 | 2.167 | — / 0–3.0 | 0.785– | — | 2.5– |
| 38110720 | Stick CF_07 | 4.0 | s0, s5 @0.667 | 3.333 | 0–2.5 | 0–2.5 | 2.333 | 2.333 | — / 0–4.0 | 1.303– | — | 2.833– |
| 38110820 | Stick CF_08 | 3.1667 | s0, s5 ×7 @0.567, 0.733, 0.9, 1.067, 1.233, 1.4, 1.567 | 2.5 | 0–2.333 | 0–2.333 | 2.167 | 2.167 | — | 0.828– | — | 2.667– |
| 38110920 | Stick CF_09 | 4.0 | s0, s5 @0.833 | 3.333 | 0–2.5 | 0–2.5 | 2.333 | 2.333 | — / 0–4.0 | 1.303– | 2.667–4.0 | 2.833– |
| 38111110 | Stick Prepare_11 | 1.0 | 2202(12010) 0.333–1.333 | 1.333 | — | — | — | — | 0–1.0 / 0–1.0 | — | 0.65–0.95 | — |
| 38111120 | Stick CF_11 | 1.8333 | s0, s5 @0.767 | 1.5 | 0–1.333 | 0–1.333 | 1.3 | 1.3 | — | 0.479– | — | 1.333– |
| 38111220 | Stick CF_12 | 1.8333 | s0, s5 @0.667 | 1.5 | 0–1.333 | 0–1.333 | 1.167 | 1.167 | — | 0.479– | — | 1.333– |
| 38111320 | Stick CF_13 | 2.8333 | s0, s5 @1.0 | 2.167 | 0–2.2 | 0–2.2 | 2.167 | 2.167 | — | 0.741– | — | 2.333– |
| 38111420 | Stick CF_14 | 4.0 | s0, s5 @0.833 | 3.333 | 0–2.5 | 0–2.5 | 2.333 | 2.333 | — | 1.046– | — | 3.5– |
| 38111510 | Stick Prepare_15 | 1.1667 | 2202(12010) 0.333–1.333 | 1.333 | — | — | — | — | 0–1.167 / 0–1.167 | — | 0.758–1.108 | — |
| 38111520 | Stick CF_15 | 3.0 | s0, s5 @0.833; s0 @1.333 | 2.5 | 0–2.333 | 0–2.333 | 2.167 | 2.167 | — | 0.785– | — | 2.5– |
| 38111610 | Stick Prepare_16 | 0.6667 | 2202(12010) 0.333–1.0 | 1.0 | — | — | — | — | 0–0.667 / 0–0.667 | — | 0.433–0.633 | — |
| 38111620 | Stick CF_16 | 1.8333 | s0, s5 @0.467 | 1.5 | 0–1.167 | 0–1.167 | 0.867 | 0.867 | — | 0.479– | — | 1.333– |
| 38111720 | Stick CF_17 | 2.3333 | s0 ×11, de 0.533 a 1.533 cada 0.1 | 2.0 | 0–2.0 | 0–2.0 | 1.833 | 1.833 | — | 0.61– | — | 1.833– |
| 38115030 | StickSpellNoBullet_R | 2.0 | — | — | 0.267–1.167 | — | — | — | 0–1.267 / 0–2.0 | 0.433– | — | 1.5– |
| 38130010 | Stick 2Hand Prepare_00 | 0.5 | — | 0.6 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.333–0.5 | — |
| 38130020 | Stick 2Hand CF_00 | 2.1667 | s0, s5 @0.567 | 1.5 | 0–1.267 | 0–1.333 | 0.9 / 1.033 | 0.767 / 0.9 | — | 0.567– | — | 1.667– |
| 38150010 | Stick 2Weapon Prepare_00 | 0.5 | — | 0.6 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.333–0.5 | — |
| 39110010 | Bell Prepare_00 | 0.6667 | — | 0.833 | — | — | — | — | 0–0.667 / 0–0.667 | — | 0.433–0.633 | — |
| 39110020 | Bell CF_00 | 2.8333 | s0, s5 @0.667 | 2.167 | 0–2.167 | 0–2.167 | 1.833 | 1.833 | — | 0.923– | — | 2.333– |
| 39110110 | Bell Prepare_01 | 1.0 | — | 1.167 | — | — | — | — | 0–1.0 / 0–1.0 | — | 0.667–0.95 | — |
| 39110120 | Bell CF_01 | 2.8333 | s0, s5 @0.667 | 2.333 | 0–2.0 | 0–2.0 | 1.833 | 1.833 | — | 0.923– | — | 2.333– |
| 39110220 | Bell CF_02 | 2.3333 | s0, s5 @0.167; 2301(34300022) @0.633 | 1.833 | 0–1.667 | 0–1.733 | 1.5 | 1.5 | — | 0.867– | — | 1.833– |
| 39110320 | Bell CF_03 | 2.8333 | s0, s5 @0.667 | 2.167 | 0–2.0 | 0–2.0 | 1.833 | 1.833 | — | 0.923– | — | 2.333– |
| 39110420 | Bell CF_04 | 2.8333 | s0, s5 @0.833 | 2.133 | 0–2.0 | 0–2.0 | 1.833 | 1.833 | — | 0.923– | — | 2.333– |
| 39110520 | Bell CF_05 | 3.8333 | s0, s5 @1.667 | 3.5 | 0–3.0 | 0–3.0 | 3.0 | 3.0 | — | 1.249– | — | 3.333– |
| 39110620 | Bell CF_06 | 4.0 | s0, s5 @0.667 | 3.333 | 0–3.0 | 0–3.0 | 2.667 | 2.667 | — | 1.303– | — | 3.5– |
| 39110710 | Bell Prepare_07 | 1.0 | — | 1.167 | — | — | — | — | 0–1.0 / 0–1.0 | — | 0.667–0.95 | — |
| 39110720 | Bell CF_07 | 1.8333 | s0, s5 @0.767 | 1.5 | 0–1.5 | 0–1.5 | 1.333 | 1.333 | — | 0.479– | — | 1.333– |
| 39110810 | Bell Prepare_08 | 0.5 | — | 0.667 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.333–0.475 | — |
| 39110820 | Bell CF_08 | 1.3333 | s0, s5 @0.167 | 1.0 | 0–1.091 | 0–1.091 | 0.97 | 0.97 | — | 0.349– | — | 0.97– |
| 39110910 | Bell Prepare_09 | 0.6667 | — | 0.833 | — | — | — | — | 0–0.667 / 0–0.667 | — | 0.444–0.633 | — |
| 39110920 | Bell CF_09 | 2.0 | s0, s5 @0.7 | 1.667 | 0–1.636 | 0–1.636 | 1.455 | 1.455 | — | 0.523– | — | 1.455– |
| 39130010 | Bell 2Hand Prepare_00 | 0.5 | — | 0.667 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.325–0.475 | — |
| 39150010 | Bell 2Weapon Prepare_00 | 0.5 | — | 0.667 | — | — | — | — | 0–0.5 / 0–0.5 | — | 0.325–0.475 | — |

- Todos los 2300 duran 0.1 s.
- **Prepare** bloquea guardia y ataques entera (`CancelControl_Guard` / `_AttackAll` de 0 a 1). **No tiene
  `CancelControl_Avoid` ni `_Move`.** La red pone la Prepare como capa del torso sobre la locomoción
  (sección 6).
- **CF** (lanzamiento): se puede rodar o moverse desde el final de Avoid / Move; volver a lanzar (Attack1L/1H),
  desde la columna Atk.

## 3. `BulletParam` (banco de bullets, `FUN_14048b510`)
- El banco tiene 4 params: BulletParam, EnemyBulletParam, SystemBulletParam y BulletAttachParam. La consulta por id
  (`thunk_FUN_141b75ce3` → `0x140443050`) elige el param por rango:
  - id < 100 000 000 → `banco+0x20` (BulletParam);
  - < 200 000 000 → `+0x30` (EnemyBulletParam);
  - < 1 000 000 000 → `+0x40` (SystemBulletParam).
- Fila de 0x140 bytes.
- **Id de daño** (segundo id de la petición): `thunk_FUN_141b0fe63` → `0x14014c655` (banco de daño `+0xb40`,
  id < 100 000 000 → `+0x10`). Que ese param sea `PlayerDamageParam` es **inferido**: la fila 31010000 existe en PDP
  y los campos que se leen (`+0x30..+0x78`) caben en sus 0x7c bytes.
- **Unidades**: todo se integra con `dt` en segundos.
  - `FUN_14012f080` acumula `t += dt` y lo compara con tiempos de la fila (`+0x50`, `+0x60`, vida).
  - `FUN_1401305b0` hace `v += a·dt` y `pos += v·dt`.
  - Velocidades en unidades de mundo de DS2 por segundo; ángulos en radianes. **No depende de 30/60 fps.**

### Emisión (`BulletEmitterCtrl`: `FUN_14043f1a0` crea, `FUN_14043f3b0` actualiza, `FUN_1404404a0` dispara)
| Campo | Uso confirmado |
|---|---|
| `+0x2e` u8 | bullets por disparo (bucle de `FUN_1404404a0`) |
| `+0x2f` i8 | número de disparos: se repite mientras `contador < +0x2f`; < 0 = sin límite. Con 0 dispara una vez |
| `+0x38` f32 | intervalo entre disparos (s) |
| `+0x14/+0x18`, `+0x30/+0x34`, `+0x3c/+0x40` | ángulo base + `i_bullet · (+0x30/+0x34)` + `i_disparo · (+0x3c/+0x40)` (abanico) |
| `+0x24/+0x28` | dispersión aleatoria ± (`FUN_14043f560`, dos ángulos) |
| `+0x1c/+0x20` | ángulos que se pasan a `FUN_14043fc40` cuando hay objetivo (`+0x20` como límite). **Sin analizar** (¿apuntado automático?) |
| `+0x08/+0x0c/+0x10` | desplazamiento del origen (leído en `FUN_1404404a0`; uso exacto sin analizar) |
| `+0x2c`, `+0x2d`, `+0x44..+0x47` u8 | banderas del disparo (`FUN_1404404a0`); **sin identificar** |
| `+0x04/+0x05` u8 | modo de origen (`FUN_1404402b0`); **sin identificar** |
| `+0x06` u8 | sincronización por red (`FUN_1404415a0`: 1 / 3); **sin identificar** |

### Objeto (`BulletObject`: init `FUN_140445020`, update `FUN_140445ce0`)
| Campo | Uso confirmado |
|---|---|
| `+0x48` f32 | **vida (s)**: `vida = petición+0x7c · +0x48`; se destruye cuando `0 ≤ vida ≤ t` (`FUN_140445ce0`). Vida < 0 = infinita |
| `+0x00` i32 | SFX del proyectil (`BulletRenderer` / `FXCGSfxCtrl`, `FUN_140448110`). `+0xb8` u8 = bandera del renderer |
| `+0xb4` i32 | SFX al lanzar (`FUN_140446ee0`, si el byte `petición+0x78` ≠ 0) |
| `+0xbc` i32 | SFX orientado al impactar (`FUN_1404468e0`). `+0xba/+0xbb` u8: usar la posición o la normal del contacto; `+0xd1` u8: orientación fija hacia arriba |
| `+0x100` i32 | otro efecto al impactar (`FUN_140446570` → gestor `DAT+0x1148`); **sin identificar** |
| `+0xb0..+0xb3` u8 | qué hace al tocar: personaje (`+0xb0`), objetos por tipo de material (`+0xb1` / `+0xb2`), otro (`+0xb3`). Valor ≠ 0 = sigue; 0 = se destruye (`FUN_1404457c0`) |
| `+0x106` u8 | al chocar (`FUN_140445720`): 1 = destruir; 2 = rebotar (`FUN_140447420`) |
| `+0x108/+0x10c/+0x110`, `+0x118/+0x11c/+0x120`, `+0x124` | rebote: ángulos base ± aleatorio, y factor de velocidad |
| `+0xd4/+0xd8`, `+0xdc/+0xe0`, `+0xe4/+0xe8` | **hasta 3 bullets hijos**, cada uno (id de bullet, id de daño) (`FUN_140445ec0`) |
| `+0xec` u8 | hijos al impactar (`FUN_1404457c0`) |
| `+0xed` u8 | hijos al acabar la vida (`FUN_140447900`) |
| `+0xee` u8 | hijos y destrucción por proximidad al objetivo: `|dx| < +0xf4` y `|dy| < +0xf8` |
| `+0xef` u8 / `+0x128` | hijos cada `+0x128` s |
| `+0xfc` f32 | retardo antes de que se puedan emitir hijos |

### Movimiento (`BulletShotMotion`: init `FUN_14012e900`, update `FUN_14012f080` → `FUN_1401305b0`)
Se usa si `petición+0x70 == 0`; si no, `BulletAttachMotion` (`FUN_14012a220`), pegado al lanzador.

| Campo | Uso confirmado |
|---|---|
| `+0x4c` f32 | velocidad inicial: `v0 = dir · +0x4c · petición+0x50` |
| `+0x50` / `+0x54` | aceleración `+0x54` (por segundo, en la dirección del bullet) desde `t ≥ +0x50`. Si `+0x54 < 0`, solo mientras `+0x4c + (t − +0x50)·+0x54 > 0` |
| `+0x58` f32 | velocidad máxima (`|v|` se recorta a [0, `+0x58`]) |
| `+0x5c` · `+0x64` | gravedad: `v.y −= +0x5c · +0x64 · dt` desde `t ≥ +0x60 · petición+0x54` (vector `(0, −1, 0)`: `DAT_141136f20 − DAT_141136f70`) |
| `+0x68` f32 | empuje constante en la dirección `motion+0xa0` (de la petición); **origen sin identificar** |
| `+0x6c`, `+0x7c`, `+0xa4` | se copian en el init (`motion+0xe8`, `+0xe4`, bandera si `+0xa4 > 0`); **uso sin analizar** |
| **Homing** (`FUN_14012f2d0`) | solo con objetivo (`motion+200`, bandera `+0xe1`) |
| `+0x98` | velocidad de giro (rad/s): el giro por frame se limita a `+0x98 · dt` (comparación de cosenos) |
| `+0x9c` | el homing solo actúa con `t > +0x9c` |
| `+0xa0` | si ≠ 0, no hay homing mientras `t ≤ +0xa0` |
| `+0x90` | se deja de seguir al recorrer `+0x90` (> 0) |
| `+0x94` | se deja de seguir si el ángulo al objetivo es ≥ `+0x94` |
| `+0x12c` | se deja de seguir a menos de `+0x12c` del objetivo |
| `+0x8d` u8 | restricción vertical del giro (1 / 2) |
| petición `+0x4c` → `motion+0xdc` | se deja de seguir más allá de esa distancia |

- **Volumen de golpe** (fila de daño; `FUN_140445970`):
  - `+0x33` u8: 1 = cápsula de largo `+0x38`; 0 = esfera;
  - `+0x34`: radio;
  - si `+0x78 > 0`, el radio crece linealmente de `+0x34` a `+0x74` en `+0x78` s;
  - `+0x31` / `+0x30`: banderas que lee el emisor.
- **No encontrado en BulletParam**: ataque y SpEffect al impactar. Van por el id de daño (PDP, ver damage.md), que no
  he seguido para los bullets.
- **Penetración**: no hay un campo propio. Es `+0xb0..+0xb3` ≠ 0 (no se destruye al tocar).

## 4. Hechizos de referencia
Ids: bullet = daño = id del hechizo (filas presentes en BulletParam y PDP). Que el juego use ese par lo respalda
2301, que trae (bullet, daño). Para los 2300, la fuente del id no está confirmada (sección 2).

| | Soul Arrow 31010000 | Fireball 33010000 | Lightning Spear 32140000 | Dark Orb 34010000 | Homing Soulmass 31070000 |
|---|---|---|---|---|---|
| Animación | 38110010 → 38110020 | 38111110 → 38111120 | 39110710 → 39110720 | 38110010 → 38110020 | 38110010 → 38110420 |
| Disparo (TAE 2300) | 0.567 s de 2.167 | 0.767 s de 1.833 | 0.767 s de 1.833 | 0.567 s de 2.167 | 0.833 s de 3.0 |
| Bullets × disparos | 1 × 1 | 1 × 1 | 1 × 1 | 1 × 1 | 1 × 1 |
| `+0x4c` v0 / `+0x58` vmáx | 25 / 50 | 17 / 50 | 30 / 50 | 15 / 50 | 0 / 50 |
| Aceleración `+0x54` | 0 | 0 | 10 desde t = 0 | 0 | 0 |
| Gravedad `+0x5c`·`+0x64` desde `+0x60` | — | 6 × 1.8 = 10.8 desde 0.3 s | — | — | — |
| Vida `+0x48` | 1.25 s | 2.0 s | 1.2 s | 1.0 s | 2.0 s |
| Homing `+0x98` / `+0x94` / `+0x90` | 0.349 rad/s / 60° / — | 0.873 rad/s / 180° / 8 | — | 0.698 rad/s / 45° / — | — |
| Golpe (PDP `+0x33`, `+0x34`, `+0x38`) | cápsula r 0.1, l 2.5 | cápsula r 0.15, l 1.0 | cápsula r 0.1, l 1.0 | cápsula r 0.15, l 1.5 | esfera r 3.0 |
| Hijos | — | 33010001 + 33010010 al impactar y al acabar (`+0xec`, `+0xed`): vida 0.5, v0 0, esfera r 0.5 | 32140010 al impactar / acabar: vida 0.2, esfera r 0.5 → 1.5 en 0.2 s; → 32140020 (r 1.5, vida 1.0) → 32140021 (r 0.25) | — | — |
| SFX `+0x00` / `+0xb4` / `+0xbc` | 5000 / 5375 / 5160 | 5090 / 0 / 5240 | 5061 / 5376 / 5213 | 5130 / 5378 / 5280 | 0 / 5375 / 0 |
| Origen `+0x10` | 0 | 0 | 0.5 | −0.5 | 0 |
| Usos (`+0xf0..`) | 1, 30, 31 … 38, 40 | 1, 8, 8, 9 … 14, 15 | 1, 3, 3, 4 … 13, 15 | 1, 20, 21 … 28, 30 | 1, 4, 4, 4 … 7, 8 |

- **Homing Soulmass**: el bullet 31070000 no se mueve (v0 = 0) y no tiene hijos. Los orbes son las filas
  31070010–31070024: vida 30 s, origen `+0x08` = 2.0, proximidad `+0xee` y el hijo 31070060, un proyectil
  (v0 12, homing 0.349 rad/s, ±90°, 10 de recorrido). **Cómo pasa el juego de 31070000 a los orbes no está
  localizado.** No se puede portar todavía.
- El homing de Soul Arrow y Dark Orb solo actúa con objetivo (fijado). **No he seguido** de dónde sale el objetivo
  (`motion+200`; `FUN_14016d7c0` en `FUN_140337fb0`).
- Los multiplicadores de la petición (`+0x50` velocidad, `+0x54` retardo de gravedad, `+0x7c` vida) los inicializa la
  petición. **Qué valores pone para los hechizos: sin confirmar.**

## 5. Para `tools/re/ds2_anims.py` (tabla `OTHER`)
Todas sin bucle (atributo 0x00 = 0), hechas para la derecha (`_R`):

```python
    # Spell casts (docs/research/casting.md): a38 = StickSpell, a39 = BellSpell. Prepare (..10) -> cast (..20).
    'ds2_38110010': (38110010, False), 'ds2_38110020': (38110020, False), 'ds2_38110120': (38110120, False),
    'ds2_38110220': (38110220, False), 'ds2_38110320': (38110320, False), 'ds2_38110420': (38110420, False),
    'ds2_38110520': (38110520, False), 'ds2_38110610': (38110610, False), 'ds2_38110620': (38110620, False),
    'ds2_38110720': (38110720, False), 'ds2_38110820': (38110820, False), 'ds2_38110920': (38110920, False),
    'ds2_38111110': (38111110, False), 'ds2_38111120': (38111120, False), 'ds2_38111220': (38111220, False),
    'ds2_38111320': (38111320, False), 'ds2_38111420': (38111420, False), 'ds2_38111510': (38111510, False),
    'ds2_38111520': (38111520, False), 'ds2_38111610': (38111610, False), 'ds2_38111620': (38111620, False),
    'ds2_38111720': (38111720, False), 'ds2_38115030': (38115030, False),   # StickSpellNoBullet_R
    'ds2_38130010': (38130010, False), 'ds2_38130020': (38130020, False),   # 2Hand (not referenced by SpellParam)
    'ds2_38150010': (38150010, False),                                      # 2Weapon (not referenced)
    'ds2_39110010': (39110010, False), 'ds2_39110020': (39110020, False), 'ds2_39110110': (39110110, False),
    'ds2_39110120': (39110120, False), 'ds2_39110220': (39110220, False), 'ds2_39110320': (39110320, False),
    'ds2_39110420': (39110420, False), 'ds2_39110520': (39110520, False), 'ds2_39110620': (39110620, False),
    'ds2_39110710': (39110710, False), 'ds2_39110720': (39110720, False), 'ds2_39110810': (39110810, False),
    'ds2_39110820': (39110820, False), 'ds2_39110910': (39110910, False), 'ds2_39110920': (39110920, False),
    'ds2_39130010': (39130010, False), 'ds2_39150010': (39150010, False),
```

Mínimo para los hechizos de referencia: 38110010, 38110020, 38110420, 38111110, 38111120, 39110710, 39110720.

| TAE | `.nsa` |
|---|---|
| 38110010 | `a38_11_0010_StickSpell1HandLi_e93737fb.nsa` |
| 38110020 | `a38_11_0020_StickSpell1HandLig_842e5c9.nsa` |
| 38110120 | `a38_11_0120_StickSpell1HandLi_94c24055.nsa` |
| 38110220 | `a38_11_0220_StickSpell1HandLi_3582b346.nsa` |
| 38110320 | `a38_11_0320_StickSpell1HandLi_a90216da.nsa` |
| 38110420 | `a38_11_0420_StickSpell1HandLi_73c248d7.nsa` |
| 38110520 | `a38_11_0520_StickSpell1HandLi_ef42ed4b.nsa` |
| 38110610 | `a38_11_0610_StickSpell1HandLi_4f252e3b.nsa` |
| 38110620 | `a38_11_0620_StickSpell1HandLi_4e021e58.nsa` |
| 38110720 | `a38_11_0720_StickSpell1HandLi_d282bbc4.nsa` |
| 38110820 | `a38_11_0820_StickSpell1HandLi_ff43bff5.nsa` |
| 38110920 | `a38_11_0920_StickSpell1HandLi_63c31a69.nsa` |
| 38111110 | `a38_11_1110_StickSpell1HandLig_fc71c7c.nsa` |
| 38111120 | `a38_11_1120_StickSpell1HandLig_4767caf.nsa` |
| 38111220 | `a38_11_1220_StickSpell1HandLi_a5368fbc.nsa` |
| 38111320 | `a38_11_1320_StickSpell1HandLi_39b62a20.nsa` |
| 38111420 | `a38_11_1420_StickSpell1HandLi_e376742d.nsa` |
| 38111510 | `a38_11_1510_StickSpell1HandLi_379bf991.nsa` |
| 38111520 | `a38_11_1520_StickSpell1HandLi_7ff6d1b1.nsa` |
| 38111610 | `a38_11_1610_StickSpell1HandLi_6492f571.nsa` |
| 38111620 | `a38_11_1620_StickSpell1HandLi_deb622a2.nsa` |
| 38111720 | `a38_11_1720_StickSpell1HandLi_4236873e.nsa` |
| 38115030 | `a38_11_5030_StickSpellNoBulle_b9a83229.nsa` |
| 38130010 | `a38_13_0010_StickSpell2HandLi_9c12ea02.nsa` |
| 38130020 | `a38_13_0020_StickSpell2HandLi_b3b32101.nsa` |
| 38150010 | `a38_15_0010_StickSpell2Weapon_dcafbabf.nsa` |
| 39110010 | `a39_11_0010_BellSpell1HandLig_eb4d7e59.nsa` |
| 39110020 | `a39_11_0020_BellSpell1HandLig_960d94aa.nsa` |
| 39110110 | `a39_11_0110_BellSpell1HandLig_da1698fc.nsa` |
| 39110120 | `a39_11_0120_BellSpell1HandLig_7f3026fc.nsa` |
| 39110220 | `a39_11_0220_BellSpell1HandLig_40b7edb1.nsa` |
| 39110320 | `a39_11_0320_BellSpell1HandLig_a98a5fe7.nsa` |
| 39110420 | `a39_11_0420_BellSpell1HandLig_3fb87b2b.nsa` |
| 39110520 | `a39_11_0520_BellSpell1HandLig_d685c97d.nsa` |
| 39110620 | `a39_11_0620_BellSpell1HandLig_e9020230.nsa` |
| 39110710 | `a39_11_0710_BellSpell1HandLig_7dcecf22.nsa` |
| 39110720 | `a39_11_0720_BellSpell1HandLight_3fb066.nsa` |
| 39110810 | `a39_11_0810_BellSpell1HandLig_655356c6.nsa` |
| 39110820 | `a39_11_0820_BellSpell1HandLig_c1a7561f.nsa` |
| 39110910 | `a39_11_0910_BellSpell1HandLig_5408b063.nsa` |
| 39110920 | `a39_11_0920_BellSpell1HandLig_289ae449.nsa` |
| 39130010 | `a39_13_0010_BellSpell2HandLig_37b7a41c.nsa` |
| 39150010 | `a39_15_0010_BellSpell2WeaponL_562dc6fe.nsa` |

## 6. Bloqueos de implementación (segunda ronda)
Salidas: `D:\Reversing\DS2\out\cast_368e70.c`, `cast_188*.c`, `cast_c262.c`. Trazas desofuscadas:
`out\case_140394148.txt` y `out\spell\f_1403936a0.txt`. SM decodificados con `out\plunge\sm.py`.

### Id de bala e id de daño — cerrado
- `FUN_1403936a0` es el inicio de una acción de hechizo (`case_140394148.txt`, el caso de `FUN_140393c00` para
  `desc+0x08` = 1 o 2). Llama a `FUN_140390050` (catalysts.md §3): `+0x0c` → bala y `+0x10` → PDP, con el
  desplazamiento opcional de `FUN_140391900`. Su salida es `{bala, daño, coste en almas}`.
- Luego `FUN_140391bf0(ctx, desc, slot = desc+0x1c, …)` arma el golpe en `chr+0x230`, en el **slot `desc+0x1c`**
  (catalysts.md: la bala usa el mismo slot de golpe, `FUN_1403747b0`).
- Qué valor tiene `desc+0x1c`, y por qué el TAE tiene 2300 en los slots 0 y 5 a la vez: **sin confirmar**. Cada 2300
  solo lanza si su slot tiene bala (`FUN_140325880`), así que no salen dos balas a menos que se llenen los dos slots.

### 1. Estamina del lanzamiento — confirmado
- **Coste** (`case_140394148.txt`):
  ```
  bloque = FUN_140397ba0(SpellParam, desc+0x04, desc+0x08)    // getter de 0x140397bab, sección 1
  si bloque ≠ null: desc+0x24 = FUN_14038fb10(desc) × bloque+0x10    // 0x1403941e4, 0x1406e0190
  ```
  - `FUN_14038fb10` (stamina.md) da `WP+0x38 × WSC[slot] × (1 + falta)`. **Sí, `SpellParam` lo modifica**: el
    factor es el último float del bloque elegido.
  - Datos de `bloque+0x10`: Soul Arrow / Dark Orb / Fireball 0.8, Lightning Spear 1.5, Homing Soulmass 1.5, Heal 2.5.
  - Sorcerer's Staff, derecha: Soul Arrow `221.35 × 0.8 = 177.08`. Cleric's Chime, derecha: Heal
    `208.05 × 2.5 = 520.1`, Lightning Spear `208.05 × 1.5 = 312.1`. Pyromancy Flame, Fireball: `299.25 × 0.8 = 239.4`.
  - Con `bloque` nulo (fallo, NoBullet) no se calcula el coste.
- **Se deja preparado**: `FUN_1403936a0` escribe `chr.b8+0x1ac = desc+0x24` (`0x141b67c75`).
  - `FUN_140393970` (rama `desc+0x4e == 1`) escribe `b8+0x1ac = 0` y no prepara bala. **Qué es `desc+0x4e`: sin
    confirmar.**
- **Se cobra con una pista de la red, no con un evento TAE**:
  - `ChrEventTrackActionAttack` (`FUN_140368e70`), caso `0x6b` = userData **107** = pista **`ShootStateControl`**.
    Al **entrar** en la pista: `FUN_1403335d0(control de estamina, b8+0x1ac, 4)`, el mismo cobro de tipo 4 que el
    2200, sin el multiplicador. Mientras dura, `chr.c0+0x68 += 1`.
  - Ni el 2300 ni el inicio de la acción cobran.
  - En los datos, `ShootStateControl` solo está en las Prepare (sección 2): Soul Arrow, 0.333 s de la 38110010;
    Heal, 0.667 s de la 39110110; Lightning Spear, 0.667 s de la 39110710; Fireball, 0.65 s de la 38111110. La
    excepción es el CF `38110920` (Strong Magic Shield), que también la tiene, de 2.667 a 4.0 s: **si cobra dos veces
    no lo comprobé en juego**.
- Las demás pistas del mismo manejador: `SpellDuration` (122 = `0x7a`) → `chr.c0+0x5c` += 1;
  `AttackDuration` (120 = `0x78`) → `c0+0x54` y `b8+0x7e4 = 1`; `AttackStateControl` (106 = `0x6a`) → `b8+0x7e5 = 1`
  al empezar. Quién los lee: sin confirmar.

### 2. Condiciones y lanzamiento fallido — confirmado (en spells.md)
- Las comprobaciones están en spells.md, "Lanzar: comprobaciones": `FUN_140395930` → `FUN_140396ba0` →
  `FUN_1403963b0`. Por orden:
  - un `thunk` ofuscado sin identificar;
  - **usos** ≥ 1 (2 en el caso `SpellParam+0x04`);
  - **requisitos** `+0x08` Int / `+0x0a` Fe, contra los atributos efectivos 6 / 7 (`FUN_1403978c0`);
  - **catalizador**: el byte de escuela `WTP +0x00/+0x01/+0x02/+0x03/+0x53` (`FUN_140396510`);
  - objetivo (`FUN_140397a00`).
- **Cualquier fallo** → animación `0x24596d6` = **38115030 `StickSpellNoBullet_R`** (2.0 s) y fila nula.
  - Es un id fijo: con campana, llama o bastón sale la misma. **No hay variante de campana en c0001.**
  - Con la fila nula, `bloque` es nulo: no hay coste calculado (arriba) ni bala.
- **Estamina 0**: `FUN_1403963b0` no la mira (spells.md). Si el filtro general `FUN_140367a80` (stamina.md) se aplica
  a la petición de hechizo: **sin confirmar**.
- Los usos no se gastan en la comprobación (abajo).

### Gasto del uso — `chr+0x188` (spells.md, "Al disparar")
- El objeto de `chr+0x188` lo crea `FUN_14037c910` (`FUN_14038eef0`, 0x60 bytes; `FUN_14038f010` guarda el chr en
  `+0x00`).
- **`FUN_1403936a0` recibe ese mismo objeto como `ctx`**. El diseño coincide: `[ctx]` = chr y `[ctx]+0x230` =
  `ChrAttackDamageCtrl`. Escribe en él:
  - **`+0x20` = id de la bala del hechizo** (`0x141b743aa`, salida `+0x00` de `FUN_140390050`);
  - `+0x38` = `entrada+0x10` del struct `r8` (sin identificar);
  - `+0x3c` = coste en PS: `chr.b8 +0x33c/+0x340/+0x344/+0x348/+0x34c` según `[SpellParam]` 0..4 (bloques
    `0x140393869..0x140393881`);
  - `+0x40` = coste en almas (salida `+0x08` de `FUN_140390050`, `SpellSoulConsumeParam`).
  - `+0x14` / `+0x18` / `+0x1c`: **sus escritores no están localizados**.
- `FUN_14037d300` (= `PlayerActionCtrl` vtable `+0x148`, `0x1410e4ab0`; mismo cuerpo que `FUN_14038f0c0`) compara
  `(*arg)+0x40` con `+0x20`. Si coincide y la mano (`FUN_14034e730((*arg)+0x68)`) tiene la marca `obj+0x10+mano`:
  `FUN_140348590` gasta **1 uso**, resta PS (`+0x3c`) y almas (`+0x40`), y los pone a 0.
- **Qué evento es**: no hay ningún tipo de evento TAE. Se compara con un **id de bala**. `(*arg)+0x40` y `+0x68`
  coinciden con los campos de la petición de bala que lee `BulletObject` (`FUN_140445020`: `+0x40` id de bala,
  `+0x68` dueño).
  - Lectura más directa: **el uso se gasta cuando se crea la bala del hechizo**, es decir, en el 2300 que la
    lanza (Soul Arrow, 0.567 s de la 38110020).
  - **Sin confirmar**: quién llama a `vt+0x148` (hay más de 200 llamadas indirectas `+0x148`; no la aislé).
- **La estamina no la cobra este objeto**: va por `b8+0x1ac` y `ShootStateControl` (arriba).
- Consecuencia de la lectura anterior, **sin confirmar**: si el lanzamiento se cancela antes del 2300, se habría
  cobrado la estamina pero no el uso.

### 3. Prepare → lanzamiento en `c0001.nmb` — parcial
- SM **`0x120c`** (`StateMachine_Main|…|SM_MoveOrAttackGuard|BT_Attack_MoveGuard|MoveAttack_Ref|BT_MoveAttack`).
  Entra desde dos pseudoestados: `0x120d` (derecha) y `0x120e` (izquierda).
  - Cada transición es una condición **0x273 sobre el CP `0x1d` `AttackID_Number`** = id de TAE sin el grupo
    (`38110010 → 110010`, `38110020 → 110020`…):
    - 110010 / 110110 / 130010 / 150010 → los SM de Prepare (`0x1210`, mezcla 0.01 s); en las Prepare, una segunda
      condición 0x271 sobre `IsMoving` (CP `0x82`) da dos variantes, las dos al mismo destino;
    - 110020…110620 → el SM de lanzamiento `0x125b` (mezcla 0.001 s).
  - **El paso de Prepare a CF no es automático al acabar la Prepare: ocurre cuando el código cambia
    `AttackID_Number` al id del CF** (escritor y momento: apartado 5 de esta sección).
  - Las Prepare a dos manos y a dos armas (130010 / 150010) se eligen igual, por `AttackID_Number`. Por eso ningún
    `SpellParam` las nombra: el código pone el número; quién lo hace, sin confirmar.
- Dentro del SM de Prepare `0x1210`:
  - el estado `0x1211` pasa al `0x1220` (mezcla 0.2 s, método 3) con la condición **0x262**;
  - 0x262 (`FUN_14065ad50`) mira la pista de duración **canal 100, userData 120 (`AttackDuration`)** del estado
    origen y se cumple cuando el valor muestreado (`entrada+0xc`) cruza hacia abajo 0.4;
  - **ese valor es la fracción que le queda al evento** (1 → 0; animations.md, "Condición 0x262"). `AttackDuration`
    cubre toda la Prepare, así que la espera empieza al **60 %** de la Prepare (0.3 s en `a38_11_0010`, de 0.5 s),
    un frame después, con mezcla de 0.2 s no lineal;
  - `0x1220` es la Prepare (nodo `0x1223`, tipo 0x6d) sobre `AdditiveStand` / `AdditiveBreathe`;
  - el tipo 0x6d es un SingleFrame: pose fija en la fracción de su CP (wall_bounce.md, "Otros cambios del
    exportador").
- **Moverse durante el lanzamiento**:
  - Prepare: el estado `0x1211` tiene dos subestados. `0x1212` es un **FeatherBlend2 (0x72, capa del torso; convención
    de weapons.md)** de la Prepare (`0x120f`) sobre la locomoción `0x1745 → 0x611 → SM 0x120`; `0x1213` es la Prepare
    sola. Se pasa de uno a otro (0.1 s) con 0x273 sobre `FB_Speed` (CP `0x1`).
  - CF: `0x126a` / `0x127a` también llevan un FeatherBlend2 (`0x125c` / `0x126c`) del CF sobre la misma locomoción.
  - **Estructuralmente se puede andar mientras se prepara y se lanza: piernas de locomoción, torso del hechizo.**
  - Lo que no está confirmado: si la entrada de movimiento mueve al personaje con `CancelControl_Move` activo en el
    CF (su lector no está confirmado, animations.md), y el giro durante la Prepare.

### 4. Mano izquierda — confirmado (estructura)
- El pseudoestado izquierdo `0x120e` lleva a `0x127d` (Prepare) y `0x12aa` (CF). Sus animaciones están bajo nodos
  **tipo 0x87** (`0x1291`, `0x1292`, `0x12bb`), el espejo que weapons.md identificó para la postura izquierda.
- **No hay animaciones propias de la izquierda**: es el mismo `.nsa _R` espejado. Coincide con el exportador
  (espejo de la mano izquierda).
- Qué decide la entrada derecha o izquierda: la mano de la petición; la condición de entrada no está leída.

### 5. Quién pone el lanzamiento en `AttackID_Number`, y cuándo (tercera ronda)
Salidas: `out\cast_cp770.c` (`FUN_14035c770`, `FUN_14035cf40`), `cast_cda0.c`, `cast_72c.c`, `cast_560.c`.
Trazas: `out\case_140394148.txt`, `out\spell\f_1403936a0.txt`.

**Escritor de los CP (confirmado).**
- `FUN_14035c770` registra los CP del control de ataque: índice 0 = `AttackID_Category`, 1 = `AttackID_Number`,
  3 = `IsAttackHold`, 5 = `Attack_RotationBlend`, 6 = `Attack_MoveBlend`…
- Su actualización `FUN_14035cf40` hace un `switch` sobre el estado de acción `chr.b8+0x10` y llama a
  `FUN_14035cda0(ctrl, anim, mensaje)`. Esa función refresca los CP 0 y 1 (`FUN_1404e2890(…, 0 / 1)`) y envía el
  mensaje 0 o 1 de la red (`FUN_1404e2cf0(ctrl+0x18, mensaje, 1)`).

| `b8+0x10` | Animación | Mensaje |
|---|---|---|
| 0x29 / 0x2a | `chr.c0+0x00` | 0 / 1 (ataque normal) |
| 0x2d / 0x2e | **`chr.c0+0x04`** | 0 / 1 |
| 0x2f / 0x30 | **`chr.c0+0x08`** | 0 / 1 |

- Que el mensaje 0 / 1 sea derecha / izquierda (los pseudoestados `0x120d` / `0x120e`): **sin confirmar**.

**Qué hay en `c0+4` y en `c0+8` (confirmado).** El caso de hechizo de `FUN_140393c00` (`case_140394148.txt`) recibe
una petición `R` (`rsp+0x38`, la que llena `vt+0x88`). Según su byte `R+0x14`:
- `R+0x14 ≠ 0` → `FUN_1403936a0`:
  - `c0+4 = R+0xc` (animación 1, la **preparación**);
  - `ctx+0x38 = R+0x10` (animación 2, el **lanzamiento**; `ctx` = objeto de `chr+0x188`);
  - `FUN_140391ab0(ctx, desc, 0x2d, 0x2e)` (`r8d = r9d − 1`).
  - Esto concuerda con `FUN_1401feab0`, que guarda la animación 1 en `+0x13c` y la 2 en `+0x140`.
- `R+0x14 == 0` y `R+0x16 == 1` → `FUN_140393970` (lanzamiento sin bala, coste 0): `c0+4 = R+0xc` y estado
  0x2d / 0x2e.
- `R+0x14 == 0`, `R+0x16 ≠ 1` y `ctx+0x38 ≠ 0` → `FUN_140391ab0(ctx, desc, 0x2f, 0x30)` y **`c0+8 = ctx+0x38`**
  (`0x141b68829`/`0x141b6882c`). Es el único sitio encontrado que copia la animación 2 (búsqueda en el binario de
  `mov e?x,[r+0x38]` seguido de `mov [r+8],e?x`).
- Por tanto, **el lanzamiento sale de una segunda petición del mismo hechizo** con `R+0x14 == 0`, que pone el estado
  0x2f / 0x30. Con eso `FUN_14035cf40` escribe `c0+8` en `AttackID_Number` y el SM `0x120c` pasa al CF.
  No es el final de la Prepare ni una transición propia de la red.
- `FUN_140391ab0` es un thunk ofuscado. Que escriba el estado en `b8+0x10` (o en `b8+0x14`, que `FUN_140394f10`
  copia desde `+0x24` del objeto) **no lo desofusqué**: está inferido del `switch` de `FUN_14035cf40`.

**Quién pide la segunda petición: `FUN_1403972d0`, cerrado en el apartado 7.** `FUN_1403972d0` (control de decisión, cada frame):
- solo actúa con `SpellDuration` activa (`c0+0x5c ≠ 0`, pista u122);
- arranca cuando entra `ShootStateControl` (`c0+0x68 ≠ 0`, u107, la misma pista que cobra la estamina): pone un
  temporizador = **`b8+0x1b8`**, la "velocidad de lanzamiento" que escribe `FUN_1403936a0`
  (`WTP+0x10c × … × b8+0x488`, catalysts.md);
- mientras el bit `0x02` de `c0+0x2b` esté puesto, descuenta `dt`. Si ese bit cae, reinicia y no pide nada;
- al llegar a ≤ 0, si `(c0+0x24 & 0xa) != 0xa`, escribe una petición nueva para la mano `+0x64`: tipo de acción
  `b8+0x18b`, bytes `b8+0x18a` / `+0x18c`, `+0xd = 0`, `+0x56 = 10`.
- Hay dos lecturas posibles: es la petición del CF (pasa ese tiempo después del inicio de `ShootStateControl`), o es
  el **encadenado** de lanzamientos. **Ninguna está confirmada**, y faltan dos datos para decidirlo:
  - qué es el bit `c0+0x2b & 2`: ¿botón mantenido? ¿lo comparte `IsAttackHold`?;
  - que esa petición llegue con `R+0x14 == 0`.
- Valor de `b8+0x1b8` para un catalizador dado: **sin calcular** (catalysts.md: "sin decodificar").

### 6. `CancelControl_Move` y el giro durante la Prepare
- La pista `CancelControl_Move` (userData 31) es el contador `b8+0x560` (animations.md). Sus lectores en el binario
  (búsqueda de `cmp [r+0x560],0`):
  - **`FUN_14035d280`** (CP del control de ataque):
    - con `b8+0x560 == 0`, `Attack_RotationBlend` (CP 5) se actualiza a 0 y, si hay entrada de movimiento
      (`chr.e0 vt+0x1c8` ≠ 0), `Attack_MoveBlend` (CP 6) pasa a 1.0;
    - con `b8+0x560 ≠ 0` va a `FUN_14035cc60` (sin leer).
    - **Mientras `CancelControl_Move` está activo, no se sube la mezcla de locomoción bajo el ataque.** Fuera de la
      ventana, el CF se mezcla con la marcha.
  - `FUN_1403681b0`: con `b8+0x560 ≠ 0` y `b8+0x4d0 ≠ 0`, congela el vector de entrada (`+0x40 = +0x80`).
  - `FUN_140385fe0` (giro, weapons.md) lee `b8+0x560` en tres sitios; en uno corta (`0x1403865b4`).
  - También `FUN_140385690`, `FUN_1401439a0` y `0x140562263` (en `FUN_140394f10`, el paso de la petición).
- **Prepare** (sin `CancelControl_Move`): con movimiento, `Attack_MoveBlend` = 1 y la locomoción va bajo la
  Prepare (FeatherBlend `0x1212`, sección 3). **Se puede andar durante la Prepare.**
- **CF**: andar queda bloqueado hasta el final de `CancelControl_Move` (Soul Arrow `38110020`: hasta 1.4 s;
  Lightning Spear `39110720`: hasta 1.5 s). Es la misma convención que las armas, ahora con lector: `FUN_14035d280`.
  - Que el personaje además **no se desplace** (no solo que no se mezcle la animación) lo respalda
    `FUN_1403681b0`, que congela la entrada. Su condición `b8+0x4d0` (flag 0 del array de animations.md) **no está
    identificada**.
- **Girar durante la Prepare**: los cortes por `b8+0x560` no se dan, porque la Prepare no tiene esa pista.
  - La velocidad de giro de los ataques (`FUN_140385fe0`, `Attack_RotationBlend`) **no la saqué**.
  - Así que el giro está permitido según estas lecturas, **con una velocidad sin confirmar**.

### 7. El disparador del lanzamiento (cuarta ronda)
Trazas: `deob.py 0x140395930` y `0x1403936a0` (prólogo), `out\cast_72c.c` (`FUN_1403972d0`). Datos: regulation.

**La petición de hechizo (`FUN_140395930`).** Escribe en un objeto de petición `P`. `FUN_1403972d0` usa el mismo
objeto: los mismos campos (`+0x4c..+0x65`), con `+0x58` como temporizador y `+0x60` como "pendiente".

| Campo de `P` | Comprobación ok | Fallo (NoBullet) | `FUN_1403972d0` (segunda petición) |
|---|---|---|---|
| `+0x4c` / `+0x50` | animación 1 / animación 2 del bloque | `0x24596d6` / 0 (`qword`) | 0 / 0 (`qword`) |
| `+0x54` | **1** | 0 | **0** |
| `+0x55` | mano | mano | mano (`+0x64`) |
| `+0x56` | código de `FUN_140396ba0` | **1** | **10** |
| otros | `+0x58 = 0`, `+0x5c = bloque+4`, `+0x60 = 0`, `+0x61 = SpellParam+0xb9 ≠ 0`, `+0x62 = 0`, `+0x64` mano, `+0x65` | | `+0x62 = 1`; acción de la mano: tipo `b8+0x18b` |

- `FUN_140393c00` lee esta petición como `R = P+0x40` (`R+0xc` = `P+0x4c` = animación 1; `R+0x10` = `P+0x50`), así
  que **`R+0x14` = `P+0x54` y `R+0x16` = `P+0x56`**. Con eso, sus tres ramas (apartado 5) quedan:
  - comprobación ok → `R+0x14 = 1` → `FUN_1403936a0`: Prepare (estado 0x2d / 0x2e, `c0+4` = animación 1) y
    guarda la animación 2;
  - fallo → `R+0x14 = 0` y `R+0x16 = 1` → `FUN_140393970`: NoBullet (`c0+4 = 0x24596d6`), coste 0;
  - `FUN_1403972d0` → `R+0x14 = 0` y `R+0x16 = 10` → **lanzamiento**: estado 0x2f / 0x30 y `c0+8` = animación 2 →
    `AttackID_Number`.
- **Cerrado: `FUN_1403972d0` es el paso de la Prepare al lanzamiento.** Que su petición llegue a `FUN_140393c00`
  por el mismo camino que la primera se apoya en que comparten objeto y campos. El llamador común no lo seguí.

**El bit `c0+0x2b & 2`.** No es un botón. `chr.c0[0x24 + slot]` son las banderas de nodo de la red que se rellenan
cada frame (stamina.md, "Qué es `chr+0xc0`"):
- `+0x2b` = nodo **`BT_MoveAttack`**, el árbol donde cuelga el SM de hechizos `0x120c`; bit 2 = activo;
- `+0x24` = `BlendTree_BasicMove`. El bit 8 que pide `(c0+0x24 & 0xa) != 0xa` **no está documentado**.

**Lógica de `FUN_1403972d0`** (cada frame):
1. Sin `SpellDuration` (`c0+0x5c == 0`): si había una espera, la borra (`+0x58 = 0`, `+0x60 = +0x61 = 0`) y sale.
2. Primera vez con `SpellDuration` **y** `ShootStateControl` activos (`c0+0x68 ≠ 0`): `+0x60 = 1`, `+0x62 = 0`, y
   **temporizador `+0x58 = b8+0x1b8`**.
3. Mientras `+0x62 == 0`:
   - si `BT_MoveAttack` no está activo, borra la espera (`+0x58`, `+0x60..+0x63`, `+0x64/+0x65`). No hay
     lanzamiento;
   - si está activo, `temporizador −= dt`.
4. Con el temporizador ≤ 0:
   - si `+0x61` (`SpellParam+0xb9`: Heal, Med/Great Heal, Soothing/Bountiful Sunlight, Caressing Prayer, Magic
     Barrier, Sacred Oath) y la acción de la mano es `0xc`: con `FUN_141ce4e3c(b8, 0x68)` (sin identificar) elige
     comparar el byte `+9` de la acción con 3 o con 4; **si coincide, no lanza todavía** y lo vuelve a mirar al frame
     siguiente;
   - si no, y `(c0+0x24 & 0xa) != 0xa`, escribe la segunda petición (tabla) y `+0x62 = 1`.
- **Si el temporizador no llega a 0** (`SpellDuration` acaba o se sale de `BT_MoveAttack` antes): la espera se borra
  y **no hay segunda petición, así que no hay CF ni bala**. La estamina sí se cobró al entrar en `ShootStateControl`
  (apartado 1) y el uso no se gastó (se gasta al crear la bala).
  - Qué animación sigue entonces, y si el estado `0x1220` de la Prepare (condición 0x262 sobre `AttackDuration`, nodo
    0x6d) mantiene `SpellDuration` activa más allá de 0.5 s: **sin confirmar**.

**`b8+0x1b8` — decodificado** (`FUN_1403936a0`, `f_1403936a0.txt` bloques `0x141b7e9e9..0x141cf0e3f`; el prólogo copia
`r9` en `r15`: `0x141b74914 push r9` / `0x141b74916 pop r15`):
```
b8+0x1b8 = bloque+0x00                                   // [r15]: primer float del bloque de SpellParam
         × ChrCommonParam+0x234..+0x250                    // FUN_140391860(chr+0x40, agarre desc+4, desc+8)
         × WTP+0x10c                                        // catalizador (desc+0x50)
         × derivada+0x0c                                    // [FUN_14038b990() + 0x38]; derivadas en bloque+0x2c (stats.md)
         × FUN_14038f9d0(ctx, desc, 10, 1)                  // falta de stats, PlayerLackOfStatsParam fila 10
         × b8+0x488
```
- `FUN_140391860`, por agarre `c` y selector `s`. **Los 8 valores valen 1.0** en `ChrCommonParam` fila 100.
  - `c` = 2 o 6 → `+0x234` (`s` = 1 o 3) / `+0x238`;
  - `c` = 3 → `+0x23c` / `+0x240`;
  - `c` = 4 → `+0x244` / `+0x248`;
  - `c` = 5 → `+0x24c` / `+0x250`.
  - `chr+0x40` = `ChrCommonParam` (stamina.md).
- `WTP+0x10c`: Sorcerer's Staff (WTP 336) 0.65; Cleric's Sacred Chime (356) 0.60; Pyromancy Flame (11) 1.0.
- **Derivada `+0x0c`** = `PhysicalStatsPerLevelStatValuesParam[(2·Sintonía + Int + Fe) / 4].f32@+0x34`
  (`FUN_14038d790`, `0x14038d8a1`; spells.md). **Ya tiene consumidor**: este temporizador. Que la división redondee
  hacia abajo: sin confirmar.
- `FUN_14038f9d0` = `PlayerGameParamCalculator vt+0x58` = `1 + pen` (stamina.md / damage.md, misma función
  `FUN_1403811f0`), con la fila 10 = (50, 100, 0, 50):
  `pen = clamp(0.01·[(50 + 50·f) si f > 0] + 0.01·[50·g si g > 0], 0, 1)`.
  - `f` = falta de requisitos del catalizador; `g` = `(coste − estamina)/coste` con `desc+0x20` (estamina) y
    `desc+0x24` (coste del hechizo).
  - **Con requisitos y estamina ≥ coste vale 1.** Sin estamina suficiente, la espera se alarga hasta ×1.5; sin los
    requisitos del catalizador, hasta ×2.
- **`b8+0x488`: no encontré escritor ni valor por defecto.** Las únicas escrituras de `+0x488` del binario son
  `qword` en `FUN_14037e…`/`FUN_14037f…` (`0x14037ec09`, `0x14037f31b`, `0x14037f47c`), y no hay prueba de que sean
  el mismo objeto. Lo mismo que los factores de escuela `b8+0x48c..` de catalysts.md. **Los números de abajo son
  "× b8+0x488".**

Ejemplos de `b8+0x1b8 / b8+0x488` en segundos (mano derecha, una mano, requisitos y estamina cumplidos):

| Hechizo / catalizador | bloque+0 × WTP | Sint/Int/Fe 10/10/5 (→8: 1.444) | 10/20/5 (→11: 1.420) | 20/30/5 (→18: 1.364) | 30/40/5 (→26: 1.300) | 30/30/30 (→30: 1.268) |
|---|---|---|---|---|---|---|
| Soul Arrow / Sorcerer's Staff | 0.25 × 0.65 = 0.1625 | 0.2347 | 0.2308 | 0.2217 | 0.2113 | 0.2061 |
| Fireball / Pyromancy Flame | 0.25 × 1.0 = 0.25 | 0.3610 | 0.3550 | 0.3410 | 0.3250 | 0.3170 |
| Lightning Spear / Cleric's Chime | 0.575 × 0.60 = 0.345 | 0.4982 | 0.4899 | 0.4706 | 0.4485 | 0.4375 |
| Heal / Cleric's Chime | 1.0 × 0.60 = 0.60 | 0.8664 | 0.8520 | 0.8184 | 0.7800 | 0.7608 |

(PSPL `+0x34` por fila: 5 → 1.468, 10 → 1.428, 20 → 1.348, 30 → 1.268, 50 → 1.108, 99 → 0.716.)

**Línea de tiempo de Soul Arrow** (Sorcerer's Staff, 10/10/5; **suponiendo `b8+0x488` = 1, sin confirmar**):

| t (s) | Qué pasa | Fuente |
|---|---|---|
| 0 | Petición ok → Prepare `38110010` (0.5 s), estado 0x2d | `FUN_140395930` → `FUN_1403936a0` |
| 0.333 | Entra `ShootStateControl`: **se cobra la estamina** (177.08) y arranca el temporizador (0.2347 s) | `FUN_140368e70` 0x6b; `FUN_1403972d0` |
| 0.5 | Acaba el `.nsa` de la Prepare. Hace falta que `SpellDuration` y `BT_MoveAttack` sigan activos (estado `0x1220`): **sin confirmar** | — |
| ≈0.568 | Expira el temporizador → segunda petición → estado 0x2f → `AttackID_Number = 110020` → CF `38110020` (mezcla 0.001 s) | `FUN_1403972d0`, `FUN_140393c00`, `FUN_14035cf40`, SM `0x120c` |
| ≈1.135 | TAE 2300 del CF a 0.567 s → **bala** y **−1 uso** | `FUN_140325880`; `FUN_14037d300` |
| ≈2.73 | Acaba el CF (2.1667 s). Se puede andar desde ≈1.97 s (`CancelControl_Move` hasta 1.4 s del CF) | sección 2 |

- Con estadísticas más altas el temporizador baja (más rápido). Con un `.nsa` de Prepare de 0.5 s y la pista a
  0.333 s, cualquier espera de más de 0.167 s depende de lo que haga el estado `0x1220` tras la Prepare. Eso pasa en
  todos los ejemplos, salvo que `b8+0x488` sea pequeño. **Es lo siguiente a confirmar, en juego o leyendo el nodo
  0x6d.**

### 8. Qué se ve entre el final de la Prepare y el lanzamiento (quinta ronda)
- **`b8+0x488` = 1.0**: el constructor `FUN_14031bcf0` llena todo el array de multiplicadores `b8+0x3bc` con 1.0
  (índice 51 = `+0x488`; catalysts.md §6). Con eso, los tiempos del apartado 7 quedan **confirmados con
  `b8+0x488` = 1**.
  - Soul Arrow con el bastón y 10/10/5: espera 0.2347 s desde 0.333 s → CF a ≈0.568 s.

**El estado `0x1220` del SM de Prepare `0x1210`.** Leído de la NodeDef de `c0001.nmb` (`out\grip\grip_nodes.py`,
`sm.py`); nombres de CP de `grip_nodenames.txt`.
- **Entrada**: desde el estado `0x1211` (la Prepare reproducida) con la condición 0x262 sobre `AttackDuration`
  (sección 6, apartado 3), mezcla 0.2 s. `0x1210` solo tiene estas dos transiciones: **de `0x1220` no se sale dentro
  del SM**. Se sale cuando el SM padre `0x120c` cambia de estado por `AttackID_Number` (el CF) o por otra transición
  del padre.
- **Árbol** (tipos de nodo de la NodeDef):
  ```
  0x1220 FeatherBlend2 (0x72; CP BlendWeight_1 = 1.0, EventBlendWeight_05 = 0.5)
  ├─ 0x1216 SM:  0x1217 locomoción (0x1745 → SM 0x120)   ⇄   0x1218 → nodo 0x1223
  │              cambia con la condición 0x271 sobre IsMoving (CP 0x82): 0.2 s a 0x1218, 0.1 s de vuelta
  └─ 0x121e (0x6b; CP BlendWeight_1 = 1.0)
     ├─ 0x1223 (0x6d) → interruptores 0x1232 / 0x1234 / 0x1230 → los mismos .nsa de Prepare
     │                  (0x122f = a38_11_0010 …, 0x1231 = a39_11_0010 …)
     └─ 0x121f (0x69) → 0x121d (0xaa) → a00_50_0220 AdditiveStand + a00_50_0250 AdditiveBreathe
  ```
- **Nodo `0x1223` (tipo 0x6d)**:
  - No tiene atributos propios. Su **única entrada de CP es `ControlParameters|SingleFrame_1`** (CP `0x19`).
  - El exe registra una función `nodeSingleFrameQueueUpdateTimePos` (`FUN_140648410`, en `FUN_140606e10`); por el
    nombre de su CP de entrada, este nodo es el **SingleFrame**. Que el tipo 0x6d sea ese nodo está **inferido por el
    nombre del CP**; no encontré la tabla tipo → función.
  - `SingleFrame_1` **no lo escribe el exe** (ninguna referencia a la cadena). Su valor es el de la red: atributo
    0x03 del nodo CP `0x19` = **1.0**.
  - Por tanto, en `0x1220` se ve **el último fotograma (fracción 1.0) del mismo `.nsa` de Prepare**, congelado. No hay
    bucle: los nodos de animación de debajo tienen el bool de bucle a 0, igual que los de `0x1211`.
- **Encima**, la rama `0x121f` añade `AdditiveStand` / `AdditiveBreathe`. Probablemente la respiración aditiva sobre
  la pose congelada; los tipos 0x69 / 0xaa y la matemática exacta de `0x121e` (0x6b) **no están decodificados**.
- **Piernas**: igual que en la Prepare, el FeatherBlend2 de la capa del torso deja la locomoción debajo cuando
  `IsMoving` está activo (`0x1217`); parado, la pose de espera (`0x1218` → `0x1223`).
- **`BT_MoveAttack`**: `0x1220` está dentro de `0x1210` → `0x120c`, que cuelga de `…|BT_MoveAttack`. Mientras la red
  esté en ese estado, el nodo sigue activo y **el temporizador de `FUN_1403972d0` sigue corriendo**.
- **`SpellDuration` (u122) en la espera**:
  - Los nodos de animación de `0x1223` (`0x122f`, …) llevan las mismas pistas que la Prepare:
    `SpellDuration` 0–1.0, `ShootStateControl` 0.667–1.0, `AttackDuration` 0–1.0.
  - Con la posición fija en 1.0, que esas pistas de duración cuenten como activas (y no se cierren al llegar a 1.0) y
    que la red las muestree en un nodo SingleFrame: **sin confirmar**.
  - Si no contaran, `FUN_1403972d0` borraría la espera al bajar `c0+0x5c` y no habría CF en ningún ejemplo de más de
    ≈0.17 s. Que el juego no falle así **es un indicio, no una prueba**.

**Para el mod**: Prepare `.nsa` entera, luego mantener su último fotograma (más la respiración aditiva, sin decodificar)
hasta la petición del CF. Es la misma animación de DS2, sin poses nuevas.
- **Pendiente de confirmar en juego**: que la espera de 0.2–0.9 s se vea así, y que las pistas sigan activas en
  `0x1223`.

## Sin confirmar (no implementar hasta resolverlo)
- El valor de `desc+0x1c` (slot de la bala) y por qué el TAE dispara los slots 0 y 5.
- Los floats `+0x00/+0x04` de cada bloque (el `+0x10` ya está: multiplica la estamina), `desc+0x08` y `desc+0x4e`.
- Si la petición de hechizo pasa por el filtro de estamina 0 (`FUN_140367a80`).
- Quién llama a `PlayerActionCtrl vt+0x148` con la bala creada (gasto del uso), y los escritores de
  `chr+0x188 +0x14/+0x18/+0x1c`.
- Si las pistas de duración (`SpellDuration`) siguen activas en el nodo SingleFrame `0x1223` (fracción 1.0) de la
  espera `0x1220`; los nodos 0x69 / 0xaa / 0x6b de la respiración aditiva,
  el interior de `FUN_140391ab0`, `FUN_141ce4e3c(b8, 0x68)`, el bit 8 de `c0+0x24` y la velocidad de giro de los
  ataques.
- Quién elige las Prepare a dos manos / a dos armas (130010 / 150010) y la entrada derecha o izquierda.
- El objetivo del homing, los ángulos `+0x1c/+0x20`, el empuje `+0x68` y el paso de Homing Soulmass a sus orbes.
- Daño y SpEffect del impacto: van por PDP (el id de daño), sin seguir para bullets.

## 7. Petición del bullet, objetivo e impacto
Salidas de Ghidra en `D:\Reversing\DS2\out\breq\` (`chain.c`, `c2.c`…`c10.c`, `atkmgr.c` = rango `0x1403ad000–0x1403b6000`,
`f391bf0.txt` = `FUN_140391bf0` desofuscado). Datos: BulletParam, PlayerDamageParam (PDP), LockOnParam.

### 7.1 De la ranura al bullet: cuatro estructuras
`S` = ranura `chr+0x230 → +0x760 + i·0xc4` (la llena `FUN_140391bf0` en `0x141c388d9` → `FUN_1403747b0`, desde `rsp+0x40`)
→ cola `+0x150` (`FUN_140373880`, `id` + copia de 0xbc bytes) → `R` (`FUN_140337fb0`, pasa a `FUN_1404415a0` / emisor)
→ `B` (`FUN_14012d1c0`, uno por bullet en `FUN_1404404a0` → `FUN_1404429a0` → `FUN_140445020`)
→ `M` (init del movimiento, `FUN_140445020` → `FUN_14012e900`).
**Ojo**: los "petición +0x50 / +0x54" de §3 son de `M`; `+0x4c`, `+0x70`, `+0x78`, `+0x7c` son de `B`.

| Uso | `M` / `B` | `R` | `S` (escritor `FUN_140391bf0`) | Valor en un hechizo |
|---|---|---|---|---|
| Distancia máx. del homing (`motion+0xdc`) | `M+0x4c` = `B+0x4c` (defecto −1) | `+0x18` = `S+0x0c` × `chr+0x3a0 → +0x24`; sin `chr+0x3a0`: −1 (`DAT_1410ac6a4`) | `S+0x0c` = `WP+0x40`; con tipo de movimiento 12 × `SpellParam+0xb4` | `PlayerLockTargetCtrl+0x24` = **LockOnParam f1** (`FUN_140382630`: `+0x24 = fila[1]`); fila 0 = **18**. Con `WP+0x40` = `+0xb4` = 1.0 (catalysts.md) → **18 m**. `FUN_14012f2d0` corta el homing si `0 ≤ +0xdc < distancia`: negativo = sin límite |
| Multiplicador de velocidad | `M+0x50` = `B+0x80` | `+0x28` | `S+0x18` | **1.0** (`DAT_1410cf028`). Tipos 8 / 9 / 10–11 (arcos/ballestas) lo toman de `b8+0x43c` / `+0x464` / `+0x450` |
| Multiplicador del retardo de gravedad | `M+0x54` = `B+0x84` | `+0x2c` | `S+0x1c` | **1.0** (`DAT_1410cf02c`; arcos: `b8+0x440/+0x468/+0x454`) |
| Multiplicador de vida | `B+0x7c` | `+0x24` | `S+0x14` | **1.0** (`DAT_1410cf024`; arcos: `b8+0x438/+0x460/+0x44c`) |
| Pegado (`BulletAttachMotion`) | `B+0x70` (defecto 0) | — | — | Solo con BulletParam `+0x05 == 2`: dueño, o el objetivo si además `+0x04 == 1`. Los hechizos de referencia tienen `+0x05 = 1` → **0 → `BulletShotMotion`** |
| SFX al lanzar | `B+0x78` | — | — | 1 en el primer bullet del primer disparo; los demás, solo con `+0x46` (bullet > 0) / `+0x47` (disparo > 0). Con 1 × 1: **siempre**, SFX `+0xb4` |
| Ids | `B+0x40 / +0x44` | `+0x00 / +0x04` | `+0x00` = 4.º arg., `+0x04` = 5.º arg. | bala / PDP |
| Objetivo | `M+0x48` = `B+0x48` | `+0x14` | — | §7.3 |
| Entero del dueño | `B+0x50` → `obj+0x34` | `+0x08` = `chr+0x148` | — | Pasa a la petición de ataque `+0x08`, salvo con PDP `+0x40` bit 2 (entonces 0) |
| Bando | `B+0x54` → `obj+0x28` | `+0x20` = `FUN_14016d7c0(chr)` | — | §7.3 |
| Mano | `B+0x68` → `obj+0x50` | `+0x10` | `S+0x08` = **−1 si `desc+0 == 1` (derecha), −2 si no** | Selector del modelo de la dummy poly (§7.2) |
| Valores de ataque | `B+0x8c` (0x9c bytes) → `obj+0x80` | `+0x30` | `S+0x20` = salida de `FUN_140391fe0` (damage.md §1, catalysts.md §2) si `DAT_1410bfff3[tipo·5] ≠ 0`; si no `FUN_140391e60` | El AR del catalizador, calculado **al llenar la ranura** |
| `S+0x10` (`WAMP+0x18`, catalysts.md) | — | `+0x1c` | | `FUN_1404404a0` no lo copia a `B`; solo lo lleva `FUN_14012bce0` (red). Uso **sin confirmar** |

- No sale bullet si `b8+0x5cc ≠ 0` (`FUN_140373c00`), si `b8+0x5c4 ≠ 0` (`FUN_140337fb0`) o si PDP `+0x30 == 0` y el
  modelo no tiene la dummy poly `PDP+0x31` (`FUN_14014f020`). Los hechizos de referencia tienen `+0x30 = 1`.

### 7.2 Origen y dirección (`FUN_1404404a0`, por bullet)
1. **Posición** (BulletParam `+0x04`, `FUN_140440190`). 0 = dummy poly del dueño (`FUN_140131990`):
   - con PDP `+0x30 == 1`: `chr vt+0x218(id, mano = R+0x10)` (`FUN_140132430`, solo si el chr pasa `FUN_1403158f0`);
   - con 0: `chr vt+0x40(id)` (`FUN_1401322c0`).
   - Id = PDP `+0x31` + contador de disparo (`emisor+0x130`, cicla hasta `+0x45`) + contador de bullet (`+0x134`, hasta `+0x44`).
   - Si no la encuentra: la matriz del dueño (`FUN_14040d770`).
   - Referencias: `+0x04 = 0`, PDP `+0x30 = 1`, `+0x31` = **200** (Soul Arrow, Fireball, Dark Orb) / **1** (Lightning Spear).
   - `vt+0x218` con −1/−2 busca en el modelo del arma activa de esa mano (parte 6 / 5 del ChrAsmModel): confirmado,
     ver `dummypoly.md` (posiciones de las dummies 200 y 1 y enganche a `R_Weapon` / `L_Weapon`).
2. **Dirección** (`+0x05 = 1` → `FUN_14043f850`), si el dueño es un personaje (handle tipo 2):
   - con el byte `chr+0xe0 → +0x10 ≠ 0`: `FUN_14043fa80` (rayo desde la cámara);
     - el rayo sale de la cámara activa (`DAT_1416148f0+0x20`): posición + adelante;
     - largo: adelante × **100** (`DAT_1410ede60`), contra la colisión (`DAT_1416148f0+0x660`, filtro 0x1b);
     - la bala apunta al punto de impacto o al final del rayo.
   - si no: el vector `+0x50` del objeto `PlayerActionCtrl+0x190` (`vt+0x40` = `FUN_14037c820`), girado por la matriz
     del personaje (`vt+0x138`).
   - **Qué es ese byte (modo de apuntado) y ese vector: sin identificar.** Con otro dueño se queda la dirección de la matriz.
   - La matriz se rehace con `FUN_140b0df90(posición, adelante)`; adelante = fila 2 (la que `FUN_14012e900` usa
     como dirección).
3. **Desplazamiento**: `pos += +0x08·fila1 + +0x0c·fila0 + +0x10·fila2`. `+0x10` va hacia delante (Lightning Spear 0.5,
   Dark Orb −0.5). Qué fila es arriba y cuál derecha: sin confirmar.
4. **Abanico**: giro `(+0x14 + i·+0x30 + n·+0x3c, +0x18 + i·+0x34 + n·+0x40)` (`FUN_140007eb0`). Referencias: 0.
5. **Apuntado al objetivo** (solo si `R+0x14` existe, `FUN_14043fc40`):
   - punto = objetivo `vt+0x50`;
   - **adelanto** (`FUN_140131be0`), solo si el objetivo es un personaje:
     - `t = distancia / (BulletParam+0x4c × R+0x28)`;
     - `punto += v_obj · t`, con `|v_obj|` recortada a [0, **8**] (`DAT_1410bf030`; `v_obj` = `thunk_FUN_141c5a440(chr)+0x30`);
     - `+0x2c ≠ 0` usa la Y adelantada, `+0x2d ≠ 0` usa X/Z.
   - Luego la dirección gira hacia el punto:
     - primero un giro horizontal (diferencia de `atan2`) limitado a **±`+0x1c`**;
     - después un giro alrededor del eje X local limitado a **`+0x20`**.
6. **Dispersión** aleatoria ± `+0x24/+0x28` (`FUN_14043f560`). Referencias: 0.

| BulletParam | Soul Arrow | Fireball | Lightning Spear | Dark Orb |
|---|---|---|---|---|
| `+0x1c` / `+0x20` (corrección máx.) | 0.349 (20°) / 0.122 (7°) | 0.524 (30°) / 0.157 (9°) | 0.524 / 0.122 | 0.524 / 0.122 |
| `+0x2c` / `+0x2d` (adelanto Y / XZ) | 0 / 1 | 0 / 1 | 0 / 0 | 0 / 1 |
| `+0x07` (objetivo sin fijar, §7.3) | 1 | 0 | 0 | 0 |

### 7.3 Objetivo (`motion+200`)
- `motion+0xc8` (= 200) = `M+0x48` = `B+0x48` = `R+0x14`. Lo pone `FUN_140337fb0`:
  - **Con objetivo fijado**: `PlayerLockTargetCtrl` (`chr+0x3a0`):
    - `vt+0x20` (`FUN_140319f90`) devuelve el candidato actual (bandera `+0x44 & 0x20`, solo si `+0x40 ≠ 0`);
    - `vt+0x30` (`FUN_140319ff0`) da su objeto punto de fijado, que se guarda como handle (`FUN_140248570`).
  - **Sin fijar**, solo si BulletParam `+0x07 ≠ 0` (de las referencias, solo Soul Arrow):
    - `PlayerActionCtrl vt+0x208` = `FUN_14037c740`;
    - si `b8+0x4c8` bit 39 está activo, `lock vt+0x38` = `FUN_140319ee0` da el candidato con `+0x44 & 0x10`, sin `& 0x40`,
      y el mayor `+0x30`.
    - **Qué es ese bit y qué miden `+0x30` / las banderas: sin identificar.**
  - Si no, 0: **sin apuntado (paso 5) y sin homing**. No hay búsqueda del enemigo más cercano en este camino.
- **`FUN_14016d7c0` no es el objetivo.** Devuelve el byte de bando del personaje (`chr+0xb0 → +0x3d`, convertido si
  `+0x4d` / `+0x4e`; 0x1a si `b8+0x4b8` bit 49). `FUN_140382630` hace el mismo cálculo antes de la prueba de hostilidad
  `thunk_FUN_141b6e16f`. Va a `R+0x20` → `obj+0x28` → petición de ataque `+0x44`.
- Hijos: objetivo = el del movimiento del padre (`vt+0x20`).

### 7.4 Impacto
- **Registro** (`FUN_140446270`, primer frame): una entrada en el gestor de ataques `DAT_1416148f0+0x10`
  (`FUN_1403b09d0`), con id único (contador `gestor+0xc`) guardado en `obj+0x7c`. La entrada lleva:
  - el PDP;
  - el handle del bullet (`+0x10`) y del dueño (`+0xf0` = `obj+0x78`);
  - el bando (`+0x44`);
  - el bloque de ataque (`+0x48`) y la vida (`+0xf4`);
  - el listener `obj+0x120`.
- **Volumen** (PDP):
  - `+0x33 = 1`: cápsula de radio `+0x34`, con el segundo extremo en `(0, 0, −+0x38)` local;
  - `+0x33 = 0`: esfera.
  - Posición = `motion+0x50`; dirección = velocidad normalizada (`motion+0x10`).
  - Se actualiza cada frame (`FUN_140447130` → `FUN_1403b1280`, forma `vt+0x60`).
  - Si crece (`+0x78 > 0`): `FUN_140447c70` → `FUN_1403b1610`; la cápsula se recorta contra la colisión (filtro 0x20).
  - Que el eje z local sea la dirección (la cápsula quedaría detrás de la punta): **sin confirmar** en la clase de forma.
  - Clase: `FUN_140bc1a50` si PDP `+0x3c > 0`, si no `FUN_140bda8f0`. Las referencias tienen `+0x3c = 0`.
- **Prueba por frame** (`FUN_1403b1870`):
  - barrido de la forma (`vt+0x48(dt)`); contactos ordenados por distancia² al inicio;
  - atacante = dueño (`entrada+0x108`) o, si no hay, el bullet (`+0x2c`).
- **Una vez por personaje**: historial `gestor+0x630` (64 entradas) con clave (atacante, víctima, id de ataque).
  - Se consulta antes del golpe (`FUN_1403b3940` y el bucle de `FUN_1403b1870`).
  - Se añade tras un golpe con resultado ≠ 0 (`FUN_1403b3270`).
  - Cada bullet, y cada hijo, tiene su id: **un bullet golpea a cada personaje una vez, pero un hijo puede volver a
    golpear al mismo**.
  - Lista 2 (`+0x120`): clave (atacante, víctima, `entrada+0x54` = PDP `+0x08` u32, o la de la tabla `gestor+0x168` del PDP),
    en `FUN_1403b31d0` / `FUN_1403b3360`. Las referencias tienen PDP `+0x08 = 0` → no se usa.
  - Cuándo caduca una entrada: **sin confirmar**.
- **Dueño y aliados**:
  - La petición de ataque `+0x08` = entero del dueño, puesto a 0 con PDP `+0x40` bit 2.
  - `FUN_14013a6a0` (lado víctima, función sin identificar) acepta según `PDP+0x40`:
    - bit 2 (4): víctima = atacante;
    - bit 3 (8): aliado (`FUN_14044a690`);
    - bit 4 (0x10): hostil (`thunk_FUN_141b6e16f`).
  - Referencias: `0xf2` → solo hostiles, **ni el dueño ni aliados**.
  - Los hijos sin bit 4 (33010010 `0x00`; 32140020 / 32140021 `0xc0`) tienen además tasa `+0x1c = 0` y piso 0.
  - **Que el camino principal del daño use esos bits: sin confirmar.**
  - Bits 6/7 → `FUN_140448010` (petición `+0x04` = 0x1b / 0x2d / 0x36, filtro sin identificar).
- **Al tocar** (`víctima vt+0x28` llena el resultado; luego el listener `vt+0x30` = `FUN_1404457c0`):
  - resultado 0 → nada, sigue; 5 → rebota (`FUN_140447420`);
  - víctima con el estado `b8` 0x2e (`thunk_FUN_141ca218b`) → **la atraviesa sin efecto** (estado sin identificar);
  - guarda el punto (`+0x130`) y la normal (`+0x140`) del contacto en `obj+0x1b0/+0x1c0`; los hijos `+0xec` salen
    una sola vez (`obj+0x49`: 2 → 3);
  - **seguir o morir**: personaje → `+0xb0`; `FUN_140447090(handle)` → `+0xb3`; handles tipo 1 / 7 → byte `+0xa2` del
    objeto: 2 → `+0xb1`, 1 o 5 → `+0xb2`, otro → muere. ≠ 0 sigue, 0 muere (`FUN_140447390`).
- **Valores de `+0xb0..+0xb3`**:
  - 3 en `+0xb1` / `+0xb2` (con radio > 0) hace que `FUN_14012e900` cree un barrido de terreno aparte (`FUN_140bc1a50`,
    filtro 0x1f);
  - la diferencia entre 1 y 2: **sin confirmar**;
  - qué tipos de mundo son 1 / 7 y la clase `+0xa2` (paredes, objetos): **sin identificar**.
  - Referencias: Soul Arrow / Dark Orb `[0,0,0,1]`, Fireball / Lightning Spear `[0,0,0,0]` → mueren al tocar a un
    personaje o el mundo.
  - `+0x106` (rebote / destrucción por choque del movimiento) = 0 en todas.

### 7.5 Hijos (`FUN_140445ec0`)
- **Petición del hijo**:
  - `+0x0c` = handle del bullet padre (tipo 3: el emisor toma su dueño `+0x78` y marca `+0x13c`);
  - `+0x08`, `+0x10`, `+0x20/+0x21` = los del padre (`obj+0x34`, `+0x50`, `+0x28`, `+0x46`);
  - objetivo = el del padre;
  - `+0x18` = −1 (homing sin límite de distancia);
  - multiplicadores `+0x24..+0x2c` = **1.0** (no hereda los del padre).
- **Posición y dirección**: todos los hijos de referencia tienen `+0x04 = 2`, `+0x05 = 3`:
  - matriz del padre (`emisor+0x60` = `FUN_14040d770(padre)`; `FUN_14043f3b0` la refresca mientras exista);
  - se rehace con su posición y su adelante;
  - **no** es el punto de contacto (`obj+0x1b0`), que solo usa el modo 5;
  - luego el desplazamiento propio (32140021 `+0x08` = 1.0).
- **Daño**:
  - si el dueño es un personaje con `DAT_1410bfff3[tipo·5] ≠ 0` (la misma prueba con la que `FUN_140391bf0` elige
    `FUN_140391fe0`), copia **el bloque de ataque del padre**. Solo `+0x14` se sustituye por la suma del daño plano
    del PDP hijo con código 5 (`+0x19..+0x1b == 5` → `+0x0c/+0x10/+0x14`).
  - con otro dueño, el bloque se pone a 0 y se suma el plano del hijo por código.
  - En las referencias ese plano es 0: **el hijo pega con el mismo AR que el padre**, con su propio PDP (forma, piso,
    tasa):

| PDP | Piso `+0x18` | Tasa `+0x1c` | `+0x40` |
|---|---|---|---|
| 33010000 / hijos 33010001, 33010010 | 55 / 40, 0 | 0.75 / 0.5, 0.0 | `0xf2` / `0xb0`, `0x00` |
| 32140000 / 32140010 → 32140020 → 32140021 → 32140030, 32140031 | 100 / 37 → 0 → 0 → 25, 25 | 1.5 / 0.45 → 0 → 0 → 0.231, 0.231 | `0xf2` / `0xf0` → `0xc0` → `0xc0` → `0xf0` |

### 7.6 Unidades
- No encontré una constante del juego que ate la unidad al metro.
- Lo que hay:
  - la trayectoria raíz de los `.nsa` y `ChrMoveParam` usan la misma unidad (Jog 4.30 frente a 4.295, equipload.md);
  - los bullets integran en la misma unidad por segundo (§3);
  - `hkpWorld` (`FUN_1406ee410`) usa **9.81** (`DAT_141125730`) como módulo de gravedad por defecto. Es un valor de
    Havok, no de DS2.
- **1 unidad = 1 m es coherente con todo eso, pero sin confirmar.**

### Sin confirmar (§7)
- El byte `chr+0xe0 → +0x10` (apuntado con cámara) y el vector `PlayerActionCtrl+0x190 → +0x50` (dirección normal).
- Si `vt+0x218` con −1/−2 es el modelo del arma de esa mano; qué fila de la matriz es arriba y cuál derecha.
- El bit 39 de `b8+0x4c8` y la puntuación `+0x30` del candidato (objetivo de Soul Arrow sin fijar).
- Si los bits de PDP `+0x40` filtran el daño principal; caducidad del historial; el estado 0x2e; los tipos 1 / 7 y
  `+0xa2`; 1 frente a 2 en `+0xb0..+0xb3`; el eje local de la cápsula.
- El uso de `S+0x10` / `R+0x1c`, y que la unidad sea el metro.

### 7.7 Dirección del bullet y filtro de bando (segunda ronda)
Salidas nuevas en `D:\Reversing\DS2\out\breq\` (`d1.c`…`d12.c`).

#### Byte `chr+0xe0 → +0x10`: cámara subjetiva (apuntado en primera persona)
- `chr+0xe0` = `PlayerActionCtrl` (ctor `FUN_14037c070`, vtable `0x1410e4968`). El byte `+0x10` lo maneja su update
  base `FUN_14030eb60`:
  - **Entrar**: con la petición `+0x11 ≠ 0` y `+0x10 == 0`, `+0x10 = 1` y `FUN_1404922f0(cámaras, {modo 2, …})`; el id
    queda en `+0x14`. El modo 2 activa `IngameCameraOperator+0x400`, un **`SubjectiveCameraOperator`** (ctor `FUN_1404a32a0`).
  - **Mientras dura**: `b8+0x4c8` bit 30 activo.
  - **Salir**: con la petición `+0x12`, gira al personaje hacia el adelante de la cámara (`cámara+0x190`), cierra el
    modo (`FUN_140492300`) y pone `+0x10 = 0`. `+0x11` y `+0x12` se borran en cada update.
- **Quién pide entrar**: el único escritor de `+0x11 = 1` que encontré es el trozo ofuscado `0x141afb65c`, al que se
  llega desde `0x140390f8a`:
  - antes pone a 1 el byte `+0x12` de su estructura y solo pide si el modo no está ya activo
    (`[rbx] → +0x480 → +0x10 == 0`);
  - después escribe `b8+0x9c` según `WeaponTypeParam +0x35` del arma: 6 → 1, 7 → 2, otro → 0.
    `+0x35` vale 1 en los arcos, 2 en los grandes arcos, 3 en las ballestas, **6 en los catalizadores** (bastón 336,
    campana 356, llama 11), 7 en la fila 480 (armas 6100000 / 6460000);
  - escribe `b8+0x98 = WTP+0x134`.
  - **El código no reserva el modo a los arcos**: los catalizadores pasan por la misma rama.
  - Qué condición (el `al` que llega a `0x140390f8a`, vía una dirección de retorno ofuscada) e input lo disparan:
    **sin confirmar**.
- **Para un jugador que lanza sin fijar objetivo**: fuera de la cámara subjetiva el byte es 0, así que la dirección es la
  del punto siguiente (no la cámara).

#### `PlayerActionCtrl+0x190 → +0x50`: la dirección de la mirada de la cabeza
- `+0x190` = **`PlayerLookAtCtrl`** (creado en `FUN_14037c910`, ctor `FUN_140398690`, vtable `0x1410e6078`;
  base `FUN_140373260`). Huesos: `Head` (`DAT_1410bfe40`) y `Pelvis` (`FUN_140398720`).
- `+0x50` = **dirección de la mirada, en espacio del modelo**. Al iniciar vale `(0, 0, 1)` (adelante, `DAT_141136f80`).
  `FUN_14043f850` la pasa a mundo con la matriz del personaje.
- Update `FUN_1403987c0` (vtable `+0x28`):
  1. `+0x40` = punto al que mirar, en espacio del modelo (`FUN_140398b40`):
     - **con objetivo fijado** (`chr+0x3a0 vt+0x20`): el punto de fijado (`FUN_140373520`, `vt+0x58`) por la inversa de
       la matriz del personaje. **Tiene componente vertical.**
     - **sin fijar** (`FUN_1403989e0`): `cabeza + (0, 0, 1)`, recto al frente, **sin componente vertical**.
       - Excepción: si `thunk_FUN_140569260(chr)` y (`chr.c0+0x54 ≠ 0` o `b8+0x520 ≠ 0`), usa
         `cabeza + (0, s, √(1 − s²))` con `s` = `cámara+0x194` (la Y del adelante de la cámara).
       - En ese caso `s` pasa a 0 si `−sin(p+0xc4) ≤ s ≤ sin(p+0xc0)` (`p` = `thunk_FUN_141cf7120`).
       - Qué son esos dos estados (parece el de apuntar, porque `c0+0x54` también cambia los límites): **sin confirmar**.
  2. Límites y velocidades (`FUN_140399460`) del `CommonParam` del personaje (`chr+0x40`; jugador `ChrCommonParam` 100):
     - normal: `+0x1bc`; con `c0+0x54 ≠ 0`: `+0x1e0`, multiplicado por `+0xac..` de la fila de arma de la mano
       (`thunk_FUN_14014cbeb`);
     - con `chr.c0+0x2c` bit 1: de otro param (`thunk_FUN_141cf7120 +0xf0..`);
     - si `b8+0x188 == 0`, los dos límites horizontales se cambian de signo y se intercambian.

| ChrCommonParam 100 | Horizontal `+0x70/+0x74` | Vertical `+0x78/+0x7c` | Velocidad horizontal / vertical `+0x80/+0x84` |
|---|---|---|---|
| normal (`+0x1bc`) | −0.5236 / +0.5236 (±30°) | +0.2618 / −0.7854 (+15° / −45°) | 4.8869 / 0.7854 |
| `c0+0x54 ≠ 0` (`+0x1e0`) | ±1.5708 (±90°) | ±0.3491 (±20°) | 4.1888 / 4.1888 |

  3. `+0x50` gira hacia `+0x40`:
     - vertical en `FUN_140399920`, horizontal en `FUN_140398ef0`;
     - cada giro se limita a esos ángulos;
     - el paso por frame lleva la velocidad × `dt` × 0.5 (`DAT_1410ac694`). La fórmula exacta del paso: sin desglosar.
- **Consecuencias para un hechizo sin fijar**:
  - la dirección es el adelante del personaje (horizontal);
  - sube o baja solo en la excepción del punto 1;
  - como la mirada se suaviza, justo después de soltar el fijado o de girar puede ir retrasada respecto al adelante.
- **Con objetivo fijado**: apunta al punto de fijado dentro de ±30° / +15° / −45° de la cabeza, y después actúa el
  apuntado de §7.2 paso 5.

#### El filtro de bando sí está en el camino principal del golpe
- **Golpe al jugador**: `FUN_1401345c0` (`ChrDamageActionCtrl vt+0x28`; misma función en el vtable del jugador
  `0x1410c2b38`). Arma `{id de PDP, fila PDP, fila DCP}` y llama a **`vt+0x40` = `FUN_14013a6a0`**. Si devuelve 0, sale
  sin resultado (`resultado = 0`, sin daño): es la puerta del daño. stamina.md describe el resto de la cadena.
- **`FUN_14013a6a0` devuelve 1** solo si se cumplen las precondiciones:
  - víctima con vida `chr+0x168 > 0`, `b8+0x5fc == 0`, `b8+0x600 == 0`, `b8+0x7e6`;
  - PDP `+0x40` bit 0 → exige `b8+0x299` o `b8+0x5f4`;
  - un bullet atacante con `+0xb0 == 2` (`FUN_1404454b0`) → **rechazado**.
- Y además alguno de:
  - bit 2 (4): atacante = víctima;
  - bit 3 (8): atacante ≠ víctima y `FUN_14044a690` (tabla de bandos `DAT_141583e40[a·0x1c + b] == 0`);
  - bit 4 (0x10): hostil (`FUN_14044a6e0`, ofuscado).
- Detalles de los bits:
  - El **atacante** se resuelve con `FUN_140139a80`: si el handle es un `BulletObject`, sube a su dueño (`+0x78`).
    Así, "atacante = víctima" es "el lanzador".
  - Si DCP `+0x17 ≠ 0`, además `FUN_14016dad0(víctima)`.
  - Con PDP `+0x65` bit 5 o tipo de ataque 0x1a, y atacante de tipo jugador: rechaza si hay un objeto de tipo 2/3
    (`FUN_1403ba6a0 +0x18`) en la consulta de `FUN_140164ef0`. Las referencias no lo usan.
- **Resultado para los hechizos de referencia** (PDP `+0x40 = 0xf2`): solo golpean a **hostiles**, nunca al lanzador ni a
  los aliados.
- **`+0xb0` aclarado**: 1 = atraviesa personajes y hace daño (33010001, 32140010, 32140030/31); 2 = los atraviesa **sin**
  daño (33010010, 32140020, 32140021, que además tienen tasa 0).

#### Sin confirmar (§7.7)
- La condición e input que piden la cámara subjetiva, y si el juego la ofrece con catalizadores (el código lo admite).
- Qué son `chr.c0+0x54` y `b8+0x520` (los que dan componente vertical sin fijar).
- La fórmula exacta del suavizado de la mirada.
- La tabla de hostilidad `FUN_14044a6e0` y los bandos del jugador y los enemigos.

## 8. Emisor de bullets (completo)
Salidas de Ghidra: `D:\Reversing\DS2\out\cast_bobj.c` (`FUN_14043f1a0`, `FUN_14043f3b0`, `FUN_14043f560`, `FUN_1404404a0`,
gestor `FUN_140441c30` / `FUN_140441af0`), `out\spell\bmod.c` (`FUN_140440190`, `FUN_1404402b0`), `out\emit\e1.c`
(`FUN_14043efb0` ctor, `FUN_140007eb0`, `FUN_140004230`, `FUN_140b0df90`), `out\emit\e2.c` (`FUN_140b0e420`).
Constantes leídas del exe con `tools/re/disasm.py`. Datos: BulletParam (fila 0x140; también EnemyBulletParam y
SystemBulletParam, mismo formato). Completa §3 "Emisión" y §7.2.

### 8.1 Convención de matrices
- Matrices 4×4 por filas, vector fila: fila 0 = X, fila 1 = Y, fila 2 = Z = **adelante** (la que usa `FUN_14012e900`),
  fila 3 = posición. `FUN_140004230(out, A, B)` = `A·B` (`out[i][j] = Σ A[i][k]·B[k][j]`): una rotación `R` aplicada como
  `R·M` gira **en el marco local** de `M`.
- `FUN_140b0df90(pos, adelante)` → `FUN_140b0e420(adelante, arriba = (0,1,0) DAT_1410bea30)`:
  Z = normalize(adelante), **X = normalize(arriba × Z)**, **Y = Z × X**, fila 3 = pos (la misma construcción que la de
  las dummies, dummypoly.md). Con adelante inclinado, Y no es el arriba del mundo sino el perpendicular a Z.
- **Qué lado es X (`arriba × adelante`)**: en el rig de c0001 (pose de reposo, `tools/re/nsa.py`) arriba = +z, los pies
  apuntan a −y (adelante) y los huesos `R_*` están en x < 0, `L_*` en x > 0. Ahí `arriba × adelante = +x` = **lado
  izquierdo** del personaje. Que el paso rig → mundo sea una rotación propia (sin espejo) **no está confirmado en código**:
  "X = izquierda" es una deducción de los datos, **sin confirmar**. En lo que sigue se dice "hacia +X".

### 8.2 Ciclo del emisor
- **Creación** (`FUN_140441c30`): `new` de 0x140 bytes, ctor `FUN_14043efb0` (matrices identidad y **`+0x120..+0x13f` a
  0**: contador de disparos 0, temporizador `+0x124` = 0), luego `FUN_14043f1a0`. Se añade a la lista del gestor
  `DAT_1416148f0+0x68`. Se crea uno por petición (`FUN_1404415a0`), también para cada hijo.
- Campos del emisor que pone `FUN_14043f1a0`:
  - `+0x14` = handle de la fuente (`petición+0xc`: el personaje, o el bullet padre en los hijos); `+0x60` = su matriz.
  - `+0x18` = el dueño: la fuente, o si es un bullet (handle tipo 3), su dueño `+0x78`, con `+0x13c = 1` ("viene de un
    bullet"); `+0xa0` = su matriz (o copia de `+0x60`).
  - `+0x1c` = objetivo (`petición+0x14`, el de §7.3); `+0xe0` = su matriz.
  - `+0x128` = PDP `+0x30`, `+0x12c` = `+0x138` = PDP `+0x31` (dummy, §7.2).
- **Actualización** (`FUN_140441af0`, cada frame, `dt` en s; se salta entera si el byte `gestor+0x2c` ≠ 0) →
  `FUN_14043f3b0(emisor, &dt, petición)`:
  1. sale si `+0x13d == 0` o no existe la fila de BulletParam;
  2. **refresca** `+0x60`, `+0xa0` y `+0xe0` con la matriz actual de la fuente, el dueño y el objetivo
     (`FUN_14040d770`), si siguen existiendo; si no, se queda la última;
  3. si `+0x124 ≤ 0`: dispara (`FUN_1404404a0`), `+0x120 += 1`; si `+0x120 < +0x2f` o `+0x2f < 0` → `+0x124 = +0x38`;
     si no → `+0x13d = 0`;
  4. `+0x124 −= dt`.
  - El gestor borra el emisor en cuanto `+0x13d == 0`.
- **Consecuencias**:
  - **El primer disparo sale en la primera actualización** (temporizador 0), sin esperar `+0x38`. Si esa actualización
    cae en el mismo frame de la creación o en el siguiente depende del orden de actualización del frame: **sin confirmar**.
  - Disparos totales = `max(1, +0x2f)` si `+0x2f ≥ 0` (0 y 1 = un disparo).
  - Entre disparos: el temporizador se **recarga** a `+0x38` (no se acumula el sobrante) y se compara antes de restar `dt`:
    el disparo k sale en la primera actualización con `Σdt ≥ +0x38` desde el anterior (cuantizado al frame). Con
    `+0x38 = 0`, un disparo por actualización.
  - `+0x2f < 0` = **sin límite**: el emisor solo para si alguien lo borra desde fuera: `FUN_140441900(gestor, id)`
    (lo llaman p. ej. `FUN_14030f650`, al bajar a 0 un contador de una lista de `b8`; qué lista es: **sin identificar**),
    `FUN_140441940` (vacía los emisores con `entrada+0x31 == 0`) o el vaciado del gestor (`FUN_1404417f0`).
    **Ningún BulletParam tiene `+0x2f < 0`**; solo EnemyBulletParam (130500001, 130510001) y 15 filas de
    SystemBulletParam (200010010…).
- **Matriz fija** (`+0x13e`): `FUN_140441a50(gestor, id, matriz)` copia una matriz en `emisor+0x20` y pone `+0x13e = 1`;
  entonces `FUN_1404404a0` usa esa matriz y **no** calcula origen ni dirección. Lo llaman `FUN_1402034e0` (emisor creado
  sin red con una matriz identidad en una posición dada) y `FUN_14012b500` (matriz recibida, contraparte de `FUN_14012bce0`,
  la de red). El lanzamiento del jugador no pasa por ahí.

### 8.3 Un disparo (`FUN_1404404a0`): para cada bullet `i = 0 … +0x2e − 1`
`n` = `emisor+0x120` = disparos ya hechos (0 en el primero). `i` empieza en 0. Todo se recalcula **por bullet**:

1. **Matriz base** `M` (si `+0x13e == 0`, `FUN_1404402b0`): primero la posición según `+0x04` (`FUN_140440190`), luego
   la orientación según `+0x05`. Ver 8.4. Con las matrices refrescadas en 8.2, **el emisor sigue al lanzador y se
   reorienta en cada disparo** (con `+0x05 = 1`, hacia la dirección de apuntado de ese momento).
2. **Desplazamiento del origen**, en el marco de `M` y antes del abanico:
   `pos += +0x0c·fila0 + +0x08·fila1 + +0x10·fila2` (es decir `+0x0c` hacia +X, `+0x08` hacia arriba local, `+0x10`
   hacia delante). Las filas 0–2 no cambian (se multiplican por la identidad). Todos los bullets de un disparo salen del
   mismo punto (la dummy podría cambiar con `+0x44/+0x45`, ver 8.5).
3. **Abanico**: `a = +0x14 + i·+0x30 + n·+0x3c`, `b = +0x18 + i·+0x34 + n·+0x40`; `R = FUN_140007eb0((a, b, 0, 0))`;
   `M = R·M` (giro local).
   - `FUN_140007eb0` es `XMMatrixRotationRollPitchYawFromVector` de DirectXMath: ángulos × 0.5 (`DAT_1410ac4d0`), seno y
     coseno, permutaciones `0x1410aca50/60/70` y signo `(1, −1, −1, 1)` (`0x1410aca80`) → cuaternión → matriz. El
     componente x es el giro sobre X (cabeceo), y el giro sobre Y (guiñada), z = 0 (alabeo). Orden `Rz·Rx·Ry`.
   - Comprobado numéricamente con la fórmula del decompilado: el nuevo adelante, en el marco de `M` (fila0, fila1, fila2), es
     **`(cos a · sin b, −sin a, cos a · cos b)`**.
   - Por tanto: **`+0x14 / +0x30 / +0x3c` = vertical**, en radianes, **positivo = hacia abajo** (elevación `−a`);
     **`+0x18 / +0x34 / +0x40` = horizontal**, en radianes, **positivo = hacia +X**, alrededor del arriba de `M`.
     La elevación no depende de la guiñada.
   - **El código no centra el abanico**: lo centra la fila con una base negativa. Dark Hail: `−0.3665 + i·0.1222`
     (i = 0…6) = −21°…+21°. Soul Shower 31130000 (`+0x14 = −45°`, hacia arriba) y sus hijos 31130010 (`+45°`) confirman
     el signo vertical: sube y luego cae. Las sondas de Firestorm / Heavenly Thunder (33050010, 32130001: `+0x14 = π/2`)
     apuntan hacia abajo.
4. **Corrección hacia el objetivo** (`FUN_14043fc40`, §7.2 paso 5), si hay objetivo. Va **por bullet y después del
   abanico**: el giro horizontal es `atan2(objetivo) − atan2(adelante del bullet)` limitado a ±`+0x1c`. Un abanico más
   estrecho que `+0x1c` se cierra sobre el objetivo. Dark Hail tiene `+0x1c = 0.524` (30°) y un abanico de ±21°.
   - **Adelanto** (confirmado): con `+0x2c ≠ 0` se usa la Y del punto adelantado (`FUN_140131be0`); con `+0x2d ≠ 0`, su X/Z.
     El resto de componentes son las del punto del objetivo (`vt+0x50`).
5. **Dispersión** (`FUN_14043f560(M, +0x24, +0x28)`):
   - Generador global xorshift128 (`DAT_1416148f0+0xf4..+0x100`: `t = x ^ (x << 11)`; `w = w ^ (w >> 19) ^ t ^ (t >> 8)`).
   - `r = float(0x3f800000 + w % 0x800001) − 1.0` (`DAT_1410ac698`) → **uniforme en [0, 1]**, pasos de 2⁻²³.
   - `α = −v + 2v·r` → **uniforme en [−v, +v]**. Dos tiradas, en orden: α con `v = +0x24`, β con `v = +0x28`.
   - `M = Ry(β)·Rx(α)·M`. `Rx` / `Ry` son las de DirectX: filas `(1,0,0) (0,c,s) (0,−s,c)` y `(c,0,−s) (0,1,0) (s,0,c)`;
     signos `0x1410ac590` = (1, −1, 1, 1) y `0x1410ac5a0` = (1, 1, −1, 1); `FUN_140c2ca00` = cos, `FUN_140c2cd00` = sin
     (la única colocación con la que el ángulo 0 da la identidad).
   - Adelante = `(sin β, −cos β · sin α, cos β · cos α)`.
   - **`+0x24` = dispersión vertical, `+0x28` = horizontal, en radianes, ± ese valor.**
6. Llena la petición de bullet y crea el bullet (`FUN_1404429a0`). Avanza el contador de bullets `+0x134` (vuelve a 0
   si `> +0x44`).

### 8.4 Modos de origen `+0x04` y de orientación `+0x05`
**`+0x04`, posición** (`FUN_140440190`):

| Valor | Origen | Filas |
|---|---|---|
| 0 | dueño `+0x18`: dummy `+0x138` (`FUN_140131990`, §7.2); sin dueño, la matriz guardada `+0xa0` | BulletParam 695, la mayoría de los hechizos |
| 1 | objetivo `+0x1c`: la misma dummy, **en el objetivo**; sin objetivo, `+0x60` (la fuente) | solo 34050010 |
| 2 | si viene de un bullet (`+0x13c`): **matriz del bullet padre** `+0x60`; si no, como 0 | 393 (hijos) |
| ≥ 3 | `+0x60` | ninguna |

- Modo 1 (y modo 2 con padre) cuando falta el handle: **sin confirmar** con datos reales.

**`+0x05`, orientación** (`FUN_1404402b0`):

| Valor | Efecto | Ejemplos |
|---|---|---|
| 0 | se queda la de la posición: la de la **dummy** (modo 0) o la del padre (modo 2) | Heavenly Thunder 32130000, Soul Appease 32170011 |
| 1 | `FUN_14043f850`: dirección de apuntado del jugador (§7.2 paso 2, §7.7) | Soul Arrow, Lightning Spear, Dark Hail, Soul Spear Barrage |
| 2 | como 0 para el emisor; además el bullet va **pegado** (`B+0x70` = dueño, u objetivo si `+0x04 == 1`) → `BulletAttachMotion` | orbes de Homing Soulmass 31070010–24 |
| 3 | si viene de un bullet: se rehace con `FUN_140b0df90(posición, fila 2)` = mismo adelante, **arriba del mundo**, sin alabeo; si no, nada | hijos de Lightning Spear, Soul Vortex, Soul Shower |
| 4 | nada | ninguna |
| 5 | si viene de un bullet: posición = **punto de contacto del padre** (`obj+0x1b0`, si el handle `obj+0x1d0` es válido; si no, la matriz del padre), adelante = fila 2, rehecha con arriba del mundo | Firestorm / Fire Tempest / Chaos Storm 330x000x, Outcry 33320001/2, Splintering Lightning Spear 32310010/20 |
| 6 | posición actual, adelante = el del **dueño** (`+0xc0` = fila 2 de `+0xa0`) | ninguna en BulletParam |

- Con 3 / 5 el abanico horizontal gira alrededor de la vertical del mundo. Los hijos de Lightning Spear salen en
  círculo horizontal.

### 8.5 Banderas
- `+0x2c` / `+0x2d`: adelanto en Y / en X-Z (8.3 paso 4). BulletParam: (0, 0) 971 filas, (0, 1) 118, nunca `+0x2c = 1`.
- `+0x44` / `+0x45`: ciclo de la dummy por bullet / por disparo. `+0x138 = PDP+0x31 + (+0x130) + (+0x134)`; `+0x134`
  (bullets, **no** se reinicia por disparo) vuelve a 0 al pasar de `+0x44`; `+0x130` (disparos) al pasar de `+0x45`.
  **Todas las filas de los tres params tienen 0 / 0** → la dummy es siempre `PDP+0x31`.
- `+0x46` / `+0x47`: SFX de lanzamiento (`B+0x78`, §7.1) también en bullets `i > 0` / disparos `n > 0`.
  Siempre en `i = 0` del disparo `n = 0`. BulletParam: `+0x46 = 1` en 294 filas, `+0x47 = 1` en 43.
- `+0x06`: red. En `FUN_1404415a0`, con `param_3 ≠ 0` y `+0x06 ≠ 0`, no se crea si `FUN_140132200(dueño)` (¿remoto?).
  Tras crear: 1 → `FUN_14012bce0`, 3 → `FUN_14012baa0` con la matriz. **Significado exacto sin identificar**; no afecta
  a la geometría.

### 8.6 Filas de referencia (BulletParam)
Ángulos en radianes. Abanico = (vertical, horizontal). Desplazamiento = (`+0x08` arriba, `+0x0c` X, `+0x10` adelante).

| Fila | `+04/+05` | Bullets × disparos, `+0x38` | Base `+14/+18` | Paso por bullet `+30/+34` | Paso por disparo `+3c/+40` | Dispersión `+24/+28` | Desplaz. | Corrección `+1c/+20` | v0 |
|---|---|---|---|---|---|---|---|---|---|
| Lightning Spear hijos 32140030 | 2 / 3 | 3 × 1 | 0 / 0 | 0 / 2.0944 (120°) | — | 0 | (0.5, 0, 0) | 0 | 6 |
| 32140031 | 2 / 3 | 3 × 1 | 0 / 1.0472 (60°) | 0 / 2.0944 | — | 0 | (0.5, 0, 0) | 0 | 8 |
| Soul Spear Barrage 31120000 | 0 / 1 | 1 × 2, 0.1 s | 0 / 0 | — | — | 0.0873 (5°) / 0.1745 (10°) | 0 | 0.087 / 0 | 15 |
| Soul Shower 31130010 | 2 / 3 | 2 × 5, 0.15 s | 0.7854 / 0 | 0 / π | 0.1745 / 1.5708 | 0.0873 / 0.1745 | 0 | 0 | 5 |
| Soul Vortex 31150010 | 2 / 3 | 3 × 1 | 0 / 0 | 0 / 2.0944 | — | 0 | (0.2, 0, 0) | 0 | 2 |
| Heavenly Thunder 32130000 | 0 / 0 | 3 × 5, 0.5 s | 0.7854 / −1.0472 | 0 / 2.0944 | −0.0349 / 0.7854 | 0.0873 / 1.5708 | (1.4, 0, 0) | 0 | 30 |
| Soul Appease 32170011 | 0 / 0 | 8 × 2, 0.05 s | 0 / 0 | 0 / 0.7854 (45°) | 0 / 0.3927 (22.5°) | 0 | (1.5, 0, 0) | 0 | 50 |
| Blinding Bolt 32180010 | 2 / 3 | 1 × 2, 0.2 s | 0.7854 / 0 | — | 0.5236 / 2.0944 | 0.7854 / 1.5708 | 0 | 0.175 / 0.175 | 25 |
| Splintering L. Spear 32310010 | 2 / 5 | 2 × 8, 0.15 s | 0 / 0 | 0 / π | −0.1571 / 0.7854 | 0.1745 / 0.3491 | 0 | 0 | 15 |
| Firestorm 33050001 | 2 / 5 | 1 × 4, 0.4 s | 0.3491 (20°) / 0 | — | 0.0349 / 2.0944 | 0.0873 / 0.5236 | (1.4, 0, 0) | 0 | 30 |
| Firestorm 33050002 | 2 / 5 | 1 × 4, 0.7 s | 0.7854 / 0 | — | −0.0349 / −2.0944 | 0.1745 / 0.7854 | (1.0, 0, 0) | 0 | 30 |
| Dark Hail 34020001 | 0 / 1 | 7 × 1 | 0 / −0.3665 (−21°) | 0 / 0.1222 (7°) | — | 0 | (0, 0, −0.8) | 0.524 / 0 | 22 |
| Homing Soulmass orbe 31070010 | 0 / 2 | 1 × 1 | 0 | — | — | 0 | (2.0, 0, 0) | 0 | 0 |
| orbes 31070011 / 31070012 | 0 / 2 | 1 × 1 | 0 | — | — | 0 | (1.95, ±0.3, 0) | 0 | 0 |
| orbe 31070024 | 0 / 2 | 1 × 1 | 0 | — | — | 0 | (1.4, −0.55, 0) | 0 | 0 |

- **Ejemplo, Lightning Spear**: 32140030 + 32140031 salen del bullet que los crea, rehechos con el arriba del mundo
  (`+0x05 = 3`) y subidos 0.5. Dan 6 chispas horizontales: 3 a v0 6 en 0/120/240° y 3 a v0 8 en 60/180/300°.
  Que el 0° sea el adelante del padre sale de la fórmula.
- **Firestorm**: 4 disparos de 1 bullet. Cada uno gira 120° más en horizontal y 2° más en vertical que el anterior, con
  ±30° / ±5° aleatorios. Sale 1.4 por encima del punto de contacto del padre (`+0x05 = 5`).
- **Homing Soulmass**: los orbes son bullets pegados al lanzador (`+0x05 = 2` → `BulletAttachMotion`), con el
  desplazamiento en su marco. **Cómo se llega de 31070000 a los orbes sigue sin localizar** (§4).

### Sin confirmar (§8)
- Si "+X" (`arriba × adelante`) es la izquierda del personaje en el mundo: deducido del rig de c0001 (`R_*` en −x), sin
  verificar en código que el paso rig → mundo no tenga espejo.
- Si la primera actualización del emisor cae en el mismo frame de su creación.
- Quién borra los emisores sin límite (`FUN_14030f650` y su lista `b8`) y el significado de `+0x06`.
- Los modos 1, 4 y 6 de origen / orientación: no hay filas de jugador que los usen; el modo 1 de posición solo está en 34050010.
