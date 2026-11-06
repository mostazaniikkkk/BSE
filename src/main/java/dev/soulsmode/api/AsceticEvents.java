package dev.soulsmode.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Public API: the Bonfire Ascetic's reset (docs/research/llama_adversidad.md) asks before putting each chunk of its
 * area back the way the world generated it. A protection mod (claims, protected regions) answers false to keep a chunk
 * as it is: its blocks, block entities and entities are left untouched.
 * <pre>{@code
 * AsceticEvents.ALLOW_CHUNK_RESET.register((level, chunk) -> !MyClaims.isClaimed(level, chunk));
 * }</pre>
 * Called on the server thread when the burn starts, once per chunk of the area; every listener must allow it.
 */
public final class AsceticEvents {
	private AsceticEvents() {
	}

	@FunctionalInterface
	public interface AllowChunkReset {
		/** False keeps {@code chunk} of {@code level} out of the reset. */
		boolean allow(ServerLevel level, ChunkPos chunk);
	}

	public static final Event<AllowChunkReset> ALLOW_CHUNK_RESET = EventFactory.createArrayBacked(AllowChunkReset.class,
			listeners -> (level, chunk) -> {
				for (AllowChunkReset listener : listeners) {
					if (!listener.allow(level, chunk)) {
						return false;
					}
				}
				return true;
			});
}
