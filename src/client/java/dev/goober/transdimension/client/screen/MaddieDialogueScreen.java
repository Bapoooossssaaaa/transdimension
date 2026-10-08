package dev.goober.transdimension.client.screen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.network.MaddieChoicePayload;

/**
 * Maddie's dialogue (and, once she's gone, Kira's): a pastel speech box at the bottom of the screen with the speaker's
 * portrait (drawn from their skin), their name, text that types itself out with little blips, and answers to pick on
 * the right.
 *
 * <p>All the words live in the language file under {@code dialogue.transdimension.maddie.*} and
 * {@code dialogue.transdimension.kira.*}. Kira is grieving Maddie, her girlfriend: her talk opens up as you go (asking
 * what happened to Maddie leads on to the fairy, asking how she is leads on to what she'll do now), her text comes more
 * slowly and her blips are lower. Accepting the gifts (Maddie's own, or the ones she left with Kira) is the only choice
 * the server hears about ({@link MaddieChoicePayload}); everything else is just talking.
 */
public class MaddieDialogueScreen extends Screen {
	private static final Identifier MADDIE_SKIN = TransDimension.id("textures/entity/maddie/maddie.png");
	private static final Identifier KIRA_SKIN = TransDimension.id("textures/entity/kira/kira.png");
	private static final int BOX_WIDTH = 340;
	private static final int BOX_HEIGHT = 86;
	private static final int PORTRAIT = 48;
	private static final int PINK = 0xFFF5A9B8;
	private static final int BLUE = 0xFF5BCEFA;

	private final int speakerId;
	private final boolean kira;
	private final String keys;
	private boolean gifted;
	private Node node;
	/** Where the player has been in this talk (some of Kira's topics only come up after others). */
	private final Set<Node> visited = EnumSet.noneOf(Node.class);
	private String text = "";
	private int revealed;
	private int ticks;

	public MaddieDialogueScreen(int speakerId, boolean gifted, boolean kira) {
		super(Component.translatable(kira ? "entity.transdimension.kira" : "entity.transdimension.maddie"));
		this.speakerId = speakerId;
		this.kira = kira;
		this.keys = kira ? "dialogue.transdimension.kira." : "dialogue.transdimension.maddie.";
		this.gifted = gifted;
		this.node = gifted ? Node.GREETING_AGAIN : Node.GREETING;
		this.visited.add(this.node);
	}

	/** What the speaker says, and which answers the player gets afterwards. PLACE is Maddie's; MADDIE to LATER are Kira's. */
	private enum Node {
		GREETING("greeting"),
		GREETING_AGAIN("greeting_again"),
		WHO("who"),
		PLACE("place"),
		MADDIE("maddie"),
		FAIRY("fairy"),
		OKAY("okay"),
		NOW("now"),
		GIFTS("gifts"),
		GIFTS_GIVEN("gifts_given"),
		THANKS("thanks"),
		LATER("later");

		final String key;

		Node(String key) {
			this.key = key;
		}
	}

	private record Answer(String key, Runnable action) {
	}

	@Override
	protected void init() {
		this.text = Component.translatable(this.keys + this.node.key).getString();
		List<Answer> answers = this.answers();
		int x = this.width / 2 + BOX_WIDTH / 2 - 170;
		int y = this.boxTop() - 6 - answers.size() * 22;
		for (Answer answer : answers) {
			this.addRenderableWidget(Button.builder(Component.translatable(this.keys + "option." + answer.key()),
					button -> answer.action().run()).bounds(x, y, 170, 20).build());
			y += 22;
		}
	}

	private List<Answer> answers() {
		List<Answer> list = new ArrayList<>();
		if (this.node == Node.GIFTS) {
			list.add(new Answer("accept", this::acceptGifts));
			list.add(new Answer("back", () -> this.goTo(this.kira ? Node.LATER : Node.GREETING_AGAIN)));
			return list;
		}
		this.offer(list, Node.WHO);
		if (this.kira) {
			this.offer(list, Node.MADDIE);
			if (this.visited.contains(Node.MADDIE)) {
				this.offer(list, Node.FAIRY);
			}
			this.offer(list, Node.OKAY);
			if (this.visited.contains(Node.OKAY)) {
				this.offer(list, Node.NOW);
			}
		} else {
			this.offer(list, Node.PLACE);
		}
		if (this.node != Node.GIFTS_GIVEN && this.node != Node.THANKS) {
			list.add(new Answer("gifts", () -> this.goTo(this.gifted ? Node.GIFTS_GIVEN : Node.GIFTS)));
		}
		list.add(new Answer("bye", this::onClose));
		return list;
	}

