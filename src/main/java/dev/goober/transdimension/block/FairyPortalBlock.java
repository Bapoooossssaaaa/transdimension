package dev.goober.transdimension.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.world.FairyRealm;

/**
 * The surface of an open Fairy Portal: a pool of shimmering pink, blue and lilac light (its texture swirls like an
 * opal) that you drop into, like an end portal. It leads to the Fairy Realm, and from there back to the portal you came
 * through. See {@link FairyRealm#portalDestination}.
 */
public class FairyPortalBlock extends Block implements Portal {
	private static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);
	private static final int[] SHIMMER = {0xF5A9B8, 0x5BCEFA, 0xC9B8F2, 0xFFFFFF};

	public FairyPortalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
			boolean isPrecise) {
		if (entity.canUsePortal(false)) {
			entity.setAsInsidePortal(this, pos);
		}
	}

	@Override
	@Nullable
	public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
		return FairyRealm.portalDestination(level, entity, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// Motes of light drift up out of the pool, and now and then a star twinkles over it.
		for (int i = 0; i < 2; i++) {
			int colour = SHIMMER[random.nextInt(SHIMMER.length)];
			level.addParticle(new DustParticleOptions(colour, 0.6F + random.nextFloat() * 0.5F), pos.getX() + random.nextDouble(),
					pos.getY() + 0.8, pos.getZ() + random.nextDouble(), 0.0, 0.04 + random.nextDouble() * 0.04, 0.0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(),
					0.0, 0.02, 0.0);
		}
	}
}
