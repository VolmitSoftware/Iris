package art.arcane.iris.core.runtime.jigsaw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JigsawStudioVariantCatalog {
    private final List<JigsawStudioVariant> variants;
    private final Map<String, JigsawStudioVariant> byPieceKey;
    private final Map<JigsawPlanarArchetype, List<JigsawStudioVariant>> byArchetype;
    private final List<JigsawStudioVariant> spatialVariants;
    private final boolean editableGraph;

    public JigsawStudioVariantCatalog(List<JigsawStudioVariant> variants) {
        this(variants, true);
    }

    public JigsawStudioVariantCatalog(
            List<JigsawStudioVariant> variants,
            boolean editableGraph
    ) {
        Objects.requireNonNull(variants, "Jigsaw Studio variants");
        if (variants.size() > JigsawStudioLayout.MAX_VARIANTS) {
            throw new IllegalArgumentException("Jigsaw Studio catalogs cannot exceed "
                    + JigsawStudioLayout.MAX_VARIANTS + " variants");
        }
        List<JigsawStudioVariant> copied = List.copyOf(variants);
        Map<String, JigsawStudioVariant> pieceIndex = new LinkedHashMap<>();
        Map<JigsawPlanarArchetype, List<JigsawStudioVariant>> archetypeIndex =
                new EnumMap<>(JigsawPlanarArchetype.class);
        List<JigsawStudioVariant> spatial = new ArrayList<>();
        for (JigsawStudioVariant variant : copied) {
            JigsawStudioVariant activeVariant = Objects.requireNonNull(variant, "Jigsaw Studio variant");
            JigsawStudioVariant previous = pieceIndex.putIfAbsent(activeVariant.pieceKey(), activeVariant);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio variant piece key "
                        + activeVariant.pieceKey());
            }
            Optional<JigsawPlanarArchetype> archetype = activeVariant.archetype();
            if (archetype.isPresent()) {
                archetypeIndex.computeIfAbsent(archetype.get(), key -> new ArrayList<>()).add(activeVariant);
            } else {
                spatial.add(activeVariant);
            }
        }
        Map<JigsawPlanarArchetype, List<JigsawStudioVariant>> immutableArchetypes =
                new EnumMap<>(JigsawPlanarArchetype.class);
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            immutableArchetypes.put(
                    archetype,
                    List.copyOf(archetypeIndex.getOrDefault(archetype, List.of())));
        }
        this.variants = copied;
        this.byPieceKey = Collections.unmodifiableMap(pieceIndex);
        this.byArchetype = Collections.unmodifiableMap(immutableArchetypes);
        this.spatialVariants = List.copyOf(spatial);
        this.editableGraph = editableGraph;
    }

    public static JigsawStudioVariantCatalog empty() {
        return new JigsawStudioVariantCatalog(List.of());
    }

    public List<JigsawStudioVariant> variants() {
        return variants;
    }

    public Optional<JigsawStudioVariant> find(String pieceKey) {
        if (pieceKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byPieceKey.get(pieceKey));
    }

    public List<JigsawStudioVariant> variants(JigsawPlanarArchetype archetype) {
        return byArchetype.get(Objects.requireNonNull(archetype, "Planar archetype"));
    }

    public List<JigsawStudioVariant> spatialVariants() {
        return spatialVariants;
    }

    public boolean editableGraph() {
        return editableGraph;
    }

    public int size() {
        return variants.size();
    }
}
