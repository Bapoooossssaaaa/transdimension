package dev.goober.transdimension.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import dev.goober.transdimension.TransDimension;

/**
 * "Blessed": what bathing in holy water (or an angel's touch) gives you. It mends your hearts quickly, like strong
 * regeneration, and wraps you in golden absorption hearts, two more for each level, like the Absorption effect does
 * (it raises your maximum absorption and fills it when it starts).
 */
public class BlessedEffect extends MobEffect {
	public BlessedEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xFFD966);
		this.addAttributeModifier(Attributes.MAX_ABSORPTION, TransDimension.id("effect.blessed"), 4.0, AttributeModifier.Operation.ADD_VALUE);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		// Half a heart every second, faster at higher levels.
		int interval = Math.max(1, 20 >> amplifier);
		return duration % interval == 0;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		if (entity.getHealth() < entity.getMaxHealth()) {
			entity.heal(1.0F);
		}
		return true;
	}

	@Override
	public void onEffectStarted(LivingEntity entity, int amplifier) {
		super.onEffectStarted(entity, amplifier);
		entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), 4.0F * (amplifier + 1)));
	}
}
