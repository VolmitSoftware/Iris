package art.arcane.iris.modded;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedWorldCheckTest {
    @Test
    public void coordinatorThreadIsDaemonSoItCannotWedgeTheJvm() {
        Thread thread = ModdedWorldCheck.coordinatorThread(() -> {
        });

        assertTrue(thread.isDaemon());
    }

    @Test
    public void validStructureStartIsGenerationEvidence() {
        assertTrue(WorldCheckPredicates.hasNativeStructureEvidence(true, 0));
    }

    @Test
    public void structureReferenceIsGenerationEvidence() {
        assertTrue(WorldCheckPredicates.hasNativeStructureEvidence(false, 1));
    }

    @Test
    public void absentStartAndReferencesFailGenerationEvidence() {
        assertFalse(WorldCheckPredicates.hasNativeStructureEvidence(false, 0));
    }

    @Test
    public void characteristicMaterialsCoverEveryNativeStructureFamily() {
        assertTrue(characteristic("stronghold", "minecraft:stronghold", "minecraft:cracked_stone_bricks"));
        assertTrue(characteristic("trial_chambers", "minecraft:trial_chambers", "minecraft:oxidized_copper_grate"));
        assertTrue(characteristic("mansion", "minecraft:mansion", "minecraft:dark_oak_planks"));
        assertTrue(characteristic("mansion", "minecraft:mansion", "minecraft:birch_planks"));
        assertTrue(characteristic("village", "minecraft:village_plains", "minecraft:oak_planks"));
        assertTrue(characteristic("village", "minecraft:village_desert", "minecraft:cut_sandstone"));
        assertTrue(characteristic("village", "minecraft:village_savanna", "minecraft:acacia_stairs"));
        assertTrue(characteristic("village", "minecraft:village_snowy", "minecraft:spruce_planks"));
        assertTrue(characteristic("village", "minecraft:village_snowy", "minecraft:stripped_spruce_log"));
        assertTrue(characteristic("village", "minecraft:village_taiga", "minecraft:cobblestone"));
        assertTrue(characteristic("monument", "minecraft:monument", "minecraft:dark_prismarine"));
    }

    @Test
    public void naturalTerrainAndWrongVillageWoodAreNotCharacteristic() {
        assertFalse(characteristic("stronghold", "minecraft:stronghold", "minecraft:stone"));
        assertFalse(characteristic("trial_chambers", "minecraft:trial_chambers", "minecraft:tuff"));
        assertFalse(characteristic("mansion", "minecraft:mansion", "minecraft:dark_oak_leaves"));
        assertFalse(characteristic("village", "minecraft:village_desert", "minecraft:sandstone"));
        assertFalse(characteristic("village", "minecraft:village_savanna", "minecraft:oak_planks"));
        assertFalse(characteristic("monument", "minecraft:monument", "minecraft:water"));
    }

    @Test
    public void materialEvidenceMustExist() {
        assertFalse(WorldCheckPredicates.hasCharacteristicMaterialEvidence(0, 0, 1));
        assertFalse(WorldCheckPredicates.hasCharacteristicMaterialEvidence(8, 0, 1));
        assertFalse(WorldCheckPredicates.hasCharacteristicMaterialEvidence(8, 1, 0));
        assertFalse(WorldCheckPredicates.hasCharacteristicMaterialEvidence(8, 2, 1));
    }

    @Test
    public void singleChunkStructureAcceptsMaterialInItsOnlyChunk() {
        assertTrue(WorldCheckPredicates.hasCharacteristicMaterialEvidence(8, 1, 1));
    }

    @Test
    public void multiChunkStructureRejectsMaterialConfinedToOneChunk() {
        assertFalse(WorldCheckPredicates.hasCharacteristicMaterialEvidence(8, 1, 4));
        assertTrue(WorldCheckPredicates.hasCharacteristicMaterialEvidence(8, 2, 4));
    }

    @Test
    public void configuredVerticalShiftRequiresSafetyClampedGenerationEvidence() {
        assertTrue(WorldCheckPredicates.verticalShiftMatches(0, null, -32, 20, -64, 320));
        assertFalse(WorldCheckPredicates.verticalShiftMatches(0, null, -112, -80, -64, 320));
        assertTrue(WorldCheckPredicates.verticalShiftMatches(0, 0, -32, 20, -64, 320));
        assertTrue(WorldCheckPredicates.verticalShiftMatches(0, 48, -64, -32, -64, 320));
        assertTrue(WorldCheckPredicates.verticalShiftMatches(-64, -64, -48, 4, -64, 320));
        assertTrue(WorldCheckPredicates.verticalShiftMatches(-64, -16, -64, -12, -64, 320));
        assertFalse(WorldCheckPredicates.verticalShiftMatches(-64, null, -48, 4, -64, 320));
        assertFalse(WorldCheckPredicates.verticalShiftMatches(-64, -15, -63, -11, -64, 320));
        assertFalse(WorldCheckPredicates.verticalShiftMatches(0, -1, -33, 19, -64, 320));
    }

    @Test
    public void mansionVegetationGateRejectsRemainingLeaves() {
        assertTrue(WorldCheckPredicates.mansionVegetationPass(0));
        assertFalse(WorldCheckPredicates.mansionVegetationPass(1));
    }

    @Test
    public void mansionVegetationAuditIgnoresTemplateBlocksAndRejectsVegetationAbovePieces() {
        assertFalse(WorldCheckPredicates.mansionVegetationAbovePiece(true, 80, 80));
        assertFalse(WorldCheckPredicates.mansionVegetationAbovePiece(true, 79, 80));
        assertTrue(WorldCheckPredicates.mansionVegetationAbovePiece(true, 81, 80));
        assertFalse(WorldCheckPredicates.mansionVegetationAbovePiece(false, 81, 80));
    }

    @Test
    public void villageFoundationGateRejectsUnsupportedColumns() {
        assertTrue(WorldCheckPredicates.villageFoundationPass(0));
        assertFalse(WorldCheckPredicates.villageFoundationPass(1));
    }

    @Test
    public void villagePoiGateRequiresInBoundsPoiWithoutOutOfBoundsRecords() {
        assertTrue(WorldCheckPredicates.villagePoiPass(1, 0));
        assertFalse(WorldCheckPredicates.villagePoiPass(0, 0));
        assertFalse(WorldCheckPredicates.villagePoiPass(1, 1));
    }

    @Test
    public void smallStructureFootprintIncludesEveryChunk() {
        BoundingBox bounds = new BoundingBox(-16, -20, -16, 31, 120, 31);

        List<ChunkPos> chunks = WorldCheckStructureAudit.boundedFootprintChunks(bounds, ChunkPos.ZERO, 96);

        assertEquals(9, chunks.size());
        assertTrue(chunks.contains(new ChunkPos(-1, -1)));
        assertTrue(chunks.contains(new ChunkPos(1, 1)));
    }

    @Test
    public void largeStructureFootprintIsBoundedAndSamplesEdges() {
        BoundingBox bounds = new BoundingBox(-512, -64, -512, 511, 300, 511);

        List<ChunkPos> chunks = WorldCheckStructureAudit.boundedFootprintChunks(bounds, ChunkPos.ZERO, 20);

        assertTrue(chunks.size() <= 20);
        assertTrue(chunks.size() >= 16);
        assertTrue(chunks.contains(ChunkPos.ZERO));
        assertTrue(chunks.contains(new ChunkPos(-32, -32)));
        assertTrue(chunks.contains(new ChunkPos(31, 31)));
    }

    @Test
    public void qaEventsEscapeStructuredValues() {
        String event = WorldCheckPredicates.qaEventJson("locate\"", "village\n", false, "x\\y\t");

        assertEquals("QA_EVT {\"event\":\"locate\\\"\",\"structure\":\"village\\n\","
                + "\"pass\":false,\"detail\":\"x\\\\y\\t\"}", event);
    }

    @Test
    public void passRequestsStopAfterValidation() {
        List<String> events = new ArrayList<>();

        int status = ModdedWorldCheck.runAndRequestStop(
                () -> {
                    events.add("check");
                    return true;
                },
                () -> events.add("request-stop")
        );

        assertEquals(0, status);
        assertEquals(List.of("check", "request-stop"), events);
    }

    @Test
    public void failureStillRequestsStop() {
        List<String> events = new ArrayList<>();

        int status = ModdedWorldCheck.runAndRequestStop(
                () -> {
                    events.add("check");
                    return false;
                },
                () -> events.add("request-stop")
        );

        assertEquals(1, status);
        assertEquals(List.of("check", "request-stop"), events);
    }

    @Test
    public void thrownCheckStillRequestsStop() {
        AtomicBoolean stopRequested = new AtomicBoolean(false);

        int status = ModdedWorldCheck.runAndRequestStop(
                () -> {
                    throw new IllegalStateException("check failed");
                },
                () -> stopRequested.set(true)
        );

        assertEquals(1, status);
        assertTrue(stopRequested.get());
    }

    @Test
    public void stopRequestFailureForcesNonzeroResult() {
        int status = ModdedWorldCheck.runAndRequestStop(
                () -> true,
                () -> {
                    throw new IllegalStateException("stop request failed");
                }
        );

        assertEquals(1, status);
    }

    @Test
    public void coordinatorAwaitsServerBeforeExit() {
        List<String> events = new ArrayList<>();

        ModdedWorldCheck.awaitStopAndExit(
                () -> events.add("await-stop"),
                0,
                status -> events.add("exit:" + status)
        );

        assertEquals(List.of("await-stop", "exit:0"), events);
    }

    @Test
    public void shutdownWaitFailureForcesNonzeroExit() {
        AtomicInteger status = new AtomicInteger(-1);

        ModdedWorldCheck.awaitStopAndExit(
                () -> {
                    throw new IllegalStateException("shutdown wait failed");
                },
                0,
                status::set
        );

        assertEquals(1, status.get());
    }

    @Test
    public void interruptionIsClearedForShutdownAndRestoredBeforeExit() {
        AtomicBoolean interruptedDuringStop = new AtomicBoolean(true);
        AtomicBoolean interruptedDuringExit = new AtomicBoolean(false);
        AtomicInteger status = new AtomicInteger(-1);
        Thread.currentThread().interrupt();
        try {
            ModdedWorldCheck.awaitStopAndExit(
                    () -> interruptedDuringStop.set(Thread.currentThread().isInterrupted()),
                    0,
                    exitStatus -> {
                        status.set(exitStatus);
                        interruptedDuringExit.set(Thread.currentThread().isInterrupted());
                    }
            );

            assertFalse(interruptedDuringStop.get());
            assertTrue(interruptedDuringExit.get());
            assertEquals(1, status.get());
        } finally {
            Thread.interrupted();
        }
    }

    private static boolean characteristic(String structureLabel, String structureKey, String blockKey) {
        return WorldCheckMaterials.isCharacteristicMaterial(structureLabel,
                Identifier.parse(structureKey), Identifier.parse(blockKey));
    }
}
