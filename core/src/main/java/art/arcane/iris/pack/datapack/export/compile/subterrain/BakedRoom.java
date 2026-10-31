package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;

import java.util.Objects;

public record BakedRoom(int originX, int originY, int originZ, TemplateVolume core, TemplateVolume shell,
                        int anchorX, int anchorY, int anchorZ, VanillaBlockState anchorState) {
    public BakedRoom {
        Objects.requireNonNull(core, "Room core volume");
        Objects.requireNonNull(shell, "Room shell volume");
        Objects.requireNonNull(anchorState, "Room anchor state");
        if (core.sizeX() != shell.sizeX() || core.sizeY() != shell.sizeY() || core.sizeZ() != shell.sizeZ()) {
            throw new IllegalArgumentException("Room core and shell volumes differ in size");
        }
        if (anchorX < 0 || anchorY < 0 || anchorZ < 0 || anchorX >= core.sizeX() || anchorY >= core.sizeY() || anchorZ >= core.sizeZ()) {
            throw new IllegalArgumentException("Room anchor " + anchorX + "," + anchorY + "," + anchorZ + " lies outside the room");
        }
    }

    public int reach() {
        return Math.max(Math.max(anchorX, core.sizeX() - 1 - anchorX), Math.max(anchorZ, core.sizeZ() - 1 - anchorZ));
    }
}
