# Ataque comprometido (espada recta a una mano)

> **Superado por `weapons.md`:** el moveset sale de los params de cada arma, no del grupo de animación. Lo que sigue
> vale para los tiempos (TAE 2200, ventanas de `c0001.nmb`); las secciones "Todas las clases de arma" y "Mano
> izquierda" sobre movesets por clase ya no aplican.

## Animaciones (`c0001.anibnd`, grupo `a29` = StraightSword)
Nombres completos sacados de la tabla `.xmd` de `c0001.nmb`:

| Id TAE | Origen |
|---|---|
| 29010010 / 0011 / 0012 | `StraightSwordAttack1HandLight1_R_Hit` / `_Hit_01` / `_Hit_02` (cadena de ataques ligeros) |
| 29010030 / 0031 / 0032 / 0033 | `StraightSwordAttack1HandHeavy1_R_Hit` / `_Hit_01` / `_Hit_02` / `_Hit_03` (cadena de ataques fuertes) |
| 29010020..0022 | `Attack1HandLight2_R_*` (sin usar todavía) |
| 29010038 | `Attack1HandSpecial2_R_Hit` |

## Ventana de golpe: evento TAE 2200
`ChrMorphemeTimeActTrackDamageCtrl` (`FUN_1403269e0`) trata los tipos 2200–2204:
- **2200** (`0x898`): al empezar toma la ranura de ataque `parámetro[0]` (0..9) de `chr+0x230`. Las ranuras miden 0xc0
  bytes y empiezan en `+0xf08` (`FUN_1403741c0`). La registra como ataque activo (`FUN_140373c50` → `FUN_140373a40`)
  y **cobra la estamina** con `FUN_1403335d0(..., coste, 4)`, que resta de `chr+0x1ac`. Al terminar el evento
  (`+0x29`) libera el ataque (`FUN_140374790`).
- **2300** (`0x8fc`, `FUN_140325880`): mismo patrón con otra tabla (`+0x760`, 0xc4 bytes, `FUN_1403740e0`), sin
  estamina, y además activa el bit 0 de `chr.b8+0x4c0`. Su papel no está identificado.

Se toma 2200 como ventana de daño:

| Ataque | Duración (`.nsa +0x28`) | 2200 |
|---|---|---|
| Light 0010 / 0011 / 0012 | 2.0 s | 0.467–0.633 / 0.467–0.600 / 0.467–0.633 |
| Heavy 0030 / 0031 | 2.333 s | 0.633–0.867 / 0.633–0.800 |
| Heavy 0032 / 0033 | 2.0 / 2.167 s | 0.367–0.633 / 0.667–0.867 |

El evento 101100 (bloqueo de regen de estamina) va de 0 a 1.5 s en los ligeros y de 0 a 1.833 s en los fuertes.

## Ventanas Morpheme (pistas de `c0001.nmb`, en segundos)
| Ataque | Avoid (bloqueado) | Move (bloqueado) | Attack1L / 1H (bloqueado hasta) | AttackComboControl |
|---|---|---|---|---|
| 0010 | 0.267–1.2 | 0–1.233 | 0.9 / 0.967 | 0–1.206 |
| 0011 | 0.267–1.1 | 0–1.167 | 0.767 / 0.9 | 0–1.206 |
| 0012 | 0.267–1.1 | 0–1.167 | 0.667 / 0.9 | 0–1.206 |
| 0030 | 0.267–1.4 | 0–1.467 | 1.0 / 1.133 | 0–1.5 |
| 0031 | 0.267–1.267 | 0–1.367 | 0.9 / 1.133 | 0–1.5 |
| 0032 | 0.229–1.333 | 0–1.4 | 0.771 / 0.971 | 0–1.286 |
| 0033 | 0.229–1.467 | 0–1.5 | 0.836 / 1.167 | 0–1.393 |

- `Attack1L` / `Attack1H` = ataque ligero / fuerte a una mano: coincide con los nombres `Attack1HandLight` / `Heavy`.
- Roll: `Attack1L` 0–0.5 s, `Attack1H` 0–0.9 s. Paso atrás: `AttackAll` 0–0.333 s.
- Otras pistas sin interpretar: `InputControl` (0.382–1.765 en 0010), `AttackStateControl` (fases 106/100/101/102),
  `AttackSpeedControl`, `AttackStagger`, `AtkAdjustControl`, `StateControl`.

## Implementado (`ds2/Attack`, `SoulsAttack`, `client/AttackClient`, mixin `Minecraft.startAttack`)
- En Modo Souls, el clic de ataque pide un ataque **ligero**; la tecla `R` (configurable) pide uno **fuerte**.
  Si se apunta a un bloque que el jugador puede romper, el clic sigue siendo vanilla.
- **Servidor**:
  - El ataque dura lo que la animación.
  - El daño solo entra durante el evento 2200, una vez por objetivo y golpe.
  - El siguiente ataque espera a `CancelControl_Attack1L/1H`. Si llega antes del fin de `AttackComboControl` y es
    del mismo peso, sigue la cadena; si no, empieza la cadena desde el principio.
  - La esquiva se rechaza dentro de `CancelControl_Avoid`; fuera de esa ventana corta el ataque.
  - El ataque se rechaza dentro de las ventanas de ataque de la esquiva y, si entra, la corta.
- **Sin búfer de entrada** (2026-11-01): una pulsación durante el bloqueo de cancelación se descarta. El búfer de DS2
  (pista `InputControl` del nmb) no está descifrado; el que había era inventado y se quitó.
