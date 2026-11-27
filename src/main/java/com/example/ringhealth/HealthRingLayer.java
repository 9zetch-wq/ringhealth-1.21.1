package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
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

        if (Config.MODE.get() != Config.DisplayMode.CROSSHAIR_RING) return; // another mode owns the tracker

        if (!Config.ENABLED.get()
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
        float o = 1.5f; // outline width

        g.flush(); // everything queued before us must already be on screen
        Matrix4f m = g.pose().last().pose();

        // Own, clean render state: other mods / layers may leave theirs behind.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest(); // draw order decides, no z-fighting between our own arcs
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Outline sandwich: light rim | black outline | bar. Readable on dark caves AND bright snow/sky.
        arc(buf, m, cx, cy, r0 - o - 1f, r1 + o + 1f, 0f, 1f, 1f, 1f, 1f, a * 0.60f);  // light rims
        arc(buf, m, cx, cy, r0 - o, r1 + o, 0f, 1f, 0f, 0f, 0f, a * 0.92f);            // black outline
        arc(buf, m, cx, cy, r0, r1, 0f, 1f, 0.28f, 0.28f, 0.28f, a);                  // empty part

        // damage trail (the part that "falls off")
        if (ghost > front) {
            arc(buf, m, cx, cy, r0, r1, front, ghost, 1.0f, 0.82f, 0.30f, a);
        }

        // current health
        int rgb = t.barRgb();
        arc(buf, m, cx, cy, r0, r1, 0f, front,
                ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);

        // soft highlight along the outer edge of the filled part
        float hl = Math.max(1f, (r1 - r0) * 0.3f);
        arc(buf, m, cx, cy, r1 - hl, r1, 0f, front, 1f, 1f, 1f, a * 0.30f);

        MeshData mesh = buf.build(); // null if nothing was added
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        drawText(g, mc, t, cx, cy + r1 + o + 5f, a);
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

    /** Filled ring segment (as quads). from/to are fractions of a full circle, from 12 o'clock, clockwise. */
    private static void arc(BufferBuilder buf, Matrix4f m, float cx, float cy, float r0, float r1,
                            float from, float to, float r, float g, float b, float a) {
        if (to - from <= 0.0005f || a <= 0.003f) return;
        int steps = Math.max(2, (int) Math.ceil(ARC_STEPS_FULL * (to - from)));

        float prevCos = 0f, prevSin = 0f;
        for (int i = 0; i <= steps; i++) {
            float t = from + (to - from) * i / steps;
            double ang = -Math.PI / 2.0 + t * Math.PI * 2.0;
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            if (i > 0) {
                buf.addVertex(m, cx + prevCos * r1, cy + prevSin * r1, 0f).setColor(r, g, b, a);
                buf.addVertex(m, cx + prevCos * r0, cy + prevSin * r0, 0f).setColor(r, g, b, a);
                buf.addVertex(m, cx + cos * r0, cy + sin * r0, 0f).setColor(r, g, b, a);
                buf.addVertex(m, cx + cos * r1, cy + sin * r1, 0f).setColor(r, g, b, a);
            }
            prevCos = cos;
            prevSin = sin;
        }
    }
}
