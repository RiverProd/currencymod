package net.currencymod.plots;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;

/**
 * Immutable value type identifying a specific chunk in a specific dimension.
 * Used as the map key for the world plot registry.
 *
 * String encoding (used as JSON key): "chunkX,chunkZ,dimension"
 * e.g. "10,-5,minecraft:overworld"
 */
public final class ChunkKey {

    public final int chunkX;
    public final int chunkZ;
    public final String dimension;

    public ChunkKey(int chunkX, int chunkZ, String dimension) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
    }

    /**
     * Derives a ChunkKey from the chunk the player is currently standing in.
     */
    public static ChunkKey fromPlayer(ServerPlayerEntity player) {
        int cx = player.getBlockPos().getX() >> 4;
        int cz = player.getBlockPos().getZ() >> 4;
        String dim = player.getWorld().getRegistryKey().getValue().toString();
        return new ChunkKey(cx, cz, dim);
    }

    /**
     * Parses a ChunkKey from its string encoding "chunkX,chunkZ,dimension".
     * Returns null if the string is malformed.
     */
    public static ChunkKey fromString(String s) {
        if (s == null) return null;
        int firstComma = s.indexOf(',');
        if (firstComma < 0) return null;
        int secondComma = s.indexOf(',', firstComma + 1);
        if (secondComma < 0) return null;
        try {
            int cx = Integer.parseInt(s.substring(0, firstComma));
            int cz = Integer.parseInt(s.substring(firstComma + 1, secondComma));
            String dim = s.substring(secondComma + 1);
            return new ChunkKey(cx, cz, dim);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return chunkX + "," + chunkZ + "," + dimension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey other)) return false;
        return chunkX == other.chunkX && chunkZ == other.chunkZ && Objects.equals(dimension, other.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkZ, dimension);
    }
}
