package com.example.ringhealth;

import java.util.Locale;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the mob you are looking at and runs the health animation.
 * Shared by the crosshair ring and the 3D world bar - only the active one calls update() every frame.
 *
 * Animation model (times in ticks, 20 ticks = 1 s):
 *  - front : real health fraction (snaps down on damage, eases up on heal)
 *  - ghost : the "damage trail" - stays GHOST_DELAY ticks after a hit, then drains smoothly down to front
 *  - flash : short white flash after a hit
 *  - pulse : the bar briefly grows after a hit
 *  - alpha : fade in / fade out when you start / stop looking at a mob
 */
public final class HealthTracker {
    public static final HealthTracker INSTANCE = new HealthTracker();

    private static final float GHOST_DELAY = 10f;
    private static final float LINGER = 4f;

    private int entityId = -1;
    private float hp, max;
    private float prevFrac, front, ghost;
    private float ghostDelay, flash, pulse, alpha, lost;
    private double x, y, z;
    private float bbWidth = 0.6f, bbHeight = 1.8f;
    private Component name = Component.empty();

    private HealthTracker() {}

    // ---------------------------------------------------------------- getters

    public boolean visible() { return alpha > 0.01f && entityId != -1; }
    public float alpha() { return alpha; }
    public float front() { return front; }
    public float ghost() { return ghost; }
    public float flash() { return flash; }
    public float pulse() { return pulse; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float bbWidth() { return bbWidth; }
    public float bbHeight() { return bbHeight; }
    public Component name() { return name; }
    public String numbers() { return fmt(hp) + " / " + fmt(max); }

    /** Bar colour (green -> yellow -> red), blended towards white while flashing. Packed 0xRRGGBB. */
    public int barRgb() {
        int rgb = Mth.hsvToRgb(Mth.clamp(front, 0f, 1f) * 0.33f, 0.85f, 1.0f);
        float fl = flash * 0.75f;
        int r = (int) Mth.lerp(fl, (rgb >> 16) & 255, 255);
        int g = (int) Mth.lerp(fl, (rgb >> 8) & 255, 255);
        int b = (int) Mth.lerp(fl, rgb & 255, 255);
        return (r << 16) | (g << 8) | b;
    }

    public void reset() {
        alpha = 0f;
        entityId = -1;
    }

    // ---------------------------------------------------------------- update

    public void update(Minecraft mc, float partial, float dt) {
        update(findTarget(mc, partial), partial, dt);
    }

    private void update(LivingEntity t, float partial, float dt) {
        if (t != null) {
            float m = Math.max(t.getMaxHealth(), 0.0001f);
            float h = t.getHealth();
            if (Float.isNaN(h)) h = 0f;
            h = Mth.clamp(h, 0f, m);
            float f = h / m;

            if (t.getId() != entityId) {
                entityId = t.getId();
                front = ghost = prevFrac = f;
                ghostDelay = flash = pulse = 0f;
            } else if (f < prevFrac - 1e-4f) {
                ghost = Math.max(ghost, Math.max(front, prevFrac));
                front = f;
                ghostDelay = GHOST_DELAY;
                flash = 1f;
                pulse = 1f;
            }

            prevFrac = f;
            hp = h;
            max = m;
            name = t.getDisplayName();

            Vec3 p = t.getPosition(partial);
            x = p.x;
            y = p.y;
            z = p.z;
            bbWidth = t.getBbWidth();
            bbHeight = t.getBbHeight();

            lost = 0f;
            alpha = Math.min(1f, alpha + dt * 0.4f);
        } else {
            lost += dt;
            if (lost > LINGER) alpha = Math.max(0f, alpha - dt * 0.25f);
            if (alpha <= 0f) entityId = -1;
        }

        if (front < prevFrac) {
            front += (prevFrac - front) * (1f - (float) Math.exp(-0.35f * dt));
            if (prevFrac - front < 0.002f) front = prevFrac;
        }

        if (ghost < front) ghost = front;
        if (ghostDelay > 0f) {
            ghostDelay -= dt;
        } else if (ghost > front) {
            float step = Math.max((ghost - front) * (1f - (float) Math.exp(-0.10f * dt)), 0.003f * dt);
            ghost = Math.max(front, ghost - step);
        }

        flash = Math.max(0f, flash - dt / 5f);
        pulse = Math.max(0f, pulse - dt / 8f);
    }

    // ---------------------------------------------------------------- targeting

    /** Own ray-cast, so it works much farther than the vanilla 3-block interaction range. */
    private static LivingEntity findTarget(Minecraft mc, float partial) {
        Entity cam = mc.getCameraEntity();
        if (cam == null || mc.level == null || mc.player == null) return null;

        double range = Config.RANGE.get();
        Vec3 eye = cam.getEyePosition(partial);
        Vec3 look = cam.getViewVector(partial);
        Vec3 end = eye.add(look.scale(range));

        double maxDistSqr = range * range;
        HitResult blockHit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistSqr = blockHit.getLocation().distanceToSqr(eye);
        }

        boolean showPlayers = Config.SHOW_PLAYERS.get();
        Predicate<Entity> filter = e ->
                e instanceof LivingEntity
                        && e != cam
                        && !(e instanceof ArmorStand)
                        && e.isPickable()
                        && !e.isSpectator()
                        && !e.isInvisibleTo(mc.player)
                        && (showPlayers || !(e instanceof Player));

        AABB box = cam.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(cam, eye, end, box, filter, maxDistSqr);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static String fmt(float v) {
        if (v >= 1000f) return String.format(Locale.ROOT, "%.1fk", v / 1000f);
        float r = Math.round(v * 10f) / 10f;
        if (r == Math.rint(r)) return Integer.toString((int) r);
        return String.format(Locale.ROOT, "%.1f", r);
    }
}
