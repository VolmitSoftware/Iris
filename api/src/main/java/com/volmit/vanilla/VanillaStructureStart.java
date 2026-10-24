package com.volmit.vanilla;

import com.volmit.resource.ResourceId;
import java.util.Objects;
import java.util.Optional;

/** One start of a platform structure a module places: the platform generates the structure, such as
 * {@code minecraft:ancient_city}, from the chunk the module chose instead of one it would pick itself.
 *
 * <p>A surface start sits on the terrain the platform finds at the chunk; {@link #y()} records the height the module
 * planned with. An {@link #underground() underground} start puts the structure's lowest block at {@link #y()}.
 * Heights are above the dimension's bottom (0 = bottom). Platform starts of the same structure that already exist in
 * the chunk win unless the start {@link #replacesPlatformStart() replaces} them or the structure is
 * {@link VanillaStructureRule#REPLACE replaced} by a module.
 *
 * <p>The {@link #memo() memo} is kept with the start and handed back whenever the host asks the module about the start
 * again, so a start that spans many chunks keeps what the module decided for it when it was placed, even after the pack
 * changed.
 */
public final class VanillaStructureStart {
    private final ResourceId structure;
    private final int chunkX;
    private final int chunkZ;
    private final int y;
    private final boolean underground;
    private final boolean replacesPlatformStart;
    private final JigsawOverrides jigsaw;
    private final StartMemo memo;

    private VanillaStructureStart(ResourceId structure, int chunkX, int chunkZ, int y, boolean underground,
                                  boolean replacesPlatformStart, JigsawOverrides jigsaw, StartMemo memo) {
        this.structure = Objects.requireNonNull(structure, "structure");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.y = y;
        this.underground = underground;
        this.replacesPlatformStart = replacesPlatformStart;
        this.jigsaw = jigsaw;
        this.memo = memo;
    }

    /** A surface start of {@code structure} in chunk {@code chunkX, chunkZ}, planned at height {@code y}, with its
     * registered definition and no memo.
     */
    public static VanillaStructureStart at(ResourceId structure, int chunkX, int chunkZ, int y) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, false, false, null, null);
    }

    /** This start with the structure's lowest block at {@link #y()} instead of on the surface. */
    public VanillaStructureStart underground() {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, true, replacesPlatformStart, jigsaw, memo);
    }

    /** This start, taking the chunk even where the platform already started the structure there. */
    public VanillaStructureStart replacingPlatformStart() {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, true, jigsaw, memo);
    }

    /** This start, assembled with {@code overrides} applied to a jigsaw structure's definition. Only jigsaw structures
     * take overrides: the host fails the start of any other structure that has some.
     */
    public VanillaStructureStart withJigsaw(JigsawOverrides overrides) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, replacesPlatformStart,
                Objects.requireNonNull(overrides, "overrides"), memo);
    }

    /** This start with {@code memo}, which the host keeps with it and hands back in every later question about it. */
    public VanillaStructureStart withMemo(StartMemo memo) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, replacesPlatformStart, jigsaw,
                Objects.requireNonNull(memo, "memo"));
    }

    public ResourceId structure() {
        return structure;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** Height above the dimension's bottom: the structure's lowest block underground, the planned surface otherwise. */
    public int y() {
        return y;
    }

    public boolean isUnderground() {
        return underground;
    }

    public boolean replacesPlatformStart() {
        return replacesPlatformStart;
    }

    /** Changes to the jigsaw definition; empty assembles the registered definition as it is. */
    public Optional<JigsawOverrides> jigsaw() {
        return Optional.ofNullable(jigsaw);
    }

    /** What the module noted about the start for itself; empty when it noted nothing. */
    public Optional<StartMemo> memo() {
        return Optional.ofNullable(memo);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VanillaStructureStart that && structure.equals(that.structure) && chunkX == that.chunkX
                && chunkZ == that.chunkZ && y == that.y && underground == that.underground
                && replacesPlatformStart == that.replacesPlatformStart
                && Objects.equals(jigsaw, that.jigsaw) && Objects.equals(memo, that.memo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(structure, chunkX, chunkZ, y, underground, replacesPlatformStart, jigsaw, memo);
    }

    @Override
    public String toString() {
        return "VanillaStructureStart[" + structure + " at chunk " + chunkX + "," + chunkZ + ", y " + y
                + (underground ? ", underground" : "") + (replacesPlatformStart ? ", replacing" : "") + "]";
    }
}
