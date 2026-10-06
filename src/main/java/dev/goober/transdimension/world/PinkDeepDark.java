package dev.goober.transdimension.world;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.PinkSculkCatalystBlock;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModParticles;

/**
 * The pink deep dark: the Trans Realm's deep dark, all in pink, under its mountains (biome
 * {@code transdimension:pink_deep_dark}, made by tools/generate_worldgen.py; its cities by tools/generate_ancient_city.py).
 *
 * <ul>
 * <li>A pink sculk catalyst within {@link #CATALYST_REACH} blocks of a death blooms, and pink sculk spreads where the
 * creature fell ({@link PinkSculkPatchFeature#spread}).
 * <li>Darkness never lasts in the Trans Realm: it turns into night vision (wardens' and shriekers' darkness alike).
 * <li>Wild fairies like it down here: they spawn far more often in it (see {@code Fairy#checkFairySpawnRules}).
 * </ul>
 */
public final class PinkDeepDark {
	public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, TransDimension.id("pink_deep_dark"));
	private static final int CATALYST_REACH = 8;

	private PinkDeepDark() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Player || !(entity.level() instanceof ServerLevel level) || !level.dimension().equals(TransDimension.TRANS_REALM)) {
				return;
			}
			BlockPos catalyst = nearestCatalyst(level, entity.blockPosition());
			if (catalyst != null) {
				PinkSculkCatalystBlock.bloom(level, catalyst, level.getBlockState(catalyst));
				level.sendParticles(ModParticles.PINK_SCULK_SOUL, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 6,
						0.3, 0.3, 0.3, 0.02);
				PinkSculkPatchFeature.spread(level, entity.blockPosition(), level.getRandom(), 6 + level.getRandom().nextInt(8), false);
			}
		});

		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 2 != 0 || !level.dimension().equals(TransDimension.TRANS_REALM)) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				MobEffectInstance darkness = player.getEffect(MobEffects.DARKNESS);
				if (darkness != null) {
					player.removeEffect(MobEffects.DARKNESS);
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Math.max(darkness.getDuration(), 200) + 200, 0, true, false));
				}
			}
		});
	}

	public static boolean isPinkDeepDark(LevelAccessor level, BlockPos pos) {
		return level.getBiome(pos).is(BIOME);
	}

	@Nullable
	private static BlockPos nearestCatalyst(ServerLevel level, BlockPos around) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-CATALYST_REACH, -CATALYST_REACH, -CATALYST_REACH),
				around.offset(CATALYST_REACH, CATALYST_REACH, CATALYST_REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(ModBlocks.PINK_SCULK_CATALYST)) {
				double distance = pos.distSqr(around);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		return best;
	}
}