	/** The answer that leads to {@code topic}, unless that's what's being said now. */
	private void offer(List<Answer> list, Node topic) {
		if (this.node != topic) {
			list.add(new Answer(topic.key, () -> this.goTo(topic)));
		}
	}

	private void goTo(Node next) {
		this.node = next;
		this.visited.add(next);
		this.revealed = 0;
		this.rebuildWidgets();
	}

	private void acceptGifts() {
		ClientPlayNetworking.send(new MaddieChoicePayload(this.speakerId, MaddieChoicePayload.ACCEPT_GIFTS));
		this.gifted = true;
		this.goTo(Node.THANKS);
	}

	@Override
	public void tick() {
		super.tick();
		this.ticks++;
		// Maddie chatters two letters a tick; Kira takes her time, three letters every two ticks.
		int letters = this.kira ? this.ticks % 2 + 1 : 2;
		if (this.revealed < this.text.length()) {
			int before = this.revealed;
			this.revealed = Math.min(this.text.length(), this.revealed + letters);
			if (before / 3 != this.revealed / 3 && this.minecraft != null) {
				float pitch = (this.kira ? 1.15F : 1.5F) + (this.text.charAt(this.revealed - 1) % 5) * 0.08F;
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), pitch, 0.12F));
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int boxTop() {
		return this.height - BOX_HEIGHT - 12;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// No blur: just a soft dusk at the bottom so the speech box stands out over the world.
		graphics.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, 0xA01A0F2E);

		int left = this.width / 2 - BOX_WIDTH / 2;
		int top = this.boxTop();
		// The speech box: a dark plum panel inside a blue, pink and white border.
		graphics.fill(left - 3, top - 3, left + BOX_WIDTH + 3, top + BOX_HEIGHT + 3, BLUE);
		graphics.fill(left - 2, top - 2, left + BOX_WIDTH + 2, top + BOX_HEIGHT + 2, PINK);
		graphics.fill(left - 1, top - 1, left + BOX_WIDTH + 1, top + BOX_HEIGHT + 1, 0xFFFFFFFF);
		graphics.fillGradient(left, top, left + BOX_WIDTH, top + BOX_HEIGHT, 0xF02A1B45, 0xF0402A5E);

		// Portrait: the speaker's face and hair layer from their skin, framed.
		int px = left + 10;
		int py = top + (BOX_HEIGHT - PORTRAIT) / 2;
		graphics.fill(px - 2, py - 2, px + PORTRAIT + 2, py + PORTRAIT + 2, PINK);
		graphics.fill(px - 1, py - 1, px + PORTRAIT + 1, py + PORTRAIT + 1, 0xFF1A0F2E);
		Identifier skin = this.kira ? KIRA_SKIN : MADDIE_SKIN;
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, px, py, 8.0F, 8.0F, PORTRAIT, PORTRAIT, 8, 8, 64, 64);
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, px, py, 40.0F, 8.0F, PORTRAIT, PORTRAIT, 8, 8, 64, 64);

		// Name plate above the box.
		String name = this.title.getString();
		int nameWidth = this.font.width(name) + 12;
		graphics.fill(left + 6, top - 15, left + 6 + nameWidth, top - 2, PINK);
		graphics.fill(left + 7, top - 14, left + 5 + nameWidth, top - 3, 0xFF2A1B45);
		graphics.text(this.font, name, left + 12, top - 12, PINK, false);

		// The words so far, wrapped to the box.
		int textLeft = px + PORTRAIT + 10;
		int textWidth = left + BOX_WIDTH - 10 - textLeft;
		int lineY = top + 9;
		for (String line : wrap(this.text.substring(0, Math.min(this.revealed, this.text.length())), textWidth)) {
			graphics.text(this.font, line, textLeft, lineY, 0xFFFFF4F8, true);
			lineY += this.font.lineHeight + 2;
		}
		if (this.revealed >= this.text.length() && (System.currentTimeMillis() / 400) % 2 == 0) {
			graphics.text(this.font, "❤", left + BOX_WIDTH - 14, top + BOX_HEIGHT - 12, BLUE, false);
		}
	}

	/** Greedy word wrap by pixel width. */
	private List<String> wrap(String words, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : words.split(" ", -1)) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (this.font.width(candidate) > width && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}
}
