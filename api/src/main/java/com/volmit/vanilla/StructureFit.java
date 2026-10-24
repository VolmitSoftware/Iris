package com.volmit.vanilla;

import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockAccess;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The starts the platform places in one chunk while it decorates it, and the chunk's blocks, for modules that fit
 * those starts into the terrain ({@link com.volmit.service.VanillaStructures#beforePieces},
 * {@link com.volmit.service.VanillaStructures#afterPieces}). Heights are above the dimension's bottom (0 = bottom) unless
 * a method says otherwise. Each provider gets its own fit of the chunk, over the same blocks and scoped to its module, which
 * is valid only while the providers run, on one thread.
 */
public interface StructureFit {
    PackWorld world();

    /** The log of the module this fit was handed to; the same as {@code world().log()}, which a host overrides where a fit has no world. */
    default ModuleLog log() {
        return world().log();
    }

    /** The seed of the platform's world, which the platform's own structure randomness derives from. */
    long seed();

    /** The blocks the platform decorates now: the chunk's columns, from one above its bottom to one below its top.
     * {@link #blocks()} may read the whole chunk column but a fit only writes here.
     */
    BlockBox area();

    /** The world height of the dimension's bottom, for rules written in world heights: a band of world heights, the
     * platform's deepslate below 0, noise sampled at world heights.
     */
    int bottomY();

    /** The chunk column's live blocks. */
    BlockAccess blocks();

    /** The height of the terrain's surface the host generated at a column: its highest terrain block, fluids ignored. */
    int surfaceY(int x, int z);

    /** The block the platform's natural terrain of this dimension is made of at height {@code y}: stone or deepslate in
     * an overworld, netherrack in a nether, end stone in an end.
     */
    BlockState naturalBlock(int y);

    /** Every start the platform places in the chunk now, in the order it places them. */
    List<Start> starts();

    /** The fit's one {@code type} instance, created by {@code create} the first time a provider asks for it: what a
     * provider works out before the pieces are placed and needs again after them. Use a class of the module as
     * {@code type}. Gone once the chunk is done.
     */
    <T> T state(Class<T> type, Supplier<? extends T> create);

    /** A start the platform places in the chunk. */
    interface Start {
        ResourceId structure();

        /** The chunk the start belongs to, where the platform started it. */
        int chunkX();

        int chunkZ();

        DecorationStep step();

        /** The terrain adaptation the platform's definition of the structure asks for. */
        TerrainAdaptation adaptation();

        /** The memo the module that placed the start gave it ({@link VanillaStructureStart#withMemo}); empty for the
         * platform's own starts.
         */
        Optional<StartMemo> memo();

        /** Every piece of the start, in the platform's order: also the pieces outside the chunk. */
        List<Piece> pieces();

        /** The key that stays the same for this start for as long as the platform keeps it, across chunks: for caches
         * about the start, held weakly so the start can be forgotten.
         */
        StartKey key();
    }

    /** A piece of a start. */
    interface Piece {
        /** The piece's bounds, minima inclusive and maxima exclusive like every {@link BlockBox}. */
        BlockBox box();

        PieceKind kind();

        /** How far above the piece's lowest block its ground level lies; 0 for a {@link PieceKind#FIXED} piece. */
        int groundLevelDelta();

        /** Where the jigsaw connections of this piece meet the pieces they lead to: the X and Z of the connection and the
         * ground height at its source. Empty for a {@link PieceKind#FIXED} piece.
         */
        List<BlockPosition> junctions();

        /** The piece's template blocks inside {@code region}, processed as the platform would place them now, against
         * the blocks the chunk holds at this moment. Asked again, the host reads the template again.
         */
        TemplateCells cells(BlockBox region);

        /** The piece's shape as vertical spans, one per column it occupies: from the lowest to the highest block of its
         * templates that is neither air nor structure void, or its whole box when the host cannot read a template.
         */
        void columns(ColumnVisitor visitor);

        /** The air blocks of the piece's legacy templates inside {@code clip}, the ones the platform's own legacy
         * placement would leave as air over existing terrain. Empty when the piece has no legacy template.
         */
        List<BlockPosition> legacyAir(BlockBox clip);
    }

    /** Receives one vertical span of a piece per column, both heights inclusive. */
    @FunctionalInterface
    interface ColumnVisitor {
        void column(int x, int z, int minY, int maxY);
    }
}
