package com.example.ringhealth;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    /** WHERE the health is shown. */
    public enum DisplayMode {
        /** Ring around the crosshair in the middle of the screen. */
        CROSSHAIR_RING,
        /** Flat bar on the screen, under the crosshair. */
        HUD_BAR,
        /** 3D bar floating in the world next to the mob. */
        WORLD_BAR,
        /** Flat ring around the mob's feet, drawn on top of the world (visible in sky and water too). */
        GROUND_RING
    }

    /** HOW it looks. Works in every display mode. */
    public enum Style {
        /** Clean outlined bar with health colours. */
        CLASSIC,
        /** Metal frame, red fill, golden damage trail, numbers inside the bar. */
        PIXEL,
        /** Segmented glowing "holo" bar with a hot core and a pulse at low health. */
        NEON
    }

    public enum ColorScheme {
        /** Bright red -> yellow -> green. */
        VIVID,
        /** Red -> yellow -> cyan: stands out on grass, good for colour-blind players. */
        CONTRAST,
        /** White bar that turns red when the mob is almost dead. */
        WHITE
    }

    public enum WorldPosition {
        BELOW,
        ABOVE
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED =
            B.comment("Master switch").define("enabled", true);
    public static final ModConfigSpec.EnumValue<DisplayMode> MODE =
            B.comment("CROSSHAIR_RING, HUD_BAR, WORLD_BAR or GROUND_RING")
                    .defineEnum("mode", DisplayMode.CROSSHAIR_RING);
    public static final ModConfigSpec.EnumValue<Style> STYLE =
            B.comment("CLASSIC, PIXEL (framed bar with numbers inside) or NEON (glowing segments)")
                    .defineEnum("style", Style.CLASSIC);
    public static final ModConfigSpec.IntValue RANGE =
            B.comment("How far (in blocks) a mob can be to show its health")
                    .defineInRange("range", 40, 4, 128);
    public static final ModConfigSpec.DoubleValue OPACITY =
            B.comment("Overall opacity")
                    .defineInRange("opacity", 1.0D, 0.1D, 1.0D);
    public static final ModConfigSpec.EnumValue<ColorScheme> COLOR_SCHEME =
            B.comment("Health colours for CLASSIC and NEON: VIVID, CONTRAST or WHITE (PIXEL is always red)")
                    .defineEnum("colorScheme", ColorScheme.VIVID);
    public static final ModConfigSpec.BooleanValue SHOW_NUMBERS =
            B.comment("Show 'current / max' text").define("showNumbers", true);
    public static final ModConfigSpec.BooleanValue SHOW_NAME =
            B.comment("Show the mob name").define("showName", false);
    public static final ModConfigSpec.BooleanValue SHOW_PLAYERS =
            B.comment("Also show the bar when looking at other players").define("showPlayers", false);

    // crosshair ring
    public static final ModConfigSpec.IntValue RADIUS =
            B.comment("[Ring mode] inner radius of the ring in GUI pixels")
                    .defineInRange("radius", 16, 8, 64);
    public static final ModConfigSpec.IntValue THICKNESS =
            B.comment("[Ring / HUD modes] thickness in GUI pixels")
                    .defineInRange("thickness", 5, 1, 10);

    // HUD bar
    public static final ModConfigSpec.IntValue HUD_WIDTH =
            B.comment("[HUD bar mode] width in GUI pixels")
                    .defineInRange("hudWidth", 120, 40, 400);
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y =
            B.comment("[HUD bar mode] vertical offset from the crosshair in GUI pixels (negative = above)")
                    .defineInRange("hudOffsetY", 40, -300, 300);

    // world modes
    public static final ModConfigSpec.EnumValue<WorldPosition> WORLD_POSITION =
            B.comment("[World bar mode] place the bar under or above the mob")
                    .defineEnum("worldPosition", WorldPosition.BELOW);
    public static final ModConfigSpec.DoubleValue WORLD_SCALE =
            B.comment("[World bar / ground ring modes] size multiplier")
                    .defineInRange("worldScale", 1.0D, 0.5D, 3.0D);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
