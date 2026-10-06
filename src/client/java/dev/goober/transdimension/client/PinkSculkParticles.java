package dev.goober.transdimension.client;

import java.util.function.Function;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ShriekParticle;
import net.minecraft.client.particle.SonicBoomParticle;
import net.minecraft.client.particle.SoulParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.VibrationSignalParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModParticles;

/**
 * Pink sculk effects. Inside the Trans Realm, vanilla's shriek rings, vibration signals and sonic booms are drawn with
 * pink sprites (from the mod's pink twin particle types, see {@link ModParticles}); everywhere else they look as usual.
 * The pink deep dark's sensors and shriekers are vanilla's blocks underneath, so this is what turns their effects pink.
 *
 * <p>Each vanilla provider is replaced (through Fabric's registry) by one that hands the particle to vanilla's own
 * provider class, built either with vanilla's sprites or with the pink ones.
 */
public final class PinkSculkParticles {
	private PinkSculkParticles() {
	}

	public static void register() {
		ParticleProviderRegistry.getInstance().register(ModParticles.PINK_SCULK_SOUL, SoulParticle.EmissiveProvider::new);
		pinkInRealm(ParticleTypes.SHRIEK, ShriekParticle.Provider::new, ModParticles.PINK_SHRIEK);
		pinkInRealm(ParticleTypes.VIBRATION, VibrationSignalParticle.Provider::new, ModParticles.PINK_VIBRATION);
		pinkInRealm(ParticleTypes.SONIC_BOOM, SonicBoomParticle.Provider::new, ModParticles.PINK_SONIC_BOOM);
	}

	@SuppressWarnings("unchecked")
	private static <T extends ParticleOptions> void pinkInRealm(ParticleType<T> type, Function<SpriteSet, ParticleProvider<T>> factory,
			SimpleParticleType pinkTwin) {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		ParticleProvider<T>[] pink = new ParticleProvider[1];
		// The twin is never spawned; registering it only loads its pink sprites, which are kept for the real provider.
		registry.register(pinkTwin, (ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> {
			pink[0] = factory.apply(sprites);
			return (options, level, x, y, z, xd, yd, zd, random) -> null;
		});
		registry.register(type, (ParticleProviderRegistry.PendingParticleProvider<T>) sprites -> {
			ParticleProvider<T> normal = factory.apply(sprites);
			return (options, level, x, y, z, xd, yd, zd, random) -> {
				ParticleProvider<T> provider = pink[0] != null && level.dimension().equals(TransDimension.TRANS_REALM) ? pink[0] : normal;
				return provider.createParticle(options, level, x, y, z, xd, yd, zd, random);
			};
		});
	}
}
