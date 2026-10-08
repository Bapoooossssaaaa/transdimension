package dev.goober.transdimension.client.herobrine;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.goober.transdimension.herobrine.HerobrineSettings;
import dev.goober.transdimension.herobrine.HerobrineSettingsPayload;

/**
 * The host's secret settings screen (opened by the secret command; see Herobrine). Each button cycles one setting;
 * "Save" keeps them for this world, and "Save and show him now" also makes him appear at once to everyone who can see
 * him, to try the settings out (pick "Everyone" or "Only me" to watch it happen yourself). Plain English labels, kept out
 * of the language file on purpose.
 */
public class HerobrineScreen extends Screen {
	private static final int BUTTON_WIDTH = 170;
	private static final int ROW = 24;
	private static final String[] AUDIENCE = {"Other players", "Only me", "Everyone"};
	private static final String[] PLACEMENT = {"Far away", "Behind you", "Far away or behind"};
	private static final String[] TIME = {"Day and night", "Only at night", "Only by day"};
	private static final String[] WEATHER = {"Any weather", "Clear skies", "Rain", "Thunderstorms"};
	private static final String[] WHERE = {"Overworld only", "Anywhere"};
	private static final int[] MINUTES = {2, 5, 10, 20, 40};
	private static final int[] LINGER = {5, 10, 20, 40, 60};
	private static final int[] VANISH = {8, 12, 16, 24, 32};

	private boolean enabled;
	private int audience;
	private int placement;
	private int time;
	private int weather;
	private int where;
	private int minutes;
	private int linger;
	private int vanishDistance;
	private boolean stare;
	private boolean sounds;

	public HerobrineScreen(HerobrineSettings settings) {
		super(Component.literal("Secret settings"));
		this.enabled = settings.enabled();
		this.audience = settings.audience();
		this.placement = settings.placement();
		this.time = settings.time();
		this.weather = settings.weather();
		this.where = settings.where();
		this.minutes = settings.minutes();
		this.linger = settings.linger();
		this.vanishDistance = settings.vanishDistance();
		this.stare = settings.stare();
		this.sounds = settings.sounds();
	}

	@Override
	protected void init() {
		int left = this.width / 2 - BUTTON_WIDTH - 4;
		int right = this.width / 2 + 4;
		int top = this.height / 2 - 3 * ROW - 6;
		this.option(left, top, () -> "Herobrine: " + (this.enabled ? "On" : "Off"), () -> this.enabled = !this.enabled);
		this.option(right, top, () -> "Seen by: " + pick(AUDIENCE, this.audience), () -> this.audience = next(AUDIENCE, this.audience));
		this.option(left, top + ROW, () -> "Stands: " + pick(PLACEMENT, this.placement), () -> this.placement = next(PLACEMENT, this.placement));
		this.option(right, top + ROW, () -> "When: " + pick(TIME, this.time), () -> this.time = next(TIME, this.time));
		this.option(left, top + 2 * ROW, () -> "Weather: " + pick(WEATHER, this.weather), () -> this.weather = next(WEATHER, this.weather));
		this.option(right, top + 2 * ROW, () -> "Where: " + pick(WHERE, this.where), () -> this.where = next(WHERE, this.where));
		this.option(left, top + 3 * ROW, () -> "About every " + this.minutes + " min", () -> this.minutes = next(MINUTES, this.minutes));
		this.option(right, top + 3 * ROW, () -> "Stays up to " + this.linger + " s", () -> this.linger = next(LINGER, this.linger));
		this.option(left, top + 4 * ROW, () -> "Gone within " + this.vanishDistance + " blocks",
				() -> this.vanishDistance = next(VANISH, this.vanishDistance));
		this.option(right, top + 4 * ROW, () -> "Gone when stared at: " + (this.stare ? "On" : "Off"), () -> this.stare = !this.stare);
		this.option(left, top + 5 * ROW, () -> "Cave sound as he goes: " + (this.sounds ? "On" : "Off"), () -> this.sounds = !this.sounds);
		int bottom = top + 6 * ROW + 8;
		this.addRenderableWidget(Button.builder(Component.literal("Save and show him now"), button -> this.save(true))
				.bounds(left, bottom, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save(false))
				.bounds(right, bottom, BUTTON_WIDTH / 2 - 2, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
				.bounds(right + BUTTON_WIDTH / 2 + 2, bottom, BUTTON_WIDTH / 2 - 2, 20).build());
	}

	/** A button that shows a setting and moves it on to its next value when pressed. */
	private void option(int x, int y, Supplier<String> label, Runnable next) {
		this.addRenderableWidget(Button.builder(Component.literal(label.get()), button -> {
			next.run();
			button.setMessage(Component.literal(label.get()));
		}).bounds(x, y, BUTTON_WIDTH, 20).build());
	}

	private static String pick(String[] names, int index) {
		return names[Math.floorMod(index, names.length)];
	}

	private static int next(String[] names, int index) {
		return Math.floorMod(index + 1, names.length);
	}

	/** The next value up the list (round to the start), from whichever value is set now. */
	private static int next(int[] values, int current) {
		for (int value : values) {
			if (value > current) {
				return value;
			}
		}
		return values[0];
	}

	private void save(boolean showNow) {
		HerobrineSettings settings = new HerobrineSettings(this.enabled, Optional.empty(), this.audience, this.placement, this.time,
				this.weather, this.where, this.minutes, this.linger, this.vanishDistance, this.stare, this.sounds);
		ClientPlayNetworking.send(new HerobrineSettingsPayload(settings, showNow));
		this.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		int top = this.height / 2 - 3 * ROW - 6;
		String title = "Secret settings";
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, top - 30, 0xFFFFFFFF, true);
		String note = "Only you can open this. He is never an entity: maps, radars and F3 can't see him.";
		graphics.text(this.font, note, (this.width - this.font.width(note)) / 2, top - 16, 0xFFA0A0A0, false);
	}
}
