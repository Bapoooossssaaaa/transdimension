package dev.goober.transdimension.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;

import dev.goober.transdimension.TransDimension;

/**
 * Heart-shaped clouds, only inside the Trans Realm.
 *
 * <p>Vanilla builds its cloud shapes from textures/environment/clouds.png when resources load.
 * When you enter the realm we re-run just the cloud renderer's own loading step, but hand it a
 * resource view where clouds.png points at our heart_clouds.png; leaving the realm loads the
 * normal clouds again. This goes through reflection on purpose: if a future update changes the
 * cloud renderer, the hearts are simply skipped (with a log line) instead of crashing the game.
 */
public final class HeartClouds {
	private static final Identifier VANILLA_CLOUDS = Identifier.withDefaultNamespace("textures/environment/clouds.png");
	private static final Identifier HEART_CLOUDS = TransDimension.id("textures/environment/heart_clouds.png");

	private static boolean heartsApplied = false;
	private static boolean disabled = false;

	private HeartClouds() {
	}

	public static void tick(Minecraft client, boolean inRealm) {
		if (disabled || client.level == null) {
			return;
		}
		if (inRealm != heartsApplied) {
			reloadClouds(client, inRealm);
		}
	}

	/** Called after a resource reload, which always restores the vanilla clouds. */
	public static void onResourcesReloaded() {
		heartsApplied = false;
	}

	private static void reloadClouds(Minecraft client, boolean hearts) {
		try {
			Object cloudRenderer = client.levelRenderer.cloudRenderer();
			ResourceManager realManager = client.getResourceManager();
			ResourceManager source = hearts ? redirectClouds(realManager) : realManager;

			Method prepare = findMethod(cloudRenderer.getClass(), "prepare", 2);
			Method apply = findMethod(cloudRenderer.getClass(), "apply", 3);
			Object prepared = prepare.invoke(cloudRenderer, source, Profiler.get());
			apply.invoke(cloudRenderer, prepared, realManager, Profiler.get());

			heartsApplied = hearts;
		} catch (Throwable throwable) {
			disabled = true;
			TransDimension.LOGGER.warn("Could not swap in the heart clouds, keeping the normal ones", throwable);
		}
	}

	private static ResourceManager redirectClouds(ResourceManager real) {
		return (ResourceManager) Proxy.newProxyInstance(
				ResourceManager.class.getClassLoader(),
				new Class<?>[] {ResourceManager.class},
				(proxy, method, args) -> {
					if (args != null) {
						for (int i = 0; i < args.length; i++) {
							if (VANILLA_CLOUDS.equals(args[i])) {
								args[i] = HEART_CLOUDS;
							}
						}
					}
					try {
						return method.invoke(real, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				});
	}

	private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			Method bridge = null;
			for (Method method : current.getDeclaredMethods()) {
				if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) {
					continue;
				}
				if (!method.isBridge()) {
					method.setAccessible(true);
					return method;
				}
				bridge = method;
			}
			if (bridge != null) {
				bridge.setAccessible(true);
				return bridge;
			}
		}
		throw new NoSuchMethodException(type.getName() + "#" + name);
	}
}
