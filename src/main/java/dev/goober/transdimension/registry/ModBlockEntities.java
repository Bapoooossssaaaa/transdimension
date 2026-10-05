package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.entity.PlushSpotBlockEntity;

/** Block entity types. */
public final class ModBlockEntities {
	public static final BlockEntityType<PlushSpotBlockEntity> PLUSH_SPOT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			TransDimension.id("plush_spot"), FabricBlockEntityTypeBuilder.create(PlushSpotBlockEntity::new, ModBlocks.PLUSH_SPOT).build());

	private ModBlockEntities() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
