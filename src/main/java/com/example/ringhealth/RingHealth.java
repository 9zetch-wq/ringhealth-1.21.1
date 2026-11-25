package com.example.ringhealth;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
            "key.ringhealth.toggle_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.ringhealth");

    public RingHealth(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RingHealth::registerLayers);
        modBus.addListener(RingHealth::registerKeys);

        NeoForge.EVENT_BUS.addListener(WorldHealthBar::onRenderLevel);
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
    }

    /** Key V (changeable in Controls): switch between the crosshair ring and the 3D bar. */
    private static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_KEY.consumeClick()) {
            Config.DisplayMode next = Config.MODE.get() == Config.DisplayMode.CROSSHAIR_RING
                    ? Config.DisplayMode.WORLD_BAR
                    : Config.DisplayMode.CROSSHAIR_RING;
            Config.MODE.set(next);
            Config.MODE.save();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String key = next == Config.DisplayMode.WORLD_BAR
                        ? "ringhealth.message.world" : "ringhealth.message.ring";
                mc.player.displayClientMessage(Component.translatable(key), true);
            }
        }
    }
}
