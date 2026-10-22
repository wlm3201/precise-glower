package com.preciseglower.client.search;

import com.preciseglower.client.compat.McCompat;
import com.preciseglower.client.config.BlockRule;
import com.preciseglower.client.config.Rule;
import com.preciseglower.client.render.BlockOutlineData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockTracker {
    private static final Direction[] FACES = Direction.values();

    private static final int FACES_PER_BLOCK = 96;

    private final Map<Long, Map<Long, Long>> chunks = new HashMap<>();

    private final Set<Long> scanned = new HashSet<>();
    private final Deque<Long> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();

    private volatile boolean outlineWanted;

    private volatile boolean outlineDirty = true;

    private int[] hitCounts = new int[0];
    private int renderedBlocks;
    private boolean truncated;

    private List<BlockRule> rules = new ArrayList<>();

    private List<Predicate<BlockState>> activePredicates = List.of();

    private List<String> lastMatcherKeys = List.of();

    private boolean rescanBoost;

    private int scanBudget = 4;

    private int maxDistance = 96;

    private int maxRendered = 8000;

    private int[] ruleRadius = new int[0];

    private int scanRadius = 6;

    private long lastChunkKey = Long.MIN_VALUE;
    private Map<Long, Long> lastChunkMap;

    public void setRules(List<BlockRule> rules) {
        boolean matchingChanged = !matcherKeys(rules).equals(lastMatcherKeys);
        lastMatcherKeys = matcherKeys(rules);
        this.rules = rules;
        this.hitCounts = new int[rules.size()];
        List<Predicate<BlockState>> predicates = new ArrayList<>();
        for (BlockRule rule : rules) {
            if (rule.enabled && rule.error() == null && rule.filter() != null) {
                predicates.add(rule.filter()::matches);
            }
        }
        this.activePredicates = predicates;
        this.outlineWanted = false;
        for (BlockRule rule : rules) {
            if (rule.enabled
                    && rule.error() == null
                    && rule.filter() != null
                    && rule.blockMode().outline()) {
                outlineWanted = true;
                break;
            }
        }
        outlineDirty = true;
        recomputeRanges();
        if (matchingChanged) rescanAll();
    }

    private void recomputeRanges() {
        int[] radius = new int[rules.size()];
        int maxChunks = 1;
        int globalChunks = globalRangeChunks();
        for (int i = 0; i < rules.size(); i++) {
            int chunks = Math.min(globalChunks, rules.get(i).rangeChunks());
            radius[i] = chunks - 1;
            if (chunks > maxChunks) maxChunks = chunks;
        }
        ruleRadius = radius;
        scanRadius = maxChunks - 1;
    }

    private int globalRangeChunks() {
        return Math.max(1, maxDistance >> 4);
    }

    private static int floorChunk(double value) {
        return (int) Math.floor(value) >> 4;
    }

    private static int floorChunk(int blockCoord) {
        return blockCoord >> 4;
    }

    private static List<String> matcherKeys(List<BlockRule> rules) {
        List<String> keys = new ArrayList<>(rules.size());
        for (BlockRule rule : rules) {
            keys.add((rule.enabled ? "1" : "0") + "|" + rule.id);
        }
        return keys;
    }

    public void setSettings(int scanBudget, int maxDistance, int maxRendered) {
        this.scanBudget = Math.max(1, scanBudget);
        this.maxDistance = Math.max(16, maxDistance);
        this.maxRendered = Math.max(100, maxRendered);
        recomputeRanges();
    }

    private void rescanAll() {
        synchronized (chunks) {
            rescanBoost = true;
            for (Long key : scanned) {
                if (queued.add(key)) pending.add(key);
            }
        }
    }

    public void reset() {
        synchronized (chunks) {
            chunks.clear();
            scanned.clear();
            queued.clear();
            pending.clear();
        }
        outlineDirty = true;
    }

    public boolean outlineWanted() {
        return outlineWanted;
    }

    public boolean consumeOutlineDirty() {
        if (!outlineDirty) return false;
        outlineDirty = false;
        return true;
    }

    public int hitCount(int ruleIndex) {
        return ruleIndex >= 0 && ruleIndex < hitCounts.length ? hitCounts[ruleIndex] : 0;
    }

    public int renderedBlocks() {
        return renderedBlocks;
    }

    public boolean truncated() {
        return truncated;
    }

    public void onChunkLoaded(ClientLevel level, int cx, int cz) {
        if (rules.isEmpty()) return;
        long key = ChunkPos.pack(cx, cz);
        synchronized (chunks) {
            scanned.remove(key);
            enqueue(key);
        }
    }

    public void onChunkUnloaded(ClientLevel level, int cx, int cz) {
        long key = ChunkPos.pack(cx, cz);
        synchronized (chunks) {
            chunks.remove(key);
            scanned.remove(key);
            queued.remove(key);
            pending.remove(key);
        }
        outlineDirty = true;
    }

    private void enqueue(long key) {
        if (queued.add(key)) pending.add(key);
    }

    public void onTick(ClientLevel level) {
        if (level == null) return;
        if (rules.isEmpty()) {
            if (!chunks.isEmpty()) reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos origin = mc.player.blockPosition();
        int radius = scanRadius;
        int cx0 = SectionPos.posToSectionCoord(origin.getX());
        int cz0 = SectionPos.posToSectionCoord(origin.getZ());

        synchronized (chunks) {
            boolean[] dropped = {false};
            chunks.keySet()
                    .removeIf(
                            key -> {
                                int x = ChunkPos.getX(key);
                                int z = ChunkPos.getZ(key);
                                if (Math.abs(x - cx0) > radius || Math.abs(z - cz0) > radius) {
                                    scanned.remove(key);
                                    queued.remove(key);
                                    pending.remove(key);
                                    dropped[0] = true;
                                    return true;
                                }
                                return false;
                            });
            if (dropped[0]) outlineDirty = true;
            scanned.removeIf(
                    key -> {
                        int x = ChunkPos.getX(key);
                        int z = ChunkPos.getZ(key);
                        if (Math.abs(x - cx0) > radius || Math.abs(z - cz0) > radius) {
                            queued.remove(key);
                            pending.remove(key);
                            return true;
                        }
                        return false;
                    });

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long key = ChunkPos.pack(cx0 + dx, cz0 + dz);
                    if (scanned.contains(key) || queued.contains(key)) continue;
                    if (level.getChunk(cx0 + dx, cz0 + dz, ChunkStatus.FULL, false) == null)
                        continue;
                    enqueue(key);
                }
            }

            int total = pending.size();
            int budget;
            if (rescanBoost) {
                if (total == 0) rescanBoost = false;
                budget = Math.max(scanBudget, (total + 5) / 6);
            } else {
                budget = scanBudget;
            }
            budget = Math.min(budget, total);
            for (int i = 0; i < budget; i++) {
                Long key = pending.poll();
                if (key == null) break;
                queued.remove(key);
                scanned.add(key);
                scanChunk(level, key);
            }
        }
    }

    private void scanChunk(ClientLevel level, long chunkKey) {
        int cx = ChunkPos.getX(chunkKey);
        int cz = ChunkPos.getZ(chunkKey);
        ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
        if (chunk == null) return;

        Map<Long, Long> found = new HashMap<>();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            PalettedContainer<BlockState> states = section.getStates();
            if (!sectionMaybeMatches(states)) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = states.get(x, y, z);
                        if (state.isAir()) continue;
                        long mask = matchMask(state);
                        if (mask == 0L) continue;
                        pos.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                        found.put(pos.asLong(), mask);
                    }
                }
            }
        }

        synchronized (chunks) {
            if (found.isEmpty()) chunks.remove(chunkKey);
            else chunks.put(chunkKey, found);
        }
        outlineDirty = true;
    }

    private boolean sectionMaybeMatches(PalettedContainer<BlockState> states) {
        for (int i = 0; i < activePredicates.size(); i++) {
            if (states.maybeHas(activePredicates.get(i))) return true;
        }
        return false;
    }

    private long matchMask(BlockState state) {
        long mask = 0L;
        int limit = Math.min(rules.size(), Long.SIZE);
        for (int i = 0; i < limit; i++) {
            BlockRule rule = rules.get(i);
            if (!rule.enabled || rule.error() != null || rule.filter() == null) continue;
            if (rule.filter().matches(state)) mask |= 1L << i;
        }
        return mask;
    }

    private int pick(int pcx, int pcz, int bx, int bz, long mask) {
        int limit = Math.min(rules.size(), Long.SIZE);
        for (int i = 0; i < limit; i++) {
            if ((mask & (1L << i)) == 0L) continue;
            if (Math.abs(floorChunk(bx) - pcx) > ruleRadius[i]) continue;
            if (Math.abs(floorChunk(bz) - pcz) > ruleRadius[i]) continue;
            return i;
        }
        return -1;
    }

    public void onBlockChanged(BlockPos pos, BlockState state) {
        if (rules.isEmpty()) return;
        long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
        synchronized (chunks) {
            if (!scanned.contains(chunkKey)) return;
            Map<Long, Long> map = chunks.get(chunkKey);
            long mask = state.isAir() ? 0L : matchMask(state);
            if (mask == 0L) {
                if (map != null && map.remove(pos.asLong()) != null) outlineDirty = true;
            } else {
                if (map == null) chunks.put(chunkKey, map = new HashMap<>());
                Long previous = map.put(pos.asLong(), mask);
                if (previous == null || previous != mask) outlineDirty = true;
            }
        }
    }

    public boolean collectOutline(ClientLevel level, BlockOutlineData out) {
        out.reset();
        if (rules.isEmpty() || !outlineWanted || level == null) return false;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = McCompat.cameraPos(mc);
        int pcx = floorChunk(cam.x());
        int pcz = floorChunk(cam.z());
        out.limit = Math.min(maxRendered * FACES_PER_BLOCK, BlockOutlineData.MAX_FACES);
        lastChunkKey = Long.MIN_VALUE;
        lastChunkMap = null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        synchronized (chunks) {
            for (Map<Long, Long> map : chunks.values()) {
                for (Map.Entry<Long, Long> entry : map.entrySet()) {
                    if (out.count >= out.limit) break;
                    long packed = entry.getKey();
                    int bx = BlockPos.getX(packed);
                    int by = BlockPos.getY(packed);
                    int bz = BlockPos.getZ(packed);
                    int index = pick(pcx, pcz, bx, bz, entry.getValue());
                    if (index < 0 || index >= rules.size()) continue;

                    BlockRule rule = rules.get(index);
                    if (!rule.enabled || !rule.blockMode().outline()) continue;

                    BlockState state = level.getBlockState(pos.set(bx, by, bz));
                    VoxelShape shape = state.getShape(level, pos);
                    if (shape.isEmpty()) continue;
                    int argb = rule.opaqueColor();
                    shape.forAllBoxes(
                            (x0, y0, z0, x1, y1, z1) -> {
                                for (int d = 0; d < FACES.length; d++) {
                                    out.addFace(bx, by, bz, x0, y0, z0, x1, y1, z1, d, argb);
                                }
                            });
                }
                if (out.count >= out.limit) break;
            }
        }
        return out.count > 0;
    }

    public int render(ClientLevel level) {
        renderedBlocks = 0;
        truncated = false;
        if (rules.isEmpty() || level == null) return 0;
        hitCounts = new int[rules.size()];

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = McCompat.cameraPos(mc);
        int pcx = floorChunk(cam.x());
        int pcz = floorChunk(cam.z());
        lastChunkKey = Long.MIN_VALUE;
        lastChunkMap = null;

        synchronized (chunks) {
            for (Map<Long, Long> map : chunks.values()) {
                for (Map.Entry<Long, Long> entry : map.entrySet()) {
                    if (renderedBlocks >= maxRendered) {
                        truncated = true;
                        return renderedBlocks;
                    }
                    long packed = entry.getKey();
                    int bx = BlockPos.getX(packed);
                    int by = BlockPos.getY(packed);
                    int bz = BlockPos.getZ(packed);
                    int index = pick(pcx, pcz, bx, bz, entry.getValue());
                    if (index < 0 || index >= rules.size()) continue;

                    Rule rule = rules.get(index);
                    hitCounts[index]++;
                    renderedBlocks++;

                    if (rule.blockMode().wireframe()) drawBlock(bx, by, bz, rule.color, rule);
                }
            }
        }
        return renderedBlocks;
    }

    private void drawBlock(int bx, int by, int bz, int color, Rule rule) {
        float pad = 0.002F;
        double x0 = bx - pad;
        double y0 = by - pad;
        double z0 = bz - pad;
        double x1 = bx + 1 + pad;
        double y1 = by + 1 + pad;
        double z1 = bz + 1 + pad;

        int mask = 0;
        if (rule.mergeAdjacent) {
            for (int i = 0; i < FACES.length; i++) {
                Direction dir = FACES[i];
                if (isHighlighted(bx + dir.getStepX(), by + dir.getStepY(), bz + dir.getStepZ())) {
                    mask |= 1 << i;
                }
            }
        }

        if (mask == 0) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, by, bz);
            GizmoProperties props =
                    Gizmos.cuboid(pos, pad, GizmoStyle.stroke(color, rule.lineWidth));
            props.setAlwaysOnTop();
        } else {
            drawEdges(bx, by, bz, mask, color, rule, x0, y0, z0, x1, y1, z1);
        }
    }

    private void drawEdges(
            int bx,
            int by,
            int bz,
            int mask,
            int color,
            Rule rule,
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1) {
        edge(bx, by, bz, mask, color, rule, x0, y0, z0, x1, y0, z0, DOWN, NORTH);
        edge(bx, by, bz, mask, color, rule, x0, y0, z1, x1, y0, z1, DOWN, SOUTH);
        edge(bx, by, bz, mask, color, rule, x0, y0, z0, x0, y0, z1, DOWN, WEST);
        edge(bx, by, bz, mask, color, rule, x1, y0, z0, x1, y0, z1, DOWN, EAST);
        edge(bx, by, bz, mask, color, rule, x0, y1, z0, x1, y1, z0, UP, NORTH);
        edge(bx, by, bz, mask, color, rule, x0, y1, z1, x1, y1, z1, UP, SOUTH);
        edge(bx, by, bz, mask, color, rule, x0, y1, z0, x0, y1, z1, UP, WEST);
        edge(bx, by, bz, mask, color, rule, x1, y1, z0, x1, y1, z1, UP, EAST);
        edge(bx, by, bz, mask, color, rule, x0, y0, z0, x0, y1, z0, WEST, NORTH);
        edge(bx, by, bz, mask, color, rule, x1, y0, z0, x1, y1, z0, EAST, NORTH);
        edge(bx, by, bz, mask, color, rule, x0, y0, z1, x0, y1, z1, WEST, SOUTH);
        edge(bx, by, bz, mask, color, rule, x1, y0, z1, x1, y1, z1, EAST, SOUTH);
    }

    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    private void edge(
            int bx,
            int by,
            int bz,
            int mask,
            int color,
            Rule rule,
            double ax,
            double ay,
            double az,
            double bxx,
            double byy,
            double bzz,
            int faceA,
            int faceB) {
        boolean naH = (mask & (1 << faceA)) != 0;
        boolean nbH = (mask & (1 << faceB)) != 0;
        boolean draw;
        if (!naH && !nbH) {
            draw = true;
        } else {
            Direction a = FACES[faceA];
            Direction b = FACES[faceB];
            boolean diagH =
                    isHighlighted(
                            bx + a.getStepX() + b.getStepX(),
                            by + a.getStepY() + b.getStepY(),
                            bz + a.getStepZ() + b.getStepZ());
            if (naH && nbH) {
                draw = !diagH;
            } else {
                draw = diagH;
            }
        }
        if (!draw) return;
        GizmoProperties props =
                Gizmos.line(new Vec3(ax, ay, az), new Vec3(bxx, byy, bzz), color, rule.lineWidth);
        props.setAlwaysOnTop();
    }

    private boolean isHighlighted(int x, int y, int z) {
        long key = ChunkPos.pack(x >> 4, z >> 4);
        Map<Long, Long> map;
        if (key == lastChunkKey) map = lastChunkMap;
        else {
            map = chunks.get(key);
            lastChunkKey = key;
            lastChunkMap = map;
        }
        return map != null && map.containsKey(BlockPos.asLong(x, y, z));
    }
}
