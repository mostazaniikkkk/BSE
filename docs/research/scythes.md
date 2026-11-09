# Guadañas y hoces de DS2 (candidatas para la azada de Minecraft)

Investigación para decidir si la azada ("la oz") puede ser un arma. **No se implementó nada** y no se tocó
`tools/re/weapons.py` ni `src/main/resources`: los datos salen de una ejecución de prueba en una carpeta aparte.

Fuentes:
- Regulation SotFS (`D:\Reversing\DS2\extracted\regulation`): `WeaponParam` WP, `WeaponTypeParam` WTP,
  `WeaponActionCategoryParam` WAC, `WeaponAttackMotionParam` WAMP, `WeaponReinforceParam` WRP,
  `WeaponStatsAffectParam` WSA, `ReinforceCostParam` RCP, `FeWeaponThreshold`. Offsets como en
  [weapons.md](weapons.md) y [damage.md](damage.md).
- Nombres: `D:\Reversing\DS2\extracted\menu_text\itemname.txt` (inglés) y
  `D:\Reversing\DS2\out\hollow\text\menu__Text__NeutralSpanish__itemname.txt` (español neutro; Latin-1).
- Camino de mejora: Ghidra, salidas en `D:\Reversing\DS2\out\scythes\` (`rcp_xrefs.txt`, `rcp_callers.c`,
  `rcp_c250.c`).
- Scripts de prueba (fuera del repo): `C:\Users\Laptop\AppData\Local\Temp\claude\63f5fe3c8140254e\557f977c-a61e-4da3-a214-bcd2510dd3b2\scratchpad\scy\`
  (`data.py`, `mv.py`, `cost.py`, `dry.py`; salida en `out\`).

## 1. Qué es una guadaña en los datos

DS2 no guarda el nombre de la categoría ("guadaña") en la fila del arma. Lo que agrupa a las armas es la fila de
`WeaponTypeParam` (`WP +0x10`). Con ese criterio:

- **WTP 150**: las 8 armas con nombre del rango 3000000–3080000. Los nombres de los `.xmd` de sus animaciones lo
  confirman: `a30_01_0010_ScytheAttack1HandLight1_R_Hit`, etc. (grupo `a30` = *Scythe*).
- **WTP 151**: solo la **Silverblack Sickle** (2100000). Está en el rango de ids de las hachas y usa animaciones de hacha
  (`a35_..._AxeAttack...`). También comparte con las hachas la postura a dos manos 214310, el guardado 10 y la categoría
  de powerstance 90.
- **"Scythe" 3210000**: a pesar del nombre, es **WTP 290**, el mismo tipo que Lucerne (3200000) y Halberd (3220000).
  Sus animaciones son `a37_..._PoleAxe...`. En los datos es una alabarda.
- Otras filas de WTP 150 sin nombre en `itemname`: 784000, 821000, 851000, 910000, 6410000. No son objetos del jugador
  (ver "Sin confirmar").

## 2. Lista completa

Física y elemento base = WRP `+0x00..` (nivel 0) y `+0x24..` (nivel máximo). Reparto = WRP `+0xa0..` × 0.01, con los
tipos 0 físico, 1 magia, 2 rayo, 3 fuego y 4 oscuro (damage.md §1). Requisitos = WP `+0x18..` (Fue/Des/Int/Fe). Peso =
WP `+0x20`. Nivel máximo = WRP `+0x48`. Escalado = coeficientes WSA (`WRP+0x4c`, `+8 + nivel·0x24`), con la letra
calculada según [scaling_letters.md](scaling_letters.md) (umbrales de `FeWeaponThreshold`).

| WP id | Inglés | Español neutro (itemname) | WTP | WAC | Req. Fue/Des/Int/Fe | Peso | Base +0 | Base máx. | Escalado +0 → máx. |
|---|---|---|---|---|---|---|---|---|---|
| 2100000 | Silverblack Sickle | Hoz platinegra | 151 | 2100 | 15/10/8/8 | 4.0 | 80 fís. + 60 osc. | 160 + 105 (+10) | Fue C, Des C, Osc C → Fue C (0.50), Des C, Osc C (0.40) |
| 3000000 | Great Scythe | Gran guadaña | 150 | 3000 | 12/14/0/0 | 5.0 | 140 fís. | 280 (+10) | Fue E, Des B (0.40) → Fue E, Des A (0.45) |
| 3010000 | Great Machete | Gran machete | 150 | 3010 | 18/16/0/0 | 6.0 | 135 fís. | 270 (+10) | Fue C (0.50), Des D → Fue B (0.55), Des D |
| 3020000 | Full Moon Sickle | Hoz de luna llena | 150 | 3020 | 20/18/0/0 | 8.0 | 140 fís. | 280 (+10) | Fue D, Des B (0.35) → Fue D, Des B (0.40) |
| 3040000 | Crescent Sickle | Hoz de media luna | 150 | 3040 | 12/19/12/0 | 8.0 | 100 fís. + 75 mag. | 200 + 150 (+10) | Fue E, Des C, Mag C → igual (Mag 0.40) |
| 3050000 | Scythe of Nahr Alma | Guadaña de Nahr Alma | 150 | 3050 | 14/12/12/12 | 5.0 | 115 fís. + 90 osc. | 173 + 100 (+5) | Fue D, Des B, Osc C → Des B (0.378), Osc C (0.378) |
| 3060000 | Bone Scythe | Guadaña de hueso | 150 | 3060 | 28/20/0/0 | 10.0 | 130 fís. | 325 (+5) | Fue A (0.95), Des – → Fue A (0.978) |
| 3070000 | Scythe of Want | Guadaña del deseo | 150 | 3070 | 14/20/18/18 | 12.0 | 104 fís. + 120 osc. | 260 + 200 (+5) | Fue D, Des B (0.40), Osc C → Des B (0.428), Osc C (0.378) |
| 3080000 | Scythe of the Forlorn | Guadaña de los Forlorn (Spanish: "de los Solitarios") | 150 | 3080 | 15/20/10/10 | 11.0 | 161 fís. + 47 osc. | 210 + 70 (+5) | Fue D, Des A (0.45), Osc C (0.45) → Des A (0.478), Osc C |
| 3210000 | Scythe (alabarda, WTP 290) | Guadaña | 290 | 3210 | 20/11/0/0 | 5.0 | 165 fís. | 330 (+10) | Fue D (0.30), Des D → Fue B (0.55), Des D |

WAC id = `WP +0x0c`. En todas coincide con el id del arma / 1000.

### 2.1 Camino de mejora

Código: `FUN_140358ee0` es el getter de `ReinforceCostParam` (slot `+0x510` del gestor, `out\damage\plist.txt` 0x20).
Lo llaman `FUN_14003c490` / `FUN_14003c690` / `FUN_14003b480` con la fila del objeto `+0xf0` (`+0xe8` va a
`CustomAttrSpecParam` con `FUN_1403587b0`, slot `+0x4f0`, y `+0xec` a `CustomAttrCostParam` con `FUN_140358780`, slot `+0x500`).
`FUN_14003c690` acepta el nivel `n < 10` si `RCP[n·4] ≠ 0` (almas) o `RCP[+0x28 + n·4] > 0` (objeto). Por lo tanto
**RCP = `WRP+0xf0`**, y por nivel la fila tiene almas `+0x00 + 4n`, id del objeto `+0x28 + 4n` y cantidad (u16) `+0x50 + 2n`.

| Arma | Fila RCP | Camino |
|---|---|---|
| Silverblack Sickle | 301600 | Titanita normal +10: fragmento ×1–3 (+1..+3), fragmento grande ×1–3 (+4..+6), trozo ×1–3 (+7..+9), losa ×1 (+10); 520 → 2060 almas |
| Great Scythe | 201800 | Titanita normal +10 (igual reparto); 440 → 1750 almas |
| Great Machete / Full Moon Sickle / Crescent Sickle | 301800 | Titanita normal +10; 520 → 2050 almas |
| Scythe (alabarda) | 301900 | Titanita normal +10; 570 → 2250 almas |
| Scythe of Nahr Alma | 10001900 | Titanita centelleante ×1..×5, +5; 1120 → 2520 almas |
| Scythe of the Forlorn | 20020100 | Titanita centelleante ×1..×5, +5; 1450 → 3260 almas |
| Bone Scythe | 20002300 | Hueso de dragón petrificado ×1..×5, +5; 1150 → 2570 almas |
| Scythe of Want | 20002400 | Hueso de dragón petrificado ×1..×5, +5; 1480 → 3330 almas |

Ids de objeto (itemname): 60970000 Titanite Shard, 60975000 Large Titanite Shard, 60980000 Titanite Chunk,
60990000 Titanite Slab, 61000000 Twinkling Titanite, 61030000 Petrified Dragon Bone.

### 2.2 Movesets compartidos

Las filas de WAC son distintas, pero su contenido (los 64 ids de WAMP y, por lo tanto, animación, daño por golpe y
costes) es **idéntico** en tres grupos:

| Grupo | Armas | R1 una mano | R2 una mano |
|---|---|---|---|
| A | Great Scythe, Scythe of Nahr Alma, Bone Scythe | `a30_01_0010/0020` ScytheAttack | `a30_01_0030`, `a37_01_0021` |
| B | Great Machete, Full Moon Sickle, Crescent Sickle | `a37_01_0020` PoleAxe, `a30_01_0020` | `a30_01_0030`, `a37_01_0021` |
| C | Scythe of Want, Scythe of the Forlorn | igual que A | igual que A; a dos manos cambia el R2 (`a30_03_0037`, `a30_03_0030`) |
| — | Silverblack Sickle (propio) | `a35_01_0010/0020` Axe | `a35_01_0031`, `a31_01_0020` |
| — | Scythe 3210000 (propio) | `a37_01_0020/0021` PoleAxe | `a37_01_0030/0031` |

Slots iguales entre grupos: A–C 62/64, A–B 55/64, B–C 53/64, guadaña-alabarda 24–32/64, Silverblack con las demás
15/64 (`mv.py`). Los tres grupos de WTP 150 comparten los críticos (`a32_01_0300/0310/0320`), la patada, los ataques en
caída y salto (`a29_01_0240/0250`) y la powerstance (`a37_05_0010..0030`). Ninguno tiene ataques desde guardia (slots
57..60 vacíos).

## 3. Candidatas por material (para que elija el usuario)

El mod convierte el material en nivel de mejora (elección del usuario, damage.md): madera +0, piedra +1, hierro y oro
+3, diamante +6 y netherita +10. `Ds2Damage` limita el nivel al máximo del arma, así que un arma +5 ya está al máximo
desde diamante. Base por tipo con reparto > 0, interpolada como `FUN_14034e820` (damage.md §1):

| Arma | Camino | +0 | +1 | +3 | +6 | +10 |
|---|---|---|---|---|---|---|
| Silverblack Sickle | titanita +10 | 80 / 60 osc. | 88 / 64 | 104 / 74 | 128 / 87 | 160 / 105 |
| Crescent Sickle | titanita +10 | 100 / 75 mag. | 110 / 82 | 130 / 98 | 160 / 120 | 200 / 150 |
| Scythe of Want | hueso +5 | 104 / 120 osc. | 135 / 136 | 198 / 168 | 260 / 200 | 260 / 200 |
| Scythe of Nahr Alma | centelleante +5 | 115 / 90 osc. | 127 / 92 | 150 / 96 | 173 / 100 | 173 / 100 |
| Bone Scythe | hueso +5 | 130 | 169 | 247 | 325 | 325 |
| Great Machete | titanita +10 | 135 | 148 | 176 | 216 | 270 |
| Great Scythe | titanita +10 | 140 | 154 | 182 | 224 | 280 |
| Full Moon Sickle | titanita +10 | 140 | 154 | 182 | 224 | 280 |
| Scythe of the Forlorn | centelleante +5 | 161 / 47 osc. | 171 / 52 | 190 / 61 | 210 / 70 | 210 / 70 |
| Scythe (alabarda) | titanita +10 | 165 | 182 | 214 | 264 | 330 |

La tabla está **ordenada por ataque físico a +0**, de menor a mayor. Sumando el elemento, el orden a +0 cambia:
Bone 130, Great Machete 135, Silverblack / Great Scythe / Full Moon 140, Scythe 165, Crescent 175, Nahr Alma 205,
Forlorn 208, Want 224.

Los datos dejan ver lo siguiente:
- Las de titanita normal (+10) crecen hasta el doble. Las de centelleante o hueso (+5) empiezan más arriba, pero se
  estancan en +5.
- Estrictamente guadañas de WTP 150 hay 8, con 3 movesets distintos. Con 6 materiales, como mínimo dos materiales
  tendrían el mismo moveset con otros números, igual que pasa con las espadas.
- Si solo se quieren guadañas de WTP 150, quedan fuera la Silverblack (moveset de hacha) y la Scythe 3210000 (alabarda).
- Precedente de las tablas del usuario (solo como dato, sin elegir): ya usa armas +5 de centelleante en piedra
  (Puzzling Stone Sword), diamante (Dragonslayer's Crescent Axe) y netherita (Red Rust Sword). Para madera usó la de
  menor físico (Broken Straight Sword, 80).

## 4. Exportación (prueba en seco)

Se cargó el módulo `tools/re/weapons.py` (sin editarlo), se agregaron las 10 armas a `WEAPONS` en memoria y se llamó
`main()` con salida en la carpeta de pruebas, con `DS2_WORK=D:/Reversing/DS2` y `DS2_RESOURCES` apuntando también a la
carpeta de pruebas. Después se corrieron `cast_origins.py` y `ds2_anims.py` sobre esa copia. `git status` de
`src/main/resources` quedó sin cambios.

- `weapons.py`: termina bien, con 34 armas y 166 animaciones (hoy son 24 y 143). Solo aparece el aviso que ya existía
  (`mace right dash: no c0001.nmb node`, TAE 33010210). Las 10 armas no dan ningún error. Agrega **23 animaciones
  nuevas**: 30010010/20/30, 30010210, 30030010/20/30/37, 30030210/220, 32010300/310/320, 37010020/30/31, 37010220,
  37030010/30, 37030210 y 37050010/20/30. Todas tienen nodo en `c0001.nmb`, `.nsa` y pistas de cancelación.
- Tipos de movimiento usados: 1, 2, 7 y 13, que ya están entre los que exporta el mod. No hay tipos nuevos.
- Postura a dos manos: 254310 (WTP 150 y 290) y 214310 (Silverblack). Ambas ya se exportan, por la lanza/bastón y las
  hachas. Clips de powerstance: 254610 y 214610, ya exportados. `dualWielding` gana solas las columnas 130 y 140 (las
  categorías de powerstance de WTP 150 y 290).
- `cast_origins.py`: termina bien. Bajó del juego los modelos `WP_2100_M`, `WP_30x0_M` y `WP_3210_M` a
  `D:\Reversing\DS2\extracted\models\` y sacó `hitDummies` 100/101 de cada uno, sin avisos de dummies faltantes. Los
  golpes son cápsulas en el modelo del arma (`hitVolume.source = weapon`): dummy 101, radio 0.3, largo 0.55–0.7.
- `ds2_anims.py`: termina bien ("written 598", 696 archivos frente a los 673 actuales: los 23 nuevos).

**No hay bloqueos en los exportadores.** Para hacerlo de verdad, en el código del mod (no se tocó):
1. Agregar los ids elegidos a `WEAPONS` en `tools/re/weapons.py` y regenerar los pasos `weapons` y las animaciones
   (`tools/ds2_build.py`).
2. `SoulsWeapons`: `assign(Items.WOODEN_HOE … NETHERITE_HOE, …)` y `assign(ItemTags.HOES, …)`.
3. Efectos en el juego que el usuario tiene que conocer (no son de DS2):
   - El clic derecho con un arma es el ataque fuerte (`AttackClient.onUseKey`). `isInteractive` no incluye la tierra,
     así que **la azada dejaría de arar** con el clic derecho en Souls Mode, igual que hoy el hacha no descorteza. Es
     una función de Minecraft: decide el usuario.
   - Peso: `SoulsEquipLoad` hoy calcula la azada por receta (rama `"hoe"`, 2 piezas + 2 palos). Con un arma asignada,
     pasaría a usar el peso de DS2 ajustado por tier.
   - `SoulsInvader` tiene listas fijas de espadas y hachas. Las guadañas no entrarían salvo que se agreguen.

## Sin confirmar
- **Nombre de la categoría en el menú.** No se encontró el texto ("Reaper" / "Guadaña") en los `.fmg` extraídos
  (`menu_text`, `out\hollow\text`). La agrupación es solo por WTP 150 y por el nombre `Scythe` en los `.xmd`.
- **Silverblack Sickle**: no se confirmó cómo la clasifica el juego (hoz / hacha). Los datos la ponen junto a las hachas.
- **`FUN_14003c250`** obtiene la fila con thunks ofuscados (`thunk_FUN_141c39505` / `141c36ad4` con `+8` del objeto).
  Que sea `WRP` (`WP+0x08`) se deduce de los datos: `WRP+0xf0` contiene ids de RCP cuyas filas tienen titanita, y
  la rama `FUN_14003ba30` (`+0x68/+0x6c/+0x70`) parece ser la de armaduras. No se desofuscó.
- **Scythe of Want** tiene `WRP+0xe8 = 0` (`CustomAttrSpecParam`). Por el nombre podría significar "no se puede
  infundir", pero no se verificó en código.
- Las filas 784000, 821000, 851000, 910000 y 6410000 (WTP 150, sin nombre) probablemente son armas de NPC. No se revisó
  quién las usa.
- La "rareza" de un arma no está en los params. Solo se cita el camino de mejora (normal / centelleante / hueso).
