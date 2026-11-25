package com.example.ringhealth;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public enum DisplayMode {
        /** Ring around the crosshair in the middle of the screen. */
        CROSSHAIR_RING,
        /** 3D bar floating in the world next to the mob. */
        WORLD_BAR
    }

    public enum WorldPosition {
        BELOW,
        ABOVE
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED =
            B.comment("Master switch").define("enabled", true);
    public static final ModConfigSpec.EnumValue<DisplayMode> MODE =
            B.comment("CROSSHAIR_RING = ring around the crosshair, WORLD_BAR = 3D bar next to the mob")
                    .defineEnum("mode", DisplayMode.CROSSHAIR_RING);
    public static final ModConfigSpec.IntValue RANGE =
            B.comment("How far (in blocks) a mob can be to show its health")
                    .defineInRange("range", 40, 4, 128);
    public static final ModConfigSpec.DoubleValue OPACITY =
            B.comment("Overall opacity")
                    .defineInRange("opacity", 0.9D, 0.1D, 1.0D);
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
            B.comment("[Ring mode] ring thickness in GUI pixels")
                    .defineInRange("thickness", 3, 1, 10);

    // 3D bar
    public static final ModConfigSpec.EnumValue<WorldPosition> WORLD_POSITION =
            B.comment("[World mode] place the bar under or above the mob")
                    .defineEnum("worldPosition", WorldPosition.BELOW);
    public static final ModConfigSpec.DoubleValue WORLD_SCALE =
            B.comment("[World mode] size multiplier of the 3D bar")
                    .defineInRange("worldScale", 1.0D, 0.5D, 3.0D);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
