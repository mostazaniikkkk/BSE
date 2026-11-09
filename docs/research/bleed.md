# Sangrado (hemorragia): acumulación, medidor y estallido

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj`, código ofuscado seguido con
`out\py\deob.py`. Salidas en `D:\Reversing\DS2\out\bleed\` (`a1.c` acumulación/medidor, `r1.c` gestor de estados
`0x140145000..0x140147000`, `a3.c` estructura `A`, `a6.c` defensas/absorciones, `a7.c` ARP, `g.c` guardia y SpEffect,
`as.c` `101070[02]`, `gen.c` daño generado por SpEffect, `st*.txt` barridos de escrituras).
Datos: regulation (`WeaponReinforceParam` WRP, `WeaponStatsAffectParam` WSA, `PhysicalStatsPerLevelStatValuesParam`
PSPL, `ArmorReinforceParam` ARP, `EnemyParam` EP, `EnemyCommonParam`, `ChrCommonParam`, `PlayerDamageParam` PDP,
`DamageAdjustParam`, `WeaponTypeParam` WTP), `SpEffectAbnormalState.emevd` / `SpEffectMustSub.emevd`
(`out\hollow\evd.py`), textos `itemname.fmg`. Convenciones de damage.md (`S`, `B`, `M`, `k`, `A`, `p5`).

## 0. Índices
- **Arreglo de daño del arma** (10 floats, damage.md §1): 0 físico … 4 oscuro, **5 veneno**, **6 sangrado**.
- **Ranuras del golpe** (13 floats, `golpe+0x48..`; enemies.md §4.1): 0..4 daños, **5 plano**, 6..12 = estados 0..6.
- **Estados** (`FUN_140145c60`, tipo `param_2`; SpEffect que aplica al llenarse):

| Tipo | Ranura | SpEffect (`0x140145c60`) | Qué es |
|---|---|---|---|
| 0 | 6 | 900100 (`0xdbc04`) | veneno: `872 (k 0 毒, 22 s, daño 9010, …)` |
| **1** | **7** | **900200** (`0xdbc68`) | **sangrado** |
| 2 | 8 | 900300 / 900301 (`0xdbccd − …`) | sin identificar |
| 3 | 9 | 900400 (`0xdbd30`) | maldición (`872 k 3 呪`) |
| 4 | 10 | 900500 (`0xdbd94`) | quemadura (`872 k 4 燃焼`) |
| 5 | 11 | 900600 (`0xdbdf8`) | veneno tóxico (`872 k 8 猛毒`) |
| 6 | 12 | 901100 (`0xdbfec`) | petrificación |

- El arreglo del arma pasa a las ranuras en el constructor del golpe del jugador `FUN_140391fe0`: el bucle de
  `0x141b8ba3d` deja `valor[i]` en `[rbp − 0x58 + 4i]`; `0x141b8c134` guarda `valor[5]` en `r14+0x18` (ranura 6,
  estado 0) y `0x141b8c129` `valor[6]` en **`r14+0x1c` (ranura 7, estado 1)**. Coincide con las piedras (titanite.md:
  Poison Stone → 5, Bleed Stone → 6) y con los SpEffect (900100 = 毒, 900210 = 出血).
- Confirmación por datos: Red Leech Troches 60210000 ("Temporarily boosts bleeding resist") = `1000[01] (1, 90, 1.2)`,
  índice **1** del arreglo de multiplicadores de estados (§3).

## 1. Ataque de sangrado del arma (atacante jugador)
`valor[6] = max(0, (S[6] + B[6])·M[6] + F[6]) · k` (damage.md §1, mismo bucle que el físico):
- **B[6]** = `lerp(WRP+0x18, WRP+0x3c, nivel / WRP+0x48) × WSA+0x00` (`FUN_14034e820`, `0x141b77371`). **No hay ×0.2**
  en el golpe (el ×0.2 de `B[5..7]` es solo del menú, resins.md). `B[7]` se pone a 0 (`0x141b59fc4`).
- **M[6]** = reparto `WRP+0xa0+4·6` × 0.01 + lo que mueve la infusión Sangrado (`WRP+0xd8`, titanite.md §4.2).
- **S[6]** = `bono2(1) · c[7]` (damage.md): `bono2(1)` = derivada `+0x4c` = PSPL **int `+0x28`** de la fila
  `⌊(3·Destreza + Fe)/4⌋` (`FUN_14038d790`, `0x14038dc75..0x14038dcaf`: slots 5 y 7). **Escala con Destreza y Fe,
  no con Adaptabilidad** (la Adaptabilidad entra en el veneno, `+0x2c`, y en la resistencia, §4).
- `F` (SpEffect código 5) y la suma de SpEffect código 1 a `S`: 0 sin efectos.
- `k = k1 × WTP[mano] × ChrParam+0xd8` (damage.md §1).

### Normalización a "puntos de medidor" (`FUN_140391fe0`, `0x14039240e..0x141b38323`)
- `X = FUN_14038b9a0()` → `FUN_14038d290`: rellena los 11 slots con **99** (`rep stosw`, `0x141b755ed`) y suma
  `PSPL[99].s16@+4 + Σ_{slots 1..8} PSPL[99].u8@+0x8a` = la vida máxima con todo a 99 (misma fórmula que stats.md) =
  `1945 + 8·70` = **2505**. No lee los stats del jugador: es una constante.
- `golpe[6+i] = valor[5+i] × 100 / 2505` (`DAT_1410acb18` = 100.0, `divss` en `0x1405a78a2`). Sangrado:
  **`golpe[7] = valor[6] × 0.03992`**.

## 2. Lo que entra en el medidor de la víctima
### Estructura `A` (`FUN_1401373b0`, bucle de 7 tras `vt+0x1c8`)
`A[6+i] = (golpe[6+i] + E_i) × golpe+0xb4 × R_i`
- `E_i` = `vt+0x1c8` de la víctima con el **atacante** (`golpe+0x70`) y su mano (`golpe+0x82`): suma de SpEffect
  código 5 (mano 1) o 6 (mano 2) del atacante (`FUN_14037fc50` jugador / `FUN_14031e1e0` enemigo); `E_1` = índice 6 de
  esa suma. Solo si `golpe+0x85 == 0`. 0 sin efectos.
- `golpe+0xb4` = `b8+0x420` del atacante = multiplicador índice 25 (catalysts.md; 1.0 por defecto).
- `R_i` = `vt+0x1b0(i)` de la víctima: jugador `FUN_14037fd10` (`+0x170..+0x188`), enemigo `FUN_14031e2a0`
  (`+0x128..+0x140`) de la fila que da `thunk_FUN_141be824c`. Los valores coinciden con `DamageAdjustParam` fila 0 (6.0 en
  los 14). **Se cancela** (abajo se divide por el mismo `R_i`).

### Acumulación del golpe (`vt+0x108` = `FUN_140137f90`; vtables `0x1410bf278`, `0x1410c9de8`, entrada tras
`FUN_140138d50`)
Llamado desde `FUN_140137aa0` (`0x140137e10`) con `rdx = resultado+0x18`, `r8 = A`, `r9 = p5` (defensas de la víctima):
```
para i = 0..6:
  base   = max(0, A[6+i] − def[6+i])                        // A+0x18+4i, p5+0x18+4i
  g      = clamp((1 − A+0x4c+4i) · p5+0x80+4i, 0, 1)        // penetración y guardia (damage.md a'_t)
  resultado+0x18+4i = (1 − g) · clamp(1 − p5+0x4c+4i, 0, 1) · Z · base / R_i
