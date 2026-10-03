package dev.goober.transdimension.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import dev.goober.transdimension.TransDimension;

/**
 * Retextures every mob while you are in the Trans Realm: passive mobs, hostile mobs and villagers.
 *
 * <p>Instead of shipping copies of Mojang's textures, each entity texture is read from your own game
 * files the first time it is drawn in the realm and gradient-mapped onto the trans flag palette
 * (shadows deep blue, mid-tones pink, highlights white), which keeps eyes, faces and details readable.
 */
public final class TransRecolor {
	private static final Map<Identifier, AbstractTexture> CACHE = new HashMap<>();
	private static final Map<Identifier, Boolean> FAILED = new HashMap<>();

	/** Gradient stops: position (0..1 brightness) and RGB. */
	private static final float[] STOPS = {0.0F, 0.28F, 0.48F, 0.68F, 0.86F, 1.0F};
	private static final int[] COLOURS = {0x22305E, 0x3E9FD8, 0x5BCEFA, 0xF5A9B8, 0xFBD3DD, 0xFFFFFF};

	private static volatile boolean active = false;
	private static Thread renderThread;

	private TransRecolor() {
	}

	public static void captureRenderThread() {
		renderThread = Thread.currentThread();
	}

	public static void setActive(boolean value) {
		active = value;
	}

	public static void clearCache() {
		CACHE.clear();
		FAILED.clear();
	}

	/** Returns the recoloured texture to use instead of {@code id}, or null to keep the original. */
	@Nullable
	public static AbstractTexture swap(Identifier id) {
		if (!active || Thread.currentThread() != renderThread) {
			return null;
		}
		String path = id.getPath();
		if (!path.startsWith("textures/entity/") || id.getNamespace().equals(TransDimension.MOD_ID)) {
			return null;
		}
		// Leave players' skins and worn armour alone.
		if (path.startsWith("textures/entity/player/") || path.startsWith("textures/entity/equipment/")) {
			return null;
		}

		AbstractTexture cached = CACHE.get(id);
		if (cached != null || FAILED.containsKey(id)) {
			return cached;
		}

		AbstractTexture created = create(id);
		if (created == null) {
			FAILED.put(id, Boolean.TRUE);
		} else {
			CACHE.put(id, created);
		}
		return created;
	}

	@Nullable
	private static AbstractTexture create(Identifier id) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream stream = resource.get().open()) {
			NativeImage image = NativeImage.read(stream);
			recolour(image);
			Identifier newId = TransDimension.id("recoloured/" + id.getNamespace() + "/" + id.getPath());
			DynamicTexture texture = new DynamicTexture(newId::toString, image);
			minecraft.getTextureManager().register(newId, texture);
			return texture;
		} catch (Exception exception) {
			TransDimension.LOGGER.debug("Could not recolour {}", id, exception);
			return null;
		}
	}

	private static void recolour(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				float luminance = (0.299F * r + 0.587F * g + 0.114F * b) / 255.0F;
				float t = Math.min(1.0F, Math.max(0.0F, (luminance - 0.5F) * 1.15F + 0.5F));
				int mapped = gradient(t);

				// Keep 15% of the original colour so mobs stay recognisable.
				int nr = Math.round(((mapped >> 16) & 0xFF) * 0.85F + r * 0.15F);
				int ng = Math.round(((mapped >> 8) & 0xFF) * 0.85F + g * 0.15F);
				int nb = Math.round((mapped & 0xFF) * 0.85F + b * 0.15F);
				image.setPixel(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
			}
		}
	}

	private static int gradient(float t) {
		for (int i = 1; i < STOPS.length; i++) {
			if (t <= STOPS[i]) {
				float local = (t - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
				return lerp(COLOURS[i - 1], COLOURS[i], local);
			}
		}
		return COLOURS[COLOURS.length - 1];
	}

	private static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
