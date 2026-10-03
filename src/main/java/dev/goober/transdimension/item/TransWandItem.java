package dev.goober.transdimension.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.goober.transdimension.entity.TransMagicBolt;

/**
 * Maddie's Trans Wand: shoots a sparkling heart of trans magic that flies straight and hurts what it hits.
 * It never runs out; its use cooldown (set on the item) keeps it from being a machine gun.
 */
public class TransWandItem extends Item {
	public TransWandItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				1.0F, 1.4F + level.getRandom().nextFloat() * 0.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS,
				0.5F, 1.8F);
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(TransMagicBolt::new, serverLevel, stack, player, 0.0F, 1.7F, 0.2F);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}
