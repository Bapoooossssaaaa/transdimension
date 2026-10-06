package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The Cloud Realm's clouds: heaps of round puffs of white wool, high in the sky, 30 to 48 blocks above whatever island is
 * under them (or anywhere from y 150 up, over the void). Every puff is round all over, so the clouds are lumpy and soft
 * underneath, not flat. They're only ever built into air. {@link #cloud} also tops the giant beanstalk.
 */
public class WoolCloudFeature extends Feature<NoneFeatureConfiguration> {
	public WoolCloudFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
		int y = ground > level.getMinY() + 1 ? ground + 30 + random.nextInt(19) : 150 + random.nextInt(80);
		if (y > level.getMaxY() - 10) {
			return false;
		}
		return cloud(level, new BlockPos(origin.getX(), y, origin.getZ()), random, 1.0) > 0;
	}

	/**
	 * A cloud round {@code centre}: a big puff in the middle and three to seven more heaped round it, a little higher and
	 * lower, each a slightly squashed ball. {@code scale} sizes the whole thing. Returns how many blocks it placed.
	 */
	public static int cloud(WorldGenLevel level, BlockPos centre, RandomSource random, double scale) {
		BlockState wool = Blocks.WOOL.white().defaultBlockState();
		int placed = puff(level, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, (4.0 + random.nextDouble() * 1.5) * scale, wool);
		int puffs = 3 + random.nextInt(5);
		double spread = (4.0 + random.nextInt(5)) * scale;
		for (int i = 0; i < puffs; i++) {
			double radius = (2.5 + random.nextDouble() * 2.5) * scale;
			double angle = random.nextDouble() * Math.PI * 2.0;
			double out = spread * (0.5 + random.nextDouble() * 0.5);
			placed += puff(level, centre.getX() + 0.5 + Math.cos(angle) * out, centre.getY() + 0.5 + (random.nextDouble() - 0.4) * 2.5 * scale,
					centre.getZ() + 0.5 + Math.sin(angle) * out, radius, wool);
		}
		return placed;
	}

	/** One ball of wool, three quarters as tall as it is wide. */
	private static int puff(WorldGenLevel level, double cx, double cy, double cz, double radius, BlockState wool) {
		int r = (int) Math.ceil(radius);
		int placed = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -r; dy <= r; dy++) {
					int x = (int) Math.floor(cx) + dx;
					int y = (int) Math.floor(cy) + dy;
					int z = (int) Math.floor(cz) + dz;
					double ox = x + 0.5 - cx;
					double oy = (y + 0.5 - cy) / 0.75;
					double oz = z + 0.5 - cz;
					if (ox * ox + oy * oy + oz * oz > radius * radius) {
						continue;
					}
					cursor.set(x, y, z);
					if (level.isOutsideBuildHeight(y) || !level.getBlockState(cursor).isAir()) {
						continue;
					}
					level.setBlock(cursor, wool, Block.UPDATE_CLIENTS);
					placed++;
				}
			}
		}
		return placed;
	}
}
