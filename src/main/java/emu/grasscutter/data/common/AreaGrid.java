package emu.grasscutter.data.common;

import emu.grasscutter.utils.Position;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The areas of a scene indexed by square tiles on x/z: each tile lists the areas whose bounding box overlaps it, so a
 * position is only tested against the few areas of its tile, not all the scene's
 */
public class AreaGrid<T> {
    /** The tile side: on scene 3's 348 weather areas, 256 leaves 3 areas in a tile on average, 27 at most */
    public static final float TILE_SIZE = 256;

    private final Long2ObjectMap<List<T>> tiles = new Long2ObjectOpenHashMap<>();

    /** Adds an area to the tiles its bounding box overlaps, a tile keeping its areas in the order added; skipped without bounds */
    public void add(T area, Bounds bounds) {
        if (bounds == null) return;
        for (int x = tile(bounds.minX()); x <= tile(bounds.maxX()); x++) {
            for (int z = tile(bounds.minZ()); z <= tile(bounds.maxZ()); z++) {
                tiles.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(area);
            }
        }
    }

    /** The areas that may contain a position, those of its tile, to be checked with their own polygon */
    public List<T> get(Position position) {
        return tiles.getOrDefault(key(tile(position.getX()), tile(position.getZ())), Collections.emptyList());
    }

    private static int tile(float coord) {
        return (int) Math.floor(coord / TILE_SIZE);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** A bounding box on x/z */
    public record Bounds(float minX, float minZ, float maxX, float maxZ) {
        /** The bounding box of a polygon's points, null without points */
        public static <P> Bounds of(P[] points, ToDoubleFunction<P> x, ToDoubleFunction<P> z) {
            if (points == null || points.length == 0) return null;
            float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (P point : points) {
                float px = (float) x.applyAsDouble(point), pz = (float) z.applyAsDouble(point);
                minX = Math.min(minX, px);
                minZ = Math.min(minZ, pz);
                maxX = Math.max(maxX, px);
                maxZ = Math.max(maxZ, pz);
            }
            return new Bounds(minX, minZ, maxX, maxZ);
        }
    }
}
