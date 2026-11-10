package art.arcane.iris.core.runtime.jigsaw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JigsawStudioLayout {
    public static final int FLOOR_Y = 64;
    public static final int PLANAR_COLUMNS = 3;
    public static final int PLANAR_GAP = 1;
    public static final int MAX_VARIANTS = 512;
    public static final String SPATIAL_WORKCELL_ID = "workcell/spatial";

    private static final int FIRST_ORIGIN = 16;
    private static final JigsawStudioControlPosition CONTROL_POSITION =
            new JigsawStudioControlPosition(8, FLOOR_Y + 1, 8);

    private final JigsawStudioMode mode;
    private final JigsawStudioCellDimensions cellDimensions;
    private final int columns;
    private final int gap;
    private final JigsawStudioVariantCatalog variantCatalog;
    private final List<JigsawStudioBay> bays;
    private final Map<String, JigsawStudioBay> byStableId;
    private final Map<String, String> spatialVariantByBay;

    private JigsawStudioLayout(
            JigsawStudioMode mode,
            JigsawStudioCellDimensions cellDimensions,
            int columns,
            int gap,
            JigsawStudioVariantCatalog variantCatalog,
            List<JigsawStudioBay> bays,
            Map<String, String> spatialVariantByBay
    ) {
        this.mode = mode;
        this.cellDimensions = cellDimensions;
        this.columns = columns;
        this.gap = gap;
        this.variantCatalog = variantCatalog;
        this.bays = Collections.unmodifiableList(new ArrayList<>(bays));
        Map<String, JigsawStudioBay> index = new LinkedHashMap<>();
        for (JigsawStudioBay bay : bays) {
            JigsawStudioBay previous = index.putIfAbsent(bay.stableId(), bay);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio workcell stable ID " + bay.stableId());
            }
        }
        this.byStableId = Collections.unmodifiableMap(index);
        this.spatialVariantByBay = Collections.unmodifiableMap(new LinkedHashMap<>(spatialVariantByBay));
    }

    public static JigsawStudioLayout create(
            JigsawStudioMode mode,
            JigsawStudioCellDimensions cellDimensions,
            JigsawStudioVariantCatalog variantCatalog
    ) {
        JigsawStudioMode activeMode = Objects.requireNonNull(mode, "Jigsaw Studio layout mode");
        JigsawStudioCellDimensions dimensions = Objects.requireNonNull(
                cellDimensions,
                "Jigsaw Studio layout cell dimensions");
        JigsawStudioVariantCatalog catalog = Objects.requireNonNull(
                variantCatalog,
                "Jigsaw Studio variant catalog");
        validateCatalogMode(activeMode, catalog);

        if (activeMode == JigsawStudioMode.PLANAR_JIGSAW) {
            return createPlanar(dimensions, uniformSpecs(dimensions), catalog);
        }

        return createSpatial(dimensions, catalog, "");
    }

    public static JigsawStudioLayout createSpatial(
            JigsawStudioCellDimensions cellDimensions,
            JigsawStudioVariantCatalog variantCatalog,
            String displayName
    ) {
        JigsawStudioCellDimensions dimensions = Objects.requireNonNull(
                cellDimensions,
                "Spatial Jigsaw Studio cell dimensions");
        JigsawStudioVariantCatalog catalog = Objects.requireNonNull(
                variantCatalog,
                "Spatial Jigsaw Studio variant catalog");
        validateCatalogMode(JigsawStudioMode.SPATIAL_JIGSAW, catalog);
        String resolvedDisplayName = displayName == null ? "" : displayName.trim();

        List<JigsawStudioVariant> variants = catalog.spatialVariants();
        List<JigsawStudioBay> workcells = new ArrayList<>(Math.max(1, variants.size()));
        Map<String, String> variantsByWorkcell = new LinkedHashMap<>();
        if (variants.isEmpty()) {
            workcells.add(new JigsawStudioBay(
                    SPATIAL_WORKCELL_ID,
                    JigsawStudioBayKind.SPATIAL_WORKCELL,
                    Optional.empty(),
                    resolvedDisplayName,
                    new JigsawStudioBounds(FIRST_ORIGIN, FLOOR_Y + 1, FIRST_ORIGIN, dimensions)));
        } else {
            int originX = FIRST_ORIGIN;
            for (int index = 0; index < variants.size(); index++) {
                JigsawStudioVariant variant = variants.get(index);
                String stableId = index == 0
                        ? SPATIAL_WORKCELL_ID
                        : SPATIAL_WORKCELL_ID + "/" + variant.pieceKey();
                workcells.add(new JigsawStudioBay(
                        stableId,
                        JigsawStudioBayKind.SPATIAL_WORKCELL,
                        Optional.empty(),
                        variant.resolvedDisplayName(),
                        new JigsawStudioBounds(originX, FLOOR_Y + 1, FIRST_ORIGIN, dimensions)));
                variantsByWorkcell.put(stableId, variant.pieceKey());
                originX = Math.addExact(originX, Math.addExact(dimensions.width(), PLANAR_GAP));
            }
        }
        return new JigsawStudioLayout(
                JigsawStudioMode.SPATIAL_JIGSAW,
                dimensions,
                workcells.size(),
                PLANAR_GAP,
                catalog,
                workcells,
                variantsByWorkcell);
    }

    public static JigsawStudioLayout createPlanar(
            JigsawStudioCellDimensions defaultDimensions,
            List<JigsawStudioWorkcellSpec> workcellSpecs,
            JigsawStudioVariantCatalog variantCatalog
    ) {
        JigsawStudioCellDimensions defaults = Objects.requireNonNull(
                defaultDimensions,
                "Jigsaw Studio default cell dimensions");
        JigsawStudioVariantCatalog catalog = Objects.requireNonNull(
                variantCatalog,
                "Jigsaw Studio variant catalog");
        validateCatalogMode(JigsawStudioMode.PLANAR_JIGSAW, catalog);
        Map<JigsawPlanarArchetype, JigsawStudioWorkcellSpec> specs = indexSpecs(workcellSpecs);
        int[] columnWidths = new int[PLANAR_COLUMNS];
        int[] rowDepths = new int[2];
        JigsawPlanarArchetype[] archetypes = JigsawPlanarArchetype.values();
        for (int index = 0; index < archetypes.length; index++) {
            JigsawStudioCellDimensions dimensions = specs.get(archetypes[index]).dimensions();
            int column = index % PLANAR_COLUMNS;
            int row = index / PLANAR_COLUMNS;
            columnWidths[column] = Math.max(columnWidths[column], dimensions.width());
            rowDepths[row] = Math.max(rowDepths[row], dimensions.depth());
        }
        List<JigsawStudioBay> workcells = new ArrayList<>(archetypes.length);
        for (int index = 0; index < archetypes.length; index++) {
            JigsawPlanarArchetype archetype = archetypes[index];
            JigsawStudioWorkcellSpec spec = specs.get(archetype);
            workcells.add(new JigsawStudioBay(
                    archetype.stableId(),
                    JigsawStudioBayKind.PLANAR_WORKCELL,
                    Optional.of(spec),
                    spec.displayName(),
                    planarBounds(index, spec.dimensions(), columnWidths, rowDepths)));
        }
        return new JigsawStudioLayout(
                JigsawStudioMode.PLANAR_JIGSAW,
                defaults,
                PLANAR_COLUMNS,
                PLANAR_GAP,
                catalog,
                workcells,
                Map.of());
    }

    public JigsawStudioMode mode() {
        return mode;
    }

    public JigsawStudioCellDimensions cellDimensions() {
        return cellDimensions;
    }

    public int columns() {
        return columns;
    }

    public int gap() {
        return gap;
    }

    public JigsawStudioVariantCatalog variantCatalog() {
        return variantCatalog;
    }

    public List<JigsawStudioBay> bays() {
        return bays;
    }

    public JigsawStudioBay get(String stableId) {
        return stableId == null ? null : byStableId.get(stableId);
    }

    public JigsawStudioBay findAt(int worldX, int worldY, int worldZ) {
        for (JigsawStudioBay bay : bays) {
            if (bay.bounds().contains(worldX, worldY, worldZ)) {
                return bay;
            }
        }
        return null;
    }

    public List<JigsawStudioVariant> variants(JigsawStudioBay workcell) {
        JigsawStudioBay activeWorkcell = requireWorkcell(workcell);
        if (activeWorkcell.kind() == JigsawStudioBayKind.SPATIAL_WORKCELL) {
            String pieceKey = spatialVariantByBay.get(activeWorkcell.stableId());
            if (pieceKey == null) {
                return variantCatalog.spatialVariants();
            }
            return variantCatalog.find(pieceKey).map(List::of).orElseGet(List::of);
        }
        return variantCatalog.variants(activeWorkcell.archetype().orElseThrow());
    }

    public Optional<JigsawStudioVariant> defaultVariant(JigsawStudioBay workcell) {
        List<JigsawStudioVariant> variants = variants(workcell);
        return variants.isEmpty() ? Optional.empty() : Optional.of(variants.getFirst());
    }

    public boolean accepts(JigsawStudioBay workcell, JigsawStudioVariant variant) {
        JigsawStudioBay activeWorkcell = requireWorkcell(workcell);
        JigsawStudioVariant activeVariant = Objects.requireNonNull(variant, "Jigsaw Studio variant");
        if (variantCatalog.find(activeVariant.pieceKey()).filter(activeVariant::equals).isEmpty()) {
            return false;
        }
        if (activeWorkcell.kind() == JigsawStudioBayKind.SPATIAL_WORKCELL) {
            return activeVariant.mode() == JigsawStudioMode.SPATIAL_JIGSAW
                    && variants(activeWorkcell).contains(activeVariant);
        }
        return activeVariant.archetype().filter(activeWorkcell.archetype().orElseThrow()::equals).isPresent();
    }

    public Optional<JigsawStudioBay> workcellForVariant(String pieceKey) {
        Optional<JigsawStudioVariant> variant = variantCatalog.find(pieceKey);
        if (variant.isEmpty()) {
            return Optional.empty();
        }
        if (mode == JigsawStudioMode.PLANAR_JIGSAW) {
            return variant.get().archetype().map(archetype -> get(archetype.stableId()));
        }
        for (Map.Entry<String, String> entry : spatialVariantByBay.entrySet()) {
            if (entry.getValue().equals(variant.get().pieceKey())) {
                return Optional.ofNullable(get(entry.getKey()));
            }
        }
        return Optional.ofNullable(get(SPATIAL_WORKCELL_ID));
    }

    public JigsawStudioControlPosition controlPosition() {
        return CONTROL_POSITION;
    }

    public int extentX() {
        int maximum = CONTROL_POSITION.worldX();
        for (JigsawStudioBay bay : bays) {
            maximum = Math.max(maximum, bay.bounds().maxX() + PLANAR_GAP);
        }
        return maximum;
    }

    public int extentZ() {
        int maximum = CONTROL_POSITION.worldZ();
        for (JigsawStudioBay bay : bays) {
            maximum = Math.max(maximum, bay.bounds().maxZ() + PLANAR_GAP);
        }
        return maximum;
    }

    private JigsawStudioBay requireWorkcell(JigsawStudioBay workcell) {
        JigsawStudioBay activeWorkcell = Objects.requireNonNull(workcell, "Jigsaw Studio workcell");
        if (byStableId.get(activeWorkcell.stableId()) != activeWorkcell) {
            throw new IllegalArgumentException("Workcell does not belong to this Jigsaw Studio layout");
        }
        return activeWorkcell;
    }

    private static void validateCatalogMode(JigsawStudioMode mode, JigsawStudioVariantCatalog catalog) {
        for (JigsawStudioVariant variant : catalog.variants()) {
            if (variant.mode() != mode) {
                throw new IllegalArgumentException("Jigsaw Studio variant mode does not match the layout mode");
            }
        }
    }

    private static List<JigsawStudioWorkcellSpec> uniformSpecs(JigsawStudioCellDimensions dimensions) {
        List<JigsawStudioWorkcellSpec> specs = new ArrayList<>(JigsawPlanarArchetype.values().length);
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            specs.add(new JigsawStudioWorkcellSpec(archetype, "", dimensions, true));
        }
        return specs;
    }

    private static Map<JigsawPlanarArchetype, JigsawStudioWorkcellSpec> indexSpecs(
            List<JigsawStudioWorkcellSpec> workcellSpecs
    ) {
        List<JigsawStudioWorkcellSpec> source = List.copyOf(Objects.requireNonNull(
                workcellSpecs,
                "Jigsaw Studio workcell specifications"));
        Map<JigsawPlanarArchetype, JigsawStudioWorkcellSpec> specs =
                new EnumMap<>(JigsawPlanarArchetype.class);
        for (JigsawStudioWorkcellSpec spec : source) {
            JigsawStudioWorkcellSpec active = Objects.requireNonNull(
                    spec,
                    "Jigsaw Studio workcell specification");
            if (specs.putIfAbsent(active.archetype(), active) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Jigsaw Studio workcell specification " + active.archetype());
            }
        }
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            if (!specs.containsKey(archetype)) {
                throw new IllegalArgumentException("Missing Jigsaw Studio workcell specification " + archetype);
            }
        }
        return Collections.unmodifiableMap(specs);
    }

    private static JigsawStudioBounds planarBounds(
            int index,
            JigsawStudioCellDimensions dimensions,
            int[] columnWidths,
            int[] rowDepths
    ) {
        int column = index % PLANAR_COLUMNS;
        int row = index / PLANAR_COLUMNS;
        int originX = FIRST_ORIGIN;
        for (int current = 0; current < column; current++) {
            originX = Math.addExact(originX, Math.addExact(columnWidths[current], PLANAR_GAP));
        }
        int originZ = FIRST_ORIGIN;
        for (int current = 0; current < row; current++) {
            originZ = Math.addExact(originZ, Math.addExact(rowDepths[current], PLANAR_GAP));
        }
        return new JigsawStudioBounds(originX, FLOOR_Y + 1, originZ, dimensions);
    }
}
