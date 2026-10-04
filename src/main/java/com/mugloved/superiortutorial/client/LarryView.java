package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.entity.Larry;
import com.mugloved.superiortutorial.entity.LarryState;
import net.minecraft.util.Mth;

/**
 * What the local player sees of Larry, moment by moment: which animation, how visible, how decayed, how bright
 * his eyes are, and how much his head follows them. All timings follow Larry's animation cue sheet.
 */
public final class LarryView {
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

    private LarryView() {}

    /**
     * Works out his phase for the local player and stores it on him, so his animation controller can read it.
     * Uses the state as of the last client tick (where endings are started), not the keys live: a key can arrive
     * between ticks, and reading it early would show him gone or rotten for a frame before his ending begins.
     */
    public static int update(Larry larry, float partialTick) {
        double t = endingSeconds(larry, partialTick);
        int settled = TutorialClient.GameEvents.settledState();
        boolean slain = settled >= 0 ? settled == 2 : LarryState.slainHere();
        boolean left = settled >= 0 ? settled == 1 : LarryState.leftHere();
        int phase;
        if (t >= 0) {
            phase = larry.clientEnding == Larry.PHASE_SLAY
                ? (t < SLAY_LENGTH ? Larry.PHASE_SLAY : Larry.PHASE_GONE)
                : (t < LEAVE_LENGTH ? Larry.PHASE_LEAVE : Larry.PHASE_ROTTEN);
        } else if (slain) {
            phase = Larry.PHASE_GONE;
        } else if (left) {
            phase = Larry.PHASE_ROTTEN;
        } else {
            phase = Larry.PHASE_IDLE;
        }
        larry.clientPhase = phase;
        return phase;
    }

    /** Seconds since the local player's ending began, or -1 when none is playing. */
    public static double endingSeconds(Larry larry, float partialTick) {
        if (larry.clientEndingStart < 0) return -1;
        return Math.max(0.0, (larry.level().getGameTime() - larry.clientEndingStart + partialTick) / 20.0);
    }

    /** How visible he is: 1 normally; he fades away at the end of the slay ending. */
    public static float alpha(Larry larry, float partialTick) {
        if (larry.clientPhase == Larry.PHASE_GONE) return 0.0f;
        if (larry.clientPhase != Larry.PHASE_SLAY) return 1.0f;
        double t = endingSeconds(larry, partialTick);
        return 1.0f - smooth(t, SLAY_FADE_START, SLAY_FADE_END);
    }

    /** How far the decayed skin has taken over during the leave ending (0 to 1). */
    public static float decay(Larry larry, float partialTick) {
        if (larry.clientPhase == Larry.PHASE_ROTTEN) return 1.0f;
        if (larry.clientPhase != Larry.PHASE_LEAVE) return 0.0f;
        return smooth(endingSeconds(larry, partialTick), 1.2, 4.5);
    }

    /** Eye glow, 0 to 1. */
    public static float glow(Larry larry, float partialTick) {
        double t = endingSeconds(larry, partialTick);
        return switch (larry.clientPhase) {
            case Larry.PHASE_IDLE -> Math.max(breathGlow(larry), shakeFlare(larry, partialTick));
            // Slay: his eyes light up (80% by 0.15 s), hold while he is ended, and go out between 1.2 and 1.9 s.
            case Larry.PHASE_SLAY -> 0.8f * smooth(t, 0.0, 0.15) * (1.0f - smooth(t, 1.2, 1.9));
            // Leave: whatever was there goes out between 0.6 and 1.6 s.
            case Larry.PHASE_LEAVE -> larry.clientGlowAtEnding * (1.0f - smooth(t, 0.6, 1.6));
            default -> 0.0f;
        };
    }

    /** The glow that follows his breath: dark when breathed out, up to 40% at the deepest breath. */
    public static float breathGlow(Larry larry) {
        return BREATH_GLOW * larry.clientBreath;
    }

    /** Called by the model each frame with how far the body leans from its resting pose, in degrees. */
    public static void breathFromBody(Larry larry, float bodyLeanDegrees) {
        larry.clientBreath = Mth.clamp(Math.abs(bodyLeanDegrees) / DEEPEST_BREATH, 0.0f, 1.0f);
    }

    /** When hit: the eyes flare to full at 0.1 s and are back by 0.4 s. */
    private static float shakeFlare(Larry larry, float partialTick) {
        double t = (larry.level().getGameTime() - larry.clientShakeStart + partialTick) / 20.0;
        if (t < 0 || t > 0.4) return 0.0f;
        return t < 0.1 ? (float) (t / 0.1) : 1.0f - smooth(t, 0.1, 0.4);
    }

    /**
     * How much his head follows the player. Fully while waiting. Slay: he keeps looking at his killer while the
     * light is in his eyes and lets go as he goes limp (1.2 to 2.1 s, into the slump). Leave: he lets go as he
     * reaches out (0.2 to 1.0 s).
     */
    public static float headFollow(Larry larry, float partialTick) {
        return switch (larry.clientPhase) {
            case Larry.PHASE_IDLE -> 1.0f;
            case Larry.PHASE_SLAY -> 1.0f - smooth(endingSeconds(larry, partialTick), 1.2, 2.1);
            case Larry.PHASE_LEAVE -> 1.0f - smooth(endingSeconds(larry, partialTick), 0.2, 1.0);
            default -> 0.0f;
        };
    }

    /** How much he is looking at someone right now (0 to 1), smoothed between ticks. */
    public static float engage(Larry larry, float partialTick) {
        return Mth.lerp(partialTick, larry.clientEngageO, larry.clientEngage);
    }

    /**
     * Once a tick: eases {@link Larry#clientEngage} toward 1 while the local player is within his reach and still
     * waiting on him (or ending him), toward 0 otherwise (about half a second either way).
     */
    static void tickEngage(Larry larry, net.minecraft.world.entity.player.Player player) {
        larry.clientEngageO = larry.clientEngage;
        // (During the slay ending he keeps looking at his killer; the head follow lets it go with the slump.)
        boolean watching = (larry.clientPhase == Larry.PHASE_IDLE || larry.clientPhase == Larry.PHASE_SLAY) && player != null && !player.isSpectator()
            && larry.distanceToSqr(player) <= 8.0 * 8.0;
        float target = watching ? 1.0f : 0.0f;
        larry.clientEngage += Mth.clamp(target - larry.clientEngage, -0.1f, 0.1f);
    }

    /** Eased 0 to 1 as t goes from a to b. */
    static float smooth(double t, double a, double b) {
        double x = Mth.clamp((t - a) / (b - a), 0.0, 1.0);
        return (float) (x * x * (3.0 - 2.0 * x));
    }
}
