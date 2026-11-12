package com.mugloved.superiortutorial.entity;

import com.superior.lib.api.unlock.PlayerUnlockApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Decaying Corpse's state for one player. Stored as Superior Lib unlock keys, so it is per player, saved with the world,
 * synced to that player's client, and readable from Story dialogue with the {@code unlocked} condition.
 */
public final class DecayingCorpseState {
    /** The player heard him out (step 1) and is now at step 2, where they come back to if they leave. */
    public static final String STEP2 = "superior_tutorial_decaying_corpse_step2";
    /** The bargain was struck (step 2): the satchel was handed over and the player went to make a weapon. */
    public static final String BARGAIN = "superior_tutorial_decaying_corpse_bargain";
    /** The player denied his request once (step 5), so armed they come back to step 6. */
    public static final String REFUSED = "superior_tutorial_decaying_corpse_refused";
    /** Either ending happened. Every Corpse dialogue is guarded with {@code unless unlocked done}. */
    public static final String DONE = "superior_tutorial_decaying_corpse_done";
    /** The player ended him. He is no longer drawn for this player. */
    public static final String SLAIN = "superior_tutorial_decaying_corpse_slain";
    /** The player left him. He is drawn decayed for this player. */
    public static final String LEFT = "superior_tutorial_decaying_corpse_left";

    private DecayingCorpseState() {}

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
