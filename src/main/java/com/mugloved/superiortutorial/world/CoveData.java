package com.mugloved.superiortutorial.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

/** Where this world's cave and cove were placed (once per world, in the overworld's data folder). */
public final class CoveData extends SavedData {
    private static final String NAME = "superior_tutorial_cove";

    /** True once the world was looked at: placed, or skipped (an older world, or no place for it). */
    boolean decided;
    BlockPos origin;
    Rotation rotation = Rotation.NONE;

    public static CoveData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(CoveData::load, CoveData::new, NAME);
    }

    public boolean placed() {
        return origin != null;
    }

    void set(BlockPos origin, Rotation rotation) {
        this.decided = true;
        this.origin = origin.immutable();
        this.rotation = rotation;
        setDirty();
    }

    void skip() {
        decided = true;
        setDirty();
    }

    private static CoveData load(CompoundTag tag) {
        CoveData data = new CoveData();
        data.decided = tag.getBoolean("Decided");
        if (tag.contains("Origin")) data.origin = BlockPos.of(tag.getLong("Origin"));
        data.rotation = Rotation.values()[Math.floorMod(tag.getInt("Rotation"), Rotation.values().length)];
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Decided", decided);
        if (origin != null) tag.putLong("Origin", origin.asLong());
        tag.putInt("Rotation", rotation.ordinal());
        return tag;
    }
}
