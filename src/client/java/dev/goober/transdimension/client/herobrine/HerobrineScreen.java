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
 * "Save" keeps them for this world, and the two test buttons also make him appear at once, or a sound play, for everyone
 * who can see him (pick "Everyone" or "Only me" to try it yourself). Plain English labels, kept out of the language file
 * on purpose.
 */
public class HerobrineScreen extends Screen {
	private static final int BUTTON_WIDTH = 170;
	private static final int ROW = 22;
	private static final int OPTION_ROWS = 8;
	/** The options, a gap, then two rows of buttons. */
	private static final int TALL = (OPTION_ROWS + 2) * ROW + 6;
	private static final String[] AUDIENCE = {"Other players", "Only me", "Everyone"};
	private static final String[] PLACEMENT = {"Far away", "Behind you", "Far away or behind"};
	private static final String[] DISTANCE = {"30 to 50 blocks", "50 to 90 blocks", "Edge of sight"};
	private static final String[] TIME = {"Day and night", "Only at night", "Only by day"};
	private static final String[] WEATHER = {"Any weather", "Clear skies", "Rain", "Thunderstorms"};
	private static final String[] WHERE = {"Overworld only", "Anywhere"};
	/** The choices for each number (0: never, or off). */
	private static final int[] MINUTES = {5, 10, 20, 40, 0};
	private static final int[] LINGER = {10, 20, 30, 60, 120};
	private static final int[] VANISH = {8, 12, 16, 24, 32};
	private static final int[] SEEN = {1, 2, 4, 8, 0};
	private static final int[] SOUND_MINUTES = {5, 10, 15, 30, 0};

	private boolean enabled;
	private int audience;
	private int placement;
	private int distance;
	private boolean cover;
	private int time;
	private int weather;
	private int where;
	private int minutes;
	private int linger;
	private int vanishDistance;
	private int seenSeconds;
	private boolean eyes;
	private boolean sounds;
	private int soundMinutes;

	public HerobrineScreen(HerobrineSettings settings) {
		super(Component.literal("Secret settings"));
		this.enabled = settings.enabled();
		this.audience = settings.audience();
		this.placement = settings.placement();
		this.distance = settings.distance();
		this.cover = settings.cover();
		this.time = settings.time();
		this.weather = settings.weather();
		this.where = settings.where();
		this.minutes = settings.minutes();
		this.linger = settings.linger();
		this.vanishDistance = settings.vanishDistance();
		this.seenSeconds = settings.seenSeconds();
		this.eyes = settings.eyes();
		this.sounds = settings.sounds();
		this.soundMinutes = settings.soundMinutes();
	}

	private int top() {
		return Math.max(36, (this.height - TALL) / 2 + 14);
	}

	@Override
	protected void init() {
		int left = this.width / 2 - BUTTON_WIDTH - 4;
		int right = this.width / 2 + 4;
		int top = this.top();
		this.option(left, top, () -> "Herobrine: " + onOff(this.enabled), () -> this.enabled = !this.enabled);
		this.option(right, top, () -> "Seen by: " + pick(AUDIENCE, this.audience), () -> this.audience = next(AUDIENCE, this.audience));
		this.option(left, top + ROW, () -> "Stands: " + pick(PLACEMENT, this.placement), () -> this.placement = next(PLACEMENT, this.placement));
		this.option(right, top + ROW, () -> "How far: " + pick(DISTANCE, this.distance), () -> this.distance = next(DISTANCE, this.distance));
		this.option(left, top + 2 * ROW, () -> "Partly hidden: " + onOff(this.cover), () -> this.cover = !this.cover);
		this.option(right, top + 2 * ROW, () -> "When: " + pick(TIME, this.time), () -> this.time = next(TIME, this.time));
		this.option(left, top + 3 * ROW, () -> "Weather: " + pick(WEATHER, this.weather), () -> this.weather = next(WEATHER, this.weather));
		this.option(right, top + 3 * ROW, () -> "Where: " + pick(WHERE, this.where), () -> this.where = next(WHERE, this.where));
		this.option(left, top + 4 * ROW, () -> this.minutes == 0 ? "Sightings: never" : "Sightings: about every " + this.minutes + " min",
				() -> this.minutes = cycle(MINUTES, this.minutes));
		this.option(right, top + 4 * ROW, () -> "Stays up to " + this.linger + " s", () -> this.linger = cycle(LINGER, this.linger));
		this.option(left, top + 5 * ROW, () -> "Gone within " + this.vanishDistance + " blocks",
				() -> this.vanishDistance = cycle(VANISH, this.vanishDistance));
		this.option(right, top + 5 * ROW, () -> this.seenSeconds == 0 ? "Stays while on screen" : "Gone after " + this.seenSeconds + " s on screen",
				() -> this.seenSeconds = cycle(SEEN, this.seenSeconds));
		this.option(left, top + 6 * ROW, () -> "Glowing eyes: " + onOff(this.eyes), () -> this.eyes = !this.eyes);
		this.option(right, top + 6 * ROW, () -> "Cave sound as he goes: " + onOff(this.sounds), () -> this.sounds = !this.sounds);
		this.option(left, top + 7 * ROW, () -> this.soundMinutes == 0 ? "Noises behind: Off"
				: "Noises behind: every " + this.soundMinutes + " min", () -> this.soundMinutes = cycle(SOUND_MINUTES, this.soundMinutes));
		int buttons = top + OPTION_ROWS * ROW + 6;
		this.addRenderableWidget(Button.builder(Component.literal("Save and show him now"), button -> this.save(HerobrineSettingsPayload.SHOW))
				.bounds(left, buttons, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Save and play a sound now"), button -> this.save(HerobrineSettingsPayload.SOUND))
				.bounds(right, buttons, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save(HerobrineSettingsPayload.SAVE))
				.bounds(left, buttons + ROW, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
				.bounds(right, buttons + ROW, BUTTON_WIDTH, 20).build());
	}

	/** A button that shows a setting and moves it on to its next value when pressed. */
	private void option(int x, int y, Supplier<String> label, Runnable next) {
		this.addRenderableWidget(Button.builder(Component.literal(label.get()), button -> {
			next.run();
			button.setMessage(Component.literal(label.get()));
		}).bounds(x, y, BUTTON_WIDTH, 20).build());
	}

	private static String onOff(boolean on) {
		return on ? "On" : "Off";
	}

	private static String pick(String[] names, int index) {
		return names[Math.floorMod(index, names.length)];
	}

	private static int next(String[] names, int index) {
		return Math.floorMod(index + 1, names.length);
	}

	/** The next choice after the one set now (round to the first again; the first, if the one set now isn't a choice). */
	private static int cycle(int[] choices, int current) {
		for (int i = 0; i < choices.length; i++) {
			if (choices[i] == current) {
				return choices[(i + 1) % choices.length];
			}
		}
		return choices[0];
	}

	private void save(int action) {
		HerobrineSettings settings = new HerobrineSettings(this.enabled, Optional.empty(), this.audience, this.placement, this.distance,
				this.cover, this.time, this.weather, this.where, this.minutes, this.linger, this.vanishDistance, this.seenSeconds, this.eyes,
				this.sounds, this.soundMinutes);
		ClientPlayNetworking.send(new HerobrineSettingsPayload(settings, action));
		this.onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		int top = this.top();
		String title = "Secret settings";
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, top - 30, 0xFFFFFFFF, true);
		String note = "Only you can open this. He is never an entity: maps, radars and F3 can't see him.";
		graphics.text(this.font, note, (this.width - this.font.width(note)) / 2, top - 16, 0xFFA0A0A0, false);
	}
}
