package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * The last thing done to every new chunk in the realms: any vanilla lava in it becomes pink lava. Vanilla's cave
 * generator fills deep caves and some of its underground pools with lava straight from code (NoiseBasedAquifer and the
 * fluid picker, which NoiseBasedChunkGeneratorMixin only partly reaches), so this catches whatever is left, wherever it
 * came from. Only sections that hold any lava at all are looked through.
 */
public class PinkLavaFeature extends Feature<NoneFeatureConfiguration> {
	public PinkLavaFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		ChunkAccess chunk = context.level().getChunk(context.origin());
		BlockState pink = ModBlocks.PINK_LAVA.defaultBlockState();
		boolean changed = false;
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.LAVA))) {
				continue;
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (state.is(Blocks.LAVA)) {
							section.setBlockState(x, y, z, pink.setValue(LiquidBlock.LEVEL, state.getValue(LiquidBlock.LEVEL)), false);
							changed = true;
						}
					}
				}
			}
		}
		return changed;
	}
}
