package dev.goober.transdimension.world;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * The Trans Realm's dungeons: vanilla's monster room (same rules for where it fits: in solid rock, opening onto a cave
 * in one to five places), built of trans cobblestone and stone bricks over a mossy trans floor, with a monster spawner,
 * cobwebs, a few unlit pink and blue candles and up to two chests of {@code chests/trans_dungeon} loot.
 */
public class TransDungeonFeature extends Feature<NoneFeatureConfiguration> {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, TransDimension.id("chests/trans_dungeon"));
	/** The spawner's mob (26.2 keeps vanilla's entity types in EntityTypes; EntityType is only the class). */
	private static final EntityType<?>[] MOBS = {EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SPIDER};

	public TransDungeonFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	private static boolean solid(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		int rx = random.nextInt(2) + 2;
		int rz = random.nextInt(2) + 2;
		int x0 = -rx - 1;
		int x1 = rx + 1;
		int z0 = -rz - 1;
		int z1 = rz + 1;

		// Like vanilla: solid floor and ceiling all the way, and one to five openings onto air at floor level.
		int openings = 0;
		for (int dx = x0; dx <= x1; dx++) {
			for (int dy = -1; dy <= 4; dy++) {
				for (int dz = z0; dz <= z1; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					boolean isSolid = solid(level.getBlockState(pos));
					if ((dy == -1 || dy == 4) && !isSolid) {
						return false;
					}
					if ((dx == x0 || dx == x1 || dz == z0 || dz == z1) && dy == 0 && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
						openings++;
					}
				}
			}
		}
		if (openings < 1 || openings > 5) {
			return false;
		}

		for (int dx = x0; dx <= x1; dx++) {
			for (int dy = 3; dy >= -1; dy--) {
				for (int dz = z0; dz <= z1; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					BlockState here = level.getBlockState(pos);
					if (here.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
						continue;
					}
					boolean wall = dx == x0 || dx == x1 || dy == -1 || dz == z0 || dz == z1;
					if (!wall) {
						if (!here.is(Blocks.CHEST) && !here.is(Blocks.SPAWNER)) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					} else if (pos.getY() >= level.getMinY() && !solid(level.getBlockState(pos.below()))) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (solid(here) && !here.is(Blocks.CHEST)) {
						level.setBlock(pos, this.wallBlock(dy, random), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		// Up to two chests, each against a wall.
		for (int chest = 0; chest < 2; chest++) {
			for (int attempt = 0; attempt < 3; attempt++) {
				BlockPos pos = origin.offset(random.nextInt(rx * 2 + 1) - rx, 0, random.nextInt(rz * 2 + 1) - rz);
				if (!level.isEmptyBlock(pos)) {
					continue;
				}
				Direction wall = this.wallBeside(level, pos);
				if (wall != null) {
					level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, wall.getOpposite()), Block.UPDATE_CLIENTS);
					RandomizableContainer.setBlockEntityLootTable(level, random, pos, LOOT);
					break;
				}
			}
		}

		// Cobwebs in the top corners, a few unlit candles along the walls.
		for (BlockPos corner : new BlockPos[] {origin.offset(x0 + 1, 3, z0 + 1), origin.offset(x1 - 1, 3, z1 - 1),
				origin.offset(x0 + 1, 3, z1 - 1), origin.offset(x1 - 1, 3, z0 + 1)}) {
			if (random.nextInt(3) != 0 && level.isEmptyBlock(corner)) {
				level.setBlock(corner, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int candle = 0; candle < 3; candle++) {
			BlockPos pos = origin.offset(random.nextInt(rx * 2 + 1) - rx, 0, random.nextInt(rz * 2 + 1) - rz);
			if (level.isEmptyBlock(pos) && solid(level.getBlockState(pos.below())) && this.wallBeside(level, pos) != null) {
				Block block = random.nextBoolean() ? Blocks.DYED_CANDLE.pink() : Blocks.DYED_CANDLE.lightBlue();
				level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		level.setBlock(origin, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(origin) instanceof SpawnerBlockEntity spawner) {
			spawner.setEntityId(MOBS[random.nextInt(MOBS.length)], random);
		}
		return true;
	}

	/** Walls and ceiling of trans cobblestone and bricks; the floor is mostly moss. */
	private BlockState wallBlock(int dy, RandomSource random) {
		if (dy == -1) {
			return (random.nextInt(3) == 0 ? ModBlocks.TRANS_COBBLESTONE : ModBlocks.TRANS_MOSS_BLOCK).defaultBlockState();
		}
		int roll = random.nextInt(10);
		if (roll < 6) {
			return ModBlocks.TRANS_COBBLESTONE.defaultBlockState();
		}
		return (roll < 9 ? ModBlocks.TRANS_STONE_BRICKS : ModBlocks.CRACKED_TRANS_STONE_BRICKS).defaultBlockState();
	}

	/** The direction of a solid wall right beside this spot, if there's exactly one. */
	@Nullable
	private Direction wallBeside(WorldGenLevel level, BlockPos pos) {
		Direction found = null;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (solid(level.getBlockState(pos.relative(direction)))) {
				if (found != null) {
					return null;
				}
				found = direction;
			}
		}
		return found;
	}
}
