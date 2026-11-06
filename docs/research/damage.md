# Daño: poder de ataque por stats y defensas

Fuente: `DarkSoulsII.exe` (SotFS), desofuscado con `D:\Reversing\DS2\out\py\deob.py`. Salidas y scripts en
`D:\Reversing\DS2\out\damage\` (`f1fe0.txt` constructor del golpe, `d790u.txt` stats derivadas, `d50.txt` víctima,
`73b0.txt` estructura `A`, `1350.txt` absorciones, `plist.txt` tabla de params, `calc.py` números).
Datos: regulation (`WeaponParam`, `WeaponReinforceParam` WRP, `WeaponStatsAffectParam` WSA,
`PhysicalStatsPerLevelStatValuesParam` PSPL, `RelatePhysicalStatToLevelStatParam`, `PlayerLackOfStatsParam`,
`WeaponTypeParam` WTP, `DamageAdjustParam` DAP, `ArmorParam`, `ArmorReinforceParam` ARP, `EnemyParam`,
`EnemyCommonParam`, `ChrCommonParam`, `PlayerCommonParam`, `PlayerDamageParam` PDP, `DamageCtrlParam` DCP).

Tabla de params del gestor (`FUN_14048b620`, slot `+0x310 + i·0x10`) en `out\damage\plist.txt`. Corrige a
regulation.md en un punto: hay `ArmorHandsUpFParam` (0x1b) y `EquipCullingTargetParam` (0x1d), así que WeaponParam = `+0x420`,
WRP `+0x470`, WSA `+0x480`, ArmorParam `+0x4a0`, ARP `+0x4b0`; los de regulation.md (0x580…0x5d0) siguen bien.

## 1. Valor del golpe (atacante jugador) — `FUN_140391fe0`
Por tipo i (0 físico, 1 magia, 2 rayo, 3 fuego, 4 oscuro, 5.. estados):
`valor[i] = max(0, (S[i] + B[i])·M[i] + F[i]) · k` (bucle `0x141b8ba3d`). Se guarda en el slot del golpe (`r14+0..+0x10`).
- **B** (`FUN_14034e820`): `lerp(WRP[i], WRP[i+9], clamp(nivel / WRP+0x48)) × WSA+0x00` (cabecera = 1.0).
- **M** (`FUN_14034c580` → `FUN_14034c760`): `WRP+0xa0+4i × 0.01` (float; sin infundir = [1,0,0,0,0]) + resinas
  ([resins.md](resins.md)), recortado a ≥ 0.
- **F**: efectos código 5 (`FUN_14038fee0`), 0 sin efectos.
- **S** = bonos de stats (`FUN_1403903b0`) + código 1 de SpEffect (`FUN_14038f3b0`) × factor de SpEffect
  (`FUN_140391020`, 1.0 por defecto, `DAT_1410ac698`).

### S — bonos de stats (`FUN_1403903b0`)
- Coeficientes `c[0..8]` = WSA `+8 + nivel·0x24` (9 floats por nivel, 11 niveles; `FUN_140397c40`). Fila WSA = `WRP+0x4c`
  (+ infusión, `FUN_14034ec10`).
- Bono de stat = int del bloque de stats del jugador (`chr+0x490 → +8`, `FUN_14038b990`): `bono(j)` = `+0x50+4j`
  (`FUN_14038d260`), `bono2(j)` = `+0x74+4j` (`FUN_14038d240`). Son las derivadas `+0x24..` que llena `FUN_14038d790`
  desde los stats **efectivos** con PSPL (fila = valor del stat, fuera de 1..99 → 1) y `Relate…` (byte n → slot n−1):

| S | Fórmula | Fila PSPL (stat) | Columna |
|---|---|---|---|
| S[0] físico | `bono(0)·c[0] + bono(1)·c[1]` | Fuerza / Destreza | `+0x0c` / `+0x10` |
| S[1] magia | `bono(3)·c[2]` | Inteligencia | `+0x14` |
| S[2] rayo | `bono(5)·c[3]` | Fe | `+0x1c` |
| S[3] fuego | `bono(4)·c[4]` | ⌊(Int+Fe)/2⌋ | `+0x18` |
| S[4] oscuro | `bono(6)·c[5]` (7 u 8 según `desc+0x70`, mismo valor) | min(Int, Fe) | `+0x20` |
| S[5] veneno | `bono2(2)·c[6]` | ⌊(3·Des+Adap)/4⌋ | `+0x2c` |
| S[6] sangrado | `bono2(1)·c[7]` | ⌊(3·Des+Fe)/4⌋ | `+0x28` |

- Byte `desc+0x31` (copiado del slot del arma, `+0x71`; por su efecto parece la infusión, **nombre sin confirmar**):
  - 8: `S[0] += bono(3)·c[8]` (Int en el físico).
  - 9: `S[0] = bono(2) × FUN_1401641c0(…)`; `bono(2)` = PSPL `+0x8c` del **menor** stat de los slots 0..8. El
    coeficiente de `FUN_1401641c0` no está sacado.
- Stats efectivos = `clamp(base + bytes de SpEffect, 1, 99)` (`FUN_14038d710` / `FUN_1401ffcc0`, bytes
  `+0x30b..+0x315` de `FUN_14014bb40`). Sin efectos = base.
- El menú usa `MenuStatsParam` (`FUN_14038c1f0`); en las filas comprobadas sus bonos coinciden con PSPL.

Bono de Fuerza = bono de Destreza (PSPL `+0x0c` = `+0x10`):

| Stat | 1 | 5 | 10 | 11 | 16 | 20 | 25 | 30 | 40 | 50 | 99 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Bono | 50 | 53 | 57 | 59 | 70 | 80 | 91 | 102 | 140 | 155 | 200 |

### k (`FUN_140391fe0`, `0x141b49ca9`)
`k = k1 × WTP[mano] × ChrParam+0xd8`
- `k1` = `FUN_14038f6a0(…, id 0, modo 2)` = `PlayerGameParamCalculator vt+0x68` (`FUN_14031e660` = `1 − vt+0x48`,
  `FUN_1403811f0`) con `PlayerLackOfStatsParam` fila 0 = (30, 80, 0, 10):
  `k1 = 1 − clamp(0.01·[(30 + 50·f) si f>0] + 0.01·[10·g si g>0], 0, 1)`.
  - `f` = falta de stats del arma (abajo). `g` = `FUN_140376510(estamina, coste)`: 0 si coste ≤ 0 o estamina ≥ coste;
    `(coste − estamina)/coste` si 0 < estamina < coste; 1 si estamina ≤ 0. **Atacar sin estamina suficiente quita hasta un 10 %.**
- `WTP[mano]`: fila WTP del arma (`desc+0x50`), byte `desc+0` = mano: 0 izquierda → `+0x54` (0.9 en todas las del mod),
  1 derecha → `+0x60` (1.0).
- `ChrParam+0xd8` (fila 100 del jugador) = 1.0.
- `k` también multiplica el daño plano del PDP (tipos `+0x19..+0x1b`).

## 2. Dos manos
- **No hay ×1.5 de Fuerza en el daño.** `FUN_1403903b0` no mira el agarre; los stats efectivos y las derivadas tampoco
  (`FUN_14038d570`, `FUN_14038d710`). La única constante 1.5 escalar (`DAT_1410bd0a8`) en este camino es la de la
  powerstance (`FUN_140350170`: requisito de Fuerza/Destreza = min(99, máx(izq, der)·1.5)).
- Lo que hace el agarre: en la **falta de stats**, con `handCtrl+0x10` = 2 o 3 (dos manos) el requisito de Fuerza se
  **divide entre 2** (`FUN_14034d3c0`, `0x14034d43d`: `shr cx, 1`; igual en `FUN_14034ce60`). Se recalcula al cambiar de
  agarre (`FUN_14037d208` → `FUN_14034a980` tras `FUN_14034f530` / `FUN_14034f470`).
- Confianza: alta en lo que hacen esas funciones; media en que no exista otro multiplicador (no se recorrieron todas las
  referencias a constantes).

## 3. Requisitos y penalización
- `WeaponParam +0x18 / +0x1a / +0x1c / +0x1e` (u16) = Fuerza / Destreza / Int / Fe.
- `FUN_14034d3c0(WP, stats, agarre)` → `slot+0x88`:
  `f = clamp(Σ max(0, 1 − stat/max(req, 1)), 0, 1)` sobre los 4 stats (Fuerza con req/2 a dos manos).
  - `slot+0x8c` = solo Fuerza/Destreza (`FUN_14034ce60`), `slot+0x90` = solo Int/Fe (`FUN_14034cfe0`). Los rellena
    `FUN_14034a980` para los 3 slots de cada mano.
  - El daño lee `slot+0x88` (`FUN_1403491e0`).
- Daño: `k1` (fila 0). Otras filas: estabilidad (fila 4, stamina.md), coste de estamina (fila 11), daño de estamina (fila 1).

## 4. Víctima — `FUN_140137aa0` → `FUN_1401373b0` (estructura `A`) → `FUN_140138d50`
### A (atacante visto por la víctima), `FUN_1401373b0`
`A[t] = (golpe[t] + suma[t]) × golpe+0xb4 × μ[t]`
- `golpe+0xb4` = `b8+0x420` del atacante (`r14+0x6c` en el constructor); los SpEffect lo reinician a 1.0
  (`0x14021e3c6`, `0x140220681`).
- `suma` = `vt+0x1c0` de la víctima (SpEffect); 0 sin efectos. Solo si `golpe+0x85 == 0`.
- **μ** = `vt+0x198` de la víctima (DamageAdjustParam):
  - jugador (`FUN_140380f30`): físico `+0x158` = **10**, magia/rayo/fuego/oscuro `+0x160..+0x16c` = 6, plano 1;
  - enemigo (`FUN_14031f2b0`): físico `+0x114` = **6** (o `+0x150` = 12, ver D), elementos `+0x118..+0x124` = 6, plano 1.

### Fórmula (`FUN_140138d50`), por t = 0..4
`d_t = a'_t · b_t · φ_t · max(piso, A[t] − def_t) / D_t · PDP+0x1c · A+0x7c · m_t`
- `piso` = byte `PDP+0x18` si `A[t] > 0` (Longsword ligero 1: 60), si no 0.
- **D** = `vt+0x1a0` de la víctima:
  - jugador (`FUN_140380ed0`): físico `DAP+0x15c` = **12**, elementos `+0x160..+0x16c` = 6;
  - enemigo (`FUN_14031f1f0`): físico `DAP+0x14c` = **6**, elementos `+0x118..+0x124` = 6.
  - Enemigo con `chr+0x45d` > 1 (ciclo) y un byte de tipo = 0 del personaje de `golpe+0x70`: μ `+0x150` = 12 y D
    `+0x154` = 13. Qué personaje es (atacante/víctima) y el byte: **sin confirmar**.
