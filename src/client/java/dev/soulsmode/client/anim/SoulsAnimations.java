package dev.soulsmode.client.anim;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.ModifierLayer;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Player animation layer for Souls Mode actions. Animations are Bedrock/GeckoLib JSON files in
 * {@code assets/soulsmode/player_animations/}; besides rotation/position/scale, every bone has a
 * {@code bend} channel: {@code torso} bends at the waist, arms at the elbow, legs at the knee.
 */
public final class SoulsAnimations {
	public static final ResourceLocation LAYER = SoulsMode.id("actions");
	/** Same layer seen in a mirror: left-hand attacks reuse the right-hand animations (c0001 has no left-hand attacks). */
	public static final ResourceLocation MIRRORED_LAYER = SoulsMode.id("actions_mirrored");
	/** PAL convention: 1500+ is for gameplay animations, above emotes (1000) and idle poses (0-10). */
	public static final int PRIORITY = 1500;

	private SoulsAnimations() {
	}

	public static void init() {
		// Only triggered animations play on this layer, so the state handler always stops.
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, SoulsAnimations::controller);
		// The mirror goes on the controller's own modifier list: wrapping it in a ModifierLayer stops PAL from ticking it.
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(MIRRORED_LAYER, PRIORITY,
				player -> {
					PlayerAnimationController controller = controller(player);
					controller.addModifierLast(new MirrorModifier());
					return controller;
				});
	}

	/** Each action controller's speed modifier (1 unless a dodge sets it; every other play resets it). */
	private static final java.util.Map<PlayerAnimationController, SpeedModifier> SPEEDS = new java.util.WeakHashMap<>();

	private static PlayerAnimationController controller(AbstractClientPlayer player) {
		PlayerAnimationController controller = new PlayerAnimationController(player,
				(c, state, animSetter) -> PlayState.STOP);
		SpeedModifier speed = new SpeedModifier(1);
		controller.addModifierLast(speed);
		SPEEDS.put(controller, speed);
		return controller;
	}

	public static boolean play(AbstractClientPlayer player, ResourceLocation animation) {
		return play(player, animation, false);
	}

	/** Like {@link #play(AbstractClientPlayer, ResourceLocation, boolean)} at a playback speed (Tired dodges). */
	public static boolean play(AbstractClientPlayer player, ResourceLocation animation, boolean mirrored, float speed) {
		boolean played = play(player, animation, mirrored);
		SpeedModifier modifier = SPEEDS.get(controller(player, mirrored));
		if (modifier != null) {
			modifier.speed = speed;
		}
		return played;
	}

	/** Changes the playback speed of what the normal or the mirrored layer is playing (SwingClient). */
	public static void speed(AbstractClientPlayer player, boolean mirrored, float speed) {
		PlayerAnimationController controller = controller(player, mirrored);
		SpeedModifier modifier = controller == null ? null : SPEEDS.get(controller);
		if (modifier != null) {
			modifier.speed = speed;
		}
	}

	/** Plays on the normal or the mirrored layer, stopping whatever the other one was playing. */
	public static boolean play(AbstractClientPlayer player, ResourceLocation animation, boolean mirrored) {
		PlayerAnimationController other = controller(player, !mirrored);
		if (other != null) {
			other.stop();
		}
		PlayerAnimationController controller = controller(player, mirrored);
		SpeedModifier speed = SPEEDS.get(controller);
		if (speed != null) {
			speed.speed = 1;
		}
		return controller != null && controller.triggerAnimation(animation);
	}

	/** Like {@link #play(AbstractClientPlayer, ResourceLocation, boolean)}, but holds the last frame until stopped. */
	public static boolean hold(AbstractClientPlayer player, ResourceLocation animation, boolean mirrored) {
		PlayerAnimationController other = controller(player, !mirrored);
		if (other != null) {
			other.stop();
		}
		PlayerAnimationController controller = controller(player, mirrored);
		if (controller == null) {
			return false;
		}
		// PAL's hold-on-last-frame pauses the controller and the pose is not kept reliably: play the raise, then loop
		// the last frame (exported as <animation>_hold by tools/re/ds2_anims.py).
		ResourceLocation hold = ResourceLocation.fromNamespaceAndPath(animation.getNamespace(), animation.getPath() + "_hold");
		controller.triggerAnimation(PlayerRawAnimationBuilder.begin().thenPlay(animation).thenLoop(hold).build());
		return true;
	}

	/** Plays {@code first} once, then loops {@code loop} until stopped or replaced. */
	public static boolean playThenLoop(AbstractClientPlayer player, ResourceLocation first, ResourceLocation loop) {
		PlayerAnimationController other = controller(player, true);
		if (other != null) {
			other.stop();
		}
		PlayerAnimationController controller = controller(player, false);
		if (controller == null) {
			return false;
		}
		controller.triggerAnimation(PlayerRawAnimationBuilder.begin().thenPlay(first).thenLoop(loop).build());
		return true;
	}

	/** Whether an action (attack, dodge, item use...) is playing on the player. */
	public static boolean isPlaying(AbstractClientPlayer player) {
		for (boolean mirrored : new boolean[] {false, true}) {
			PlayerAnimationController controller = controller(player, mirrored);
			if (controller != null && controller.isActive()) {
				return true;
			}
		}
		return false;
	}

	public static void stop(AbstractClientPlayer player) {
		for (boolean mirrored : new boolean[] {false, true}) {
			PlayerAnimationController controller = controller(player, mirrored);
			if (controller != null) {
				controller.stop();
			}
		}
	}

	private static PlayerAnimationController controller(AbstractClientPlayer player, boolean mirrored) {
		IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, mirrored ? MIRRORED_LAYER : LAYER);
		if (layer instanceof ModifierLayer<?> modifiers) {
			layer = modifiers.getAnimation();
		}
		return layer instanceof PlayerAnimationController controller ? controller : null;
	}
}
