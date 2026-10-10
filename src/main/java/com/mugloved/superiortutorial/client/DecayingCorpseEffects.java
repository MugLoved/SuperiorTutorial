package com.mugloved.superiortutorial.client;

import com.mugloved.superiortutorial.entity.DecayingCorpse;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The particles of his two endings, and of what is left afterwards. Still quiet: nothing flashes, nothing
 * rises into the sky. Things just stop, or rot. (Sounds come later, through Superior Sounds.)
 *
 * <pre>
 * Slay  (4.4 s)  0.0  his eyes light up (renderer); a low ring of pale light settles on the floor around him
 *                1.2  the light goes out of his eyes: soul flecks drift off his face
 *                2.15 he goes limp: a last breath of ash and smoke from his mouth, soul smoke from his chest
 *                2.9+ he fades (renderer); ash comes away from him, a thin trail of souls rises, thinning out
 *                4.3  the last of him: a small fall of dust where he sat
 * Leave (6.0 s)  0.6  his eyes start to go out (renderer)
 *                1.2  the decayed skin creeps in (renderer)
 *                1.55 his hand drops: a cold cloud of dust rolls out low over the floor, ash and bone dust burst
 *                1.6+ ash, bone dust and dark flakes keep sifting off him while he rots; the stain soaks in
 *                     (renderer, see DecayingCorpseStain); all of it thinning out by 5 s
 * Rotten         now and then a flake of ash or a dark fleck falls off him
 * </pre>
 */
