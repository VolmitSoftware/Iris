package art.arcane.iris.integration;

import org.bukkit.World;

public record IrisPapiPosition(World world, int blockX, int blockZ, long publishedAtMs) {
    public boolean sameColumn(World other, int otherBlockX, int otherBlockZ) {
        return world == other && blockX == otherBlockX && blockZ == otherBlockZ;
    }
}
