package com.mugloved.superiortutorial.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.particles.ParticleTypes;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * the Corpse: a corpse slumped against the cave wall, held to life against his will.
 * He never moves, never dies to a survival player, keeps his body facing one way, and turns only his head
 * (up to {@link #MAX_HEAD_TURN} degrees) toward the nearest player who has not finished with him.
 * Everything he says is Story dialogue bound to this entity type.
 *
 * <p>How he looks is decided on each player's own screen, from that player's own unlock keys: idle, the slay or
 * leave ending as it plays, rotten afterwards, or gone. The client keeps that in the {@code client*} fields below
 * (plain values, so this class never touches client-only code); the server never reads them.
 */
public class DecayingCorpse extends Mob implements GeoEntity {
    public static final float MAX_HEAD_TURN = 35.0f;
    /** How fast his head turns toward the player while Story holds him in a conversation, degrees per tick. */
    private static final float HELD_HEAD_SPEED = 8.0f;
    private static final int LINE_COOLDOWN_TICKS = 60;
    private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(DecayingCorpse.class, EntityDataSerializers.FLOAT);
    private static final String FACING_TAG = "DecayingCorpseFacing";
    /** Length of his idle (breathing) loop, in seconds. */
    private static final double IDLE_LENGTH = 12.0;
    /** Longest a twitch may take before he goes back to breathing regardless (it runs 0.9 s). */
    private static final int TWITCH_TIMEOUT_TICKS = 30;

    // What the local player sees. Set by the client (see client.DecayingCorpseView); unused on the server.
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_SLAY = 1;
    public static final int PHASE_LEAVE = 2;
    public static final int PHASE_ROTTEN = 3;
    public static final int PHASE_GONE = 4;
    public int clientPhase = PHASE_IDLE;
    /** The ending playing for the local player ({@link #PHASE_SLAY} or {@link #PHASE_LEAVE}), and when it began (game time), or -1. */
    public int clientEnding = PHASE_IDLE;
    public long clientEndingStart = -1;
    /** How strongly his eyes glowed when the ending began, so the leave ending can fade from there. */
    public float clientGlowAtEnding;
    /** Twitching between breaths: whether one is playing, when it began, breaths left until the next, and how far into the current breath he is (seconds, -1 when not tracked). */
    public boolean clientTwitching;
    public long clientTwitchStart;
    public int clientLoopsUntilTwitch;
    public double clientLoopSeconds = -1;
    /** How far into his breath he is (0 = breathed out, 1 = deepest breath), read off the animated body each frame. */
    public float clientBreath;
    /** Seconds into the ending already handled by the particle effects. */
    public double clientEffectsDone;
    /** How much he is looking at someone (0 to 1, eased over about half a second), this tick and the last. */
    public float clientEngage, clientEngageO;
    /** The head turn (radians) the model added on top of the animation in the last world frame. */
    public float clientHeadYawAdded, clientHeadPitchAdded;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.decaying_corpse.idle");
    private static final RawAnimation TWITCH = RawAnimation.begin().thenPlay("animation.decaying_corpse.twitch");
    private static final RawAnimation SLAY = RawAnimation.begin().thenPlayAndHold("animation.decaying_corpse.slay");
    private static final RawAnimation LEAVE = RawAnimation.begin().thenPlayAndHold("animation.decaying_corpse.leave");
    private static final RawAnimation ROTTEN = RawAnimation.begin().thenLoop("animation.decaying_corpse.rotten_idle");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /** Per-player cooldown for his dry line when hit, so spam-clicking does not spam text. */
    private final Map<UUID, Long> lastHitLine = new HashMap<>();

    public DecayingCorpse(EntityType<? extends DecayingCorpse> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(true);
    }

    // ---- Animation ---------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // One controller carries everything, twitches included, so nothing ever fights over his bones.
        controllers.add(new MainController(this));
    }

    /** The main controller, which can also say how far into the current idle loop he is. */
    private static final class MainController extends AnimationController<DecayingCorpse> {
        MainController(DecayingCorpse corpse) {
            super(corpse, "main", 3, corpse::mainAnimation);
        }

        /** Seconds into the current 12 s idle loop, at the given animation tick. */
        double loopSeconds(double animationTick) {
            double ticks = Math.max(animationTick - tickOffset, 0.0) * getAnimationSpeed();
            return (ticks / 20.0) % IDLE_LENGTH;
        }
    }

    private PlayState mainAnimation(AnimationState<DecayingCorpse> state) {
        if (clientPhase != PHASE_IDLE) {
            clientTwitching = false;
            clientLoopSeconds = -1;
        }
        return switch (clientPhase) {
            case PHASE_SLAY -> state.setAndContinue(SLAY);
            case PHASE_LEAVE -> state.setAndContinue(LEAVE);
            case PHASE_ROTTEN -> state.setAndContinue(ROTTEN);
            case PHASE_GONE -> PlayState.STOP;
            default -> idleAnimation(state);
        };
    }

    /**
     * Waiting: he breathes, and at the end of every third to fifth breath (one 12 s loop each) a twitch runs
     * through him, then he breathes on. Not while someone is talking to him.
     */
    private PlayState idleAnimation(AnimationState<DecayingCorpse> state) {
        long now = level().getGameTime();
        if (clientTwitching) {
            boolean done = state.isCurrentAnimation(TWITCH) && state.getController().hasAnimationFinished();
            if (!done && now - clientTwitchStart < TWITCH_TIMEOUT_TICKS) return state.setAndContinue(TWITCH);
            clientTwitching = false;
            clientLoopSeconds = -1;
            return state.setAndContinue(IDLE);
        }
        if (state.isCurrentAnimation(IDLE) && state.getController() instanceof MainController main
            && main.getAnimationState() == AnimationController.State.RUNNING) {
            double t = main.loopSeconds(state.getAnimationTick());
            boolean loopEnded = clientLoopSeconds >= 0 && t < clientLoopSeconds - IDLE_LENGTH / 2;
            clientLoopSeconds = t;
            if (loopEnded) {
                if (clientLoopsUntilTwitch <= 0) clientLoopsUntilTwitch = nextTwitchGap();
                if (--clientLoopsUntilTwitch <= 0) {
                    clientLoopsUntilTwitch = nextTwitchGap();
                    // (Held in a conversation: he keeps still, and the count starts over.)
                    if (!isNoAi()) {
                        twitch(now);
                        return state.setAndContinue(TWITCH);
                    }
                }
            }
        } else {
            clientLoopSeconds = -1;
        }
        return state.setAndContinue(IDLE);
    }

    private int nextTwitchGap() {
        return 3 + random.nextInt(3);
    }

    /** Client: the twitch begins, and a little dust is shaken loose. */
    private void twitch(long now) {
        clientTwitching = true;
        clientTwitchStart = now;
        clientLoopSeconds = -1;
        for (int i = 0; i < 4; i++) {
            level().addParticle(ParticleTypes.WHITE_ASH,
                getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.3 + random.nextDouble() * 0.8, getZ() + (random.nextDouble() - 0.5) * 0.6,
                0.0, -0.02, 0.0);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(FACING, Float.NaN);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new WatchGoal(this));
    }

    // ---- Facing: the body is fixed, only the head turns -------------------------------------------------------

    /** The direction his body faces, or NaN until his first tick. */
    public float facing() {
        return entityData.get(FACING);
    }

    private boolean anchored() {
        return !Float.isNaN(facing());
    }

    @Override
    public void tick() {
        if (!level().isClientSide && !anchored()) {
            // First tick in the world: face the nearest player (whoever placed him), else keep the current yaw.
            Player near = level().getNearestPlayer(this, 8.0);
            float yaw = getYRot();
            if (near != null) {
                yaw = (float) (Mth.atan2(near.getZ() - getZ(), near.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0f;
            }
            entityData.set(FACING, Mth.wrapDegrees(yaw));
        }
        super.tick();
        if (!level().isClientSide && anchored() && isNoAi()) watchWhileHeld();
        holdPose();
    }

    /**
     * Story holds a speaker still during a conversation by switching its AI off, and then points its head straight
     * along its body every tick. the Corpse keeps looking at the player himself instead (Story's head turns are ignored
     * while he is held, see {@link #setYHeadRot}). After his ending the player is no longer someone he watches, so
     * his head simply stays where it was; the client lets it go as part of the ending.
     */
    private void watchWhileHeld() {
        Player target = WatchGoal.nearest(this);
        if (target == null) return;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float head = yHeadRot + Mth.clamp(Mth.wrapDegrees(wanted - yHeadRot), -HELD_HEAD_SPEED, HELD_HEAD_SPEED);
        super.setYHeadRot(clampHead(head, facing()));
    }

    /** Puts the body back on its facing and keeps the head within reach of it. Runs on both sides every tick. */
    private void holdPose() {
        if (!anchored()) return;
        float body = facing();
        super.setYRot(body);
        yRotO = body;
        yBodyRot = body;
        yBodyRotO = body;
        yHeadRot = clampHead(yHeadRot, body);
        yHeadRotO = clampHead(yHeadRotO, body);
    }

    private static float clampHead(float head, float body) {
        return body + Mth.clamp(Mth.wrapDegrees(head - body), -MAX_HEAD_TURN, MAX_HEAD_TURN);
    }

    @Override
    public void setYRot(float yaw) {
        super.setYRot(anchored() ? facing() : yaw);
    }

    @Override
    public void setYBodyRot(float yaw) {
        super.setYBodyRot(anchored() ? facing() : yaw);
    }

    @Override
    public void setYHeadRot(float yaw) {
        // While Story holds him (AI off), it keeps pointing his head forward; he looks at the player instead.
        if (anchored() && !level().isClientSide && isNoAi()) return;
        super.setYHeadRot(anchored() ? clampHead(yaw, facing()) : yaw);
    }

    @Override
    public int getMaxHeadYRot() {
        return (int) MAX_HEAD_TURN;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                // The body never follows the head.
            }
        };
    }

    /** Structures may be placed rotated; turn his facing with them. */
    @Override
    public float rotate(Rotation rotation) {
        if (anchored()) {
            float turn = switch (rotation) {
                case CLOCKWISE_90 -> 90.0f;
                case CLOCKWISE_180 -> 180.0f;
                case COUNTERCLOCKWISE_90 -> 270.0f;
                default -> 0.0f;
            };
            entityData.set(FACING, Mth.wrapDegrees(facing() + turn));
            return facing();
        }
        return super.rotate(rotation);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchored()) tag.putFloat(FACING_TAG, facing());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(FACING_TAG)) entityData.set(FACING, tag.getFloat(FACING_TAG));
    }

    // ---- He cannot be moved or killed by a survival player ----------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // /kill and the void still work, so an operator can always remove him.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && player.getAbilities().instabuild) {
            // Creative mode removes him in one hit, for editing the cave.
            if (!level().isClientSide) discard();
            return true;
        }
        if (attacker instanceof ServerPlayer player && !DecayingCorpseState.has(player, DecayingCorpseState.DONE)) {
            // Nothing moves; he just has something to say about it (not every hit).
            long now = level().getGameTime();
            Long last = lastHitLine.get(player.getUUID());
            if (last == null || now - last >= LINE_COOLDOWN_TICKS) {
                lastHitLine.put(player.getUUID(), now);
                player.displayClientMessage(Component.translatable("superior_tutorial.decaying_corpse.hit"), true);
            }
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid(net.minecraftforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** A player who ended him can no longer aim at him; to them he is gone. */
    @Override
    public boolean isPickable() {
        if (level().isClientSide && DecayingCorpseState.slainHere()) return false;
        return super.isPickable();
    }

    // ---- What is left after the player walks away -------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && DecayingCorpseState.has(server, DecayingCorpseState.LEFT)) {
            server.displayClientMessage(Component.translatable("superior_tutorial.decaying_corpse.remains"), true);
            return InteractionResult.SUCCESS;
        }
        return level().isClientSide && DecayingCorpseState.leftHere() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Turns his head toward the nearest player in reach who has not finished with him. */
    private static final class WatchGoal extends Goal {
        private static final double RANGE = 8.0;
        private final DecayingCorpse corpse;
        private Player target;
        private int recheck;

        WatchGoal(DecayingCorpse corpse) {
            this.corpse = corpse;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = nearest();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && eligible(target) && corpse.distanceToSqr(target) <= RANGE * RANGE * 1.5;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            target = null;
        }

        @Override
        public void tick() {
            if (--recheck <= 0) {
                recheck = 20;
                Player nearer = nearest();
                if (nearer != null) target = nearer;
            }
            if (target != null) corpse.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        }

        private Player nearest() {
            return nearest(corpse);
        }

        /** The nearest player within reach who has not finished with him, or null. */
        static Player nearest(DecayingCorpse corpse) {
            Player best = null;
            double bestDistance = RANGE * RANGE;
            for (Player player : corpse.level().players()) {
                if (!eligible(player)) continue;
                double distance = corpse.distanceToSqr(player);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best;
        }

        private static boolean eligible(Player player) {
            if (player.isSpectator()) return false;
            return !(player instanceof ServerPlayer server) || !DecayingCorpseState.has(server, DecayingCorpseState.DONE);
        }
    }
}
