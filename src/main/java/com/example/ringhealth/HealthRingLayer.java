package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;

/** GUI modes: CROSSHAIR_RING and HUD_BAR. */
public final class HealthRingLayer implements LayeredDraw.Layer {
    public static final HealthRingLayer INSTANCE = new HealthRingLayer();

    private static final int ARC_STEPS_FULL = 96;

    private HealthRingLayer() {}

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Config.DisplayMode mode = Config.MODE.get();
        if (mode != Config.DisplayMode.CROSSHAIR_RING && mode != Config.DisplayMode.HUD_BAR) return; // not ours

        Minecraft mc = Minecraft.getInstance();
        HealthTracker t = HealthTracker.INSTANCE;
        boolean ring = mode == Config.DisplayMode.CROSSHAIR_RING;

        if (!Config.ENABLED.get()
                || mc.level == null || mc.player == null
                || mc.options.hideGui
                || mc.screen != null
                || (ring && !mc.options.getCameraType().isFirstPerson())) {
            t.reset();
            return;
        }

        float dt = Math.min(delta.getRealtimeDeltaTicks(), 5f);
        t.update(mc, delta.getGameTimeDeltaPartialTick(false), dt);
        if (!t.visible()) return;

        float a = t.alpha() * Config.OPACITY.get().floatValue();

        g.flush(); // everything queued before us must already be on screen
        Matrix4f m = g.pose().last().pose();

        // Own, clean render state: other mods / layers may leave theirs behind.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float cx = g.guiWidth() / 2f;
        float cy = g.guiHeight() / 2f;
        float textX = cx, textY;
        boolean pixel = Config.STYLE.get() == Config.Style.PIXEL;
        boolean numbersInside = false;
        float barTop = 0f, barH = 0f;

        if (ring) {
            float r0 = Config.RADIUS.get();
            float thick = Config.THICKNESS.get() + t.pulse() * 1.5f;
            HealthPainter.render(new RingShape(m, cx, cy, r0, thick), t, a);
            textY = cy + r0 + thick * (1f + HealthPainter.outerExtent() + 0.1f) + 4f;
        } else {
            float w = Config.HUD_WIDTH.get();
            barH = (pixel ? 12f : Config.THICKNESS.get() + 2f) * (1f + 0.25f * t.pulse());
            barTop = cy + Config.HUD_OFFSET_Y.get();
            HealthPainter.render(new HudBarShape(m, cx - w / 2f, barTop, w, barH), t, a);
            numbersInside = pixel;
            textY = barTop + barH * (1f + HealthPainter.outerExtent() + 0.1f) + 3f;
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        int ai = (int) (a * 255f);
        if (ai < 8) return; // vanilla font treats very low alpha as fully opaque
        int color = (ai << 24) | HealthPainter.textRgb(t);
        boolean showName = Config.SHOW_NAME.get();
        boolean showNum = Config.SHOW_NUMBERS.get();
        Font font = mc.font;

        if (numbersInside) {
            if (showNum) {
                float k = Math.max(0.5f, (barH - 3f) / 9f);
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(cx, barTop + barH / 2f - 4.5f * k, 0f);
                ps.scale(k, k, 1f);
                g.drawCenteredString(font, t.numbers(), 0, 0, color);
                ps.popPose();
            }
            if (showName) {
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(cx, barTop - 10f, 0f);
                ps.scale(0.75f, 0.75f, 1f);
                g.drawCenteredString(font, t.name(), 0, 0, color);
                ps.popPose();
            }
        } else if (showName || showNum) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(textX, textY, 0f);
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
    }

    // ------------------------------------------------------------------ shapes

    /** Ring around a point. c: 0 = inner edge, 1 = outer edge. t = 0 is 12 o'clock, clockwise. */
    private static final class RingShape implements HealthPainter.Shape {
        private final Matrix4f m;
        private final float cx, cy, r0, thick;

        RingShape(Matrix4f m, float cx, float cy, float r0, float thick) {
            this.m = m; this.cx = cx; this.cy = cy; this.r0 = r0; this.thick = thick;
        }

        @Override
        public void quad(BufferBuilder b, float t0, float t1, float c0, float c1, int col0, int col1) {
            float rIn = Math.max(0f, r0 + c0 * thick);
            float rOut = Math.max(0f, r0 + c1 * thick);
            int steps = Math.max(1, (int) Math.ceil(ARC_STEPS_FULL * (t1 - t0)));
            float pc = 0f, ps = 0f;
            for (int i = 0; i <= steps; i++) {
                double ang = -Math.PI / 2.0 + (t0 + (t1 - t0) * i / steps) * Math.PI * 2.0;
                float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                if (i > 0) {
                    HealthPainter.vertex(b, m, cx + pc * rOut, cy + ps * rOut, 0f, col1);
                    HealthPainter.vertex(b, m, cx + pc * rIn, cy + ps * rIn, 0f, col0);
                    HealthPainter.vertex(b, m, cx + c * rIn, cy + s * rIn, 0f, col0);
                    HealthPainter.vertex(b, m, cx + c * rOut, cy + s * rOut, 0f, col1);
                }
                pc = c;
                ps = s;
            }
        }
    }

    /** Horizontal bar on the screen. c: 0 = bottom edge, 1 = top edge. */
    private static final class HudBarShape implements HealthPainter.Shape {
        private final Matrix4f m;
        private final float x0, yTop, w, h;

        HudBarShape(Matrix4f m, float x0, float yTop, float w, float h) {
            this.m = m; this.x0 = x0; this.yTop = yTop; this.w = w; this.h = h;
        }

        private float y(float c) { return yTop + h - c * h; }

        @Override
        public void quad(BufferBuilder b, float t0, float t1, float c0, float c1, int col0, int col1) {
            float xa = x0 + t0 * w, xb = x0 + t1 * w;
            HealthPainter.vertex(b, m, xa, y(c1), 0f, col1);
            HealthPainter.vertex(b, m, xb, y(c1), 0f, col1);
            HealthPainter.vertex(b, m, xb, y(c0), 0f, col0);
            HealthPainter.vertex(b, m, xa, y(c0), 0f, col0);
        }

        @Override
        public void cap(BufferBuilder b, float extent, int argb) {
            float top = y(1f + extent), bot = y(-extent), mid = y(0.5f);
            float tip = (1f + 2f * extent) * h * 0.5f;
            // left
            HealthPainter.vertex(b, m, x0, top, 0f, argb);
            HealthPainter.vertex(b, m, x0, bot, 0f, argb);
            HealthPainter.vertex(b, m, x0 - tip, mid, 0f, argb);
            HealthPainter.vertex(b, m, x0 - tip, mid, 0f, argb);
            // right
            HealthPainter.vertex(b, m, x0 + w, bot, 0f, argb);
            HealthPainter.vertex(b, m, x0 + w, top, 0f, argb);
            HealthPainter.vertex(b, m, x0 + w + tip, mid, 0f, argb);
            HealthPainter.vertex(b, m, x0 + w + tip, mid, 0f, argb);
        }
    }
}