- `b_t = clamp(1 − abs_t, 0, 1)` (absorción, abajo). `a'_t = 1 − clamp((1 − pen_t)·guardia_t, 0, 1)`:
  - `guardia_t` = bloque `p5+0x68` que solo llena `vt+0x1e0` en categoría 2 (guardia); fuera de guardia 0 → `a' = 1`.
  - `pen_t` = `A+0x34+4t` = `0.01·DCP+0x08 × WP+0x90 × M[t]` (`0x14000adeb`, `0x140137604`). Las armas del mod tienen
    `WP+0x90` = 0.
- `φ_0` (solo físico) = `vt+0x128` del control de daño (`FUN_140139a40`): `CommonParam` de la víctima `+0x270/+0x274/+0x278`
  según el subtipo `DCP+0x00` = 0/1/2; otro → 1.0. Jugador (`ChrCommonParam` 100): 1.0. Longsword ligero (DCP 14011100):
  subtipo 0.
- `PDP+0x1c` = tasa del movimiento (ya portada). `A+0x7c` = `WP+0x80` (1.0 en las del mod; `r14+0x70`).
- `m_t` = 1.0 salvo `golpe+0x85` (`vt+0x1a8`).
- Total: `trunc((1 + 0.01·DCP+0xac·caída) · Σ d_t · A+0x78 · X · Y · Z + plano)`; el entero se resta a la vida
  (`FUN_140136220` → `FUN_14016a300`, `resultado+0x34`).
  - `caída` = atenuación por distancia (`golpe+0x78`, `FUN_140136ab0`, ver stamina.md). Longsword: `DCP+0xac` = −30 →
    **−30 % de daño entre 1.4 y 2.0 m**.
  - `X` = `PDP+0x20 · A+0x80` (`WTP+0x6c`) si la víctima tiene `b8+0x630 ≠ 0` o `c0+0xf4 & 0x20`; si no 1 (weapons.md).
  - `Y` = `CommonParam+0x34c+4·subtipo` (jugador 0.8) × `vt+0x1b8` × `b8+0x79c` solo si `golpe+0x83`; qué es ese
    flag: **sin identificar**.
  - `Z` = u16 `+2` × 0.01 de `A+0x90` (`FUN_1401649b0`) si existe; si no 1. **Sin identificar.**
  - `A+0x78` = producto de factores del atacante/víctima, ciclo NG+, red (`0x1401379c6`): **no desglosado**; los caminos
    por defecto devuelven 1.0.
  - Bits `b8+0x4b8` 34..45 de la víctima ponen la absorción de ese tipo a 1.0 (inmune).

