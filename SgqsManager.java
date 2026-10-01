package dev.sgqs;

import dev.sgqs.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SgqsManager {
	public static KeyBinding openKey;

	private static final String P_KEY = "key.keyboard.p";
	private static final int[] COLORS = {0xFFFFFFFF, 0xFFFF4444, 0xFF44FF44, 0xFF44FFFF, 0xFFFFFF44, 0xFFC77DFF};

	private static int tick, dynOffset, lowCount, highCount;
	private static long lastAttackTick = -1000;
	private static LivingEntity lastTarget;
	private static float lastHealth;
	private static boolean prevLeft, streamerApplied;
	private static final List<Pop> pops = new ArrayList<>();
	private static final ArrayDeque<Long> clicks = new ArrayDeque<>();

	private static final class Pop {
		final float amount;
		final int dx;
		int age;

		Pop(float amount) {
			this.amount = amount;
			this.dx = (int) (Math.random() * 24) - 12;
		}
	}

	private SgqsManager() {}

	// ---------------------------------------------------------------- ticks

	public static void startTick(MinecraftClient mc) {
		SgqsConfig c = SgqsConfig.get();
		if (c.antiMisclick && tick - lastAttackTick < 60 && mc.currentScreen == null) {
			// swallow inventory / hand-swap / drop presses for 3s after attacking
			while (mc.options.inventoryKey.wasPressed()) { }
			while (mc.options.swapHandsKey.wasPressed()) { }
			while (mc.options.dropKey.wasPressed()) { }
		}
	}

	public static void endTick(MinecraftClient mc) {
		tick++;
		SgqsConfig c = SgqsConfig.get();

		while (openKey.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new SettingsScreen(null));
		}

		if (tick % 100 == 1) unbindP(mc);

		// CPS (polls the left mouse button)
		if (mc.getWindow() != null) {
			boolean left = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
			if (left && !prevLeft && mc.currentScreen == null) clicks.add(System.currentTimeMillis());
			prevLeft = left;
		}

		// Damage numbers
		if (c.damage && lastTarget != null) {
			if (lastTarget.isRemoved() || tick - lastAttackTick > 100) {
				lastTarget = null;
			} else {
				float h = lastTarget.getHealth();
				if (h < lastHealth - 0.01f) pops.add(new Pop(lastHealth - h));
				lastHealth = h;
			}
		}
		pops.removeIf(p -> ++p.age > 30);

		// Dynamic FPS scaling
		if (c.dynamicScaling && mc.world != null && tick % 20 == 0) {
			int fps = mc.getCurrentFps();
			int base = Math.max(2, Math.round(c.renderDistance / 16f));
			if (fps < 40) {
				highCount = 0;
				if (++lowCount >= 3 && base - dynOffset > 2) {
					dynOffset++;
					lowCount = 0;
					applyAll(mc, false, false);
				}
			} else if (fps > 90) {
				lowCount = 0;
				if (++highCount >= 10 && dynOffset > 0) {
					dynOffset--;
					highCount = 0;
					applyAll(mc, false, false);
				}
			} else {
				lowCount = 0;
				highCount = 0;
			}
		}
		if (!c.dynamicScaling && dynOffset != 0) {
			dynOffset = 0;
			applyAll(mc, false, false);
		}
	}

	// ---------------------------------------------------------------- P key cleanup

	/** Unbinds every other key mapping that is on P (vanilla: Social Interactions). */
	public static void unbindP(MinecraftClient mc) {
		if (openKey == null || !P_KEY.equals(openKey.getBoundKeyTranslationKey())) return;
		boolean changed = false;
		for (KeyBinding kb : mc.options.allKeys) {
			if (kb != openKey && P_KEY.equals(kb.getBoundKeyTranslationKey())) {
				kb.setBoundKey(InputUtil.UNKNOWN_KEY);
				changed = true;
			}
		}
		if (changed) {
			KeyBinding.updateKeysByCode();
			mc.options.write();
		}
	}

	// ---------------------------------------------------------------- apply settings

	public static void applyAll(MinecraftClient mc, boolean resize, boolean write) {
		try {
			SgqsConfig c = SgqsConfig.get();
			GameOptions o = mc.options;

			o.getMaxFps().setValue(c.fpsLimit >= 260 ? 260 : c.fpsLimit);

			int base = Math.max(2, Math.round(c.renderDistance / 16f));
			o.getViewDistance().setValue(Math.max(2, Math.min(32, base - dynOffset)));

			o.getEntityDistanceScaling().setValue(c.entityDistance / 100.0);

			int level = c.particles > 66 ? 0 : c.particles > 25 ? 1 : 2;
			if (c.effectsOff) level = 2;
			else if (dynOffset > 0 && level == 0) level = 1;
			setParticleLevel(o, level);
			o.getEntityShadows().setValue(!c.effectsOff);

			if (c.fullbright) {
				@SuppressWarnings("unchecked")
				SimpleOptionAccessor<Double> acc = (SimpleOptionAccessor<Double>) (Object) o.getGamma();
				acc.sgqs$setRaw(16.0);
			} else {
				o.getGamma().setValue(c.gamma / 100.0);
			}

			o.getDirectionalAudio().setValue(c.directional);

			if (c.muteWeather) {
				if (c.origWeather < 0) c.origWeather = o.getSoundVolumeOption(SoundCategory.WEATHER).getValue();
				o.getSoundVolumeOption(SoundCategory.WEATHER).setValue(0.0);
			} else if (c.origWeather >= 0) {
				o.getSoundVolumeOption(SoundCategory.WEATHER).setValue(c.origWeather);
				c.origWeather = -1;
			}
			if (c.muteNeutral) {
				if (c.origNeutral < 0) c.origNeutral = o.getSoundVolumeOption(SoundCategory.NEUTRAL).getValue();
				o.getSoundVolumeOption(SoundCategory.NEUTRAL).setValue(0.0);
			} else if (c.origNeutral >= 0) {
				o.getSoundVolumeOption(SoundCategory.NEUTRAL).setValue(c.origNeutral);
				c.origNeutral = -1;
			}

			if (c.streamer != streamerApplied) {
				o.getReducedDebugInfo().setValue(c.streamer);
				streamerApplied = c.streamer;
			}

			if (resize && c.resolution > 0 && !mc.getWindow().isFullscreen()) {
				int[][] sizes = {{0, 0}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3840, 2160}, {5120, 2880}};
				GLFW.glfwSetWindowSize(mc.getWindow().getHandle(), sizes[c.resolution][0], sizes[c.resolution][1]);
			}

			if (write) o.write();
		} catch (Throwable t) {
			SgqsClient.LOGGER.error("Failed to apply settings", t);
		}
	}

	/** 0 = all, 1 = decreased, 2 = minimal (vanilla enum order). */
	@SuppressWarnings("unchecked")
	private static void setParticleLevel(GameOptions o, int level) {
		SimpleOption<?> opt = o.getParticles();
		Object cur = opt.getValue();
		if (cur instanceof Enum<?> e) {
			Object[] vals = e.getDeclaringClass().getEnumConstants();
			((SimpleOption<Object>) opt).setValue(vals[Math.min(level, vals.length - 1)]);
		}
	}

	public static void border(DrawContext ctx, int x, int y, int w, int h, int col) {
		ctx.fill(x, y, x + w, y + 1, col);
		ctx.fill(x, y + h - 1, x + w, y + h, col);
		ctx.fill(x, y + 1, x + 1, y + h - 1, col);
		ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
	}

	// ---------------------------------------------------------------- combat helpers

	public static void onAttack(Entity e) {
		SgqsConfig c = SgqsConfig.get();
		lastAttackTick = tick;
		if (e instanceof LivingEntity le) {
			lastTarget = le;
			lastHealth = le.getHealth();
		}
		if (c.hitSound > 0) {
			SoundEvent s = c.hitSound == 2 ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.ENTITY_ARROW_HIT_PLAYER;
			float pitch = c.hitSound == 1 ? 1.0f : c.hitSound == 2 ? 1.3f : 2.0f;
			float vol = c.hitSound == 2 ? 0.4f : 0.6f;
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player != null) mc.player.playSound(s, vol, pitch);
		}
	}

	private static int cps() {
		long now = System.currentTimeMillis();
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
		return clicks.size();
	}

	// ---------------------------------------------------------------- HUD

	public static void renderFilters(DrawContext ctx, RenderTickCounter tc) {
		SgqsConfig c = SgqsConfig.get();
		if (c.blueLight > 0) {
			int a = (int) (c.blueLight * 0.6);
			ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), (a << 24) | 0xFF9A1E);
		}
	}

	public static void renderInfo(DrawContext ctx, RenderTickCounter tc) {
		SgqsConfig c = SgqsConfig.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) return;

		if (c.overlay) {
			int ping = -1;
			if (mc.getNetworkHandler() != null) {
				PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
				if (e != null) ping = e.getLatency();
			}
			String s = "FPS " + mc.getCurrentFps() + "  |  Ping " + (ping < 0 ? "-" : ping + "ms") + "  |  CPS " + cps();
			int x = ctx.getScaledWindowWidth() - mc.textRenderer.getWidth(s) - 6;
			ctx.fill(x - 3, 2, ctx.getScaledWindowWidth() - 2, 14, 0x88000000);
			ctx.drawTextWithShadow(mc.textRenderer, s, x, 4, 0xFFFFFFFF);
		}

		if (c.damage) {
			int cx = ctx.getScaledWindowWidth() / 2;
			int cy = ctx.getScaledWindowHeight() / 2;
			for (Pop p : pops) {
				int a = Math.max(8, 255 - p.age * 8);
				String t = String.format(Locale.ROOT, "-%.1f", p.amount);
				ctx.drawTextWithShadow(mc.textRenderer, t, cx + 14 + p.dx, cy - 10 - p.age / 2, (a << 24) | 0xFF5555);
			}
		}
	}

	public static void renderCrosshair(DrawContext ctx, HudElement original, RenderTickCounter tc) {
		SgqsConfig c = SgqsConfig.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (c.crosshair == 0) {
			original.render(ctx, tc);
			return;
		}
		if (!mc.options.getPerspective().isFirstPerson()) return;

		int cx = ctx.getScaledWindowWidth() / 2;
		int cy = ctx.getScaledWindowHeight() / 2;
		int s = c.crosshairSize;
		int col = COLORS[Math.max(0, Math.min(COLORS.length - 1, c.crosshairColor))];
		switch (c.crosshair) {
			case 1 -> {
				int r = Math.max(1, s / 4);
				ctx.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, col);
			}
			case 2 -> {
				int gap = 2;
				ctx.fill(cx - gap - s, cy, cx - gap, cy + 1, col);
				ctx.fill(cx + gap + 1, cy, cx + gap + 1 + s, cy + 1, col);
				ctx.fill(cx, cy - gap - s, cx + 1, cy - gap, col);
				ctx.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + s, col);
			}
			default -> {
				int r = Math.max(2, s / 2);
				border(ctx, cx - r, cy - r, r * 2 + 1, r * 2 + 1, col);
			}
		}
	}
}
