package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.entity.Larry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The particles of his two endings, and of what is left afterwards. Quiet on purpose: nothing flashes,
 * nothing rises into the sky. Things just stop, or rot.
 *
 * <pre>
 * Slay  (2.8 s)  0.0  his eyes light up (drawn by the renderer)
 *                1.2  the light goes out of them: a few soul flecks drift off his face
 *                1.9  he starts to fade; ash comes away from him and drifts down as he goes
 *                2.2  he goes limp: soul smoke from where his chest was, then a thin trail of souls for a moment
 * Leave (6.0 s)  0.6  his eyes start to go out (renderer)
 *                1.2  the decayed skin creeps in (renderer)
 *                1.6  his hand drops: a burst of ash and bone dust
 *                1.6+ ash and dust keep sifting off him while he rots, thinning out by 4.5 s
 * Rotten         now and then a flake of ash falls off him
 * </pre>
 */
final class LarryEffects {
    private static final ParticleOptions BONE_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BONE_BLOCK.defaultBlockState());

    private LarryEffects() {}

    static void tick(ClientLevel level, Larry larry) {
        int phase = LarryView.update(larry, 0.0f);   // (also keeps his phase current while he is off screen)
        RandomSource random = level.random;
        if (phase == Larry.PHASE_ROTTEN) {
            if (random.nextInt(70) == 0) emit(level, chest(larry), ParticleTypes.WHITE_ASH, 1, 0.35, 0.3, -0.01, 0.0);
            return;
        }
        if (larry.clientEndingStart < 0) return;
        double before = larry.clientEffectsDone;
        double now = LarryView.endingSeconds(larry, 0.0f);
        larry.clientEffectsDone = now;
        if (now <= before) return;

        if (larry.clientEnding == Larry.PHASE_SLAY) {
            if (crossed(before, now, 1.2)) emit(level, eyes(larry), ParticleTypes.SOUL, 3, 0.08, 0.05, 0.01, 0.015);
            if (now >= 1.9 && before < 2.8) emit(level, chest(larry), ParticleTypes.WHITE_ASH, 3, 0.35, 0.55, 0.01, 0.02);
            if (crossed(before, now, 2.2)) {
                emit(level, chest(larry), ParticleTypes.SOUL, 14, 0.3, 0.45, 0.02, 0.03);
                emit(level, chest(larry), ParticleTypes.SMOKE, 12, 0.3, 0.45, 0.015, 0.02);
            }
            if (now >= 2.3 && before < 3.6 && random.nextInt(3) == 0) {
                emit(level, chest(larry), ParticleTypes.SOUL, 1, 0.12, 0.2, 0.025, 0.035);
            }
        } else if (larry.clientEnding == Larry.PHASE_LEAVE) {
            if (crossed(before, now, 1.6)) {
                emit(level, chest(larry), ParticleTypes.WHITE_ASH, 36, 0.4, 0.55, 0.01, 0.03);
                emit(level, chest(larry), BONE_DUST, 10, 0.35, 0.5, 0.0, 0.0);
                emit(level, chest(larry), ParticleTypes.SMOKE, 6, 0.25, 0.35, 0.01, 0.015);
            }
            if (now > 1.6 && before < 4.5) {
                // Sifting off him, thinning out as the decay finishes.
                float left = 1.0f - LarryView.smooth(now, 1.6, 4.5);
                if (random.nextFloat() < 0.9f * left) emit(level, chest(larry), ParticleTypes.WHITE_ASH, 1, 0.4, 0.55, -0.005, 0.0);
                if (random.nextFloat() < 0.25f * left) emit(level, chest(larry), BONE_DUST, 1, 0.35, 0.5, 0.0, 0.0);
            }
        }
    }

    private static boolean crossed(double before, double now, double moment) {
        return before < moment && now >= moment;
    }

    /**
     * Spreads particles around a point: sideways by up to {@code spread}, up and down by up to {@code height}.
     * Ash and dust ignore the given motion and drift down on their own; souls and smoke follow it.
     */
    private static void emit(ClientLevel level, Vec3 at, ParticleOptions type, int count, double spread, double height, double rise, double drift) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(type,
                at.x + (random.nextDouble() - 0.5) * 2.0 * spread,
                at.y + (random.nextDouble() - 0.5) * 2.0 * height,
                at.z + (random.nextDouble() - 0.5) * 2.0 * spread,
                (random.nextDouble() - 0.5) * 2.0 * drift, rise + random.nextDouble() * Math.abs(rise), (random.nextDouble() - 0.5) * 2.0 * drift);
        }
    }

    /** The middle of his body, which sits a little behind his block's centre (his back is to the wall). */
    private static Vec3 chest(Larry larry) {
        return at(larry, -0.12, 0.55);
    }

    /** About where his eyes are: high on his slumped head, at its front. */
    private static Vec3 eyes(Larry larry) {
        return at(larry, 0.22, 1.08);
    }

    /** A point {@code forward} blocks in front of him (negative: behind) and {@code up} blocks above his feet. */
    private static Vec3 at(Larry larry, double forward, double up) {
        float facing = larry.yBodyRot * Mth.DEG_TO_RAD;
        double ahead = forward + LarryRenderer.FORWARD;
        return new Vec3(larry.getX() - Mth.sin(facing) * ahead, larry.getY() + LarryRenderer.LIFT + up, larry.getZ() + Mth.cos(facing) * ahead);
    }
}