### Defensa del jugador (`vt+0x1d0` = `FUN_140380070`, `vt+0x1d8` = `FUN_140381350`)
- **Físico** (solo `def_0`; `def_1..4` = 0):
  `def_0 = Σ_piezas ARP_sub(nivel)·max(1 + 0.01·SpE7, 0)·falta_arm + (Σ_piezas ArmorParam+0x1c)·defStat + 5.5`
  - Pieza: slot `equip + 0x290 + p·0x30` (`+0x298` ArmorParam, `+0x2b0` ARP = `ArmorParam+0x18`, `FUN_140349b40`).
    Sin pieza → fila "desnuda" (11001100..03).
  - `ARP_sub` (`FUN_14034dbb0`): subtipo `DCP+0x00` 0/1/2/3 → `lerp(ARP+0x00/+0x04/+0x08/+0x0c, +0x30/+0x34/+0x38/+0x3c,
    nivel/ARP+0x60)`.
  - `defStat` = derivada `+0x1c` = PSPL `+0x80` (int) de la fila ⌊(Aguante + Fuerza + Vitalidad + Destreza)/4⌋
    (`FUN_14038d790`, `0x14038d97b`).
  - `ArmorParam+0x1c`: desnudo 0.075 / 0.225 / 0.075 / 0.125 (suma 0.5); Hunter/Leather 0.22/0.44/0.22/0.22 (1.1); el resto
    del mod 0.266/0.532/0.266/0.266 (1.33).
  - `falta_arm` (`FUN_140381c20`, fila 15 = (30, 50)): `1 − 0.01·(30 + 20·f)` con `f = slot+0x2b8`; quién escribe ese
    `f` **no está trazado** (0 → 1.0).
  - `5.5` = `vt+0x248` (`FUN_140380400`) = `0.01·interp(ChrEquipLoadParam fila 43 = 100 en todos los umbrales) ×
    PlayerCommonParam+0xa8 (5.5)`.
