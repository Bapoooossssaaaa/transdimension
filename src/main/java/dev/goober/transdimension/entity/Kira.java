package dev.goober.transdimension.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Kira, who moves into the Egg House once the Trans Fairy has struck Maddie down (see {@link Maddie#mustLeave}); new
 * Egg Houses get her too. She keeps Maddie's promise: anyone who never got Maddie's Trans Wand and Trans Wings gets
 * them from her, and after that she has a few words for you. Her head is comically big (KiraRenderer).
 *
 * <p>Otherwise she's like Maddie: she can't be hurt, never despawns and potters around her home. Unlike Maddie, she
 * stays.
 */
public class Kira extends Maddie {
	/** How many lines Kira has ({@code message.transdimension.kira.line_1} and on, written by generate_data.py). */
	private static final int LINES = 5;

	public Kira(EntityType<? extends Kira> entityType, Level level) {
		super(entityType, level);
		this.setCustomName(Component.translatable("entity.transdimension.kira"));
	}

	@Override
	protected boolean mustLeave(ServerLevel level) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			this.getNavigation().stop();
			this.getLookControl().setLookAt(player, 30.0F, 30.0F);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
					0.6F, 1.5F);
			if (hasGifted(serverPlayer)) {
				this.say(serverPlayer, "message.transdimension.kira.line_" + (1 + this.getRandom().nextInt(LINES)));
			} else {
				this.say(serverPlayer, "message.transdimension.kira.gifts");
				this.giveGifts(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Kira's line in chat, as "<Kira> ...". */
	private void say(ServerPlayer player, String line) {
		player.sendSystemMessage(Component.translatable("chat.type.text", this.getName(), Component.translatable(line)));
	}
}
