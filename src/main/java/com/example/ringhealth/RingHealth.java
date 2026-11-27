package com.example.ringhealth;

import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only mod: the class is never even loaded on a dedicated server. */
@Mod(value = RingHealth.MOD_ID, dist = Dist.CLIENT)
public class RingHealth {
    public static final String MOD_ID = "ringhealth";

    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.ringhealth.toggle_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.ringhealth");
    public static final KeyMapping STYLE_KEY = new KeyMapping(
            "key.ringhealth.cycle_style", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.ringhealth");
    public static final KeyMapping OPACITY_UP_KEY = new KeyMapping(
            "key.ringhealth.opacity_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.ringhealth");
    public static final KeyMapping OPACITY_DOWN_KEY = new KeyMapping(
            "key.ringhealth.opacity_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.ringhealth");

    public RingHealth(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RingHealth::registerLayers);
        modBus.addListener(RingHealth::registerKeys);

        NeoForge.EVENT_BUS.addListener(LevelRenderHandler::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(RingHealth::onClientTick);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "health_ring"),
                HealthRingLayer.INSTANCE);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
        event.register(STYLE_KEY);
        event.register(OPACITY_UP_KEY);
        event.register(OPACITY_DOWN_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPACITY_UP_KEY.consumeClick()) changeOpacity(+0.1);
        while (OPACITY_DOWN_KEY.consumeClick()) changeOpacity(-0.1);

        // V: where it is shown (crosshair ring -> HUD bar -> 3D bar -> ring under the mob)
        while (TOGGLE_KEY.consumeClick()) {
            Config.DisplayMode[] all = Config.DisplayMode.values();
            Config.DisplayMode next = all[(Config.MODE.get().ordinal() + 1) % all.length];
            Config.MODE.set(next);
            Config.MODE.save();
            HealthTracker.INSTANCE.reset();
            message(Component.translatable("ringhealth.message.mode."
                    + next.name().toLowerCase(Locale.ROOT)));
        }

        // B: how it looks (classic -> pixel -> neon)
        while (STYLE_KEY.consumeClick()) {
            Config.Style[] all = Config.Style.values();
            Config.Style next = all[(Config.STYLE.get().ordinal() + 1) % all.length];
            Config.STYLE.set(next);
            Config.STYLE.save();
            message(Component.translatable("ringhealth.message.style",
                    Component.translatable("ringhealth.style." + next.name().toLowerCase(Locale.ROOT))));
        }
    }

    /** ']' / '[' in game: more / less opaque, saved to the config. */
    private static void changeOpacity(double delta) {
        double v = Math.round(Mth.clamp(Config.OPACITY.get() + delta, 0.1, 1.0) * 10.0) / 10.0;
        Config.OPACITY.set(v);
        Config.OPACITY.save();
        message(Component.translatable("ringhealth.message.opacity", (int) Math.round(v * 100)));
    }

    private static void message(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, true);
    }
}
