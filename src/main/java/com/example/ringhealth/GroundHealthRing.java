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

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Mode GROUND_RING: a flat health ring lying on the ground under the mob, like a shadow.
 *  - for flying mobs the ring is projected down onto the ground below them (and gets fainter with height)
 *  - the ring's "12 o'clock" is always the side of the mob farthest from you, filling clockwise as seen from your view
 *  - it is a real 3D object: blocks and the mob's own body hide parts of it correctly
 */
public final class GroundHealthRing {
    private static final int STEPS_FULL = 128;

    private GroundHealthRing() {}

    public static void draw(RenderLevelStageEvent event, Minecraft mc, HealthTracker t) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();

        double ex = t.x(), ey = t.y(), ez = t.z();

        // find the ground below the mob
        Vec3 from = new Vec3(ex, ey + 0.1, ez);
        HitResult hit = mc.level.clip(new ClipContext(
                from, from.add(0.0, -48.0, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
        double gy = hit.getType() == HitResult.Type.MISS ? ey : hit.getLocation().y;
        float height = (float) Math.max(0.0, ey - gy);

        double dx = ex - cam.x;
        double dz = ez - cam.z;
        double dy = gy - cam.y;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float fx, fz;
        if (horiz < 0.01) { fx = 0f; fz = -1f; } else { fx = (float) (dx / horiz); fz = (float) (dz / horiz); }
        float rx = -fz; // "to the right" as seen from the camera
        float rz = fx;

        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float sc = Config.WORLD_SCALE.get().floatValue() * Mth.clamp(dist / 12f, 1f, 2.5f);

        float thick = 0.16f * sc;
        float rMid = Math.max(0.5f, t.bbWidth() * 0.5f + 0.35f) + t.pulse() * 0.08f;
        float r0 = Math.max(0.12f, rMid - thick / 2f);
        float r1 = r0 + thick;
        float o = 0.03f * sc;    // black outline
        float rim = 0.02f * sc;  // light rim

        float a = t.alpha() * Config.OPACITY.get().floatValue()
                * Mth.clamp(1f - height / 24f, 0.4f, 1f);

        float front = t.front();
        float ghost = t.ghost();
        float lift = 0.03f + dist * 0.0012f; // keeps it above the ground surface (no z-fighting)

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(dx, dy + lift, dz);
        Matrix4f m = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();   // real 3D: blocks / the mob hide what is behind them
        RenderSystem.depthMask(false);    // our own layers never fight each other
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        if (Config.GROUND_SHADOW.get()) {
            arc(buf, m, fx, fz, rx, rz, 0f, r1 + o, 0f, 1f, 0f, 0f, 0f, a * 0.30f);          // soft shadow disc
        }
        arc(buf, m, fx, fz, rx, rz, r0 - o - rim, r1 + o + rim, 0f, 1f, 1f, 1f, 1f, a * 0.60f); // light rims
        arc(buf, m, fx, fz, rx, rz, r0 - o, r1 + o, 0f, 1f, 0f, 0f, 0f, a * 0.92f);             // black outline
        arc(buf, m, fx, fz, rx, rz, r0, r1, 0f, 1f, 0.28f, 0.28f, 0.28f, a);                   // empty part

        if (ghost > front) {
            arc(buf, m, fx, fz, rx, rz, r0, r1, front, ghost, 1.0f, 0.82f, 0.30f, a);           // damage trail
        }

        int rgb = t.barRgb();
        arc(buf, m, fx, fz, rx, rz, r0, r1, 0f, front,
                ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a); // health

        float hl = Math.max(0.01f, (r1 - r0) * 0.3f);
        arc(buf, m, fx, fz, rx, rz, r1 - hl, r1, 0f, front, 1f, 1f, 1f, a * 0.30f);            // highlight

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    /**
     * Flat ring segment in the XZ plane. from/to are fractions of a full circle; 0 is the direction (fx, fz)
     * (away from the camera) and the angle grows towards (rx, rz) = the camera's right.
     */
    private static void arc(BufferBuilder buf, Matrix4f m,
                            float fx, float fz, float rx, float rz,
                            float r0, float r1, float from, float to,
                            float r, float g, float b, float a) {
        if (to - from <= 0.0005f || a <= 0.003f || r1 <= r0) return;
        int steps = Math.max(2, (int) Math.ceil(STEPS_FULL * (to - from)));

        float pdx = 0f, pdz = 0f;
        for (int i = 0; i <= steps; i++) {
            double ang = (from + (to - from) * i / steps) * Math.PI * 2.0;
            float c = (float) Math.cos(ang);
            float s = (float) Math.sin(ang);
            float ddx = fx * c + rx * s;
            float ddz = fz * c + rz * s;
            if (i > 0) {
                buf.addVertex(m, pdx * r1, 0f, pdz * r1).setColor(r, g, b, a);
                buf.addVertex(m, pdx * r0, 0f, pdz * r0).setColor(r, g, b, a);
                buf.addVertex(m, ddx * r0, 0f, ddz * r0).setColor(r, g, b, a);
                buf.addVertex(m, ddx * r1, 0f, ddz * r1).setColor(r, g, b, a);
            }
            pdx = ddx;
            pdz = ddz;
        }
    }
}
