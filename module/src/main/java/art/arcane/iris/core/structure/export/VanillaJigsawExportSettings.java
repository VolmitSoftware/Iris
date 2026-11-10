package art.arcane.iris.core.structure.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VanillaJigsawExportSettings {
    private final List<String> biomes;
    private final int startHeight;
    private final ProjectHeightmap projectHeightmap;
    private final GenerationStep generationStep;
    private final TerrainAdaptation terrainAdaptation;
    private final boolean expansionHack;
    private final int maxDistanceVertical;
    private final int spacing;
    private final int separation;
    private final int salt;
    private final float frequency;
    private final SpreadType spreadType;

    private VanillaJigsawExportSettings(Builder builder) {
        biomes = List.copyOf(builder.biomes);
        startHeight = builder.startHeight;
        projectHeightmap = builder.projectHeightmap;
        generationStep = builder.generationStep;
        terrainAdaptation = builder.terrainAdaptation;
        expansionHack = builder.expansionHack;
        maxDistanceVertical = builder.maxDistanceVertical;
        spacing = builder.spacing;
        separation = builder.separation;
        salt = builder.salt;
        frequency = builder.frequency;
        spreadType = builder.spreadType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VanillaJigsawExportSettings defaults() {
        return builder().build();
    }

    public List<String> biomes() {
        return biomes;
    }

    public int startHeight() {
        return startHeight;
    }

    public ProjectHeightmap projectHeightmap() {
        return projectHeightmap;
    }

    public GenerationStep generationStep() {
        return generationStep;
    }

    public TerrainAdaptation terrainAdaptation() {
        return terrainAdaptation;
    }

    public boolean expansionHack() {
        return expansionHack;
    }

    public int maxDistanceVertical() {
        return maxDistanceVertical;
    }

    public int spacing() {
        return spacing;
    }

    public int separation() {
        return separation;
    }

    public int salt() {
        return salt;
    }

    public float frequency() {
        return frequency;
    }

    public SpreadType spreadType() {
        return spreadType;
    }

    public enum ProjectHeightmap {
        NONE(null),
        WORLD_SURFACE_WG("WORLD_SURFACE_WG"),
        WORLD_SURFACE("WORLD_SURFACE"),
        OCEAN_FLOOR_WG("OCEAN_FLOOR_WG"),
        OCEAN_FLOOR("OCEAN_FLOOR"),
        MOTION_BLOCKING("MOTION_BLOCKING"),
        MOTION_BLOCKING_NO_LEAVES("MOTION_BLOCKING_NO_LEAVES");

        private final String serializedName;

        ProjectHeightmap(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum GenerationStep {
        RAW_GENERATION("raw_generation"),
        LAKES("lakes"),
        LOCAL_MODIFICATIONS("local_modifications"),
        UNDERGROUND_STRUCTURES("underground_structures"),
        SURFACE_STRUCTURES("surface_structures"),
        STRONGHOLDS("strongholds"),
        UNDERGROUND_ORES("underground_ores"),
        UNDERGROUND_DECORATION("underground_decoration"),
        FLUID_SPRINGS("fluid_springs"),
        VEGETAL_DECORATION("vegetal_decoration"),
        TOP_LAYER_MODIFICATION("top_layer_modification");

        private final String serializedName;

        GenerationStep(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum TerrainAdaptation {
        NONE("none"),
        BURY("bury"),
        BEARD_THIN("beard_thin"),
        BEARD_BOX("beard_box"),
        ENCAPSULATE("encapsulate");

        private final String serializedName;

        TerrainAdaptation(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum SpreadType {
        LINEAR("linear"),
        TRIANGULAR("triangular");

        private final String serializedName;

        SpreadType(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public static final class Builder {
        private final List<String> biomes = new ArrayList<>(List.of("minecraft:plains"));
        private int startHeight;
        private ProjectHeightmap projectHeightmap = ProjectHeightmap.WORLD_SURFACE_WG;
        private GenerationStep generationStep = GenerationStep.SURFACE_STRUCTURES;
        private TerrainAdaptation terrainAdaptation = TerrainAdaptation.NONE;
        private boolean expansionHack;
        private int maxDistanceVertical = 4064;
        private int spacing = 32;
        private int separation = 8;
        private int salt;
        private float frequency = 1.0F;
        private SpreadType spreadType = SpreadType.LINEAR;

        private Builder() {
        }

        public Builder biomes(List<String> values) {
            biomes.clear();
            biomes.addAll(Objects.requireNonNull(values));
            return this;
        }

        public Builder startHeight(int value) {
            startHeight = value;
            return this;
        }

        public Builder projectHeightmap(ProjectHeightmap value) {
            projectHeightmap = Objects.requireNonNull(value);
            return this;
        }

        public Builder generationStep(GenerationStep value) {
            generationStep = Objects.requireNonNull(value);
            return this;
        }

        public Builder terrainAdaptation(TerrainAdaptation value) {
            terrainAdaptation = Objects.requireNonNull(value);
            return this;
        }

        public Builder expansionHack(boolean value) {
            expansionHack = value;
            return this;
        }

        public Builder maxDistanceVertical(int value) {
            maxDistanceVertical = value;
            return this;
        }

        public Builder randomSpread(int spacingValue, int separationValue, int saltValue) {
            spacing = spacingValue;
            separation = separationValue;
            salt = saltValue;
            return this;
        }

        public Builder frequency(float value) {
            frequency = value;
            return this;
        }

        public Builder spreadType(SpreadType value) {
            spreadType = Objects.requireNonNull(value);
            return this;
        }

        public VanillaJigsawExportSettings build() {
            return new VanillaJigsawExportSettings(this);
        }
    }
}
