package dev.goober.transdimension.item;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.TransDimension;

/**
 * A Trans Pearl set with a Trans Crystal: the key to a Fairy Portal (set twelve into its frames). Held up in the Trans
 * Realm it tugs towards the nearest Fairy Sanctum: a trail of sparkles streams off that way and it tells you roughly
 * how far. It isn't used up by that.
 */
public class TransCrystalPearlItem extends Item {
	public static final TagKey<Structure> FAIRY_SANCTUMS = TagKey.create(Registries.STRUCTURE, TransDimension.id("fairy_sanctums"));
	private static final int[] TRAIL = {0xF5A9B8, 0x5BCEFA, 0xFFFFFF};
	private static final String[] DIRECTIONS = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};

	public TransCrystalPearlItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (!serverLevel.dimension().equals(TransDimension.TRANS_REALM)) {
			player.sendOverlayMessage(Component.translatable("item.transdimension.trans_crystal_pearl.quiet"));
			return InteractionResult.SUCCESS;
		}
		BlockPos sanctum = this.findSanctum(serverLevel, player.blockPosition());
		if (sanctum == null) {
			player.sendOverlayMessage(Component.translatable("item.transdimension.trans_crystal_pearl.nothing"));
			return InteractionResult.SUCCESS;
		}
		double dx = sanctum.getX() + 0.5 - player.getX();
		double dz = sanctum.getZ() + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 24.0) {
			player.sendOverlayMessage(Component.translatable("item.transdimension.trans_crystal_pearl.here"));
		} else {
			// Yaw-style angle: 0 = south, 90 = west, 180 = north, 270 = east (like the player's facing).
			float angle = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
			int sector = Math.floorMod(Math.round(angle / 45.0F), 8);
			Component direction = Component.translatable("direction.transdimension." + DIRECTIONS[sector]);
			int rounded = (int) (Math.round(distance / 50.0) * 50);
			player.sendOverlayMessage(Component.translatable("item.transdimension.trans_crystal_pearl.tug", direction, rounded));
			// A trail of sparkles streaming off towards it.
			Vec3 start = player.getEyePosition().add(0.0, -0.3, 0.0);
			Vec3 step = new Vec3(dx, 0.0, dz).normalize().scale(0.5);
			for (int i = 2; i < 24; i++) {
				Vec3 at = start.add(step.scale(i)).add(0.0, Mth.sin(i * 0.5F) * 0.15, 0.0);
				serverLevel.sendParticles(new DustParticleOptions(TRAIL[i % TRAIL.length], 1.0F), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
		return InteractionResult.SUCCESS;
	}

	private BlockPos findSanctum(ServerLevel level, BlockPos from) {
		HolderSet<Structure> sanctums = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(FAIRY_SANCTUMS).orElse(null);
		if (sanctums == null) {
			return null;
		}
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, sanctums, from, 100, false);
		return found != null ? found.getFirst() : null;
	}
}
