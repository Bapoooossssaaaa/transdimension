package dev.goober.transdimension.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.network.FairyRescuePayload;
import dev.goober.transdimension.registry.ModItems;

/**
 * The Bottled Fairy's rescue. Held in either hand, it saves you from death once, like a totem of undying, but in its own
 * way: the fairy bursts out of the bottle (you keep the empty bottle) in a shower of blue, pink and white sparkles with
 * an amethyst chime, heals you, and lets you float down gently. The Bottled Fairy pops up on your screen the way a totem
 * does ({@link FairyRescuePayload}).
 *
 * <p>It runs on Fabric's {@code ALLOW_DEATH}, which fires just before vanilla looks for a totem, so the item has no
 * death protection component (that would bring the totem's green sparkles and sound). Like a totem it can't help against
 * damage that bypasses invulnerability (the void, /kill), and the hands are checked in vanilla's order, so a totem held
 * in an earlier hand is used first.
 */
public final class BottledFairy {
	/** Blue, pink and white, the flag's colours. */
	private static final int[] SPARKLES = {0x5BCEFA, 0xF5A9B8, 0xFFFFFF};

	private BottledFairy() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !rescue(entity, source));
	}

	/** Uses a held Bottled Fairy to save {@code entity}; returns whether it did. */
	private static boolean rescue(LivingEntity entity, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = entity.getItemInHand(hand);
			if (held.has(DataComponents.DEATH_PROTECTION)) {
				return false;
			}
			if (held.is(ModItems.BOTTLED_FAIRY)) {
				held.shrink(1);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (held.isEmpty()) {
					entity.setItemInHand(hand, bottle);
				} else if (!(entity instanceof Player player && player.getInventory().add(bottle))) {
					entity.spawnAtLocation(level, bottle);
				}
				revive(entity, level);
				return true;
			}
		}
		return false;
	}

	private static void revive(LivingEntity entity, ServerLevel level) {
		entity.setHealth(1.0F);
		entity.removeAllEffects();
		entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 45, 1));
		entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 5, 1));
		entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 40, 0));
		entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 10, 0));

		double x = entity.getX();
		double y = entity.getY() + entity.getBbHeight() * 0.5;
		double z = entity.getZ();
		for (int colour : SPARKLES) {
			level.sendParticles(new DustParticleOptions(colour, 1.5F), x, y, z, 40, 0.6, 0.9, 0.6, 0.1);
		}
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 30, 0.4, 0.8, 0.4, 0.15);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, entity.getSoundSource(), 1.5F, 1.4F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, entity.getSoundSource(), 2.0F, 1.0F);
		if (entity instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, FairyRescuePayload.INSTANCE);
		}
	}
}
