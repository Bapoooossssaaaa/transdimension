package dev.goober.transdimension.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModSounds;

/**
 * In the Trans Realm, every sculk block and every warden is pink, so any sculk or warden sound the server sends (a sensor's
 * click, a shrieker's shriek, a warden's roar or heartbeat-quick anger) is played as its soft pink stand-in instead
 * ({@link ModSounds#soften}), at no more than full volume. The server already swaps most of them; this catches the rest.
 * With {@code require = 0}, if the method ever changes, sounds are simply vanilla's.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true, require = 0)
	private void transdimension$softSculk(ClientboundSoundPacket packet, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		// The packet is first handed in on the network thread, and handled again on the game's own thread.
		if (!minecraft.isSameThread() || minecraft.level == null || !TransDimension.TRANS_REALM.equals(minecraft.level.dimension())) {
			return;
		}
		SoundEvent soft = ModSounds.soften(packet.getSound().value());
		if (soft != null) {
			ci.cancel();
			minecraft.level.playLocalSound(packet.getX(), packet.getY(), packet.getZ(), soft, packet.getSource(), Math.min(packet.getVolume(), 1.0F),
					packet.getPitch(), false);
		}
	}
}
