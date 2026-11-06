# API de BSE para otros mods

Para mods de Fabric que quieran convivir con BSE. Todo está en el paquete `dev.soulsmode.api`. Lo que se puede hacer
sin escribir Java (armas, armaduras, consumibles, hechizos, enemigos, etiquetas) está en
[datapacks.md](datapacks.md).

## Llama de la adversidad: proteger chunks del reinicio

Al quemar una Llama de la adversidad en una hoguera, BSE devuelve los chunks de alrededor (17 × 17, radio de 8 chunks
alrededor del chunk de la hoguera) a como los generó el mundo. Eso borra lo que los jugadores construyeron ahí. Los
servidores con terrenos protegidos (claims, regiones) pueden excluir chunks con el evento
`dev.soulsmode.api.AsceticEvents.ALLOW_CHUNK_RESET`.

```java
import dev.soulsmode.api.AsceticEvents;

AsceticEvents.ALLOW_CHUNK_RESET.register((level, chunk) -> !MisClaims.estaReclamado(level, chunk));
```

- **Cuándo se llama:** una vez por cada chunk del área, en el hilo del servidor, en el momento en que el jugador quema
  la llama (antes de empezar a generar).
- **Firma:** `boolean allow(ServerLevel level, ChunkPos chunk)`. Devolver `false` deja ese chunk fuera.
- **Varios oyentes:** basta que uno devuelva `false` para que el chunk quede fuera.
- **Qué se respeta en un chunk excluido:** sus bloques, cofres y demás bloques con datos, y sus entidades (no se borran
  ni se agregan). Tampoco recibe las vetas de mineral extra del ciclo.
- **Qué no cambia:** el resto del área se reinicia igual, y la zona sube de ciclo igual (más dificultad, vindicadores,
  evocadores, etc. también en los chunks protegidos). Los bloques del límite pueden quedar con un corte entre lo
  protegido y lo regenerado.
- **Una a la vez:** el servidor regenera un área por vez. El log de cada reinicio dice cuántos chunks quedaron
  protegidos: `Bonfire Ascetic: regenerated 17x17 chunks ... N protected chunks`.

Detalles de la mecánica (ciclos, efectos por ciclo, tiempos): [research/llama_adversidad.md](research/llama_adversidad.md).

## Armas para ítems de otros mods

`dev.soulsmode.api.SoulsWeapons` asigna con qué arma de DS2 (o de datapack) ataca un ítem en el modo Souls,
por ejemplo un arma de otro mod. La documentación está en el propio archivo; para hacerlo sin Java, usa la clave
`"items"` de un arma de datapack ([datapacks.md](datapacks.md), "Armas").
