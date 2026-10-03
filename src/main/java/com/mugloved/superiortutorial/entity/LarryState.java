package com.mugloved.superiortutorial.entity;

import com.superior.lib.api.unlock.PlayerUnlockApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * Larry's state for one player. Stored as Superior Lib unlock keys, so it is per player, saved with the world,
 * synced to that player's client, and readable from Story dialogue with the {@code unlocked} condition.
 */
public final class LarryState {
    /** The bargain was struck: the player went to make a weapon. */
    public static final String BARGAIN = "superior_tutorial_larry_bargain";
    /** Either ending happened. Every Larry dialogue is guarded with {@code unless unlocked done}. */
    public static final String DONE = "superior_tutorial_larry_done";
    /** The player ended him. He is no longer drawn for this player. */
    public static final String SLAIN = "superior_tutorial_larry_slain";
    /** The player left him. He is drawn decayed for this player. */
    public static final String LEFT = "superior_tutorial_larry_left";

    private LarryState() {}

    public static boolean has(ServerPlayer player, String key) {
        return PlayerUnlockApi.isUnlocked(player, key);
    }

    /** Client side: the local player has ended him. */
    public static boolean slainHere() {
        return PlayerUnlockApi.isUnlockedClient(SLAIN);
    }

    /** Client side: the local player left him. */
    public static boolean leftHere() {
        return PlayerUnlockApi.isUnlockedClient(LEFT) && !slainHere();
    }
}
