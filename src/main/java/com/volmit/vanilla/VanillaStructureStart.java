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
 * <p>{@link #identity()} names what placed the start, stable for one pack: the host records it with every start it
 * publishes, so a start found in the world later can be traced back to its placement.
 */
public final class VanillaStructureStart {
    private final ResourceId structure;
    private final int chunkX;
    private final int chunkZ;
    private final int y;
    private final boolean underground;
    private final boolean replacesPlatformStart;
    private final long identity;
    private final JigsawOverrides jigsaw;
    private final VanillaStructureFit fit;

    private VanillaStructureStart(ResourceId structure, int chunkX, int chunkZ, int y, boolean underground,
                                  boolean replacesPlatformStart, long identity, JigsawOverrides jigsaw,
                                  VanillaStructureFit fit) {
        this.structure = Objects.requireNonNull(structure, "structure");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.y = y;
        this.underground = underground;
        this.replacesPlatformStart = replacesPlatformStart;
        this.identity = identity;
        this.jigsaw = jigsaw;
        this.fit = Objects.requireNonNull(fit, "fit");
    }

    /** A surface start of {@code structure} in chunk {@code chunkX, chunkZ}, planned at height {@code y}, with the
     * structure's own terrain fitting, its registered definition and identity 0.
     */
    public static VanillaStructureStart at(ResourceId structure, int chunkX, int chunkZ, int y) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, false, false, 0L, null, VanillaStructureFit.SOURCE);
    }

    /** This start with the structure's lowest block at {@link #y()} instead of on the surface. */
    public VanillaStructureStart underground() {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, true, replacesPlatformStart, identity, jigsaw, fit);
    }

    /** This start, taking the chunk even where the platform already started the structure there. */
    public VanillaStructureStart replacingPlatformStart() {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, true, identity, jigsaw, fit);
    }

    public VanillaStructureStart withIdentity(long identity) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, replacesPlatformStart, identity, jigsaw, fit);
    }

    /** This start, assembled with {@code overrides} applied to a jigsaw structure's definition. Only jigsaw structures
     * take overrides: the host fails the start of any other structure that has some.
     */
    public VanillaStructureStart withJigsaw(JigsawOverrides overrides) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, replacesPlatformStart, identity,
                Objects.requireNonNull(overrides, "overrides"), fit);
    }

    public VanillaStructureStart withFit(VanillaStructureFit fit) {
        return new VanillaStructureStart(structure, chunkX, chunkZ, y, underground, replacesPlatformStart, identity, jigsaw, fit);
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

    public long identity() {
        return identity;
    }

    /** Changes to the jigsaw definition; empty assembles the registered definition as it is. */
    public Optional<JigsawOverrides> jigsaw() {
        return Optional.ofNullable(jigsaw);
    }

    public VanillaStructureFit fit() {
        return fit;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VanillaStructureStart that && structure.equals(that.structure) && chunkX == that.chunkX
                && chunkZ == that.chunkZ && y == that.y && underground == that.underground
                && replacesPlatformStart == that.replacesPlatformStart && identity == that.identity
                && Objects.equals(jigsaw, that.jigsaw) && fit.equals(that.fit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(structure, chunkX, chunkZ, y, underground, replacesPlatformStart, identity, jigsaw, fit);
    }

    @Override
    public String toString() {
        return "VanillaStructureStart[" + structure + " at chunk " + chunkX + "," + chunkZ + ", y " + y
                + (underground ? ", underground" : "") + (replacesPlatformStart ? ", replacing" : "") + "]";
    }
}
