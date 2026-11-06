package dev.soulsmode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A bonfire the player has rested at, so it can be travelled to (DS2's travel menu, FUN_14017fdb0).
 * {@code pos} is the campfire, {@code stand} where the player stood when resting (where travel puts them).
 */
public record KnownBonfire(ResourceLocation dimension, BlockPos pos, BlockPos stand) {
	public static final Codec<KnownBonfire> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("dimension").forGetter(KnownBonfire::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(KnownBonfire::pos),
			BlockPos.CODEC.fieldOf("stand").forGetter(KnownBonfire::stand)
	).apply(i, KnownBonfire::new));
	public static final StreamCodec<ByteBuf, KnownBonfire> STREAM_CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, KnownBonfire::dimension,
			BlockPos.STREAM_CODEC, KnownBonfire::pos,
			BlockPos.STREAM_CODEC, KnownBonfire::stand,
			KnownBonfire::new);

	public boolean isAt(ResourceLocation otherDimension, BlockPos otherPos) {
		return dimension.equals(otherDimension) && pos.equals(otherPos);
	}
}
