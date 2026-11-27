package com.example.ringhealth;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.util.Mth;

/**
 * Draws the health bar in the chosen Style onto ANY shape (crosshair ring, HUD bar, 3D bar, ground ring).
 *
 * A shape is described by two coordinates:
 *   t = progress along the bar, 0..1 (angle for rings, x for bars)
 *   c = cross-section, 0 = inner / bottom edge of the bar body, 1 = outer / top edge.
 *       Values below 0 or above 1 are outside the body (frames, outlines, glow), as a fraction of the body thickness.
 *
 * The caller sets up the GL state (blend, shader, depth) before calling render().
 */
public final class HealthPainter {

    public interface Shape {
        /** Adds a band [t0,t1] x [c0,c1]; col0 is the colour at the c0 edge, col1 at the c1 edge (ARGB). */
        void quad(BufferBuilder b, float t0, float t1, float c0, float c1, int col0, int col1);

        /** Pointed end caps (bars only). extent = how far the cap reaches across the bar, in body thickness. */
        default void cap(BufferBuilder b, float extent, int argb) {}
    }

    private HealthPainter() {}

    // ------------------------------------------------------------------ entry points

    public static void render(Shape shape, HealthTracker t, float a) {
        Config.Style style = Config.STYLE.get();

        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        switch (style) {
            case PIXEL -> pixel(shape, t, a, b);
            case NEON -> neon(shape, t, a, b);
            default -> classic(shape, t, a, b);
        }
        flush(b);

        if (style == Config.Style.NEON) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder g = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            neonGlow(shape, t, a, g);
            flush(g);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** Colour of the numbers / name text (RGB). */
    public static int textRgb(HealthTracker t) {
        return Config.STYLE.get() == Config.Style.NEON ? t.barRgb() : 0xFFFFFF;
    }

    /** Frames of the PIXEL style are taller than the bar body: how much extra room text below needs. */
    public static float outerExtent() {
        return Config.STYLE.get() == Config.Style.PIXEL ? 0.6f : 0.5f;
    }

    /** Helper for shape implementations. */
    public static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float z, int argb) {
        b.addVertex(m, x, y, z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    // ------------------------------------------------------------------ CLASSIC

    private static void classic(Shape s, HealthTracker t, float a, BufferBuilder b) {
        float front = t.front(), ghost = t.ghost();
        int hp = t.barRgb();

        solid(s, b, 0f, 1f, -0.5f, 1.5f, 0xFFFFFF, a * 0.60f);   // light rim
        solid(s, b, 0f, 1f, -0.3f, 1.3f, 0x000000, a * 0.92f);    // black outline
        solid(s, b, 0f, 1f, 0f, 1f, 0x474747, a);                  // empty part
        if (ghost > front) solid(s, b, front, ghost, 0f, 1f, 0xFFD14D, a);   // damage trail
        solid(s, b, 0f, front, 0f, 1f, hp, a);                     // health
        solid(s, b, 0f, front, 0.7f, 1f, 0xFFFFFF, a * 0.30f);     // highlight
        solid(s, b, 0f, front, 0f, 0.25f, 0x000000, a * 0.16f);    // shade
    }

    // ------------------------------------------------------------------ PIXEL (metal frame, red fill, gold trail)

    private static void pixel(Shape s, HealthTracker t, float a, BufferBuilder b) {
        float front = t.front(), ghost = t.ghost();
        float fl = t.flash() * 0.75f;
        final int DARK = 0x151515;

        // frame: dark outline, shaded lower frame, lit upper frame, pointed end caps
        solid(s, b, 0f, 1f, -0.58f, 1.58f, DARK, a);
        s.cap(b, 0.58f, argb(DARK, a));
        solid(s, b, 0f, 1f, -0.46f, 0f, 0x6B6B6B, a);
        solid(s, b, 0f, 1f, 1f, 1.46f, 0xB5B5B5, a);
        s.cap(b, 0.46f, argb(0x8D8D8D, a));

        // empty part (dark maroon)
        solid(s, b, 0f, 1f, 0f, 0.5f, 0x2B0C0C, a);
        solid(s, b, 0f, 1f, 0.5f, 1f, 0x431414, a);

        // damage trail (gold)
        if (ghost > front) {
            solid(s, b, front, ghost, 0f, 0.5f, 0xC8A03F, a);
            solid(s, b, front, ghost, 0.5f, 1f, 0xF1D37C, a);
        }

        // health (red, banded like pixel art)
        if (front > 0f) {
            solid(s, b, 0f, front, 0f, 0.34f, HealthTracker.lerpRgb(0x7E1D1D, 0xFFFFFF, fl), a);
            solid(s, b, 0f, front, 0.34f, 0.72f, HealthTracker.lerpRgb(0xB02E2E, 0xFFFFFF, fl), a);
            solid(s, b, 0f, front, 0.72f, 1f, HealthTracker.lerpRgb(0xD64A4A, 0xFFFFFF, fl), a);
            solid(s, b, 0f, front, 0.88f, 1f, 0xFF9A9A, a * 0.35f);
        }
    }

    // ------------------------------------------------------------------ NEON (segmented holo bar, glow, hot core)

    private static final int NEON_SEGMENTS = 10;
    private static final float NEON_GAP = 0.22f;

    private static void neon(Shape s, HealthTracker t, float a, BufferBuilder b) {
        float front = t.front(), ghost = t.ghost();
        int hp = t.barRgb();

        solid(s, b, 0f, 1f, -0.35f, 1.35f, 0x04070D, a * 0.65f);   // dark backing

        for (int k = 0; k < NEON_SEGMENTS; k++) {
            float s0 = (k + NEON_GAP / 2f) / NEON_SEGMENTS;
            float s1 = (k + 1f - NEON_GAP / 2f) / NEON_SEGMENTS;

            solid(s, b, s0, s1, 0f, 1f, hp, a * 0.17f);            // empty segment, dimly lit

            if (ghost > front) {                                    // damage trail: white-hot
                solid(s, b, Math.max(s0, front), Math.min(s1, ghost), 0f, 1f, 0xFFF1C4, a * 0.90f);
            }

            float f1 = Math.min(s1, front);
            if (f1 > s0) {
                solid(s, b, s0, f1, 0f, 1f, hp, a);                 // lit segment
                solid(s, b, s0, f1, 0.36f, 0.64f, 0xFFFFFF, a * 0.55f); // hot core
            }
        }
    }

    private static void neonGlow(Shape s, HealthTracker t, float a, BufferBuilder b) {
        float front = t.front(), ghost = t.ghost();
        if (front <= 0.002f) return;
        int hp = t.barRgb();

        // heartbeat when almost dead
        float pulse = front < 0.3f ? 0.65f + 0.35f * (float) Math.sin(System.nanoTime() / 1.0e9 * 9.0) : 1f;
        float ga = a * 0.5f * pulse;

        s.quad(b, 0f, front, -1.1f, 0f, argb(hp, 0f), argb(hp, ga));
        s.quad(b, 0f, front, 1f, 2.1f, argb(hp, ga), argb(hp, 0f));

        if (ghost > front) {
            float tg = a * 0.35f;
            s.quad(b, front, ghost, -0.8f, 0f, argb(0xFFE9A8, 0f), argb(0xFFE9A8, tg));
            s.quad(b, front, ghost, 1f, 1.8f, argb(0xFFE9A8, tg), argb(0xFFE9A8, 0f));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void solid(Shape s, BufferBuilder b, float t0, float t1, float c0, float c1, int rgb, float alpha) {
        if (t1 - t0 <= 0.0004f || alpha <= 0.004f) return;
        int c = argb(rgb, alpha);
        s.quad(b, t0, t1, c0, c1, c, c);
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void flush(BufferBuilder b) {
        MeshData mesh = b.build(); // null when nothing was added
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}
