package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * Very rarely, a giant beanstalk climbs out of a Cloud Realm island up to a cloud of its own. It's made of concrete
 * (because why not): two strands of lime and green concrete twisting round a green core, with big flat lime leaves
 * every few blocks to climb by, and on the cloud at the top, a cloud chest of heavenly loot.
 */
public class BeanstalkFeature extends Feature<NoneFeatureConfiguration> {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, TransDimension.id("chests/heavenly_ruin"));
	private static final double TWIST = 0.16;
	private static final double STRAND_RADIUS = 2.2;
	private static final int LEAF_EVERY = 8;

	public BeanstalkFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		// The middle of the chunk, so it stays inside the chunks a feature may build in.
		int cx = (context.origin().getX() & ~15) + 8;
		int cz = (context.origin().getZ() & ~15) + 8;
		int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
		if (ground <= level.getMinY() + 8 || ground > 140 || level.getBlockState(new BlockPos(cx, ground - 1, cz)).isAir()) {
			return false;
		}
		int top = Math.min(level.getMaxY() - 14, ground + 100);
		if (top - ground < 40) {
			return false;
		}
		BlockState lime = Blocks.CONCRETE.lime().defaultBlockState();
		BlockState green = Blocks.CONCRETE.green().defaultBlockState();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double phase = random.nextDouble() * Math.PI * 2.0;
		for (int y = ground - 2; y <= top; y++) {
			double angle = phase + (y - ground) * TWIST;
			// the green core
			set(level, cursor.set(cx, y, cz), green);
			// two strands twisting round it
			for (int strand = 0; strand < 2; strand++) {
				double a = angle + strand * Math.PI;
				double sx = cx + 0.5 + Math.cos(a) * STRAND_RADIUS;
				double sz = cz + 0.5 + Math.sin(a) * STRAND_RADIUS;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int x = (int) Math.floor(sx) + dx;
						int z = (int) Math.floor(sz) + dz;
						double ox = x + 0.5 - sx;
						double oz = z + 0.5 - sz;
						if (ox * ox + oz * oz <= 1.3) {
							set(level, cursor.set(x, y, z), strand == 0 ? lime : green);
						}
					}
				}
			}
			// a big flat leaf every few blocks, sticking out from the strands
			if (y > ground + 3 && y < top - 4 && (y - ground) % LEAF_EVERY == 0) {
				leaf(level, cursor, cx + 0.5, y, cz + 0.5, angle + Math.PI * 0.5 + random.nextDouble() * 0.6, lime);
			}
		}
		// The cloud at the top, with the beanstalk's treasure on it.
		BlockPos cloudCentre = new BlockPos(cx, top + 3, cz);
		WoolCloudFeature.cloud(level, cloudCentre, random, 1.4);
		BlockPos chest = new BlockPos(cx, top + 3, cz);
		for (int y = top + 12; y > top - 2; y--) {
			if (!level.getBlockState(cursor.set(cx, y - 1, cz)).isAir() && level.getBlockState(cursor.set(cx, y, cz)).isAir()) {
				chest = new BlockPos(cx, y, cz);
				break;
			}
		}
		// A cloud chest, named like one a player places (from its item), with the heavenly ruins' holy loot.
		level.setBlock(chest, ModBlocks.CLOUD_CHEST.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
		BlockEntity entity = level.getBlockEntity(chest);
		if (entity != null) {
			entity.applyComponentsFromItemStack(new ItemStack(ModBlocks.CLOUD_CHEST));
		}
		RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		return true;
	}

	/** A leaf: a flat oval pad three blocks out from the stalk, in the direction {@code angle}. */
	private static void leaf(WorldGenLevel level, BlockPos.MutableBlockPos cursor, double cx, int y, double cz, double angle, BlockState state) {
		double lx = cx + Math.cos(angle) * 4.0;
		double lz = cz + Math.sin(angle) * 4.0;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				int x = (int) Math.floor(lx) + dx;
				int z = (int) Math.floor(lz) + dz;
				double ox = x + 0.5 - lx;
				double oz = z + 0.5 - lz;
				// long along the leaf's direction, narrow across it
				double along = ox * cos + oz * sin;
				double across = -ox * sin + oz * cos;
				if ((along * along) / 9.0 + (across * across) / 3.0 <= 1.0) {
					set(level, cursor.set(x, y, z), state);
				}
			}
		}
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (!level.isOutsideBuildHeight(pos.getY()) && level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}
}
