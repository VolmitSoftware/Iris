package com.volmit.tooling;

import com.volmit.geometry.BlockPosition;
import java.util.List;
import java.util.Objects;

/**
 * A structure template the platform loads, such as a vanilla {@code .nbt}: its size and the blocks of its first palette.
 *
 * @param size         the template's width, height and depth in blocks
 * @param paletteCount how many palettes the template has; only the first is in {@code blocks}
 * @param entityCount  how many entities the template holds, which tools do not read
 * @param blocks       the blocks of the first palette, with positions from the template's corner
 */
public record StructureTemplate(BlockPosition size, int paletteCount, int entityCount, List<TemplateBlock> blocks) {
    public StructureTemplate {
        Objects.requireNonNull(size, "size");
        blocks = List.copyOf(blocks);
    }
}
