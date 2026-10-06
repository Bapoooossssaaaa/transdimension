package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import dev.goober.transdimension.TransDimension;

/**
 * Pink twins of vanilla's sculk particles (textures by tools/generate_textures.py, recoloured from vanilla's).
 *
 * <p>Pink sculk catalysts send up {@link #PINK_SCULK_SOUL}s, and the pink deep dark floats with them. The other three are
 * never spawned themselves: they only give the client their pink sprites, which it uses to draw vanilla's shrieks,
 * vibrations and sonic booms while you're in the Trans Realm (client/PinkSculkParticles).
 */
public final class ModParticles {
	public static final SimpleParticleType PINK_SCULK_SOUL = register("pink_sculk_soul");
	public static final SimpleParticleType PINK_SHRIEK = register("pink_shriek");
	public static final SimpleParticleType PINK_VIBRATION = register("pink_vibration");
	public static final SimpleParticleType PINK_SONIC_BOOM = register("pink_sonic_boom");
	/** The ritual candles' pink flames. */
	public static final SimpleParticleType PINK_FLAME = register("pink_flame");
	/** A twinkling prismatic star: the ritual's and the Sky Portal's own sparkle. */
	public static final SimpleParticleType PRISM_SPARK = register("prism_spark");
	/** A golden mote of holy light (angels and holy water). */
	public static final SimpleParticleType HOLY_SPARK = register("holy_spark");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, TransDimension.id(name), FabricParticleTypes.simple());
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
