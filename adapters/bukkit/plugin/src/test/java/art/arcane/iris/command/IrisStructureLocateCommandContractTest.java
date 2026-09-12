package art.arcane.iris.command;

import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureLocateCommandContractTest {
    @Test
    public void findRejectsUnregisteredNativeAndDormantPlacements() {
        assertEquals(CommandFind.StructureLookupRoute.UNKNOWN,
                CommandFind.selectStructureLookupRoute(
                        false, null, true, true, false, true, true));
        assertEquals(CommandFind.StructureLookupRoute.UNKNOWN,
                CommandFind.selectStructureLookupRoute(
                        false, null, false, false, false, true, true));
        assertEquals(CommandFind.StructureLookupRoute.NO_ACTIVE_PLACEMENT,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.REPLACED_BY_IRIS),
                        false, false, false, true, true));
        assertEquals(CommandFind.StructureLookupRoute.NO_ACTIVE_PLACEMENT,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.GENERATE_NATIVE),
                        true, false, false, true, false));
    }

    @Test
    public void findAllowsLocatableEditablePlacementsWithoutNativeGeneration() {
        assertEquals(CommandFind.StructureLookupRoute.IRIS,
                CommandFind.selectStructureLookupRoute(
                        false, null, false, false, true, false, false));
        assertEquals(CommandFind.StructureLookupRoute.IRIS,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.REPLACED_BY_IRIS),
                        false, false, true, false, false));
    }

    @Test
    public void findRequiresWorldGenerationAndReachabilityForNativeRoutes() {
        assertEquals(CommandFind.StructureLookupRoute.WORLD_DISABLED,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.REPLACED_BY_IRIS),
                        true, true, false, false, true));
        assertEquals(CommandFind.StructureLookupRoute.WORLD_DISABLED,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.GENERATE_NATIVE),
                        false, false, false, false, true));
        assertEquals(CommandFind.StructureLookupRoute.UNREACHABLE,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.GENERATE_NATIVE),
                        false, false, false, true, false));
        assertEquals(CommandFind.StructureLookupRoute.NATIVE,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.GENERATE_NATIVE),
                        true, true, false, true, false));
        assertEquals(CommandFind.StructureLookupRoute.NATIVE,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.GENERATE_NATIVE),
                        true, false, false, true, true));
    }

    @Test
    public void findHonorsPackDisableBeforePlacementRouting() {
        assertEquals(CommandFind.StructureLookupRoute.POLICY_DISABLED,
                CommandFind.selectStructureLookupRoute(
                        true, decision(NativeStructureGenerationStatus.DISABLED_BY_PACK),
                        false, false, true, true, true));
    }

    @Test
    public void unregisteredDiagnosticExplainsEveryRejectedRoute() {
        assertEquals(
                "Native structure minecraft:village is disabled by this dimension's importedStructures settings.",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.POLICY_DISABLED, "minecraft:village",
                        NativeStructureGenerationStatus.DISABLED_BY_PACK,
                        false, List.of(), Set.of()));
        assertEquals(
                "native structure generation is disabled for this world",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.WORLD_DISABLED, "minecraft:village",
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        false, List.of(), Set.of()));
        assertEquals(
                "its resolved biome filter is empty",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.UNREACHABLE, "towns_and_towers:exclusive",
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        false, List.of(), Set.of()));
        assertEquals(
                "this pack does not produce any of its required biome(s): terralith:alpine_grove/bwg:aspen_boreal",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.UNREACHABLE, "towns_and_towers:exclusive",
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        false, List.of("terralith:alpine_grove", "bwg:aspen_boreal"),
                        Set.of("minecraft:plains")));
        assertEquals(
                "no active positive-weight, positive-frequency structure-set entry includes it in this world",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.UNREACHABLE, "example:dormant",
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        false, List.of("minecraft:plains"), Set.of("minecraft:plains")));
        assertEquals(
                "no active positive-weight, positive-frequency structure-set entry includes it in this world",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.UNREACHABLE, "example:partial_overlap",
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        false, List.of("minecraft:plains", "mod:absent"), Set.of("minecraft:plains")));
    }

    @Test
    public void unregisteredDiagnosticExplainsInactiveAndMissingNativePlacements() {
        assertEquals(
                "configured Iris replacement is inactive: no matching placement has positive density when "
                        + "density-based and a Y band intersecting this world",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.NO_ACTIVE_PLACEMENT, "example:replacement",
                        NativeStructureGenerationStatus.REPLACED_BY_IRIS,
                        false, List.of(), Set.of()));
        assertEquals(
                "configured nativeStructures placement is inactive: no matching placement has positive density "
                        + "when density-based and a Y band intersecting this world; the native route is also "
                        + "inactive because its resolved biome filter is empty",
                CommandFind.describeStructureExclusion(
                        CommandFind.StructureLookupRoute.NO_ACTIVE_PLACEMENT, "example:native",
                        NativeStructureGenerationStatus.GENERATE_NATIVE,
                        true, List.of(), Set.of()));
        assertTrue(CommandFind.describeConfiguredUnregisteredNative(true)
                .contains("key is not registered by the active server/datapack"));
        assertFalse(CommandFind.describeConfiguredUnregisteredNative(true)
                .contains("Iris placement is also inactive"));
        assertTrue(CommandFind.describeConfiguredUnregisteredNative(false)
                .contains("Iris placement is also inactive"));
    }

    @Test
    public void unplacedEditableDiagnosticDefersToRegistryAndNativeCategories() {
        assertTrue(CommandFind.isUnplacedEditableCandidate(
                "pack:editable", Set.of(), Set.of(), false));
        assertFalse(CommandFind.isUnplacedEditableCandidate(
                "pack:editable", Set.of("pack:editable"), Set.of(), false));
        assertFalse(CommandFind.isUnplacedEditableCandidate(
                "pack:editable", Set.of(), Set.of("pack:editable"), false));
        assertFalse(CommandFind.isUnplacedEditableCandidate(
                "pack:editable", Set.of(), Set.of(), true));
    }

    @Test
    public void findNativePolicyMessagesMatchModdedDiagnostics() {
        assertEquals(
                "Native structure minecraft:village_plains is disabled by this dimension's importedStructures settings.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:village_plains", NativeStructureGenerationStatus.DISABLED_BY_PACK));
        assertEquals(
                "Native structure minecraft:ancient_city is replaced by an Iris placement in this pack and locates through that explicit replacement.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:ancient_city", NativeStructureGenerationStatus.REPLACED_BY_IRIS));
        assertEquals(
                "Native structure minecraft:stronghold generates natively.",
                NativeStructureGenerationPolicy.generationStatusMessage(
                        "minecraft:stronghold", NativeStructureGenerationStatus.GENERATE_NATIVE));
    }

    private static IrisNativeStructureDecision decision(NativeStructureGenerationStatus status) {
        return new IrisNativeStructureDecision(
                status, 0, null, false, null, null);
    }
}
