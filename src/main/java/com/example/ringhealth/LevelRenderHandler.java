package com.example.ringhealth;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Runs once per frame for the world-space modes: updates the animation, then draws the active mode. */
public final class LevelRenderHandler {
    private LevelRenderHandler() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Config.DisplayMode mode = Config.MODE.get();
        if (mode == Config.DisplayMode.CROSSHAIR_RING) return; // the GUI layer handles this one

        Minecraft mc = Minecraft.getInstance();
        HealthTracker t = HealthTracker.INSTANCE;

        if (!Config.ENABLED.get()
                || mc.level == null || mc.player == null
                || mc.options.hideGui
                || mc.screen != null) {
            t.reset();
            return;
        }

        DeltaTracker delta = event.getPartialTick();
        t.update(mc, delta.getGameTimeDeltaPartialTick(false), Math.min(delta.getRealtimeDeltaTicks(), 5f));
        if (!t.visible()) return;

        if (mode == Config.DisplayMode.WORLD_BAR) {
            WorldHealthBar.draw(event, mc, t);
        } else {
            GroundHealthRing.draw(event, mc, t);
        }
    }
}
