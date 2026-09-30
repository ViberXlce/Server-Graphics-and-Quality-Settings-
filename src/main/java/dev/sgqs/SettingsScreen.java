package dev.sgqs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class SettingsScreen extends Screen {
	private static final int CW = 150, ROW_H = 26, HEAD_H = 24;

	private static final class Row {
		Text label;
		List<ClickableWidget> ws = new ArrayList<>();
		boolean header;
		int y, h;
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private int px, py, pw, ph, listTop, listBottom, contentH;
	private double scroll;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("sgqs.title"));
		this.parent = parent;
	}

	// ------------------------------------------------------------ builders

	private void header(String key) {
		Row r = new Row();
		r.label = Text.translatable(key);
		r.header = true;
		r.h = HEAD_H;
		rows.add(r);
	}

	private Row row(Text label, ClickableWidget... widgets) {
		Row r = new Row();
		r.label = label;
		r.h = ROW_H;
		for (ClickableWidget w : widgets) {
			r.ws.add(w);
			addSelectableChild(w);
		}
		rows.add(r);
		return r;
	}

	private Row row(String key, ClickableWidget... widgets) {
		return row(Text.translatable(key), widgets);
	}

	private void soon(Row r) {
		r.label = r.label.copy().append(Text.translatable("sgqs.soon"));
		for (ClickableWidget w : r.ws) w.active = false;
	}

	private ButtonWidget button(Text text, int w, Runnable action) {
		return ButtonWidget.builder(text, b -> action.run()).dimensions(0, 0, w, 20).build();
	}

	private ButtonWidget toggle(BooleanSupplier get, Consumer<Boolean> set) {
		return ButtonWidget.builder(Text.translatable(get.getAsBoolean() ? "sgqs.on" : "sgqs.off"), b -> {
			boolean n = !get.getAsBoolean();
			set.accept(n);
			b.setMessage(Text.translatable(n ? "sgqs.on" : "sgqs.off"));
		}).dimensions(0, 0, CW, 20).build();
	}

	private ButtonWidget cycle(String prefix, int count, IntSupplier get, IntConsumer set) {
		return ButtonWidget.builder(Text.translatable(prefix + get.getAsInt()), b -> {
			int n = (get.getAsInt() + 1) % count;
			set.accept(n);
			b.setMessage(Text.translatable(prefix + n));
		}).dimensions(0, 0, CW, 20).build();
	}

	private IntSlider slider(int min, int max, int value, java.util.function.IntFunction<Text> fmt, IntConsumer set) {
		return new IntSlider(CW, min, max, value, fmt, set);
	}

	private static Text pct(int v) {
		return Text.literal(v + "%");
	}

	// ------------------------------------------------------------ init / layout

	@Override
	protected void init() {
		rows.clear();
		SgqsConfig c = SgqsConfig.get();

		pw = Math.min(width - 20, 520);
		ph = Math.min(height - 16, 330);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		listTop = py + 34;
		listBottom = py + ph - 36;

		header("sgqs.section.presets");
		row("sgqs.preset",
				button(Text.translatable("sgqs.presets.low"), 50, () -> preset(0)),
				button(Text.translatable("sgqs.presets.mid"), 50, () -> preset(1)),
				button(Text.translatable("sgqs.presets.max"), 50, () -> preset(2)));

		header("sgqs.section.perf");
		row("sgqs.fps", slider(40, 600, c.fpsLimit,
				v -> Text.translatable(v >= 260 ? "sgqs.fps.unlimited" : "sgqs.fps.value", v), v -> c.fpsLimit = v));
		row("sgqs.dist", slider(10, 100, c.renderDistance, v -> Text.translatable("sgqs.dist.value", v), v -> c.renderDistance = v));
		row("sgqs.dynamic", toggle(() -> c.dynamicScaling, v -> c.dynamicScaling = v));
		row("sgqs.entity", slider(50, 500, c.entityDistance, SettingsScreen::pct, v -> c.entityDistance = v));
		row("sgqs.particles", slider(0, 100, c.particles, SettingsScreen::pct, v -> c.particles = v));
		row("sgqs.effects_off", toggle(() -> c.effectsOff, v -> c.effectsOff = v));

		header("sgqs.section.gfx");
		row("sgqs.resolution", cycle("sgqs.res.", 6, () -> c.resolution, v -> c.resolution = v));
		row("sgqs.brightness", slider(0, 100, c.gamma, SettingsScreen::pct, v -> c.gamma = v));
		row("sgqs.fullbright", toggle(() -> c.fullbright, v -> c.fullbright = v));
		row("sgqs.calibration", button(Text.translatable("sgqs.open"), CW,
				() -> client.setScreen(new CalibrationScreen(parent))));
		row("sgqs.bluelight", slider(0, 100, c.blueLight, SettingsScreen::pct, v -> c.blueLight = v));
		soon(row("sgqs.hdr", toggle(() -> false, v -> { })));
		soon(row("sgqs.contrast", slider(0, 100, 50, SettingsScreen::pct, v -> { })));
		soon(row("sgqs.colorblind", cycle("sgqs.cb.", 4, () -> 0, v -> { })));

		header("sgqs.section.audio");
		row("sgqs.hitsound", cycle("sgqs.hit.", 4, () -> c.hitSound, v -> c.hitSound = v));
		row("sgqs.mute_weather", toggle(() -> c.muteWeather, v -> c.muteWeather = v));
		row("sgqs.mute_neutral", toggle(() -> c.muteNeutral, v -> c.muteNeutral = v));
		row("sgqs.directional", toggle(() -> c.directional, v -> c.directional = v));

		header("sgqs.section.hud");
		row("sgqs.crosshair", cycle("sgqs.xh.", 4, () -> c.crosshair, v -> c.crosshair = v));
		row("sgqs.crosshair_color", cycle("sgqs.color.", 6, () -> c.crosshairColor, v -> c.crosshairColor = v));
		row("sgqs.crosshair_size", slider(2, 20, c.crosshairSize, v -> Text.literal(String.valueOf(v)), v -> c.crosshairSize = v));
		row("sgqs.overlay", toggle(() -> c.overlay, v -> c.overlay = v));
		row("sgqs.damage", toggle(() -> c.damage, v -> c.damage = v));

		header("sgqs.section.gameplay");
		row("sgqs.streamer", toggle(() -> c.streamer, v -> c.streamer = v));
		row("sgqs.antimisclick", toggle(() -> c.antiMisclick, v -> c.antiMisclick = v));

		int fy = py + ph - 28;
		int bw = Math.min(110, (pw - 40) / 3);
		int gap = 8;
		int fx = px + (pw - (bw * 3 + gap * 2)) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("sgqs.save"), b -> {
			SgqsConfig.save();
			SgqsManager.applyAll(client, true, true);
			client.setScreen(parent);
		}).dimensions(fx, fy, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("sgqs.defaults"), b -> {
			SgqsConfig.reset();
			client.setScreen(new SettingsScreen(parent));
		}).dimensions(fx + bw + gap, fy, bw, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("sgqs.back"), b -> close())
				.dimensions(fx + (bw + gap) * 2, fy, bw, 20).build());

		layoutRows();
	}

	private void preset(int p) {
		SgqsConfig.get().applyPreset(p);
		client.setScreen(new SettingsScreen(parent));
	}

	private void layoutRows() {
		int y = listTop - (int) scroll;
		int total = 0;
		for (Row r : rows) {
			r.y = y;
			y += r.h;
			total += r.h;
			if (r.header) continue;
			int wsum = 0;
			for (ClickableWidget w : r.ws) wsum += w.getWidth();
			wsum += 2 * (r.ws.size() - 1);
			int x = px + pw - 20 - wsum;
			for (ClickableWidget w : r.ws) {
				w.setPosition(x, r.y + 3);
				w.visible = r.y >= listTop - 2 && r.y + r.h <= listBottom + 2;
				x += w.getWidth() + 2;
			}
		}
		contentH = total;
	}

	private double maxScroll() {
		return Math.max(0, contentH - (listBottom - listTop));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - verticalAmount * 20));
		layoutRows();
		return true;
	}

	@Override
	public void close() {
		SgqsConfig.reload();
		client.setScreen(parent);
	}

	// ------------------------------------------------------------ render

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// custom background is drawn in render()
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xAA080312);
		ctx.fillGradient(px, py, px + pw, py + ph, 0xF02E1A4F, 0xF0150A2A);
		ctx.drawBorder(px, py, pw, ph, 0xFFB56CFF);
		ctx.drawBorder(px + 1, py + 1, pw - 2, ph - 2, 0x66E0B0FF);
		ctx.drawCenteredTextWithShadow(textRenderer, title, px + pw / 2, py + 14, 0xFFF0D9FF);

		ctx.enableScissor(px + 6, listTop, px + pw - 6, listBottom);
		for (Row r : rows) {
			if (r.y + r.h < listTop || r.y > listBottom) continue;
			if (r.header) {
				ctx.drawTextWithShadow(textRenderer, r.label, px + 16, r.y + 10, 0xFFD9A8FF);
				ctx.fill(px + 16, r.y + 21, px + pw - 20, r.y + 22, 0x55B56CFF);
			} else {
				ctx.fill(px + 12, r.y + 1, px + pw - 12, r.y + r.h - 1, 0x33000000);
				ctx.drawTextWithShadow(textRenderer, r.label, px + 18, r.y + 9, 0xFFE9DCFF);
				for (ClickableWidget w : r.ws) {
					if (w.visible) w.render(ctx, mouseX, mouseY, delta);
				}
			}
		}
		ctx.disableScissor();

		double ms = maxScroll();
		if (ms > 0) {
			int trackH = listBottom - listTop;
			int barH = Math.max(16, (int) (trackH * (trackH / (double) contentH)));
			int barY = listTop + (int) ((trackH - barH) * (scroll / ms));
			ctx.fill(px + pw - 9, listTop, px + pw - 6, listBottom, 0x33FFFFFF);
			ctx.fill(px + pw - 9, barY, px + pw - 6, barY + barH, 0xFFB56CFF);
		}

		super.render(ctx, mouseX, mouseY, delta);
	}
}
