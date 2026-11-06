# Resinas de pino (buff de arma)

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj`. Salidas en `D:\Reversing\DS2\out\resin\`
(`disp.py`/`tab16.py` = tablas de despacho de SpEffect, `ins.c`, `e1..e11.c`, `f19f0.txt` = cálculo de AR desofuscado).
Datos: regulation (`ItemParam`, `ItemTypeParam`, `ItemUsageParam`, `WeaponReinforceParam`, `WeaponStatsAffectParam`,
`SpEffectActiveItem.emevd`), `c0001.nmb`, `c000100_pl.tae`, textos `NeutralSpanish`.

## Objetos
| | Dorada | Carbón | Oscura | Podrida |
|---|---|---|---|---|
| ItemParam / SpEffect (`+0x04`) / icono (`+0x00`) | 60250000 | 60260000 | 60270000 | 60280000 |
| Elemento (índice del arreglo de daño) | rayo (2) | fuego (3) | oscuro (4) | veneno (5) |
| Modelo en mano (`+0x08`) | 908 | 902 | 904 | 906 |

- Las cuatro: `+0x38` = 0.7 (velocidad de uso, igual que la efigie), `+0x40` ItemTypeParam **600**, `+0x44` ItemUsageParam
  **1700**, `+0x4a` = 99 (probable máximo; no verificado), `+0x4c` = **27**, `+0x4d` = 2.
- `+0x2c..+0x34` (203–206 / 1500–2000 / 150–200): sin decodificar (¿orden, precio?).
- ItemUsageParam 1700: `+5` bit 0 = 0 → no se puede usar en escalera (`FUN_14032f450`, ver [hollowing.md](hollowing.md)).
- Índice → elemento: por datos, no por código. Las filas de `WeaponReinforceParam` con reparto `+0xa0..` de 100 en un
  índice son armas de ese elemento, y los textos de cada resina coinciden (rayo 2, fuego 3, oscuro 4, veneno 5;
  el Suero sangrante 60290000 usa el 6).

## SpEffect (`SpEffectActiveItem.emevd`; despacho: tablas del exe, archivo `0x156dfc8` constructores con nombre,
`0x156f7e0` instrucciones directas)
Por resina (X = índice del elemento; valores de la dorada; la podrida cambia 20→**50** y 40→**100**):
1. `100130[04] (909, 0.1, 0, 1.0, 0, 0)` → `SpEffectActionImpl_Special_WeaponSfx` (`FUN_14021ca00`, 特殊制御：武器SFX).
   SFX del arma: **909** dorada, **903** carbón, **905** oscura, **907** podrida.
2. `100040[01] (956, 276, 0)` → `SpEffectActionImpl_Basic_Sfx` (`FUN_14021c6b0`, 効果SFX). 956 / 957 / 958 / 959.
3. `100080[01] (90.0, X, 20.0)` → `FUN_14022c4c0`: crea/actualiza una entrada `SpEffectEach_Property` **tipo 1**
   (`FUN_140225af0` / `FUN_1402225a0`): duración = 90 × multiplicadores (por defecto 1.0, no trazados:
   `FUN_14022d950`, `FUN_14021f280`), `valores[X] = 20` (arreglo de 10 floats en `+0x50`).
4. `100090[02] (90.0, X, 40)` → `SpEffectActionImpl_ChangeAtkDef` (`FUN_14021a9a0`, 攻撃力増加). Su `vt+0x98`
   (`FUN_14021b230`): consulta código 1 → `out[byte +4] += u16 +6` (ids 2/4/9 suman, 3/5 restan). Duración 90 s.
5. Solo la de carbón: `1000[03] (23, 90.0)` → `SpEffectActionImpl_ChrStatus_Flag`: bit **23** de `b8+0x4b8` durante
  90 s. **Qué hace ese bit: sin identificar.**

**Duración: 90 s** (todas las acciones).

### Mano
- `+0x2e` de cada acción = ranura del SpEffect: 26/`0xfc` → mano izquierda, **27**/`0xfd` → **derecha**, resto → "otra"
  (`FUN_1402259f0`, `FUN_14021b230`, `FUN_14021cd50`: 27 → SFX en la mano 0x202). La resina trae 27
  (`ItemParam +0x4c`, pasado en `FUN_14032f870`; la traducción `thunk_FUN_14000a2fc` está ofuscada).
- La consulta es **por mano, no por arma**: máscara `(mano != 0) + 5` (`FUN_14038f440`, `FUN_140200610`).
  No se encontró código que quite el buff al cambiar de arma: el arma derecha nueva lo recibiría (no verificado en juego).

### Segunda resina
- Al aplicar, `FUN_14022c4c0` → `FUN_14022f750` → `FUN_14023a370`: en cada grupo marca para borrar las entradas con la
  **misma ranura (27) y otro id de SpEffect** (`FUN_14023a0a0` para Property). **Una resina nueva reemplaza a la otra.**
- La misma resina otra vez: si ya hay entrada del mismo id, `FUN_140222580` solo reescribe el valor. **Si reinicia los
  90 s no está confirmado.**
- Modo de ranura `+0x4d` = 2 → se aplica siempre (`FUN_14022d8a0`, casos 2..5).

## Cómo entra en el daño
Constructor del golpe `FUN_140391fe0` (y el mismo cálculo para el menú en `FUN_1402019f0`, llamado por `FUN_140200610`).
Arreglos de 10 floats por elemento (0 físico, 1 magia, 2 rayo, 3 fuego, 4 oscuro, 5 veneno, 6 sangrado…):
- **B** base (`FUN_14034e820`): `lerp(WRP[i], WRP[i+9], nivel / WRP+0x48) × WSA[0]`.
  `WRP` = `WeaponReinforceParam` (fila `WeaponParam +0x08`), `WSA` = `WeaponStatsAffectParam` fila `WRP+0x4c` + infusión
  (`FUN_14034ec10`; puntero `+0x230` de la mano, `FUN_140349fe4`). El base es **el mismo número para los 5 daños**
  (Longsword: 112 en todos, 800 en veneno y sangrado).
- **M** reparto (`FUN_14034c580` → `FUN_14034c760`): `WRP+0xa0.. × 0.01` (sin infundir: [1, 0, 0, …]), luego
  **`M += 0.01 × valores de resina`** (`FUN_14038f440` / `FUN_14014cd70` → `FUN_1402259f0` tipo 1; `FUN_14034c7d0`),
  recortado a ≥ 0. Resina de rayo: `M[2] = 0.2`; podrida: `M[5] = 0.5`.
- **S** suma: bonos de stats × coeficientes de `WSA` (sin decodificar) **+ código 1 de SpEffect** (`FUN_14022e1e0` →
  `ChangeAtkDef`): `S[X] += 40` (podrida `S[5] += 100`).
- Golpe: `valor[i] = max(0, (S[i] + B[i]) × M[i] + F[i]) × k` (`FUN_140391fe0`, bloque `141b8ba3d`).
  `F` = código 5 (`FUN_14038fee0`), `k` = `FUN_14038f6a0` × `[r12+0x50]+0x54` × `(…)+0xd8`: **sin decodificar**.
- Menú: igual, pero `B[5..7] × 0.2` (`DAT_1410ad5e0`, bloque `14031fd70`) y factores `FUN_14014ce40` /
  `FUN_14034cc80`. **Si el golpe real también aplica ese ×0.2 al veneno no está confirmado.**
- Víctima: rayo/fuego/oscuro van a `FUN_140138d50` como cualquier tipo (defensa, absorción, `D_t`, ver
  [weapons.md](weapons.md)). `D_t` (`vt+0x1a0`) no está sacado: **no hay número de daño final.**

### Números con `S` de stats = 0 y `k` = 1 (nivel +0, sin infusión)
Elemento = `(B + 40) × 0.2`; veneno = `(800 + 100) × 0.5` en el golpe (130 si el ×0.2 del menú también aplicara).
| Arma | Físico | Rayo / fuego / oscuro | Veneno (podrida) |
|---|---|---|---|
| Longsword 1220000 | 112 | 30.4 | 450 (130) |
| Battle Axe 2010000 | 140 | 36.0 | 450 (130) |
| Mace 2410000 | 120 | 32.0 | 450 (130) |
| Club 2400000 | 105 | 29.0 | 450 (130) |
| Spear 2800000 | 105 | 29.0 | 450 (130) |
| Pickaxe 2610000 | 150 | 38.0 | 450 (130) |
| Fists 3400000 | 10 | 10.0 | 450 (130) |
| Drangleic Shield 11230000 | 86 | 25.2 | 93 (58.6) |
El físico no cambia (`M[0]` sigue en 1).

## Animación — `a00_18_0060 ItemUseEnchantWeapon` (TAE 180060)
- ItemTypeParam 600 `+0x10` = **60** (`+0x14` en escalera = 60) → `ItemUseCategory` → nodo `a00_18_0060` (misma
  correspondencia por número que la efigie; el nodo selector del nmb no está decodificado).
- `.nsa`: `a00_18_0060_ItemUseEnchantWea_1d5c335e.nsa`, **4.0 s**, no es versión izquierda.
- `ItemUse_Speed` = 0.7 × factores (`FUN_14032f750`), igual que la efigie; los tiempos de abajo son del clip.

| Evento / pista | Tiempo (s) |
|---|---|
| TAE 220200 modelo 0 (resina, dummy 21) | 0.267–2.167 |
| TAE 220200 modelos 1 y 2 (dummy 20 / 249; vacíos en las resinas) | 0.667–2.167 |
| **TAE 220000: aplica el SpEffect** (`FUN_14032f870`) | **0.667** (hasta 0.833) |
| TAE 220100 / 310000 | 0–3.333 / 0–3.5 |
| `CancelControl_Avoid`, `_AttackAll`, `_Guard` | 0–**2.667** |
| `InputControl`, `ItemUseInputControl`, `ToMoveswitchFoot` | desde 2.067 |
| `ItemUseCancelControl`, `StateControll_1` | desde 2.667 |
| `StateControll` | 3.467–3.989 |
| `ItemUseChangeHide` | 0.174–3.267 |
| `UsingArm` | 0–3.7 |
- **No hay `CancelControl_Move`** (se usa andando, como la efigie y la antorcha).
- Herramienta: `tools/re/weapons.py item_use(180060)`.

## Textos (NeutralSpanish = es_mx; Spanish igual salvo "temen", "se ha vuelto")
- **Resina de pino dorada** — "Aplica electricidad al arma derecha temporalmente." / "Una extraña resina de pino con un
  brillo dorado. Aplica electricidad al arma derecha. El arma afectada inflige daño de electricidad durante un breve
  intervalo. Es muy eficaz contra los enemigos resistentes a la magia y al fuego."
- **Resina de pino carbón** — "Aplica fuego al arma derecha de forma temporal." / "Resina de pino negra como el carbón.
  Aplica fuego al arma derecha. El arma afectada provoca daño de fuego durante un breve intervalo. Es muy eficaz contra
  enemigos vulnerables al fuego, como los no muertos y las bestias."
- **Resina de pino oscura** — "Aplica oscuridad al arma dcha. de forma temporal." / "Resina de pino hechizada. Aplica una
  llama oscura al arma derecha. El arma afectada provoca daño oscuro durante un breve intervalo. Es muy eficaz contra
  enemigos que le temen la oscuridad, como los clérigos y las criaturas mágicas."
- **Resina de pino podrida** — "Aplica veneno al arma derecha de forma temporal." / "Esta resina de pino podrida se volvió
  venenosa. Aplica veneno al arma derecha. Los enemigos envenenados pierden PS con el tiempo."
- Archivos: `out\hollow\text\menu__Text__NeutralSpanish__{itemname,SimpleExplanation,DetailedExplanation}.txt`.

## Iconos
- `ItemParam +0x00` = 60250000 / 60260000 / 60270000 / 60280000 → `IC_00602x0000.tpf` (regla de
  [hollowing.md](hollowing.md)). No extraídos: el usuario da las referencias.

## Sin confirmar
- Qué hace el bit 23 de `b8+0x4b8` (solo carbón).
- Si reaplicar la misma resina reinicia los 90 s.
- Restricciones por arma ("no se puede encantar"): no se encontró ningún chequeo; la compuerta del menú no está trazada.
  Tampoco si exige tener arma en la mano derecha.
- El factor `k` del golpe, los bonos de stats (`S`) y si el veneno lleva el ×0.2 en el golpe real.
- El veneno en la víctima (acumulación, resistencia, daño por segundo, duración): no trazado
  (`SpEffectAbnormalState.emevd` 9001xx–9024xx son candidatos sin identificar).
- `D_t` de la víctima → no hay daño final.
- Cuándo se descuenta la resina del inventario.

## Excepciones del usuario (2026-10-31, no son de DS2)
- Resina de carbón (fuego): además del daño de fuego, prende fuego al objetivo 4 s, como Aspecto ígneo I
  (`SoulsResin.FIRE_SECONDS`); no a los inmunes al fuego.
- Resina de rayo: el daño de rayo del golpe ×2 si el objetivo está en el agua o tiene alas (tag `soulsmode:winged`:
  murciélago, abeja, loro, phantom, gallina, allay, vex, dragón).
- Resina oscura: el daño oscuro del golpe ×2 contra mobs del End, vex y allays (tag `soulsmode:dark_weak`).
- Solo se duplica el elemento (el valor de ataque de ese tipo antes de la fórmula de daño); el físico no cambia.
  Los tags se pueden ampliar por datapack.
