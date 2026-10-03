package dev.goober.transdimension.event;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * Small gameplay hooks that make the Trans Realm feel like its own place.
 */
public final class RealmEvents {
	/** Vanilla's sheep shearing table (it picks the per-colour wool table). */
	private static final ResourceKey<LootTable> SHEAR_SHEEP = ResourceKey.create(Registries.LOOT_TABLE,
			Identifier.withDefaultNamespace("shearing/sheep"));

	private RealmEvents() {
	}

	public static void initialize() {
		// Sheep in the realm wear trans flag wool (TransRecolor draws it), so shearing them gives trans wool.
		LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> {
			if (!holder.is(SHEAR_SHEEP) || !context.getLevel().dimension().equals(TransDimension.TRANS_REALM)) {
				return;
			}
			drops.replaceAll(stack -> stack.is(ItemTags.WOOL) ? new ItemStack(ModBlocks.TRANS_WOOL, stack.getCount()) : stack);
		});
	}
}
