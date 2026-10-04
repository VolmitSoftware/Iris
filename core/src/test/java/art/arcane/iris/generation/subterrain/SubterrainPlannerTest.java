package art.arcane.iris.generation.subterrain;

import art.arcane.iris.pack.value.IrisRange;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SubterrainPlannerTest {
    @Test
    public void terraceAnchorsUseOccupiedPathsWhenCenterIsPillarOrUpperStep() {
        for (IrisSubterrainFluid fluid : IrisSubterrainFluid.values()) {
            IrisSubterrainFeature feature = compactFeature(IrisSubterrainFamily.TRAVERTINE_TERRACES).setFluid(fluid);
            SubterrainPlan pillar = direct(feature);
            for (int y = pillar.bounds().minY(); y <= pillar.bounds().maxY(); y++) {
                assertFalse(pillar.sample(0, y, 0).occupied());
            }
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
    public void faultWallsRemainParallelBeyondTwoHundredBlocksAndHaveShelves() {
        SubterrainPlan plan = direct(feature(IrisSubterrainFamily.TECTONIC_FAULT));
        for (int x = -110; x <= 110; x++) {
            assertTrue(plan.sample(x, 0, 0).occupied());
            assertTrue(plan.sample(x, 0, 32).solid());
            assertTrue(plan.sample(x, 0, -32).solid());
        }
        int centralFloor = plan.sample(0, 0, 0).room().floorY();
        int ledgeFloor = plan.sample(0, 0, 28).room().floorY();
        assertTrue(ledgeFloor > centralFloor);
        assertTrue(plan.sample(0, ledgeFloor, 28).solid());
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
        int rimX = -128 + 42;
        assertTrue(plan.sample(rimX, -10, 0).solid());
        assertTrue(plan.sample(0, 0, 28).room().floorY() > plan.sample(0, 0, 28).room().fluidHeadY());
    }

    @Test
    public void pillarsSpanTheWholeVaultAndFormationsScaleWithVault() {
        IrisSubterrainFeature feature = feature(IrisSubterrainFamily.CENOTE).setPillarSpacing(12).setFormationFraction(0.3);
        SubterrainPlan plan = direct(feature);
        SubterrainRoom room = plan.sample(12, 0, 12).room();
        for (int y = room.floorY(); y <= room.ceilingY(); y++) {
            assertTrue("pillar y=" + y, plan.sample(12, y, 12).solid());
        }
        assertTrue(plan.sample(0, 0, 0).room().reservedPassage());
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
        SubterrainPlanner first = new SubterrainPlanner(new SubterrainPlanner.Options(features, 7123, -64, 97));
        Collections.reverse(features);
        SubterrainPlanner reordered = new SubterrainPlanner(new SubterrainPlanner.Options(features, 7123, -64, 97));
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
        SubterrainPlanner anotherSeed = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 999, -64, 97));
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
        assertTrue(upper.sample(32, -14, 0).occupied());
        assertTrue(lower.sample(32, -14, 0).solid());
        assertTrue(planner.sample(List.of(upper, lower), 32, -14, 0).solid());
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
        new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature, feature), 1, -64, 97));
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
        return new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 7123, -64, 97));
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
