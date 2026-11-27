package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Mode GROUND_RING: a flat health ring around the mob's feet, drawn ON TOP of the world (no depth test),
 * so it stays visible for flying mobs, in water, behind grass, etc.
 * The ring's "12 o'clock" is always the side of the mob farthest from you; it fills clockwise as seen from your view.
 */
public final class GroundHealthRing {
    private static final int STEPS_FULL = 128;

    private GroundHealthRing() {}

    public static void draw(RenderLevelStageEvent event, Minecraft mc, HealthTracker t) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        boolean pixel = Config.STYLE.get() == Config.Style.PIXEL;

        double dx = t.x() - cam.x;
        double dy = t.y() - cam.y;
        double dz = t.z() - cam.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float fx, fz;
        if (horiz < 0.01) { fx = 0f; fz = -1f; } else { fx = (float) (dx / horiz); fz = (float) (dz / horiz); }
        float rx = -fz; // "to the right" as seen from the camera
        float rz = fx;

        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float sc = Config.WORLD_SCALE.get().floatValue() * Mth.clamp(dist / 12f, 1f, 2.5f);

        float thick = 0.16f * sc * (pixel ? 1.4f : 1f);
        float rMid = Math.max(0.5f, t.bbWidth() * 0.5f + 0.35f) + t.pulse() * 0.08f;
        float r0 = Math.max(0.12f, rMid - thick / 2f);

        float a = t.alpha() * Config.OPACITY.get().floatValue();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(dx, dy + 0.05, dz);
        Matrix4f m = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest(); // overlay: visible in the sky, in water, behind blocks
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        HealthPainter.render(new GroundShape(m, fx, fz, rx, rz, r0, thick), t, a);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    /** Flat ring in the XZ plane. c: 0 = inner edge, 1 = outer edge. */
    private static final class GroundShape implements HealthPainter.Shape {
        private final Matrix4f m;
        private final float fx, fz, rx, rz, r0, thick;

        GroundShape(Matrix4f m, float fx, float fz, float rx, float rz, float r0, float thick) {
            this.m = m; this.fx = fx; this.fz = fz; this.rx = rx; this.rz = rz; this.r0 = r0; this.thick = thick;
        }

        @Override
        public void quad(BufferBuilder b, float t0, float t1, float c0, float c1, int col0, int col1) {
            float rIn = Math.max(0f, r0 + c0 * thick);
            float rOut = Math.max(0f, r0 + c1 * thick);
            int steps = Math.max(1, (int) Math.ceil(STEPS_FULL * (t1 - t0)));
            float pdx = 0f, pdz = 0f;
            for (int i = 0; i <= steps; i++) {
                double ang = (t0 + (t1 - t0) * i / steps) * Math.PI * 2.0;
                float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
                float ddx = fx * c + rx * s;
                float ddz = fz * c + rz * s;
                if (i > 0) {
                    HealthPainter.vertex(b, m, pdx * rOut, 0f, pdz * rOut, col1);
                    HealthPainter.vertex(b, m, pdx * rIn, 0f, pdz * rIn, col0);
                    HealthPainter.vertex(b, m, ddx * rIn, 0f, ddz * rIn, col0);
                    HealthPainter.vertex(b, m, ddx * rOut, 0f, ddz * rOut, col1);
                }
                pdx = ddx;
                pdz = ddz;
            }
        }
    }
}
