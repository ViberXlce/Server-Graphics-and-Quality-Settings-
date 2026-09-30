package dev.sgqs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SgqsClient implements ClientModInitializer {
	public static final String MOD_ID = "sgqs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		SgqsConfig.load();

		// P opens the settings screen
		SgqsManager.openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.sgqs.open",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
		));

		ClientTickEvents.START_CLIENT_TICK.register(SgqsManager::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(SgqsManager::endTick);

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			SgqsManager.unbindP(mc);
			SgqsManager.applyAll(mc, false, false);
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient()) SgqsManager.onAttack(entity);
			return ActionResult.PASS;
		});

		// Streamer mode: drop incoming private messages
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (SgqsConfig.get().streamer
					&& message.getContent() instanceof TranslatableTextContent t
					&& t.getKey().startsWith("commands.message.display")) {
				return false;
			}
			return true;
		});

		HudElementRegistry.addFirst(Identifier.of(MOD_ID, "filters"), SgqsManager::renderFilters);
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "info"), SgqsManager::renderInfo);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,
				original -> (ctx, tc) -> SgqsManager.renderCrosshair(ctx, original, tc));
	}
}