- **Cliente**:
  - Sin movimiento, salto ni giro durante `CancelControl_Move`.
  - El ataque sale hacia la dirección que se esté pulsando (relativa a la cámara).
  - Animación `attack_<ligero|fuerte>_N` con la duración de DS2. El barrido coincide con la ventana 2200; las poses son nuestras.
  - Las animaciones se envían a los jugadores cercanos.
- **No viene de DS2 (provisional)**:
  - **Área de golpe**: alcance vanilla y ±60° delante. La geometría está en los datos de la ranura de ataque, sin descifrar.
  - **Daño**: el vanilla del arma (atributo, encantamientos, durabilidad). Las fórmulas de DS2 no están portadas.
  - **Fin de cadena**: tras el último golpe vuelve al primero.
  - **Estamina**: el coste de 2200 se cobra desde 2026-10-29 (ver [stamina.md](stamina.md), "Costes de ataque y guardia").

## Todas las clases de arma (`data/soulsmode/ds2/movesets.json`, generado con `tools/re/movesets.py`)
- **Cadenas**: la ligera son los ids `GG01_0010..` contiguos con evento 2200; la fuerte, los `GG01_0030..`.
  Cada ataque toma su duración del `.nsa` y sus ventanas del nodo de `c0001.nmb` cuya pista `TimeAct` lleva ese id de TAE.
- Si varios 2200 se solapan (por ejemplo el fuerte de lanza, `a25_01_0030`), se fusionan en una ventana:
  cada objetivo recibe un golpe por ventana.
- **Incluidas** (ligeros / fuertes): fist 1/1, dagger 2/2, rapier 1/4, whip 1/1, twinblade 1/1, spear 2/1, lance 1/2,
  curved_sword 1/3, katana 1/2, straight_sword 3/4, scythe 1/1, ultra_greatsword 2/1, hammer 1/1, axe 2/2, great_axe 2/2.
- **Fuera**: greatsword, great_hammer y halberd. Algunos de sus ids (por ejemplo `31010030`, `34010010`, `37010010`)
  no aparecen en ninguna pista `TimeAct` de `c0001.nmb`, así que no hay ventanas que leer.
- **Vanilla**: espadas → `straight_sword`, hachas → `axe`, tridente → `spear`. Todo lo demás pega con `fist`.
  El resto queda disponible por API (`dev.soulsmode.api.SoulsWeapons`).
- **Animaciones**: `attack_<clase>_<light|heavy>_<n>.json`, generadas con `tools/re/gen_attack_animations.py`.
  La duración y el barrido son de DS2; las poses son nuestras.

## Mano izquierda
- `c0001` no tiene animaciones de ataque `_L` (solo guardias de escudo `_L_`) ni ids de TAE `GG02xxxx`.
  El exe sí incluye el nodo espejo de Morpheme (`NodeMirrorTransforms` / `nodeMirrorQueueTransformBuff`).
  Por eso el ataque con la mano izquierda reproduce la animación derecha **en espejo**, con los mismos tiempos.
  Que DS2 lo haga así es lo que indican esos datos; no está confirmado en el código.
- **Bloqueos**: `CancelControl_Attack2L` / `2H` son los de la mano izquierda (en el roll valen lo mismo que `1L` / `1H`).
  Un ataque con la otra mano no sigue el combo: empieza su propia cadena.
- **Controles** (configurables): `Z` ataque ligero y `X` ataque fuerte con la mano izquierda.
  Clic derecho con un arma = ataque fuerte (`R` sigue disponible).
  Un objeto sin moveset en la mano izquierda (escudo, antorcha…) no ataca; la mano vacía da un puñetazo.
- **Daño**: el atributo de daño trae el bonus del arma principal. Para la mano izquierda se cambia por el bonus del arma
  de esa mano; los encantamientos y la durabilidad también son los de esa arma.
- **Clic derecho sobre bloques** con menú, puertas, trampillas, vallas, botones, palancas, camas y campanas:
  hace la acción vanilla (decisión de diseño).
- **Construir y picar** (pedido del usuario, decisión de diseño): el Modo Souls usa supervivencia como base.
  Antes era aventura; los jugadores que ya estaban en el modo pasan a supervivencia al entrar al mundo.
  - **Apuntar**: con la cámara Souls, el bloque o la entidad apuntada es la que está en el centro de la pantalla,
    con un rayo desde la cámara. El alcance vanilla se sigue midiendo desde los ojos. El contorno del bloque muestra
    a qué se apunta.
  - **Clic izquierdo**: pica el bloque apuntado si no tienes arma en la mano (mano vacía, pala, azada, bloques…)
    o si el arma es la herramienta correcta para ese bloque (pico en piedra, hacha en madera). Si no, es el ataque
    ligero, y mantenerlo apretado tampoco cava: una espada contra el suelo ataca.
  - **Clic derecho**: con un bloque en la mano apuntando a otro, lo coloca (ningún cofre ni hoguera cercana se
    lleva el clic). Con un arma, es el ataque fuerte.
- **Bloques con menú cercanos** (decisión de diseño, igual que la hoguera): la cámara Souls no apunta a bloques bajos.
  Por eso la tecla de usar abre el bloque con menú más cercano (cofre, cofre de ender, mesa de crafteo, horno,
  yunque…). Tiene que estar a 2,5 bloques o menos y a la vista: un rayo desde los ojos tiene que llegar a él.
  Si hay una fogata encendida más cerca, se descansa en ella. Agachado no se abre, así que se pueden poner bloques al lado.
  Puertas, palancas, etc. siguen pidiendo apuntarlas.
- **Comer** (decisión de diseño): con comida en la mano principal, el clic derecho es el uso vanilla.
  Con un arma en la mano principal y comida en la izquierda, también come, siempre que el jugador pueda comer
  (con hambre o comida que siempre se puede comer). Si no, hace el ataque fuerte, que también sigue en `R`.
