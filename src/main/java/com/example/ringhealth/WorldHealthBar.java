package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Mode WORLD_BAR: a 3D bar that floats in the world under (or above) the mob you are looking at.
 * It is a billboard (always faces the camera), gets smaller with distance like a real object
 * (with a soft lower limit so it stays readable), and has a little bevel for a 3D look.
 */
public final class WorldHealthBar {
    private static final float BAR_HEIGHT = 0.10f; // in blocks

    private WorldHealthBar() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        HealthTracker t = HealthTracker.INSTANCE;

        if (!Config.ENABLED.get()
                || Config.MODE.get() != Config.DisplayMode.WORLD_BAR
                || mc.level == null || mc.player == null
                || mc.options.hideGui
                || mc.screen != null) {
            t.reset();
            return;
        }

        DeltaTracker delta = event.getPartialTick();
        t.update(mc, delta.getGameTimeDeltaPartialTick(false), Math.min(delta.getRealtimeDeltaTicks(), 5f));
        if (!t.visible()) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();

        float dist = (float) Math.sqrt(
                (t.x() - cam.x) * (t.x() - cam.x)
                        + (t.y() - cam.y) * (t.y() - cam.y)
                        + (t.z() - cam.z) * (t.z() - cam.z));
        float s = Config.WORLD_SCALE.get().floatValue() * Mth.clamp(dist / 10f, 1f, 3f);

        double wy = Config.WORLD_POSITION.get() == Config.WorldPosition.ABOVE
                ? t.y() + t.bbHeight() + 0.30 * s
                : t.y() - 0.18 * s;

        float width = Mth.clamp(t.bbWidth() * 1.1f, 0.7f, 2.5f);
        float h = BAR_HEIGHT * (1f + 0.5f * t.pulse());
        float b = 0.015f; // border
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
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float x0 = -width / 2f;
        float x1 = width / 2f;
        float yb = -h / 2f;
        float yt = h / 2f;
        float front = t.front();
        float ghost = t.ghost();
        float xf = x0 + width * front;
        float xg = x0 + width * ghost;

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        rect(buf, m, x0 - b, yb - b, x1 + b, yt + b, 0f, 0f, 0f, a * 0.65f);        // outline
        rect(buf, m, x0, yb, x1, yt, 0.12f, 0.12f, 0.12f, a * 0.80f);               // track
        if (ghost > front) {
            rect(buf, m, xf, yb, xg, yt, 1.0f, 0.82f, 0.30f, a);                    // damage trail
        }
        int rgb = t.barRgb();
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float bl = (rgb & 255) / 255f;
        rect(buf, m, x0, yb, xf, yt, r, g, bl, a);                                  // health
        rect(buf, m, x0, 0f, xf, yt, 1f, 1f, 1f, a * 0.22f);                        // bevel: light top
        rect(buf, m, x0, yb, xf, 0f, 0f, 0f, 0f, a * 0.18f);                        // bevel: dark bottom

        BufferUploader.drawWithShader(buf.buildOrThrow());

        drawText(mc, t, m, h, a);

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    private static void drawText(Minecraft mc, HealthTracker t, Matrix4f m, float h, float a) {
        boolean showName = Config.SHOW_NAME.get();
        boolean showNum = Config.SHOW_NUMBERS.get();
        if (!showName && !showNum) return;

        int ai = (int) (a * 255f);
        if (ai < 8) return;
        int color = (ai << 24) | 0xFFFFFF;

        Font font = mc.font;
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        Matrix4f tm = new Matrix4f(m)
                .translate(0f, -(h / 2f) - 0.05f, 0f)
                .scale(-0.012f, -0.012f, 0.012f);

        float y = 0f;
        if (showName) {
            font.drawInBatch(t.name(), -font.width(t.name()) / 2f, y, color, false, tm, bs,
                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            y += 10f;
        }
        if (showNum) {
            String s = t.numbers();
            font.drawInBatch(s, -font.width(s) / 2f, y, color, false, tm, bs,
                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        }
        bs.endBatch();
    }

    /**
     * Rectangle in billboard space. x is given as "screen right" and is mirrored here, because after
     * applying the camera rotation local +X points to the viewer's left (same as vanilla name tags).
     */
    private static void rect(BufferBuilder b, Matrix4f m,
                             float x0, float y0, float x1, float y1,
                             float r, float g, float bl, float a) {
        if (x1 - x0 <= 0.0001f || a <= 0.003f) return;
        b.addVertex(m, -x0, y0, 0f).setColor(r, g, bl, a);
        b.addVertex(m, -x1, y0, 0f).setColor(r, g, bl, a);
        b.addVertex(m, -x1, y1, 0f).setColor(r, g, bl, a);
        b.addVertex(m, -x0, y1, 0f).setColor(r, g, bl, a);
    }
}
