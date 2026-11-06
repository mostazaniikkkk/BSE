package dev.soulsmode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.soulsmode.ds2.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent Souls Mode progress of a player. Souls held are the vanilla experience points
 * (see {@link ExperienceSouls}); only what vanilla has no slot for lives here.
 *
 * @param classIndex starting class index (FUN_140203e80), 0 = none chosen yet
 * @param attributes the 11 attribute slots
 * @param soulMemory total souls gained (obj+0xf4)
 */
public record SoulsData(int classIndex, List<Integer> attributes, int soulMemory) {
	public static final Codec<SoulsData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("class").forGetter(SoulsData::classIndex),
			Codec.INT.listOf().fieldOf("attributes").forGetter(SoulsData::attributes),
			Codec.INT.fieldOf("soul_memory").forGetter(SoulsData::soulMemory)
	).apply(i, SoulsData::new));

	public static SoulsData of(int classIndex, Attributes attributes, int soulMemory) {
		List<Integer> list = new ArrayList<>(Attributes.SLOT_COUNT);
		for (short value : attributes.toArray()) {
			list.add((int) value);
		}
		return new SoulsData(classIndex, List.copyOf(list), soulMemory);
	}

	public Attributes toAttributes() {
		short[] slots = new short[Attributes.SLOT_COUNT];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = attributes.get(i).shortValue();
		}
		return new Attributes(slots);
	}

	public SoulsData withAttributes(Attributes newAttributes) {
		return of(classIndex, newAttributes, soulMemory);
	}

	public SoulsData withSoulMemory(int newSoulMemory) {
		return new SoulsData(classIndex, attributes, newSoulMemory);
	}
}
