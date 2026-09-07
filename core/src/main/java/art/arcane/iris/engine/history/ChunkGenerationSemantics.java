package art.arcane.iris.engine.history;

import art.arcane.iris.engine.hydrology.HydrologyFeatureType;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ChunkGenerationSemantics {
    static final int MAX_KEY_BYTES = 1_024;
    static final int MAX_KEYS_PER_KIND = 2_048;
    static final int MAX_RIVER_FEATURES = 16_384;
    static final int MAX_STRUCTURES = 4_096;

    private static final Comparator<StructureOccurrence> STRUCTURE_COMPARATOR = Comparator
            .comparing(StructureOccurrence::key)
            .thenComparingInt(occurrence -> occurrence.position().x())
            .thenComparingInt(occurrence -> occurrence.position().z())
            .thenComparingInt(occurrence -> occurrence.position().y());
    private static final Comparator<PointOfInterest> POI_COMPARATOR = Comparator.comparing(PointOfInterest::key)
            .thenComparingInt(point -> point.position().x())
            .thenComparingInt(point -> point.position().z())
            .thenComparingInt(point -> point.position().y());
    private static final Comparator<RiverFeatureOccurrence> RIVER_FEATURE_COMPARATOR = Comparator
            .comparing(RiverFeatureOccurrence::profileKey)
            .thenComparing(RiverFeatureOccurrence::type)
            .thenComparingInt(occurrence -> occurrence.position().x())
            .thenComparingInt(occurrence -> occurrence.position().z())
            .thenComparingInt(occurrence -> occurrence.position().y())
            .thenComparingLong(RiverFeatureOccurrence::featureId);

    private final int chunkX;
    private final int chunkZ;
    private final long activationId;
    private final boolean sealed;
    private final Set<String> surfaceBiomeKeys;
    private final Set<String> caveBiomeKeys;
    private final Set<String> regionKeys;
    private final Set<String> riverProfileKeys;
    private final Set<String> objectKeys;
    private final Set<RiverFeatureOccurrence> riverFeatures;
    private final Set<StructureOccurrence> structures;
    private final Set<PointOfInterest> pointsOfInterest;

    private ChunkGenerationSemantics(Builder builder) {
        chunkX = builder.chunkX;
        chunkZ = builder.chunkZ;
        activationId = builder.activationId;
        sealed = builder.sealed;
        surfaceBiomeKeys = immutableKeys(builder.surfaceBiomeKeys, "surface biome");
        caveBiomeKeys = immutableKeys(builder.caveBiomeKeys, "cave biome");
        regionKeys = immutableKeys(builder.regionKeys, "region");
        riverProfileKeys = immutableKeys(builder.riverProfileKeys, "river profile");
        objectKeys = immutableKeys(builder.objectKeys, "object");
        riverFeatures = immutableRiverFeatures(builder.riverFeatures);
        structures = immutableStructures(builder.structures);
        pointsOfInterest = immutablePoints(builder.pointsOfInterest);
    }

    public static Builder builder(int chunkX, int chunkZ, long activationId) {
        return new Builder(chunkX, chunkZ, activationId);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long activationId() {
        return activationId;
    }

    public boolean sealed() {
        return sealed;
    }

    public Set<String> surfaceBiomeKeys() {
        return surfaceBiomeKeys;
    }

    public Set<String> caveBiomeKeys() {
        return caveBiomeKeys;
    }

    public Set<String> regionKeys() {
        return regionKeys;
    }

    public Set<String> riverProfileKeys() {
        return riverProfileKeys;
    }

    public Set<String> objectKeys() {
        return objectKeys;
    }

    public Set<RiverFeatureOccurrence> riverFeatures() {
        return riverFeatures;
    }

    public Set<PointOfInterest> pointsOfInterest() {
        return pointsOfInterest;
    }

    public Set<StructureOccurrence> structures() {
        return structures;
    }

    public ChunkGenerationSemantics merge(ChunkGenerationSemantics update) {
        ChunkGenerationSemantics requiredUpdate = Objects.requireNonNull(update, "update");
        if (chunkX != requiredUpdate.chunkX || chunkZ != requiredUpdate.chunkZ) {
            throw new IllegalArgumentException(
                    "Generation semantic updates must address the same chunk: "
                            + chunkX + "," + chunkZ + " and "
                            + requiredUpdate.chunkX + "," + requiredUpdate.chunkZ
            );
        }
        if (activationId != requiredUpdate.activationId) {
            throw new IllegalStateException(
                    "Chunk " + chunkX + "," + chunkZ
                            + " belongs to generation activation " + activationId
                            + ", not " + requiredUpdate.activationId
            );
        }

        boolean addsFacts = !surfaceBiomeKeys.containsAll(requiredUpdate.surfaceBiomeKeys)
                || !caveBiomeKeys.containsAll(requiredUpdate.caveBiomeKeys)
                || !regionKeys.containsAll(requiredUpdate.regionKeys)
                || !riverProfileKeys.containsAll(requiredUpdate.riverProfileKeys)
                || !objectKeys.containsAll(requiredUpdate.objectKeys)
                || !riverFeatures.containsAll(requiredUpdate.riverFeatures)
                || !structures.containsAll(requiredUpdate.structures)
                || !pointsOfInterest.containsAll(requiredUpdate.pointsOfInterest);
        if (sealed && addsFacts) {
            throw new IllegalStateException(
                    "Chunk " + chunkX + "," + chunkZ + " has sealed generation semantics"
            );
        }
        if (!addsFacts && (sealed || !requiredUpdate.sealed)) {
            return this;
        }

        Builder merged = builder(chunkX, chunkZ, activationId)
                .addSurfaceBiomes(surfaceBiomeKeys)
                .addSurfaceBiomes(requiredUpdate.surfaceBiomeKeys)
                .addCaveBiomes(caveBiomeKeys)
                .addCaveBiomes(requiredUpdate.caveBiomeKeys)
                .addRegions(regionKeys)
                .addRegions(requiredUpdate.regionKeys)
                .addRiverProfiles(riverProfileKeys)
                .addRiverProfiles(requiredUpdate.riverProfileKeys)
                .addObjects(objectKeys)
                .addObjects(requiredUpdate.objectKeys)
                .addRiverFeatures(riverFeatures)
                .addRiverFeatures(requiredUpdate.riverFeatures)
                .addStructures(structures)
                .addStructures(requiredUpdate.structures)
                .addPointsOfInterest(pointsOfInterest)
                .addPointsOfInterest(requiredUpdate.pointsOfInterest);
        if (sealed || requiredUpdate.sealed) {
            merged.seal();
        }
        return merged.build();
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof ChunkGenerationSemantics semantics)) {
            return false;
        }
        return chunkX == semantics.chunkX
                && chunkZ == semantics.chunkZ
                && activationId == semantics.activationId
                && sealed == semantics.sealed
                && surfaceBiomeKeys.equals(semantics.surfaceBiomeKeys)
                && caveBiomeKeys.equals(semantics.caveBiomeKeys)
                && regionKeys.equals(semantics.regionKeys)
                && riverProfileKeys.equals(semantics.riverProfileKeys)
                && objectKeys.equals(semantics.objectKeys)
                && riverFeatures.equals(semantics.riverFeatures)
                && structures.equals(semantics.structures)
                && pointsOfInterest.equals(semantics.pointsOfInterest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                chunkX,
                chunkZ,
                activationId,
                sealed,
                surfaceBiomeKeys,
                caveBiomeKeys,
                regionKeys,
                riverProfileKeys,
                objectKeys,
                riverFeatures,
                structures,
                pointsOfInterest
        );
    }

    @Override
    public String toString() {
        return "ChunkGenerationSemantics[chunkX=" + chunkX
                + ", chunkZ=" + chunkZ
                + ", activationId=" + activationId
                + ", sealed=" + sealed
                + ", surfaceBiomeKeys=" + surfaceBiomeKeys
                + ", caveBiomeKeys=" + caveBiomeKeys
                + ", regionKeys=" + regionKeys
                + ", riverProfileKeys=" + riverProfileKeys
                + ", objectKeys=" + objectKeys
                + ", riverFeatures=" + riverFeatures
                + ", structures=" + structures
                + "]";
    }

    static Comparator<StructureOccurrence> structureComparator() {
        return STRUCTURE_COMPARATOR;
    }

    static Comparator<RiverFeatureOccurrence> riverFeatureComparator() {
        return RIVER_FEATURE_COMPARATOR;
    }

    static String requireResourceKey(String key) {
        String requiredKey = Objects.requireNonNull(key, "key");
        if (requiredKey.isBlank()) {
            throw new IllegalArgumentException("Generation semantic resource keys must not be blank");
        }
        if (!requiredKey.equals(requiredKey.trim())) {
            throw new IllegalArgumentException("Generation semantic resource keys must not contain surrounding whitespace: " + requiredKey);
        }
        for (int index = 0; index < requiredKey.length(); index++) {
            char character = requiredKey.charAt(index);
            if (character == '\\' || Character.isISOControl(character)) {
                throw new IllegalArgumentException("Generation semantic resource key contains an invalid character");
            }
        }
        if (requiredKey.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_BYTES) {
            throw new IllegalArgumentException("Generation semantic resource key is too long");
        }
        return requiredKey;
    }

    private static Set<String> immutableKeys(Collection<String> keys, String kind) {
        if (keys.size() > MAX_KEYS_PER_KIND) {
            throw new IllegalArgumentException(
                    "A chunk cannot contain more than " + MAX_KEYS_PER_KIND + " " + kind + " keys"
            );
        }
        TreeSet<String> validated = new TreeSet<>();
        for (String key : keys) {
            validated.add(requireResourceKey(key));
        }
        return Collections.unmodifiableSet(validated);
    }

    private static Set<StructureOccurrence> immutableStructures(Collection<StructureOccurrence> structures) {
        if (structures.size() > MAX_STRUCTURES) {
            throw new IllegalArgumentException(
                    "A chunk cannot contain more than " + MAX_STRUCTURES + " structure occurrences"
            );
        }
        TreeSet<StructureOccurrence> validated = new TreeSet<>(STRUCTURE_COMPARATOR);
        for (StructureOccurrence occurrence : structures) {
            validated.add(Objects.requireNonNull(occurrence, "structure occurrence"));
        }
        return Collections.unmodifiableSet(validated);
    }

    private static Set<RiverFeatureOccurrence> immutableRiverFeatures(
            Collection<RiverFeatureOccurrence> occurrences
    ) {
        if (occurrences.size() > MAX_RIVER_FEATURES) {
            throw new IllegalArgumentException(
                    "A chunk cannot contain more than " + MAX_RIVER_FEATURES + " river feature occurrences"
            );
        }
        TreeSet<RiverFeatureOccurrence> validated = new TreeSet<>(RIVER_FEATURE_COMPARATOR);
        for (RiverFeatureOccurrence occurrence : occurrences) {
            validated.add(Objects.requireNonNull(occurrence, "river feature occurrence"));
        }
        return Collections.unmodifiableSet(validated);
    }

    private static Set<PointOfInterest> immutablePoints(Collection<PointOfInterest> points) {
        if (points.size() > MAX_STRUCTURES) {
            throw new IllegalArgumentException("A chunk contains too many points of interest");
        }
        TreeSet<PointOfInterest> copy = new TreeSet<>(POI_COMPARATOR);
        for (PointOfInterest point : points) {
            copy.add(Objects.requireNonNull(point, "point of interest"));
        }
        return Collections.unmodifiableSet(copy);
    }

    static Comparator<PointOfInterest> pointComparator() {
        return POI_COMPARATOR;
    }

    public record PointOfInterest(String key, BlockPosition position) {
        public PointOfInterest {
            key = requireResourceKey(key);
            position = Objects.requireNonNull(position, "position");
        }
    }

    public record BlockPosition(int x, int y, int z) {
    }

    public record StructureOccurrence(String key, BlockPosition position) {
        public StructureOccurrence {
            key = requireResourceKey(key);
            position = Objects.requireNonNull(position, "position");
        }
    }

    public record RiverFeatureOccurrence(
            String profileKey,
            HydrologyFeatureType type,
            long featureId,
            BlockPosition position
    ) {
        public RiverFeatureOccurrence {
            profileKey = requireResourceKey(profileKey);
            type = Objects.requireNonNull(type, "type");
            position = Objects.requireNonNull(position, "position");
        }
    }

    public static final class Builder {
        private final int chunkX;
        private final int chunkZ;
        private final long activationId;
        private final Set<String> surfaceBiomeKeys;
        private final Set<String> caveBiomeKeys;
        private final Set<String> regionKeys;
        private final Set<String> riverProfileKeys;
        private final Set<String> objectKeys;
        private final Set<RiverFeatureOccurrence> riverFeatures;
        private final Set<StructureOccurrence> structures;
        private final Set<PointOfInterest> pointsOfInterest;
        private boolean sealed;

        private Builder(int chunkX, int chunkZ, long activationId) {
            if (activationId <= 0L) {
                throw new IllegalArgumentException("Generation activation IDs must be positive: " + activationId);
            }
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.activationId = activationId;
            surfaceBiomeKeys = new HashSet<>();
            caveBiomeKeys = new HashSet<>();
            regionKeys = new HashSet<>();
            riverProfileKeys = new HashSet<>();
            objectKeys = new HashSet<>();
            riverFeatures = new TreeSet<>(RIVER_FEATURE_COMPARATOR);
            structures = new TreeSet<>(STRUCTURE_COMPARATOR);
            pointsOfInterest = new TreeSet<>(POI_COMPARATOR);
        }

        public Builder addSurfaceBiome(String key) {
            addKey(surfaceBiomeKeys, key, "surface biome");
            return this;
        }

        public Builder addSurfaceBiomes(Collection<String> keys) {
            addKeys(surfaceBiomeKeys, keys, "surface biome");
            return this;
        }

        public Builder addCaveBiome(String key) {
            addKey(caveBiomeKeys, key, "cave biome");
            return this;
        }

        public Builder addCaveBiomes(Collection<String> keys) {
            addKeys(caveBiomeKeys, keys, "cave biome");
            return this;
        }

        public Builder addRegion(String key) {
            addKey(regionKeys, key, "region");
            return this;
        }

        public Builder addRegions(Collection<String> keys) {
            addKeys(regionKeys, keys, "region");
            return this;
        }

        public Builder addRiverProfile(String key) {
            addKey(riverProfileKeys, key, "river profile");
            return this;
        }

        public Builder addRiverProfiles(Collection<String> keys) {
            addKeys(riverProfileKeys, keys, "river profile");
            return this;
        }

        public Builder addRiverFeature(
                String profileKey,
                HydrologyFeatureType type,
                long featureId,
                int blockX,
                int blockY,
                int blockZ
        ) {
            return addRiverFeature(new RiverFeatureOccurrence(
                    profileKey,
                    type,
                    featureId,
                    new BlockPosition(blockX, blockY, blockZ)
            ));
        }

        public Builder addRiverFeature(RiverFeatureOccurrence occurrence) {
            RiverFeatureOccurrence required = Objects.requireNonNull(
                    occurrence,
                    "river feature occurrence"
            );
            if (riverFeatures.size() >= MAX_RIVER_FEATURES && !riverFeatures.contains(required)) {
                throw new IllegalArgumentException(
                        "A chunk cannot contain more than " + MAX_RIVER_FEATURES
                                + " river feature occurrences"
                );
            }
            riverFeatures.add(required);
            addRiverProfile(required.profileKey());
            return this;
        }

        public Builder addRiverFeatures(Collection<RiverFeatureOccurrence> occurrences) {
            Collection<RiverFeatureOccurrence> requiredOccurrences = Objects.requireNonNull(
                    occurrences,
                    "river feature occurrences"
            );
            for (RiverFeatureOccurrence occurrence : requiredOccurrences) {
                addRiverFeature(occurrence);
            }
            return this;
        }

        public Builder addObject(String key) {
            addKey(objectKeys, key, "object");
            return this;
        }

        public Builder addObjects(Collection<String> keys) {
            addKeys(objectKeys, keys, "object");
            return this;
        }

        public Builder addStructure(String key, int blockX, int blockY, int blockZ) {
            return addStructure(new StructureOccurrence(key, new BlockPosition(blockX, blockY, blockZ)));
        }

        public Builder addStructure(StructureOccurrence occurrence) {
            if (structures.size() >= MAX_STRUCTURES && !structures.contains(occurrence)) {
                throw new IllegalArgumentException(
                        "A chunk cannot contain more than " + MAX_STRUCTURES + " structure occurrences"
                );
            }
            structures.add(Objects.requireNonNull(occurrence, "structure occurrence"));
            return this;
        }

        public Builder addStructures(Collection<StructureOccurrence> occurrences) {
            Collection<StructureOccurrence> requiredOccurrences = Objects.requireNonNull(occurrences, "occurrences");
            for (StructureOccurrence occurrence : requiredOccurrences) {
                addStructure(occurrence);
            }
            return this;
        }

        public Builder addPointOfInterest(PointOfInterest point) {
            Objects.requireNonNull(point, "point of interest");
            if (pointsOfInterest.size() >= MAX_STRUCTURES && !pointsOfInterest.contains(point)) {
                throw new IllegalArgumentException("A chunk contains too many points of interest");
            }
            pointsOfInterest.add(point);
            return this;
        }

        public Builder addPointsOfInterest(Collection<PointOfInterest> points) {
            for (PointOfInterest point : points) {
                addPointOfInterest(point);
            }
            return this;
        }

        public Builder seal() {
            sealed = true;
            return this;
        }

        public ChunkGenerationSemantics build() {
            return new ChunkGenerationSemantics(this);
        }

        private static void addKeys(Set<String> target, Collection<String> keys, String kind) {
            Collection<String> requiredKeys = Objects.requireNonNull(keys, "keys");
            for (String key : requiredKeys) {
                addKey(target, key, kind);
            }
        }

        private static void addKey(Set<String> target, String key, String kind) {
            if (target.contains(Objects.requireNonNull(key, "key"))) {
                return;
            }
            String validated = requireResourceKey(key);
            if (target.size() >= MAX_KEYS_PER_KIND) {
                throw new IllegalArgumentException(
                        "A chunk cannot contain more than " + MAX_KEYS_PER_KIND + " " + kind + " keys"
                );
            }
            target.add(validated);
        }
    }
}
