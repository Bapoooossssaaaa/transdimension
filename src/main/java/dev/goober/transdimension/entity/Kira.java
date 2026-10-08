package dev.goober.transdimension.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Kira, Maddie's girlfriend, who moves into the Egg House once the Trans Fairy has struck Maddie down (see
 * {@link Maddie#mustLeave}); new Egg Houses get her too. Talk to her and her own dialogue opens in Maddie's speech box
 * (MaddieDialogueScreen, {@code dialogue.transdimension.kira.*}): she's heartbroken, and she keeps Maddie's promise,
 * handing on the Trans Wand and Trans Wings Maddie left with her to anyone who never got them. Her head is comically big
 * (KiraRenderer).
 *
 * <p>Otherwise she's like Maddie: she can't be hurt, never despawns and potters around her home. Unlike Maddie, she
 * stays.
 */
public class Kira extends Maddie {
	public Kira(EntityType<? extends Kira> entityType, Level level) {
		super(entityType, level);
		this.setCustomName(Component.translatable("entity.transdimension.kira"));
	}

	@Override
	protected boolean mustLeave(ServerLevel level) {
		return false;
	}
}
