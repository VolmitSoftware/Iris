package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.studio.render.IrisRenderer;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyDiagnosticRenderSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyPlanner;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.generation.hydrology.HydrologyTile;
import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import art.arcane.iris.generation.hydrology.RiverCourse;
import art.arcane.iris.generation.hydrology.RiverCourseType;
import art.arcane.iris.generation.hydrology.RiverOutlet;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveSource;
import art.arcane.iris.generation.hydrology.IrisDeepFluidConfig;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import org.junit.Test;
import org.mockito.InOrder;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleHydrologyComponentTest {
    @Test
    public void fallingPublicationWritesLevelEightFluidAndUpdateMarker() {
        CavePosition position = new CavePosition(4, 20, 6);
        PlatformBlockState source = mock(PlatformBlockState.class);
        PlatformBlockState falling = mock(PlatformBlockState.class);
        when(source.key()).thenReturn("minecraft:water[level=0]");
        when(source.withProperty("level", "8")).thenReturn(falling);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.resolveHydrologyFluid("river", position.x(), position.z())).thenReturn(source);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        MantleWriter writer = mock(MantleWriter.class);
        MantleHydrologyComponent.Publication publication = new MantleHydrologyComponent.Publication(
                Map.of(),
                Map.of(position, new MantleHydrologyComponent.SurfaceFluidWrite(
                        "river",
                        HydrologyCaveAction.FALLING_FLUID
                )),
                Set.of(position)
        );

        MantleHydrologyComponent.publish(writer, context, publication);

        InOrder order = inOrder(writer);
        order.verify(writer).setData(position.x(), position.y(), position.z(), falling);
        order.verify(writer).setData(position.x(), position.y(), position.z(), UpdateMatter.ON);
        verify(writer, never()).setData(position.x(), position.y(), position.z(), source);
    }

    @Test
    public void componentUsesCanonicalFlagPriorityAndConfiguration() {
        ComponentFlag flag = MantleHydrologyComponent.class.getAnnotation(ComponentFlag.class);
        assertNotNull(flag);
        assertEquals(ReservedFlag.RIVER_HYDROLOGY, flag.value());
        assertEquals(1, MantleHydrologyComponent.PRIORITY);

        IrisDimension dimension = new IrisDimension();
        assertFalse(MantleHydrologyComponent.isEnabledFor(dimension));
        dimension.getHydrology().getRivers().setEnabled(true);
        assertTrue(MantleHydrologyComponent.isEnabledFor(dimension));
        dimension.getHydrology().getRivers().setEnabled(false);
        dimension.getHydrology().getDeepFluids().add(new IrisDeepFluidConfig());
        assertTrue(MantleHydrologyComponent.isEnabledFor(dimension));
        dimension.setCarvingEnabled(false);
        assertFalse(MantleHydrologyComponent.isEnabledFor(dimension));
    }

    @Test
    public void surfaceDropPublishesSourceLipFallingThroatAndReceivingPool() {
        HydrologyColumnLayer drop = layer(
                HydrologyFeatureType.WATERFALL,
                11L,
                7L,
                60,
                70,
                70,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer receiving = layer(
                HydrologyFeatureType.WATERFALL,
                12L,
                7L,
                60,
                64,
                64,
                false,
                true,
                "river"
        );
        Map<Long, HydrologyColumnSample> samples = samples(sample(8, 8, false, 78, List.of(drop, receiving)));

        MantleHydrologyComponent.Publication publication = compile(
                samples,
                List.of(),
                new TestCaveVoxelView()
        );

        assertEquals(3, publication.caveCells().size());
        for (HydrologyCaveCell cell : publication.caveCells().values()) {
            assertEquals(HydrologyCaveAction.SEAL_GUARD, cell.action());
        }
        for (int y = 61; y <= 64; y++) {
            assertEquals(HydrologyCaveAction.WET_SOURCE, publication.surfaceWrites()
                    .get(new CavePosition(8, y, 8)).action());
        }
        for (int y = 65; y < 70; y++) {
            CavePosition position = new CavePosition(8, y, 8);
            assertEquals(HydrologyCaveAction.FALLING_FLUID, publication.surfaceWrites().get(position).action());
            assertFalse(publication.fluidUpdates().contains(position));
        }
        assertEquals(HydrologyCaveAction.WET_SOURCE, publication.surfaceWrites()
                .get(new CavePosition(8, 70, 8)).action());
        assertTrue(publication.fluidUpdates().contains(new CavePosition(8, 70, 8)));
    }

    @Test
    public void overlappingSourceMasksACompletelySubmergedFallingThroat() {
        HydrologyColumnLayer falling = layer(
                HydrologyFeatureType.RIFFLE,
                11L,
                7L,
                60,
                70,
                70,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer overlappingSource = layer(
                HydrologyFeatureType.RIFFLE,
                12L,
                8L,
                60,
                69,
                69,
                false,
                true,
                "river"
        );
        HydrologyColumnSample sample = sample(
                8,
                8,
                false,
                78,
                List.of(overlappingSource, falling)
        );
        assertEquals(falling, sample.primarySurfaceFluidLayer().orElseThrow());

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(),
                new TestCaveVoxelView()
        );
        CavePosition throat = new CavePosition(8, 61, 8);

        assertEquals(HydrologyCaveAction.WET_SOURCE, publication.surfaceWrites().get(throat).action());
        assertFalse(publication.fluidUpdates().contains(throat));
    }

    @Test
    public void surfaceFluidBelowTheComposedTerrainBedIsNotPublished() {
        HydrologyColumnLayer selectedTerrain = layer(
                HydrologyFeatureType.WATERFALL,
                13L,
                9L,
                65,
                70,
                70,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer submergedSource = layer(
                HydrologyFeatureType.SURFACE_POOL,
                14L,
                10L,
                60,
                64,
                64,
                false,
                true,
                "river"
        );
        HydrologyColumnSample sample = sample(
                8,
                8,
                false,
                78,
                List.of(submergedSource, selectedTerrain)
        );

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(),
                new TestCaveVoxelView()
        );

        assertEquals(65, sample.terrainHeight());
        for (int y = 61; y <= 65; y++) {
            assertFalse(publication.surfaceWrites().containsKey(new CavePosition(8, y, 8)));
        }
        assertEquals(
                HydrologyCaveAction.FALLING_FLUID,
                publication.surfaceWrites().get(new CavePosition(8, 66, 8)).action()
        );
    }

    @Test
    public void plannedGradualCascadePublishesGradedFaceAndReceivingPool() {
        HydrologyTile tile = plannedCascadeTile();

        int transitionCount = 0;
        for (RiverCourse course : tile.courses()) {
            if (course.type() != RiverCourseType.SURFACE) {
                continue;
            }
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() != HydrologyFeatureType.CASCADE || segment.drop() <= 0) {
                    continue;
                }
                transitionCount++;
                assertFalse(segment.fallingFluid());

                MantleHydrologyComponent.Publication poolPublication = MantleHydrologyComponent.compilePublication(
                        Math.floorDiv(segment.end().x(), 16),
                        Math.floorDiv(segment.end().z(), 16),
                        128,
                        tile::columnAt,
                        tile.cavePlans(),
                        new TestCaveVoxelView()
                );
                CavePosition receiving = new CavePosition(
                        segment.end().x(),
                        segment.downstreamHeadY(),
                        segment.end().z()
                );
                assertNotNull(poolPublication.surfaceWrites().get(receiving));
                assertEquals(
                        HydrologyCaveAction.WET_SOURCE,
                        poolPublication.surfaceWrites().get(receiving).action()
                );
            }
        }
        assertTrue(transitionCount >= 1);
    }

    @Test
    public void acceptedSurfaceHeadwaterMaterializesAsOneContinuousExposedPublishedChannel() {
        HydrologyTile tile = plannedCascadeTile();
        ArrayList<RiverCourse> surfaceCourses = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.SURFACE) {
                surfaceCourses.add(course);
            }
        }
        assertEquals(tile.diagnosticCandidates().toString(), 1, surfaceCourses.size());
        RiverCourse course = surfaceCourses.getFirst();

        LinkedHashMap<Long, HydrologyColumnLayer> fluidLayers = new LinkedHashMap<>();
        CavePosition sourceCell = null;
        long maximumSourceDistanceSquared = 0L;
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            HydrologyColumnLayer layer = column.primarySurfaceFluidLayer().orElse(null);
            if (layer == null || layer.feature().courseId() != course.id()) {
                continue;
            }
            assertTrue(layer.channel());
            assertTrue(layer.connectedFluid());
            assertTrue(layer.fluidOwned());
            assertFalse(layer.oceanApron());
            assertTrue(
                    "column " + column.x() + "," + column.z() + " publishes no surface fluid",
                    layer.publishesSurfaceFluid()
            );
            assertEquals(layer.fluidHeadY(), layer.ceilingY());
            assertTrue(layer.bedY() < layer.fluidHeadY());
            if (layer.fallingFluid()) {
                assertFalse(layer.terrainOwned());
            } else {
                assertTrue(layer.terrainOwned());
                assertEquals(layer.bedY(), column.terrainHeight());
            }
            fluidLayers.put(pack(column.x(), column.z()), layer);
            if (layer.feature().source()) {
                assertNull(sourceCell);
                sourceCell = new CavePosition(column.x(), layer.fluidHeadY(), column.z());
            }
        }
        assertNotNull(sourceCell);
        assertTrue(fluidLayers.size() > 64);

        LinkedHashMap<Long, MantleHydrologyComponent.Publication> chunkPublications = new LinkedHashMap<>();
        for (long packedColumn : fluidLayers.keySet()) {
            int x = (int) (packedColumn >> 32);
            int z = (int) packedColumn;
            int chunkX = Math.floorDiv(x, 16);
            int chunkZ = Math.floorDiv(z, 16);
            long packedChunk = pack(chunkX, chunkZ);
            chunkPublications.computeIfAbsent(
                    packedChunk,
                    (Long ignored) -> MantleHydrologyComponent.compilePublication(
                            chunkX,
                            chunkZ,
                            128,
                            tile::columnAt,
                            tile.cavePlans(),
                            new TestCaveVoxelView()
                    )
            );
        }

        HashSet<CavePosition> materializedFluid = new HashSet<>();
        for (Map.Entry<Long, HydrologyColumnLayer> entry : fluidLayers.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) entry.getKey().longValue();
            HydrologyColumnLayer layer = entry.getValue();
            for (int y = layer.bedY() + 1; y <= layer.fluidHeadY(); y++) {
                materializedFluid.add(new CavePosition(x, y, z));
            }
            long deltaX = (long) x - sourceCell.x();
            long deltaZ = (long) z - sourceCell.z();
            maximumSourceDistanceSquared = Math.max(
                    maximumSourceDistanceSquared,
                    deltaX * deltaX + deltaZ * deltaZ
            );
        }
        int fallingWriteCount = 0;
        int wetWriteCount = 0;
        for (MantleHydrologyComponent.Publication publication : chunkPublications.values()) {
            assertFalse(publication.caveCells().isEmpty());
            for (HydrologyCaveCell cell : publication.caveCells().values()) {
                assertEquals(HydrologyCaveAction.SEAL_GUARD, cell.action());
            }
            materializedFluid.addAll(publication.surfaceWrites().keySet());
            for (Map.Entry<CavePosition, MantleHydrologyComponent.SurfaceFluidWrite> entry
                    : publication.surfaceWrites().entrySet()) {
                if (entry.getValue().action() == HydrologyCaveAction.FALLING_FLUID) {
                    fallingWriteCount++;
                    assertFalse(publication.fluidUpdates().contains(entry.getKey()));
                } else {
                    assertEquals(HydrologyCaveAction.WET_SOURCE, entry.getValue().action());
                    wetWriteCount++;
                    if (publication.fluidUpdates().contains(entry.getKey())) {
                        assertEquals(HydrologyCaveAction.WET_SOURCE, entry.getValue().action());
                    }
                }
            }
        }
        boolean expectsFallingFluid = false;
        for (HydraulicSegment segment : course.segments()) {
            if (segment.fallingFluid()) {
                expectsFallingFluid = true;
                break;
            }
        }
        assertEquals(expectsFallingFluid, fallingWriteCount > 0);
        assertTrue(wetWriteCount > 0);
        int transitionCount = 0;
        for (HydraulicSegment segment : course.segments()) {
            if (segment.drop() <= 0) {
                continue;
            }
            MantleHydrologyComponent.Publication poolPublication = publicationAt(
                    chunkPublications,
                    segment.end().x(),
                    segment.end().z()
            );
            CavePosition receiving = new CavePosition(
                    segment.end().x(),
                    segment.downstreamHeadY(),
                    segment.end().z()
            );
            assertEquals(
                    HydrologyCaveAction.WET_SOURCE,
                    poolPublication.surfaceWrites().get(receiving).action()
            );
            transitionCount++;
        }
        assertTrue(transitionCount >= 2);
        assertTrue(maximumSourceDistanceSquared >= 64L * 64L);

        HashMap<CavePosition, MantleHydrologyComponent.SurfaceFluidWrite> publishedWrites = new HashMap<>();
        HashSet<CavePosition> publishedUpdates = new HashSet<>();
        for (MantleHydrologyComponent.Publication publication : chunkPublications.values()) {
            publishedWrites.putAll(publication.surfaceWrites());
            publishedUpdates.addAll(publication.fluidUpdates());
        }
        int simulatedSpillFaces = 0;
        int[] offsetsX = {-1, 1, 0, 0};
        int[] offsetsZ = {0, 0, -1, 1};
        for (CavePosition update : publishedUpdates) {
            MantleHydrologyComponent.SurfaceFluidWrite upperWrite = publishedWrites.get(update);
            if (upperWrite == null) {
                continue;
            }
            for (int index = 0; index < offsetsX.length; index++) {
                CavePosition lower = new CavePosition(
                        update.x() + offsetsX[index],
                        update.y() - 1,
                        update.z() + offsetsZ[index]
                );
                MantleHydrologyComponent.SurfaceFluidWrite lowerWrite = publishedWrites.get(lower);
                if (lowerWrite == null
                        || lowerWrite.action() != HydrologyCaveAction.WET_SOURCE
                        || !lowerWrite.profileKey().equals(upperWrite.profileKey())) {
                    continue;
                }
                if (materializedFluid.add(new CavePosition(lower.x(), update.y(), lower.z()))) {
                    simulatedSpillFaces++;
                }
            }
        }
        assertTrue(simulatedSpillFaces > 0);

        Set<CavePosition> connected = connectedFluidFrom(sourceCell, materializedFluid);
        HashSet<CavePosition> disconnected = new HashSet<>(materializedFluid);
        disconnected.removeAll(connected);
        assertEquals(disconnected.stream().limit(12).toList().toString(), materializedFluid.size(), connected.size());
    }

    @Test
    public void lShapedCoastTerminatesAtFirstOceanContactWithOnlyBoundedNonOwningApron() {
        HydrologyPlannerSettings settings = cascadeSettings();
        HydrologyTerrainSampler terrain = (int x, int z) -> {
            boolean ocean = x >= 96 && z >= 48;
            int height = ocean ? 54 : 160 - Math.floorDiv(x + z, 8);
            return cascadeTerrain(height, ocean, x == 0 && z == 0, !ocean);
        };
        HydrologyTile tile = new HydrologyPlanner(
                4207L,
                settings,
                terrain,
                new TestCaveVoxelView()
        ).plan(new HydrologyTileKey(0, 0));
        ArrayList<RiverCourse> surfaceCourses = new ArrayList<>();
        for (RiverCourse candidate : tile.courses()) {
            if (candidate.type() == RiverCourseType.SURFACE) {
                surfaceCourses.add(candidate);
            }
        }
        assertEquals(1, surfaceCourses.size());
        RiverCourse course = surfaceCourses.getFirst();
        RiverOutlet outlet = tile.outlet(course.outletId().orElseThrow()).orElseThrow();
        assertTrue(outlet.directOcean());
        assertEquals(HydrologyFeatureType.MOUTH, outlet.type());
        assertFalse(terrain.sample(outlet.landwardPoint().x(), outlet.landwardPoint().z()).ocean());
        assertTrue(terrain.sample(outlet.connectionPoint().x(), outlet.connectionPoint().z()).ocean());
        assertEquals(1L, outlet.landwardPoint().distanceSquared2D(outlet.connectionPoint()));

        List<HydrologyPoint> centerline = rasterizedCourseCenterline(course);
        int firstOceanIndex = -1;
        int firstShoreContactIndex = -1;
        for (int index = 0; index < centerline.size(); index++) {
            HydrologyPoint point = centerline.get(index);
            if (terrain.sample(point.x(), point.z()).ocean()) {
                if (firstOceanIndex < 0) {
                    firstOceanIndex = index;
                }
            } else if (firstShoreContactIndex < 0 && hasCardinalOceanNeighbor(terrain, point)) {
                firstShoreContactIndex = index;
            }
        }
        assertEquals(centerline.size() - 1, firstOceanIndex);
        assertEquals(centerline.size() - 2, firstShoreContactIndex);
        assertEquals(outlet.landwardPoint().x(), centerline.get(centerline.size() - 2).x());
        assertEquals(outlet.landwardPoint().z(), centerline.get(centerline.size() - 2).z());
        assertEquals(outlet.connectionPoint().x(), centerline.getLast().x());
        assertEquals(outlet.connectionPoint().z(), centerline.getLast().z());

        HydraulicSegment mouth = course.segments().getLast();
        assertEquals(HydrologyFeatureType.MOUTH, mouth.type());
        List<HydrologyPoint> mouthCenterline = rasterizedSegmentCenterline(mouth);
        HashSet<Long> coastChunks = new HashSet<>();
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            if (column.ocean() || hasCardinalOceanNeighbor(
                    terrain,
                    new HydrologyPoint(column.x(), column.naturalHeight(), column.z())
            )) {
                coastChunks.add(pack(Math.floorDiv(column.x(), 16), Math.floorDiv(column.z(), 16)));
            }
            if (!column.ocean()) {
                continue;
            }
            for (HydrologyColumnLayer layer : column.layers()) {
                if (layer.feature().courseId() != course.id()) {
                    continue;
                }
                assertTrue(layer.oceanApron());
                assertEquals(HydrologyFeatureType.MOUTH, layer.feature().type());
                assertFalse(layer.terrainOwned());
                assertFalse(layer.fluidOwned());
                assertFalse(layer.grading());
                assertFalse(layer.shore());
                assertTrue(minimumDistance(column.x(), column.z(), mouthCenterline)
                        <= settings.outlets().maximumOceanApron() + 0.25D);
            }
        }
        assertFalse(coastChunks.isEmpty());
        for (long packedChunk : coastChunks) {
            int chunkX = (int) (packedChunk >> 32);
            int chunkZ = (int) packedChunk;
            MantleHydrologyComponent.Publication publication = MantleHydrologyComponent.compilePublication(
                    chunkX,
                    chunkZ,
                    192,
                    tile::columnAt,
                    tile.cavePlans(),
                    new TestCaveVoxelView()
            );
            for (CavePosition position : publication.surfaceWrites().keySet()) {
                assertFalse(terrain.sample(position.x(), position.z()).ocean());
            }
        }
    }

    @Test
    public void rendererClassificationMatchesMaterializedPublicationAcrossHydrologyClasses() {
        HydrologyColumnLayer surface = layer(
                HydrologyFeatureType.SURFACE_POOL,
                101L,
                101L,
                60,
                63,
                63,
                false,
                false,
                "river"
        );
        HydrologyColumnLayer drop = layer(
                HydrologyFeatureType.WATERFALL,
                102L,
                102L,
                52,
                63,
                63,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer underground = layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                103L,
                103L,
                20,
                23,
                28,
                false,
                false,
                "river"
        );
        HydrologyColumnLayer grotto = layer(
                HydrologyFeatureType.INLAND_GROTTO,
                104L,
                104L,
                18,
                22,
                29,
                false,
                false,
                "river"
        );
        HydrologyColumnLayer deep = layer(
                HydrologyFeatureType.DEEP_CHANNEL,
                105L,
                105L,
                6,
                9,
                13,
                false,
                false,
                "deep_lava"
        );
        List<PublicationParityCase> cases = List.of(
                surfacePublicationParityCase(surface, HydrologyCaveAction.WET_SOURCE),
                surfacePublicationParityCase(drop, HydrologyCaveAction.FALLING_FLUID),
                caveParityCase(underground),
                caveParityCase(grotto),
                caveParityCase(deep)
        );

        for (PublicationParityCase parityCase : cases) {
            MantleHydrologyComponent.Publication publication = compile(
                    samples(parityCase.sample()),
                    parityCase.plans(),
                    new TestCaveVoxelView()
            );
            BufferedImage rendered = renderRiverColumn(parityCase.sample());
            assertEquals(
                    parityCase.type().name(),
                    IrisRenderer.hydrologyFeatureColor(parityCase.type()),
                    rendered.getRGB(0, 0)
            );
            switch (parityCase.target()) {
                case TERRAIN -> assertTerrainMaterialization(parityCase, publication);
                case SURFACE_PUBLICATION -> assertSurfacePublication(parityCase, publication);
                case CAVE_PUBLICATION -> assertCavePublication(parityCase, publication);
            }
        }
    }

    @Test
    public void acceptedSurfaceDropAndReceiverPublishTheirGuardsWithoutOwningTheRemainingRiver() {
        for (boolean falling : new boolean[]{true, false}) {
            HydrologyColumnLayer drop = new HydrologyColumnLayer(
                    feature(HydrologyFeatureType.WATERFALL, 71L, 14L, 80), 70, 80, 80,
                    true, false, false, true, falling, !falling, !falling, true, false,
                    "river", "surface", "mouth", "shore", "dry", "flooded");
            HydrologyColumnLayer exposed = layer(HydrologyFeatureType.SURFACE_POOL, 72L, 15L,
                    76, 80, 80, false, false, "river");
            HydrologyColumnSample throat = sample(8, 8, false, 90, List.of(drop));
            HydrologyColumnSample downstream = sample(12, 8, false, 90, List.of(exposed));
            HydrologyCavePlan plan = acceptedPlan(throat, drop);

            MantleHydrologyComponent.Publication publication = compile(samples(throat, downstream),
                    List.of(plan), new TestCaveVoxelView());

            assertEquals(HydrologyCaveAction.SEAL_GUARD,
                    publication.caveCells().get(new CavePosition(9, 72, 8)).action());
            assertEquals(falling ? HydrologyCaveAction.FALLING_FLUID : HydrologyCaveAction.WET_SOURCE,
                    publication.caveCells().get(new CavePosition(8, 75, 8)).action());
            assertEquals(HydrologyCaveAction.WET_SOURCE,
                    publication.surfaceWrites().get(new CavePosition(12, 78, 8)).action());
            assertFalse(publication.caveCells().containsKey(new CavePosition(12, 78, 8)));
        }
    }

    @Test
    public void acceptedExactPlanPublishesWetDryAndGuardActions() {
        HydrologyColumnLayer pool = layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                21L,
                8L,
                20,
                23,
                30,
                false,
                false,
                "river"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        HydrologyCavePlan plan = acceptedPlan(sample, pool);

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(plan),
                new TestCaveVoxelView()
        );

        assertEquals(13, publication.caveCells().size());
        for (int y = 21; y <= 23; y++) {
            HydrologyCaveCell cell = publication.caveCells().get(new CavePosition(8, y, 8));
            assertEquals(HydrologyCaveAction.WET_SOURCE, cell.action());
            assertEquals("river", cell.fluidProfileKey());
            assertEquals("flooded", cell.floodedBiomeKey());
        }
        for (int y = 24; y <= 30; y++) {
            HydrologyCaveCell cell = publication.caveCells().get(new CavePosition(8, y, 8));
            assertEquals(HydrologyCaveAction.DRY_AIR, cell.action());
            assertEquals("flooded", cell.floodedBiomeKey());
        }
        assertEquals(HydrologyCaveAction.SEAL_GUARD, publication.caveCells()
                .get(new CavePosition(8, 20, 8)).action());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, publication.caveCells()
                .get(new CavePosition(8, 31, 8)).action());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, publication.caveCells()
                .get(new CavePosition(9, 22, 8)).action());
    }

    @Test
    public void omittedHistoricalHaloVolumeDoesNotPublishItsGuardInNewChunk() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample historical = sample(-1, 8, false, 80, List.of(pool));
        HydrologyColumnSample current = sample(8, 8, false, 80, List.of());

        MantleHydrologyComponent.Publication publication = compile(
                samples(current),
                List.of(acceptedPlan(historical, pool)),
                new TestCaveVoxelView()
        );

        assertEmpty(publication);
    }

    @Test
    public void includedHaloVolumePublishesOnlyItsGuardInsideTargetChunk() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample neighbor = sample(-1, 8, false, 80, List.of(pool));

        MantleHydrologyComponent.Publication publication = compile(
                samples(neighbor),
                List.of(acceptedPlan(neighbor, pool)),
                new TestCaveVoxelView()
        );

        assertEquals(1, publication.caveCells().size());
        assertEquals(HydrologyCaveAction.SEAL_GUARD,
                publication.caveCells().get(new CavePosition(0, 22, 8)).action());
        assertTrue(publication.surfaceWrites().isEmpty());
        assertTrue(publication.fluidUpdates().isEmpty());
    }

    @Test
    public void includedCaveColumnStillRejectsGuardWithoutPublishedOwner() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        HydrologyCavePlan plan = acceptedPlan(sample, pool);
        CavePosition extraVolume = new CavePosition(8, 35, 8);
        CavePosition extraGuard = new CavePosition(9, 35, 8);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>(plan.actions());
        actions.put(extraVolume, HydrologyCaveAction.WET_SOURCE);
        actions.put(extraGuard, HydrologyCaveAction.SEAL_GUARD);
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions =
                new LinkedHashMap<>(plan.baselinePreconditions());
        preconditions.put(extraVolume, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        preconditions.put(extraGuard, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        HydrologyCavePlan malformed = new HydrologyCavePlan(plan.source(), plan.rejection(),
                actions, preconditions, plan.arbitrationWinnerSourceId());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> compile(samples(sample), List.of(malformed), new TestCaveVoxelView()));

        assertTrue(failure.getMessage().contains("guard has no adjacent cave volume"));
        assertTrue(failure.getMessage().contains(extraGuard.toString()));
    }

    @Test
    public void includedCaveLayerStillRejectsMissingAcceptedAction() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        HydrologyCavePlan plan = acceptedPlan(sample, pool);
        CavePosition missing = new CavePosition(8, 25, 8);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>(plan.actions());
        actions.remove(missing);
        HydrologyCavePlan malformed = new HydrologyCavePlan(plan.source(), plan.rejection(),
                actions, plan.baselinePreconditions(), plan.arbitrationWinnerSourceId());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> compile(samples(sample), List.of(malformed), new TestCaveVoxelView()));

        assertTrue(failure.getMessage().contains("layer is absent from its containment plan"));
        assertTrue(failure.getMessage().contains(missing.toString()));
    }

    @Test
    public void lavaPreconditionMismatchPublishesNothing() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tracked = new CavePosition(8, 21, 8);
        HydrologyCavePlan plan = acceptedPlan(sample, pool);
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(tracked, CaveVoxel.LAVA);

        assertEmpty(compile(samples(sample), List.of(plan), view));
    }

    @Test
    public void incompatibleFluidPreconditionMismatchPublishesNothing() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tracked = new CavePosition(8, 21, 8);
        HydrologyCavePlan plan = acceptedPlan(sample, pool);
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(tracked, CaveVoxel.INCOMPATIBLE_FLUID);

        assertEmpty(compile(samples(sample), List.of(plan), view));
    }

    @Test
    public void newlyOpenSurfacePreconditionPublishesNothing() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tracked = new CavePosition(8, 24, 8);
        HydrologyCavePlan plan = replacePrecondition(
                acceptedPlan(sample, pool),
                tracked,
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false)
        );
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(tracked, CaveVoxel.CAVE_AIR);
        view.setOpenToSurface(tracked);

        assertEmpty(compile(samples(sample), List.of(plan), view));
    }

    @Test
    public void changedSolidPreconditionPublishesNothing() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tracked = new CavePosition(8, 25, 8);
        HydrologyCavePlan plan = acceptedPlan(sample, pool);
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(tracked, CaveVoxel.CAVE_AIR);

        assertEmpty(compile(samples(sample), List.of(plan), view));
    }

    @Test
    public void sameCourseSurfaceFluidOwnsAChangedCaveBoundary() {
        long courseId = 33L;
        HydrologyColumnLayer ridge = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                34L,
                courseId,
                10L,
                20,
                25,
                26,
                "river"
        );
        HydrologyColumnLayer connectedSurface = layerForCourse(
                HydrologyFeatureType.SURFACE_POOL,
                35L,
                courseId,
                11L,
                20,
                23,
                23,
                "river"
        );
        HydrologyColumnLayer selectedSurface = layerForCourse(
                HydrologyFeatureType.SURFACE_POOL,
                36L,
                99L,
                12L,
                23,
                25,
                25,
                "river"
        );
        HydrologyColumnSample sample = sample(
                8,
                8,
                false,
                80,
                List.of(ridge, connectedSurface, selectedSurface)
        );
        CavePosition tracked = new CavePosition(8, 23, 8);
        HydrologyCavePlan plan = replacePrecondition(
                acceptedPlan(sample, ridge),
                tracked,
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true)
        );

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(plan),
                new TestCaveVoxelView()
        );

        assertEquals(HydrologyCaveAction.SEAL_GUARD, publication.caveCells().get(tracked).action());
        assertEquals(
                HydrologyCaveAction.WET_SOURCE,
                publication.caveCells().get(new CavePosition(8, 24, 8)).action()
        );
    }

    @Test
    public void newlySolidOpenBoundaryStillPublishesContainedCaveVolume() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tightenedBoundary = new CavePosition(9, 23, 8);
        HydrologyCavePlan plan = replacePrecondition(
                acceptedPlan(sample, pool),
                tightenedBoundary,
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true)
        );

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(plan),
                new TestCaveVoxelView()
        );

        assertEquals(
                HydrologyCaveAction.WET_SOURCE,
                publication.caveCells().get(new CavePosition(8, 21, 8)).action()
        );
    }

    @Test
    public void newlyClosedWetCaveAirStillPublishesContainedFluid() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tracked = new CavePosition(8, 21, 8);
        HydrologyCavePlan plan = replacePrecondition(
                acceptedPlan(sample, pool),
                tracked,
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true)
        );
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(tracked, CaveVoxel.CAVE_AIR);

        MantleHydrologyComponent.Publication publication = compile(samples(sample), List.of(plan), view);

        assertEquals(HydrologyCaveAction.WET_SOURCE, publication.caveCells().get(tracked).action());
    }

    @Test
    public void newlyClosedDryOpeningPublishesNothing() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition tracked = new CavePosition(8, 24, 8);
        HydrologyCavePlan plan = replacePrecondition(
                acceptedPlan(sample, pool),
                tracked,
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true)
        );
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(tracked, CaveVoxel.CAVE_AIR);

        assertEmpty(compile(samples(sample), List.of(plan), view));
    }

    @Test
    public void remoteCrossChunkPreconditionMismatchDoesNotSuppressLocalPublication() {
        HydrologyColumnLayer pool = cavePool();
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(pool));
        CavePosition remote = new CavePosition(16, 22, 8);
        HydrologyCavePlan plan = replacePrecondition(
                acceptedPlan(sample, pool),
                remote,
                new CaveVoxelPrecondition(CaveVoxel.SOLID, false)
        );
        TestCaveVoxelView view = new TestCaveVoxelView();
        view.setVoxel(remote, CaveVoxel.CAVE_AIR);

        MantleHydrologyComponent.Publication publication = compile(samples(sample), List.of(plan), view);

        assertEquals(
                HydrologyCaveAction.WET_SOURCE,
                publication.caveCells().get(new CavePosition(8, 21, 8)).action()
        );
    }

    @Test
    public void deepFluidPublishesItsAcceptedProfileAndExactVolume() {
        HydrologyColumnLayer deep = layer(
                HydrologyFeatureType.DEEP_CHANNEL,
                61L,
                12L,
                8,
                10,
                14,
                false,
                false,
                "deep_lava"
        );
        HydrologyColumnSample sample = sample(7, 7, false, 70, List.of(deep));

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(acceptedPlan(sample, deep)),
                new TestCaveVoxelView()
        );

        for (int y = 9; y <= 14; y++) {
            HydrologyCaveCell cell = publication.caveCells().get(new CavePosition(7, y, 7));
            assertEquals("deep_lava", cell.fluidProfileKey());
        }
        assertEquals(HydrologyCaveAction.WET_SOURCE, publication.caveCells()
                .get(new CavePosition(7, 10, 7)).action());
        assertEquals(HydrologyCaveAction.DRY_AIR, publication.caveCells()
                .get(new CavePosition(7, 11, 7)).action());
    }

    @Test
    public void incompatibleAcceptedCaveActionsCannotMergeAtOneVoxel() {
        HydrologyColumnLayer higherHead = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                101L,
                101L,
                1L,
                20,
                23,
                25,
                "river"
        );
        HydrologyColumnLayer lowerHead = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                102L,
                102L,
                1L,
                20,
                22,
                25,
                "river"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(higherHead, lowerHead));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> compile(
                        samples(sample),
                        List.of(acceptedPlan(sample, higherHead), acceptedPlan(sample, lowerHead)),
                        new TestCaveVoxelView()
                )
        );

        assertOverlapFailure(failure, 101L, 102L, new CavePosition(8, 23, 8));
    }

    @Test
    public void overlappingLayersConsumeTheirCoursesComposedContainmentAction() {
        HydrologyColumnLayer higherHead = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_DROP,
                111L,
                111L,
                1L,
                20,
                23,
                25,
                "river"
        );
        HydrologyColumnLayer lowerHead = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_DROP,
                112L,
                111L,
                2L,
                20,
                22,
                25,
                "river"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(higherHead, lowerHead));

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(acceptedPlan(sample, higherHead)),
                new TestCaveVoxelView()
        );

        assertEquals(HydrologyCaveAction.WET_SOURCE, publication.caveCells()
                .get(new CavePosition(8, 23, 8)).action());
    }

    @Test
    public void incompatibleAcceptedCaveProfilesCannotMergeAtOneVoxel() {
        HydrologyColumnLayer river = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                201L,
                201L,
                1L,
                20,
                23,
                25,
                "river"
        );
        HydrologyColumnLayer lava = layerForCourse(
                HydrologyFeatureType.DEEP_POOL,
                202L,
                202L,
                1L,
                20,
                23,
                25,
                "deep_lava"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(river, lava));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> compile(
                        samples(sample),
                        List.of(acceptedPlan(sample, river), acceptedPlan(sample, lava)),
                        new TestCaveVoxelView()
                )
        );

        assertOverlapFailure(failure, 201L, 202L, new CavePosition(8, 21, 8));
    }

    @Test
    public void incompatibleAcceptedGuardCannotHideAnotherCourseVolume() {
        HydrologyColumnLayer guarded = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                301L,
                301L,
                1L,
                20,
                23,
                25,
                "river"
        );
        HydrologyColumnLayer occupying = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                302L,
                302L,
                1L,
                20,
                23,
                25,
                "river"
        );
        HydrologyColumnSample guardedSample = sample(8, 8, false, 80, List.of(guarded));
        HydrologyColumnSample occupyingSample = sample(9, 8, false, 80, List.of(occupying));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> compile(
                        samples(guardedSample, occupyingSample),
                        List.of(
                                acceptedPlan(guardedSample, guarded),
                                acceptedPlan(occupyingSample, occupying)
                        ),
                        new TestCaveVoxelView()
                )
        );

        assertOverlapFailure(failure, 301L, 302L, new CavePosition(9, 22, 8));
    }

    @Test
    public void compatibleAcceptedCaveCoursesMayShareVoxels() {
        HydrologyColumnLayer first = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                401L,
                401L,
                1L,
                20,
                22,
                24,
                "river"
        );
        HydrologyColumnLayer second = layerForCourse(
                HydrologyFeatureType.UNDERGROUND_POOL,
                402L,
                402L,
                1L,
                20,
                22,
                24,
                "river"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(first, second));

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(acceptedPlan(sample, first), acceptedPlan(sample, second)),
                new TestCaveVoxelView()
        );

        assertEquals(7, publication.caveCells().size());
        assertEquals(
                HydrologyCaveAction.WET_SOURCE,
                publication.caveCells().get(new CavePosition(8, 22, 8)).action()
        );
        assertEquals(
                HydrologyCaveAction.SEAL_GUARD,
                publication.caveCells().get(new CavePosition(9, 22, 8)).action()
        );
    }

    @Test
    public void matchingSurfaceFluidComposesOverAcceptedCaveClearance() {
        HydrologyColumnLayer surface = layer(
                HydrologyFeatureType.WATERFALL,
                71L,
                14L,
                20,
                23,
                23,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer cave = layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                72L,
                15L,
                20,
                23,
                28,
                false,
                false,
                "river"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(surface, cave));

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(acceptedPlan(sample, cave)),
                new TestCaveVoxelView()
        );

        assertTrue(publication.surfaceWrites().isEmpty());
        assertEquals(
                HydrologyCaveAction.FALLING_FLUID,
                publication.caveCells().get(new CavePosition(8, 21, 8)).action()
        );
        assertEquals(
                HydrologyCaveAction.WET_SOURCE,
                publication.caveCells().get(new CavePosition(8, 23, 8)).action()
        );
    }

    @Test
    public void differentSurfaceAndCaveFluidsCannotOverlap() {
        HydrologyColumnLayer surface = layer(
                HydrologyFeatureType.WATERFALL,
                71L,
                14L,
                20,
                23,
                23,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer cave = layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                72L,
                15L,
                20,
                23,
                28,
                false,
                false,
                "lava"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(surface, cave));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> compile(samples(sample), List.of(acceptedPlan(sample, cave)), new TestCaveVoxelView())
        );

        assertTrue(failure.getMessage().contains(new CavePosition(8, 21, 8).toString()));
    }

    @Test
    public void composedCavePlanPublishesTheExactOverlappingSurfaceAction() {
        HydrologyColumnLayer surface = layer(
                HydrologyFeatureType.WATERFALL,
                73L,
                16L,
                20,
                23,
                23,
                true,
                false,
                "river"
        );
        HydrologyColumnLayer cave = layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                74L,
                17L,
                20,
                23,
                28,
                false,
                false,
                "river"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(surface, cave));
        HydrologyCavePlan basePlan = acceptedPlan(sample, cave);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>(basePlan.actions());
        for (int y = surface.bedY() + 1; y <= surface.fluidHeadY(); y++) {
            HydrologyColumnSample.SurfacePublicationCell cell = sample.surfacePublicationCellAt(y).orElseThrow();
            actions.put(new CavePosition(sample.x(), y, sample.z()), cell.action());
        }
        HydrologyCavePlan composedPlan = new HydrologyCavePlan(
                basePlan.source(),
                basePlan.rejection(),
                actions,
                basePlan.baselinePreconditions(),
                basePlan.arbitrationWinnerSourceId()
        );

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(composedPlan),
                new TestCaveVoxelView()
        );

        assertTrue(publication.surfaceWrites().isEmpty());
        assertEquals(
                HydrologyCaveAction.FALLING_FLUID,
                publication.caveCells().get(new CavePosition(8, 21, 8)).action()
        );
        assertFalse(publication.fluidUpdates().contains(new CavePosition(8, 21, 8)));
    }

    @Test
    public void surfaceBedGuardOverridesAnOverlappingAcceptedCaveFluidCell() {
        HydrologyColumnLayer surface = layer(
                HydrologyFeatureType.SURFACE_POOL,
                75L,
                18L,
                20,
                23,
                23,
                false,
                false,
                "lava"
        );
        HydrologyColumnLayer cave = layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                76L,
                19L,
                17,
                23,
                28,
                false,
                false,
                "lava"
        );
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(surface, cave));

        MantleHydrologyComponent.Publication publication = compile(
                samples(sample),
                List.of(acceptedPlan(sample, cave)),
                new TestCaveVoxelView()
        );

        assertEquals(20, sample.terrainHeight());
        for (int y = 18; y <= 20; y++) {
            assertEquals(
                    HydrologyCaveAction.SEAL_GUARD,
                    publication.caveCells().get(new CavePosition(8, y, 8)).action()
            );
        }
        assertEquals(
                HydrologyCaveAction.WET_SOURCE,
                publication.caveCells().get(new CavePosition(8, 21, 8)).action()
        );
    }

    private List<HydrologyPoint> rasterizedCourseCenterline(RiverCourse course) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (HydraulicSegment segment : course.segments()) {
            for (HydrologyPoint point : rasterizedSegmentCenterline(segment)) {
                HydrologyPoint previous = points.isEmpty() ? null : points.getLast();
                if (previous == null || previous.x() != point.x() || previous.z() != point.z()) {
                    points.add(point);
                }
            }
        }
        return List.copyOf(points);
    }

    private List<HydrologyPoint> rasterizedSegmentCenterline(HydraulicSegment segment) {
        List<HydrologyPoint> configured = segment.centerline();
        if (configured.size() == 1) {
            return configured;
        }
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int pairIndex = 0; pairIndex < configured.size() - 1; pairIndex++) {
            HydrologyPoint start = configured.get(pairIndex);
            HydrologyPoint end = configured.get(pairIndex + 1);
            int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
            if (steps == 0) {
                if (points.isEmpty()) {
                    points.add(start);
                }
                continue;
            }
            int firstStep = points.isEmpty() ? 0 : 1;
            for (int step = firstStep; step <= steps; step++) {
                double progress = step / (double) steps;
                HydrologyPoint point = new HydrologyPoint(
                        (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress),
                        (int) StrictMath.round(start.y() + (end.y() - start.y()) * progress),
                        (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress)
                );
                HydrologyPoint previous = points.isEmpty() ? null : points.getLast();
                if (previous == null || previous.x() != point.x() || previous.z() != point.z()) {
                    points.add(point);
                }
            }
        }
        return List.copyOf(points);
    }

    private boolean hasCardinalOceanNeighbor(HydrologyTerrainSampler terrain, HydrologyPoint point) {
        return terrain.sample(point.x() + 1, point.z()).ocean()
                || terrain.sample(point.x() - 1, point.z()).ocean()
                || terrain.sample(point.x(), point.z() + 1).ocean()
                || terrain.sample(point.x(), point.z() - 1).ocean();
    }

    private double minimumDistance(int x, int z, List<HydrologyPoint> centerline) {
        double minimum = Double.POSITIVE_INFINITY;
        for (HydrologyPoint point : centerline) {
            minimum = Math.min(minimum, StrictMath.hypot(x - point.x(), z - point.z()));
        }
        return minimum;
    }

    private Set<CavePosition> connectedFluidFrom(
            CavePosition source,
            Set<CavePosition> materializedFluid
    ) {
        assertTrue(materializedFluid.contains(source));
        ArrayDeque<CavePosition> pending = new ArrayDeque<>();
        HashSet<CavePosition> connected = new HashSet<>();
        pending.add(source);
        connected.add(source);
        while (!pending.isEmpty()) {
            CavePosition position = pending.removeFirst();
            addConnected(position.offset(1, 0, 0), materializedFluid, connected, pending);
            addConnected(position.offset(-1, 0, 0), materializedFluid, connected, pending);
            addConnected(position.offset(0, 1, 0), materializedFluid, connected, pending);
            addConnected(position.offset(0, -1, 0), materializedFluid, connected, pending);
            addConnected(position.offset(0, 0, 1), materializedFluid, connected, pending);
            addConnected(position.offset(0, 0, -1), materializedFluid, connected, pending);
        }
        return Set.copyOf(connected);
    }

    private MantleHydrologyComponent.Publication publicationAt(
            Map<Long, MantleHydrologyComponent.Publication> publications,
            int x,
            int z
    ) {
        MantleHydrologyComponent.Publication publication = publications.get(
                pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
        );
        assertNotNull(publication);
        return publication;
    }

    private void addConnected(
            CavePosition position,
            Set<CavePosition> materializedFluid,
            Set<CavePosition> connected,
            ArrayDeque<CavePosition> pending
    ) {
        if (materializedFluid.contains(position) && connected.add(position)) {
            pending.addLast(position);
        }
    }

    private PublicationParityCase terrainParityCase(HydrologyColumnLayer layer) {
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(layer));
        return new PublicationParityCase(
                layer.feature().type(),
                sample,
                List.of(),
                new CavePosition(sample.x(), layer.fluidHeadY(), sample.z()),
                HydrologyCaveAction.WET_SOURCE,
                PublicationTarget.TERRAIN
        );
    }

    private PublicationParityCase surfacePublicationParityCase(
            HydrologyColumnLayer layer,
            HydrologyCaveAction expectedAction
    ) {
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(layer));
        int probeY = expectedAction == HydrologyCaveAction.FALLING_FLUID
                ? layer.fluidHeadY() - 1
                : layer.fluidHeadY();
        return new PublicationParityCase(
                layer.feature().type(),
                sample,
                List.of(),
                new CavePosition(sample.x(), probeY, sample.z()),
                expectedAction,
                PublicationTarget.SURFACE_PUBLICATION
        );
    }

    private PublicationParityCase caveParityCase(HydrologyColumnLayer layer) {
        HydrologyColumnSample sample = sample(8, 8, false, 80, List.of(layer));
        return new PublicationParityCase(
                layer.feature().type(),
                sample,
                List.of(acceptedPlan(sample, layer)),
                new CavePosition(sample.x(), layer.fluidHeadY(), sample.z()),
                HydrologyCaveAction.WET_SOURCE,
                PublicationTarget.CAVE_PUBLICATION
        );
    }

    private void assertTerrainMaterialization(
            PublicationParityCase parityCase,
            MantleHydrologyComponent.Publication publication
    ) {
        HydrologyColumnLayer selected = parityCase.sample().primarySurfaceFluidLayer().orElseThrow();
        assertEquals(parityCase.type().name(), parityCase.type(), selected.feature().type());
        assertEquals(selected.bedY(), parityCase.sample().terrainHeight());
        assertTrue(parityCase.probe().y() > selected.bedY());
        assertTrue(parityCase.probe().y() <= selected.fluidHeadY());
        assertFalse(publication.surfaceWrites().containsKey(parityCase.probe()));
        assertFalse(publication.caveCells().containsKey(parityCase.probe()));
    }

    private void assertSurfacePublication(
            PublicationParityCase parityCase,
            MantleHydrologyComponent.Publication publication
    ) {
        MantleHydrologyComponent.SurfaceFluidWrite write = publication.surfaceWrites().get(parityCase.probe());
        assertNotNull(parityCase.type().name(), write);
        assertEquals(parityCase.type().name(), parityCase.expectedAction(), write.action());
        assertFalse(publication.caveCells().containsKey(parityCase.probe()));
    }

    private void assertCavePublication(
            PublicationParityCase parityCase,
            MantleHydrologyComponent.Publication publication
    ) {
        HydrologyCaveCell cell = publication.caveCells().get(parityCase.probe());
        assertNotNull(parityCase.type().name(), cell);
        assertEquals(parityCase.type().name(), parityCase.expectedAction(), cell.action());
        assertFalse(publication.surfaceWrites().containsKey(parityCase.probe()));
    }

    private BufferedImage renderRiverColumn(HydrologyColumnSample sample) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.sampleRenderFootprint(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(sample.renderSample());
        when(runtime.sampleDiagnosticFootprint(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new HydrologyDiagnosticRenderSample(sample.x(), sample.z(), List.of()));
        return new IrisRenderer(engine).renderStudio(
                sample.x(),
                sample.z(),
                1D,
                1,
                RenderType.RIVER,
                () -> false
        );
    }

    private MantleHydrologyComponent.Publication compile(
            Map<Long, HydrologyColumnSample> samples,
            List<HydrologyCavePlan> plans,
            CaveVoxelView caveView
    ) {
        return MantleHydrologyComponent.compilePublication(
                0,
                0,
                128,
                (x, z) -> Optional.ofNullable(samples.get(pack(x, z))),
                plans,
                caveView
        );
    }

    private static HydrologyTile plannedCascadeTile() {
        return CascadeTileHolder.TILE;
    }

    private static HydrologyTile createCascadeTile() {
        HydrologyTerrainSampler terrainSampler = (int x, int z) -> {
            if (x >= 112) {
                return cascadeTerrain(54, true, false, false);
            }
            int terrace = Math.max(0, Math.floorDiv(x, 32));
            return cascadeTerrain(120 - terrace * 2, false, x == 0 && z == 0, z == 0);
        };
        return new HydrologyPlanner(
                1441L,
                cascadeSettings(),
                terrainSampler,
                new TestCaveVoxelView()
        ).plan(new HydrologyTileKey(0, 0));
    }

    private static HydrologyPlannerSettings cascadeSettings() {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                1D,
                80,
                1,
                1,
                16
        );
        HydrologyPlannerSettings.Source disabledSources = new HydrologyPlannerSettings.Source(
                false,
                0D,
                Integer.MIN_VALUE,
                0,
                0,
                0
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 16, 8, 0.5D, 12D, 0.5D, 0.1D, 1D, 0, HydrologyPlannerSettings.Regional.disabled()),
                new HydrologyPlannerSettings.Surface(
                        true,
                        surfaceSources,
                        2,
                        2,
                        2,
                        2,
                        128,
                        0D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        false,
                        disabledSources,
                        64,
                        84,
                        2,
                        2,
                        1,
                        1,
                        2,
                        2,
                        false,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        new HydrologyPlannerSettings.Grotto(false, 2, 2, 2, 512),
                        false,
                        32,
                        16,
                        1,
                        8,
                        8
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private static HydrologyTerrainSample cascadeTerrain(
            int height,
            boolean ocean,
            boolean source,
            boolean transit
    ) {
        return new HydrologyTerrainSample(
                height,
                ocean ? 0D : 1D,
                ocean,
                !ocean,
                68,
                74,
                transit,
                transit,
                source,
                source,
                false,
                false,
                0D,
                source ? 1D : 0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                ocean ? "ocean" : "land",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("default"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private void assertEmpty(MantleHydrologyComponent.Publication publication) {
        assertTrue(publication.caveCells().isEmpty());
        assertTrue(publication.surfaceWrites().isEmpty());
        assertTrue(publication.fluidUpdates().isEmpty());
    }

    private void assertOverlapFailure(
            IllegalStateException failure,
            long firstCourseId,
            long secondCourseId,
            CavePosition position
    ) {
        assertTrue(failure.getMessage().contains(Long.toString(firstCourseId)));
        assertTrue(failure.getMessage().contains(Long.toString(secondCourseId)));
        assertTrue(failure.getMessage().contains(position.toString()));
    }

    private HydrologyCavePlan acceptedPlan(
            HydrologyColumnSample sample,
            HydrologyColumnLayer layer
    ) {
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        for (int y = layer.bedY() + 1; y <= layer.ceilingY(); y++) {
            HydrologyCaveAction action;
            if (y > layer.fluidHeadY()) {
                action = HydrologyCaveAction.DRY_AIR;
            } else if (layer.fallingFluid() && y < layer.fluidHeadY()) {
                action = HydrologyCaveAction.FALLING_FLUID;
            } else {
                action = HydrologyCaveAction.WET_SOURCE;
            }
            actions.put(new CavePosition(sample.x(), y, sample.z()), action);
        }
        actions.put(new CavePosition(sample.x(), layer.bedY(), sample.z()), HydrologyCaveAction.SEAL_GUARD);
        actions.put(new CavePosition(sample.x(), layer.ceilingY() + 1, sample.z()), HydrologyCaveAction.SEAL_GUARD);
        actions.put(new CavePosition(sample.x() + 1, layer.bedY() + 2, sample.z()), HydrologyCaveAction.SEAL_GUARD);

        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        for (CavePosition position : actions.keySet()) {
            preconditions.put(position, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        }
        HydrologyCaveSource source = new HydrologyCaveSource(
                layer.feature().courseId(),
                new CavePosition(sample.x(), layer.ceilingY(), sample.z()),
                new CavePosition(sample.x(), layer.bedY() + 1, sample.z()),
                layer.fluidHeadY(),
                HydrologyCaveMode.CLOSED_COMPONENT
        );
        return new HydrologyCavePlan(
                source,
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );
    }

    private HydrologyCavePlan replacePrecondition(
            HydrologyCavePlan plan,
            CavePosition position,
            CaveVoxelPrecondition precondition
    ) {
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>(
                plan.baselinePreconditions()
        );
        preconditions.put(position, precondition);
        return new HydrologyCavePlan(
                plan.source(),
                plan.rejection(),
                plan.actions(),
                preconditions,
                plan.arbitrationWinnerSourceId()
        );
    }

    private HydrologyColumnLayer cavePool() {
        return layer(
                HydrologyFeatureType.UNDERGROUND_POOL,
                31L,
                9L,
                20,
                23,
                30,
                false,
                false,
                "river"
        );
    }

    private Map<Long, HydrologyColumnSample> samples(HydrologyColumnSample... samples) {
        HashMap<Long, HydrologyColumnSample> indexed = new HashMap<>();
        for (HydrologyColumnSample sample : samples) {
            indexed.put(pack(sample.x(), sample.z()), sample);
        }
        return indexed;
    }

    private HydrologyColumnSample sample(
            int x,
            int z,
            boolean ocean,
            int naturalHeight,
            List<HydrologyColumnLayer> layers
    ) {
        return new HydrologyColumnSample(x, z, naturalHeight, 63, ocean, "parent", layers);
    }

    private HydrologyColumnLayer layer(
            HydrologyFeatureType type,
            long id,
            long segmentId,
            int bed,
            int head,
            int ceiling,
            boolean falling,
            boolean receiving,
            String profileKey
    ) {
        return layerForCourse(
                type,
                id,
                1L,
                segmentId,
                bed,
                head,
                ceiling,
                falling,
                receiving,
                profileKey
        );
    }

    private HydrologyColumnLayer layerForCourse(
            HydrologyFeatureType type,
            long id,
            long courseId,
            long segmentId,
            int bed,
            int head,
            int ceiling,
            String profileKey
    ) {
        return layerForCourse(
                type,
                id,
                courseId,
                segmentId,
                bed,
                head,
                ceiling,
                false,
                false,
                profileKey
        );
    }

    private HydrologyColumnLayer layerForCourse(
            HydrologyFeatureType type,
            long id,
            long courseId,
            long segmentId,
            int bed,
            int head,
            int ceiling,
            boolean falling,
            boolean receiving,
            String profileKey
    ) {
        return new HydrologyColumnLayer(
                feature(type, id, courseId, segmentId, head),
                bed,
                head,
                ceiling,
                true,
                false,
                false,
                true,
                falling,
                receiving,
                true,
                true,
                false,
                profileKey,
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded"
        );
    }

    private HydrologyFeatureRef feature(HydrologyFeatureType type, long id, long segmentId, int y) {
        return feature(type, id, 1L, segmentId, y);
    }

    private HydrologyFeatureRef feature(
            HydrologyFeatureType type,
            long id,
            long courseId,
            long segmentId,
            int y
    ) {
        return new HydrologyFeatureRef(id, type, courseId, segmentId, 8, y, 8, 1, 0, false);
    }

    private long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private record PublicationParityCase(
            HydrologyFeatureType type,
            HydrologyColumnSample sample,
            List<HydrologyCavePlan> plans,
            CavePosition probe,
            HydrologyCaveAction expectedAction,
            PublicationTarget target
    ) {
    }

    private enum PublicationTarget {
        TERRAIN,
        SURFACE_PUBLICATION,
        CAVE_PUBLICATION
    }

    private static final class CascadeTileHolder {
        private static final HydrologyTile TILE = createCascadeTile();
    }

    private static final class TestCaveVoxelView implements CaveVoxelView {
        private final Map<CavePosition, CaveVoxel> voxels = new HashMap<>();
        private final Set<CavePosition> openToSurface = new HashSet<>();

        @Override
        public boolean isInWorld(CavePosition position) {
            return position.y() > 0 && position.y() < 127;
        }

        @Override
        public CaveVoxel voxelAt(CavePosition position) {
            return voxels.getOrDefault(position, CaveVoxel.SOLID);
        }

        @Override
        public boolean isOpenToSurface(CavePosition position) {
            return openToSurface.contains(position);
        }

        @Override
        public boolean isAboveTerrainSurface(CavePosition position) {
            return false;
        }

        private void setVoxel(CavePosition position, CaveVoxel voxel) {
            voxels.put(position, voxel);
        }

        private void setOpenToSurface(CavePosition position) {
            openToSurface.add(position);
        }
    }
}
