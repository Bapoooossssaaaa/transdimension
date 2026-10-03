package dev.goober.transdimension.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.world.PlushLedger;

/** Data the mod attaches to game objects (Fabric Data Attachment API). */
public final class ModAttachments {
	/** On a level: which villages already got their plush, and how many of each cat went out. */
	public static final AttachmentType<PlushLedger> PLUSH_LEDGER = AttachmentRegistry.createPersistent(
			TransDimension.id("plush_ledger"), PlushLedger.CODEC);

	private ModAttachments() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
