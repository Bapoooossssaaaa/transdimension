package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A cloud of white wool hanging in the Cloud Realm's sky: a cluster of round puffs that all rise from one flat base,
 * like a fair-weather cumulus. Only fills air, so clouds drift round the islands rather than through them.
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
		if (!level.getBlockState(origin).isAir()) {
			return false;
		}
		BlockState wool = Blocks.WOOL.white().defaultBlockState();
		int puffs = 3 + random.nextInt(5);
		double spread = 3.0 + random.nextInt(5);
		int base = origin.getY();
		int placed = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i < puffs; i++) {
			double radius = 2.0 + random.nextDouble() * 3.0;
			double cx = origin.getX() + (random.nextDouble() - 0.5) * spread * 2.0;
			double cz = origin.getZ() + (random.nextDouble() - 0.5) * spread * 2.0;
			double cy = base + radius * 0.35;
			int r = (int) Math.ceil(radius);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = -r; dy <= r; dy++) {
						double x = Math.floor(cx) + dx;
						double y = Math.floor(cy) + dy;
						double z = Math.floor(cz) + dz;
						if (y < base) {
							continue; // the flat underside
						}
						double ox = x + 0.5 - cx;
						double oy = (y + 0.5 - cy) * 1.25;
						double oz = z + 0.5 - cz;
						if (ox * ox + oy * oy + oz * oz > radius * radius) {
							continue;
						}
						cursor.set(x, y, z);
						if (level.getBlockState(cursor).isAir()) {
							level.setBlock(cursor, wool, Block.UPDATE_CLIENTS);
							placed++;
						}
					}
				}
			}
		}
		return placed > 0;
	}
}
