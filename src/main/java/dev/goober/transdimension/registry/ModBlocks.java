package dev.goober.transdimension.registry;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffect;
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
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TransparentBlock;
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

	// ---------------------------------------------------------------- trans deepslate family
	public static final Block TRANS_DEEPSLATE = register("trans_deepslate", RotatedPillarBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE));
	public static final Block COBBLED_TRANS_DEEPSLATE = copy("cobbled_trans_deepslate", Blocks.COBBLED_DEEPSLATE);
	public static final Block COBBLED_TRANS_DEEPSLATE_STAIRS = stairs("cobbled_trans_deepslate_stairs", COBBLED_TRANS_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS);
	public static final Block COBBLED_TRANS_DEEPSLATE_SLAB = slab("cobbled_trans_deepslate_slab", Blocks.COBBLED_DEEPSLATE_SLAB);
	public static final Block COBBLED_TRANS_DEEPSLATE_WALL = wall("cobbled_trans_deepslate_wall", Blocks.COBBLED_DEEPSLATE_WALL);
	public static final Block POLISHED_TRANS_DEEPSLATE = copy("polished_trans_deepslate", Blocks.POLISHED_DEEPSLATE);
	public static final Block POLISHED_TRANS_DEEPSLATE_STAIRS = stairs("polished_trans_deepslate_stairs", POLISHED_TRANS_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_STAIRS);
	public static final Block POLISHED_TRANS_DEEPSLATE_SLAB = slab("polished_trans_deepslate_slab", Blocks.POLISHED_DEEPSLATE_SLAB);
	public static final Block POLISHED_TRANS_DEEPSLATE_WALL = wall("polished_trans_deepslate_wall", Blocks.POLISHED_DEEPSLATE_WALL);
	public static final Block TRANS_DEEPSLATE_BRICKS = copy("trans_deepslate_bricks", Blocks.DEEPSLATE_BRICKS);
	public static final Block TRANS_DEEPSLATE_BRICK_STAIRS = stairs("trans_deepslate_brick_stairs", TRANS_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS);
	public static final Block TRANS_DEEPSLATE_BRICK_SLAB = slab("trans_deepslate_brick_slab", Blocks.DEEPSLATE_BRICK_SLAB);
	public static final Block TRANS_DEEPSLATE_BRICK_WALL = wall("trans_deepslate_brick_wall", Blocks.DEEPSLATE_BRICK_WALL);
	public static final Block CRACKED_TRANS_DEEPSLATE_BRICKS = copy("cracked_trans_deepslate_bricks", Blocks.CRACKED_DEEPSLATE_BRICKS);
	public static final Block TRANS_DEEPSLATE_TILES = copy("trans_deepslate_tiles", Blocks.DEEPSLATE_TILES);
	public static final Block TRANS_DEEPSLATE_TILE_STAIRS = stairs("trans_deepslate_tile_stairs", TRANS_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS);
	public static final Block TRANS_DEEPSLATE_TILE_SLAB = slab("trans_deepslate_tile_slab", Blocks.DEEPSLATE_TILE_SLAB);
	public static final Block TRANS_DEEPSLATE_TILE_WALL = wall("trans_deepslate_tile_wall", Blocks.DEEPSLATE_TILE_WALL);
	public static final Block CRACKED_TRANS_DEEPSLATE_TILES = copy("cracked_trans_deepslate_tiles", Blocks.CRACKED_DEEPSLATE_TILES);
	public static final Block CHISELED_TRANS_DEEPSLATE = copy("chiseled_trans_deepslate", Blocks.CHISELED_DEEPSLATE);

	// ---------------------------------------------------------------- rose granite, pearl diorite, sky andesite, gravel
	public static final Block TRANS_GRANITE = copy("trans_granite", Blocks.GRANITE);
	public static final Block TRANS_GRANITE_STAIRS = stairs("trans_granite_stairs", TRANS_GRANITE, Blocks.GRANITE_STAIRS);
	public static final Block TRANS_GRANITE_SLAB = slab("trans_granite_slab", Blocks.GRANITE_SLAB);
	public static final Block TRANS_GRANITE_WALL = wall("trans_granite_wall", Blocks.GRANITE_WALL);
	public static final Block POLISHED_TRANS_GRANITE = copy("polished_trans_granite", Blocks.POLISHED_GRANITE);
	public static final Block POLISHED_TRANS_GRANITE_STAIRS = stairs("polished_trans_granite_stairs", POLISHED_TRANS_GRANITE, Blocks.POLISHED_GRANITE_STAIRS);
	public static final Block POLISHED_TRANS_GRANITE_SLAB = slab("polished_trans_granite_slab", Blocks.POLISHED_GRANITE_SLAB);
	public static final Block TRANS_DIORITE = copy("trans_diorite", Blocks.DIORITE);
	public static final Block TRANS_DIORITE_STAIRS = stairs("trans_diorite_stairs", TRANS_DIORITE, Blocks.DIORITE_STAIRS);
	public static final Block TRANS_DIORITE_SLAB = slab("trans_diorite_slab", Blocks.DIORITE_SLAB);
	public static final Block TRANS_DIORITE_WALL = wall("trans_diorite_wall", Blocks.DIORITE_WALL);
	public static final Block POLISHED_TRANS_DIORITE = copy("polished_trans_diorite", Blocks.POLISHED_DIORITE);
	public static final Block POLISHED_TRANS_DIORITE_STAIRS = stairs("polished_trans_diorite_stairs", POLISHED_TRANS_DIORITE, Blocks.POLISHED_DIORITE_STAIRS);
	public static final Block POLISHED_TRANS_DIORITE_SLAB = slab("polished_trans_diorite_slab", Blocks.POLISHED_DIORITE_SLAB);
	public static final Block TRANS_ANDESITE = copy("trans_andesite", Blocks.ANDESITE);
	public static final Block TRANS_ANDESITE_STAIRS = stairs("trans_andesite_stairs", TRANS_ANDESITE, Blocks.ANDESITE_STAIRS);
	public static final Block TRANS_ANDESITE_SLAB = slab("trans_andesite_slab", Blocks.ANDESITE_SLAB);
	public static final Block TRANS_ANDESITE_WALL = wall("trans_andesite_wall", Blocks.ANDESITE_WALL);
	public static final Block POLISHED_TRANS_ANDESITE = copy("polished_trans_andesite", Blocks.POLISHED_ANDESITE);
	public static final Block POLISHED_TRANS_ANDESITE_STAIRS = stairs("polished_trans_andesite_stairs", POLISHED_TRANS_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS);
	public static final Block POLISHED_TRANS_ANDESITE_SLAB = slab("polished_trans_andesite_slab", Blocks.POLISHED_ANDESITE_SLAB);
	public static final Block TRANS_GRAVEL = register("trans_gravel",
			properties -> new ColoredFallingBlock(new ColorRGBA(0xFFB9AEB4), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.GRAVEL));

	// ---------------------------------------------------------------- vanilla ores in trans stone and trans deepslate
	public static final Block TRANS_COAL_ORE = ore("trans_coal_ore", UniformInt.of(0, 2), Blocks.COAL_ORE);
	public static final Block TRANS_DEEPSLATE_COAL_ORE = ore("trans_deepslate_coal_ore", UniformInt.of(0, 2), Blocks.DEEPSLATE_COAL_ORE);
	public static final Block TRANS_IRON_ORE = ore("trans_iron_ore", ConstantInt.of(0), Blocks.IRON_ORE);
	public static final Block TRANS_DEEPSLATE_IRON_ORE = ore("trans_deepslate_iron_ore", ConstantInt.of(0), Blocks.DEEPSLATE_IRON_ORE);
	public static final Block TRANS_COPPER_ORE = ore("trans_copper_ore", ConstantInt.of(0), Blocks.COPPER_ORE);
	public static final Block TRANS_DEEPSLATE_COPPER_ORE = ore("trans_deepslate_copper_ore", ConstantInt.of(0), Blocks.DEEPSLATE_COPPER_ORE);
	public static final Block TRANS_GOLD_ORE = ore("trans_gold_ore", ConstantInt.of(0), Blocks.GOLD_ORE);
	public static final Block TRANS_DEEPSLATE_GOLD_ORE = ore("trans_deepslate_gold_ore", ConstantInt.of(0), Blocks.DEEPSLATE_GOLD_ORE);
	public static final Block TRANS_REDSTONE_ORE = register("trans_redstone_ore", RedStoneOreBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_ORE));
	public static final Block TRANS_DEEPSLATE_REDSTONE_ORE = register("trans_deepslate_redstone_ore", RedStoneOreBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_REDSTONE_ORE));
	public static final Block TRANS_LAPIS_ORE = ore("trans_lapis_ore", UniformInt.of(2, 5), Blocks.LAPIS_ORE);
	public static final Block TRANS_DEEPSLATE_LAPIS_ORE = ore("trans_deepslate_lapis_ore", UniformInt.of(2, 5), Blocks.DEEPSLATE_LAPIS_ORE);
	public static final Block TRANS_DIAMOND_ORE = ore("trans_diamond_ore", UniformInt.of(3, 7), Blocks.DIAMOND_ORE);
	public static final Block TRANS_DEEPSLATE_DIAMOND_ORE = ore("trans_deepslate_diamond_ore", UniformInt.of(3, 7), Blocks.DEEPSLATE_DIAMOND_ORE);
	public static final Block TRANS_EMERALD_ORE = ore("trans_emerald_ore", UniformInt.of(3, 7), Blocks.EMERALD_ORE);
	public static final Block TRANS_DEEPSLATE_EMERALD_ORE = ore("trans_deepslate_emerald_ore", UniformInt.of(3, 7), Blocks.DEEPSLATE_EMERALD_ORE);

	// ---------------------------------------------------------------- crystals
	public static final Block TRANS_CRYSTAL_ORE = register("trans_crystal_ore",
			properties -> new DropExperienceBlock(UniformInt.of(3, 7), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE).lightLevel(state -> 3));
	public static final Block TRANS_DEEPSLATE_CRYSTAL_ORE = register("trans_deepslate_crystal_ore",
			properties -> new DropExperienceBlock(UniformInt.of(3, 7), properties),
			BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE).lightLevel(state -> 3));
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
	public static final Block POTTED_PRIDE_BLOSSOM = potted("potted_pride_blossom", PRIDE_BLOSSOM);
	public static final Block TRANS_TULIP = flower("trans_tulip", MobEffects.SPEED, 5.0F);
	public static final Block POTTED_TRANS_TULIP = potted("potted_trans_tulip", TRANS_TULIP);
	public static final Block PEARL_DAISY = flower("pearl_daisy", MobEffects.REGENERATION, 8.0F);
	public static final Block POTTED_PEARL_DAISY = potted("potted_pearl_daisy", PEARL_DAISY);
	public static final Block SKY_BELL = flower("sky_bell", MobEffects.SLOW_FALLING, 8.0F);
	public static final Block POTTED_SKY_BELL = potted("potted_sky_bell", SKY_BELL);
	public static final Block FLAG_LILY = flower("flag_lily", MobEffects.LUCK, 10.0F);
	public static final Block POTTED_FLAG_LILY = potted("potted_flag_lily", FLAG_LILY);
	public static final Block LAVENDER_PUFF = flower("lavender_puff", MobEffects.NIGHT_VISION, 8.0F);
	public static final Block POTTED_LAVENDER_PUFF = potted("potted_lavender_puff", LAVENDER_PUFF);
	public static final Block TRANS_ORCHID = flower("trans_orchid", MobEffects.JUMP_BOOST, 6.0F);
	public static final Block POTTED_TRANS_ORCHID = potted("potted_trans_orchid", TRANS_ORCHID);
	public static final Block HEART_BLOOM = flower("heart_bloom", MobEffects.ABSORPTION, 8.0F);
	public static final Block POTTED_HEART_BLOOM = potted("potted_heart_bloom", HEART_BLOOM);
	public static final Block PRIDE_PEONY = register("pride_peony", TallFlowerBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.PEONY));
	public static final Block TRANS_PETALS = register("trans_petals", FlowerBedBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.PINK_PETALS));

	// ---------------------------------------------------------------- glass, wool and light
	/** Clear glass with a pink and blue frame; what trans sand smelts into. */
	public static final Block TRANS_GLASS = register("trans_glass", TransparentBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS));
	public static final Block TRANS_GLASS_PANE = register("trans_glass_pane", IronBarsBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS_PANE));
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

	private static Block flower(String name, Holder<MobEffect> stewEffect, float stewSeconds) {
		return register(name, properties -> new FlowerBlock(stewEffect, stewSeconds, properties),
				BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY));
	}

	private static Block potted(String name, Block plant) {
		return registerWithoutItem(name, properties -> new FlowerPotBlock(plant, properties),
				BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_POPPY));
	}

	private static Block copy(String name, Block copyFrom) {
		return register(name, Block::new, BlockBehaviour.Properties.ofFullCopy(copyFrom));
	}

	private static Block ore(String name, IntProvider experience, Block copyFrom) {
		return register(name, properties -> new DropExperienceBlock(experience, properties), BlockBehaviour.Properties.ofFullCopy(copyFrom));
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
