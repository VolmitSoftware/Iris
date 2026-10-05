package art.arcane.iris.generation.subterrain;

import art.arcane.iris.pack.value.IrisRange;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SubterrainPlannerTest {
    @Test
    public void warpedFamiliesHaveAsymmetricSeededReliefAndRetainedFluids() {
        for (IrisSubterrainFamily family : IrisSubterrainFamily.values()) {
            IrisSubterrainFeature feature = feature(family).setShapeWarp(1).setRadius(16)
                    .setHeight(28).setLength(family == IrisSubterrainFamily.TECTONIC_FAULT ? 208 : 80)
                    .setChimneyHeight(0);
            SubterrainPlanner planner = planner(feature);
            for (int seed = 0; seed < 3; seed++) {
                SubterrainPlan plan = new SubterrainPlan(new SubterrainPlan.Options(
                        SubterrainPlanner.Definition.from(feature), 0, 0, 0, false, feature.getId() + ":" + seed));
                SubterrainPlan rotated = new SubterrainPlan(new SubterrainPlan.Options(
                        SubterrainPlanner.Definition.from(feature), 0, 0, 0, true, plan.id()));
                SubterrainPosition anchor = plan.anchor();
                assertTrue(plan.sample(anchor.x(), anchor.y(), anchor.z()).occupied());
                Set<Integer> floors = new HashSet<>();
                Set<Integer> ceilings = new HashSet<>();
                int asymmetric = 0;
                SubterrainBounds bounds = plan.bounds();
                for (int x = bounds.minX(); x <= bounds.maxX(); x += 2) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 2) {
                        for (int y = bounds.minY(); y <= bounds.maxY(); y += 2) {
                            SubterrainCell cell = plan.sample(x, y, z);
                            assertEquals(cell.kind(), rotated.sample(z, y, x).kind());
                            if (cell.occupied()) {
                                floors.add(cell.room().floorY());
                                ceilings.add(cell.room().ceilingY());
                                assertTrue(x > bounds.minX() && x < bounds.maxX());
                                assertTrue(z > bounds.minZ() && z < bounds.maxZ());
                                assertTrue(y > bounds.minY() && y < bounds.maxY());
                            }
                            if (cell.kind() != plan.sample(-x, y, -z).kind()) {
                                asymmetric++;
                            }
                        }
                    }
                }
                assertTrue(family + " floors", floors.size() >= 3);
                assertTrue(family + " ceilings", ceilings.size() >= 6);
                assertTrue(family + " asymmetry", asymmetric > 100);
                if (family != IrisSubterrainFamily.TECTONIC_FAULT) {
                    assertTrue(family + " retained fluid", assertRetained(planner, List.of(plan),
                            family == IrisSubterrainFamily.LAVA_TUBE ? SubterrainCell.Kind.LAVA : SubterrainCell.Kind.WATER) > 30);
                }
            }
        }
    }

    @Test
    public void shapeWarpRejectsInvalidStrengthsAndSnapshotsConfiguration() {
        for (double invalid : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> planner(feature(IrisSubterrainFamily.CENOTE).setShapeWarp(invalid)));
        }
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE).setShapeWarp(0.9);
        SubterrainPlanner.Definition definition = SubterrainPlanner.Definition.from(feature);
        feature.setShapeWarp(0.2);
        assertEquals(0.9, definition.shapeWarp(), 0);
    }

    @Test
    public void serializedWarpedDefinitionRestoresTheSamePlan() {
        Gson gson = new Gson();
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE).setShapeWarp(0.93);
        String saved = gson.toJson(feature);
        IrisSubterrainFeature restored = gson.fromJson(saved, IrisSubterrainFeature.class);
        assertEquals(0.93, restored.getShapeWarp(), 0);
        assertEquals(SubterrainPlanner.Definition.from(feature), SubterrainPlanner.Definition.from(restored));
        SubterrainPlan before = direct(feature);
        feature.setShapeWarp(0);
        SubterrainPlan after = direct(restored);
        assertEquals(before.anchor(), after.anchor());
        SubterrainBounds bounds = before.bounds();
        for (int x = bounds.minX(); x <= bounds.maxX(); x += 3) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 3) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y += 3) {
                    assertEquals(before.sample(x, y, z), after.sample(x, y, z));
                }
            }
        }
    }

    @Test
    public void terraceAnchorsUseOccupiedPathsWhenCenterIsPillarOrUpperStep() {
        for (IrisSubterrainFluid fluid : IrisSubterrainFluid.values()) {
            IrisSubterrainFeature feature = compactFeature(IrisSubterrainFamily.TRAVERTINE_TERRACES).setFluid(fluid);
            SubterrainPlan pillar = direct(feature);
            SubterrainPosition pillarAnchor = pillar.anchor();
            assertTrue(pillar.sample(pillarAnchor.x(), pillarAnchor.y(), pillarAnchor.z()).occupied());
            assertTrue(pillar.sample(pillarAnchor.x(), pillarAnchor.y(), pillarAnchor.z()).room().reservedPassage());
            feature.setHeight(8).setFluidDepth(2).setTerraceCount(16);
            for (boolean rotated : List.of(false, true)) {
                SubterrainPlan lowVault = new SubterrainPlan(new SubterrainPlan.Options(
                        SubterrainPlanner.Definition.from(feature), 0, 0, 0, rotated, "terrace:low-vault"));
                SubterrainPosition anchor = lowVault.anchor();
                SubterrainCell cell = lowVault.sample(anchor.x(), anchor.y(), anchor.z());
                assertTrue(cell.occupied());
                assertTrue(cell.room().reservedPassage());
                assertTrue((rotated ? anchor.z() : anchor.x()) < 0);
                assertEquals(anchor, lowVault.anchor());
            }
        }
    }

    @Test
    public void explicitFluidChangesOnlyLiquidKindAndMaterialForEveryWetFamily() {
        for (IrisSubterrainFamily family : List.of(IrisSubterrainFamily.CENOTE,
                IrisSubterrainFamily.LAVA_TUBE, IrisSubterrainFamily.TRAVERTINE_TERRACES)) {
            IrisSubterrainFeature water = compactFeature(family).setFluid(IrisSubterrainFluid.WATER);
            IrisSubterrainFeature lava = compactFeature(family).setFluid(IrisSubterrainFluid.LAVA);
            SubterrainPlanner waterPlanner = planner(water);
            SubterrainPlanner lavaPlanner = planner(lava);
            SubterrainPlan waterPlaced = first(waterPlanner);
            SubterrainPlan lavaPlaced = first(lavaPlanner);
            assertEquals(waterPlaced.id(), lavaPlaced.id());
            assertEquals(waterPlaced.bounds(), lavaPlaced.bounds());
            assertEquals(waterPlaced.anchor(), lavaPlaced.anchor());
            SubterrainPlan waterPlan = direct(water);
            SubterrainPlan lavaPlan = direct(lava);
            SubterrainBounds bounds = waterPlan.bounds();
            int fluidCells = 0;
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        SubterrainCell original = waterPlanner.sample(List.of(waterPlan), x, y, z);
                        SubterrainCell changed = lavaPlanner.sample(List.of(lavaPlan), x, y, z);
                        if (original.fluid()) {
                            fluidCells++;
                            assertEquals(SubterrainCell.Kind.WATER, original.kind());
                            assertEquals("minecraft:water", original.material());
                            assertEquals(SubterrainCell.Kind.LAVA, changed.kind());
                            assertEquals("minecraft:lava", changed.material());
                            SubterrainRoom room = original.room();
                            assertEquals(new SubterrainRoom(room.featureId(), room.biome(), room.family(),
                                    room.centerX(), room.centerY(), room.centerZ(), room.pathX(), room.pathY(), room.pathZ(),
                                    room.floorY(), room.ceilingY(), room.boundaryDistance(), room.fluidHeadY(),
                                    room.reservedPassage(), room.reservedSolid(), SubterrainCell.Kind.LAVA), changed.room());
                        } else {
                            assertEquals(original, changed);
                        }
                    }
                }
            }
            assertTrue(family.toString(), fluidCells > 100);
            assertEquals(assertRetained(waterPlanner, List.of(waterPlan), SubterrainCell.Kind.WATER),
                    assertRetained(lavaPlanner, List.of(lavaPlan), SubterrainCell.Kind.LAVA));
        }
    }

    @Test
    public void omittedAndNullFluidsPreserveFamilyDefaults() {
        Gson gson = new Gson();
        for (IrisSubterrainFamily family : IrisSubterrainFamily.values()) {
            IrisSubterrainFluid expected = family == IrisSubterrainFamily.LAVA_TUBE
                    ? IrisSubterrainFluid.LAVA : IrisSubterrainFluid.WATER;
            IrisSubterrainFeature omitted = gson.fromJson("{\"family\":\"" + family + "\"}", IrisSubterrainFeature.class);
            IrisSubterrainFeature nullable = gson.fromJson("{\"family\":\"" + family + "\",\"fluid\":null}", IrisSubterrainFeature.class);
            assertNull(omitted.getFluid());
            assertNull(nullable.getFluid());
            omitted.setId("default");
            nullable.setId("default");
            assertEquals(expected, SubterrainPlanner.Definition.from(omitted).fluid());
            assertEquals(SubterrainPlanner.Definition.from(omitted), SubterrainPlanner.Definition.from(nullable));
            SubterrainPlan implicit = direct(feature(family));
            SubterrainPlan explicit = direct(feature(family).setFluid(expected));
            assertEquals(implicit.bounds(), explicit.bounds());
            for (int x = implicit.bounds().minX(); x <= implicit.bounds().maxX(); x += 7) {
                for (int z = implicit.bounds().minZ(); z <= implicit.bounds().maxZ(); z += 7) {
                    for (int y = implicit.bounds().minY(); y <= implicit.bounds().maxY(); y++) {
                        assertEquals(implicit.sample(x, y, z), explicit.sample(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    public void faultRemainsDryForEveryConfiguredFluid() {
        SubterrainPlan original = direct(feature(IrisSubterrainFamily.TECTONIC_FAULT));
        for (IrisSubterrainFluid fluid : IrisSubterrainFluid.values()) {
            SubterrainPlan configured = direct(feature(IrisSubterrainFamily.TECTONIC_FAULT).setFluid(fluid));
            for (int x = original.bounds().minX(); x <= original.bounds().maxX(); x += 2) {
                for (int z = original.bounds().minZ(); z <= original.bounds().maxZ(); z += 2) {
                    for (int y = original.bounds().minY(); y <= original.bounds().maxY(); y++) {
                        SubterrainCell cell = configured.sample(x, y, z);
                        assertFalse(cell.fluid());
                        assertEquals(original.sample(x, y, z), cell);
                    }
                }
            }
        }
    }

    @Test
    public void plansSnapshotResolvedFluidAcrossConfigurationMutation() {
        IrisSubterrainFeature feature = compactFeature(IrisSubterrainFamily.CENOTE);
        SubterrainPlan implicit = direct(feature);
        feature.setFluid(IrisSubterrainFluid.LAVA);
        SubterrainPlan explicit = direct(feature);
        feature.setFluid(IrisSubterrainFluid.WATER);
        assertEquals(SubterrainCell.Kind.WATER, implicit.sample(0, -10, 0).kind());
        assertEquals(SubterrainCell.Kind.LAVA, explicit.sample(0, -10, 0).kind());
    }

    @Test
    public void fluidJsonOnlyAcceptsTypedWaterOrLava() {
        Gson gson = new Gson();
        for (IrisSubterrainFluid fluid : IrisSubterrainFluid.values()) {
            assertEquals(fluid, gson.fromJson("{\"fluid\":\"" + fluid + "\"}", IrisSubterrainFeature.class).getFluid());
            assertEquals("\"" + fluid + "\"", gson.toJson(fluid));
        }
        for (String invalid : List.of("\"OIL\"", "\"minecraft:water\"", "\"water\"", "5", "true", "{}", "[]")) {
            assertThrows(invalid, JsonParseException.class,
                    () -> gson.fromJson("{\"fluid\":" + invalid + "}", IrisSubterrainFeature.class));
        }
    }

    @Test
    public void allFamiliesHaveBoundedOccupiedAnchorsAndAbsoluteOwnership() {
        for (IrisSubterrainFamily family : IrisSubterrainFamily.values()) {
            SubterrainPlanner planner = planner(feature(family));
            SubterrainPlan plan = first(planner);
            SubterrainPosition anchor = plan.anchor();
            assertTrue(family.toString(), plan.sample(anchor.x(), anchor.y(), anchor.z()).occupied());
            assertTrue(plan.bounds().minY() >= -64);
            assertTrue(plan.bounds().maxY() <= 96);
            assertFalse(planner.sample(anchor.x(), -65, anchor.z()).owned());
            assertFalse(planner.sample(anchor.x(), 97, anchor.z()).owned());
            assertFalse(plan.sample(plan.bounds().maxX() + 1, anchor.y(), anchor.z()).owned());
            assertNotNull(plan.sample(anchor.x(), anchor.y(), anchor.z()).room());
        }
    }

    @Test
    public void faultsAndTerracesHaveTaperedIrregularWallsAndVaultedCeilings() {
        for (IrisSubterrainFamily family : List.of(IrisSubterrainFamily.TECTONIC_FAULT,
                IrisSubterrainFamily.TRAVERTINE_TERRACES)) {
            SubterrainPlan plan = direct(feature(family));
            Set<Integer> widths = new HashSet<>();
            Set<Integer> ceilings = new HashSet<>();
            for (int x = -100; x <= 100; x += 5) {
                int width = 0;
                for (int z = -32; z <= 32; z++) {
                    SubterrainCell cell = plan.sample(x, 0, z);
                    if (cell.occupied()) {
                        width++;
                        ceilings.add(cell.room().ceilingY());
                    }
                }
                widths.add(width);
            }
            assertTrue(family.toString(), widths.size() > 4);
            assertTrue(family.toString(), ceilings.size() > 5);
            assertFalse(plan.sample(120, 0, 28).occupied());
            assertFalse(plan.sample(-120, 0, -28).occupied());
            assertTrue(plan.sample(0, 0, 0).room().ceilingY() > plan.sample(0, 0, 20).room().ceilingY());
        }
    }

    @Test
    public void organicShapesRemainSealedInsideMaximumBoundsAcrossSeedsAndRotation() {
        for (IrisSubterrainFamily family : List.of(IrisSubterrainFamily.TECTONIC_FAULT,
                IrisSubterrainFamily.TRAVERTINE_TERRACES, IrisSubterrainFamily.LAVA_TUBE)) {
            IrisSubterrainFeature feature = feature(family).setLength(2048).setRadius(256).setSpacing(4096);
            for (int seed = 0; seed < 4; seed++) {
                for (boolean rotated : List.of(false, true)) {
                    SubterrainPlan plan = new SubterrainPlan(new SubterrainPlan.Options(
                            SubterrainPlanner.Definition.from(feature), 0, 0, 0, rotated, feature.getId() + ":" + seed));
                    SubterrainBounds bounds = plan.bounds();
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        for (int x = bounds.minX(); x <= bounds.maxX(); x += 13) {
                            assertFalse(plan.sample(x, y, bounds.minZ()).occupied());
                            assertFalse(plan.sample(x, y, bounds.maxZ()).occupied());
                        }
                        for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 13) {
                            assertFalse(plan.sample(bounds.minX(), y, z).occupied());
                            assertFalse(plan.sample(bounds.maxX(), y, z).occupied());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void organicGeometryVariesPerPlanAndRotatesWithoutChangingOccupancy() {
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.TRAVERTINE_TERRACES);
        SubterrainPlanner.Definition definition = SubterrainPlanner.Definition.from(feature);
        SubterrainPlan original = new SubterrainPlan(new SubterrainPlan.Options(definition, 0, 0, 0, false, "terrace:one"));
        SubterrainPlan rotated = new SubterrainPlan(new SubterrainPlan.Options(definition, 0, 0, 0, true, "terrace:one"));
        SubterrainPlan another = new SubterrainPlan(new SubterrainPlan.Options(definition, 0, 0, 0, false, "terrace:two"));
        int changed = 0;
        for (int x = -120; x <= 120; x += 4) {
            for (int z = -30; z <= 30; z += 3) {
                for (int y = -16; y <= 16; y += 4) {
                    SubterrainCell cell = original.sample(x, y, z);
                    SubterrainCell turned = rotated.sample(z, y, x);
                    assertEquals(cell.kind(), turned.kind());
                    if (cell.owned()) {
                        assertEquals(cell.room().floorY(), turned.room().floorY());
                        assertEquals(cell.room().ceilingY(), turned.room().ceilingY());
                    }
                    if (cell.kind() != another.sample(x, y, z).kind()) {
                        changed++;
                    }
                }
            }
        }
        assertTrue(changed > 100);
    }

    @Test
    public void cenoteDomeAndSealedWaterTableHaveNoExposedFluidSidesOrBottom() {
        SubterrainPlan plan = direct(feature(IrisSubterrainFamily.CENOTE));
        SubterrainPlanner planner = planner(feature(IrisSubterrainFamily.CENOTE));
        assertTrue(plan.sample(0, 0, 0).room().ceilingY() > plan.sample(20, 0, 0).room().ceilingY());
        assertTrue(assertRetained(planner, List.of(plan), SubterrainCell.Kind.WATER) > 1000);
        assertTrue(plan.sample(0, -16, 0).solid());
    }

    @Test
    public void lavaTubeHasConnectedHornitosAndDryElevatedWalkways() {
        SubterrainPlan plan = direct(feature(IrisSubterrainFamily.LAVA_TUBE));
        int chimneyX = 64;
        int pathZ = 5;
        for (int y = 0; y < 32; y++) {
            assertEquals("chimney y=" + y, SubterrainCell.Kind.AIR, plan.sample(chimneyX, y, pathZ).kind());
        }
        assertTrue(plan.sample(chimneyX + 4, 25, pathZ).solid());
        SubterrainCell walkway = plan.sample(0, 0, 18);
        assertTrue(walkway.room().floorY() > walkway.room().fluidHeadY());
        assertTrue(plan.sample(0, walkway.room().floorY(), 18).solid());
        SubterrainPlanner planner = planner(feature(IrisSubterrainFamily.LAVA_TUBE));
        assertTrue(assertRetained(planner, List.of(plan), SubterrainCell.Kind.LAVA) > 1000);
    }

    @Test
    public void travertineBasinsStepAndRetainFluidBehindRims() {
        SubterrainPlan plan = direct(feature(IrisSubterrainFamily.TRAVERTINE_TERRACES));
        assertTrue(plan.sample(90, 0, 0).room().floorY() > plan.sample(-90, 0, 0).room().floorY());
        SubterrainPlanner planner = planner(feature(IrisSubterrainFamily.TRAVERTINE_TERRACES));
        assertTrue(assertRetained(planner, List.of(plan), SubterrainCell.Kind.WATER) > 1000);
        Set<Integer> rimLocations = new HashSet<>();
        for (int z = -16; z <= 16; z += 4) {
            for (int x = -90; x < -35; x++) {
                SubterrainCell cell = plan.sample(x, -10, z);
                if (cell.owned() && cell.room().floorY() > cell.room().fluidHeadY()) {
                    rimLocations.add(x);
                    break;
                }
            }
        }
        assertTrue(rimLocations.size() > 2);
    }

    @Test
    public void pillarsSpanTheWholeVaultAndFormationsScaleWithVault() {
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE).setPillarSpacing(12).setFormationFraction(0.3);
        SubterrainPlan plan = direct(feature);
        SubterrainRoom room = plan.sample(12, 0, 12).room();
        Set<SubterrainPosition> previous = new HashSet<>();
        for (int y = room.floorY() + 1; y < room.ceilingY(); y++) {
            Set<SubterrainPosition> layer = new HashSet<>();
            for (int x = 7; x <= 17; x++) {
                for (int z = 7; z <= 17; z++) {
                    if (plan.sample(x, y, z).solid()) {
                        layer.add(new SubterrainPosition(x, 0, z));
                    }
                }
            }
            assertFalse("pillar layer y=" + y, layer.isEmpty());
            if (!previous.isEmpty()) {
                assertTrue("pillar connected y=" + y, layer.stream().anyMatch(previous::contains));
            }
            previous = layer;
        }
        assertTrue(plan.sample(0, -10, 0).room().reservedPassage());
        assertFalse(plan.sample(0, 0, 0).room().reservedPassage());
        assertFalse(plan.sample(0, 0, 0).solid());
        int formationCells = 0;
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                SubterrainCell middle = plan.sample(x, 0, z);
                if (!middle.occupied()) {
                    continue;
                }
                SubterrainRoom context = middle.room();
                int quarter = Math.max(1, context.vaultHeight() / 4);
                if (plan.sample(x, context.ceilingY() - quarter, z).solid()) {
                    formationCells++;
                }
            }
        }
        assertTrue(formationCells > 0);
    }

    @Test
    public void configOrderAndChunkTraversalDoNotChangeIdentityOrCells() {
        IrisSubterrainFeature cenote = feature(IrisSubterrainFamily.CENOTE);
        IrisSubterrainFeature fault = feature(IrisSubterrainFamily.TECTONIC_FAULT);
        List<IrisSubterrainFeature> features = new ArrayList<>(List.of(cenote, fault));
        SubterrainPlanner first = new SubterrainPlanner(new SubterrainPlanner.Options(features, 7123, -64, 97, (x, z) -> "test-region"));
        Collections.reverse(features);
        SubterrainPlanner reordered = new SubterrainPlanner(new SubterrainPlanner.Options(features, 7123, -64, 97, (x, z) -> "test-region"));
        List<SubterrainPlan> plans = first.plansForBounds(-1024, -1024, 1024, 1024);
        assertEquals(plans.stream().map(SubterrainPlan::id).toList(),
                reordered.plansForBounds(-1024, -1024, 1024, 1024).stream().map(SubterrainPlan::id).toList());
        for (SubterrainPlan plan : plans) {
            for (int x = plan.bounds().minX(); x <= plan.bounds().maxX(); x += 13) {
                for (int z = plan.bounds().minZ(); z <= plan.bounds().maxZ(); z += 13) {
                    int chunkX = Math.floorDiv(x, 16) * 16;
                    int chunkZ = Math.floorDiv(z, 16) * 16;
                    List<SubterrainPlan> chunk = first.plansForBounds(chunkX - 1, chunkZ - 1, chunkX + 16, chunkZ + 16);
                    for (int y = plan.bounds().minY(); y <= plan.bounds().maxY(); y += 7) {
                        assertEquals(first.sample(x, y, z), first.sample(chunk, x, y, z));
                        assertEquals(first.sample(x, y, z), reordered.sample(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    public void immutableSnapshotsAndSeedsControlPlans() {
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE);
        SubterrainPlanner planner = planner(feature);
        List<String> before = planner.plansForBounds(-1024, -1024, 1024, 1024).stream().map(SubterrainPlan::id).toList();
        feature.setRadius(8).setBiome("changed").setId("changed");
        assertEquals(before, planner.plansForBounds(-1024, -1024, 1024, 1024).stream().map(SubterrainPlan::id).toList());
        assertEquals("subterranean/example", first(planner).biome());
        SubterrainPlanner anotherSeed = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 999, -64, 97, (x, z) -> "test-region"));
        assertNotEquals(before, anotherSeed.plansForBounds(-1024, -1024, 1024, 1024).stream().map(SubterrainPlan::id).toList());
    }

    @Test
    public void narrowYBandNeverClipsSealsIntoWorld() {
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE).setWorldYRange(new IrisRange(0, 10));
        assertTrue(planner(feature).plansForBounds(-2048, -2048, 2048, 2048).isEmpty());
    }

    @Test
    public void solidSealsWinOverHigherPriorityUnrelatedCarving() {
        SubterrainPlan lower = direct(feature(IrisSubterrainFamily.CENOTE));
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.TECTONIC_FAULT).setPriority(100);
        SubterrainPlanner.Definition definition = SubterrainPlanner.Definition.from(feature);
        SubterrainPlan upper = new SubterrainPlan(new SubterrainPlan.Options(definition, 32, 0, 0, false, "fault:other"));
        SubterrainPlanner planner = planner(feature);
        assertTrue(upper.sample(25, -13, 0).occupied());
        assertTrue(lower.sample(25, -13, 0).solid());
        assertTrue(lower.sample(25, -13, 0).room().reservedSolid());
        assertTrue(planner.sample(List.of(upper, lower), 25, -13, 0).solid());
    }

    @Test
    public void dryFeatureIntersectionsJoinAcrossEitherShellPriority() {
        IrisSubterrainFeature basin = feature(IrisSubterrainFamily.CENOTE).setFluidDepth(0).setShapeWarp(1);
        IrisSubterrainFeature fault = feature(IrisSubterrainFamily.TECTONIC_FAULT).setShapeWarp(1);
        SubterrainPlan first = direct(basin);
        SubterrainPlan second = direct(fault);
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(basin, fault), 7, -64, 97, (x, z) -> "test-region"));
        int joined = 0;
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                for (int y = -16; y <= 16; y++) {
                    SubterrainCell shell = first.sample(x, y, z);
                    SubterrainCell passage = second.sample(x, y, z);
                    if (shell.solid() && !shell.room().reservedSolid() && passage.kind() == SubterrainCell.Kind.AIR) {
                        assertEquals(passage, planner.sample(List.of(first, second), x, y, z));
                        assertEquals(passage, planner.sample(List.of(second, first), x, y, z));
                        joined++;
                    }
                }
            }
        }
        assertTrue(joined > 100);
    }

    @Test
    public void exteriorCornersAndRaisedBanksDoNotExtrudeToFlatBoundingPlanes() {
        SubterrainPlan plan = direct(feature(IrisSubterrainFamily.CENOTE).setFluidDepth(0));
        assertTrue(plan.sample(32, -14, 0).solid());
        assertFalse(plan.sample(33, -16, 0).owned());
        assertFalse(plan.sample(30, -17, 0).owned());
    }

    @Test
    public void roundedWetBasesHaveReliefAndRemainInsideThePlacementBand() {
        for (IrisSubterrainFamily family : List.of(IrisSubterrainFamily.CENOTE,
                IrisSubterrainFamily.LAVA_TUBE, IrisSubterrainFamily.TRAVERTINE_TERRACES)) {
            IrisSubterrainFeature feature = feature(family).setShapeWarp(1).setChimneyHeight(0);
            SubterrainPlan plan = direct(feature);
            SubterrainBounds bounds = plan.bounds();
            int maximumBaseDepth = SubterrainPlanner.Definition.from(feature).baseDepth();
            Set<Integer> bottomHeights = new HashSet<>();
            int deepest = 0;
            for (int x = bounds.minX(); x <= bounds.maxX(); x += 2) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 2) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        SubterrainCell cell = plan.sample(x, y, z);
                        if (!cell.solid() || !cell.room().reservedSolid()) {
                            continue;
                        }
                        bottomHeights.add(y);
                        int depth = cell.room().floorY() - y;
                        assertTrue(depth <= maximumBaseDepth + 2);
                        deepest = Math.max(deepest, depth);
                        break;
                    }
                }
            }
            assertTrue(family + " base relief", bottomHeights.size() > 5);
            assertTrue(family + " rock depth", deepest > 6);
            feature.setWorldYRange(new IrisRange(0, feature.getHeight() + 6));
            assertTrue(planner(feature).plansForBounds(-512, -512, 512, 512).isEmpty());
            feature.setWorldYRange(new IrisRange(0, 80));
            List<SubterrainPlan> placedPlans = planner(feature).plansForBounds(-512, -512, 512, 512);
            assertFalse(placedPlans.isEmpty());
            for (SubterrainPlan placed : placedPlans) {
                assertTrue(placed.bounds().minY() >= 0);
                assertTrue(placed.bounds().maxY() <= 80);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortFaultDefinitionsAreRejected() {
        planner(feature(IrisSubterrainFamily.TECTONIC_FAULT).setLength(120));
    }

    @Test
    public void footprintAdmissionRejectsTheEntireFeatureAcrossSavedOwnership() {
        SubterrainPlanner original = planner(feature(IrisSubterrainFamily.CENOTE));
        SubterrainPlan plan = first(original);
        SubterrainPosition anchor = plan.anchor();
        int protectedX = plan.bounds().maxX();
        int protectedZ = plan.bounds().maxZ();
        SubterrainPlanner restricted = original.restrictTo(bounds ->
                !bounds.intersects(protectedX, protectedZ, protectedX, protectedZ));
        assertTrue(original.sample(anchor.x(), anchor.y(), anchor.z()).occupied());
        assertFalse(restricted.plansForBounds(plan.bounds().minX(), plan.bounds().minZ(),
                plan.bounds().maxX(), plan.bounds().maxZ()).stream().anyMatch(candidate -> candidate.id().equals(plan.id())));
        assertFalse(restricted.sample(anchor.x(), anchor.y(), anchor.z()).owned());
        assertTrue(original.sample(anchor.x(), anchor.y(), anchor.z()).occupied());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdentifiersAreRejected() {
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE);
        new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature, feature), 1, -64, 97, (x, z) -> "test-region"));
    }

    private int assertRetained(SubterrainPlanner planner, List<SubterrainPlan> candidates, SubterrainCell.Kind kind) {
        int count = 0;
        SubterrainBounds bounds = candidates.getFirst().bounds();
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, 0}};
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    SubterrainCell cell = planner.sample(candidates, x, y, z);
                    if (cell.kind() != kind) {
                        continue;
                    }
                    count++;
                    for (int[] offset : offsets) {
                        SubterrainCell neighbor = planner.sample(candidates, x + offset[0], y + offset[1], z + offset[2]);
                        assertTrue("fluid at " + x + "," + y + "," + z, neighbor.solid() || neighbor.kind() == kind);
                    }
                }
            }
        }
        return count;
    }

    private IrisSubterrainFeature feature(IrisSubterrainFamily family) {
        return new IrisSubterrainFeature().setId(family.name().toLowerCase()).setFamily(family)
                .setProbability(1).setWorldYRange(new IrisRange(-64, 96)).setBiome("subterranean/example")
                .setPillarSpacing(0).setFormationFraction(0);
    }

    private IrisSubterrainFeature compactFeature(IrisSubterrainFamily family) {
        return feature(family).setLength(64).setRadius(12).setHeight(24).setChimneyHeight(8)
                .setPillarSpacing(12).setFormationFraction(0.2);
    }

    private SubterrainPlanner planner(IrisSubterrainFeature feature) {
        return new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 7123, -64, 97, (x, z) -> "test-region"));
    }

    private SubterrainPlan first(SubterrainPlanner planner) {
        List<SubterrainPlan> plans = planner.plansForBounds(-1024, -1024, 1024, 1024);
        assertFalse(plans.isEmpty());
        return plans.getFirst();
    }

    private SubterrainPlan direct(IrisSubterrainFeature feature) {
        return new SubterrainPlan(new SubterrainPlan.Options(SubterrainPlanner.Definition.from(feature),
                0, 0, 0, false, feature.getId() + ":test"));
    }
}
