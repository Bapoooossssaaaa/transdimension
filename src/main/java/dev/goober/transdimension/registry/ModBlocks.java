package dev.goober.transdimension.registry;

import java.util.Optional;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.FurnitureBlock;
import dev.goober.transdimension.block.PrideOvenBlock;
import dev.goober.transdimension.block.TransGrassBlock;

/**
 * All Trans Realm blocks. Most copy the "feel" (hardness, sounds, tool) of a vanilla
 * counterpart via {@link BlockBehaviour.Properties#ofFullCopy}.
 *
 * <p>Never copy from blocks whose properties depend on block-state properties (for example the smoker's
 * {@code LIT} light level): the copied light function would then read a property our block doesn't have.
 */
public final class ModBlocks {
	/** Grows the trans trees from {@code data/transdimension/worldgen/configured_feature/}. */
	public static final TreeGrower TRANS_TREE_GROWER = new TreeGrower(
			"transdimension:trans",
			Optional.of(configuredFeature("heartwood_tree")),
			Optional.of(configuredFeature("trans_tree")),
			Optional.of(configuredFeature("trans_cherry_tree")));

	// ---------------------------------------------------------------- terrain
	public static final Block TRANS_GRASS_BLOCK = register("trans_grass_block", TransGrassBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK));
	public static final Block TRANS_DIRT = register("trans_dirt", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT));
	public static final Block TRANS_SAND = register("trans_sand",
			properties -> new ColoredFallingBlock(new ColorRGBA(0xFFF5A9B8), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.SAND));

	// ---------------------------------------------------------------- trans stone family
	public static final Block TRANS_STONE = register("trans_stone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));
	public static final Block TRANS_STONE_STAIRS = stairs("trans_stone_stairs", TRANS_STONE, Blocks.STONE_STAIRS);
	public static final Block TRANS_STONE_SLAB = slab("trans_stone_slab", Blocks.STONE_SLAB);
	public static final Block TRANS_STONE_BUTTON = register("trans_stone_button",
			properties -> new ButtonBlock(BlockSetType.STONE, 20, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BUTTON));
	public static final Block TRANS_STONE_PRESSURE_PLATE = register("trans_stone_pressure_plate",
			properties -> new PressurePlateBlock(BlockSetType.STONE, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_PRESSURE_PLATE));

	public static final Block TRANS_COBBLESTONE = register("trans_cobblestone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE));
	public static final Block TRANS_COBBLESTONE_STAIRS = stairs("trans_cobblestone_stairs", TRANS_COBBLESTONE, Blocks.COBBLESTONE_STAIRS);
	public static final Block TRANS_COBBLESTONE_SLAB = slab("trans_cobblestone_slab", Blocks.COBBLESTONE_SLAB);
	public static final Block TRANS_COBBLESTONE_WALL = wall("trans_cobblestone_wall", Blocks.COBBLESTONE_WALL);

	public static final Block TRANS_STONE_BRICKS = register("trans_stone_bricks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS));
	public static final Block CRACKED_TRANS_STONE_BRICKS = register("cracked_trans_stone_bricks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRACKED_STONE_BRICKS));
	public static final Block CHISELED_TRANS_STONE_BRICKS = register("chiseled_trans_stone_bricks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_STONE_BRICKS));
	public static final Block TRANS_STONE_BRICK_STAIRS = stairs("trans_stone_brick_stairs", TRANS_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS);
	public static final Block TRANS_STONE_BRICK_SLAB = slab("trans_stone_brick_slab", Blocks.STONE_BRICK_SLAB);
	public static final Block TRANS_STONE_BRICK_WALL = wall("trans_stone_brick_wall", Blocks.STONE_BRICK_WALL);

	// ---------------------------------------------------------------- trans sandstone family
	public static final Block TRANS_SANDSTONE = register("trans_sandstone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.SANDSTONE));
	public static final Block CUT_TRANS_SANDSTONE = register("cut_trans_sandstone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CUT_SANDSTONE));
	public static final Block CHISELED_TRANS_SANDSTONE = register("chiseled_trans_sandstone", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_SANDSTONE));
	public static final Block TRANS_SANDSTONE_STAIRS = stairs("trans_sandstone_stairs", TRANS_SANDSTONE, Blocks.SANDSTONE_STAIRS);
	public static final Block TRANS_SANDSTONE_SLAB = slab("trans_sandstone_slab", Blocks.SANDSTONE_SLAB);
	public static final Block TRANS_SANDSTONE_WALL = wall("trans_sandstone_wall", Blocks.SANDSTONE_WALL);

	// ---------------------------------------------------------------- crystals
	public static final Block TRANS_CRYSTAL_ORE = register("trans_crystal_ore",
			properties -> new DropExperienceBlock(UniformInt.of(3, 7), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).lightLevel(state -> 3));
	public static final Block TRANS_CRYSTAL_BLOCK = register("trans_crystal_block", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).strength(5.0F, 6.0F).requiresCorrectToolForDrops().lightLevel(state -> 6));
	/** A glowing crystal cluster that grows on any face, like amethyst clusters (height 7, width 10). */
	public static final Block TRANS_CRYSTAL_CLUSTER = register("trans_crystal_cluster",
			properties -> new AmethystClusterBlock(7.0F, 10.0F, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER).lightLevel(state -> 7));

	// ---------------------------------------------------------------- trans wood family
	public static final Block TRANS_LOG = register("trans_log", RotatedPillarBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LOG));
	public static final Block STRIPPED_TRANS_LOG = register("stripped_trans_log", RotatedPillarBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_BIRCH_LOG));
	public static final Block TRANS_WOOD = register("trans_wood", RotatedPillarBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_WOOD));
	public static final Block STRIPPED_TRANS_WOOD = register("stripped_trans_wood", RotatedPillarBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_BIRCH_WOOD));
	public static final Block TRANS_PLANKS = register("trans_planks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PLANKS));
	public static final Block TRANS_STAIRS = stairs("trans_stairs", TRANS_PLANKS, Blocks.CHERRY_STAIRS);
	public static final Block TRANS_SLAB = slab("trans_slab", Blocks.CHERRY_SLAB);
	public static final Block TRANS_FENCE = register("trans_fence", FenceBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_FENCE));
	public static final Block TRANS_FENCE_GATE = register("trans_fence_gate",
			properties -> new FenceGateBlock(WoodType.CHERRY, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_FENCE_GATE));
	public static final Block TRANS_DOOR = register("trans_door",
			properties -> new DoorBlock(BlockSetType.CHERRY, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_DOOR));
	public static final Block TRANS_TRAPDOOR = register("trans_trapdoor",
			properties -> new TrapDoorBlock(BlockSetType.CHERRY, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_TRAPDOOR));
	public static final Block TRANS_BUTTON = register("trans_button",
			properties -> new ButtonBlock(BlockSetType.CHERRY, 30, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_BUTTON));
	public static final Block TRANS_PRESSURE_PLATE = register("trans_pressure_plate",
			properties -> new PressurePlateBlock(BlockSetType.CHERRY, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PRESSURE_PLATE));
	public static final Block TRANS_LEAVES = register("trans_leaves",
			properties -> new UntintedParticleLeavesBlock(0.02F, ParticleTypes.CHERRY_LEAVES, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LEAVES));
	public static final Block TRANS_SAPLING = register("trans_sapling",
			properties -> new SaplingBlock(TRANS_TREE_GROWER, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_SAPLING));
	public static final Block POTTED_TRANS_SAPLING = registerWithoutItem("potted_trans_sapling",
			properties -> new FlowerPotBlock(TRANS_SAPLING, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_CHERRY_SAPLING));

	// ---------------------------------------------------------------- flowers
	public static final Block PRIDE_BLOSSOM = register("pride_blossom",
			properties -> new FlowerBlock(MobEffects.REGENERATION, 8.0F, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY));
	public static final Block POTTED_PRIDE_BLOSSOM = registerWithoutItem("potted_pride_blossom",
			properties -> new FlowerPotBlock(PRIDE_BLOSSOM, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_POPPY));

	// ---------------------------------------------------------------- glass, wool and light
	public static final Block TRANS_STAINED_GLASS = glass("trans_stained_glass", DyeColor.PINK);
	public static final Block TRANS_STAINED_GLASS_PANE = pane("trans_stained_glass_pane", DyeColor.PINK);
	public static final Block TRANS_PINK_STAINED_GLASS = glass("trans_pink_stained_glass", DyeColor.PINK);
	public static final Block TRANS_PINK_STAINED_GLASS_PANE = pane("trans_pink_stained_glass_pane", DyeColor.PINK);
	public static final Block TRANS_BLUE_STAINED_GLASS = glass("trans_blue_stained_glass", DyeColor.LIGHT_BLUE);
	public static final Block TRANS_BLUE_STAINED_GLASS_PANE = pane("trans_blue_stained_glass_pane", DyeColor.LIGHT_BLUE);

	public static final Block TRANS_WOOL = register("trans_wool", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
	public static final Block TRANS_CARPET = register("trans_carpet", CarpetBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
	public static final Block TRANS_LANTERN = register("trans_lantern", LanternBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.LANTERN));

	// ---------------------------------------------------------------- furniture
	public static final Block TRANS_CHAIR = register("trans_chair",
			properties -> new FurnitureBlock(FurnitureBlock.CHAIR_SHAPE, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PLANKS).noOcclusion());
	public static final Block TRANS_TABLE = register("trans_table",
			properties -> new FurnitureBlock(FurnitureBlock.TABLE_SHAPE, properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_PLANKS).noOcclusion());

	// ---------------------------------------------------------------- bakery
	public static final Block TRANS_CAKE = register("trans_cake", CakeBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE), new Item.Properties().stacksTo(1));
	/** Job site block of the Trans Baker villager. */
	public static final Block PRIDE_OVEN = register("pride_oven", PrideOvenBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(3.5F).requiresCorrectToolForDrops()
					.sound(SoundType.STONE).lightLevel(state -> 8));

	private ModBlocks() {
	}

	private static ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> configuredFeature(String name) {
		return ResourceKey.create(Registries.CONFIGURED_FEATURE, TransDimension.id(name));
	}

	private static Block stairs(String name, Block base, Block copyFrom) {
		return register(name, properties -> new StairBlock(base.defaultBlockState(), properties),
				BlockBehaviour.Properties.ofFullCopy(copyFrom));
	}

	private static Block slab(String name, Block copyFrom) {
		return register(name, SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(copyFrom));
	}

	private static Block wall(String name, Block copyFrom) {
		return register(name, WallBlock::new, BlockBehaviour.Properties.ofFullCopy(copyFrom));
	}

	private static Block glass(String name, DyeColor beaconColor) {
		return register(name, properties -> new StainedGlassBlock(beaconColor, properties),
				BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS));
	}

	private static Block pane(String name, DyeColor beaconColor) {
		return register(name, properties -> new StainedGlassPaneBlock(beaconColor, properties),
				BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS_PANE));
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
