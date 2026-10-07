package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * The Cloud Realm's heavenly ruins: a white spiral tower with a stair winding round the outside up to a golden-domed
 * lookout, a temple of white columns round a pool of holy water, or a round shrine under a golden dome with a holy water
 * fountain (tools/generate_heavenly_ruins.py writes their templates and their cloud chests' loot).
 *
 * <p>They're a feature rather than a structure because the realm's islands float: a ruin is only set down where there's
 * an island under it, roughly level across its whole width. Its floor (the template's bottom layer) takes the place of
 * the ground, and wherever the ground dips under the floor, it's shored up with cloudcite, so the ruin sits on the island
 * rather than overhanging it. It's centred on its chunk, so it never builds beyond the chunks a feature may touch, and
 * each is turned a random way.
 */
public class HeavenlyRuinFeature extends Feature<NoneFeatureConfiguration> {
	private static final Identifier[] TEMPLATES = {TransDimension.id("heavenly_ruin/spiral_tower"), TransDimension.id("heavenly_ruin/temple"),
			TransDimension.id("heavenly_ruin/sky_shrine"), TransDimension.id("heavenly_ruin/ruined_church")};
	/** How deep each template's floor sits: its bottom layer, in place of the top of the ground. */
	private static final int FOUNDATION = 1;
	/** How far down the floor is shored up where the ground falls away under it. */
	private static final int SHORE = 8;
	/** How much the ground may rise or fall under a ruin's corners. */
	private static final int UNEVEN = 3;

	public HeavenlyRuinFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		ServerLevel server = level.getLevel();
		StructureTemplate template = server.getStructureManager().get(TEMPLATES[random.nextInt(TEMPLATES.length)]).orElse(null);
		if (template == null) {
			return false;
		}
		Vec3i size = template.getSize();
		int cx = (context.origin().getX() & ~15) + 8;
		int cz = (context.origin().getZ() & ~15) + 8;
		int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
		if (ground <= level.getMinY() + 8 || ground + size.getY() > level.getMaxY()) {
			return false;
		}
		int x0 = cx - size.getX() / 2;
		int z0 = cz - size.getZ() / 2;
		int[][] corners = {{x0, z0}, {x0 + size.getX() - 1, z0}, {x0, z0 + size.getZ() - 1}, {x0 + size.getX() - 1, z0 + size.getZ() - 1}};
		for (int[] corner : corners) {
			if (Math.abs(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, corner[0], corner[1]) - ground) > UNEVEN) {
				return false;
			}
		}
		BlockPos at = new BlockPos(x0, ground - FOUNDATION, z0);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.getRandom(random))
				.setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
		template.placeInWorld(level, at, at, settings, random, Block.UPDATE_CLIENTS);
		// Shore the floor up wherever the ground falls away under it.
		BoundingBox box = template.getBoundingBox(settings, at);
		BlockState fill = ModBlocks.CLOUDCITE.defaultBlockState();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				if (level.getBlockState(cursor.set(x, at.getY(), z)).isAir()) {
					continue;
				}
				for (int y = at.getY() - 1; y >= at.getY() - SHORE && level.getBlockState(cursor.set(x, y, z)).canBeReplaced(); y--) {
					level.setBlock(cursor, fill, Block.UPDATE_CLIENTS);
				}
			}
		}
		return true;
	}
}
