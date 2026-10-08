package art.arcane.iris.core.commands;

import art.arcane.iris.engine.framework.NativeStructureGenerationPolicy;
import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureGenerationStatus;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureLocateCommandContractTest {
    @Test
    public void findReportsDensityLimitAndUsesExactLocatedOrigin() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandFindSource")));

        assertTrue(source.contains("LocateStatus.SEARCH_LIMIT_REACHED"));
        assertTrue(source.contains("the density search safety limit was reached"));
        assertTrue(source.contains("result.originX(), result.baseY(), result.originZ()"));
        assertFalse(source.contains("at[0] + 8"));
        assertFalse(source.contains("at[2] + 8"));
    }

    @Test
    public void findRoutesOnlyActiveWorldStructures() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandFindSource")));
        int methodStart = source.indexOf("public void structure(");
        int methodEnd = source.indexOf("static StructureLookupRoute selectStructureLookupRoute", methodStart);
        String method = source.substring(methodStart, methodEnd);
        int nativeResolution = method.indexOf("resolveNativeStructure(structureKey)");
        int policyResolution = method.indexOf("NativeStructureGenerationPolicy.resolve(", nativeResolution);
        int nativeLocatableCheck = method.indexOf(
                "IrisStructureLocator.hasLocatableNativePlacement(e, structureKey)", policyResolution);
        int editableLocatableCheck = method.indexOf(
                "IrisStructureLocator.hasLocatableEditablePlacement(e, structureKey)", nativeLocatableCheck);
        int worldGenerationCheck = method.indexOf("targetWorld.canGenerateStructures()", editableLocatableCheck);
        int routeResolution = method.indexOf("selectStructureLookupRoute(", editableLocatableCheck);
        int nativeLocate = method.indexOf("targetWorld.locateNearestStructure(", routeResolution);

        assertTrue(nativeResolution >= 0);
        assertTrue(policyResolution > nativeResolution);
        assertTrue(nativeLocatableCheck > policyResolution);
        assertTrue(editableLocatableCheck > nativeLocatableCheck);
        assertTrue(worldGenerationCheck > editableLocatableCheck);
        assertTrue(routeResolution > editableLocatableCheck);
        assertTrue(nativeLocate > routeResolution);
        assertFalse(method.contains("IrisStructureLocator.isPlaced(e, structureKey)"));
        assertFalse(method.contains("NativeStructureLocateCapability"));
    }

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
    public void unregisteredDiagnosticPrintsDetailsOnlyToConsole() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandFindSource")));
        int methodStart = source.indexOf("public void unregistered()");
        int methodEnd = source.indexOf("private static StructureExclusionReport", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(methodStart >= 0);
        assertTrue(source.contains("Iris.info(\"[%s] %s%s: %s\""));
        assertTrue(method.contains("non-generating structure candidate(s) and their reasons to the server console"));
        assertTrue(method.contains("no chunks were searched"));
        assertTrue(method.indexOf("DatapackIngestService.installed()") > method.indexOf("J.a(() ->"));
        assertTrue(source.contains("engine.getDimension().getDatapackImports()"));
        assertTrue(source.contains("snapshot.configuredImportUrls().contains(importUrl)"));
        assertTrue(source.contains("StructureResources.structureKeys(engine.getData())"));
        assertFalse(method.contains("commandSender.sendMessage(exclusion"));
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

    @Test
    public void structureVerifyReportsDensityLimitAndUsesExactLocatedOrigin() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandStructureSource")));

        assertTrue(source.contains("[iris-search-limit]"));
        assertTrue(source.contains("density searches safety-limited"));
        assertTrue(source.contains("result.originX() + \",\" + result.baseY() + \",\" + result.originZ()"));
        assertFalse(source.contains("at[0] + 8"));
        assertFalse(source.contains("at[2] + 8"));
    }

    @Test
    public void bulkStructureImportRestrictsCaptureWithoutChangingStandaloneCapture() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandStructureSource")));
        int importStart = source.indexOf("public void importAll(");
        int importEnd = source.indexOf("@Director(name = \"capture\"", importStart);
        int captureStart = source.indexOf("public void capture(", importEnd);
        int captureEnd = source.indexOf("@Director(description = \"Verify", captureStart);

        assertTrue(importStart >= 0);
        assertTrue(importEnd > importStart);
        assertTrue(captureStart > importEnd);
        assertTrue(captureEnd > captureStart);
        String importMethod = source.substring(importStart, importEnd);
        String captureMethod = source.substring(captureStart, captureEnd);
        assertTrue(importMethod.contains("StructureCaptureImporter.importStructures("));
        assertTrue(importMethod.contains("jigsaws.captureCandidates()"));
        assertFalse(importMethod.contains("StructureCaptureImporter.importAllStructures("));
        assertTrue(captureMethod.contains("StructureCaptureImporter.importAllStructures("));
    }

    @Test
    public void structurePlaceSeparatesPackContentFromTargetWorldEngine() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandStructureSource")));
        int methodStart = source.indexOf("public void place(");
        assertTrue(methodStart >= 0);
        String method = source.substring(methodStart);
        assertTrue(method.contains("IrisData data = dimension.getLoader()"));
        assertTrue(method.contains("PlatformChunkGenerator targetGenerator = IrisToolbelt.access(targetWorld)"));
        assertTrue(method.contains("CommandObject.createPlacer(targetWorld, future, targetEngine)"));
        assertTrue(method.contains("if (blockChanges == 0)"));
        assertTrue(method.contains("COMMAND_STRUCTURE_PLACEMENT_CHANGED_NO_BLOCKS"));
        Path structureSource = Path.of(System.getProperty("iris.commandStructureSource"));
        String objectSource = Files.readString(structureSource.resolveSibling("CommandObject.java"));
        assertTrue(objectSource.contains("futureBlockChanges.putIfAbsent(block, block.getBlockData())"));
        assertFalse(method.contains("data.getEngine()"));
    }

    @Test
    public void structureVerifyPartitionsPolicyBeforeNativeReachability() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.commandStructureSource")));
        int methodStart = source.indexOf("private void runVerification(");
        int methodEnd = source.indexOf("private void sendVerificationMessages(", methodStart);
        String method = source.substring(methodStart, methodEnd);
        int policyResolution = method.indexOf("NativeStructureGenerationPolicy.resolve(engine, keyName, false)");
        int nativeRequirement = method.indexOf(
                "requiresNativeReachability |= !IrisStructureLocator.isPlaced", policyResolution);
        int reachabilityGuard = method.indexOf("if (requiresNativeReachability)", nativeRequirement);
        int reachabilityLookup = method.indexOf("StructureReachability.reachableKeys(engine)", reachabilityGuard);

        assertTrue(policyResolution >= 0);
        assertTrue(nativeRequirement > policyResolution);
        assertTrue(reachabilityGuard > nativeRequirement);
        assertTrue(reachabilityLookup > reachabilityGuard);
    }

    private static IrisNativeStructureDecision decision(NativeStructureGenerationStatus status) {
        return new IrisNativeStructureDecision(
                status, 0, null, false, null, null);
    }
}
