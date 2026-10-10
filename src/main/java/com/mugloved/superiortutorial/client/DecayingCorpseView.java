package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.entity.DecayingCorpse;
import com.mugloved.superiortutorial.entity.DecayingCorpseState;
import net.minecraft.util.Mth;

/**
 * What the local player sees of the Corpse, moment by moment: which animation, how visible, how decayed, how bright
 * his eyes are, and how much his head follows them. All timings follow the Corpse's animation cue sheet.
 */
public final class DecayingCorpseView {
    /**
     * Lengths of the two endings, in seconds. The slay animation runs 2.8 s and holds its last pose while he fades
     * (2.9 to 4.3 s), so the whole ending is seen before he goes; the leave animation is the whole leave ending.
     */
    public static final double SLAY_LENGTH = 4.4;
    public static final double LEAVE_LENGTH = 6.0;
    /** Slay: when he starts to fade, and when he is gone. */
    static final double SLAY_FADE_START = 2.9;
    static final double SLAY_FADE_END = 4.3;
    /** How far up (degrees) he lifts his head when someone is in front of him: his head hangs forward at rest. */
    public static final float LOOK_UP = 12.0f;
    /** Deepest breath of the idle animation (degrees the body leans back), which is full breath glow. */
    private static final float DEEPEST_BREATH = 1.7f;
    /** Strongest the eyes get from breathing alone. */
    private static final float BREATH_GLOW = 0.4f;

    private DecayingCorpseView() {}

    /**
     * Works out his phase for the local player and stores it on him, so his animation controller can read it.
     * Uses the state as of the last client tick (where endings are started), not the keys live: a key can arrive
     * between ticks, and reading it early would show him gone or rotten for a frame before his ending begins.
     */
    public static int update(DecayingCorpse corpse, float partialTick) {
        double t = endingSeconds(corpse, partialTick);
        int settled = TutorialClient.GameEvents.settledState();
        boolean slain = settled >= 0 ? settled == 2 : DecayingCorpseState.slainHere();
        boolean left = settled >= 0 ? settled == 1 : DecayingCorpseState.leftHere();
        int phase;
        if (t >= 0) {
            phase = corpse.clientEnding == DecayingCorpse.PHASE_SLAY
                ? (t < SLAY_LENGTH ? DecayingCorpse.PHASE_SLAY : DecayingCorpse.PHASE_GONE)
                : (t < LEAVE_LENGTH ? DecayingCorpse.PHASE_LEAVE : DecayingCorpse.PHASE_ROTTEN);
        } else if (slain) {
            phase = DecayingCorpse.PHASE_GONE;
        } else if (left) {
            phase = DecayingCorpse.PHASE_ROTTEN;
        } else {
            phase = DecayingCorpse.PHASE_IDLE;
        }
        corpse.clientPhase = phase;
        return phase;
    }

    /** Seconds since the local player's ending began, or -1 when none is playing. */
    public static double endingSeconds(DecayingCorpse corpse, float partialTick) {
        if (corpse.clientEndingStart < 0) return -1;
        return Math.max(0.0, (corpse.level().getGameTime() - corpse.clientEndingStart + partialTick) / 20.0);
    }

    /** How visible he is: 1 normally; he fades away at the end of the slay ending. */
    public static float alpha(DecayingCorpse corpse, float partialTick) {
        if (corpse.clientPhase == DecayingCorpse.PHASE_GONE) return 0.0f;
        if (corpse.clientPhase != DecayingCorpse.PHASE_SLAY) return 1.0f;
        double t = endingSeconds(corpse, partialTick);
        return 1.0f - smooth(t, SLAY_FADE_START, SLAY_FADE_END);
    }

