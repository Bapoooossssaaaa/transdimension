package dev.goober.transdimension.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.TransGrassBlock;

/**
 * All Trans Realm blocks. Most copy the "feel" (hardness, sounds, tool) of a vanilla
 * counterpart via {@link BlockBehaviour.Properties#ofFullCopy}.
 */
public final class ModBlocks {
	// Terrain
	public static final Block TRANS_GRASS_BLOCK = register("trans_grass_block", TransGrassBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK));
	public static final Block TRANS_DIRT = register("trans_dirt", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT));
	public static final Block TRANS_STONE = register("trans_stone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));
	public static final Block TRANS_COBBLESTONE = register("trans_cobblestone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE));
	public static final Block TRANS_STONE_BRICKS = register("trans_stone_bricks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS));
	public static final Block TRANS_SAND = register("trans_sand",
			properties -> new ColoredFallingBlock(new ColorRGBA(0xFFF5A9B8), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.SAND));

	// Ore and storage
	public static final Block TRANS_CRYSTAL_ORE = register("trans_crystal_ore",
			properties -> new DropExperienceBlock(UniformInt.of(3, 7), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).lightLevel(state -> 3));
	public static final Block TRANS_CRYSTAL_BLOCK = register("trans_crystal_block", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_BLOCK).lightLevel(state -> 6));

	// Trees and plants
	public static final Block TRANS_LOG = register("trans_log", RotatedPillarBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LOG));
	public static final Block TRANS_PLANKS = register("trans_planks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PLANKS));
	public static final Block TRANS_LEAVES = register("trans_leaves",
			properties -> new UntintedParticleLeavesBlock(0.02F, ParticleTypes.CHERRY_LEAVES, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LEAVES));
	public static final Block PRIDE_BLOSSOM = register("pride_blossom",
			properties -> new FlowerBlock(MobEffects.REGENERATION, 8.0F, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY));

	// Bakery
	public static final Block TRANS_CAKE = register("trans_cake", CakeBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE), new Item.Properties().stacksTo(1));
	/** Job site block of the Trans Baker villager. */
	public static final Block PRIDE_OVEN = register("pride_oven", Block::new,
			BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.STONE).lightLevel(state -> 8));

	// The block form of Trans Water (no item; use the bucket).
	public static final Block TRANS_WATER = registerWithoutItem("trans_water",
			properties -> new LiquidBlock(ModFluids.TRANS_WATER, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.WATER));

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return register(name, factory, properties, new Item.Properties());
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties, Item.Properties itemProperties) {
		Identifier id = TransDimension.id(name);
		BlockItemId ids = BlockItemId.create(id, id);

		Block block = factory.apply(properties.setId(ids.block()));
		Registry.register(BuiltInRegistries.BLOCK, ids.block(), block);

		BlockItem blockItem = new BlockItem(block, itemProperties.useBlockDescriptionPrefix().setId(ids.item()));
		Registry.register(BuiltInRegistries.ITEM, ids.item(), blockItem);
		return block;
	}

	private static Block registerWithoutItem(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TransDimension.id(name));
		Block block = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
