package com.mugloved.superiortutorial.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Haskel Hobb, the fisherman in the cove (working name; all his text is in the lang file).
 *
 * <ul>
 *   <li>He keeps to his dock: he only walks between his home spot and his dock spots, never into water and never
 *   off an edge, and stops to face any player who comes within {@link #STOP_RANGE} blocks.</li>
 *   <li>His head follows the nearest player first (up to {@link #MAX_HEAD_TURN} degrees); his body turns only when
 *   the head has been turned far for a while, and then slowly, so his stance survives.</li>
 *   <li>He cannot be hurt. A player's hit makes him vanish in a burst of smoke and step out again on the dock spot
 *   farthest from that player.</li>
 *   <li>Everything he says is Story dialogue bound to this entity type (see the {@code fisherman_*} scenes).</li>
 * </ul>
 *
 * <p>Spots: placed by the tutorial with {@link #setSpots}, or from NBT. {@code HaskelSpotOffsets} (x, y, z triples
 * relative to where he stands when first ticked) is for structure files, and turns with the structure; so does
 * {@code HaskelHouseOffset}, one more spot he walks to (inside his house) that a hit never sends him to. Where he
 * stands when first ticked is his home, the spot he likes best: in the cove, the end of his dock, where a player
 * coming out of the cave sees him first.
 */
public class Fisherman extends PathfinderMob implements GeoEntity {
    public static final float MAX_HEAD_TURN = 70.0f;
    static final double STOP_RANGE = 3.0;
    static final double WATCH_RANGE = 6.0;
    /**
     * Walking speed: about 1 block a second, an unhurried walk (his walk animation is made for 1.0 and is matched to
     * his real speed). A land mob's ground speed grows with the square of this value: 0.155 settles at about 0.053
     * blocks a tick. (0.0825, about 0.3 blocks a second, read as a quarter of sneaking.)
     */
    private static final double SPEED = 0.155;
    public static final double WALK_ANIMATION_SPEED = 1.0;
    private static final int HIT_COOLDOWN_TICKS = 60;
    private static final int APPEAR_DELAY_TICKS = 4;
    private static final double MIN_VANISH_DISTANCE = 8.0;

    public static final byte EVENT_VANISH = 101;
    public static final byte EVENT_APPEAR = 102;
    /** 103, 104, 105: start fidget 0, 1 or 2. The server picks them, since only it knows he is standing still. */
    public static final byte EVENT_FIDGET = 103;

    /** First fidget 6 to 14 s after he comes to a stop; after that one every 15 to 40 s while he stands. */
    private static final int FIRST_FIDGET_MIN_TICKS = 120, FIRST_FIDGET_SPREAD_TICKS = 161;
    private static final int FIDGET_MIN_TICKS = 300, FIDGET_SPREAD_TICKS = 501;
    /** Clip lengths of the three fidgets, plus the 6-tick blend, so he does not walk off in the middle of one. */
    private static final int[] FIDGET_TICKS = {94, 58, 66};
    /**
     * Story keeps turning a held speaker's head every tick of a conversation. If his AI is off and nothing has done
     * that for this long, the hold was lost (a conversation that ended without letting go): switch his AI back on.
     */
    private static final int LOST_HOLD_TICKS = 60;

    private static final String HOME_TAG = "HaskelHome";
    private static final String SPOTS_TAG = "HaskelSpots";
    private static final String OFFSETS_TAG = "HaskelSpotOffsets";
    private static final String HOUSE_TAG = "HaskelHouse";
    private static final String HOUSE_OFFSET_TAG = "HaskelHouseOffset";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.haskel_hobb.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.haskel_hobb.walk");
    private static final RawAnimation[] FIDGETS = {
        RawAnimation.begin().thenPlay("animation.haskel_hobb.fidget_weight_shift"),
        RawAnimation.begin().thenPlay("animation.haskel_hobb.fidget_pack_settle"),
        RawAnimation.begin().thenPlay("animation.haskel_hobb.fidget_toe_tap"),
    };
    /** Longest fidget (4.4 s) plus a margin: after this he goes back to the idle regardless. */
    private static final int FIDGET_TIMEOUT_TICKS = 100;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos home;
    private final List<BlockPos> spots = new ArrayList<>();
    /** Spot offsets from the structure file, resolved against his position on his first tick. */
    private final List<BlockPos> pendingOffsets = new ArrayList<>();
    /** His spot in the house (walked to, never vanished to), and its offset from a structure file until resolved. */
    private BlockPos house;
    private BlockPos pendingHouseOffset;

    private long lastVanish = -1000;
    private long heldTouchedAt;
    private long nextFidgetAt = -1;
    private long fidgetUntil;
    private boolean stoodStill;
    private int appearIn;
    private UUID lastAttacker;

    // What the local player sees (set on the client only; never read on the server).
    /** True from the vanish until he steps out again, so his slide to the new spot is never seen. */
    public boolean clientHidden;
    public long clientHiddenSince;
    /** Sound cue waiting to be played by the client audio (0 none, 1 vanish, 2 appear). */
    public int clientPendingSound;
    public int clientFidget = -1;
    public long clientFidgetStart;
    /** Game time of his next grumble (client; -1 when not counting). */
    public long clientNextAmbient = -1;
    /** His body turn at the end of the last client tick (the dialogue portrait turns him; see the renderer). */
    public float clientTickBodyRot;
    /** The head turn (radians) the model added on top of the animation in the last world frame. */
    public float clientHeadYawAdded, clientHeadPitchAdded;

    public Fisherman(EntityType<? extends Fisherman> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        // Water is a wall to him. (Not its edges: his dock stands on waterlogged posts, so every dock board counts as
        // a water edge, and treating those as walls left him nowhere to walk.)
        setPathfindingMalus(BlockPathTypes.WATER, -1.0f);
        // His house has doors: he opens them, walks through and closes them behind him (see OpenDoorGoal below).
        if (getNavigation() instanceof GroundPathNavigation ground) ground.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, SPEED)
            .add(Attributes.FOLLOW_RANGE, 32.0)   // how far he can plan a walk (house to dock end and back)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ---- Spots ------------------------------------------------------------------------------------------------

    /** Sets his home spot and dock spots (absolute block positions; he stands on top of the block below each). */
    public void setSpots(BlockPos home, List<BlockPos> dockSpots) {
        this.home = home.immutable();
        spots.clear();
        for (BlockPos spot : dockSpots) spots.add(spot.immutable());
        pendingOffsets.clear();
        pendingHouseOffset = null;
    }

    /** A spot inside his house he walks to now and then (never one a hit sends him to). */
    public void setHouse(BlockPos house) {
        this.house = house == null ? null : house.immutable();
    }

    public BlockPos home() {
        return home;
    }

    /** Home first, then the dock spots, then the house. */
    List<BlockPos> allSpots() {
        List<BlockPos> all = new ArrayList<>(spots.size() + 2);
        if (home != null) all.add(home);
        all.addAll(spots);
        if (house != null) all.add(house);
        return all;
    }

    BlockPos nearestSpot() {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos spot : allSpots()) {
            double distance = spot.distToCenterSqr(position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = spot;
            }
        }
        return best;
    }

    @Override
    public float rotate(Rotation rotation) {
        pendingOffsets.replaceAll(offset -> offset.rotate(rotation));
        if (pendingHouseOffset != null) pendingHouseOffset = pendingHouseOffset.rotate(rotation);
        return super.rotate(rotation);
    }

    @Override
    public float mirror(Mirror mirror) {
        java.util.function.UnaryOperator<BlockPos> flip = offset -> switch (mirror) {
            case LEFT_RIGHT -> new BlockPos(offset.getX(), offset.getY(), -offset.getZ());
            case FRONT_BACK -> new BlockPos(-offset.getX(), offset.getY(), offset.getZ());
            default -> offset;
        };
        pendingOffsets.replaceAll(flip);
        if (pendingHouseOffset != null) pendingHouseOffset = flip.apply(pendingHouseOffset);
        return super.mirror(mirror);
    }

    // ---- Ticking ----------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (home == null) {
                // First tick: where he stands is home, and any spot offsets from a structure file become real spots.
                home = blockPosition();
                for (BlockPos offset : pendingOffsets) spots.add(home.offset(offset));
                pendingOffsets.clear();
                if (pendingHouseOffset != null) house = home.offset(pendingHouseOffset);
                pendingHouseOffset = null;
            }
            if (appearIn > 0 && --appearIn == 0) appear();
            long now = level().getGameTime();
            if (isNoAi() && now - heldTouchedAt > LOST_HOLD_TICKS) {
                setNoAi(false);
                getPersistentData().remove("superiorstory_noai_restore");
            }
            if (isNoAi()) watchWhileHeld();
            fidgetTick(now);
        }
        super.tick();
    }

    /**
     * Story holds a speaker still during a conversation by switching its AI off, and then keeps pointing its head
     * along its body. Haskel keeps looking at the player instead (Story's head turns are ignored while held).
     */
    private void watchWhileHeld() {
        Player target = level().getNearestPlayer(this, WATCH_RANGE + 2.0);
        if (target == null) return;
        float wanted = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        float head = yHeadRot + Mth.clamp(Mth.wrapDegrees(wanted - yHeadRot), -8.0f, 8.0f);
        super.setYHeadRot(head);
    }

    @Override
    public void setYHeadRot(float yaw) {
        if (!level().isClientSide && isNoAi()) {
            heldTouchedAt = level().getGameTime();   // Story's hold turns the head every tick while it lasts
            return;
        }
        super.setYHeadRot(yaw);
    }

    @Override
    public void setNoAi(boolean noAi) {
        super.setNoAi(noAi);
        if (noAi && level() != null) heldTouchedAt = level().getGameTime();
    }

    /** Server: while he stands still and nobody is talking to him, now and then one of his fidgets. */
    private void fidgetTick(long now) {
        boolean still = !isNoAi() && getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 1.0e-5;
        if (!still) {
            stoodStill = false;
            nextFidgetAt = -1;
            return;
        }
        if (nextFidgetAt < 0) {
            nextFidgetAt = stoodStill
                ? now + FIDGET_MIN_TICKS + random.nextInt(FIDGET_SPREAD_TICKS)
                : now + FIRST_FIDGET_MIN_TICKS + random.nextInt(FIRST_FIDGET_SPREAD_TICKS);
            stoodStill = true;
            return;
        }
        if (now < nextFidgetAt) return;
        int which = random.nextInt(FIDGET_TICKS.length);
        fidgetUntil = now + FIDGET_TICKS[which];
        nextFidgetAt = -1;
        level().broadcastEntityEvent(this, (byte) (EVENT_FIDGET + which));
    }

    /** True while a fidget plays (he waits for it to end before walking on). */
    boolean fidgeting() {
        return level().getGameTime() < fidgetUntil;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new FishermanGoals.StopForPlayer(this));
        goalSelector.addGoal(2, new FishermanGoals.ReturnToDock(this));
        goalSelector.addGoal(3, new FishermanGoals.WalkTheDock(this));
        goalSelector.addGoal(4, new FishermanGoals.WatchPlayer(this));
    }

    // ---- Turning: head first, body late -----------------------------------------------------------------------

    @Override
    public int getMaxHeadYRot() {
        return (int) MAX_HEAD_TURN;
    }

    @Override
    public int getHeadRotSpeed() {
        return 6;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new LateBodyTurn(this);
    }

    /**
     * Walking: the body faces where he walks. Standing: the body stays put while the head turns, up to its limit;
     * once the head has been turned more than 35 degrees for 1.5 s, the body follows slowly until it is nearly square.
     */
    private static final class LateBodyTurn extends BodyRotationControl {
        private final Fisherman mob;
        private int turnedTicks;
        private boolean catchingUp;

        LateBodyTurn(Fisherman mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public void clientTick() {
            double dx = mob.getX() - mob.xo;
            double dz = mob.getZ() - mob.zo;
            if (dx * dx + dz * dz > 2.5e-5) {
                mob.yBodyRot = Mth.approachDegrees(mob.yBodyRot, mob.getYRot(), 12.0f);
                turnedTicks = 0;
                catchingUp = false;
                return;
            }
            float offset = Mth.wrapDegrees(mob.yHeadRot - mob.yBodyRot);
            if (Math.abs(offset) > MAX_HEAD_TURN) {
                mob.yBodyRot = mob.yHeadRot - Math.signum(offset) * MAX_HEAD_TURN;
                offset = Math.signum(offset) * MAX_HEAD_TURN;
            }
            if (Math.abs(offset) > 35.0f) {
                if (++turnedTicks > 30) catchingUp = true;
            } else {
                turnedTicks = 0;
            }
            if (catchingUp) {
                mob.yBodyRot = Mth.approachDegrees(mob.yBodyRot, mob.yHeadRot, 3.0f);
                if (Math.abs(Mth.wrapDegrees(mob.yHeadRot - mob.yBodyRot)) < 10.0f) catchingUp = false;
            }
        }
    }

    // ---- He cannot be hurt; a hit sends him away in smoke -----------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // /kill and the void still work, so an operator can always remove him.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && player.getAbilities().instabuild && player.isShiftKeyDown()) {
            // Creative mode: sneak-hit removes him, for editing the cove.
            if (!level().isClientSide) discard();
            return true;
        }
        if (!level().isClientSide && attacker instanceof Player player && !player.isSpectator()) {
            long now = level().getGameTime();
            if (now - lastVanish >= HIT_COOLDOWN_TICKS) {
                lastVanish = now;
                vanish(player);
            }
        }
        return false;
    }

    private void vanish(Player from) {
        Vec3 target = vanishTarget(from);
        level().broadcastEntityEvent(this, EVENT_VANISH);
        getNavigation().stop();
        lastAttacker = from.getUUID();
        if (target != null) {
            teleportTo(target.x, target.y, target.z);
            float yaw = (float) (Mth.atan2(from.getZ() - target.z, from.getX() - target.x) * Mth.RAD_TO_DEG) - 90.0f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        appearIn = APPEAR_DELAY_TICKS;
    }

    private void appear() {
        level().broadcastEntityEvent(this, EVENT_APPEAR);
        if (lastAttacker != null && level().getPlayerByUUID(lastAttacker) instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("superior_tutorial.fisherman.hit." + (1 + random.nextInt(2))), true);
        }
        lastAttacker = null;
    }

    /**
     * Where a hit sends him: the spot on his dock (home or a dock spot, never the house) farthest from the player,
     * preferably 8 or more blocks off. Without any, a dry spot 8 to 12 blocks away.
     */
    private Vec3 vanishTarget(Player from) {
        Vec3 best = null;
        double bestDistance = -1.0;
        boolean bestFar = false;
        List<BlockPos> dock = new ArrayList<>(spots);
        if (home != null) dock.add(home);
        for (BlockPos spot : dock) {
            Vec3 stand = standingPoint(spot);
            if (stand == null) continue;
            double distance = stand.distanceToSqr(from.position());
            boolean far = distance >= MIN_VANISH_DISTANCE * MIN_VANISH_DISTANCE;
            if ((far && !bestFar) || (far == bestFar && distance > bestDistance)) {
                best = stand;
                bestDistance = distance;
                bestFar = far;
            }
        }
        if (best != null) return best;
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double reach = 8.0 + random.nextDouble() * 4.0;
            BlockPos column = BlockPos.containing(getX() + Math.cos(angle) * reach, getY(), getZ() + Math.sin(angle) * reach);
            for (int dy = 3; dy >= -3; dy--) {
                Vec3 stand = standingPoint(column.above(dy));
                if (stand != null && stand.distanceToSqr(from.position()) >= MIN_VANISH_DISTANCE * MIN_VANISH_DISTANCE) return stand;
            }
        }
        return null;
    }

    /**
     * Where he stands at a spot: on top of whatever is under it, a full block, a slab, a closed trapdoor or a cold
     * campfire alike (his dock spots stand on all of those), with room for him above and no water at his feet.
     * Null if he cannot stand there.
     */
    private Vec3 standingPoint(BlockPos spot) {
        Level level = level();
        BlockPos below = spot.below();
        net.minecraft.world.phys.shapes.VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
        if (floor.isEmpty()) return null;
        double y = below.getY() + floor.max(net.minecraft.core.Direction.Axis.Y);
        if (y < spot.getY() - 1.0 + 0.05) return null;   // too thin to stand on
        if (!level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()) return null;
        if (!level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()) return null;
        if (level.getFluidState(spot).is(FluidTags.WATER) || level.getFluidState(BlockPos.containing(spot.getX(), y + 0.05, spot.getZ())).is(FluidTags.WATER)) return null;
        return new Vec3(spot.getX() + 0.5, y, spot.getZ() + 0.5);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_VANISH) {
            clientHidden = true;
            clientHiddenSince = level().getGameTime();
            clientPendingSound = 1;
            smoke(36, 1.0);
        } else if (id >= EVENT_FIDGET && id < EVENT_FIDGET + FIDGETS.length) {
            clientFidget = id - EVENT_FIDGET;
            clientFidgetStart = level().getGameTime();
        } else if (id == EVENT_APPEAR) {
            clientHidden = false;
            clientPendingSound = 2;
            smoke(16, 0.6);
            for (int i = 0; i < 10; i++) {
                level().addParticle(ParticleTypes.SPLASH,
                    getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 1.2, 0.0, 0.1, 0.0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** Client: a thick burst of big smoke where he stands. */
    private void smoke(int count, double size) {
        for (int i = 0; i < count; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * size;
            double y = getY() + random.nextDouble() * 1.9;
            double z = getZ() + (random.nextDouble() - 0.5) * size;
            double vx = (random.nextDouble() - 0.5) * 0.12;
            double vz = (random.nextDouble() - 0.5) * 0.12;
            level().addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, vx, 0.03 + random.nextDouble() * 0.03, vz);
            if (i % 2 == 0) level().addParticle(ParticleTypes.POOF, x, y, z, vx * 2.0, 0.05, vz * 2.0);
            if (i % 3 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, vx, 0.02, vz);
        }
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
    public int getMaxFallDistance() {
        return 1;
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

    // ---- Saving -----------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putLong(HOME_TAG, home.asLong());
        long[] packed = new long[spots.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = spots.get(i).asLong();
        tag.putLongArray(SPOTS_TAG, packed);
        if (house != null) tag.putLong(HOUSE_TAG, house.asLong());
        if (pendingHouseOffset != null) {
            tag.putIntArray(HOUSE_OFFSET_TAG, new int[] {pendingHouseOffset.getX(), pendingHouseOffset.getY(), pendingHouseOffset.getZ()});
        }
        if (!pendingOffsets.isEmpty()) {
            int[] offsets = new int[pendingOffsets.size() * 3];
            for (int i = 0; i < pendingOffsets.size(); i++) {
                BlockPos offset = pendingOffsets.get(i);
                offsets[i * 3] = offset.getX();
                offsets[i * 3 + 1] = offset.getY();
                offsets[i * 3 + 2] = offset.getZ();
            }
            tag.putIntArray(OFFSETS_TAG, offsets);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = tag.contains(HOME_TAG) ? BlockPos.of(tag.getLong(HOME_TAG)) : null;
        spots.clear();
        for (long packed : tag.getLongArray(SPOTS_TAG)) spots.add(BlockPos.of(packed));
        pendingOffsets.clear();
        int[] offsets = tag.getIntArray(OFFSETS_TAG);
        for (int i = 0; i + 2 < offsets.length; i += 3) pendingOffsets.add(new BlockPos(offsets[i], offsets[i + 1], offsets[i + 2]));
        house = tag.contains(HOUSE_TAG) ? BlockPos.of(tag.getLong(HOUSE_TAG)) : null;
        int[] houseOffset = tag.getIntArray(HOUSE_OFFSET_TAG);
        pendingHouseOffset = houseOffset.length == 3 ? new BlockPos(houseOffset[0], houseOffset[1], houseOffset[2]) : null;
        if (!pendingOffsets.isEmpty() || pendingHouseOffset != null) home = null;   // a structure copy: resolve the offsets where he now stands
    }

    // ---- Animation --------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // One controller for everything, so nothing fights over his bones; 6 ticks of blending between clips.
        controllers.add(new AnimationController<>(this, "main", 6, this::animate));
    }

    private PlayState animate(AnimationState<Fisherman> state) {
        long now = level().getGameTime();
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
        if (speed > 0.05) {
            clientFidget = -1;
            // Matched to his real speed so his feet don't slide.
            state.getController().setAnimationSpeed(Mth.clamp(speed / WALK_ANIMATION_SPEED, 0.6, 1.8));
            return state.setAndContinue(WALK);
        }
        state.getController().setAnimationSpeed(1.0);
        if (clientFidget >= 0) {
            // Started by the server (see fidgetTick); runs to its end, then back to the idle.
            RawAnimation fidget = FIDGETS[clientFidget];
            boolean done = state.isCurrentAnimation(fidget) && state.getController().hasAnimationFinished();
            if (!done && now - clientFidgetStart < FIDGET_TIMEOUT_TICKS && !isNoAi()) return state.setAndContinue(fidget);
            clientFidget = -1;
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

}