- **Absorción** (`p5+0x34+4t`): física = 0; elementos (`FUN_140381350`):
  `abs_t = clamp(Σ_piezas 0.01·ARP_elem(nivel)·falta_arm16 + 0.01·SpE + stat_t, 0, 1)`, luego `min(·, tope)`.
  - ARP (`FUN_14034dda0`, `FUN_14034f7b0` ×0.01): magia `+0x10`, rayo `+0x14`, fuego `+0x18`, oscuro `+0x1c`
    (máx. `+0x40..+0x4c`).
  - `stat_t` (derivadas ×0.01, `FUN_14038d270`): magia PSPL `+0x58`[Int], rayo `+0x60`[Fe], fuego `+0x5c`[⌊(Int+Fe)/2⌋],
    oscuro `+0x64`[min(Int, Fe)]. Stat 5 → 13 %, 10 → 16 %, 20 → 24 %, 40 → 26 %.
  - Tope (`FUN_140164af0`): 1.0, o `PlayerCommonParam+0x18c`·0.01 = 0.99 si pasa una comprobación de versión
    (`FUN_140358de0(…, 8) ≥ 0x1a`); **cuál aplica: sin confirmar**.

Sets del mod a +0 (subtipo 0; con subtipo 3 se usan `+0x0c`):

