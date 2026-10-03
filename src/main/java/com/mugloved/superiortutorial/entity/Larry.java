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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Larry: a skeleton slumped against the cave wall, held to life against his will.
 * He never moves, never dies to a survival player, keeps his body facing one way, and turns only his head
 * (up to {@link #MAX_HEAD_TURN} degrees) toward the nearest player who has not finished with him.
 * Everything he says is Story dialogue bound to this entity type.
 */
public class Larry extends Mob {
    public static final float MAX_HEAD_TURN = 70.0f;
    private static final int LINE_COOLDOWN_TICKS = 60;
    private static final EntityDataAccessor<Float> FACING = SynchedEntityData.defineId(Larry.class, EntityDataSerializers.FLOAT);
    private static final String FACING_TAG = "LarryFacing";

    /** Per-player cooldown for his dry line when hit, so spam-clicking does not spam text. */
    private final Map<UUID, Long> lastHitLine = new HashMap<>();

    public Larry(EntityType<? extends Larry> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(true);
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
        holdPose();
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
        if (attacker instanceof ServerPlayer player && !LarryState.has(player, LarryState.DONE)) {
            long now = level().getGameTime();
            Long last = lastHitLine.get(player.getUUID());
            if (last == null || now - last >= LINE_COOLDOWN_TICKS) {
                lastHitLine.put(player.getUUID(), now);
                player.displayClientMessage(Component.translatable("superior_tutorial.larry.hit"), true);
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
        if (level().isClientSide && LarryState.slainHere()) return false;
        return super.isPickable();
    }

    // ---- What is left after the player walks away -------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && LarryState.has(server, LarryState.LEFT)) {
            server.displayClientMessage(Component.translatable("superior_tutorial.larry.remains"), true);
            return InteractionResult.SUCCESS;
        }
        return level().isClientSide && LarryState.leftHere() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Turns his head toward the nearest player in reach who has not finished with him. */
    private static final class WatchGoal extends Goal {
        private static final double RANGE = 8.0;
        private final Larry larry;
        private Player target;
        private int recheck;

        WatchGoal(Larry larry) {
            this.larry = larry;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = nearest();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && eligible(target) && larry.distanceToSqr(target) <= RANGE * RANGE * 1.5;
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
            if (target != null) larry.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        }

        private Player nearest() {
            Player best = null;
            double bestDistance = RANGE * RANGE;
            for (Player player : larry.level().players()) {
                if (!eligible(player)) continue;
                double distance = larry.distanceToSqr(player);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best;
        }

        private static boolean eligible(Player player) {
            if (player.isSpectator()) return false;
            return !(player instanceof ServerPlayer server) || !LarryState.has(server, LarryState.DONE);
        }
    }
}
