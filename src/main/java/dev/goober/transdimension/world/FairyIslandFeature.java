package dev.goober.transdimension.world;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * A small floating island of the Fairy Realm: a grassy top with flowers (and on bigger islands a tree or a pond) over a
 * rocky cone that tapers away below, with prism crystals hanging from its underside. None grow within
 * {@link #CLEAR_RADIUS} blocks of the middle of the realm, where the arena island floats.
 */
public class FairyIslandFeature extends Feature<NoneFeatureConfiguration> {
	public static final int CLEAR_RADIUS = 96;
	private static final List<ResourceKey<ConfiguredFeature<?, ?>>> TREES = List.of(tree("pearlwood_tree"), tree("candy_floss_tree_pink"),
			tree("candy_floss_tree_blue"), tree("twilight_tree_small"), tree("trans_cherry_tree"), tree("bluebell_tree"));
	private static final List<ResourceKey<ConfiguredFeature<?, ?>>> BUSHES = List.of(tree("pastel_bush"), tree("blossom_hedge_bush"),
			tree("bluebell_hedge_bush"), tree("pearl_hedge_bush"));

	public FairyIslandFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	private static ResourceKey<ConfiguredFeature<?, ?>> tree(String name) {
		return ResourceKey.create(Registries.CONFIGURED_FEATURE, TransDimension.id(name));
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		if ((long) origin.getX() * origin.getX() + (long) origin.getZ() * origin.getZ() < (long) CLEAR_RADIUS * CLEAR_RADIUS) {
			return false;
		}
		int radius = 4 + random.nextInt(6);
		int depth = radius + 2 + random.nextInt(4);
		int top = origin.getY();
		// A lumpy outline: each of 8 directions gets its own small bulge or dent.
		float[] lumps = new float[8];
		for (int i = 0; i < lumps.length; i++) {
			lumps[i] = random.nextFloat() * 1.6F - 0.8F;
		}
		boolean pond = radius >= 7 && random.nextInt(3) == 0;
		int pondX = random.nextInt(3) - 1;
		int pondZ = random.nextInt(3) - 1;

		BlockState grass = ModBlocks.TRANS_GRASS_BLOCK.defaultBlockState();
		BlockState dirt = ModBlocks.TRANS_DIRT.defaultBlockState();
		BlockState stone = ModBlocks.TRANS_STONE.defaultBlockState();
		BlockState granite = ModBlocks.TRANS_GRANITE.defaultBlockState();
		BlockState deepslate = ModBlocks.TRANS_DEEPSLATE.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				float angle = (float) Mth.atan2(dz, dx);
				int sector = Math.floorMod(Math.round(angle / (Mth.TWO_PI / 8.0F)), 8);
				float distance = Mth.sqrt(dx * dx + dz * dz) - lumps[sector];
				if (distance > radius) {
					continue;
				}
				float f = 1.0F - distance / radius;
				int bottom = top - 1 - (int) (depth * Math.pow(Math.max(f, 0.0F), 0.75));
				int surface = top + (f > 0.55F ? 1 : 0);
				boolean inPond = pond && (dx - pondX) * (dx - pondX) + (dz - pondZ) * (dz - pondZ) <= 5;
				for (int y = bottom; y <= surface; y++) {
					BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
					BlockState state;
					if (y == surface) {
						state = inPond ? water : grass;
					} else if (y >= surface - 2) {
						state = dirt;
					} else if (y <= bottom + 1 && f > 0.5F) {
						state = deepslate;
					} else {
						state = (dx + 2 * y + dz) % 7 == 0 ? granite : stone;
					}
					level.setBlock(pos, state, Block.UPDATE_CLIENTS);
				}
				BlockPos above = new BlockPos(origin.getX() + dx, surface + 1, origin.getZ() + dz);
				if (inPond) {
					if (random.nextInt(4) == 0) {
						level.setBlock(above, ModBlocks.TRANS_LILY_PAD.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				} else {
					this.decorate(level, above, random);
				}
				// Prism crystals hang from the underside here and there.
				if (random.nextInt(9) == 0) {
					level.setBlock(new BlockPos(origin.getX() + dx, bottom - 1, origin.getZ() + dz),
							ModBlocks.TRANS_CRYSTAL_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN), Block.UPDATE_CLIENTS);
				}
			}
		}

		// A tree on bigger islands, a bush on small ones.
		List<ResourceKey<ConfiguredFeature<?, ?>>> plants = radius >= 6 ? TREES : BUSHES;
		ResourceKey<ConfiguredFeature<?, ?>> plant = plants.get(random.nextInt(plants.size()));
		int spread = Math.max(1, radius / 3);
		BlockPos treePos = new BlockPos(origin.getX() + random.nextInt(2 * spread + 1) - spread, top + 2,
				origin.getZ() + random.nextInt(2 * spread + 1) - spread);
		if (level.getBlockState(treePos.below()).is(ModBlocks.TRANS_GRASS_BLOCK)) {
			level.setBlock(treePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(plant)
					.ifPresent(feature -> feature.value().place(level, context.chunkGenerator(), random, treePos));
		}
		return true;
	}

	private void decorate(WorldGenLevel level, BlockPos pos, RandomSource random) {
		int roll = random.nextInt(100);
		BlockState plant;
		if (roll < 30) {
			plant = ModBlocks.TRANS_SHORT_GRASS.defaultBlockState();
		} else if (roll < 36) {
			plant = ModBlocks.PRIDE_BLOSSOM.defaultBlockState();
		} else if (roll < 40) {
			plant = ModBlocks.FAIRY_BELL.defaultBlockState();
		} else if (roll < 43) {
			plant = ModBlocks.STAR_BLOOM.defaultBlockState();
		} else if (roll < 46) {
			plant = ModBlocks.TRANS_TULIP.defaultBlockState();
		} else if (roll < 48) {
			plant = ModBlocks.HEART_BLOOM.defaultBlockState();
		} else {
			return;
		}
		level.setBlock(pos, plant, Block.UPDATE_CLIENTS);
	}
}