| Set | Def. física piezas (cabeza/pecho/brazos/piernas, `ARP+0x00`) | Σ | Σ `+0x1c` | Abs. magia/rayo/fuego/oscuro (Σ %) |
|---|---|---|---|---|
| Desnudo | 0 | 0 | 0.5 | 0 |
| Hunter/Leather 11040100.. | 16 / 63 / 37 / 37 | 153 | 1.1 | 5.8 / 7.7 / 3.7 / 6.3 |
| Chainmail 17420100.. | 41 / 93 / 33 / 43 | 210 | 1.33 | 6.2 / 3.5 / 6.8 / 6.2 |
| Knight 11050100.. | 40 / 104 / 34 / 61 | 239 | 1.33 | 6.3 / 4.6 / 6.6 / 6.3 |
| King's 15130100.. | 48 / 151 / 53 / 90 | 342 | 1.33 | 10.8 / 8.5 / 10.8 / 7.7 |
| Drangleic 17240100.. | 86 / 211 / 90 / 110 | 497 | 1.33 | 7.6 / 6.6 / 7.6 / 7.6 |
| Old Bell Helm 11660100 | 34 | 34 | 0.266 | 0.6 / 0.8 / 0.7 / 0.7 |

### Defensa del enemigo (`vt+0x1d0` = `FUN_14031e6b0`, `vt+0x1d8` = `FUN_14031f570`)
- `chr+0x38` / `chr+0x40` = `EnemyParam` / `EnemyCommonParam` para ids 1000..1000999 (`FUN_140358620` / `FUN_140358530`).
- `def_0 = (int) EnemyParam+0x9c + 1.0` (`vt+0x248` = `FUN_1401f9ac0` devuelve 1.0); `def_1..4` = 0.
- Absorción: magia/rayo/fuego/oscuro = `EnemyParam +0xa0/+0xa4/+0xa8/+0xac` × 0.01 (+ SpEffect `vt+0x250`), recortado a
  [0, 1]; física 0.
- `φ_0` = `EnemyCommonParam +0x270..+0x278`. Qué campo de `EnemyParam` elige la fila de EnemyCommonParam **no está
  confirmado** (`+0x04/+0x08/+0x0c` valen 318000 en la fila 3181 y esa fila existe: 0.93 / 0.93 / 1.2).
- `EnemyParam+0x9c` en las 976 filas: mediana 479, cuartiles 363 / 634.5, máx. 1000. Absorción de magia: mediana 30 %.
- Las filas de EnemyParam no tienen nombre en la regulation.

## 5. Resumen práctico (sin guardia, sin efectos, NG, a ≤ 1.4 m)
- Contra enemigo: `físico = φ·max(piso, 6·AR − def)/6 · tasa` = `φ·max(piso/6, AR − def/6)·tasa`.
- Contra jugador: `físico = max(piso, 10·AR − def)/12 · tasa`.
- Elementos (ambos): `max(piso, 6·AR_e)/6 · (1 − abs_e) · tasa`.
- `AR = (S + B)·M·k`.

## 6. Números a +0, sin infusión (Int = Fe = 5; AR físico; `k1` a una mano)
| Arma | B | c Fue / Des | Req. Fue / Des | 10/10 | 20/20 | 40/40 | Fue 16 / Des 11 |
|---|---|---|---|---|---|---|---|
| Longsword 1220000 | 112 | 0.45 / 0.25 | 10 / 9 | 151.9 | 168.0 | 210.0 | 158.25 |
| Battle Axe 2010000 | 140 | 0.50 / 0.15 | 15 / 5 | 177.05 (k1 0.533) | 192.0 | 231.0 | 183.85 |
| Mace 2410000 | 120 | 0.75 / 0.10 | 12 / 5 | 168.45 (0.617) | 188.0 | 239.0 | 178.4 |
| Club 2400000 | 105 | 0.95 / 0 | 12 / 3 | 159.15 (0.617) | 181.0 | 238.0 | 171.5 |
| Spear 2800000 | 105 | 0.30 / 0.40 | 9 / 12 | 144.9 (0.617) | 161.0 | 203.0 | 149.6 (k1 0.658) |
| Pickaxe 2610000 | 150 | 0.50 / 0 | 22 / 8 | 178.5 (0.427) | 190.0 (0.655) | 220.0 | 185.0 (k1 0.564; a dos manos 1.0) |
| Fists 3400000 | 10 | 0.50 / 0.15 | 0 / 0 | 47.05 | 62.0 | 101.0 | 53.85 |
| Drangleic Shield 11230000 | 86 | 0.45 / 0.25 | 16 / 0 | 125.9 (0.512) | 142.0 | 184.0 | 132.25 |
- El golpe usa `AR·k1`. Por nivel: c sale de la fila WSA del nivel (Longsword Fue 0.45 → 0.50 a +10, Pickaxe 0.50 → 0.95);
  B hace `lerp` hasta WRP `+0x24..` (Longsword 225 a +10).
