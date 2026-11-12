package com.mugloved.superiortutorial;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/superior_tutorial-common.toml */
public final class TutorialConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PLACE_COVE;
    public static final ForgeConfigSpec.BooleanValue START_IN_CAVE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        PLACE_COVE = builder
            .comment("Place the tutorial cave and cove near spawn when a new world is created.")
            .define("placeCoveInNewWorlds", true);
        START_IN_CAVE = builder
            .comment("Put each player in the tutorial cave the first time they join (needs the cove to be placed).")
            .define("startPlayersInCave", true);
        SPEC = builder.build();
    }

    private TutorialConfig() {}
}
