package art.arcane.iris.pack.datapack.export;

import java.util.List;

public record TemplateVolume(int sizeX, int sizeY, int sizeZ, List<TemplateBlock> blocks) {
    public TemplateVolume {
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            throw new IllegalArgumentException("Template size must not be negative, got " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        blocks = List.copyOf(blocks);
        for (TemplateBlock block : blocks) {
            if (block.x() < 0 || block.y() < 0 || block.z() < 0 || block.x() >= sizeX || block.y() >= sizeY || block.z() >= sizeZ) {
                throw new IllegalArgumentException("Template block at " + block.x() + "," + block.y() + "," + block.z()
                        + " lies outside the " + sizeX + "x" + sizeY + "x" + sizeZ + " template");
            }
        }
    }

    public int footprint() {
        return Math.max(sizeX, sizeZ);
    }
}
