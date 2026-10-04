package dev.goober.transdimension.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import dev.goober.transdimension.registry.ModBlockEntities;

/** Holds nothing: it only exists so the client can draw the Fairy Jar's dancing light (FairyJarRenderer). */
public class FairyJarBlockEntity extends BlockEntity {
	public FairyJarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FAIRY_JAR, pos, state);
	}
}
