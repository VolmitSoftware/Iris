package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioWorkcellSpec;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionInputKind;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionStrategy;
import art.arcane.iris.core.structure.export.VanillaJigsawExportFormat;
import art.arcane.iris.engine.framework.structure.ObjectFrame;
import com.volmit.geometry.BlockPosition;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.Sender;
import com.volmit.tooling.world.ToolPlayer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawCommandsContractTest {
    private static final long DEFAULT_SEED = 1337L;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Command root = JigsawCommands.root(Optional::empty);

    @Test
    public void theJigsawRootAnswersToItsShortNamesAndHoldsTheWholeTree() {
        assertEquals("jigsaw", root.name());
        assertEquals(List.of("jig", "jgs"), root.aliases());
        for (String name : List.of("adopt", "connector", "pool", "piece", "variant", "workcell", "rules", "preview")) {
            assertTrue(name, child(root, name).action().isEmpty());
        }
    }

    @Test
    public void exposesStudioLifecycleAndAuthoringCommands() {
        assertArguments("create", "key", "mode", "compatibility", "width", "height", "depth", "seed");
        assertArguments("convert", "source", "target", "seed");
        assertArguments("open", "key", "seed");
        assertArguments("close", "discard");
        assertArguments("delete", "confirm");
        assertArguments("status");
        assertArguments("menu");
        assertArguments("select");
        assertArguments("bounds", "width", "height", "depth");
        assertArguments("save", "bay");
        assertArguments("goto", "bay");
        assertArguments("particles", "visible");
        assertArguments("export", "namespace", "output", "format", "replace");
    }

    @Test
    public void importAndCaptureAreJigsawCommandsAndNeedNoPlayer() {
        Command importAll = child(root, "import");
        Command capture = child(root, "capture");

        assertEquals(List.of("import-all", "reimport", "imp", "all"), importAll.aliases());
        assertEquals(List.of("cap"), capture.aliases());
        assertTrue(importAll.arguments().isEmpty());
        assertTrue(capture.arguments().isEmpty());
        assertTrue(importAll.action().isPresent());
        assertTrue(capture.action().isPresent());
    }

    @Test
    public void committedActivationStartsInitialEvaluationBeforePlayerBinding() throws Exception {
        String source = source("StudioLifecycleCommands.java");
        int commit = source.indexOf("registry.commit(staged)");
        int evaluation = source.indexOf(
                "use.studio().activationCommitted(request.requestId())",
                commit);
        int binding = source.indexOf("StudioSessions.bind(", evaluation);

        assertTrue(commit >= 0);
        assertTrue(evaluation > commit);
        assertTrue(binding > evaluation);
    }

    @Test
    public void convertIsAddOnlyWorkflowWithAliasesAndDefaults() {
        Command convert = child(root, "convert");

        assertEquals(List.of("import-vanilla"), convert.aliases());
        assertEquals("auto", convert.arguments().get(1).fallback());
        assertEquals(DEFAULT_SEED, convert.arguments().get(2).fallback());
        ResourceId source = StudioLifecycleCommands.parseRegisteredStructureKey("minecraft:village_plains");
        assertEquals("minecraft_village_plains", StudioLifecycleCommands.resolveConversionTarget(source, "auto"));
        assertEquals("villages/plains", StudioLifecycleCommands.resolveConversionTarget(source, "iris:villages/plains"));
        ResourceId ancientCity = StudioLifecycleCommands.parseRegisteredStructureKey("minecraft:ancient_city");
        assertEquals("minecraft_ancient_city", StudioLifecycleCommands.resolveConversionTarget(ancientCity, "auto"));
        assertThrows(IllegalArgumentException.class,
                () -> StudioLifecycleCommands.resolveConversionTarget(source, "custom:village"));
    }

    @Test
    public void adoptionCommandsExposeTwoStepPlanContract() {
        Command adopt = child(root, "adopt");
        Command inspect = child(adopt, "inspect");
        Command apply = child(adopt, "apply");

        assertEquals(List.of("source", "target", "strategy"), argumentNames(inspect));
        assertEquals("auto", inspect.arguments().get(1).fallback());
        assertEquals("auto", inspect.arguments().get(2).fallback());
        assertEquals(List.of("plan"), argumentNames(apply));
        assertEquals(List.of("auto", "clone", "in-place"), inspect.arguments().get(2).complete("", null));
        assertEquals(IrisStructureAdoptionStrategy.IN_PLACE, AdoptionCommands.parseAdoptionStrategy("claim"));
        assertEquals(IrisStructureAdoptionStrategy.CLONE, AdoptionCommands.parseAdoptionStrategy("copy"));
        assertThrows(IllegalArgumentException.class, () -> AdoptionCommands.parseAdoptionStrategy("overwrite"));
    }

    @Test
    public void adoptionInputKindUsesOnlyOwnershipProvenance() throws Exception {
        Path packRoot = temporaryFolder.newFolder("adoption-provenance").toPath();

        assertEquals(IrisStructureAdoptionInputKind.UNOWNED_IRIS,
                AdoptionCommands.adoptionInputKind(packRoot, "unowned"));
        writeManifest(packRoot, "datapack-created", StructureSource.Kind.DATAPACK,
                StructureOwnershipManifest.Provenance.created());
        assertEquals(IrisStructureAdoptionInputKind.UNOWNED_IRIS,
                AdoptionCommands.adoptionInputKind(packRoot, "datapack-created"));
        writeManifest(packRoot, "managed-provenance", StructureSource.Kind.IRIS, managedProvenance());
        assertEquals(IrisStructureAdoptionInputKind.MANAGED_DATAPACK,
                AdoptionCommands.adoptionInputKind(packRoot, "managed-provenance"));
    }

    @Test
    public void legacyGraphWritersShareTheServiceMutationContract() throws Exception {
        Method mutation = GraphMutations.class.getDeclaredMethod(
                "runGraphMutation",
                StudioUse.class,
                StudioSessions.ActiveContext.class,
                JigsawStudioService.CommandGraphMutation.class);

        assertEquals(void.class, mutation.getReturnType());
        assertNotNull(JigsawStudioService.CommandGraphMutation.class.getDeclaredMethod("run"));
        assertNotNull(JigsawStudioService.CommandGraphMutationResult.class.getDeclaredConstructor(
                JigsawStudioLayout.class,
                String.class,
                String.class,
                String.class));
    }

    @Test
    public void exposesAutosaveDynamicEvaluationAndSelectedWorkcellResize() {
        assertEquals("Flush the automatic save for a workcell now", child(root, "save").description());
        assertEquals("Show active Jigsaw Studio and dynamic evaluation state", child(root, "status").description());
        assertEquals("Set the selected Studio workcell capacity", child(root, "bounds").description());
        assertThrows(IllegalStateException.class, () -> child(root, "validate"));
    }

    @Test
    public void createDefaultsToPlanarIrisWithCompleteCellAndSeedDefaults() {
        List<Argument<?>> arguments = child(root, "create").arguments();

        assertEquals("planar", arguments.get(1).fallback());
        assertEquals("iris", arguments.get(2).fallback());
        assertEquals(15L, arguments.get(3).fallback());
        assertEquals(15L, arguments.get(4).fallback());
        assertEquals(15L, arguments.get(5).fallback());
        assertEquals(DEFAULT_SEED, arguments.get(6).fallback());
    }

    @Test
    public void structureKeysExplainTheirResourceAndOpenSupportsEditingAliases() {
        Command create = child(root, "create");
        Command open = child(root, "open");

        assertEquals("key", create.arguments().getFirst().name());
        assertEquals("New key written as structures/<key>.json", create.arguments().getFirst().description());
        assertEquals("key", open.arguments().getFirst().name());
        assertEquals("Existing key loaded from structures/<key>.json", open.arguments().getFirst().description());
        assertEquals(List.of("edit", "reopen"), open.aliases());
    }

    @Test
    public void createChoicesExposeCanonicalCompletionsAndRetainAliases() {
        Command create = child(root, "create");

        assertEquals(List.of("planar", "spatial"), create.arguments().get(1).complete("", null));
        assertEquals(JigsawStudioMode.PLANAR_JIGSAW, StudioLifecycleCommands.parseMode("2d"));
        assertEquals(JigsawStudioMode.SPATIAL_JIGSAW, StudioLifecycleCommands.parseMode("3d"));
        assertEquals(List.of("iris", "vanilla"), create.arguments().get(2).complete("", null));
        assertEquals(art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                StudioLifecycleCommands.parseCompatibility("extended"));
        assertEquals(art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget.VANILLA_PORTABLE,
                StudioLifecycleCommands.parseCompatibility("portable"));
        assertThrows(IllegalArgumentException.class, () -> StudioLifecycleCommands.parseMode("volume"));
        assertThrows(IllegalArgumentException.class, () -> StudioLifecycleCommands.parseCompatibility("mixed"));
    }

    @Test
    public void exposesNestedPieceVariantAndPreviewCommands() {
        assertArguments(child(root, "pool"), "create", "poolKey", "fallbackPoolKey");
        assertArguments(child(root, "connector"), "channel", "channel");
        Command piece = child(root, "piece");
        assertArguments(piece, "create", "poolKey", "pieceKey", "weight");
        assertArguments(piece, "add", "poolKey", "pieceKey", "weight");
        assertArguments(piece, "remove", "poolKey");
        assertArguments(piece, "rotatable", "rotatable");
        assertArguments(piece, "expand");
        assertEquals("Resize the selected piece object exactly to workcell capacity",
                child(piece, "expand").description());
        Command variant = child(root, "variant");
        assertArguments(variant, "weight", "poolKey", "weight");
        assertArguments(variant, "resize", "width", "height", "depth");
        assertArguments(variant, "label", "displayName");
        assertArguments(variant, "label-reset");
        assertArguments(variant, "duplicate");
        assertArguments(variant, "duplicate-family", "themeKey");
        assertEquals("next", child(variant, "duplicate-family").arguments().getFirst().fallback());
        Command workcell = child(root, "workcell");
        assertArguments(workcell, "capacity", "width", "height", "depth");
        assertArguments(workcell, "label", "displayName");
        assertArguments(workcell, "label-reset");
        Command rules = child(root, "rules");
        assertArguments(rules, "limits", "maxDepth", "maxSizeChunks");
        assertArguments(rules, "fallback", "poolKey", "fallbackPoolKey");
        Command preview = child(root, "preview");
        assertArguments(preview, "assemble", "seed");
        assertArguments(preview, "goto");
    }

    @Test
    public void pieceCreateUsesResolvedWorkcellCapacityInsteadOfLayoutDefault() throws Exception {
        String source = source("GraphEditCommands.java");
        int create = source.indexOf("private void pieceCreate(");
        int add = source.indexOf("private void pieceAdd(", create);
        String createSource = source.substring(create, add);

        assertTrue(createSource.contains("targetWorkcell = contextual;"));
        assertTrue(createSource.contains("JigsawStudioCellDimensions dimensions = targetWorkcell.capacity();"));
        assertFalse(createSource.contains("layout().cellDimensions()"));
    }

    @Test
    public void pieceAddUsesCanonicalAxesForRotatedRectangularPlanarWorkcells() {
        JigsawStudioLayout layout = nonuniformPlanarLayout();
        IrisJigsawPiece eastEnd = new IrisJigsawPiece();
        eastEnd.getConnectors().add(new IrisJigsawConnector().setDirection(JigsawDirection.EAST_POSITIVE_X));
        ObjectFrame exactObject = new ObjectFrame(7, 5, 13);

        GraphMutations.PieceWorkcellResolution exact = GraphMutations.resolvePieceWorkcell(
                layout,
                eastEnd,
                exactObject);
        GraphMutations.PieceWorkcellResolution oversized = GraphMutations.resolvePieceWorkcell(
                layout,
                eastEnd,
                new ObjectFrame(8, 5, 13));

        assertEquals(JigsawPlanarArchetype.END.stableId(), exact.workcell().stableId());
        assertEquals(new BlockPosition(13, 5, 7), exact.requiredDimensions());
        assertEquals(new JigsawStudioCellDimensions(13, 5, 7), exact.workcell().capacity());
        assertTrue(exact.fits());
        assertTrue(exactObject.depth() > exact.workcell().capacity().depth());
        assertFalse(oversized.fits());
    }

    @Test
    public void pieceAddKeepsRawObjectAxesForSpatialWorkcells() {
        JigsawStudioCellDimensions capacity = new JigsawStudioCellDimensions(7, 5, 13);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                capacity,
                JigsawStudioVariantCatalog.empty());
        IrisJigsawPiece spatialPiece = new IrisJigsawPiece();
        spatialPiece.getConnectors().add(
                new IrisJigsawConnector().setDirection(JigsawDirection.EAST_POSITIVE_X));

        GraphMutations.PieceWorkcellResolution resolution = GraphMutations.resolvePieceWorkcell(
                layout,
                spatialPiece,
                new ObjectFrame(7, 5, 13));

        assertEquals(JigsawStudioLayout.SPATIAL_WORKCELL_ID, resolution.workcell().stableId());
        assertEquals(new BlockPosition(7, 5, 13), resolution.requiredDimensions());
        assertEquals(capacity, resolution.workcell().capacity());
        assertTrue(resolution.fits());
    }

    @Test
    public void everyStudioCommandRejectsSendersThatAreNotPlayers() throws Exception {
        List<String> answered = new ArrayList<>();
        RecordingSender sender = new RecordingSender();

        for (Command command : playerCommands(root)) {
            sender.errors.clear();
            command.action().orElseThrow().run(contextOf(sender));
            assertEquals(command.name(), List.of(StudioUse.PLAYERS_ONLY), sender.errors);
            answered.add(command.name());
        }

        assertTrue(answered.size() > 30);
    }

    @Test
    public void exportOutputIsOneSafeChildArtifact() {
        Path exportRoot = Path.of("build", "jigsaw-exports").toAbsolutePath().normalize();

        assertEquals(exportRoot.resolve("village.zip"), JigsawExportCommand.resolveExportDestination(
                exportRoot, "village", VanillaJigsawExportFormat.ZIP));
        assertEquals(exportRoot.resolve("village"), JigsawExportCommand.resolveExportDestination(
                exportRoot, "village", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> JigsawExportCommand.resolveExportDestination(
                exportRoot, "", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> JigsawExportCommand.resolveExportDestination(
                exportRoot, ".", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> JigsawExportCommand.resolveExportDestination(
                exportRoot, "../all-exports", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> JigsawExportCommand.resolveExportDestination(
                exportRoot, "nested/export", VanillaJigsawExportFormat.DIRECTORY));
    }

    @Test
    public void exportLeaseRejectsDuplicatePlayerAndDestination() {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        Path firstDestination = Path.of("build", "jigsaw-exports", "first.zip");
        Path secondDestination = Path.of("build", "jigsaw-exports", "second.zip");

        assertTrue(JigsawExportCommand.beginExport(firstPlayer, firstDestination));
        try {
            assertFalse(JigsawExportCommand.beginExport(firstPlayer, secondDestination));
            assertFalse(JigsawExportCommand.beginExport(secondPlayer, firstDestination));
        } finally {
            JigsawExportCommand.finishExport(firstPlayer, firstDestination);
        }

        assertTrue(JigsawExportCommand.beginExport(secondPlayer, firstDestination));
        JigsawExportCommand.finishExport(secondPlayer, firstDestination);
    }

    @Test
    public void exportStartFailuresHavePreciseOperatorMessages() {
        assertEquals("The active Jigsaw Studio is no longer available.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.NOT_ACTIVE));
        assertEquals("Only the Jigsaw Studio owner can export this project.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.NOT_OWNER));
        assertEquals("Wait for the pending autosave or discard the edits before exporting the on-disk graph.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.DIRTY));
        assertEquals("The active Jigsaw Studio is closing and cannot be exported.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.CLOSING));
        assertEquals("Wait for the current Jigsaw Studio save to finish before exporting.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.SAVE_IN_PROGRESS));
        assertEquals("Wait for the current Jigsaw Studio operation to finish before exporting.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.OPERATION_IN_PROGRESS));
        assertEquals("A Jigsaw Studio export is already in progress.",
                JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.IN_PROGRESS));
        assertThrows(IllegalArgumentException.class,
                () -> JigsawExportCommand.exportStartError(JigsawStudioService.ExportStart.STARTED));
    }

    @Test
    public void exportLeaseReleaseActionRunsExactlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        JigsawExportCommand.ExportLease lease = new JigsawExportCommand.ExportLease(releases::incrementAndGet);

        lease.release();
        lease.release();

        assertEquals(1, releases.get());
    }

    @Test
    public void exportSourceAcquiresStudioLeaseBeforeStaticLeaseAndReleasesBothPaths() throws Exception {
        String source = source("JigsawExportCommand.java");
        int serviceLease = source.indexOf("studioService.tryBeginExport(requestId, playerId)");
        int staticLease = source.indexOf("beginExport(playerId, destination)", serviceLease);
        int dispatch = source.indexOf("runExport(operation);", staticLease);
        int schedulingRelease = source.indexOf("operation.lease().release()", dispatch);
        int exporter = source.indexOf("new VanillaJigsawDatapackExporter().export(operation.request())", dispatch);
        int completionRelease = source.indexOf("operation.lease().release()", exporter);

        assertTrue(serviceLease >= 0);
        assertTrue(staticLease > serviceLease);
        assertTrue(dispatch > staticLease);
        assertTrue(schedulingRelease > dispatch);
        assertTrue(exporter > schedulingRelease);
        assertTrue(completionRelease > exporter);
    }

    @Test
    public void exportGoesBelowTheOutputFolderOfThePack() throws Exception {
        String source = source("JigsawExportCommand.java");

        assertTrue(source.contains("use.pack().outputFolder()"));
    }

    private static Command child(Command parent, String name) {
        return parent.children().stream()
                .filter(command -> command.answersTo(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(parent.name() + " has no sub-command " + name));
    }

    private void assertArguments(String name, String... argumentNames) {
        assertArguments(root, name, argumentNames);
    }

    private static void assertArguments(Command parent, String name, String... argumentNames) {
        Command command = child(parent, name);
        assertTrue(name, command.action().isPresent());
        assertEquals(name, List.of(argumentNames), argumentNames(command));
    }

    private static List<String> argumentNames(Command command) {
        return command.arguments().stream().map(Argument::name).toList();
    }

    private static List<Command> playerCommands(Command parent) {
        List<Command> commands = new ArrayList<>();
        for (Command child : parent.children()) {
            if (parent.name().equals("jigsaw") && (child.name().equals("import") || child.name().equals("capture"))) {
                continue;
            }
            if (child.action().isPresent()) {
                commands.add(child);
            }
            commands.addAll(playerCommands(child));
        }
        return commands;
    }

    private static String source(String fileName) throws Exception {
        return Files.readString(Path.of("src/main/java/art/arcane/iris/core/structure/tooling", fileName))
                .replace("\r\n", "\n");
    }

    private static CommandContext contextOf(Sender sender) {
        return (CommandContext) java.lang.reflect.Proxy.newProxyInstance(
                JigsawCommandsContractTest.class.getClassLoader(),
                new Class<?>[]{CommandContext.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sender")) {
                        return sender;
                    }
                    throw new IllegalStateException("A command that needs a player asks for " + method.getName() + " too early");
                });
    }

    private static final class RecordingSender implements Sender {
        private final List<String> errors = new ArrayList<>();

        @Override
        public String name() {
            return "console";
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void success(String message) {
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        @Override
        public <T> Optional<T> extension(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public Optional<ToolPlayer> player() {
            return Optional.empty();
        }
    }

    private static JigsawStudioLayout nonuniformPlanarLayout() {
        List<JigsawStudioWorkcellSpec> workcells = List.of(
                workcell(JigsawPlanarArchetype.BLANK, 3, 1, 3),
                workcell(JigsawPlanarArchetype.END, 13, 5, 7),
                workcell(JigsawPlanarArchetype.STRAIGHT, 5, 2, 11),
                workcell(JigsawPlanarArchetype.CORNER, 9, 3, 6),
                workcell(JigsawPlanarArchetype.TEE, 12, 4, 8),
                workcell(JigsawPlanarArchetype.CROSS, 10, 6, 10));
        return JigsawStudioLayout.createPlanar(
                new JigsawStudioCellDimensions(3, 1, 3),
                workcells,
                JigsawStudioVariantCatalog.empty());
    }

    private static JigsawStudioWorkcellSpec workcell(
            JigsawPlanarArchetype archetype,
            int width,
            int height,
            int depth
    ) {
        return new JigsawStudioWorkcellSpec(
                archetype,
                "",
                new JigsawStudioCellDimensions(width, height, depth),
                true);
    }

    private static void writeManifest(
            Path packRoot,
            String structure,
            StructureSource.Kind sourceKind,
            StructureOwnershipManifest.Provenance provenance
    ) throws Exception {
        StructureKey structureKey = new StructureKey("iris", structure);
        StructureOwnershipManifest manifest = new StructureOwnershipManifest(
                StructureOwnershipManifest.CURRENT_SCHEMA_VERSION,
                structureKey,
                StructureSource.of(sourceKind, new StructureKey("iris", "source/" + structure)),
                StructureBackend.IRIS_ASSEMBLY,
                List.of(StructureCapability.BLOCKS, StructureCapability.CONNECTORS),
                List.of(),
                Map.of("structures/" + structure + ".json", StructureHash.sha256(
                        structure.getBytes(StandardCharsets.UTF_8))),
                provenance);
        Path manifestPath = new StructureTransactionWriter(packRoot).ownershipManifestPath(structureKey);
        Files.createDirectories(manifestPath.getParent());
        Files.write(manifestPath, manifest.toJson());
    }

    private static StructureOwnershipManifest.Provenance managedProvenance() {
        String path = "structures/source.json";
        String hash = StructureHash.sha256("source".getBytes(StandardCharsets.UTF_8));
        return new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.MANAGED_DATAPACK,
                UUID.randomUUID().toString(),
                StructureHash.sha256("plan".getBytes(StandardCharsets.UTF_8)),
                StructureHash.sha256("closure".getBytes(StandardCharsets.UTF_8)),
                1L,
                Map.of(path, hash),
                Map.of(path, path),
                StructureOwnershipManifest.RollbackDisposition.NONE);
    }
}
