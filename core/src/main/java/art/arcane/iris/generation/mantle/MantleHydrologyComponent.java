package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.hydrology.IrisDeepFluidConfig;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.hydrology.IrisHydrology;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.IrisRiverHydrology;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@ComponentFlag(ReservedFlag.RIVER_HYDROLOGY)
public final class MantleHydrologyComponent extends IrisMantleComponent {
    private static final int CHUNK_SIZE = 16;
    private static final int SAMPLE_HALO = 1;
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };
    private static final Comparator<CavePosition> POSITION_ORDER = Comparator
            .comparingInt(CavePosition::x)
            .thenComparingInt(CavePosition::y)
            .thenComparingInt(CavePosition::z);
    private static final MantleFlag[] PREREQUISITES = {ReservedFlag.CARVED};
    static final int PRIORITY = 1;

    public MantleHydrologyComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.RIVER_HYDROLOGY, PRIORITY);
    }

    @Override
    public MatterGenerationPhase getGenerationPhase() {
        return MatterGenerationPhase.TERRAIN;
    }

    @Override
    public MantleFlag[] getPrerequisiteFlags() {
        return PREREQUISITES;
    }

    @Override
    public boolean isInputGenerationLazy() {
        return true;
    }

    @Override
    public void generateLayer(MantleWriter writer, int chunkX, int chunkZ, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        IrisHydrologyRuntime runtime = complex.getHydrologyRuntime();
        if (runtime == null || !complex.allowsMantleChunkWrite(chunkX, chunkZ)) {
            return;
        }
        int minimumX = chunkX << 4;
        int minimumZ = chunkZ << 4;
        int maximumX = minimumX + CHUNK_SIZE;
        int maximumZ = minimumZ + CHUNK_SIZE;
        List<HydrologyCavePlan> cavePlans = runtime.cavePlansIn(
                minimumX - SAMPLE_HALO,
                minimumZ - SAMPLE_HALO,
                maximumX + SAMPLE_HALO,
                maximumZ + SAMPLE_HALO
        );
        CaveVoxelView caveView = new MantleHydrologyCaveVoxelView(
                writer.getMantle(),
                writer.getMantle().getWorldHeight(),
                new MantleHydrologyCaveVoxelView.TerrainSources((x, z) -> MantleHydrologyCaveVoxelView.terrainColumn(
                        complex, complex.sampleHydrologyColumn(x, z), x, z), complex::isNaturalTerrainSolid,
                (inputChunkX, inputChunkZ) -> MantleHydrologyCaveVoxelView.generateCarvingInput(
                        getEngineMantle(),
                        complex,
                        inputChunkX,
                        inputChunkZ
                ))
        );
        Publication publication = compilePublication(
                chunkX,
                chunkZ,
                writer.getMantle().getWorldHeight(),
                (x, z) -> Optional.ofNullable(complex.sampleHydrologyColumn(x, z)),
                cavePlans,
                caveView
        );
        publish(writer, context, publication);
    }

    @Override
    protected int computeRadius() {
        return 0;
    }

    public static boolean isEnabledFor(IrisDimension dimension) {
        if (!dimension.isUseMantle()
                || !dimension.isCarvingEnabled()
                || dimension.getDisabledComponents().contains(ReservedFlag.CARVED)
                || dimension.getDisabledComponents().contains(ReservedFlag.RIVER_HYDROLOGY)) {
            return false;
        }
        IrisHydrology hydrology = dimension.getHydrology();
        if (hydrology == null) {
            return false;
        }
        IrisRiverHydrology rivers = hydrology.getRivers();
        if (rivers != null && rivers.isEnabled()) {
            return true;
        }
        for (IrisDeepFluidConfig deepFluid : hydrology.getDeepFluids()) {
            if (deepFluid != null
                    && deepFluid.getDensity() > 0D
                    && (deepFluid.isContainedPools() || deepFluid.isShortChannels())) {
                return true;
            }
        }
        return false;
    }

    static Publication compilePublication(
            int chunkX,
            int chunkZ,
            int worldHeight,
            ColumnSampler sampler,
            Collection<HydrologyCavePlan> cavePlans,
            CaveVoxelView caveView
    ) {
        if (worldHeight < 2) {
            throw new IllegalArgumentException("worldHeight must be at least two");
        }
        Objects.requireNonNull(sampler);
        Objects.requireNonNull(cavePlans);
        Objects.requireNonNull(caveView);
        int minimumX = chunkX << 4;
        int minimumZ = chunkZ << 4;
        int maximumX = minimumX + CHUNK_SIZE - 1;
        int maximumZ = minimumZ + CHUNK_SIZE - 1;
        Map<Long, HydrologyColumnSample> samples = sampleColumns(
                minimumX - SAMPLE_HALO,
                minimumZ - SAMPLE_HALO,
                maximumX + SAMPLE_HALO,
                maximumZ + SAMPLE_HALO,
                sampler
        );
        if (samples.isEmpty()) {
            return Publication.empty();
        }
        if (!preconditionsHold(
                cavePlans,
                caveView,
                samples,
                minimumX,
                minimumZ,
                maximumX,
                maximumZ
        )) {
            return Publication.empty();
        }
        Map<Long, HydrologyCavePlan> cavePlansByCourseId = indexCavePlans(cavePlans);

        Map<CavePosition, PlannedCell> caveCells = new HashMap<>();
        Map<CavePosition, PlannedCell> surfaceCells = new HashMap<>();
        Map<CavePosition, PlannedCell> surfaceGuards = new HashMap<>();
        for (HydrologyColumnSample sample : samples.values()) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.oceanApron() || !ownsVolume(layer)) {
                    continue;
                }
                validateLayer(layer, worldHeight);
                if (layer.channel() && (isCaveLayer(layer)
                        || hasAcceptedVolume(sample, layer, cavePlansByCourseId.get(layer.feature().courseId())))) {
                    addCaveVolume(sample, layer, cavePlansByCourseId, caveCells);
                }
            }
            addSurfaceVolume(sample, surfaceCells);
            addSurfaceBedGuards(sample, surfaceGuards);
        }

        LinkedHashMap<CavePosition, HydrologyCaveCell> publishedCaveCells = new LinkedHashMap<>();
        LinkedHashMap<CavePosition, SurfaceFluidWrite> surfaceWrites = new LinkedHashMap<>();
        HashSet<CavePosition> fluidUpdates = new HashSet<>();
        copyOwnedCells(caveCells, minimumX, minimumZ, maximumX, maximumZ, publishedCaveCells, fluidUpdates);
        copySurfaceGuards(
                surfaceGuards,
                minimumX,
                minimumZ,
                maximumX,
                maximumZ,
                publishedCaveCells
        );
        copySurfaceWrites(surfaceCells, minimumX, minimumZ, maximumX, maximumZ, surfaceWrites, fluidUpdates);
        addAcceptedGuards(
                caveCells,
                cavePlans,
                samples,
                publishedCaveCells,
                minimumX,
                minimumZ,
                maximumX,
                maximumZ
        );
        suppressSurfaceWritesCoveredByCaves(publishedCaveCells, surfaceWrites, caveCells, samples);
        return new Publication(publishedCaveCells, surfaceWrites, fluidUpdates);
    }

    private static void suppressSurfaceWritesCoveredByCaves(
            Map<CavePosition, HydrologyCaveCell> caveCells,
            Map<CavePosition, SurfaceFluidWrite> surfaceWrites,
            Map<CavePosition, PlannedCell> plannedCaveCells,
            Map<Long, HydrologyColumnSample> samples
    ) {
        Iterator<Map.Entry<CavePosition, HydrologyCaveCell>> iterator = caveCells.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<CavePosition, HydrologyCaveCell> entry = iterator.next();
            CavePosition position = entry.getKey();
            SurfaceFluidWrite surfaceWrite = surfaceWrites.get(position);
            if (surfaceWrite == null) {
                continue;
            }
            HydrologyCaveCell caveCell = entry.getValue();
            if (!caveCell.fluidProfileKey().equals(surfaceWrite.profileKey())) {
                throw new IllegalStateException("Accepted surface and cave hydrology disagree at " + position
                        + ": surface=" + surfaceWrite.profileKey() + "/" + surfaceWrite.action()
                        + ", cave=" + caveCell.fluidProfileKey() + "/" + caveCell.action());
            }
            if (caveCell.action() == HydrologyCaveAction.SEAL_GUARD) {
                iterator.remove();
                continue;
            }
            if (caveCell.action() != surfaceWrite.action()) {
                PlannedCell plannedCaveCell = plannedCaveCells.get(position);
                if (plannedCaveCell == null) {
                    throw new IllegalStateException("Accepted cave hydrology has no publication owner at "
                            + position + ".");
                }
                caveCells.put(position, new HydrologyCaveCell(
                        surfaceWrite.action(),
                        caveCell.fluidProfileKey(),
                        floodedBiomeKeyAt(position, plannedCaveCell, samples)
                ));
            }
            surfaceWrites.remove(position);
        }
    }

    private static String floodedBiomeKeyAt(
            CavePosition position,
            PlannedCell plannedCaveCell,
            Map<Long, HydrologyColumnSample> samples
    ) {
        HydrologyColumnSample sample = samples.get(pack(position.x(), position.z()));
        if (sample != null) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.feature().courseId() == plannedCaveCell.courseId()
                        && layer.channel() && ownsVolume(layer) && !layer.oceanApron()
                        && position.y() > layer.bedY()
                        && position.y() <= layer.ceilingY()) {
                    return layer.floodedCaveBiomeKey();
                }
            }
        }
        throw new IllegalStateException("Accepted cave hydrology has no flooded biome owner at " + position + ".");
    }

    private static Map<Long, HydrologyColumnSample> sampleColumns(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ,
            ColumnSampler sampler
    ) {
        LinkedHashMap<Long, HydrologyColumnSample> samples = new LinkedHashMap<>();
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                Optional<HydrologyColumnSample> sampled = Objects.requireNonNull(sampler.sample(x, z));
                if (sampled.isPresent()) {
                    HydrologyColumnSample sample = sampled.get();
                    if (sample.x() != x || sample.z() != z) {
                        throw new IllegalStateException("Hydrology sample coordinates do not match the requested column");
                    }
                    samples.put(pack(x, z), sample);
                }
            }
        }
        return samples;
    }

    private static void addCaveVolume(
            HydrologyColumnSample sample,
            HydrologyColumnLayer layer,
            Map<Long, HydrologyCavePlan> cavePlansByCourseId,
            Map<CavePosition, PlannedCell> caveCells
    ) {
        HydrologyCavePlan plan = cavePlansByCourseId.get(layer.feature().courseId());
        if (plan == null) {
            throw new IllegalStateException("Accepted cave layer has no containment plan: "
                    + layer.feature().courseId());
        }
        for (int y = layer.bedY() + 1; y <= layer.ceilingY(); y++) {
            CavePosition position = new CavePosition(sample.x(), y, sample.z());
            HydrologyCaveAction action = plan.actions().get(position);
            if (action == null || action == HydrologyCaveAction.SEAL_GUARD) {
                throw new IllegalStateException("Accepted cave layer is absent from its containment plan at "
                        + position + ".");
            }
            PlannedCell candidate = new PlannedCell(
                    new HydrologyCaveCell(action, layer.profileKey(), layer.floodedCaveBiomeKey()),
                    layer.feature().courseId()
            );
            mergeCaveCell(caveCells, position, candidate);
        }
    }

    private static void addSurfaceVolume(
            HydrologyColumnSample sample,
            Map<CavePosition, PlannedCell> surfaceCells
    ) {
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (HydrologyColumnLayer layer : sample.layers()) {
            if (!layer.publishesSurfaceFluid()) {
                continue;
            }
            minimumY = Math.min(minimumY, layer.bedY() + 1);
            maximumY = Math.max(maximumY, layer.fluidHeadY());
        }
        if (minimumY == Integer.MAX_VALUE) {
            return;
        }
        for (int y = minimumY; y <= maximumY; y++) {
            HydrologyColumnSample.SurfacePublicationCell surfaceCell = sample.surfacePublicationCellAt(y)
                    .orElse(null);
            if (surfaceCell == null) {
                continue;
            }
            HydrologyColumnLayer layer = surfaceCell.layer();
            CavePosition position = new CavePosition(sample.x(), y, sample.z());
            PlannedCell candidate = new PlannedCell(
                    new HydrologyCaveCell(surfaceCell.action(), layer.profileKey(), ""),
                    layer.feature().courseId()
            );
            surfaceCells.put(position, candidate);
        }
    }

    private static void addSurfaceBedGuards(
            HydrologyColumnSample sample,
            Map<CavePosition, PlannedCell> surfaceGuards
    ) {
        HydrologyColumnLayer layer = sample.primarySurfaceLayer().orElse(null);
        if (layer == null || layer.oceanApron() || !layer.terrainOwned()) {
            return;
        }
        int minimumY = Math.max(1, layer.bedY() - 2);
        for (int y = minimumY; y <= layer.bedY(); y++) {
            CavePosition position = new CavePosition(sample.x(), y, sample.z());
            PlannedCell guard = new PlannedCell(
                    new HydrologyCaveCell(HydrologyCaveAction.SEAL_GUARD, layer.profileKey(), ""),
                    layer.feature().courseId()
            );
            mergeCell(surfaceGuards, position, guard);
        }
    }

    private static void mergeCell(
            Map<CavePosition, PlannedCell> cells,
            CavePosition position,
            PlannedCell candidate
    ) {
        PlannedCell existing = cells.get(position);
        if (existing == null || actionPriority(candidate.cell().action()) < actionPriority(existing.cell().action())) {
            cells.put(position, candidate);
        }
    }

    private static void mergeCaveCell(
            Map<CavePosition, PlannedCell> cells,
            CavePosition position,
            PlannedCell candidate
    ) {
        PlannedCell existing = cells.get(position);
        if (existing != null) {
            assertCompatibleCaveOverlap(position, existing, candidate);
        }
        mergeCell(cells, position, candidate);
    }

    private static void assertCompatibleCaveOverlap(
            CavePosition position,
            PlannedCell existing,
            PlannedCell candidate
    ) {
        long existingCourseId = existing.courseId();
        long candidateCourseId = candidate.courseId();
        if (existing.cell().action() == candidate.cell().action()
                && existing.cell().fluidProfileKey().equals(candidate.cell().fluidProfileKey())) {
            return;
        }
        throw new IllegalStateException("Incompatible accepted cave courses "
                + existingCourseId + " and " + candidateCourseId + " overlap at " + position
                + ": " + existing.cell().fluidProfileKey() + "/" + existing.cell().action()
                + " versus " + candidate.cell().fluidProfileKey() + "/" + candidate.cell().action());
    }

    private static void copyOwnedCells(
            Map<CavePosition, PlannedCell> planned,
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ,
            Map<CavePosition, HydrologyCaveCell> published,
            Set<CavePosition> fluidUpdates
    ) {
        for (Map.Entry<CavePosition, PlannedCell> entry : sortedEntries(planned)) {
            CavePosition position = entry.getKey();
            if (!inside(position, minimumX, minimumZ, maximumX, maximumZ)) {
                continue;
            }
            HydrologyCaveCell cell = entry.getValue().cell();
            published.put(position, cell);
        }
    }

    private static void copySurfaceWrites(
            Map<CavePosition, PlannedCell> planned,
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ,
            Map<CavePosition, SurfaceFluidWrite> published,
            Set<CavePosition> fluidUpdates
    ) {
        for (Map.Entry<CavePosition, PlannedCell> entry : sortedEntries(planned)) {
            CavePosition position = entry.getKey();
            if (!inside(position, minimumX, minimumZ, maximumX, maximumZ)) {
                continue;
            }
            HydrologyCaveCell cell = entry.getValue().cell();
            published.put(position, new SurfaceFluidWrite(cell.fluidProfileKey(), cell.action()));
        }
        for (Map.Entry<CavePosition, SurfaceFluidWrite> entry : published.entrySet()) {
            SurfaceFluidWrite write = entry.getValue();
            if (write.action() != HydrologyCaveAction.WET_SOURCE) {
                continue;
            }
            CavePosition position = entry.getKey();
            PlannedCell below = planned.get(new CavePosition(position.x(), position.y() - 1, position.z()));
            if (below != null
                    && below.cell().action() == HydrologyCaveAction.FALLING_FLUID
                    && below.cell().fluidProfileKey().equals(write.profileKey())) {
                fluidUpdates.add(position);
                continue;
            }
            if (hasLowerHorizontalFluidFace(planned, position, write.profileKey())) {
                fluidUpdates.add(position);
            }
        }
    }

    private static boolean hasLowerHorizontalFluidFace(
            Map<CavePosition, PlannedCell> planned,
            CavePosition position,
            String profileKey
    ) {
        int[] offsetsX = {-1, 1, 0, 0};
        int[] offsetsZ = {0, 0, -1, 1};
        for (int index = 0; index < offsetsX.length; index++) {
            CavePosition adjacent = new CavePosition(
                    position.x() + offsetsX[index],
                    position.y(),
                    position.z() + offsetsZ[index]
            );
            PlannedCell level = planned.get(adjacent);
            if (level != null && level.cell().fluidProfileKey().equals(profileKey)) {
                continue;
            }
            PlannedCell lower = planned.get(new CavePosition(adjacent.x(), adjacent.y() - 1, adjacent.z()));
            if (lower != null
                    && lower.cell().action() == HydrologyCaveAction.WET_SOURCE
                    && lower.cell().fluidProfileKey().equals(profileKey)) {
                return true;
            }
        }
        return false;
    }

    private static void copySurfaceGuards(
            Map<CavePosition, PlannedCell> planned,
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ,
            Map<CavePosition, HydrologyCaveCell> published
    ) {
        for (Map.Entry<CavePosition, PlannedCell> entry : sortedEntries(planned)) {
            CavePosition position = entry.getKey();
            if (!inside(position, minimumX, minimumZ, maximumX, maximumZ)) {
                continue;
            }
            published.put(position, entry.getValue().cell());
        }
    }

    private static void addAcceptedGuards(
            Map<CavePosition, PlannedCell> caveCells,
            Collection<HydrologyCavePlan> cavePlans,
            Map<Long, HydrologyColumnSample> samples,
            Map<CavePosition, HydrologyCaveCell> published,
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        HashMap<CavePosition, PlannedCell> guardCells = new HashMap<>();
        ArrayList<HydrologyCavePlan> orderedPlans = new ArrayList<>(cavePlans);
        orderedPlans.sort(Comparator.comparingLong((HydrologyCavePlan plan) -> plan.source().sourceId()));
        for (HydrologyCavePlan plan : orderedPlans) {
            ArrayList<CavePosition> guardPositions = new ArrayList<>();
            plan.forEachActionIn(minimumX, minimumZ, maximumX + 1, maximumZ + 1,
                    (CavePosition position, HydrologyCaveAction action) -> {
                        if (action == HydrologyCaveAction.SEAL_GUARD) {
                            guardPositions.add(position);
                        }
                    });
            guardPositions.sort(POSITION_ORDER);
            for (CavePosition position : guardPositions) {
                PlannedCell owner = guardOwner(position, plan, caveCells);
                if (owner == null) {
                    if (guardOutsidePublication(position, plan, samples)) {
                        continue;
                    }
                    throw new IllegalStateException("Accepted containment guard has no adjacent cave volume at "
                            + position);
                }
                PlannedCell candidate = new PlannedCell(
                        new HydrologyCaveCell(
                                HydrologyCaveAction.SEAL_GUARD,
                                owner.cell().fluidProfileKey(),
                                ""
                        ),
                        plan.source().sourceId()
                );
                PlannedCell caveVolume = caveCells.get(position);
                if (caveVolume != null) {
                    assertCompatibleCaveOverlap(position, caveVolume, candidate);
                }
                mergeCaveCell(guardCells, position, candidate);
            }
        }
        for (Map.Entry<CavePosition, PlannedCell> entry : sortedEntries(guardCells)) {
            published.putIfAbsent(entry.getKey(), entry.getValue().cell());
        }
    }

    private static PlannedCell guardOwner(
            CavePosition guard,
            HydrologyCavePlan plan,
            Map<CavePosition, PlannedCell> caveCells
    ) {
        for (int[] offset : NEIGHBORS) {
            CavePosition ownerPosition = guard.offset(-offset[0], -offset[1], -offset[2]);
            PlannedCell owner = caveCells.get(ownerPosition);
            HydrologyCaveAction plannedAction = plan.actions().get(ownerPosition);
            if (owner != null
                    && plannedAction != null
                    && plannedAction != HydrologyCaveAction.SEAL_GUARD
                    && owner.cell().action() == plannedAction) {
                return owner;
            }
        }
        return null;
    }

    private static boolean guardOutsidePublication(
            CavePosition guard,
            HydrologyCavePlan plan,
            Map<Long, HydrologyColumnSample> samples
    ) {
        boolean hasPlannedVolume = false;
        for (int[] offset : NEIGHBORS) {
            CavePosition position = guard.offset(-offset[0], -offset[1], -offset[2]);
            HydrologyCaveAction action = plan.actions().get(position);
            if (action == null || action == HydrologyCaveAction.SEAL_GUARD) {
                continue;
            }
            hasPlannedVolume = true;
            HydrologyColumnSample sample = samples.get(pack(position.x(), position.z()));
            if (sample == null) {
                continue;
            }
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.feature().courseId() == plan.source().sourceId()
                        && layer.channel() && ownsVolume(layer) && !layer.oceanApron()
                        && (isCaveLayer(layer) || hasAcceptedVolume(sample, layer, plan))) {
                    return false;
                }
            }
        }
        return hasPlannedVolume;
    }

    private static boolean preconditionsHold(
            Collection<HydrologyCavePlan> cavePlans,
            CaveVoxelView caveView,
            Map<Long, HydrologyColumnSample> samples,
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        for (HydrologyCavePlan plan : cavePlans) {
            if (!plan.allPreconditionsIn(
                    minimumX,
                    minimumZ,
                    maximumX + 1,
                    maximumZ + 1,
                    (CavePosition position, CaveVoxelPrecondition expected) -> preconditionMatches(
                            plan,
                            position,
                            expected,
                            caveView,
                            samples.get(pack(position.x(), position.z()))
                    ))) {
                return false;
            }
        }
        return true;
    }

    private static boolean preconditionMatches(
            HydrologyCavePlan plan,
            CavePosition position,
            CaveVoxelPrecondition expected,
            CaveVoxelView caveView,
            HydrologyColumnSample sample
    ) {
        if (expected.voxel() == CaveVoxel.UNCONDITIONAL) {
            return true;
        }
        CaveVoxel actual = caveView.voxelAt(position);
        boolean actualOpen = caveView.isOpenToSurface(position);
        if (actual == expected.voxel() && actualOpen == expected.openToSurface()) {
            return true;
        }
        HydrologyCaveAction action = plan.actions().get(position);
        if (expected.voxel() == CaveVoxel.CAVE_AIR
                && expected.openToSurface()
                && actual == CaveVoxel.CAVE_AIR
                && !actualOpen
                && action != HydrologyCaveAction.DRY_AIR) {
            return true;
        }
        if (expected.voxel() != CaveVoxel.CAVE_AIR
                || !expected.openToSurface()
                || actual != CaveVoxel.SOLID
                || actualOpen) {
            return false;
        }
        if (action == null || action == HydrologyCaveAction.SEAL_GUARD) {
            return true;
        }
        if (action != HydrologyCaveAction.WET_SOURCE
                && action != HydrologyCaveAction.FALLING_FLUID) {
            return false;
        }
        if (sample == null) {
            return false;
        }
        for (HydrologyColumnLayer layer : sample.layers()) {
            if (layer.feature().courseId() == plan.source().sourceId()
                    && layer.feature().type().isSurface()
                    && !layer.oceanApron()
                    && layer.channel()
                    && layer.terrainOwned()
                    && layer.fluidOwned()
                    && layer.connectedFluid()
                    && position.y() > layer.bedY()
                    && position.y() <= layer.fluidHeadY()) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, HydrologyCavePlan> indexCavePlans(
            Collection<HydrologyCavePlan> cavePlans
    ) {
        LinkedHashMap<Long, HydrologyCavePlan> indexed = new LinkedHashMap<>();
        for (HydrologyCavePlan plan : cavePlans) {
            if (!plan.accepted()) {
                throw new IllegalArgumentException("Mantle publication cannot consume rejected cave plans");
            }
            long courseId = plan.source().sourceId();
            HydrologyCavePlan existing = indexed.putIfAbsent(courseId, plan);
            if (existing != null && !existing.equals(plan)) {
                throw new IllegalArgumentException("Conflicting cave plans for course " + courseId);
            }
        }
        return indexed;
    }

    static void publish(
            MantleWriter writer,
            ChunkContext context,
            Publication publication
    ) {
        LinkedHashMap<CavePosition, PlatformBlockState> resolvedSurfaceWrites = new LinkedHashMap<>();
        for (Map.Entry<CavePosition, SurfaceFluidWrite> entry : sortedEntries(publication.surfaceWrites())) {
            CavePosition position = entry.getKey();
            SurfaceFluidWrite write = entry.getValue();
            PlatformBlockState fluid = Objects.requireNonNull(context.getComplex().resolveHydrologyFluid(
                    write.profileKey(),
                    position.x(),
                    position.z()
            ));
            PlatformBlockState state = write.action() == HydrologyCaveAction.FALLING_FLUID
                    ? fallingFluidState(fluid)
                    : fluid;
            resolvedSurfaceWrites.put(position, state);
        }
        HashSet<FluidCoordinate> resolvedCaveFluids = new HashSet<>();
        for (Map.Entry<CavePosition, HydrologyCaveCell> entry : sortedEntries(publication.caveCells())) {
            HydrologyCaveCell cell = entry.getValue();
            CavePosition position = entry.getKey();
            FluidCoordinate coordinate = new FluidCoordinate(
                    cell.fluidProfileKey(),
                    position.x(),
                    position.z()
            );
            if (cell.isWet() && resolvedCaveFluids.add(coordinate)) {
                Objects.requireNonNull(context.getComplex().resolveHydrologyFluid(
                        coordinate.profileKey(),
                        coordinate.x(),
                        coordinate.z()
                ));
            }
        }
        for (Map.Entry<CavePosition, PlatformBlockState> entry : resolvedSurfaceWrites.entrySet()) {
            CavePosition position = entry.getKey();
            writer.setData(position.x(), position.y(), position.z(), entry.getValue());
        }
        for (CavePosition position : sortedPositions(publication.fluidUpdates())) {
            writer.setData(position.x(), position.y(), position.z(), UpdateMatter.ON);
        }
        for (Map.Entry<CavePosition, HydrologyCaveCell> entry : sortedEntries(publication.caveCells())) {
            CavePosition position = entry.getKey();
            writer.setData(position.x(), position.y(), position.z(), entry.getValue());
        }
    }

    private static void validateLayer(HydrologyColumnLayer layer, int worldHeight) {
        if (layer.bedY() < 0
                || layer.fluidHeadY() < layer.bedY()
                || layer.ceilingY() < layer.fluidHeadY()
                || layer.ceilingY() >= worldHeight) {
            throw new IllegalStateException("Accepted hydrology layer exceeds the mantle bounds: " + layer);
        }
    }

    private static boolean ownsVolume(HydrologyColumnLayer layer) {
        return layer.terrainOwned() || layer.fallingFluid() && layer.fluidOwned();
    }

    private static boolean hasAcceptedVolume(HydrologyColumnSample sample, HydrologyColumnLayer layer,
                                              HydrologyCavePlan plan) {
        if (plan == null || !plan.accepted()) {
            return false;
        }
        for (int y = layer.bedY() + 1; y <= layer.ceilingY(); y++) {
            HydrologyCaveAction action = plan.actions().get(new CavePosition(sample.x(), y, sample.z()));
            if (action != null && action != HydrologyCaveAction.SEAL_GUARD) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCaveLayer(HydrologyColumnLayer layer) {
        return layer.feature().type().isUnderground() || layer.feature().type().isDeepFluid();
    }

    private static HydrologyCaveAction actionAt(HydrologyColumnLayer layer, int y) {
        if (y > layer.fluidHeadY()) {
            return HydrologyCaveAction.DRY_AIR;
        }
        if (layer.fallingFluid() && y < layer.fluidHeadY()) {
            return HydrologyCaveAction.FALLING_FLUID;
        }
        return HydrologyCaveAction.WET_SOURCE;
    }

    private static int actionPriority(HydrologyCaveAction action) {
        return switch (action) {
            case WET_SOURCE -> 0;
            case FALLING_FLUID -> 1;
            case DRY_AIR -> 2;
            case SEAL_GUARD -> 3;
        };
    }

    private static PlatformBlockState fallingFluidState(PlatformBlockState fluid) {
        if (!IrisProceduralBlocks.hasProperty(fluid, "level")
                || "8".equals(IrisProceduralBlocks.propertyValue(fluid, "level"))) {
            return fluid;
        }
        return fluid.withProperty("level", "8");
    }

    private static boolean inside(
            CavePosition position,
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        return position.x() >= minimumX && position.x() <= maximumX
                && position.z() >= minimumZ && position.z() <= maximumZ;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static <T> List<Map.Entry<CavePosition, T>> sortedEntries(Map<CavePosition, T> values) {
        ArrayList<Map.Entry<CavePosition, T>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey(POSITION_ORDER));
        return entries;
    }

    private static List<CavePosition> sortedPositions(Set<CavePosition> values) {
        ArrayList<CavePosition> positions = new ArrayList<>(values);
        positions.sort(POSITION_ORDER);
        return positions;
    }

    @FunctionalInterface
    interface ColumnSampler {
        Optional<HydrologyColumnSample> sample(int x, int z);
    }

    record Publication(
            Map<CavePosition, HydrologyCaveCell> caveCells,
            Map<CavePosition, SurfaceFluidWrite> surfaceWrites,
            Set<CavePosition> fluidUpdates
    ) {
        Publication {
            caveCells = Map.copyOf(Objects.requireNonNull(caveCells));
            surfaceWrites = Map.copyOf(Objects.requireNonNull(surfaceWrites));
            fluidUpdates = Set.copyOf(Objects.requireNonNull(fluidUpdates));
        }

        static Publication empty() {
            return new Publication(Map.of(), Map.of(), Set.of());
        }
    }

    record SurfaceFluidWrite(String profileKey, HydrologyCaveAction action) {
        SurfaceFluidWrite {
            if (profileKey == null || profileKey.isBlank()) {
                throw new IllegalArgumentException("profileKey must not be blank");
            }
            profileKey = profileKey.trim();
            if (action != HydrologyCaveAction.WET_SOURCE
                    && action != HydrologyCaveAction.FALLING_FLUID) {
                throw new IllegalArgumentException("Surface hydrology writes must contain fluid");
            }
        }
    }

    private record FluidCoordinate(String profileKey, int x, int z) {
    }

    private record PlannedCell(HydrologyCaveCell cell, long courseId) {
    }
}
