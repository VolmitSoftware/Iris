package art.arcane.iris.engine.framework.nativefit;

import java.util.List;

/** A piece of a start, as the platform code reads it, in world heights. */
public interface NativeFitPiece<S> {
    NativeFitBox box();

    /** {@code FIXED}, {@code RIGID} or {@code TERRAIN_MATCHING}, the names of the API's {@code PieceKind}. */
    String kind();

    int groundLevelDelta();

    /** Each jigsaw junction's source X, ground height and Z. */
    List<int[]> junctions();

    NativeTemplateCells<S> cells(NativeFitBox region);

    void columns(ColumnVisitor visitor);

    /** The positions ({@code {x, y, z}}) of the legacy template air inside {@code clip}. */
    List<int[]> legacyAir(NativeFitBox clip);

    /** Receives one vertical span per column, both heights inclusive world heights. */
    @FunctionalInterface
    interface ColumnVisitor {
        void column(int x, int z, int minY, int maxY);
    }
}
