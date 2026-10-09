package com.volmit.vanilla;

import java.util.Objects;
import java.util.Optional;

/** How a platform structure sits in the terrain: the fitting the host does around it before its pieces are placed,
 * and the foundation columns it builds under them afterwards. Without a terrain fitting the host replays the
 * structure's own ({@link StructureTerrain.Mode#SOURCE}); without stilts it builds none.
 */
public final class VanillaStructureFit {
    /** The structure's own terrain adaptation and no stilts. */
    public static final VanillaStructureFit SOURCE = new VanillaStructureFit(null, null);

    private final StructureTerrain terrain;
    private final StructureStilts stilts;

    private VanillaStructureFit(StructureTerrain terrain, StructureStilts stilts) {
        this.terrain = terrain;
        this.stilts = stilts;
    }

    public VanillaStructureFit withTerrain(StructureTerrain terrain) {
        return new VanillaStructureFit(Objects.requireNonNull(terrain, "terrain"), stilts);
    }

    public VanillaStructureFit withStilts(StructureStilts stilts) {
        return new VanillaStructureFit(terrain, Objects.requireNonNull(stilts, "stilts"));
    }

    public Optional<StructureTerrain> terrain() {
        return Optional.ofNullable(terrain);
    }

    public Optional<StructureStilts> stilts() {
        return Optional.ofNullable(stilts);
    }

    /** This fit with each part {@code later} states replaced by {@code later}'s. */
    public VanillaStructureFit then(VanillaStructureFit later) {
        return new VanillaStructureFit(later.terrain().orElse(terrain), later.stilts().orElse(stilts));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VanillaStructureFit that && Objects.equals(terrain, that.terrain)
                && Objects.equals(stilts, that.stilts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(terrain, stilts);
    }

    @Override
    public String toString() {
        return "VanillaStructureFit[" + terrain().map(Object::toString).orElse("source terrain") + ", "
                + stilts().map(Object::toString).orElse("no stilts") + "]";
    }
}
