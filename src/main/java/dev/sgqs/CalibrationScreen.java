package dev.sgqs;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Resident Evil style brightness calibration: three logos at different darkness levels. */
public class CalibrationScreen extends Screen {
	private static final int[] BASE = {0x0A, 0x18, 0x34};
	private final Screen settingsParent;

	public CalibrationScreen(Screen settingsParent) {
		super(Text.translatable("sgqs.cal.title"));
		this.settingsParent = settingsParent;
	}

	@Override
	protected void init() {
		SgqsConfig c = SgqsConfig.get();
		IntSlider s = new IntSlider(200, 0, 100, c.gamma, v -> Text.translatable("sgqs.brightness").append(Text.literal(": " + v + "%")), v -> {
			c.gamma = v;
			SgqsManager.applyAll(client, false, false);
		});
		s.setPosition(width / 2 - 100, height - 60);
		addDrawableChild(s);
		addDrawableChild(ButtonWidget.builder(Text.translatable("sgqs.done"), b -> {
			SgqsConfig.save();
			client.setScreen(new SettingsScreen(settingsParent));
		}).dimensions(width / 2 - 50, height - 34, 100, 20).build());
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
	}

	private void figure(DrawContext ctx, int cx, int top, int color) {
		ctx.fill(cx - 8, top, cx + 8, top + 16, color);             // head
		ctx.fill(cx - 14, top + 19, cx + 14, top + 56, color);      // torso
		ctx.fill(cx - 22, top + 19, cx - 17, top + 48, color);      // left arm
		ctx.fill(cx + 17, top + 19, cx + 22, top + 48, color);      // right arm
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xFF000000);
		ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFF0D9FF);

		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("sgqs.cal.hint"), Math.min(width - 40, 360));
		int y = 32;
		for (OrderedText l : lines) {
			ctx.drawTextWithShadow(textRenderer, l, width / 2 - Math.min(width - 40, 360) / 2, y, 0xFFB9A5D6);
			y += 10;
		}

		double boost = 0.5 + SgqsConfig.get().gamma / 100.0 * 1.5;
		int cy = height / 2 - 20;
		for (int i = 0; i < 3; i++) {
			int v = Math.min(255, (int) (BASE[i] * boost));
			figure(ctx, width / 2 + (i - 1) * 80, cy, 0xFF000000 | (v << 16) | (v << 8) | v);
		}
		super.render(ctx, mouseX, mouseY, delta);
	}
}
