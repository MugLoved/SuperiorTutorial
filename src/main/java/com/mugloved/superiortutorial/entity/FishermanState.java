package com.mugloved.superiortutorial.entity;

import com.superior.lib.api.unlock.PlayerUnlockApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * Haskel's quest state for one player, stored as Superior Lib unlock keys like the Corpse's (per player, saved with
 * the world, readable in Story dialogue with {@code unlocked}).
 */
public final class FishermanState {
    /** The player has met him (any first-meeting choice). */
    public static final String MET = "superior_tutorial_fisherman_met";
    /** The player took the deal: fish his crate out of the pond. */
    public static final String DEAL = "superior_tutorial_fisherman_deal";
    /** The crate was handed in and the map and the Haskelsack given. */
    public static final String DONE = "superior_tutorial_fisherman_done";

    private FishermanState() {}

    public static boolean has(ServerPlayer player, String key) {
        return PlayerUnlockApi.isUnlocked(player, key);
    }

    public static void set(ServerPlayer player, String key) {
        PlayerUnlockApi.unlock(player, key, key, false);
    }
}
