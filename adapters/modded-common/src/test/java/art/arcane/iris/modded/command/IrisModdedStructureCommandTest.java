package art.arcane.iris.modded.command;

import art.arcane.iris.engine.framework.NativeStructureGenerationStatus;
import art.arcane.iris.nativegen.NativeStructureLocateResults;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public void gotoStructureSupportsIrisAndNativeRegistryTargets() throws IOException {
        String source = source("ModdedLocateCommands.java");
        String suggestions = source("ModdedCommandSuggestions.java");

        assertTrue(source.contains("ModuleServices.canLocateStructure(engine, key)"));
        assertTrue(source.contains("registry.get(identifier)"));
        assertTrue(source.contains("getPlacementsForStructure(holder)"));
        assertTrue(source.contains("generator.findNearestMapStructure("));
        assertTrue(source.contains("NATIVE_STRUCTURE_LOCATE_RADIUS = 100"));
        assertTrue(source.contains("HolderSet.direct(target.holder())"));
        assertFalse(source.contains("NativeStructureLocateCapability"));
        assertTrue(source.contains("boolean teleported = player.teleportTo("));
        assertTrue(suggestions.contains("combineStructureKeys(unregisteredIrisKeys, nativeKeys)"));
        assertTrue(source.contains("irisGenerator.isNativeStructureReachable(holder)"));
        assertTrue(source.contains("StructureSearch.Outcome.SEARCH_LIMIT_REACHED"));
        assertTrue(source.contains("IRIS_MODDED_COMMANDS_UNABLE_LOCATE_IRIS_PLACED_STRUCTURE_DENSITY_SEARCH_SAFETY_LIMIT_WAS"));
        assertTrue(source.contains("int targetX = result.x()"));
        assertTrue(source.contains("int targetY = result.y() + 2"));
        assertTrue(source.contains("int targetZ = result.z()"));
        assertFalse(source.contains("at[0] + 8"));
        assertFalse(source.contains("at[2] + 8"));
    }

    @Test
    public void registeredStructureEligibilityMatchesGotoAndSuggestions() {
        NativeStructureGenerationStatus nativeDecision = NativeStructureGenerationStatus.GENERATE_NATIVE;
        NativeStructureGenerationStatus replacementDecision = NativeStructureGenerationStatus.REPLACED_BY_IRIS;
        NativeStructureGenerationStatus disabledDecision = NativeStructureGenerationStatus.DISABLED_BY_PACK;

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
                NativeStructureGenerationStatus.GENERATE_NATIVE,
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

    @Test
    public void unregisteredCommandUsesGotoEligibilityAndPrintsToConsole() throws IOException {
        String command = source("ModdedUnregisteredStructures.java");
        String tree = source("ModdedCommandTree.java");

        assertTrue(tree.contains("Commands.literal(\"unregistered\")"));
        assertTrue(tree.contains("ModdedUnregisteredStructures.print(context.getSource())"));
        assertTrue(command.contains("ModdedCommandSuggestions.isEligibleRegisteredStructure("));
        assertTrue(command.contains("ModdedLocateCommands.registeredStructureUnavailableMessage("));
        assertTrue(command.contains("ModuleServices.structureKeys(engine.getData())"));
        assertTrue(command.contains("ModuleServices.canLocateStructure(engine, key)"));
        assertTrue(command.contains("ModdedIrisLog.info(\"[Iris goto unregistered] [{}] {} - {}\""));
        assertTrue(command.contains("UNREGISTERED(\"unregistered\")"));
        assertFalse(command.contains("DatapackIngestService"));
    }

    @Test
    public void structureSuggestionsUseActiveLevelAvailability() throws IOException {
        String suggestions = source("ModdedCommandSuggestions.java");
        int methodStart = suggestions.indexOf("static CompletableFuture<Suggestions> suggestStructureKeys(");
        int methodEnd = suggestions.indexOf("static boolean isEligibleRegisteredStructure(", methodStart);
        String method = suggestions.substring(methodStart, methodEnd);

        assertTrue(method.contains("ServerLevel level = source.getLevel()"));
        assertTrue(method.contains("Engine engine = IrisModdedCommands.engineFor(level)"));
        assertFalse(method.contains("IrisStructureLocator"));
        assertTrue(method.contains("ModuleServices.locatableStructures(engine)"));
        assertTrue(method.contains("StructureReachability.reachableKeys(engine)"));
        assertTrue(method.contains("NativeStructureGenerationPolicy.resolve(engine, key)"));
        assertTrue(method.contains("ModuleServices.canLocateStart(engine, key)"));
        assertTrue(method.contains("ModuleServices.canLocateStructure(engine, key)"));
        assertTrue(method.contains("reachableNativeKeys.contains(normalizeKey(key))"));
        assertTrue(method.contains("nativeGenerationEnabled"));
        assertTrue(method.contains("eligibleUnregisteredEditableKeys("));
        assertTrue(method.contains("ModuleServices.startsStructure(engine, candidate)"));
        assertFalse(method.contains("NativeStructureGenerationKeys.active(level)"));
        assertFalse(method.contains("getPlacementsForStructure"));
    }

    @Test
    public void generatorLocateUsesEveryIrisPlacedNativeStructure() throws IOException {
        String source = moddedSource("ModdedNativeStructureStage.java");
        int methodStart = source.indexOf("Pair<BlockPos, Holder<Structure>> findNearestIrisStructure(");
        int methodEnd = source.indexOf("HolderSet<Structure> filterReachableNativeStructures(", methodStart);
        String method = source.substring(methodStart, methodEnd);
        int registryLookup = method.indexOf("level.registryAccess().lookupOrThrow(Registries.STRUCTURE)");
        int placedCheck = method.indexOf(
                "if (!ModuleServices.startsStructure(current, structureId))");
        int irisLocate = method.indexOf("NativeStructureLocatePersistence.search(", placedCheck);
        int nearestSelection = method.indexOf(
                "NativeStructureLocateResults.nearest(pos, predicted, nativeLocated)", irisLocate);
        int selectedVerification = method.indexOf("bestSearch.search().verify(bestResult)", nearestSelection);
        int selectedReference = method.indexOf(
                "selectedSearch.search().reference(selectedStart)", selectedVerification);

        assertTrue(registryLookup >= 0);
        assertTrue(placedCheck > registryLookup);
        assertTrue(irisLocate > placedCheck);
        assertTrue(nearestSelection > irisLocate);
        assertTrue(selectedVerification > nearestSelection);
        assertTrue(selectedReference > selectedVerification);
        assertTrue(method.contains("NativeStructureLocatePersistence.probe("));
        assertTrue(method.contains("findUnexplored"));
        assertTrue(method.contains("Outcome.SEARCH_LIMIT_REACHED"));
        assertTrue(method.contains("verified.ownership().locatorY()"));
        assertTrue(method.contains("selectedSearch.search().reference(selectedStart)"));
        assertTrue(method.contains("NativeStructureLocateResults.selectAndReference("));
        assertFalse(method.contains("NativeStructureLocateCapability"));
        assertTrue(source.contains("structureBiomeSource.isStructureReachable(holder)"));
        assertFalse(source.contains("isPaperUnavailable"));
        String generator = moddedSource("IrisModdedChunkGenerator.java");
        assertTrue(generator.contains("NativeStructureVanillaLocator.predict("));
        assertFalse(generator.contains("super.findNearestMapStructure(level, reachable"));
    }

    @Test
    public void commandResolvesNativePolicyBeforeAnyVanillaAliasLookup() throws IOException {
        String source = source("ModdedLocateCommands.java");
        int methodStart = source.indexOf("static int gotoStructure(");
        int methodEnd = source.indexOf("private static void locateIrisStructure(", methodStart);
        String method = source.substring(methodStart, methodEnd);
        int nativeResolution = method.indexOf("resolveNativeStructure(source, level, engine, key)");
        int editableIrisLookup = method.indexOf(
                "ModuleServices.canLocateStructure(engine, key)", nativeResolution);
        int policyResolution = method.indexOf(
                "NativeStructureGenerationPolicy.resolve(engine, target.key())", editableIrisLookup);
        int eligibilityCheck = method.indexOf(
                "ModdedCommandSuggestions.isEligibleRegisteredStructure(", policyResolution);
        int replacementLocate = method.indexOf(
                "locateIrisStructure(source, level, engine, player, target.key())", eligibilityCheck);
        int nativeLocate = method.indexOf("runNativeStructureLocate(source, level, player, target)",
                replacementLocate);

        assertTrue(nativeResolution >= 0);
        assertTrue(editableIrisLookup > nativeResolution);
        assertTrue(policyResolution > editableIrisLookup);
        assertTrue(eligibilityCheck > policyResolution);
        assertTrue(replacementLocate > eligibilityCheck);
        assertTrue(nativeLocate > replacementLocate);
        assertTrue(method.contains("ModuleServices.canLocateStart(engine, target.key())"));
        assertTrue(method.contains("ModuleServices.canLocateStructure(engine, target.key())"));
        assertTrue(method.contains("!ModuleServices.startsStructure(engine, key)"));
        assertTrue(method.contains("StructureReachability.isReachable(engine, target.key())"));
        assertTrue(method.contains("getWorldGenSettings().options().generateStructures()"));
        assertFalse(method.contains("ModuleServices.placesStructure(engine, key)"));
    }

    @Test
    public void verifyResolvesRegisteredNativeBeforeGenericIrisAliases() throws IOException {
        String source = source("ModdedLocateCommands.java");
        int methodStart = source.indexOf("private static int verifyStructure(");
        int methodEnd = source.indexOf("private static Optional<NativeStructureTarget> resolveNativeStructure(",
                methodStart);
        String method = source.substring(methodStart, methodEnd);
        int nativeResolution = method.indexOf("resolveNativeStructure(source, level, engine, key)");
        int genericIrisLookup = method.indexOf("ModuleServices.placesStructure(engine, key)", nativeResolution);

        assertTrue(nativeResolution >= 0);
        assertTrue(genericIrisLookup > nativeResolution);
        assertTrue(method.contains("NativeStructureAvailability.IRIS_SUPPRESSED"));
    }

    @Test
    public void structureVerificationNoLongerClaimsNativeGenerationIsMissing() throws IOException {
        String source = source("ModdedStructureCommands.java");

        assertTrue(source.contains("verifyTree(\"verify\")"));
        assertTrue(source.contains("verifyTree(\"locateall\")"));
        assertTrue(source.contains("IrisModdedCommands.verifyStructures("));
        assertFalse(source.contains("Vanilla structure locate is meaningless here"));
    }

    private String source(String fileName) throws IOException {
        Path source = commonSourceRoot().resolve("art/arcane/iris/modded/command/")
                .resolve(fileName)
                .normalize();
        return Files.readString(source);
    }

    private String moddedSource(String fileName) throws IOException {
        Path source = commonSourceRoot().resolve("art/arcane/iris/modded/")
                .resolve(fileName)
                .normalize();
        return Files.readString(source);
    }

    private Path commonSourceRoot() {
        String configuredRoot = System.getProperty("iris.moddedCommonSources");
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot);
        }
        return Path.of(System.getProperty("user.dir"))
                .resolve("../modded-common/src/main/java")
                .normalize();
    }
}