    /** How far the decayed skin has taken over during the leave ending (0 to 1). */
    public static float decay(DecayingCorpse corpse, float partialTick) {
        if (corpse.clientPhase == DecayingCorpse.PHASE_ROTTEN) return 1.0f;
        if (corpse.clientPhase != DecayingCorpse.PHASE_LEAVE) return 0.0f;
        return smooth(endingSeconds(corpse, partialTick), 1.2, 4.5);
    }

    /** Eye glow, 0 to 1. */
    public static float glow(DecayingCorpse corpse, float partialTick) {
        double t = endingSeconds(corpse, partialTick);
        return switch (corpse.clientPhase) {
            case DecayingCorpse.PHASE_IDLE -> breathGlow(corpse);
            // Slay: his eyes light up (80% by 0.15 s), hold while he is ended, and go out between 1.2 and 1.9 s.
            case DecayingCorpse.PHASE_SLAY -> 0.8f * smooth(t, 0.0, 0.15) * (1.0f - smooth(t, 1.2, 1.9));
            // Leave: whatever was there goes out between 0.6 and 1.6 s.
            case DecayingCorpse.PHASE_LEAVE -> corpse.clientGlowAtEnding * (1.0f - smooth(t, 0.6, 1.6));
            default -> 0.0f;
        };
    }

    /** The glow that follows his breath: dark when breathed out, up to 40% at the deepest breath. */
    public static float breathGlow(DecayingCorpse corpse) {
        return BREATH_GLOW * corpse.clientBreath;
    }

    /** Called by the model each frame with how far the body leans from its resting pose, in degrees. */
    public static void breathFromBody(DecayingCorpse corpse, float bodyLeanDegrees) {
        corpse.clientBreath = Mth.clamp(Math.abs(bodyLeanDegrees) / DEEPEST_BREATH, 0.0f, 1.0f);
    }

    /**
     * How much his head follows the player. Fully while waiting. Slay: he keeps looking at his killer while the
     * light is in his eyes and lets go as he goes limp (1.2 to 2.1 s, into the slump). Leave: he lets go as he
     * reaches out (0.2 to 1.0 s).
     */
    public static float headFollow(DecayingCorpse corpse, float partialTick) {
        return switch (corpse.clientPhase) {
            case DecayingCorpse.PHASE_IDLE -> 1.0f;
            case DecayingCorpse.PHASE_SLAY -> 1.0f - smooth(endingSeconds(corpse, partialTick), 1.2, 2.1);
            case DecayingCorpse.PHASE_LEAVE -> 1.0f - smooth(endingSeconds(corpse, partialTick), 0.2, 1.0);
            default -> 0.0f;
        };
    }

    /** How much he is looking at someone right now (0 to 1), smoothed between ticks. */
    public static float engage(DecayingCorpse corpse, float partialTick) {
        return Mth.lerp(partialTick, corpse.clientEngageO, corpse.clientEngage);
    }

    /**
     * Once a tick: eases {@link the Corpse#clientEngage} toward 1 while the local player is within his reach and still
     * waiting on him (or ending him), toward 0 otherwise (about half a second either way).
     */
    static void tickEngage(DecayingCorpse corpse, net.minecraft.world.entity.player.Player player) {
        corpse.clientEngageO = corpse.clientEngage;
        // (During the slay ending he keeps looking at his killer; the head follow lets it go with the slump.)
        boolean watching = (corpse.clientPhase == DecayingCorpse.PHASE_IDLE || corpse.clientPhase == DecayingCorpse.PHASE_SLAY) && player != null && !player.isSpectator()
            && corpse.distanceToSqr(player) <= 8.0 * 8.0;
        float target = watching ? 1.0f : 0.0f;
        corpse.clientEngage += Mth.clamp(target - corpse.clientEngage, -0.1f, 0.1f);
    }

    /** Eased 0 to 1 as t goes from a to b. */
    static float smooth(double t, double a, double b) {
        double x = Mth.clamp((t - a) / (b - a), 0.0, 1.0);
        return (float) (x * x * (3.0 - 2.0 * x));
    }
}
