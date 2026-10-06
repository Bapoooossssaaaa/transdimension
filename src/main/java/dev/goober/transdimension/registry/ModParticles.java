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

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, TransDimension.id(name), FabricParticleTypes.simple());
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
