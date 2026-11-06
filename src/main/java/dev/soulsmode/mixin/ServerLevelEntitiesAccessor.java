package dev.soulsmode.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The temporary level of a chunk regeneration (ChunkRegenerator) closes its entity storage without a full save. */
@Mixin(ServerLevel.class)
public interface ServerLevelEntitiesAccessor {
	@Accessor("entityManager")
	PersistentEntitySectionManager<Entity> soulsmode$entityManager();
}
