package dev.goober.transdimension.item;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.CrystalEye;

/**
 * A Trans Pearl set with a Trans Crystal: the key to a Fairy Portal (set twelve into its frames). Thrown in the Trans
 * Realm it works like an eye of ender: it flies off towards the nearest Fairy Sanctum ({@link CrystalEye}), and you
 * follow it. Most of the time it drops back down to be thrown again; now and then it shatters.
 */
public class TransCrystalPearlItem extends Item {
	public static final TagKey<Structure> FAIRY_SANCTUMS = TagKey.create(Registries.STRUCTURE, TransDimension.id("fairy_sanctums"));

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
		ItemStack stack = player.getItemInHand(hand);
		CrystalEye eye = new CrystalEye(serverLevel, player, stack);
		eye.setPos(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
		eye.signalTo(sanctum);
		serverLevel.addFreshEntity(eye);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				1.0F, 1.6F);
		stack.consume(1, player);
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
