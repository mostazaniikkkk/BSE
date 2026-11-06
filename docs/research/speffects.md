# SpEffects de los hechizos: de dónde salen, a quién se aplican y qué hacen

Fuente: `DarkSoulsII.exe` (SotFS), proyecto Ghidra `D:\Reversing\DS2\ghidra_proj`. Código ofuscado seguido con
`out\py\deob.py`. Datos: `SpellParam`, `BulletParam`, `PlayerDamageParam` (PDP), `SpEffect*.emevd` (decodificados con
`out\hollow\evd.py`), `c0001.flv` (dummies, `tools\flver_dummy.py`).
Salidas en `D:\Reversing\DS2\out\spfx\` (`a1.c`…`a17.c` decompilados, `x*.txt` referencias, `bc*.txt` cadenas
hacia atrás, `tables.txt` / `table1.txt` tablas de despacho, `chainspell.py` trazador hechizo → bala → PDP → SpEffect,
`heals.txt` / `buffs_heal.txt` su salida).

**Portado** (`SoulsSpEffects`, `SoulsBullet`): §3 (SpEffect de `PDP +0x48` al golpear, filtro `+0x40`), §4, §5 y §6
(curaciones). **Sin portar**: los buffs de §8 y lo abierto de §9.

## 1. Resumen de la cadena (Heal como ejemplo)
1. El hechizo dispara su bala normal (casting.md §7): `SpellParam +0x0c/+0x10` = bala / PDP. En los milagros de apoyo
   la **bala 1** va pegada al lanzador (`BulletParam +0x05 = 2`, `BulletAttachMotion`) y su PDP no hace daño
   (piso `+0x18 = 0`, tasa `+0x1c = 0`).
2. Al golpear, el PDP aplica **su SpEffect `PDP +0x48`** a quien recibe el golpe, si `PDP +0x4c` bit 0 (§3).
   El filtro de bando de `PDP +0x40` (casting.md §7.7) decide quién puede recibirlo: Heal `32010000` tiene `0x06` → solo el lanzador.
3. Ese SpEffect (`32010000`, mismo id que el hechizo) contiene `100120[06]` **StatusSelectBullet**: según la **Fe**
   del lanzador elige una de 5 balas `32010010 + k` y la dispara (§4).
4. La **bala 2** (esfera de radio 3.0 a los pies del lanzador, 2.0 s) tiene PDP `+0x40 = 0x0c` (lanzador + aliados) y
   aplica el SpEffect `32010010 + k` a cada personaje que toca.
5. Ese SpEffect lleva la curación: `100040[00]` (multiplicador por Fe) + `100050[00]` → evento 850 de
   `SpEffectMustSub` → `101050[00]` = curación de `N` PS en `t` s (§5, §6).

## 2. Despacho de instrucciones
Una instrucción de SpEffect es `banco[id] (args)`. Hay dos tablas en `.data` (extraídas en `out\spfx\tables.txt`):

- **Acciones** (`0x14156f9d8…`, entradas de 0x20 bytes `{nombre (en la entrada anterior), banco, id, ctor}`): crean
  un objeto `SpEffectActionImpl_*` (vtable con nombre RTTI). Las que importan aquí:

| Instrucción | Nombre en el exe | Clase / ctor |
|---|---|---|
| `1000[00]` | キャラステータス整数増減設定 (suma entera a un stat) | `0x14021ae80` |
| `1000[01]` | キャラステータス倍率設定 (multiplicador) | `ChrStatus_Rate`, `0x14021bd70` |
| `1000[09]` | キャラステータス整数増減大設定 | `0x14021ad20` |
| `100040[01]` | 効果SFX | `Basic_Sfx`, `0x14021c6b0` |
| `100040[06]` | 効果時間延長許可 (permite alargar la duración) | `PermissionTimeExtend`, `0x14021da80` |
| `100040[07]` | 効果時間延長 | `TimeExtend`, `0x14021db50` |
| `100090[02]` / `[03]` | 攻撃力増加 / 攻撃力減少 (ataque ±) | `ChangeAtkDef`, `0x14021a9a0` |
| `100090[04]` / `[05]` | 防御力増加 / 防御力減少 (defensa ±) | `ChangeAtkDef`, `0x14021a9a0` |
| `100090[06]` | 移動速度変更 | `ChrStatus_MovementRate<MovingSpeedChange>`, `0x14021e290` |
| `100120[03]` / `[04]` | 追従するソウルの塊発射 / 追従するソウルの塊 (Homing Soulmass) | `0x140215b90` / `0x140215a90` |
| `100120[06]` | ステータス判定バレット (bala según stat) | `StatusSelectBullet`, `0x140215f30` |
| `100130[04]` | 武器SFX | `Special_WeaponSfx`, `0x14021ca00` |
| `100170[00]` | ダメージ生成 | `0x140218a10` |

- **Entradas de grupo** (`0x1415711e0…`, 0x10 bytes `{banco, id, handler}`, sin nombres): el handler
  (`switchD_14023df7c::caseD_*`) crea una entrada en el grupo del banco. Grupos del gestor (ctor `FUN_1402317f0`;
  banco ↔ offset en `FUN_14023a370`):

| Offset | Banco | Grupo (vtable RTTI) |
|---|---|---|
| `+0x08` | 100040 | `SpEffectGroup_Basic` |
| `+0x10` | 100050 | `SpEffectGroup_Heal` |
| `+0x18` | 100060 | `SpEffectGroup_Spell` |
| `+0x20` | 100080 | `SpEffectGroup_Property` |
| `+0x28` | 100090 | `SpEffectGroup_Status` |
| `+0x30` | 100130 | `SpEffectGroup_Special` |
| `+0x38` | 100140 | `SpEffectGroup_Death` |
| `+0x40` | 100170 | `SpEffectGroup_Damage` |
| `+0x48` | 100180 | `SpEffectGroup_MultiPlay` |
| `+0x50` | 100700 | `SpEffectGroup_Others` |
| `+0x58` | 101050 | `SpEffectGroup_HealSub` |
| `+0x60` | 101070 | `SpEffectGroup_AbnormalStateSub` |
| `+0x68` | 101130 | `SpEffectGroup_SpecialSub` |

  Handlers usados aquí: `100050[00]` y `[01]` → `FUN_14022a8c0` (llamar a un evento), `100080[01]` → `caseD_9`
  `0x14022c4c0`, `100170[01]` → `caseD_10` `0x14022a210`, `100700[04]` → `caseD_18` `0x14022bfd0`,
  `101050[00]` → `caseD_19` `0x14022b0e0`, `101050[01]` → `caseD_1a` `0x14022b310`, `101050[02]` → `caseD_1b` `0x14022ae60`.
- **`100040[00]` no está en ninguna tabla.** Lo trata un pre-pase: `FUN_140230ad0` → `FUN_140230610`
  (compara `banco == 0x186c8 && id == 0`) → `FUN_1402304f0` (§6).
- **Llamada a evento** (`FUN_14022a8c0`): con `args[0] == 1` llama al evento `args[1]` de `SpEffectMustSub.emevd`
  (`FUN_14022f2b0`); los parámetros empiezan en `args + 8` (`param_3 + param_4`, con `param_4 = 4` sobre `&args[1]`).
  La sustitución sale de la tabla de parámetros del emevd (cabecera `+0x58`, entradas de 0x20 bytes
  `{instr, destino, origen, largo}`). Evento 850: `(0, 0, 0, 2)`, `(0, 4, 4, 4)`, `(0, 8, 8, 1)`.
  Por eso `100050[00] (1, 850, N, t, f)` da a `101050[00]` los argumentos `u16 N`, `f32 t` y `u8 f`.

## 3. SpEffect al golpear: `PDP +0x48` / `+0x4c` / `+0x4d` / `+0x4e`
Todo en código ofuscado; flujo sacado con `deob.py`:

- **Al que recibe el golpe** — `FUN_14014be20(ctrl, resultado, ataque)`:
  - lo llama `FUN_140314990`, un método del personaje que está en dos vtables (`0x1410df440`, `0x1410e4de0`).
    Este método llama a `chr+0xe0 vt+0x138` y luego a `FUN_14014be20(chr+0x3e0, rdx, r8)`;
  - exige `resultado+0 == 1` y `resultado+0x13 ≠ 0`;
  - `fila = ataque+8` (si es nula: `FUN_140170c80(ataque+0)`);
  - si `fila+0x48 > 0` y `fila+0x4c & 1`, aplica el SpEffect `fila+0x48` al propio `ctrl` con la petición
    `{id, 1, −1.0, FUN_14023e170(fila+0x4d), fila+0x4e, 0}` (`0x141b6bb8d` → `FUN_14014bec0`).
  - Que `fila` es una fila de PDP: la función lee `+0x48` (id de SpEffect en todas las filas de PDP de hechizos de apoyo)
    y `+0x4c..+0x4e`.
- **Variante con `+0x4c & 2`** — `FUN_14014bd70` (lo llama `FUN_140314830`, otro método del personaje):
  - busca el PDP por `ataque+0x1c` y aplica `+0x48` al `ctrl` que se le pasa;
  - antes copia `resultado+4` a `ctrl+0x18` y lo pone a 0 al terminar;
  - exige `resultado == 1` y (`+0x13 ≠ 0` o `resultado+4 > 0`).
  - **A qué personaje corresponde (¿atacante?) y para qué sirve `resultado+4`: sin confirmar.** Ningún hechizo de
    esta nota usa el bit 1.
- `FUN_14014bec0` = aplicar SpEffect (`thunk_FUN_141b3cbe1` → `FUN_14022eb30`). La misma estructura la usan la hoguera
  (`FUN_1402027b0`: `{110000010, 1, −1.0, …, 2}`) y los estados alterados (`FUN_140145c60`).
- Qué significan `+0x4d` (pasa por `FUN_14023e170`; Heal 0, Magic Weapon 0x1a, Replenishment 0x1f, Bountiful 0x23)
  y `+0x4e` (1 o 2): **sin identificar**.
- **Condición `resultado == 1 / +0x13`**: `FUN_1401345c0` (golpe al jugador, casting.md §7.7) pone `+0x13 = 1` en la
  rama del golpe aceptado y `resultado+0 =` el tipo que elige `FUN_140136570`. Si `FUN_14013a6a0` (filtro de bando,
  `PDP+0x40`) rechaza, sale con resultado 0 y no hay SpEffect.
  - Que el resultado de `FUN_1401345c0` sea el mismo que recibe `FUN_140314990`: **probable** (mismo formato; el enlace
    no se trazó).
  - Qué es el tipo 1: **sin confirmar**.
- **No hay SpEffect en `BulletParam`.** `+0x100` (casting.md §3) no es un SpEffect: en estas balas vale 0.

## 4. `100120[06]` StatusSelectBullet — la bala según un stat
Ctor `FUN_140215f30`; disparo en `vt+0x10` = `FUN_140216ad0`:
```c
args: i32 bala, i32 pdp, u8 stat (+8), u16 u1 (+0xa), u16 u2 (+0xc), u16 u3 (+0xe), u16 u4 (+0x10)
s   = stats_efectivos(dueño)[stat − 1];          // FUN_14023c8e0 → FUN_1402036f0 → FUN_140203760
umbral = {0, u1, u2, u3, u4};
k   = último i (0..4) con umbral[i] <= s;
FUN_14023c3a0(dueño, 0, bala + k, pdp + k);       // petición de bala normal → FUN_1404415a0
```
- `FUN_140203760` copia los 11 `u16` de `bloque+0x16` (`FUN_14038b990`) = **stats efectivos** (stats.md: base en
  `[0..10]`, efectivos en `[0xb..0x15]`). Con stat 8 → slot 7 = **Fe**. Si el personaje no es de un tipo con stats
  (`DAT_1410bfff0[tipo·5] ≠ 2`), todos valen 0 → `k = 0`.
- `FUN_14023c3a0` arma la petición: dueño = el personaje del SpEffect (`+0x148`), bando `FUN_14016d7c0`, valores de
  ataque desde el PDP (`thunk_FUN_141cf6e72`). No sale si `owner+0x18 ≠ 0`.

## 5. Curación: `101050[00]`, el grupo `HealSub` y el consumo
### Alta — `caseD_19` (`0x14022b0e0`)
```c
cantidad = (float)(u16)args[0] × mulB.cantidad × ctx(clave 1);  // se trunca a u16: (u32)(i64)(…) & 0xffff
duración = args[4] (f32) × mulB.tiempo × ctx(clave 0);         // 0 si la petición tiene +0x1c == 1
cortar_al_llenar = args[8] (u8) != 0;
entrada = nueva entrada HealSub (FUN_14022b5e0, siempre nueva);
FUN_1402313c0(entrada, …, cantidad, duración, cortar_al_llenar);
```
- `mulB` (`FUN_14022d950` → grupo Basic, `FUN_140223780`): la primera entrada del grupo Basic de tipo 2 (`100040[02]`)
  da `(cantidad, tiempo)`; sin ninguna, `(1.0, 1.0)`. Hay `100040[02]` en Lifegems (`1.0, 2.8`) y Estus (`1.0, 2.5`)
  (`SpEffectActiveItem`), activos solo si `FUN_14023c870(…)+0x37 ≠ 0` (`caseD_0` `0x140229e30`; condición sin
  identificar). **Ningún hechizo de esta nota lo usa.**
- `ctx(clave)` = `FUN_14021f280` sobre el contexto de efecto (§6).
- `FUN_1402313c0`:
  - con `cantidad <= 0` la entrada se termina;
  - con `duración < entrada+8` (= **0.01 s**, ctor `FUN_140221cd0`: `0x3c23d70a`) es **instantánea** (`+0x64 |= 2`,
    `+0x50 = cantidad`);
  - si no: duración restante `+0x24 = duración` (`FUN_140221a20`), total `+0x58 = cantidad`, acumulado `+0x5c = 0`,
    ritmo `+0x50 = cantidad / duración`;
  - el bit 0 de `+0x64` = `cortar_al_llenar`.

### Tick — `FUN_140231470(entrada, dt)` (desde el update del grupo `FUN_14023b300`)
```c
t += dt;                                   // +0x54
if (t >= 0.01) {
    trozo = t × ritmo;                     // +0x60
    trozo = min(trozo, total − acumulado);
    acumulado += trozo;  restante −= t;  t = 0;
    if (restante <= 0) fin;
}
grupo+0x20 += trozo;                       // suma de todas las entradas
```
- Instantánea: el primer tick da toda la cantidad y termina.

### Consumo — `FUN_14014c310` → `FUN_14014b810` (ofuscado)
```c
tope = chr+0x174;  vida = chr+0x168;
if (vida <= 0) { grupo+0x20 = 0; return; }           // los muertos no se curan
n = (int)grupo+0x20;  grupo+0x20 −= n;                // la fracción se guarda para el frame siguiente
if (vida + n >= tope) termina toda entrada con +0x64 bit 0;   // las "cortar al llenar"
FUN_14016a4b0(chr, n): si vida < tope → vida = min(vida + n, tope), dentro de [chr+0x16c, chr+0x170]
```
- `chr+0x174` es el **tope de vida con el hollowing** (hollowing.md, `FUN_140202ca0`).
- **Esto cierra un punto de bonfire.md**: `101050[01]` (hoguera) cura `chr+0x174 × pct / 100`, o sea el
  **100 % de la vida máxima actual** (con el recorte del hollowing), de golpe (duración 0 → instantánea).
- Llamador por frame de `FUN_14014c310` y de `FUN_14023b300`: no trazado (los dos son ofuscados / vtable). El tiempo
  `dt` se acumula como segundos, igual que el resto del gestor.
- `101050[02]` (`caseD_1b`): igual que `[01]`, pero el porcentaje se aplica a `FUN_14023c780(…)`. Qué devuelve:
  **sin identificar**.

## 6. Multiplicador por stat: `100040[00]` y el contexto de efecto
- `FUN_1402304f0(args)`: `ctx.mul(args[0], FUN_14023c660(dueño, args))` (`FUN_140220280`).
- **Contexto de efecto** (ctor `0x14021e3a0`) con dos multiplicadores:
  - clave 0 en `+0x420` (byte `+0x424 = 0`);
  - clave 1 en `+0x428` (byte `+0x42c = 1`);
  - bandera `+0x430`.
  - `FUN_140220280` multiplica el de la clave dada.
  - `FUN_14021f280(ctx, clave)` devuelve: clave 0 → `+0x420` **solo si `+0x430 ≠ 0`**; clave 1 → `+0x428`; si no, 1.0.
  - `+0x430 = 1` lo pone `FUN_1402202c0`, que llama el ctor de **`100040[06]` PermissionTimeExtend** (`FUN_14021da80`).
  - Que el contexto que modifica `FUN_1402304f0` (`*(p+0x18)`) sea el mismo que leen los handlers (`*(p[3]+0x20)`):
    **por estructura**, sin comprobar el llamador.
  - **Corrige damage.md §4**: las escrituras `0x14021e3c6` / `0x140220681` que allí se atribuyen a `b8+0x420` son de
    este contexto (`+0x420/+0x428 = 1.0`, `+0x424 = 0`, `+0x42c = 1`), no del bloque `b8`.
- **Uso de cada clave** en los handlers:
  - clave 1 = cantidad de la curación (`caseD_19`);
  - clave 0 = duración (`caseD_19`, `100080[01]` `caseD_9`, `100700[04]` `caseD_18`).
  - Así, la duración solo escala si el SpEffect tiene `100040[06]`. Ninguna curación lo tiene; Magic Weapon, Flash Sweat e
    Iron Flesh sí.
- `FUN_14023c660 = FUN_14023cbd0 × FUN_14023cb60`:
```c
// args: u8 clave, u8 stat (+1), u16 lo (+2), u16 hi (+4), f32 f_lo (+8), f32 f_hi (+0xc), f32 c1..c4 (+0x10..+0x1c)
FUN_14023cbd0: if (stat == 0) return 1.0;
    s = stats_efectivos(JUGADOR LOCAL)[stat − 1];   // FUN_14023cce0: DAT_1416148f0+0xd0; tabla 0x1410cf240: código n → slot n−1
                                                    // sin jugador local: s = lo
    if (hi == lo || f_hi == f_lo) return f_lo;
    return clamp(f_lo + (s − lo)/(hi − lo) × (f_hi − f_lo), f_lo, f_hi);
