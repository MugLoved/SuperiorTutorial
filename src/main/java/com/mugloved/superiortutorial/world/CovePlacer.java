package com.mugloved.superiortutorial.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mugloved.superiortutorial.SuperiorTutorial;
import com.mugloved.superiortutorial.TutorialConfig;
import com.superior.lib.api.unlock.PlayerUnlockApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Puts Mug's cave and cove (structures/cove.nbt, cut from Mug's world) into the world.
 *
 * <ul>
 *   <li>A new world gets it once, near spawn, on the flattest dry ground nearby; an older world never gets it by
 *   itself ({@code /superior_tutorial place_cove} puts it where an operator stands).</li>
 *   <li>The file holds only the open space inside, the rock within 3 blocks of it, the surface around the opening
 *   and the builds above it. Everything else is left out, so the world's own rock and soil stay around it. The
 *   sinkhole's opening is cleared up to the sky.</li>
 *   <li>A new world's site is chosen before its first chunk is made, and from then on world generation grows no
 *   trees or plants over or into the cove ({@link CoveGuard}), so nothing has to be taken down afterwards (which
 *   distant-terrain mods never saw). In an older world, {@code place_cove} takes down any tree in or leaning over
 *   the opening whole first.</li>
 *   <li>The Corpse and his workbench, Haskel and the chests are in the file. The cave's chests and the one under the
 *   dock have loot tables ({@code chests/cove_secret}, {@code chests/cove_sunken}, which always holds Haskel's crate),
 *   so Lootr gives every player their own. The cove is always placed unturned, and blocks keep exactly the shape they
 *   were saved with (posts, fences and the like are not reconnected).</li>
 *   <li>Each player starts in the cave the first time they join (key {@link #CAVE_STARTED}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SuperiorTutorial.MOD_ID)
public final class CovePlacer {
    public static final ResourceLocation COVE = new ResourceLocation(SuperiorTutorial.MOD_ID, "cove");
    /** Set when a player has been put in the cave on their first join (or chose to skip it). */
    public static final String CAVE_STARTED = "superior_tutorial_cave_started";

    // Points in the file (local block coordinates; see tools/cut.py in the fisherman folder).
    /** The grass surface around the sinkhole. */
    private static final int SURFACE = 45;
    /** Top of the cave's rock box. */
    private static final int CAVE_TOP = 24;
    /** The cave is x 0..40 of the file; the cavern and pond are x 41 and up. */
    private static final int CAVERN_FROM_X = 41;
    /** Where a new player starts: on the first rock out of the water at the dark end of the cave, facing the Corpse. */
    private static final Vec3 SPAWN = new Vec3(5.5, 14.0, 20.5);
    private static final float SPAWN_YAW = -90.0f;

    /** Ground over the cavern may be this many blocks uneven; more and the rim floats or gets buried. */
    private static final int MAX_UNEVEN = 6;
    private static final int SEARCH_RADIUS = 512;

    private CovePlacer() {}

    // ---- New worlds -------------------------------------------------------------------------------------------

    /**
     * A brand-new world, before its first chunk is made: choose the cove's site now, so world generation already
     * knows to grow no trees or plants over it (see {@link CoveGuard}). The cove itself is placed once the server
     * has started.
     */
    @SubscribeEvent
    public static void chooseSite(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!TutorialConfig.PLACE_COVE.get()) return;
        CoveData data = CoveData.get(level);
        if (data.decided || data.chosen != null) return;
        Optional<StructureTemplate> template = template(level);
        if (template.isEmpty()) return;
        // Where vanilla will look for the world spawn (worked out from the noise alone; no chunk is made for it).
        BlockPos spawn = new ChunkPos(level.getChunkSource().randomState().sampler().findSpawnPosition()).getMiddleBlockPosition(0);
        BlockPos origin = findSite(level, template.get(), Rotation.NONE, spawn);
        if (origin == null) return;
        data.choose(origin);
        CoveGuard.set(template.get(), origin, origin.getY() + SURFACE, false);
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        CoveGuard.clear();
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        CoveData data = CoveData.get(level);
        if (data.placed()) {
            // A world played before: keep guarding the finished cove against chunks made from here on.
            template(level).ifPresent(t -> CoveGuard.set(t, data.origin, data.origin.getY() + SURFACE, true));
            return;
        }
        if (data.chosen != null && !data.decided) {
            Optional<StructureTemplate> chosenTemplate = template(level);
            if (chosenTemplate.isEmpty()) {
                data.skip();
                return;
            }
            BlockPos origin = data.chosen;
            place(level, chosenTemplate.get(), origin, Rotation.NONE);
            data.set(origin, Rotation.NONE);
            return;
        }
        if (data.decided) return;
        if (!TutorialConfig.PLACE_COVE.get() || level.getGameTime() > 200) {
            // An older world (or placing is off): never dig into a world someone has already played.
            data.skip();
            return;
        }
        Optional<StructureTemplate> template = template(level);
        if (template.isEmpty()) {
            data.skip();
            return;
        }
        // Never turned: some modded blocks in the build (Quark posts, for one) don't turn with a structure, so a turned
        // cove put them on the wrong axis.
        Rotation rotation = Rotation.NONE;
        BlockPos origin = findSite(level, template.get(), rotation, level.getSharedSpawnPos());
        if (origin == null) {
            SuperiorTutorial.LOGGER.warn("No place near spawn for the tutorial cove; use /superior_tutorial place_cove");
            data.skip();
            return;
        }
        place(level, template.get(), origin, rotation);
        data.set(origin, rotation);
    }

    private static Optional<StructureTemplate> template(ServerLevel level) {
        Optional<StructureTemplate> template = level.getStructureManager().get(COVE);
        if (template.isEmpty()) SuperiorTutorial.LOGGER.error("Missing structure {}", COVE);
        return template;
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(Mirror.NONE)
            .setRotationPivot(BlockPos.ZERO)
            .setIgnoreEntities(false)
            .setKeepLiquids(false)
            // Blocks keep the shape they were saved with; reshaping turned Mug's posts the wrong way.
            .setKnownShape(true);
    }

    /** A file point in the world. */
    private static Vec3 toWorld(BlockPos origin, Rotation rotation, Vec3 local) {
        Vec3 turned = StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
        return turned.add(origin.getX(), origin.getY(), origin.getZ());
    }

    private static BlockPos toWorld(BlockPos origin, Rotation rotation, int x, int y, int z) {
        Vec3 point = toWorld(origin, rotation, new Vec3(x + 0.5, y, z + 0.5));
        return BlockPos.containing(point);
    }

    /**
     * Rings out from world spawn. A site is good when the ground over the cavern is even and dry (no ocean, river or
     * beach) and well above sea level, and the cave stays covered. The nearest good site wins; failing that, the most
     * even one found.
     */
    private static BlockPos findSite(ServerLevel level, StructureTemplate template, Rotation rotation, BlockPos spawn) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        int sea = generator.getSeaLevel();
        var size = template.getSize();
        // Where the opening's middle is in the file, so candidates are centred on the sinkhole.
        int midX = (CAVERN_FROM_X + size.getX()) / 2;
        int midZ = size.getZ() / 2;

        BlockPos mid = toWorld(BlockPos.ZERO, rotation, midX, 0, midZ);
        BlockPos best = null;
        int bestUneven = Integer.MAX_VALUE;
        for (int ring = 0; ring <= SEARCH_RADIUS; ring += 48) {
            int steps = ring == 0 ? 1 : Math.max(8, ring / 12);
            for (int s = 0; s < steps; s++) {
                double angle = Math.PI * 2 * s / steps;
                int cx = spawn.getX() + (int) Math.round(Math.cos(angle) * ring);
                int cz = spawn.getZ() + (int) Math.round(Math.sin(angle) * ring);
                // Origin at y 0 so that the file's middle lands on (cx, cz).
                BlockPos origin0 = new BlockPos(cx - mid.getX(), 0, cz - mid.getZ());

                List<Integer> cavern = new ArrayList<>();
                List<Integer> cave = new ArrayList<>();
                boolean wet = false;
                for (int x = 0; x < size.getX() && !wet; x += 8) {
                    for (int z = 0; z < size.getZ(); z += 8) {
                        BlockPos at = toWorld(origin0, rotation, x, 0, z);
                        int h = generator.getBaseHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
                        if (x >= CAVERN_FROM_X) {
                            cavern.add(h);
                            Holder<Biome> biome = level.getUncachedNoiseBiome(at.getX() >> 2, h >> 2, at.getZ() >> 2);
                            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
                                wet = true;
                                break;
                            }
                        } else {
                            cave.add(h);
                        }
                    }
                }
                if (wet || cavern.isEmpty()) continue;
                cavern.sort(Integer::compare);
                int surface = cavern.get(cavern.size() / 2);
                int uneven = cavern.get(cavern.size() - 1) - cavern.get(0);
                int y = surface - 1 - SURFACE;   // heights are the first air above the ground
                if (surface < sea + 4) continue;
                if (y <= level.getMinBuildHeight() + 1 || y + size.getY() >= level.getMaxBuildHeight()) continue;
                int caveRoof = y + CAVE_TOP + 4;
                if (cave.stream().anyMatch(h -> h < caveRoof)) continue;
                BlockPos origin = origin0.atY(y);
                if (uneven <= MAX_UNEVEN / 2) return origin;
                if (uneven <= MAX_UNEVEN && uneven < bestUneven) {
                    best = origin;
                    bestUneven = uneven;
                }
            }
            if (best != null && ring >= 192) return best;
        }
        return best;
    }

    /** At most this many tree blocks are taken down in one sweep, so a big forest canopy can't run away. */
    private static final int MAX_TREE_BLOCKS = 12000;
    /** How far past the file's edges a tree may reach and still be taken down. */
    private static final int TREE_MARGIN = 16;
    /** A tree within this many blocks (sideways) of the opening is taken down. */
    private static final int TREE_RING = CoveGuard.TREE_RING;

    /**
     * Takes down every tree in or near the opening, whole: all logs and leaves touching it, so no branch or canopy is
     * left in the air. Blocks of the cove itself ({@code keep}) are never touched, and unloaded chunks are left alone.
     */
    private static void clearTrees(ServerLevel level, List<BlockPos> openingColumns, int surfaceY, BlockPos min, BlockPos max,
                                   Set<BlockPos> keep) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        Set<Long> columns = new HashSet<>();
        for (BlockPos column : openingColumns) {
            for (int dx = -TREE_RING; dx <= TREE_RING; dx++) {
                for (int dz = -TREE_RING; dz <= TREE_RING; dz++) {
                    columns.add(BlockPos.asLong(column.getX() + dx, 0, column.getZ() + dz));
                }
            }
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (long column : columns) {
            int x = BlockPos.getX(column), z = BlockPos.getZ(column);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = surfaceY - 4; y < top; y++) {
                at.set(x, y, z);
                if (keep.contains(at) || !CoveGuard.isTree(level.getBlockState(at))) continue;
                BlockPos pos = at.immutable();
                if (seen.add(pos)) queue.add(pos);
            }
        }
        int removed = 0;
        while (!queue.isEmpty() && removed < MAX_TREE_BLOCKS) {
            BlockPos pos = queue.poll();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            removed++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (next.getX() < min.getX() || next.getX() > max.getX() || next.getZ() < min.getZ() || next.getZ() > max.getZ()) continue;
                        if (seen.contains(next) || keep.contains(next) || !level.hasChunk(next.getX() >> 4, next.getZ() >> 4)) continue;
                        if (CoveGuard.isTree(level.getBlockState(next))) {
                            seen.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        if (removed > 0) SuperiorTutorial.LOGGER.info("Took down {} tree blocks over the tutorial cove", removed);
    }

    /** The opening's columns (world positions at the surface). */
    private static List<BlockPos> openingColumns(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
        int surfaceY = origin.getY() + SURFACE;
        List<BlockPos> opening = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.AIR)) {
            if (info.pos().getY() == surfaceY) opening.add(info.pos());
        }
        return opening;
    }

    /**
     * Takes down any trees over the opening (none in a new world, where generation never grew them; this is for
     * {@code place_cove} in an older world), clears it up to the sky, then places the file (blocks, chests, the NPCs).
     */
    static void place(ServerLevel level, StructureTemplate template, BlockPos origin, Rotation rotation) {
        StructurePlaceSettings settings = settings(rotation);
        int surfaceY = origin.getY() + SURFACE;
        List<BlockPos> opening = openingColumns(template, origin, settings);
        var size = template.getSize(rotation);
        BlockPos min = origin.offset(-TREE_MARGIN, 0, -TREE_MARGIN);
        BlockPos max = origin.offset(size.getX() + TREE_MARGIN, 0, size.getZ() + TREE_MARGIN);
        clearTrees(level, opening, surfaceY, min, max, Set.of());
        for (BlockPos column : opening) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
            BlockPos.MutableBlockPos at = column.mutable();
            for (int y = surfaceY + 1; y < top; y++) {
                at.setY(y);
                if (!level.getBlockState(at).isAir()) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        // Chunks made from here on must not grow anything into it.
        CoveGuard.set(template, origin, surfaceY, true);
        SuperiorTutorial.LOGGER.info("Placed the tutorial cove at {} turned {}", origin, rotation);
    }

    // ---- Players ----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TutorialConfig.START_IN_CAVE.get() || PlayerUnlockApi.isUnlocked(player, CAVE_STARTED)) return;
        ServerLevel level = player.server.overworld();
        CoveData data = CoveData.get(level);
        if (!data.placed()) return;
        sendToCave(player, level, data);
        PlayerUnlockApi.unlock(player, CAVE_STARTED, CAVE_STARTED, false);
    }

    private static void sendToCave(ServerPlayer player, ServerLevel level, CoveData data) {
        Vec3 spot = toWorld(data.origin, data.rotation, SPAWN);
        float yaw = Mth.wrapDegrees(SPAWN_YAW + turn(data.rotation));
        player.teleportTo(level, spot.x, spot.y, spot.z, yaw, 0.0f);
    }

    private static float turn(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90.0f;
            case CLOCKWISE_180 -> 180.0f;
            case COUNTERCLOCKWISE_90 -> 270.0f;
            default -> 0.0f;
        };
    }

    // ---- Commands ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("superior_tutorial")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("place_cove").executes(context -> placeHere(context.getSource())))
            .then(Commands.literal("cave").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ServerLevel level = player.server.overworld();
                CoveData data = CoveData.get(level);
                if (!data.placed()) {
                    context.getSource().sendFailure(Component.translatable("commands.superior_tutorial.no_cove"));
                    return 0;
                }
                sendToCave(player, level, data);
                return 1;
            })));
    }

    /** Places the cove so the sinkhole opens where the player stands, facing the way the file was built. */
    private static int placeHere(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (level != player.server.overworld()) {
            source.sendFailure(Component.translatable("commands.superior_tutorial.overworld_only"));
            return 0;
        }
        Optional<StructureTemplate> template = template(level);
        if (template.isEmpty()) return 0;
        // The opening's middle, found from the file itself.
        StructurePlaceSettings settings = settings(Rotation.NONE);
        long sx = 0, sz = 0, n = 0;
        for (StructureTemplate.StructureBlockInfo info : template.get().filterBlocks(BlockPos.ZERO, settings, Blocks.AIR)) {
            if (info.pos().getY() != SURFACE) continue;
            sx += info.pos().getX();
            sz += info.pos().getZ();
            n++;
        }
        if (n == 0) return 0;
        BlockPos feet = player.blockPosition();
        BlockPos origin = new BlockPos(feet.getX() - (int) (sx / n), feet.getY() - 1 - SURFACE, feet.getZ() - (int) (sz / n));
        if (origin.getY() <= level.getMinBuildHeight()) {
            source.sendFailure(Component.translatable("commands.superior_tutorial.too_low"));
            return 0;
        }
        place(level, template.get(), origin, Rotation.NONE);
        CoveData.get(level).set(origin, Rotation.NONE);
        source.sendSuccess(() -> Component.translatable("commands.superior_tutorial.placed", origin.getX(), origin.getY(), origin.getZ()), true);
        return 1;
    }
}
