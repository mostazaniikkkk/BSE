package dev.soulsmode.mixin;

import dev.soulsmode.worldgen.GroundItemPlacer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ground pickups inside structures (GroundItemPlacer.structure), once the structure is placed in the chunk. */
@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
	@Inject(method = "placeInChunk", at = @At("TAIL"))
	private void soulsmode$groundItem(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunk, CallbackInfo ci) {
		StructureStart self = (StructureStart) (Object) this;
		if (self.isValid()) {
			// Its own random (from the seed, the chunk and the structure's box): vanilla's structure random stays untouched.
			RandomSource own = RandomSource.create(level.getSeed() ^ chunk.toLong() * 341873128712L
					^ self.getBoundingBox().hashCode() * 132897987541L);
			GroundItemPlacer.structure(level, own, chunkBox, self.getPieces());
		}
	}
}
