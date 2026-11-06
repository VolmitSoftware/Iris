package com.volmit.tooling;

import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/**
 * A studio view of the block patterns a module lays over the world, such as a biome's palettes: a catalogue of
 * entries, each drawn from above, one block per column, at a depth below its top.
 */
public non-sealed interface PatternView extends StudioView {
    /** The view's entries in the world's pack, in the order the studio lists them. */
    List<PatternEntry> entries(PackWorld world);

    /**
     * The blocks the entry named {@code key} places {@code depth} blocks below its top across {@code area}, as the world
     * places them; empty when the pack has no such entry.
     *
     * @param depth from 0, the top block, to the entry's {@link PatternEntry#depths()}, exclusive
     */
    Optional<BlockPattern> pattern(PackWorld world, String key, int depth, PatternArea area);
}
