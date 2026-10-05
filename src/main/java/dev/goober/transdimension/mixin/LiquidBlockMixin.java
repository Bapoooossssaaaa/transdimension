package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * Water meeting a pink lava source makes pink obsidian. Vanilla decides in {@code LiquidBlock#shouldSpreadLiquid}
 * (a source turns into {@code Blocks.OBSIDIAN}, flowing lava into cobblestone; NeoForge's 26.2 patch shows the method);
 * for the pink lava block, that obsidian becomes {@link ModBlocks#PINK_OBSIDIAN}. With {@code require = 0}, if the
 * method ever changes, pink lava just makes ordinary obsidian.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
	@ModifyExpressionValue(method = "shouldSpreadLiquid", at = @At(value = "FIELD",
			target = "Lnet/minecraft/world/level/block/Blocks;OBSIDIAN:Lnet/minecraft/world/level/block/Block;"), require = 0)
	private Block transdimension$pinkObsidian(Block obsidian) {
		return (Object) this == ModBlocks.PINK_LAVA ? ModBlocks.PINK_OBSIDIAN : obsidian;
	}
}
