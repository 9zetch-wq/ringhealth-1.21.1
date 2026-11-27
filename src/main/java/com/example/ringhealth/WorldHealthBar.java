package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Mode WORLD_BAR: a bar that floats in the world under (or above) the mob you are looking at.
 * It is a billboard (always faces the camera) and gets smaller with distance (with a soft lower limit).
 */
public final class WorldHealthBar {
    private static final float BAR_HEIGHT = 0.10f; // in blocks

    private WorldHealthBar() {}

    public static void draw(RenderLevelStageEvent event, Minecraft mc, HealthTracker t) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        boolean pixel = Config.STYLE.get() == Config.Style.PIXEL;

        float dist = (float) Math.sqrt(
                (t.x() - cam.x) * (t.x() - cam.x)
                        + (t.y() - cam.y) * (t.y() - cam.y)
                        + (t.z() - cam.z) * (t.z() - cam.z));
        float s = Config.WORLD_SCALE.get().floatValue() * Mth.clamp(dist / 10f, 1f, 3f);

        double wy = Config.WORLD_POSITION.get() == Config.WorldPosition.ABOVE
                ? t.y() + t.bbHeight() + 0.30 * s
                : t.y() - 0.18 * s;

        float width = Mth.clamp(t.bbWidth() * 1.1f, 0.7f, 2.5f);
        float h = BAR_HEIGHT * (pixel ? 1.9f : 1f) * (1f + 0.5f * t.pulse());
        float a = t.alpha() * Config.OPACITY.get().floatValue();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(t.x() - cam.x, wy - cam.y, t.z() - cam.z);
        ps.mulPose(camera.rotation()); // billboard
        ps.scale(s, s, s);
        Matrix4f m = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest(); // only the mob you are looking at is shown, so it is always readable
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        HealthPainter.render(new BarShape(m, width, h), t, a);

        drawText(mc, t, m, width, h, a, pixel);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    private static void drawText(Minecraft mc, HealthTracker t, Matrix4f m, float w, float h, float a, boolean pixel) {
        boolean showName = Config.SHOW_NAME.get();
        boolean showNum = Config.SHOW_NUMBERS.get();
        if (!showName && !showNum) return;

        int ai = (int) (a * 255f);
        if (ai < 8) return;
        int color = (ai << 24) | HealthPainter.textRgb(t);

        Font font = mc.font;
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();

        if (pixel) {
            if (showNum) { // numbers inside the bar
                float k = h * 0.70f / 9f;
                Matrix4f tm = new Matrix4f(m).scale(-k, -k, k);
                String str = t.numbers();
                font.drawInBatch(str, -font.width(str) / 2f, -4.5f, color, true, tm, bs,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            if (showName) { // name above the bar
                Matrix4f tm = new Matrix4f(m).translate(0f, h * 0.5f * (1f + 2f * 0.58f) + 0.03f, 0f)
                        .scale(-0.012f, -0.012f, 0.012f);
                font.drawInBatch(t.name(), -font.width(t.name()) / 2f, -9f, color, false, tm, bs,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
        } else {
            Matrix4f tm = new Matrix4f(m)
                    .translate(0f, -(h / 2f) - 0.07f, 0f)
                    .scale(-0.012f, -0.012f, 0.012f);
            float y = 0f;
            if (showName) {
                font.drawInBatch(t.name(), -font.width(t.name()) / 2f, y, color, false, tm, bs,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                y += 10f;
            }
            if (showNum) {
                String str = t.numbers();
                font.drawInBatch(str, -font.width(str) / 2f, y, color, false, tm, bs,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
        }
        bs.endBatch();
    }

    /**
     * Bar in billboard space. After applying the camera rotation local +X points to the viewer's LEFT
     * (same as vanilla name tags), so x is mirrored here. c: 0 = bottom edge, 1 = top edge.
     */
    private static final class BarShape implements HealthPainter.Shape {
        private final Matrix4f m;
        private final float w, h;

        BarShape(Matrix4f m, float w, float h) { this.m = m; this.w = w; this.h = h; }

        private float x(float t) { return -(-w / 2f + t * w); }  // mirrored
        private float y(float c) { return -h / 2f + c * h; }

        @Override
        public void quad(BufferBuilder b, float t0, float t1, float c0, float c1, int col0, int col1) {
            HealthPainter.vertex(b, m, x(t0), y(c1), 0f, col1);
            HealthPainter.vertex(b, m, x(t1), y(c1), 0f, col1);
            HealthPainter.vertex(b, m, x(t1), y(c0), 0f, col0);
            HealthPainter.vertex(b, m, x(t0), y(c0), 0f, col0);
        }

        @Override
        public void cap(BufferBuilder b, float extent, int argb) {
            float top = y(1f + extent), bot = y(-extent), mid = y(0.5f);
            float tip = (1f + 2f * extent) * h * 0.5f;
            // screen-left cap (t = 0 side)
            HealthPainter.vertex(b, m, x(0f), top, 0f, argb);
            HealthPainter.vertex(b, m, x(0f), bot, 0f, argb);
            HealthPainter.vertex(b, m, -(-w / 2f - tip), mid, 0f, argb);
            HealthPainter.vertex(b, m, -(-w / 2f - tip), mid, 0f, argb);
            // screen-right cap (t = 1 side)
            HealthPainter.vertex(b, m, x(1f), bot, 0f, argb);
            HealthPainter.vertex(b, m, x(1f), top, 0f, argb);
            HealthPainter.vertex(b, m, -(w / 2f + tip), mid, 0f, argb);
            HealthPainter.vertex(b, m, -(w / 2f + tip), mid, 0f, argb);
        }
    }
}
