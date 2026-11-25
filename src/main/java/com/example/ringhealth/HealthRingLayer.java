package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;

/** Mode CROSSHAIR_RING: draws the health ring around the crosshair. */
public final class HealthRingLayer implements LayeredDraw.Layer {
    public static final HealthRingLayer INSTANCE = new HealthRingLayer();

    private static final int ARC_STEPS_FULL = 96;

    private HealthRingLayer() {}

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        HealthTracker t = HealthTracker.INSTANCE;

        if (!Config.ENABLED.get()
                || Config.MODE.get() != Config.DisplayMode.CROSSHAIR_RING
                || mc.level == null || mc.player == null
                || mc.options.hideGui
                || mc.screen != null
                || !mc.options.getCameraType().isFirstPerson()) {
            t.reset();
            return;
        }

        float dt = Math.min(delta.getRealtimeDeltaTicks(), 5f);
        t.update(mc, delta.getGameTimeDeltaPartialTick(false), dt);

        if (t.visible()) draw(g, mc, t);
    }

    private void draw(GuiGraphics g, Minecraft mc, HealthTracker t) {
        float cx = g.guiWidth() / 2f;
        float cy = g.guiHeight() / 2f;

        float r0 = Config.RADIUS.get();
        float r1 = r0 + Config.THICKNESS.get() + t.pulse() * 1.5f;
        float a = t.alpha() * Config.OPACITY.get().floatValue();
        float front = t.front();
        float ghost = t.ghost();

        g.flush();
        Matrix4f m = g.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // dark outline + track
        arc(m, cx, cy, r0 - 1f, r1 + 1f, 0f, 1f, 0f, 0f, 0f, a * 0.45f);
        arc(m, cx, cy, r0, r1, 0f, 1f, 0.12f, 0.12f, 0.12f, a * 0.55f);

        // damage trail (the part that "falls off")
        if (ghost > front) {
            arc(m, cx, cy, r0, r1, front, ghost, 1.0f, 0.82f, 0.30f, a * 0.95f);
        }

        // current health
        int rgb = t.barRgb();
        arc(m, cx, cy, r0, r1, 0f, front,
                ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        drawText(g, mc, t, cx, cy + r1 + 5f, a);
    }

    private void drawText(GuiGraphics g, Minecraft mc, HealthTracker t, float x, float y, float a) {
        boolean showName = Config.SHOW_NAME.get();
        boolean showNum = Config.SHOW_NUMBERS.get();
        if (!showName && !showNum) return;

        int ai = (int) (a * 255f);
        if (ai < 8) return; // vanilla font treats very low alpha as fully opaque
        int color = (ai << 24) | 0xFFFFFF;

        Font font = mc.font;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0f);
        ps.scale(0.75f, 0.75f, 1f);
        int line = 0;
        if (showName) {
            g.drawCenteredString(font, t.name(), 0, line, color);
            line += 10;
        }
        if (showNum) {
            g.drawCenteredString(font, t.numbers(), 0, line, color);
        }
        ps.popPose();
    }

    /** Filled ring segment. from/to are fractions of a full circle, starting at 12 o'clock, clockwise. */
    private static void arc(Matrix4f m, float cx, float cy, float r0, float r1,
                            float from, float to, float r, float g, float b, float a) {
        if (to - from <= 0.0005f || a <= 0.003f) return;
        int steps = Math.max(2, (int) Math.ceil(ARC_STEPS_FULL * (to - from)));

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float t = from + (to - from) * i / steps;
            double ang = -Math.PI / 2.0 + t * Math.PI * 2.0;
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            buf.addVertex(m, cx + cos * r1, cy + sin * r1, 0f).setColor(r, g, b, a);
            buf.addVertex(m, cx + cos * r0, cy + sin * r0, 0f).setColor(r, g, b, a);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
    }
}