- Elementos: S[1..4] solo cuentan si `M[i] > 0` (infusión/resina).

## 7. Ejemplo: Longsword +0, Fue 16 / Des 11 (resto del personaje del usuario), ligero 1 (PDP 10031800)
- `S[0] = 70·0.45 + 59·0.25 = 46.25`; `B = 112`; `M = 1`; `k = 1` (mano derecha, requisitos 10/9 cumplidos, estamina
  ≥ coste) → **AR físico = 158.25**; elementos 0. Tasa `PDP+0x1c` = 1.0, piso 60, DCP 14011100 subtipo 0.
- (a) Defensa 0:
  - enemigo: `6·158.25/6 · φ = 158.25·φ` → **158** con φ = 1;
  - jugador: `10·158.25/12 = 131.9` → **131**.
- (b) Jugador con el set Knight +0 y los mismos stats: `defStat` = PSPL[⌊(6+16+6+11)/4⌋ = 9]`+0x80` = 78;
  `def = 239 + 1.33·78 + 5.5 = 348.24` → `(1582.5 − 348.24)/12 = 102.9` → **102**.
  **Corregido por speffects.md §11.3**: el término `+5.5` es `vt+0x240`, no el de la defensa; sin buffs vale 0 →
  `def = 342.74` → **103** (desnudo: `def = 39` → 128.6).
  - Desnudo: `def = 0.5·78 + 5.5 = 44.5` → **128**.
- (c) EnemyParam 3181 (sin nombre): `def = 642 + 1 = 643` → `(949.5 − 643)/6 = 51.1·φ`; con la fila de EnemyCommonParam
  318000 (φ = 0.93, sin confirmar) → **47**. Con la mediana (479) → `(949.5 − 480)/6 = 78.3·φ`.
- Entre 1.4 y 2.0 m baja hasta un 30 % (`DCP+0xac` = −30).
- Contexto: vida del personaje (Vigor 8…) = 858 (stats.md).

## Sin confirmar
- `A+0x78` (factores globales NG+/red), `Y` (`golpe+0x83`) y `Z` (`A+0x90`): se suponen 1.0 en NG sin red.
- Qué personaje decide la variante NG+ de μ/D del enemigo (12/13).
- Qué campo de EnemyParam elige la fila de EnemyCommonParam (φ del enemigo).
- Quién escribe la falta de stats de la armadura (`slot+0x2b8`), y el tope de absorción (1.0 o 0.99).
- Nombres de los subtipos físicos `DCP+0x00` (0..3) y de las infusiones 8/9.
- Coeficiente de la infusión 9 (`FUN_1401641c0`).
- Que no haya otro bono de dos manos fuera de la falta de stats.
- El veneno/sangrado en la víctima (acumulación): no trazado.

## Implementado (`ds2/Ds2Damage`, `SoulsAttack.damage`)
- Ataque por tipo con la fórmula de §1 (base por nivel, reparto, bonos de stats × coeficientes del nivel, falta de
  requisitos con la Fuerza a la mitad a dos manos, factor de mano 1.0 / 0.9). Resinas: reparto +0.2 y +40 en su tipo.
- Víctima mob = enemigo con defensa DS2 0 (×6 ÷6, suelo `PDP +0x18`, × tasa del golpe × `WeaponParam +0x80`); la
  armadura de Minecraft se aplica encima. Resultado en PS de DS2 ÷ 40 (**elección del usuario**: la misma escala que la
  vida).
- Material = nivel de mejora (**elección del usuario**): madera +0, piedra +1, hierro y oro +3, diamante +6, netherita
  +10; sin material +0. Encantamientos encima, como en vanilla.
- Sin portar: el término de déficit de estamina del factor k, el daño contra jugadores (defensa de armadura y stats),
  la acumulación de veneno/sangrado de DS2.

## Implementado: déficit de estamina
- `g = (coste − estamina) / coste` del último cobro del ataque (`FUN_140376510`) suma 0.01 × 10 g a la penalización
  (`PlayerLackOfStatsParam` fila 0 `+0x0c`).
