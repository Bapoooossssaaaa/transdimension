package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * A small trans-themed village, built block by block:
 * a plaza with a Trans Water fountain and a giant trans flag, a bakery (Pride Ovens, trans cakes,
 * a chest of treats) and three cottages with trans-flag roofs. Villagers spawn unemployed and claim
 * the job sites themselves, so the bakery ends up staffed by Trans Bakers.
 *
 * <p>Features may only write within one chunk of the chunk being decorated, so the whole village
 * stays inside a 45x45 square centred on the chunk centre.
 */
public class TransVillageFeature extends Feature<NoneFeatureConfiguration> {
	private static final int SEA_LEVEL = 63;
	private static final int RADIUS = 22;
	private static final int FLAGS = 2; // Block.UPDATE_CLIENTS

	private static final ResourceKey<LootTable> BAKERY_LOOT = ResourceKey.create(Registries.LOOT_TABLE, TransDimension.id("chests/trans_bakery"));
	private static final ResourceKey<LootTable> HOUSE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, TransDimension.id("chests/trans_house"));

	public TransVillageFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		int cx = SectionPos.blockToSectionCoord(origin.getX()) * 16 + 8;
		int cz = SectionPos.blockToSectionCoord(origin.getZ()) * 16 + 8;

		// Only build on fairly flat dry land.
		int centre = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
		int min = centre;
		int max = centre;
		for (int dx = -20; dx <= 20; dx += 10) {
			for (int dz = -20; dz <= 20; dz += 10) {
				int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + dx, cz + dz);
				if (h <= SEA_LEVEL + 1) {
					return false;
				}
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
		}
		if (max - min > 9) {
			return false;
		}
		int baseY = centre - 1;
		if (!level.getFluidState(new BlockPos(cx, baseY, cz)).isEmpty()) {
			return false;
		}

		new Builder(level, context.random(), cx, baseY, cz).build();
		return true;
	}

	private static final class Builder {
		private final WorldGenLevel level;
		private final RandomSource random;
		private final int cx;
		private final int baseY;
		private final int cz;

		private final BlockState air = Blocks.AIR.defaultBlockState();
		private final BlockState grass = ModBlocks.TRANS_GRASS_BLOCK.defaultBlockState();
		private final BlockState dirt = ModBlocks.TRANS_DIRT.defaultBlockState();
		private final BlockState bricks = ModBlocks.TRANS_STONE_BRICKS.defaultBlockState();
		private final BlockState cobble = ModBlocks.TRANS_COBBLESTONE.defaultBlockState();
		private final BlockState planks = ModBlocks.TRANS_PLANKS.defaultBlockState();
		private final BlockState log = with(ModBlocks.TRANS_LOG.defaultBlockState(), BlockStateProperties.AXIS, Direction.Axis.Y);
		private final BlockState crystal = ModBlocks.TRANS_CRYSTAL_BLOCK.defaultBlockState();
		private final BlockState water = ModBlocks.TRANS_WATER.defaultBlockState();
		private final BlockState blossom = ModBlocks.PRIDE_BLOSSOM.defaultBlockState();
		private final BlockState cake = ModBlocks.TRANS_CAKE.defaultBlockState();
		private final BlockState oven = ModBlocks.PRIDE_OVEN.defaultBlockState();

		// Vanilla blocks are looked up by id: 26.2 regrouped the dyed-block constants, but ids never change.
		private final BlockState pinkWool = vanilla("pink_wool");
		private final BlockState blueWool = vanilla("light_blue_wool");
		private final BlockState whiteWool = vanilla("white_wool");
		private final BlockState pinkConcrete = vanilla("pink_concrete");
		private final BlockState blueConcrete = vanilla("light_blue_concrete");
		private final BlockState whiteConcrete = vanilla("white_concrete");
		private final BlockState pinkGlass = vanilla("pink_stained_glass");
		private final BlockState blueGlass = vanilla("light_blue_stained_glass");
		private final BlockState whiteGlass = vanilla("white_stained_glass");
		private final BlockState pinkCarpet = vanilla("pink_carpet");
		private final BlockState blueCarpet = vanilla("light_blue_carpet");
		private final BlockState lantern = vanilla("lantern");
		private final BlockState bell = vanilla("bell");
		private final BlockState chest = vanilla("chest");
		private final BlockState door = vanilla("cherry_door");
		private final BlockState pinkBed = vanilla("pink_bed");
		private final BlockState blueBed = vanilla("light_blue_bed");
		private final BlockState whiteBed = vanilla("white_bed");
		private final BlockState composter = vanilla("composter");
		private final BlockState lectern = vanilla("lectern");
		private final BlockState loom = vanilla("loom");

		Builder(WorldGenLevel level, RandomSource random, int cx, int baseY, int cz) {
			this.level = level;
			this.random = random;
			this.cx = cx;
			this.baseY = baseY;
			this.cz = cz;
		}

		void build() {
			prepareGround();
			roadsAndPlaza();
			fountain();
			prideFlag(-6, 5);
			set(5, 1, 5, bell);
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					lampPost(6 * sx, 6 * sz, 3);
				}
			}
			lampPost(20, 2, 2);
			lampPost(-20, -2, 2);
			lampPost(2, -20, 2);
			lampPost(-2, 20, 2);
			flowerStrips();

			BlockState[] bakeryStripes = {blueConcrete, pinkConcrete, whiteConcrete, pinkConcrete, blueConcrete};
			building(8, -18, 18, -10, 5, Direction.SOUTH, bakeryStripes, whiteGlass);
			bakeryInterior(8, -18, 18, -10);

			building(-16, -16, -10, -10, 4, Direction.SOUTH, new BlockState[] {pinkConcrete}, blueGlass);
			cottageInterior(-16, -16, -10, -10, Direction.SOUTH, blueBed, composter);

			building(-16, 10, -10, 16, 4, Direction.NORTH, new BlockState[] {blueConcrete}, pinkGlass);
			cottageInterior(-16, 10, -10, 16, Direction.NORTH, pinkBed, lectern);

			building(10, 10, 16, 16, 4, Direction.NORTH, new BlockState[] {whiteConcrete}, pinkGlass);
			cottageInterior(10, 10, 16, 16, Direction.NORTH, whiteBed, loom);

			spawnVillager(4, 1, -4);
		}

		// ------------------------------------------------------------------ terrain

		private void prepareGround() {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					if (dx * dx + dz * dz > (RADIUS + 1) * (RADIUS + 1)) {
						continue;
					}
					int x = cx + dx;
					int z = cz + dz;
					int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
					if (top > baseY) {
						for (int y = baseY + 1; y <= top; y++) {
							setAbs(x, y, z, air);
						}
					} else {
						for (int y = Math.max(top, baseY - 8); y < baseY; y++) {
							setAbs(x, y, z, dirt);
						}
					}
					setAbs(x, baseY, z, grass);
				}
			}
		}

		private void roadsAndPlaza() {
			for (int i = -RADIUS; i <= RADIUS; i++) {
				for (int w = -1; w <= 1; w++) {
					set(i, 0, w, roadBlock());
					set(w, 0, i, roadBlock());
				}
			}
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					int ring = Math.max(Math.abs(dx), Math.abs(dz));
					if (ring == 5) {
						set(dx, 0, dz, (dx + dz) % 2 == 0 ? pinkConcrete : blueConcrete);
					} else {
						set(dx, 0, dz, bricks);
					}
				}
			}
		}

		private BlockState roadBlock() {
			return random.nextInt(10) < 7 ? bricks : cobble;
		}

		private void fountain() {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
						set(dx, 0, dz, bricks);
						set(dx, 1, dz, bricks);
					} else {
						set(dx, -1, dz, bricks);
						set(dx, 0, dz, water);
					}
				}
			}
			for (int dy = 0; dy <= 3; dy++) {
				set(0, dy, 0, crystal);
			}
			set(0, 4, 0, water);
			set(-3, 2, -3, lantern);
			set(3, 2, -3, lantern);
			set(-3, 2, 3, lantern);
			set(3, 2, 3, lantern);
		}

		/** A giant trans flag on a pole: light blue, pink, white, pink, light blue. */
		private void prideFlag(int poleX, int poleZ) {
			for (int dy = 1; dy <= 10; dy++) {
				set(poleX, dy, poleZ, log);
			}
			BlockState[] stripes = {blueWool, pinkWool, whiteWool, pinkWool, blueWool};
			for (int row = 0; row < stripes.length; row++) {
				for (int i = 1; i <= 5; i++) {
					set(poleX + i, 10 - row, poleZ, stripes[row]);
				}
			}
		}

		private void lampPost(int x, int z, int height) {
			for (int dy = 1; dy <= height; dy++) {
				set(x, dy, z, log);
			}
			set(x, height + 1, z, lantern);
		}

		private void flowerStrips() {
			for (int i = 8; i <= 20; i++) {
				if (i == 13) {
					continue; // leave the door paths free
				}
				for (int sign = -1; sign <= 1; sign += 2) {
					if (random.nextInt(3) != 0) {
						set(i * sign, 1, 3, blossom);
					}
					if (random.nextInt(3) != 0) {
						set(i * sign, 1, -3, blossom);
					}
					if (random.nextInt(3) != 0) {
						set(3, 1, i * sign, blossom);
					}
					if (random.nextInt(3) != 0) {
						set(-3, 1, i * sign, blossom);
					}
				}
			}
		}

		// ------------------------------------------------------------------ buildings

		/**
		 * Walls (one block state per row, repeating), log corner pillars, glass windows, a cherry door,
		 * a path to the main road and a trans-flag roof.
		 */
		private void building(int x1, int z1, int x2, int z2, int height, Direction doorSide, BlockState[] wallRows, BlockState glass) {
			for (int x = x1; x <= x2; x++) {
				for (int z = z1; z <= z2; z++) {
					set(x, 0, z, planks);
					boolean edgeX = x == x1 || x == x2;
					boolean edgeZ = z == z1 || z == z2;
					for (int y = 1; y <= height; y++) {
						if (edgeX && edgeZ) {
							set(x, y, z, log);
						} else if (edgeX || edgeZ) {
							set(x, y, z, wallRows[(y - 1) % wallRows.length]);
						} else {
							set(x, y, z, air);
						}
					}
				}
			}

			int midX = (x1 + x2) / 2;
			int midZ = (z1 + z2) / 2;
			for (int y = 2; y <= 3; y++) {
				set(x1, y, midZ, glass);
				set(x2, y, midZ, glass);
				set(midX - 2, y, z1, glass);
				set(midX + 2, y, z1, glass);
				set(midX - 2, y, z2, glass);
				set(midX + 2, y, z2, glass);
			}

			int doorZ = doorSide == Direction.NORTH ? z1 : z2;
			BlockState lower = with(with(door, BlockStateProperties.HORIZONTAL_FACING, doorSide.getOpposite()),
					BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
			BlockState upper = with(lower, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
			set(midX, 1, doorZ, lower);
			set(midX, 2, doorZ, upper);

			int step = doorSide.getStepZ();
			for (int z = doorZ + step; z * step < -1; z += step) {
				set(midX, 0, z, roadBlock());
				set(midX, 1, z, air);
				set(midX, 2, z, air);
			}

			flagRoof(x1, z1, x2, z2, height + 1);
		}

		/** Stepped roof whose layers, bottom to top, are the trans flag. */
		private void flagRoof(int x1, int z1, int x2, int z2, int y0) {
			BlockState[] layers = {blueWool, pinkWool, whiteWool, pinkWool, blueWool};
			for (int layer = 0; layer < layers.length; layer++) {
				int ax1 = x1 - 1 + layer;
				int ax2 = x2 + 1 - layer;
				int az1 = z1 - 1 + layer;
				int az2 = z2 + 1 - layer;
				if (ax1 > ax2 || az1 > az2) {
					break;
				}
				for (int x = ax1; x <= ax2; x++) {
					for (int z = az1; z <= az2; z++) {
						set(x, y0 + layer, z, layers[layer]);
					}
				}
			}
		}

		private void bakeryInterior(int x1, int z1, int x2, int z2) {
			int backZ = z1 + 1;   // door is on the south wall, so the back wall is z1
			int midX = (x1 + x2) / 2;
			int midZ = (z1 + z2) / 2;

			// Two Pride Ovens: the job sites of the Trans Bakers.
			set(x1 + 2, 1, backZ, oven);
			set(x2 - 2, 1, backZ, oven);

			// Display counter full of trans cakes, with a gap in front of the door.
			for (int x = x1 + 2; x <= x2 - 2; x++) {
				if (Math.abs(x - midX) <= 1) {
					continue;
				}
				set(x, 1, midZ, planks);
				if (random.nextInt(3) != 0) {
					set(x, 2, midZ, cake);
				}
			}

			chest(x1 + 1, 1, backZ, Direction.SOUTH, BAKERY_LOOT);
			chest(x2 - 1, 1, backZ, Direction.SOUTH, BAKERY_LOOT);
			set(x1 + 1, 1, z2 - 1, lantern);
			set(x2 - 1, 1, z2 - 1, lantern);
			set(midX, 1, backZ, pinkCarpet);
			set(midX, 1, midZ + 2, blueCarpet);

			spawnVillager(midX - 2, 1, backZ + 1);
			spawnVillager(midX + 2, 1, backZ + 1);
		}

		private void cottageInterior(int x1, int z1, int x2, int z2, Direction doorSide, BlockState bed, BlockState workstation) {
			int inward = doorSide == Direction.SOUTH ? 1 : -1;  // from the back wall towards the door
			int backZ = doorSide == Direction.SOUTH ? z1 : z2;
			Direction toBack = doorSide.getOpposite();

			int headZ = backZ + inward;
			int footZ = headZ + inward;
			set(x1 + 1, 1, headZ, with(with(bed, BlockStateProperties.HORIZONTAL_FACING, toBack), BlockStateProperties.BED_PART, BedPart.HEAD));
			set(x1 + 1, 1, footZ, with(with(bed, BlockStateProperties.HORIZONTAL_FACING, toBack), BlockStateProperties.BED_PART, BedPart.FOOT));

			chest(x2 - 1, 1, headZ, doorSide, HOUSE_LOOT);
			set(x2 - 1, 1, footZ, workstation);
			set(x1 + 1, 1, footZ + inward * 2, lantern);

			int midX = (x1 + x2) / 2;
			int midZ = (z1 + z2) / 2;
			set(midX, 1, midZ, (midX + midZ) % 2 == 0 ? pinkCarpet : blueCarpet);
			spawnVillager(midX, 1, midZ);
		}

		private void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable) {
			set(x, y, z, with(chest, BlockStateProperties.HORIZONTAL_FACING, facing));
			BlockPos pos = new BlockPos(cx + x, baseY + y, cz + z);
			if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
				container.setLootTable(lootTable, random.nextLong());
			}
		}

		private void spawnVillager(int x, int y, int z) {
			Villager villager = new Villager(EntityTypes.VILLAGER, level.getLevel());
			villager.snapTo(new Vec3(cx + x + 0.5, baseY + y, cz + z + 0.5));
			villager.setPersistenceRequired();
			level.addFreshEntity(villager);
		}

		// ------------------------------------------------------------------ helpers

		private void set(int dx, int dy, int dz, BlockState state) {
			level.setBlock(new BlockPos(cx + dx, baseY + dy, cz + dz), state, FLAGS);
		}

		private void setAbs(int x, int y, int z, BlockState state) {
			level.setBlock(new BlockPos(x, y, z), state, FLAGS);
		}

		private static BlockState vanilla(String name) {
			return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name)).defaultBlockState();
		}

		private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
			return state.hasProperty(property) ? state.setValue(property, value) : state;
		}
	}
}
