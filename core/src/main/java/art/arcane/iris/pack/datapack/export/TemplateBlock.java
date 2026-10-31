package art.arcane.iris.pack.datapack.export;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;

import java.util.Objects;

public record TemplateBlock(int x, int y, int z, VanillaBlockState state, CompoundTag nbt) {
    public TemplateBlock {
        Objects.requireNonNull(state, "Template block state");
    }
}
