package dev.sgqs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class SgqsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SgqsConfig instance = new SgqsConfig();

	// Performance
	public int fpsLimit = 144;          // 40..600 (>=260 = unlimited)
	public int renderDistance = 64;     // blocks, 10..100
	public boolean dynamicScaling = true;
	public int entityDistance = 100;    // percent, 50..500
	public int particles = 100;         // percent, 0..100
	public boolean effectsOff = false;

	// Graphics
	public int resolution = 0;          // 0 = don't change, 1=720p 2=1080p 3=1440p 4=4K 5=5K
	public int gamma = 50;              // 0..100
	public boolean fullbright = false;
	public int blueLight = 0;           // 0..100

	// Audio
	public int hitSound = 0;            // 0 off, 1 classic, 2 soft, 3 sharp
	public boolean muteWeather = false;
	public boolean muteNeutral = false;
	public boolean directional = false;
	public double origWeather = -1;
	public double origNeutral = -1;

	// HUD
	public int crosshair = 0;           // 0 vanilla, 1 dot, 2 cross, 3 box
	public int crosshairColor = 0;
	public int crosshairSize = 8;
	public boolean overlay = false;
	public boolean damage = false;

	// Gameplay
	public boolean streamer = false;
	public boolean antiMisclick = false;

	public static SgqsConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("server-graphics-quality.json");
	}

	public static void load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				SgqsConfig c = GSON.fromJson(Files.readString(f), SgqsConfig.class);
				if (c != null) instance = c;
			}
		} catch (Exception e) {
			instance = new SgqsConfig();
		}
		instance.clamp();
	}

	public static void reload() {
		load();
	}

	public static void save() {
		try {
			instance.clamp();
			Files.writeString(file(), GSON.toJson(instance));
		} catch (Exception e) {
			SgqsClient.LOGGER.error("Could not save config", e);
		}
	}

	public static void reset() {
		SgqsConfig old = instance;
		instance = new SgqsConfig();
		instance.origWeather = old.origWeather;
		instance.origNeutral = old.origNeutral;
	}

	public void applyPreset(int p) {
		switch (p) {
			case 0 -> { // weak device
				fpsLimit = 40; renderDistance = 10; particles = 0; effectsOff = true; entityDistance = 50; resolution = 1;
			}
			case 1 -> { // balanced
				fpsLimit = 144; renderDistance = 32; particles = 60; effectsOff = false; entityDistance = 100; resolution = 2;
			}
			default -> { // max
				fpsLimit = 600; renderDistance = 100; particles = 100; effectsOff = false; entityDistance = 200; resolution = 4;
			}
		}
	}

	private static int cl(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public void clamp() {
		fpsLimit = cl(fpsLimit, 40, 600);
		renderDistance = cl(renderDistance, 10, 100);
		entityDistance = cl(entityDistance, 50, 500);
		particles = cl(particles, 0, 100);
		resolution = cl(resolution, 0, 5);
		gamma = cl(gamma, 0, 100);
		blueLight = cl(blueLight, 0, 100);
		hitSound = cl(hitSound, 0, 3);
		crosshair = cl(crosshair, 0, 3);
		crosshairColor = cl(crosshairColor, 0, 5);
		crosshairSize = cl(crosshairSize, 2, 20);
	}
}
