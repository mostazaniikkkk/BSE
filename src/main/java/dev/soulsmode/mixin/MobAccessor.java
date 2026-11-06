package dev.soulsmode.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public interface MobAccessor {
	@Invoker("getEquipmentDropChance")
	float soulsmode$getEquipmentDropChance(EquipmentSlot slot);

	@Accessor("goalSelector")
	GoalSelector soulsmode$goalSelector();
}
