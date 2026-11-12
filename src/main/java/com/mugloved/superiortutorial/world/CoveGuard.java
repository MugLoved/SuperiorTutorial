package com.mugloved.superiortutorial.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.registries.ForgeRegistries;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Keeps world generation from growing anything over or into the cove, so the trees and plants there are never made
 * in the first place (taking them down afterwards left distant-terrain (LOD) mods still drawing them, and let plants
 * from chunks made later land inside the sinkhole).
 *
 * <p>The cove's site is chosen before the first chunk around spawn is made (see {@link CovePlacer#chooseSite}), and
 * from then on, in the overworld only:
 * <ul>
 *   <li>no tree (vanilla tree, huge fungus or huge mushroom feature, or any feature with "tree" in its name) is
 *   started within {@link #NO_TREE_RING} blocks of the opening, so no canopy is cut off at the edge;</li>
 *   <li>no tree block (log, leaves, vines...) is set within {@link #TREE_RING} blocks of the opening, which catches
 *   modded trees made by other kinds of features;</li>
 *   <li>no plant is set in the opening's columns, nor anywhere in the cove's box below the surface;</li>
 *   <li>once the cove is placed, nothing at all is set in its box below the surface (the cave and cavern are
 *   finished; later chunks next to it can't spill ore, lakes or plants into them).</li>
 * </ul>
 * Blocks the mod places itself (the cove file) go straight into the level and are not affected.
 */
public final class CoveGuard {
    /** No tree may start within this many blocks (sideways) of the opening. */
    static final int NO_TREE_RING = 8;
    /** No tree block may be set within this many blocks of the opening. */
    static final int TREE_RING = 3;

    private record Zone(LongSet opening, LongSet treeRing, LongSet noTreeRing,
                        int minX, int maxX, int minZ, int maxZ, int bottomY, int surfaceY, boolean placed) {
        /** Inside the cove's box, or near enough to its opening to matter (the box plus the widest ring). */
        boolean near(int x, int z) {
            return x >= minX - NO_TREE_RING && x <= maxX + NO_TREE_RING && z >= minZ - NO_TREE_RING && z <= maxZ + NO_TREE_RING;
        }

        boolean inBox(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static volatile Zone zone;

    private CoveGuard() {}

    /** Guards the cove whose file is {@code template}, placed (or to be placed) unturned at {@code origin}. */
    static void set(StructureTemplate template, BlockPos origin, int surfaceY, boolean placed) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
        LongSet opening = new LongOpenHashSet();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.AIR)) {
            if (info.pos().getY() == surfaceY) opening.add(column(info.pos().getX(), info.pos().getZ()));
        }
        var size = template.getSize();
        zone = new Zone(opening, dilate(opening, TREE_RING), dilate(opening, NO_TREE_RING),
            origin.getX(), origin.getX() + size.getX() - 1, origin.getZ(), origin.getZ() + size.getZ() - 1,
            origin.getY(), surfaceY, placed);
    }

    /** A new world or server: forget the last world's cove. */
    static void clear() {
        zone = null;
    }

    private static LongSet dilate(LongSet columns, int ring) {
        LongSet out = new LongOpenHashSet();
        for (long c : columns) {   // (x, z) packed as in column()
            int x = (int) (c >> 32), z = (int) c;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (dx * dx + dz * dz <= ring * ring + ring) out.add(column(x + dx, z + dz));
                }
            }
        }
        return out;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static boolean overworld(WorldGenLevel level) {
        ServerLevel server = level.getLevel();
        return server != null && server.dimension() == Level.OVERWORLD;
    }

    /** World generation is about to set {@code state} at {@code pos}: false to leave the block as it is. */
    public static boolean denyWrite(WorldGenLevel level, BlockPos pos, BlockState state) {
        Zone z = zone;
        if (z == null) return false;
        int x = pos.getX(), y = pos.getY(), zz = pos.getZ();
        if (!z.near(x, zz)) return false;
        long column = column(x, zz);
        boolean inBox = z.inBox(x, zz);
        boolean below = inBox && y >= z.bottomY && y < z.surfaceY - 4;
        boolean nearOpening = z.treeRing.contains(column);
        if (!below && !nearOpening) return false;
        if (!overworld(level)) return false;
        if (below && z.placed) return true;
        if (state.isAir()) return false;
        boolean tree = isTree(state);
        boolean plant = tree || isPlant(state);
        if (below) return plant;
        if (z.opening.contains(column)) return plant && y >= z.surfaceY - 4;
        return tree && y >= z.surfaceY - 4;
    }

    /** World generation is about to run {@code feature} at {@code origin}: true to skip it. */
    public static boolean denyFeature(WorldGenLevel level, Feature<?> feature, BlockPos origin) {
        Zone z = zone;
        if (z == null || !z.near(origin.getX(), origin.getZ()) || !z.noTreeRing.contains(column(origin.getX(), origin.getZ()))) return false;
        if (origin.getY() < z.surfaceY - 8) return false;
        if (!isTreeFeature(feature)) return false;
        return overworld(level);
    }

    private static boolean isTreeFeature(Feature<?> feature) {
        if (feature instanceof TreeFeature || feature instanceof HugeFungusFeature || feature instanceof AbstractHugeMushroomFeature) return true;
        ResourceLocation id = ForgeRegistries.FEATURES.getKey(feature);
        return id != null && id.getPath().contains("tree");
    }

    /** Tree blocks a cleared opening could leave floating (modded trees too: by tag, leaves class, or name). */
    static boolean isTree(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS)
            || state.getBlock() instanceof LeavesBlock
            || state.is(Blocks.VINE) || state.is(Blocks.BEE_NEST) || state.is(Blocks.COCOA)
            || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
            || state.is(Blocks.MANGROVE_ROOTS)) return true;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath();
        if (path.contains("dripleaf") || path.contains("pumpkin") || path.contains("melon")) return false;
        return path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_leaves") || path.endsWith("_stem")
            || path.endsWith("_hyphae");
    }

    /** Flowers, grass, bushes, vines, lichen, cactus, cane and the like (anything that pops off without support). */
    static boolean isPlant(BlockState state) {
        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) return false;
        Block block = state.getBlock();
        return block instanceof BushBlock || block instanceof IPlantable || block instanceof GrowingPlantBlock
            || block instanceof VineBlock || block instanceof MultifaceBlock || block instanceof CactusBlock
            || block instanceof SugarCaneBlock || block instanceof BambooStalkBlock
            || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE_BY_TREES)
            || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.BIG_DRIPLEAF) || state.is(Blocks.BIG_DRIPLEAF_STEM)
            || state.is(Blocks.SPORE_BLOSSOM) || state.is(Blocks.HANGING_ROOTS);
    }
}