FUN_14023cb60: según thunk_FUN_141c592e8(chr)+0x10: 1 → c1, 2–3 → c2, 4 → c3, 5–6 → c4, otro → 1.0
```
  - **Ojo**: el stat se lee del **jugador local**, no del que recibe el SpEffect. En una partida sin otros jugadores es
    el lanzador.
  - Qué es la categoría de `FUN_14023cb60`: **sin identificar**. En todas las filas citadas aquí `c1..c4 = 1.0`.

## 7. Datos — milagros de curación
Bala 1 = pegada al lanzador (`+0x05 = 2`), esfera en la dummy 200 (catalizador), PDP `+0x40 = 0x06` (solo el lanzador),
sin daño. Bala 2 = `+0x04 = 0` / `+0x05 = 0` / `v0 = 0`: queda quieta donde nace, en la dummy **9** del cuerpo
(`c0001.flv`: hueso `Master`, posición (0, 0, −0.0002) → **los pies**). Su PDP aplica la curación a **lanzador + aliados**
(`0x0c`) o **solo al lanzador** (`0x04` / `0x06`). Cada bala golpea a cada personaje una vez (casting.md §7.4).

| Hechizo | Fe req. (`SpellParam +0x0a`) | Selección | Bala 2: radio / vida / quién | Curación por variante k = 0..4 | Duración | Multiplicador (`100040[00]`, clave 1) | Corta al llenar |
|---|---|---|---|---|---|---|---|
| Heal 32010000 | 12 | Fe ≥ 0/12/22/32/42 | 3.0 / 2.0 s / lanzador + aliados | 300, 450, 500, 550, 600 | 1.0 s | Fe 12→22: 1.0→1.1 | sí |
| Med Heal 32020000 | 18 | Fe ≥ 0/15/18/28/38 | 3.0 / 2.0 s / + aliados | 400, 600, 800, 825, 850 | 1.25 s | Fe 18→28: 1.0→1.1 | sí |
| Great Heal Excerpt 32030000 | 14 | — (hijo directo 32030001) | 3.0 / 2.0 s / **solo lanzador** (`0x04`) | 1300 | 1.5 s | Fe 10→14: 0.5→1.0 | sí |
| Great Heal 32040000 | 28 | Fe ≥ 0/18/28/38/48 | 3.0 / 2.0 s / + aliados | 600, 1000, 1100, 1200, 1300 | 1.75 s | Fe 28→38: 1.0→1.1 | sí |
| Soothing Sunlight 32050000 | 52 | Fe ≥ 0/32/42/52/62 | **10.0 / 5.0 s** / + aliados | 500, 600, 700, 1000, 1100 | 1.75 s | Fe 52→62: 1.0→1.1 | sí |
| Replenishment 32060000 | 16 | — (hijo 32060001) | 3.0 / 2.0 s / solo lanzador (`0x06`) | 840 | **120 s** | Fe 10→16: 0.5→1.0 | no |
| Resplendent Life 32070000 | 25 | — (hijo 32070001) | 3.0 / 2.0 s / solo lanzador (`0x06`) | 840 | **60 s** | Fe 15→35: 0.5→1.3 | no |
| Bountiful Sunlight 32080000 | 38 | Fe ≥ 0/18/28/38/48 | **10.0 / 2.0 s** / + aliados | 400, 500, 750, 880, 1250 | 100, 100, 125, 125.714, 178.571 s | k 0–1: Fe 38→48 1.0→1.1; k 2–4: 0.9→1.0 | no |

- En Great Heal Excerpt, Replenishment y Resplendent Life la bala 1 tiene hijos (`+0xec` / `+0xed`) en lugar de SpEffect
  de selección. La bala 1 de Replenishment / Resplendent tiene PDP `0x04` y SpEffect `32060000` / `32070000`, que solo
  llevan dos `100170[01]` (§9).
- Todas las curaciones llevan además `100040[01]` (SFX 5200…5208 en la dummy 505) y `100700[04] (3.0)` (entrada del
  grupo Others de 3 s; **para qué sirve: sin identificar**).
- Las regeneraciones tienen bandera 0: **siguen con la vida llena**. Ritmo = cantidad / duración. Replenishment a
  multiplicador 1.0: **7 PS/s** durante 120 s. Resplendent: 14 PS/s × m durante 60 s. Bountiful k 0: 4 PS/s.
- **Caressing Prayer 32090000** (Fe 15): bala 2 radio 3.0, 2.0 s, en la dummy 249 (hueso `Pelvis`), lanzador + aliados.
  - `100070[00] (1, 871, k)` con k = 0, 8, 11 → limpia veneno, veneno tóxico y empapado de veneno (tabla de bonfire.md).
  - `1000[01] (0, 1, 100)` y `(5, 1, 100)`: multiplicadores 0 y 5 ×100 durante 1 s (índices sin identificar).
  - Sin curación de PS.

## 8. Datos — buffs propios
| Hechizo | Cadena | Instrucciones del SpEffect |
|---|---|---|
| **Magic Weapon** 31180000 (Int 10; izquierda 31181000) | bala 31180000 pegada, vida 0, PDP `0x06` sin SpEffect → hijo al acabar la vida (`+0xed`) 31180001: pegada, vida 1.0, esfera r 3.0 en la dummy 20, `0x06`, SpEffect **31180000** (`+0x4d = 0x1a`, `+0x4e = 2`) | `100040[06]` (permite escalar la duración) · `100130[04] (5177, 0.1, 0, 1, 0, 0)` SFX de arma · `100040[01] (5310, 277, 0)` SFX (276 en la versión izquierda) · `100040[00]` clave 0, **Int 10→15: ×0.7→×1.5** · `100080[01] (90, 1, 15)` · `100090[02] (90, 1, 30)` |
| Great Magic Weapon 31190000 | igual | `100040[00]` clave 0, Int 10→27: ×0.3→×1.2 · `100080[01] (90, 1, 20)` · `100090[02] (90, 1, 30)` |
| Crystal Magic Weapon 31200000 | igual | `100040[00]` clave 0, Int 14→42: ×0.3→×1.1 · `100080[01] (90, 1, 30)` · `100090[02] (90, 1, 15)` |
| **Flash Sweat** 33180000 | bala 1 pegada, `0x06`, SpEffect 33180000 (dos `100170[01]`) + hijo 33180010 (vida 1.0, r 3.0, `0x06`) → SpEffect **33180010** | `100040[06]` · `100040[01] (5257, …)` · `100090[04] (90, 3, 30)` |
| **Iron Flesh** 33190000 | igual → SpEffect **33190010** | `100040[06]` · `100040[01] (5258, …)` · `1000[09] (21, 25, 4)` · `1000[00] (19, 25, 3)` · `100090[04] (25, 0, 100)` · `100090[04] (25, t, 20)` con t = 1..6, 8, 9 · `1000[01] (26, 25, 0.6)` · `1000[09] (33, 25, 100)` · `1000[00] (1, 25, 65)` · `100090[06] (25, 0.9, 1)` |

Qué se sabe de cada instrucción:
- **Duración**:
  - `100080[01] (t, i, v)` (`caseD_9` → `FUN_140225af0` / `FUN_1402225a0`): entrada del grupo Property con duración
    `t × ctx(clave 0) × mulB.tiempo` (`vt+8` → `+0x24`) y valor `v` en la casilla `i` (`+0x50 + 4i`, i < 10).
    - Si ya hay una entrada del mismo tipo y el mismo `u16 +0x2c` (de la petición), **solo cambia la casilla; no renueva
      la duración** (`FUN_140222580`).
    - Para qué sirve cada casilla: **sin identificar**. El lector de la suma (`FUN_14014b9d0` → `FUN_14014cdb0`,
      ofuscado) no se terminó de seguir.
  - Acciones (`100090[0x]`, `1000[0x]`): el primer float es la duración (`FUN_14021ded0`: `+0x20 = args[0]` si > 0; −1.0
    = sin fin). Se resta `dt` cada update (`FUN_14021e060`) y la acción vive mientras `+0x20 > 0` (`FUN_14021b660`).
    `FUN_14021df70` multiplica `+0x20` por un factor de tiempo, pero **quién le pasa ese factor (¿`ctx` clave 0?) no se
    trazó**.
  - Con eso: **Magic Weapon dura 90 s × clamp(0.7 + (Int − 10)/5 × 0.8, 0.7, 1.5)** para la entrada Property
    (confirmado). Para `100090[02]`, probable, sin confirmar. Int 10 → 63 s; Int ≥ 15 → 135 s.
  - En Iron Flesh la duración es 25 s; en Flash Sweat, 90 s.
- `100090[02/04] (t, u16 a, u16 b)` (`ChangeAtkDef`):
  - el ctor guarda los argumentos (`+0x40` = puntero), pero su `vt+0x18` está vacío: lo leen otros sistemas
    (cálculo de ataque o defensa), **no trazado**;
  - que `a` sea el tipo de daño (0 físico, 1 magia, 2 rayo, 3 fuego…, como el bloque de ataque de damage.md) y `b` el
    valor encaja con los datos (Magic Weapon a = 1, Flash Sweat a = 3), pero **no está confirmado por código**.
- `1000[00/01/09] (índice, duración, valor)`:
  - `1000[01]` multiplica `b8+0x3bc + 4·índice` (hollowing.md, catalysts.md §6);
  - `1000[00]` / `[09]` suman un entero a un stat.
  - Índices 1, 19, 21, 26, 33 de Iron Flesh: **sin identificar**.
- `100090[06] (25, 0.9, 1)` → `MovementRate<MovingSpeedChange>`: velocidad de movimiento ×0.9 durante 25 s (según el
  nombre de la clase; el lector no se trazó).

## 9. Lo que queda abierto
- **`100170[01] (id)`** (`caseD_10`): entrada del grupo Damage con `+0x50 = id` (32080050 / 32230050) y `+0x54 = −1.0`.
  - Lo llevan Replenishment, Resplendent Life, Bountiful Sunlight, Sacred Oath, Flash Sweat e Iron Flesh.
  - 32080050 / 32230050 existen como PDP (`0x06`, r 0.5, dummy 249) y como SpEffect (`100700[04] (0.5)`).
  - Qué dispara y cuándo (¿al recibir daño?): **sin identificar**.
- **Acumulación del mismo SpEffect**: `FUN_14022eb30` (ofuscado) no se terminó.
  - Lo visto: la petición `+4` (= 1) se usa como número de repeticiones y `+8` (−1.0) entra en una rama si es > 0
    (`FUN_14021e9f0`, ¿duración forzada?).
  - Por grupos: **HealSub siempre crea una entrada nueva** (dos Heal seguidos se suman); Property actualiza la entrada que
    ya existe (arriba). El resto: sin confirmar.
- **Quitar buffs al descansar o al morir**: el SpEffect de la hoguera 110000010 (bonfire.md) no contiene nada que borre
  Property / Status / HealSub. No se encontró otra vía. **Sin confirmar.**
- `100700[04]` (grupo Others), `100040[00]` categoría `FUN_14023cb60`, `PDP +0x40` bit 1, `PDP +0x4d / +0x4e`, la
  petición `+0x1c == 1` (duración 0) y el tipo de resultado 1 de §3.
- Qué valor del arma o catalizador cambia la potencia de Magic Weapon (el "spell buff"): no aparece en estas instrucciones.
- El SFX (`100040[01]`, `100130[04]`) y la luz no se analizaron (no cambian números).

## 10. Valores de referencia
- **Heal, lanzador con Fe 12** (efectiva):
  - StatusSelectBullet: `0 ≤ 12`, `12 ≤ 12`, `22 > 12` → **k = 1** → bala / PDP / SpEffect 32010011;
  - multiplicador: Fe 12 en [12, 22] → 1.0;
  - **450 PS repartidos en 1.0 s** (450 PS/s, unos 15 PS por frame a 30 fps; la fracción se arrastra);
  - se corta al llenar la vida.
  - **Quién**: el lanzador y los aliados que toquen la esfera de **radio 3.0** centrada en sus pies durante **2.0 s**,
    una vez cada uno.
  - La bala 1 (r 2.0, pegada, 0.5 s) solo sirve para aplicarle al lanzador el SpEffect 32010000.
- Heal con Fe 17: k = 1, ×1.05 → `(u16)(472.5)` = **472**. Fe 30: k = 2, ×1.1 (recortado) → **550**. Fe 99: k = 4 → **660**.
- **Soothing Sunlight con Fe 52**: k = 3, ×1.0 → **1000 PS en 1.75 s**, esfera de radio **10.0** durante **5.0 s**,
  lanzador + aliados.
- **Replenishment con Fe 16**: ×1.0 → 840 PS en 120 s = **7 PS/s**, solo el lanzador, sigue con la vida llena.
  Con Fe 10: ×0.5 → 3.5 PS/s.
- **Magic Weapon con Int 10**: ×0.7 → entrada Property de **63 s** con casilla 1 = 15. Con Int ≥ 15: **135 s**.

## 11. Buffs: lectores y fórmulas
Salidas en `D:\Reversing\DS2\out\buffs\` (`b1.c`…`b26.c` decompilados, `d_*.txt` desofuscados, `x*.txt` referencias,
`callscan.py` / `ripscan.py` / `vcall.py` / `dispscan.py` / `b8ctx.py` buscadores en el exe). Contesta casi todo §8 y parte de §9.

### 11.1 Consultas al gestor (cómo se leen las acciones)
- Las acciones viven en una lista de hasta 0x80 punteros (`gestor+0x20`, cuenta `+0x418`; el **contexto de efecto** de §6 son
  los campos que siguen: `+0x420/+0x428/+0x430`). `FUN_14021f100` → `FUN_140220ff0` llama `vt+0x98(acción, salida, &código,
  máscara)` en cada una (consulta tipo 6, código 1..6).
- Seis envoltorios fijan el código: `FUN_14014cdb0` = 1, `FUN_14014ce90` = 2, `FUN_14014ce60` = 3, `FUN_14014cd80` = 4,
  `FUN_14014ce10` = 5, `FUN_14014cde0` = 6 (`mov byte [rsp+0x30], N`). Envoltorios con el gestor del personaje:
  `FUN_14014b9d0` (1), `FUN_14014bae0` (2), `FUN_14014b990` (4), `FUN_14014ba50` (5), `FUN_14014ba10` (6).
- **Máscara de mano** (la misma en `ChangeAtkDef`, `ChrStatus_Rate` y en el grupo Property `FUN_1402259f0`): según la
  ranura `+0x2e` de la acción: `0x1a` o `0xfc` → bit 0; `0x1b` o `0xfd` → bit 1; cualquier otra → bit 2.
  El golpe pide `(mano ≠ 0) + 5` (`FUN_14038f3b0`, `desc+0`: 0 izquierda → 5 = bits 0 y 2; 1 derecha → 6 = bits 1 y 2).
  Las defensas piden 7 (todo).
- **`ChangeAtkDef` `vt+0x98` = `FUN_14021b230`**: tipo de la acción `+0x18` = id de la instrucción (2..5, 9).
  - Responde al código 1 si el id es 2 o 3, al código 2 si es 4 o 5, al código 4 si es 9 (`100090[09]`, no lo usa ningún
    buff de esta nota).
  - `salida[a] += b` para los ids 2, 4, 9 (máscara `0x214`); `salida[a] −= b` para 3 y 5. `a` = `u8 args+4`, `b` = `u16 args+6`
    (Magic Weapon `0x001E0001` → a = 1, b = 30).
  - **`a` es el índice del arreglo de daño** (0 físico, 1 magia, 2 rayo, 3 fuego, 4 oscuro, 5.. estados; el mismo arreglo de
    10 floats de damage.md). **`b` es una suma plana**, no un porcentaje (el lector decide la escala, abajo).
  - Varias acciones del mismo tipo se suman.

### 11.2 Ataque ± (`100090[02]/[03]`) — código 1
- Lector: el constructor del golpe `FUN_140391fe0` → `FUN_14038f3b0(desc, mano, S)`: `S += 1.0 × Σ salida`
  (`FUN_14034c7d0`, `DAT_1410ac698` = 1.0), después de los bonos de stats (`FUN_1403903b0`). Luego `FUN_140391020` multiplica
  `S[0..9]` por el código 8 (`FUN_14014ba90` → `FUN_1402210e0`, producto; 1.0 sin efectos; no lo usan estos buffs).
- Es decir: `valor[i] = max(0, (S_stats[i] + Σb[i] + B[i])·M[i] + F[i])·k` (damage.md §1). **El +30 de Magic Weapon solo
  cuenta si `M[1] > 0`**; lo da su `100080[01]` (abajo).
- Mano: la de la ranura de la acción (§11.6). Hechizos: el golpe del catalizador sale del mismo `FUN_140391fe0`
  (catalysts.md §6), así que un catalizador en la mano encantada también recibe el +30 y el reparto. **Por código; sin
  verificar en el juego.**
- Otros lectores del código 1: `FUN_140201cd1` (menú, con `FUN_1402019f0`).

### 11.3 Defensa ± (`100090[04]/[05]`) — código 2
- Lector único en combate: `vt+0x250` = **`FUN_14031fe20`** (está en las vtables del jugador `0x1410e4ec8` y del enemigo
  `0x1410c9f58`; `FUN_14014bae0`, máscara 7):
  ```c
  sum = Σ salida;  out[0] += sum[0];  out[i] += 0.01 × sum[i]  (i ≥ 1; DAT_1410acb08 = 0.01)
  ```
- **Físico (a = 0) → defensa plana**: `vt+0x248` = `FUN_140320380` devuelve `out[0]` de `vt+0x250` con `out` a cero. Lo suman
  la defensa del jugador (`FUN_140380070`, último término, `0x14038020a`) y la del enemigo (`FUN_14031e6b0`, `0x14031e6e4`):
  `def_0 = … + Σb[0]`.
  - **Corrige damage.md §4**: `vt+0x248` no es `FUN_140380400` (5.5) ni `FUN_1401f9ac0` (1.0). Esas dos están en `vt+0x240`
    (vtables volcadas desde `+0x190`: `vt+0x198` = `FUN_140380f30` / `FUN_14031f2b0`, `+0x1d0` = `FUN_140380070` /
    `FUN_14031e6b0` cuadran con damage.md). **Sin buffs el término vale 0**: el jugador no tiene `+5.5` ni el enemigo `+1.0`.
    Ejemplo de damage.md §7(b) con Knight: `def = 239 + 1.33·78 = 342.74` → `(1582.5 − 342.74)/12 = 103.3` → **103**.
- **Elementos y estados (a ≥ 1) → absorción**: `abs[a] += 0.01·b`. Jugador `FUN_140381350` (`0x14038179d`, luego cada
  valor se recorta a [0, 1] y después al tope); enemigo `FUN_14031f570` (`abs = EnemyParam·0.01 + out`, recorte [0, 1]). El
  `out[0]` que llega aquí se descarta (absorción física = 0 en los dos).
  - En el enemigo, los índices ≥ 5 van a `param_2[6..12]` junto con `EnemyParam +0xb0..+0xbc`: 5 → [6] y [11], 6 → [7],
    7 → [8], 9 → [9], 8 → [12]. Qué estado es cada uno: **sin confirmar** (resins.md da 5 = veneno, 6 = sangrado).
- El menú (`FUN_140200940`, `FUN_140201780`, desde `FUN_140200610`) suma `sum[0]` a las cuatro defensas físicas mostradas.

### 11.4 Grupo Property (`100080[00]/[01]`, args `(f32 t, u8 casilla, f32 v)`)
- El tipo de la entrada (`+0x14`) es el id de la instrucción: `[00]` → tipo 0, `[01]` → tipo 1. **La casilla es el índice
  del arreglo de daño** (lo suma `FUN_140222470`, 10 floats `+0x50..`).
- **Tipo 1** (Magic Weapon, resinas): `FUN_14038f440` → `FUN_14014bb90` → `FUN_14022e3d0` (`FUN_1402259f0` tipo 1, máscara
  de mano): `M[i] += 0.01 × Σv[i]` (`DAT_1410acb08`). **Casilla 1 = reparto de magia**: Magic Weapon +0.15, Great +0.20,
  Crystal +0.30. El menú lo lee con `FUN_14014cd70`. `FUN_14022e420` compara tipo 1 con el código 3 (`ChrStatus_Rate`
  índices 12..21) y elige el máximo; quién usa ese índice: **sin identificar**.
- **Tipo 0** (ningún buff de esta nota): `FUN_14014bb20` → `FUN_14022e2f0` (máscara 7). Jugador: defensa física
  `× max(1 + 0.01·v[0], 0)` (`FUN_140382000`, es el `SpE7` de damage.md) y absorción `+0.01·v[t]` (`FUN_140381350`).
- Duración: `t × mulB.tiempo × ctx(clave 0)`; valor: `v × mulB.cantidad` (`caseD_9`, `FUN_140225af0`).

### 11.5 Duración de las acciones (`100090`, `1000`)
- `FUN_1402200f0(ctx, instr)` (desde `FUN_140230ad0`, el bucle de instrucciones) crea la acción y llama `vt+0x20` =
  `FUN_14021df70(acción, instr, &factor, ctx+0x428)` con `factor = ctx+0x430 ≠ 0 ? ctx+0x420 : 1.0`. Así,
  **`duración = t × ctx(clave 0)`**, el mismo factor que Property. **Confirmado**: el contexto que escribe `100040[00]`
  (`FUN_1402304f0`, `*(p+0x18)`) es el mismo objeto que recibe `FUN_1402200f0` (`*(p+0x18)`), y es la lista de acciones
  `gestor+0x20` que leen los handlers.
- `FUN_140220670` (llamado por el update del gestor `FUN_14022f820`) pone `+0x420/+0x428` a 1.0 y `+0x430` a 0 antes de
  avanzar las acciones: el factor dura lo que dura la aplicación. Las instrucciones corren en el orden del emevd; en Magic Weapon
  `100040[06]` y `100040[00]` van antes que `100080[01]` y `100090[02]`.
- Cuenta atrás: `FUN_14021e060` resta `dt` salvo en el modo `+0x30` 1 o 2. El modo 1 (no caduca) se da si la ranura está
  en `0xf4..0xfd` (`FUN_14023da90`, ranuras de equipo); el modo 2 lo tienen las acciones de la tabla `0x14156f9c8`
  (`100040[01]`, `[08]`, `[04]`, `[05]`, `100130[02]`, `[04]`, `100110[03]`; `FUN_14021e0b0`). Las ranuras 26, 27 y 31 caducan.

### 11.6 Ranura (`PDP +0x4d`) y modo (`PDP +0x4e`) — vida del buff
- **`PDP +0x4d` es la ranura del efecto.** `FUN_14023e170` la traduce a un índice con la tabla `0x141571490` (pares
  `{byte, bandera}`; 26 → 19, 27 → 18, 31 → 16, 0 → 25…) y `FUN_14023e160` hace el camino inverso. La aplicación
  (`FUN_14014bec0`) y el bucle (`FUN_140230ad0`) vuelven al byte, que acaba en `+0x2e` de cada acción o entrada.
- **`PDP +0x4e` es el modo de aplicación** (se guarda en `+0x2f`). Antes de ejecutar, `FUN_14022fe50` busca efectos activos:
  - con ranura en `0x12..0xfd` (`FUN_14023da70`): los de la misma ranura (`FUN_14021f3a0` en acciones, `FUN_140235780` en
    los grupos);
  - si no, los del mismo id de SpEffect (`FUN_14021f2c0`, `FUN_140234d70`).
  - Si el encontrado tiene modo **2** se borra todo lo de esa ranura (`FUN_1402302e0` → `FUN_1402203f0` + `FUN_140239690`)
    o de ese id (`FUN_140230370`) y se aplica el nuevo. Si tiene modo **3**, el nuevo no se aplica.
  - Los demás modos: **sin analizar**.
  - Cada aplicación lleva un número de serie nuevo (`FUN_1402277b0`, contador `+8`) en `+0x2c`.
  - `caseD_9` repite el borrado por ranura con otro serie (`FUN_14022f750`).
- Datos: todos estos buffs tienen modo 2.

| Hechizo | `+0x4d` | Ranura → mano |
|---|---|---|
| Magic Weapon 31180000 / Great 31190000 / Crystal 31200000 (bala derecha, `SpellParam +0x0c`) | `0x1a` | 26 → **arma izquierda** |
| 31181000 / 31191000 / 31201000 (bala izquierda, `+0x100`) | `0x1b` | 27 → **arma derecha** |
| Flash Sweat 33180000/33180010, Iron Flesh 33190000/33190010 | `0x1f` | 31 → las dos manos y defensas |
| Replenishment (§3) | `0x1f` | 31 |
| Resinas (resins.md) | 27 | 27 → arma derecha |

  La esfera hija va en la dummy 20 (versión derecha) o 21 (izquierda).

- Consecuencias (por código, **sin verificar en el juego**):
  - **Relanzar el mismo buff = renovar**, no acumular: lo viejo de la ranura se borra y lo nuevo empieza con su duración entera.
  - Los buffs de **una misma ranura se reemplazan entre sí**:
    - Magic Weapon / Great / Crystal en la misma mano;
    - una resina y el Magic Weapon lanzado con la mano izquierda (los dos en 27);
    - Flash Sweat, Iron Flesh y Replenishment (31). Además, al aplicar 33180010 se borra lo que dejó 33180000 (su bala madre,
      misma ranura).
  - Con los dos Magic Weapon a la vez, cada uno en su mano, conviven.
- **Cambiar de arma** (`FUN_140349d30(equipo, mano, ranura)`, al cambiar el arma activa de una mano; también al equipar,
  `FUN_1403482a0` / `FUN_140349ed0`) → `FUN_14034b6b0` → `FUN_14022f710(gestor, mano ? 0x1b : 0x1a)`: **borra los efectos de
  la ranura de esa mano**.
  - Solo si `DAT_1416148f0 vt+0x60 == 3`; qué estado global es ese: **sin identificar**.
  - También confirma por código que 26 = mano 0 (izquierda) y 27 = mano 1 (derecha).
- **Descansar**: la hoguera aplica 110000010 con ranura 0 (índice 25) y modo 2 (`FUN_1402027b0`); la ranura 0 no es de mano,
  así que solo reemplaza por id. **No toca los buffs** (sin otra vía encontrada).
- **Morir**: no se encontró quién borra. Los borrados del gestor son `FUN_14022f710` (ranura: cambio de arma y
  `FUN_140221a40` con 0x1d), `FUN_14022f7c0` (id; un llamador ofuscado en `0x141b898d7` sin seguir) y `FUN_14022ea10`.
  **Sin confirmar.**

### 11.7 Instrucciones de stat de Iron Flesh
`FUN_14031c580` (cada frame, antes de las acciones) pone a cero `b8+0x305..+0x31b` (s8, `1000[00]`, índices 0..22) y
`b8+0x31c..+0x3bb` (i32, `1000[09]`, índices 0..39), y a 1.0 `b8+0x3bc..` (f32, `1000[01]`) y `b8+0x134`. Las acciones lo
vuelven a aplicar en `vt+0x18` (`FUN_14021a960`: `b8+0x305+i += s8 args+8`; `FUN_14021a920`: `b8+0x31c+4i += i32 args+8`;
`FUN_14021bd30`: `b8+0x3bc+4i *= f32 args+8`).

| Instrucción | Campo | Lector | Efecto |
|---|---|---|---|
| `1000[00] (1, 25, 65)` | `b8+0x306` | `FUN_14037e430` (equipload.md): peso efectivo `= b8+0x4ac × peso + (s8)b8+0x306` | **+65 de peso** |
| `1000[00] (19, 25, 3)` | `b8+0x318` | `FUN_14030eb60` lo copia cada frame a `chr.b0+0x24` (int) | **sin identificar** (lector de `b0+0x24` no trazado) |
| `1000[09] (21, 25, 4)` | `b8+0x370` | no encontrado | **sin identificar** |
| `1000[09] (33, 25, 100)` | `b8+0x3a0` | `FUN_140145a40`: `chr+0x220 = vt+0x1f8 + ChrParam+0x118 + (s8)b8+0x305 + b8+0x3a0`, y `chr+0x218` se recorta a `[+0x21c, +0x220]` | **+100 al máximo de `chr+0x218`** |
| `1000[01] (26, 25, 0.6)` | `b8+0x424` | `FUN_1401373b0` (`0x1401378ed`): factor de la víctima en `A+0x78` (damage.md §4, multiplica el total) | **daño recibido ×0.6** |
| `100090[06] (25, 0.9, 1)` | `b8+0x11c[6]` (`b8+0x134`) | `FUN_14021e8d0`: `set(6, get(6) × 0.9)`; `FUN_14031c7f0` = producto de `b8+0x11c[0..6]` (velocidad, equipload.md §3) | **velocidad ×0.9** |

- `vt+0x1f8` del jugador (`FUN_14037fdd0`) = `Σ_piezas ArmorParam+0x40 × falta (fila 0x1c) + derivada +0x4c`.
  `ArmorParam +0x40`: Knight 8 / 20 / 6 / 12, yelmo de Havel 19. Por eso parece el aguante de golpe (poise), pero el nombre
  **no está confirmado**.
- `100090[06]`: el byte `args+8` elige a quién se aplica: bit 0 si el personaje es de tipo 2 (`DAT_1410bfff0[tipo·5]`,
  `FUN_140203c10`, como el jugador en §4); bit 1 si no. Iron Flesh trae 1 → solo personajes de tipo 2.
- Además lleva `100090[04] (25, 0, 100)`, o sea **+100 de defensa física plana**, y `(25, a, 20)` con a = 1..6, 8, 9, o sea
  **+20 puntos de absorción** en magia, rayo, fuego, oscuro y los estados 5, 6, 8 y 9.

### 11.8 Valores de referencia
- **Magic Weapon a Int 10**, Longsword +0 en la **mano derecha** (lanzado con el catalizador en la izquierda, 31181000;
  Fue 16 / Des 11, requisitos cumplidos, k = 1):
  - duración `90 × 0.7` = **63 s** (Property y ataque);
  - magia `= (bono(Int 10) = 53 × c[2] 0.35 + 30 + B 112) × (0 + 0.15)` = **24.08**;
  - el físico no cambia (158.25).
- Con Int 20: 1.5 → **135 s**, magia `(37.1 + 30 + 112) × 0.15` = **26.86**.
- Lanzado con la derecha (31180000) encanta el **arma izquierda**, que lleva `WTP+0x54` = 0.9 en k.
- Con Int 10:

  | | Duración | Longsword +0 |
  |---|---|---|
  | Great Magic Weapon | ×0.3 → 27 s | 32.11 |
  | Crystal Magic Weapon (por debajo de su mínimo, ×0.3) | 27 s | 43.66 |

- Otras armas +0, Int 10 / 20 (mismo cálculo; `c[2]` = 0.35 en las ocho del mod):

  | Arma | MW | GMW | CMW |
  |---|---|---|---|
  | Battle Axe | 28.28 / 31.06 | 37.71 / 41.42 | 52.06 / 57.63 |
  | Mace | 25.28 / 28.06 | 33.71 / 37.42 | 46.06 / 51.63 |
  | Club, Spear | 23.03 / 25.81 | 30.71 / 34.42 | 41.56 / 47.13 |
  | Pickaxe | 29.78 / 32.56 | 39.71 / 43.42 | 55.06 / 60.63 |
  | Fists | 8.78 / 11.56 | 11.71 / 15.42 | 13.06 / 18.63 |
  | Drangleic Shield | 20.18 / 22.96 | 26.91 / 30.62 | 35.86 / 41.43 |

  - La magia entra en la víctima como cualquier elemento (damage.md §5): `max(piso, 6·AR)/6 · (1 − abs) · tasa`.
- **Flash Sweat** (33180010): absorción de **fuego +0.30** durante **90 s** (tiene `100040[06]` pero no `100040[00]` → ×1.0).
  Desnudo con Int = Fe = 10: `0.16 + 0.30 = 0.46` → el fuego entra ×0.54.
- **Iron Flesh** (33190010), **25 s**: defensa física +100, absorción +0.20 (magia/rayo/fuego/oscuro y estados), daño
  recibido ×0.6, máximo de `chr+0x218` +100, peso +65, velocidad ×0.9.
- Las resinas (resins.md) usan las mismas instrucciones (`100080[01]` + `100090[02]`), los mismos lectores y el mismo modo (2),
  en la ranura 27 y sin `100040[06]` (90 s fijos). Sus números de resins.md omiten `S` de stats: con stats hay que sumar
  `bono·c[i]` antes de multiplicar por 0.2.

### 11.9 Lo que sigue abierto
- Quién lee `chr.b0+0x24` (Iron Flesh índice 19) y `b8+0x370` (índice 21).
- El nombre de `chr+0x218..+0x220` (¿poise?).
- Qué estado es cada índice ≥ 5 del arreglo de absorción.
- Modos de aplicación distintos de 2 y 3.
- Borrado al morir.
- El estado `vt+0x60 == 3` del borrado al cambiar de arma.
- Que todo esto se vea igual en el juego.