final class DecayingCorpseEffects {
    private static final ParticleOptions BONE_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BONE_BLOCK.defaultBlockState());
    private static final ParticleOptions DIRT_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.COARSE_DIRT.defaultBlockState());

    private DecayingCorpseEffects() {}

    static void tick(ClientLevel level, DecayingCorpse corpse) {
        int phase = DecayingCorpseView.update(corpse, 0.0f);   // (also keeps his phase current while he is off screen)
        DecayingCorpseView.tickEngage(corpse, net.minecraft.client.Minecraft.getInstance().player);
        RandomSource random = level.random;
        if (phase == DecayingCorpse.PHASE_ROTTEN) {
            if (random.nextInt(60) == 0) emit(level, chest(corpse), ParticleTypes.WHITE_ASH, 1, 0.35, 0.3, -0.01, 0.0);
            if (random.nextInt(140) == 0) emit(level, chest(corpse), ParticleTypes.ASH, 1, 0.3, 0.3, 0.0, 0.0);
            return;
        }
        if (corpse.clientEndingStart < 0) return;
        double before = corpse.clientEffectsDone;
        double now = DecayingCorpseView.endingSeconds(corpse, 0.0f);
        corpse.clientEffectsDone = now;
        if (now <= before) return;

        if (corpse.clientEnding == DecayingCorpse.PHASE_SLAY) {
            slay(level, corpse, random, before, now);
        } else if (corpse.clientEnding == DecayingCorpse.PHASE_LEAVE) {
            leave(level, corpse, random, before, now);
        }
    }

    private static void slay(ClientLevel level, DecayingCorpse corpse, RandomSource random, double before, double now) {
        if (crossed(before, now, 0.05)) ring(level, corpse, random);
        if (crossed(before, now, 1.2)) emit(level, eyes(corpse), ParticleTypes.SOUL, 5, 0.08, 0.05, 0.01, 0.015);
        if (crossed(before, now, 2.15)) {
            // The last breath goes out of him.
            breath(level, corpse, random);
            emit(level, chest(corpse), ParticleTypes.SOUL, 10, 0.3, 0.4, 0.02, 0.03);
            emit(level, chest(corpse), ParticleTypes.SMOKE, 10, 0.3, 0.4, 0.015, 0.02);
        }
        if (now >= DecayingCorpseView.SLAY_FADE_START && before < DecayingCorpseView.SLAY_FADE_END) {
            // He comes apart as he fades: ash and a little dust fall away, a thin trail of souls rises.
            float left = 1.0f - DecayingCorpseView.smooth(now, DecayingCorpseView.SLAY_FADE_START, DecayingCorpseView.SLAY_FADE_END);
            emit(level, chest(corpse), ParticleTypes.WHITE_ASH, 2 + Math.round(3 * left), 0.4, 0.55, 0.0, 0.02);
            if (random.nextFloat() < 0.5f) emit(level, chest(corpse), BONE_DUST, 1, 0.35, 0.5, 0.0, 0.0);
            if (random.nextFloat() < 0.35f * left + 0.1f) emit(level, chest(corpse), ParticleTypes.SOUL, 1, 0.15, 0.25, 0.025, 0.03);
        }
        if (crossed(before, now, DecayingCorpseView.SLAY_FADE_END)) {
            emit(level, floor(corpse, 0.0, 0.15), BONE_DUST, 14, 0.45, 0.15, 0.0, 0.0);
            emit(level, floor(corpse, 0.0, 0.1), ParticleTypes.WHITE_ASH, 18, 0.5, 0.1, 0.0, 0.01);
        }
    }

    private static void leave(ClientLevel level, DecayingCorpse corpse, RandomSource random, double before, double now) {
        if (crossed(before, now, 1.55)) {
            // His hand hits the floor: a cold cloud of dust rolls out low and slow, and he sheds a burst of ash.
            dustCloud(level, corpse, random);
            emit(level, chest(corpse), ParticleTypes.WHITE_ASH, 40, 0.4, 0.55, 0.01, 0.03);
            emit(level, chest(corpse), BONE_DUST, 14, 0.35, 0.5, 0.0, 0.0);
            emit(level, chest(corpse), ParticleTypes.ASH, 20, 0.4, 0.5, 0.0, 0.0);
        }
        if (now > 1.6 && before < 5.0) {
            // Sifting off him while he rots, thinning out as the decay finishes.
            float left = 1.0f - DecayingCorpseView.smooth(now, 1.6, 5.0);
            if (random.nextFloat() < 1.2f * left) emit(level, chest(corpse), ParticleTypes.WHITE_ASH, 1, 0.4, 0.55, -0.005, 0.0);
            if (random.nextFloat() < 0.4f * left) emit(level, chest(corpse), BONE_DUST, 1, 0.35, 0.5, 0.0, 0.0);
            if (random.nextFloat() < 0.6f * left) emit(level, chest(corpse), ParticleTypes.ASH, 1, 0.4, 0.5, 0.0, 0.0);
            if (random.nextFloat() < 0.25f * left) emit(level, floor(corpse, 0.1, 0.05), DIRT_DUST, 1, 0.6, 0.05, 0.0, 0.0);
        }
    }

    /** A low ring of pale light on the floor around him, drifting very slightly outward and up. */
    private static void ring(ClientLevel level, DecayingCorpse corpse, RandomSource random) {
        Vec3 middle = floor(corpse, 0.0, 0.12);
        int count = 22;
        for (int i = 0; i < count; i++) {
            double angle = (i + random.nextDouble() * 0.5) / count * Math.PI * 2.0;
            double radius = 0.85 + random.nextDouble() * 0.15;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            level.addParticle(ParticleTypes.END_ROD,
                middle.x + cos * radius, middle.y + random.nextDouble() * 0.05, middle.z + sin * radius,
                cos * 0.008, 0.004 + random.nextDouble() * 0.004, sin * 0.008);
        }
    }

    /** His last breath: ash and smoke pushed gently out of his mouth, in the direction he faces. */
    private static void breath(ClientLevel level, DecayingCorpse corpse, RandomSource random) {
        Vec3 mouth = eyes(corpse).add(0.0, -0.12, 0.0);
        Vec3 ahead = forward(corpse);
        for (int i = 0; i < 14; i++) {
            double speed = 0.02 + random.nextDouble() * 0.03;
            ParticleOptions type = i % 2 == 0 ? ParticleTypes.SMOKE : ParticleTypes.WHITE_ASH;
            level.addParticle(type,
                mouth.x + (random.nextDouble() - 0.5) * 0.12, mouth.y + (random.nextDouble() - 0.5) * 0.08, mouth.z + (random.nextDouble() - 0.5) * 0.12,
                ahead.x * speed + (random.nextDouble() - 0.5) * 0.01, 0.003, ahead.z * speed + (random.nextDouble() - 0.5) * 0.01);
        }
    }

    /** A cloud of dust rolling out low across the floor, every way at once, slowly. */
    private static void dustCloud(ClientLevel level, DecayingCorpse corpse, RandomSource random) {
        Vec3 middle = floor(corpse, 0.15, 0.15);
        for (int i = 0; i < 26; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.03 + random.nextDouble() * 0.04;
            level.addParticle(i % 3 == 0 ? ParticleTypes.SMOKE : ParticleTypes.POOF,
                middle.x + Math.cos(angle) * 0.3, middle.y + random.nextDouble() * 0.15, middle.z + Math.sin(angle) * 0.3,
                Math.cos(angle) * speed, i % 3 == 0 ? 0.002 : 0.0, Math.sin(angle) * speed);
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
    private static Vec3 chest(DecayingCorpse corpse) {
        return at(corpse, -0.12, 0.55);
    }

    /** About where his eyes are: high on his slumped head, at its front. */
    private static Vec3 eyes(DecayingCorpse corpse) {
        return at(corpse, 0.22, 1.08);
    }

    /** On the floor under him, {@code forward} blocks ahead of his seat, {@code up} blocks above the floor. */
    private static Vec3 floor(DecayingCorpse corpse, double forward, double up) {
        return at(corpse, forward, up - DecayingCorpseRenderer.LIFT);
    }

    /** The direction his body faces, flat. */
    private static Vec3 forward(DecayingCorpse corpse) {
        float facing = corpse.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(facing), 0.0, Mth.cos(facing));
    }

    /** A point {@code forward} blocks in front of him (negative: behind) and {@code up} blocks above his feet. */
    private static Vec3 at(DecayingCorpse corpse, double forward, double up) {
        float facing = corpse.yBodyRot * Mth.DEG_TO_RAD;
        double ahead = forward + DecayingCorpseRenderer.FORWARD;
        return new Vec3(corpse.getX() - Mth.sin(facing) * ahead, corpse.getY() + DecayingCorpseRenderer.LIFT + up, corpse.getZ() + Mth.cos(facing) * ahead);
    }
}
