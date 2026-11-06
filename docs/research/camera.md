# Cámara de juego

## Qué cámara es
- La cámara normal en juego es **`ExFollowCameraOperator`** (vtable `0x1410f4a98`), con **`CameraExFollowParam`**:
  76 filas de 0x134 bytes. La fila 0 es la base y las demás, por id de zona, sobrescriben los campos distintos de 0
  (`FUN_14049b1f0`).
- `FUN_14049ac60` copia la fila (comprueba que `+0x00 == 7`) a la cámara. `FUN_14049ab60` deja `cam+0x560` apuntando
  a la copia activa; el resto del código lee los campos a través de ese puntero.
- `PlayerCameraOperator` tiene constantes fijas en su constructor (`FUN_140496e10`) y lee el pad en crudo: parece la
  cámara libre de depuración, no la de juego. `NormalCameraOperator` usa `CameraNormalParam` (cámara genérica).

## Campos confirmados (fila 0)
| Offset | Valor | Uso en el código | Significado |
|---|---|---|---|
| `+0x04` | 0.768 rad (44°) | Escalado junto con `+0x18` por el efecto de impacto (`FUN_14049ba20`). En las otras cámaras este campo va con near 0.1 y far 5000. | **Campo de visión vertical** |
| `+0x0c/+0x10/+0x14` | (0, 1.42, 0) | `FUN_14049d0e0` → `cam+0x710` (vector, w = 1) | **Punto de pivote sobre el personaje: 1.42 m de altura** |
| `+0x18` | 3.6 | `FUN_14049d0e0` → `cam+0x724`; el impacto lo escala igual que el FOV | **Distancia de la cámara** (probable; la usa como longitud del brazo) |
| `+0x1c/+0x20/+0x24` | 0.3 / 0.1 / 0.99 | `FUN_14049c8b0`: distancia normalizada por `+0x1c` y acotada a `[+0x20, +0x24]` | Acercamiento por colisión (radio y límites) |
| `+0x78` / `+0x7c` | −0.698 / 1.222 rad (**−40° / +70°**) | `FUN_14049df40`: el ángulo vertical (`cam+0x6cc`) se envuelve a (−π, π] y se acota a este rango | **Límites de inclinación** |
| `+0x80` / `+0x84` | ±40° | Mezclados con los anteriores según `+0x88..+0x8c` | Límites alternativos (sin identificar el caso) |
| `+0x90→+0x94` | 80 → 240 °/s | `cam+0x734 = lerp(+0x90, +0x94, deflexión²)`, aplicado al eje que se acota con `+0x78/+0x7c` | **Velocidad vertical** según cuánto se empuja el stick |
| `+0x98→+0x9c` | 80 → 240 °/s | `cam+0x738` | **Velocidad horizontal** |
| `+0xa0..+0xac` | 180 → 500 °/s | `cam+0x73c` / `+0x740`: la horizontal sube hacia este valor según `cam+0x6c8` | Aceleración del giro horizontal |
| `+0xb8`, `+0x118` | 0.1, 0.8 s | Rampa de `cam+0x77c` hasta `+0xb8` en `+0x118` segundos | Sin identificar |

## Sin resolver
- **Signo del ángulo vertical**: si +70° es la cámara por encima (mirando hacia abajo) o por debajo.
- **Suavizado del pivote** al seguir al personaje (`FUN_1404a0340`, `+0x2c` / `+0x3c` / `+0x110`) y la rama
  con `+0x11c ≠ 0` (la fila 0 tiene `+0x11c = 1`), que suaviza el giro con `+0xd8/+0xdc/+0xe0`.
- **El giro del personaje hacia la dirección de movimiento** no está en la cámara, sino en el control del personaje
  (`ChrMoveParam`); falta analizarlo.

## Para Minecraft
- **Ratón**: las velocidades de stick no se trasladan tal cual, porque el ratón fija el ángulo directamente.
  Se aplican el pivote (1.42), la distancia (3.6), los límites (−40°/+70°) y la colisión.
- **FOV de 44°** vertical frente a los 70° por defecto de Minecraft: es una opción de diseño.

## Implementado (`client/camera/SoulsCamera` + mixins `Camera`, `Entity.turn`, `LocalPlayer.aiStep`, `GameRenderer.getFov`)
- **Activación**: en Modo Souls con la cámara en tercera persona trasera (F5). Interruptor `SoulsCamera.ENABLED`.
- **Ratón**: gira solo la cámara (yaw libre, pitch acotado a −40°..+70°). +70 = mirar desde arriba (signo provisional).
- **Cámara**: pivote a 1.42 m sobre los pies, brazo de **4.5 m** con la colisión vanilla (`getMaxZoom`).
  DS2 usa 3.6 m (`DS2_DISTANCE`); la cámara se alejó a pedido del usuario (2026-10-30).
- **FOV**: 0.768 rad (44°); se desactiva con `USE_DS2_FOV = false`.
- **Movimiento**: las teclas son relativas a la cámara; el cuerpo gira hacia esa dirección y avanza en ella.
  La velocidad de giro es la de DS2 (`Movement.turnRate`, ver movement.md).
- **Esquiva**: usa la dirección relativa a la cámara.
