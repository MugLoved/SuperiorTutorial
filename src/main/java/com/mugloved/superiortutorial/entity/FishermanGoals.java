package com.mugloved.superiortutorial.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/** Haskel's dock routine: stop for players, get back to the dock if moved off it, wander between spots, watch people. */
final class FishermanGoals {
    private FishermanGoals() {}

    /** The nearest player he should notice within {@code range}, or null. */
    static Player nearestPlayer(Fisherman fisherman, double range) {
        Player best = null;
        double bestDistance = range * range;
        for (Player player : fisherman.level().players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double distance = fisherman.distanceToSqr(player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /** A player within 3 blocks: he stops where he is (finishing his step) and turns to them, head first. */
    static final class StopForPlayer extends Goal {
        private final Fisherman fisherman;
        private Player target;

        StopForPlayer(Fisherman fisherman) {
            this.fisherman = fisherman;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = nearestPlayer(fisherman, Fisherman.STOP_RANGE);
            return target != null && !fisherman.isInWater();
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && !target.isSpectator()
                && fisherman.distanceToSqr(target) <= (Fisherman.STOP_RANGE + 1.0) * (Fisherman.STOP_RANGE + 1.0);
        }

        @Override
        public void start() {
            fisherman.getNavigation().stop();
        }

        @Override
        public void stop() {
            target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player nearer = nearestPlayer(fisherman, Fisherman.STOP_RANGE);
            if (nearer != null) target = nearer;
            fisherman.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        }
    }

    /** Pushed, knocked or teleported off his spots (more than 3 blocks from all of them): walk back to the nearest. */
    static final class ReturnToDock extends Goal {
        private final Fisherman fisherman;
        private BlockPos goal;
        private int retry;

        ReturnToDock(Fisherman fisherman) {
            this.fisherman = fisherman;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--retry > 0) return false;
            retry = 40;
            goal = fisherman.nearestSpot();
            return goal != null && goal.distToCenterSqr(fisherman.position()) > 9.0;
        }

        @Override
        public void start() {
            fisherman.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !fisherman.getNavigation().isDone() && goal.distToCenterSqr(fisherman.position()) > 1.0;
        }
    }

    /** He stays at a spot 15 to 35 seconds, then walks to another; about one walk in four goes home. */
    static final class WalkTheDock extends Goal {
        private final Fisherman fisherman;
        private int waitTicks = 100;
        private BlockPos goal;

        WalkTheDock(Fisherman fisherman) {
            this.fisherman = fisherman;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--waitTicks > 0) return false;
            waitTicks = 300 + fisherman.getRandom().nextInt(401);
            List<BlockPos> all = fisherman.allSpots();
            if (all.size() < 2) return false;
            BlockPos here = fisherman.nearestSpot();
            BlockPos home = fisherman.home();
            if (home != null && !home.equals(here) && fisherman.getRandom().nextInt(4) == 0) {
                goal = home;
            } else {
                goal = all.get(fisherman.getRandom().nextInt(all.size()));
                if (goal.equals(here)) return false;
            }
            return fisherman.getNavigation().createPath(goal, 0) != null;
        }

        @Override
        public void start() {
            fisherman.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !fisherman.getNavigation().isDone();
        }
    }

    /** His head follows the nearest player within 6 blocks; the body catches up late (see Fisherman.LateBodyTurn). */
    static final class WatchPlayer extends Goal {
        private final Fisherman fisherman;
        private Player target;

        WatchPlayer(Fisherman fisherman) {
            this.fisherman = fisherman;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = nearestPlayer(fisherman, Fisherman.WATCH_RANGE);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && fisherman.distanceToSqr(target) <= Fisherman.WATCH_RANGE * Fisherman.WATCH_RANGE * 1.4;
        }

        @Override
        public void stop() {
            target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            fisherman.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        }
    }
}
