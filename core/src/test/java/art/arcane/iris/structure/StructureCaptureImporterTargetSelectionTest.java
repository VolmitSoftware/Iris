package art.arcane.iris.structure;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StructureCaptureImporterTargetSelectionTest {
    @Test
    public void onlyNonJigsawTemplateMissesBecomeCaptureCandidates() {
        assertTrue(BulkStructureImporter.isCaptureCandidate(
                "minecraft:igloo is not a jigsaw structure",
                "No loadable structure NBT for key minecraft:igloo"));
        assertFalse(BulkStructureImporter.isCaptureCandidate(
                "Missing template pool towns_and_towers:broken/start",
                "No loadable structure NBT for key towns_and_towers:broken"));
        assertFalse(BulkStructureImporter.isCaptureCandidate(
                "minecraft:desert_pyramid is not a jigsaw structure",
                "Failed to load structure minecraft:desert_pyramid"));
    }

    @Test
    public void bulkSelectionUsesOnlyLiveTemplateMissCandidates() {
        HashSet<String> candidates = new HashSet<>(List.of(
                "minecraft:swamp_hut",
                "minecraft:igloo"
        ));
        BulkStructureImporter.Report report = new BulkStructureImporter.Report(
                4, 1, 2, 1, Map.of(), false, candidates);

        candidates.add("towns_and_towers:broken_jigsaw");

        assertEquals(
                List.of("minecraft:igloo", "minecraft:swamp_hut"),
                List.copyOf(report.captureCandidates()));
        assertThrows(UnsupportedOperationException.class,
                () -> report.captureCandidates().add("minecraft:stronghold"));
        assertEquals(
                List.of("minecraft:igloo", "minecraft:swamp_hut"),
                StructureCaptureImporter.selectCaptureKeys(
                        List.of(
                                "minecraft:village_plains",
                                "Minecraft:Swamp_Hut",
                                "minecraft:igloo",
                                "towns_and_towers:broken_jigsaw"
                        ),
                        report.captureCandidates()));
    }

    @Test
    public void standaloneSelectionRemainsUnfiltered() {
        assertEquals(
                List.of(
                        "minecraft:igloo",
                        "minecraft:village_plains",
                        "towns_and_towers:broken_jigsaw"
                ),
                StructureCaptureImporter.selectCaptureKeys(
                        List.of(
                                "towns_and_towers:broken_jigsaw",
                                "minecraft:village_plains",
                                "Minecraft:Igloo",
                                "minecraft:igloo"
                        ),
                        null));
    }
}