```
- `def[6+i]` = 0 en jugador y enemigo (`FUN_140380070` / `FUN_14031e6b0` solo escriben `def[0]`).
- **`Z` = factor de multijugador de la víctima** (§2.1). Sin otros jugadores en la sesión vale **1.0**.
- Guardia: `p5+0x68+4t` solo se llena en la categoría 2 (damage.md). Jugador `FUN_14037ff30`: índice 7 = salida `[6]`
  de `FUN_14034cae0` (escudo). Enemigo `FUN_14031e420`: `EP+0x90 × 0.01 × b8+0x404`.
- Penetración `A+0x50` = `golpe+0xb4 × golpe+0x98` (`FUN_1401373b0`); con `WP+0x90 = 0` (armas del mod) vale 0.
- **Sin guardia ni efectos**: `entra = golpe[7] × (1 − abs_sangrado)`.

### 2.1 `Z` (`A+0x90`) — `ChrMultiplayParam`
- Lo escribe el final de `FUN_1401373b0`: `A+0x90 = FUN_1401649b0(FUN_140164160(&h, chr_víctima))` (puntero de 8 bytes,
  `param_2[0x24]`). `FUN_140164160` arma `{gestor de params, chr}`.
- `FUN_1401649b0` (desofuscado, `0x1401649b2..0x140164a1b`):
  ```
  fila = FUN_140358d20(gestor, u8 (chr+0x38)+0x24)     // gestor+0x810 = ChrMultiplayParam (plist 0x50)
  n    = FUN_1403587e0(gestor)                         // u8 gestor+0xd58 (o +0xd5a si +0xd59 ≠ 0)
  puntero = {null, fila+0x00, fila+0x0c, fila+0x18}[min(n, 3)]
  ```
  `FUN_140137f90` lee `u16 puntero+4 × 0.01`; con `puntero == null` (n = 0) usa 1.0. El daño (damage.md `Z`) lee `+2` del
  mismo bloque y la vida `+0` (enemies.md §3).
- `gestor+0xd58` lo escribe `FUN_14035a212` con `FUN_14025f560(sesión)` (cuenta de miembros de la sesión de red,
  `DAT_1416148f0+0x22f0` → `FUN_140513270`); sin sesión no se escribe. `+0xd59/+0xd5a` los fijan `FUN_140359b50/b60`
  (llamados desde `FUN_140180ec0`, `FUN_140181490`, `FUN_140181ca0`, `FUN_140181d70`).
- `chr+0x38` = `ChrParam` (id ≤ 999) o `EnemyParam` (1000..999 999), `FUN_140315b20` → `FUN_140358620`.
  - Jugador: `ChrParam 100 +0x24` = 0 → fila 0 = todo 100 → **Z = 1.0 siempre**.
  - Enemigos: `EP+0x24` (tabla de §9). Bloques `+0x04 / +0x10 / +0x1c` (n = 1 / 2 / 3); la fila 10 da 0.90 / 0.80 / 0.75.
- **Golpe cuerpo a cuerpo normal del jugador** (p. ej. Longsword R1): `Z` no depende del golpe ni del PDP, solo de la
  víctima y de `n`. Un jugador solo contra un mob: **1.0**.

### 2.2 Otros factores del golpe
- **`golpe+0xb4`** = `b8+0x420` del atacante = multiplicador índice 25. El ctor de `b8` (`FUN_14031bcf0`, bucle
  `0x14031c2a0`, `rdx = 0x3f8000003f800000`) pone 1.0 en todo el arreglo desde `+0x3bc` (catalysts.md); solo lo cambian
  `1000[01] (25, …)` (p. ej. 900301 ×0.95). **Sin efectos: 1.0.**
- **`b8+0x5fc`** (corta el paso 1 de §3): contador que solo sube en el **jugador local** (`DAT_1416148f0+0xd0`):
  `FUN_1400595f0` (al abrir un menú `FeWarehouseBase`) y `FUN_1401c2a80` / `FUN_1401c2cf0` (estado de juego `0x1e`); baja
  en `FUN_140059810` / `FUN_140059a60`. También lo mira el filtro de bando `FUN_14013a6a0`. En combate normal vale 0;
  **no afecta a los mobs** (su `b8+0x5fc` nadie lo sube).
- **`FUN_1402e7410(…, 0xc)`**: va **después** del estallido y solo si `FUN_140569260(chr)`; no corta la acumulación.

### Absorción de sangrado (`p5+0x4c+4·1` = índice 7 del vector de 13)
- **Enemigo** (`FUN_14031f570`): `abs[7] = EP+0xb4 × 0.01 + SpE`, recortado a [0, 1]. `SpE` = salida `[6]` de
  `vt+0x250` (`100090[04/05]`, código 2, ×0.01 para índices ≥ 1; speffects.md §11.3).
  - `EP+0xb4` en 976 filas: 30 (543 filas), 100 = inmune (138), 15 (137), 10 (75), 50 (64), 85 (9); mín. 0.
  - Resto de estados: `[6]` = `EP+0xb0` (veneno), `[8]` = `+0xb8`, `[9]` = `+0xbc` (maldición), `[10]` = `+0xa8` (el
    fuego, para la quemadura), `[11]` = `+0xb0` (tóxico = veneno), `[12]` solo SpEffect (petrificación).
- **Jugador** (`FUN_140381350`, `vt+0x1d8`): `abs[7] = clamp(l[6], 0, 1)` con
  `l[6] = Σ_piezas 0.01·lerp(ARP+0x24, ARP+0x54, nivel/ARP+0x60) · falta17 + SpE + PSPL+0x48·0.01`.
  - ARP del vector de resistencias (`FUN_14034dda0`): `[5]` = `+0x20` (veneno), **`[6]` = `+0x24` (sangrado)**,
    `[7]` = 0, `[8]` = `+0x28` (petrificación, va a `abs[12]`), `[9]` = `+0x2c` (maldición); máximos `+0x50..+0x5c`.
  - `falta17` = `FUN_140381c20(…, pieza, PlayerLackOfStatsParam fila 0x11 = (30, 50))` (lack_of_stats.md; 1.0 sin falta).
  - Stat: `FUN_14038d250(b, 1)` = derivada `+0x5c` = **PSPL f32 `+0x48` × 0.01** de la fila
    **`⌊(3·Adaptabilidad + Fe)/4⌋`** (`0x14038dd65..0x14038dda5`, slots 8 y 7).
    Valores: 1 → 10, 5 → 13, 10 → 16, 15 → 20, 20 → 24, 30 → 25, 50 → 27, 99 → 30 (%).
  - `SpE`: SpEffect código 7 × 0.01 sumado dentro del bucle de piezas (`thunk_FUN_141c54847(…, 7)`) y `vt+0x250` índice 6
    después. Sin efectos, 0.
  - Mapa de salida (`FUN_140381350`): `abs[6]` = `l[5]` (veneno; su stat es `FUN_14038d250(b, 2)` = PSPL `+0x4c` de
    `⌊(3·Adap + Vitalidad)/4⌋`, `0x14038ddaa`), `abs[7]` = `l[6]`,
    `abs[8]` = `l[7]`, `abs[9]` = `l[9]`, `abs[11]` = `l[5]`, `abs[12]` = `l[8]`.
  - Sets del mod a +0 (Σ `ARP+0x24` de las 4 piezas): desnudo 0; Hunter/Leather 11040100..03 = 5.4; Chainmail
    17420100..03 = 11.6; Knight 11050100..03 = 7.8; King's 15130100..03 = 11.1; Drangleic 17240100..03 = 9.9 (%).

## 3. El medidor (`chr+0x1c4 + 12·i` = actual, `+0x1c8` mín., `+0x1cc` máx.)
Llamadas: `FUN_140136220` (`vt+0x100` del control de daño) llama `FUN_140145c60(gestor = chr+0x3f0, i, resultado+0x18+4i)`
para los 7 tipos (`0x1401363a2..0x14013641e`).

### `FUN_140145c60(gestor, i, v)` — por golpe
1. Si `b8+0x5fc ≠ 0`: nada. (Tipo 3 mira además una tabla `DAT_1410c0050`.)
2. Si la víctima tiene **activo** el estado del grupo `AbnormalStateSub` de ese tipo (`FUN_14014bc80` con el índice que
   da `FUN_14023dab0` = entrada de `0x1415710c0` con byte 1 = **出血**): nada. **Mientras dura el sangrado no acumula.**
3. Si `medidor + v ≥ 100` (`DAT_1410acb18`, `0x140145e7e`) → **estallido**:
   - `FUN_140146430(gestor, i, 100.0)` (`0x140145e61`): `medidor = clamp(medidor − 100, mín, máx)`; como antes era < 100,
     queda en **0**. El `v` de ese golpe no se suma.
   - Aplica el SpEffect **900200** con `{id, 1, −1.0, …}` (`thunk_FUN_141b3cbe1`).
   - Si el personaje cumple `FUN_140569260` → `FUN_1402e7410(…, 0xc)` (sin identificar).
4. Si no → `FUN_1401462a0(gestor, i, v)`: solo si `v > 0` **y** el multiplicador `b8+0x3bc+4i < 70`
   (`DAT_1410bfc78`; `FUN_140146370`):
   - `gestor+8+4i = 0.5` (**retardo de 0.5 s** antes de que baje),
   - `medidor = clamp(medidor + v, mín, máx)`.
- Máximo: enemigo `(0, 0, 100)` al crearse (enemies.md §3); el umbral de 100 está fijo en el código.

### Bajada — `FUN_1401464b0(gestor, dt)` (lo llama `FUN_140315080`)
Para cada tipo:
- `r = b8+0x3bc+4i` (multiplicador; `b8+0x3c0` para el sangrado). Si `r < 70` y `retardo > 1.19e−7`: `retardo −= dt` y
  no baja. Si no (retardo agotado, o `r ≥ 70`):
  ```
  lo, hi = CommonParam(chr+0x40) +0x2c4 / +0x2c8        // sangrado (veneno +0x2b4/+0x2b8, …)
  x      = 1/r, recortado a [0, 9999] (si 1/r > 9999 → 9999)   // DAT_1410bf330
  T      = (lo + (hi − lo)·medidor·0.01) · x            // segundos para vaciar 100
  medidor = clamp(medidor − (100/T)·dt, mín, máx)       // T == 0 → −100 de golpe; T == −1 → no baja
  ```
- `chr+0x40` = `FUN_140358530(gestor, row+0x08 > 0 ? row+0x08 : id)` (`FUN_140315b20`): id ≤ 999 → `ChrCommonParam`
  (`+0x320`), 1000..999 999 → `EnemyCommonParam` (`+0x360`). Jugador: `ChrParam 100 +0x08` = 100 → `ChrCommonParam` 100.
- Valores: jugador `ChrCommonParam` 100: `+0x2c4/+0x2c8` = **25 / 25 s** (−4 por s). Enemigos `EnemyCommonParam`:
  **120 / 120 s** en 213 filas (−0.83 por s), 90 / 90 en 12 (las de los invasores: 800000..800003, 812000, 761000…).
- Multiplicador `b8+0x3bc+4i`: 1.0 de fábrica (ctor `FUN_14031bcf0`, catalysts.md); lo cambia `1000[01] (i, t, ×)`
  (`FUN_14021bd70`). Más alto = baja más rápido; `≥ 70` = no acumula y baja casi al instante.
  - Hoguera 110000010 y 150200000 / 150300000: `(0..6, 3 s, ×100)` → limpia todos los medidores.
  - Bendición divina 60105000: `×100` 3 s en 0, 5, 1, 6, 3. Red Leech Troches 60210000: **sangrado ×1.2 durante 90 s**.

## 4. El estallido: SpEffect 900200 → daño 9020 → SpEffect 900210
- **900200** = `100170[01] (9020)` (grupo Damage, `caseD_10` `0x14022a210`: entrada con `+0x50 = 9020`, un solo uso).
  `FUN_140223cb0` → `FUN_140221ea0` la consume una vez y `FUN_14014c1a4` llama `FUN_14016d670(chr, 9020)`: arma un golpe
  con el propio personaje como atacante y lo recibe por `chr vt+0x220`.
  - Construcción (`0x14016d6ae..0x14016d771`): ctor del golpe (`FUN_1403add10`, `FUN_1403adad0`, …), atacante = el propio
    personaje (`FUN_14017b2c0`), byte `+0x3d` del personaje, y **`FUN_140170710(slots, 9020)`** (`0x14016d75c`), que copia
    los pares del PDP **sin multiplicar** (enemies.md §4.1). Justo después llama `vt+0x220`. No pasa por
    `FUN_140391fe0` / `FUN_140391e60`: **sin `k`, sin `EP+0xd8`**.
- **PDP 9020**: DCP 99987020; un solo par `+0x0c = 200`, tipo `+0x19 = 5` (**plano**); piso 0; tasa `+0x1c` 1.0;
  `+0x48 = 900210`, `+0x4c = 1` (bit 0: lo aplica a quien recibe el golpe, speffects.md §3), `+0x4d = 0`, `+0x4e = 2`.
  - El plano va directo al total: `A[5] = (E5 + golpe[5]) × μ5` (μ5 = 1, sin `golpe+0xb4`) y `FUN_140138d50` lo suma
    aparte (`0x140138f9c`, `t == 5`), sin defensa, absorción, `D`, tasa ni `A+0x78`.
  - **Daño del estallido = 200 PS planos**, igual para jugador y enemigo. No hay porcentaje de la vida en este camino.
- **900210** (estado de sangrado):

| Instrucción | Efecto |
|---|---|
| `100070[00] (1, 872, 1, 10)` → `101070[02]` (`caseD_1e` `0x1402299e0`: `+0` tipo, `+4` duración, `+8` daño, `+0xc` intervalo, `+0x10` SFX, `+0x14` dummy) | entrada **出血** (tipo 1) del grupo AbnormalStateSub durante **10 s**, sin daño periódico. Es lo que bloquea la acumulación del paso 2 de §3 |
| `100040[01] (651, 505, 0)` | SFX **651** en el dummy **505** |
| `1000[01] (8, 10, 0.75)` | multiplicador 8 = `b8+0x3dc` = **estamina máxima ×0.75 durante 10 s** (stamina.md) |
| `100090[06] (3, 0.5, 2)` y `(3, 0.7, 1)` | `MovingSpeedChange`: velocidad ×0.5 y ×0.7 durante 3 s (modos 2 / 1; según el nombre de la clase) |
| `100090[01] (10, 0.7)` | grupo Status, handler `0x14022d2d0`: **sin identificar** |

- Si se aplica de nuevo con el estado activo, `caseD_1e` reescala la duración que queda (`FUN_140222e00`).

## 5. Enemigos y jugadores
| | Enemigo | Jugador |
|---|---|---|
| Absorción | `EP+0xb4`·0.01 (+ SpEffect) | armadura `ARP+0x24`·0.01·falta + PSPL `+0x48`·0.01 de `⌊(3·Adap+Fe)/4⌋` (+ SpEffect) |
| Bajada (100 → 0) | `EnemyCommonParam +0x2c4/+0x2c8` (120 s casi siempre) | `ChrCommonParam` 100: 25 s |
| Umbral, retardo, estallido, 900210 | iguales | iguales |
- El atacante jugador normaliza con `/2505` (§1). El atacante enemigo suma pares de `EnemyDamageParam` (tipos 6..12)
  × `EP+0xd8` (`FUN_140391e60`, enemies.md §4.1); en ese camino no se vio la división por 2505.

## 6. Ejemplo: Longsword de Sangrado +0, Destreza 20, golpe a un enemigo
Longsword 1220000: WRP 1220 (`+0x18` = 800, `+0x3c` = 1000, nivel máx. 10), WSA 1003050 + 6 = **1003056**
(`+0x00` = 1.4; `c[7]` = 2.0 a +0, 2.1 a +10), `WRP+0xd8` = 50 → `M[6]` = 0.5 (y `M[0]` = 0.5). Mano derecha, requisitos
cumplidos, estamina suficiente → `k = 1`.
- `B[6] = 800 × 1.4 = 1120`.
- `S[6] = PSPL[⌊(60 + Fe)/4⌋]+0x28 × 2.0`: Fe 20 → fila 20 → 102 → **204**; Fe 5 → fila 16 → 84 → 168.
  **La Adaptabilidad 20 no cambia el ataque de sangrado.**
- `valor[6] = (204 + 1120) × 0.5 = 662` (Fe 5: 644).
- Ranura: `662 × 100/2505 =` **26.43** (Fe 5: 25.71).
- Enemigo con `EP+0xb4 = 30` (el valor más común): **18.50 por golpe** (Fe 5: 18.00). Con 15 → 22.46; 10 → 23.78;
  100 → 0.
- **Golpes para el estallido**: 6 (5 golpes = 92.5; el sexto llega a 111). Entre golpes baja `0.83/s` después de 0.5 s.
- Estallido: **200 PS** al enemigo, medidor a 0, 10 s sin acumular, SFX 651.
  (En el mod, con la escala de damage.md de PS ÷ 40: 5 de vida.)
- Contra un jugador desnudo con Adap 20 / Fe 20 (`abs` = 24 %): 20.09 por golpe → 5 golpes; con el set Knight +0
  (7.8 % + 24 %): 18.02 → 6 golpes. Baja 4 por s.

## 6.1 Enemigos que usa el mod
`ECP` = `EP+0x08` (o el id), `MP` = `EP+0x24` (fila de `ChrMultiplayParam`; `Z` con 1 / 2 / 3 miembros).

| EnemyParam | Modelo | `EP+0xb4` sangrado | ECP | Bajada (`+0x2c4/+0x2c8`) | MP → Z (1/2/3) |
|---|---|---|---|---|---|
| 102000, 102010, 102020, 102030, 102040 (Hollow Soldier) | c1020 | **15 %** | 102000 | 120 / 120 s | 10 → 0.90 / 0.80 / 0.75 |
| 102011 | c1020 | 15 % | 102000 | 120 s | 50 → 0.90 / 0.80 / 0.75 |
| 300000, 300001, 300002, 6220, 6260, 6300 (Ogro) | c3000 | **50 %** | 300000 | 120 s | 40 → 0.90 / 0.80 / 0.75 |
| 309600, 3096 (Último Gigante) | c3096 | **30 %** | 309600 | 120 s | 100 → 0.67 / 0.50 / 0.45 |
| Invasores (`EP+0x04 = 100`, 223 filas) | c0001 | 30 % en 199 filas (50 en 19, 10 en 3, 15 en 2) | 800000..800003 y otras | **90 / 90 s** | 160 (90 filas) → 0.90/0.80/0.75; 60 (75) → 1.0; 50 (41) → 0.90/0.80/0.75 |
- Ejemplos de invasores: 814009 / 820000 / 821000 (ECP 800000, MP 60, Z = 1.0); 897500 / 898000 (ECP 800002, MP 160).
- Jugador solo contra un mob (n = 0): **Z = 1.0 para todos**.
- Golpes para el estallido con el Longsword de Sangrado +0 (Des 20 / Fe 20, 26.43 por golpe):
  Hollow Soldier 26.43·0.85 = 22.46 → **5**; Ogro 13.21 → **8**; Último Gigante 18.50 → **6**.
- **Invasores**: son `PlayerIns` (npc_invaders.md §1.1). Su `chr+0x40` sale de la misma `FUN_140315b20` con el id de EP
  (→ ECP 8000xx, 90 s) si su creación pasa por ahí (`FUN_14037f3d0` → `FUN_140312dd0` → `FUN_140315b20`, sin comprobar
  el id que recibe). Si su absorción sale del calculador del jugador (armadura + stats a 99 → 30 % de stat) o de
  `EP+0xb4`: **sin confirmar**.

## 7. Visual y sonido (solo ids)
- SFX **651** en el dummy **505** (900210). El 900200 no tiene SFX propio. Veneno: SFX 650 en el dummy 249.
- DCP del golpe 9020: **99987020**. Qué reacción/animación produce: no trazado.
- Animaciones `DmgBleed` (`a00_16_1110` inicio / `_1120` bucle / `_1130` fin) existen en **c1020 y c3000**
  (enemy_ai.md: 0.500 / 1.333 / 0.833 s); **`c0001` no tiene `DmgBleed`**.
- Sonido: no se encontró ningún id de sonido en 900200 / 900210 / PDP 9020.

## 8. Anexo: infusión Mundana (byte 9) — `FUN_1401641c0`
En `FUN_1403903b0`, con `desc+0x31 == 9` (`0x141b68fc1`) **se salta** el cálculo normal Fuerza/Destreza
(`bono(0)·c[0] + bono(1)·c[1]`) y hace `S[0] = bono(2) × FUN_1401641c0(&desc+0x50)` (`0x141bf1028`, `0x14039042b`).
- `desc+0x50` = fila de `WeaponTypeParam` del arma (damage.md §1).
- `FUN_1401641c0` (`0x141ca205b`, desofuscado):
  ```
  si fila == null                         → 1.0
  si version(WeaponTypeParam) < 0x19      → 1.0     // FUN_140358de0(gestor, 0x14) = u16 +8 del .param
  si no                                   → (float) u8 WTP+0x13a × 0.01
  ```
  La regulation tiene `WeaponTypeParam` versión **25 (0x19)** → se usa `WTP+0x13a`.
- `WTP+0x13a`: 100 en 134 filas; Longsword (WTP 141), Battle Axe 271, Mace/Club 204, Spear 100, Pickaxe 241, Fists 10 y
  Drangleic Shield 430 = **100 → 1.00**. Dagger (WTP 44) = 75 → 0.75. Otros: 64, 62, 47, 80, 65, 0.
- **No depende del nivel ni de WSA**: los coeficientes de WSA 1003059 (`c[0]/c[1]` 0.225/0.125 → 0.25/0.125) no entran en
  el físico Mundano. De WSA 1003059 solo cuenta `+0x00` = **0.5** en `B` (y `c[2..]` para elementos con `M > 0`, que la
  Mundana no tiene).
- `bono(2)` = PSPL int `+0x8c` del **menor** stat de los slots 0..8 (damage.md): 1 → 50, 5 → 80, 8 → 98, 10 → 110,
  12 → 122, 15 → 140, 20 → 170, 25 → 185, 30 → 200, 40 → 210, 50 → 225, 99 → 250.
- Longsword Mundana (`M[0] = 1`): +0 `AR = 112·0.5 + bono(2)·1.0` → stat mínimo 5: **136**; 10: 166. +10:
  `225·0.5 + bono(2)` → stat mínimo 10: 222.5.

## Sin confirmar
- **Golpe 9020 dentro de `vt+0x220`**: la construcción no escala los 200 (§4) y `A[5]` → plano sin defensa está
  confirmado; el tramo `vt+0x220` → `FUN_140137aa0` no se recorrió instrucción por instrucción. `E5` (SpEffect de la
  víctima) y `μ5` = 1 (damage.md) se suponen sin cambios.
- **Valor inicial de `gestor+0xd58`** sin sesión de red (se asume 0 → `Z` = 1.0; el ctor del gestor no se miró).
- **Invasores**: id con el que se llama `FUN_140315b20` y qué calculador (jugador o enemigo) da su absorción (§6.1).
- **`100090[01] (10, 0.7)`** de 900210 (handler `0x14022d2d0`): sin identificar. `100090[06]`: solo por el nombre de la
  clase (`MovingSpeedChange`), lector no trazado.
- **Medidor del jugador**: el trío `(0, 0, 100)` solo se vio en la creación del enemigo (enemies.md); el umbral de 100
  es fijo.
- Qué hace `FUN_1402e7410(…, 0xc)` (no corta la acumulación) y la tabla del tipo 3 (maldición).
- **Guardia**: qué campo del escudo es la salida `[6]` de `FUN_14034cae0` (weapons.md tiene las reducciones `WRP+0x50..`
  como sin confirmar).
- **Identidad de `R_i`** (`thunk_FUN_141be824c`): los valores cuadran con `DamageAdjustParam` 0; se cancela igual.
- **SpEffect en la absorción del jugador**: el código 7 se suma dentro del bucle de las 4 piezas; no se desglosó si cuenta
  una vez o cuatro.
- **Reacción y animación** del golpe 9020 (DCP 99987020); sonido del sangrado.
- `FUN_140145fe0` (SpEffect 900700..902300, estados "濡れ") no tiene relación con el sangrado; no se siguió.
