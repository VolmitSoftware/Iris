package art.arcane.iris.modded.command;

import art.arcane.iris.engine.object.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.NativeStructureGenerationStatus;
import art.arcane.iris.nativegen.NativeStructureLocateResults;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisModdedStructureCommandTest {
    @Test
    public void mixedUnexploredLocateReferencesOnlyTheSelectedProvider() {
        BlockPos origin = BlockPos.ZERO;
        Pair<BlockPos, String> irisNear = Pair.of(new BlockPos(4, 70, 0), "iris");
        Pair<BlockPos, String> nativeFar = Pair.of(new BlockPos(8, 70, 0), "native");
        AtomicInteger irisReferences = new AtomicInteger();
        AtomicInteger nativeReferences = new AtomicInteger();

        Pair<BlockPos, String> irisSelected = NativeStructureLocateResults.selectAndReference(
                origin,
                irisNear, () -> irisReferences.incrementAndGet(),
                nativeFar, () -> nativeReferences.incrementAndGet());

        assertSame(irisNear, irisSelected);
        assertEquals(1, irisReferences.get());
        assertEquals(0, nativeReferences.get());

        Pair<BlockPos, String> nativeNear = Pair.of(new BlockPos(2, 70, 0), "native");
        Pair<BlockPos, String> nativeSelected = NativeStructureLocateResults.selectAndReference(
                origin,
                irisNear, () -> irisReferences.incrementAndGet(),
                nativeNear, () -> nativeReferences.incrementAndGet());

        assertSame(nativeNear, nativeSelected);
        assertEquals(1, irisReferences.get());
        assertEquals(1, nativeReferences.get());
    }

    @Test
    public void registeredStructureEligibilityMatchesGotoAndSuggestions() {
        IrisNativeStructureDecision nativeDecision = decision(NativeStructureGenerationStatus.GENERATE_NATIVE);
        IrisNativeStructureDecision replacementDecision = decision(
                NativeStructureGenerationStatus.REPLACED_BY_IRIS);
        IrisNativeStructureDecision disabledDecision = decision(
                NativeStructureGenerationStatus.DISABLED_BY_PACK);

        assertTrue(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                nativeDecision, false, false, false, true, true));
        assertTrue(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                nativeDecision, true, true, false, false, true));
        assertFalse(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                nativeDecision, true, false, false, false, true));
        assertFalse(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                nativeDecision, true, true, false, true, false));
        assertTrue(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                replacementDecision, false, false, true, false, false));
        assertTrue(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                replacementDecision, true, true, false, false, true));
        assertFalse(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                replacementDecision, true, true, false, false, false));
        assertFalse(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                replacementDecision, false, false, false, true, true));
        assertFalse(ModdedCommandSuggestions.isEligibleRegisteredStructure(
                disabledDecision, true, true, true, true, true));
    }

    @Test
    public void structureSuggestionsDedupeAndSortPlacedAndNativeKeys() {
        List<String> suggestions = ModdedCommandSuggestions.combineStructureKeys(
                List.of("towns_and_towers:village_forest", "minecraft:village_plains",
                        "towns_and_towers:village_forest", "MINECRAFT:VILLAGE_PLAINS"),
                List.of("minecraft:stronghold", "minecraft:village_plains"));

        assertEquals(List.of("minecraft:stronghold", "minecraft:village_plains",
                "towns_and_towers:village_forest"), suggestions);
    }

    @Test
    public void structureSuggestionsCannotReintroduceRegistryOrNativePlacementCollisions() {
        List<String> suggestions = ModdedCommandSuggestions.eligibleUnregisteredEditableKeys(
                List.of("iris:custom", "minecraft:disabled", "MINECRAFT:UNREACHABLE",
                        "iris:native_collision"),
                Set.of("minecraft:disabled", "minecraft:unreachable"),
                (String key) -> key.equalsIgnoreCase("iris:native_collision"));

        assertEquals(List.of("iris:custom"), suggestions);
    }

    @Test
    public void unregisteredReportFindsOnlyConfiguredNativeKeysAbsentFromRegistry() {
        List<String> missing = ModdedUnregisteredStructures.missingConfiguredNativeKeys(
                List.of("iris:editable", "missing:native", "MISSING:NATIVE", "registered:native"),
                Set.of("registered:native"),
                (String key) -> key.equalsIgnoreCase("missing:native")
                        || key.equalsIgnoreCase("registered:native"));

        assertEquals(List.of("missing:native"), missing);
    }

    @Test
    public void unregisteredReportDistinguishesEveryNativeExclusionReason() {
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.WORLD_DISABLED,
                ModdedLocateCommands.classifyNativeAvailability(false, true, false, false, true, true));
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.FILTERED,
                ModdedLocateCommands.classifyNativeAvailability(true, false, false, false, true, true));
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.IRIS_SUPPRESSED,
                ModdedLocateCommands.classifyNativeAvailability(true, true, true, false, true, true));
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.EMPTY_BIOME_FILTER,
                ModdedLocateCommands.classifyNativeAvailability(true, true, false, true, false, false));
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.BIOME_UNREACHABLE,
                ModdedLocateCommands.classifyNativeAvailability(true, true, false, false, false, false));
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.NO_PLACEMENT,
                ModdedLocateCommands.classifyNativeAvailability(true, true, false, false, true, false));
        assertEquals(ModdedLocateCommands.NativeStructureAvailability.AVAILABLE,
                ModdedLocateCommands.classifyNativeAvailability(true, true, false, false, true, true));

        assertTrue(ModdedLocateCommands.nativeUnavailableMessage(
                "towns_and_towers:exclusive",
                ModdedLocateCommands.NativeStructureAvailability.EMPTY_BIOME_FILTER)
                .contains("resolves to zero registered biomes"));
        assertTrue(ModdedLocateCommands.nativeUnavailableMessage(
                "minecraft:village_plains",
                ModdedLocateCommands.NativeStructureAvailability.NO_PLACEMENT)
                .contains("no active positive-weight, positive-frequency structure-set placement"));
        String combined = ModdedLocateCommands.registeredStructureUnavailableMessage(
                "towns_and_towers:exclusive",
                ModdedLocateCommands.NativeStructureAvailability.EMPTY_BIOME_FILTER,
                decision(NativeStructureGenerationStatus.GENERATE_NATIVE),
                true, false, true, false);
        assertTrue(combined.contains("resolves to zero registered biomes"));
        assertTrue(combined.contains("matching Iris nativeStructures placement is also configured"));
    }

    @Test
    public void unregisteredReportExplainsEditablePlacementState() {
        assertTrue(ModdedUnregisteredStructures.editableExclusionReason(false)
                .contains("no biome, region, or dimension structure placement"));
        assertTrue(ModdedUnregisteredStructures.editableExclusionReason(true)
                .contains("non-positive density or a Y band outside"));
    }

    private IrisNativeStructureDecision decision(NativeStructureGenerationStatus status) {
        return new IrisNativeStructureDecision(status, 0, null, false, null, null);
    }
}
