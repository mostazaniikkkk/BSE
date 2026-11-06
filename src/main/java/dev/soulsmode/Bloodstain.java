package dev.soulsmode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * The player's single bloodstain (NetSvrBloodstainManager +0x2c..+0x48 in DS2: flag, souls, location).
 * {@code pos} is where the {@code soulsmode:bloodstain} block was placed.
 */
public record Bloodstain(ResourceLocation dimension, BlockPos pos, int souls) {
	public static final Codec<Bloodstain> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("dimension").forGetter(Bloodstain::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Bloodstain::pos),
			Codec.INT.fieldOf("souls").forGetter(Bloodstain::souls)
	).apply(i, Bloodstain::new));
}
