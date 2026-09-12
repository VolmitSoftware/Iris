package art.arcane.iris.command;

import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.studio.object.StudioOpenCoordinator;
import art.arcane.iris.studio.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.studio.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.studio.jigsaw.JigsawStudioLayout;
import art.arcane.iris.studio.jigsaw.JigsawStudioMode;
import art.arcane.iris.studio.jigsaw.JigsawStudioVariantCatalog;
import art.arcane.iris.studio.jigsaw.JigsawStudioWorkcellSpec;
import art.arcane.iris.studio.jigsaw.JigsawStudioService;
import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.conversion.IrisStructureAdoptionInputKind;
import art.arcane.iris.structure.export.VanillaJigsawExportFormat;
import art.arcane.iris.world.IrisCreator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.command.specialhandlers.IrisStructureHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CommandJigsawContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void commandIrisRegistersJigsawTree() throws Exception {
        Field field = CommandIris.class.getDeclaredField("jigsaw");

        assertEquals(CommandJigsaw.class, field.getType());
        assertNotNull(CommandJigsaw.class.getDeclaredField("piece"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("pool"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("connector"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("variant"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("workcell"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("rules"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("preview"));
        assertNotNull(CommandJigsaw.class.getDeclaredField("adopt"));
    }

    @Test
    public void exposesStudioLifecycleAndAuthoringCommands() throws Exception {
        assertCommand("create", IrisDimension.class, String.class, String.class, String.class,
                int.class, int.class, int.class, long.class);
        assertCommand("convert", IrisDimension.class, String.class, String.class, long.class);
        assertCommand("open", IrisDimension.class, String.class, long.class);
        assertCommand("close", boolean.class);
        assertCommand("delete", boolean.class);
        assertCommand("status");
        assertCommand("menu");
        assertCommand("select");
        assertCommand("bounds", int.class, int.class, int.class);
        assertCommand("save", String.class);
        assertCommand("gotoBay", String.class);
        assertCommand("particles", boolean.class);
        assertCommand("export", String.class, String.class, String.class, boolean.class);
    }

    @Test
    public void jigsawStudioUsesItsDedicatedOpenLifecycle() {
        assertEquals(
                StudioOpenCoordinator.StudioOpenKind.JIGSAW,
                CommandJigsaw.STUDIO_OPEN_KIND);
        assertFalse(CommandJigsaw.STUDIO_OPEN_KIND.openWorkspace());
        assertFalse(CommandJigsaw.STUDIO_OPEN_KIND.teleportThroughStandardEntry());
        assertEquals(
                IrisCreator.DatapackPreparation.REUSE_LOADED_RUNTIME_IF_READY,
                CommandJigsaw.STUDIO_OPEN_KIND.datapackPreparation());
    }

    @Test
    public void packValidationDenialsAreExpectedOpenFailures() {
        BrokenPackException failure = new BrokenPackException(
                "overworld", List.of("Biome 'broken' has no resolvable regions."));

        assertTrue(CommandJigsawOpen.isExpectedOpenDenial(failure));
        assertTrue(CommandJigsawOpen.isExpectedOpenDenial(
                new CompletionException(failure)));
        assertFalse(CommandJigsawOpen.isExpectedOpenDenial(
                new IllegalStateException("Unexpected Studio failure")));
    }

    @Test
    public void convertIsAddOnlyWorkflowWithAliasesAndDefaults() throws Exception {
        Method convert = CommandJigsaw.class.getDeclaredMethod(
                "convert", IrisDimension.class, String.class, String.class, long.class);
        Director command = convert.getAnnotation(Director.class);
        Parameter[] parameters = convert.getParameters();

        assertEquals(List.of("import", "import-vanilla"), List.of(command.aliases()));
        assertParameter(parameters[2], "auto", null);
        assertParameter(parameters[3], "1337", null);
        NamespacedKey source = CommandJigsaw.parseRegisteredStructureKey("minecraft:village_plains");
        assertEquals("minecraft_village_plains", CommandJigsaw.resolveConversionTarget(source, "auto"));
        assertEquals("villages/plains", CommandJigsaw.resolveConversionTarget(source, "iris:villages/plains"));
        NamespacedKey ancientCity = CommandJigsaw.parseRegisteredStructureKey(
                "minecraft:ancient_city");
        assertEquals("minecraft_ancient_city",
                CommandJigsaw.resolveConversionTarget(ancientCity, "auto"));
        assertThrows(IllegalArgumentException.class,
                () -> CommandJigsaw.resolveConversionTarget(source, "custom:village"));
    }

    @Test
    public void adoptionCommandsExposeTwoStepPlanContract() throws Exception {
        Method inspect = CommandJigsawAdopt.class.getDeclaredMethod(
                "inspect", IrisDimension.class, String.class, String.class, String.class);
        Method apply = CommandJigsawAdopt.class.getDeclaredMethod("apply", String.class);
        Parameter[] inspectParameters = inspect.getParameters();

        assertNotNull(inspect.getAnnotation(Director.class));
        assertNotNull(apply.getAnnotation(Director.class));
        assertParameter(inspectParameters[2], "auto", null);
        assertParameter(inspectParameters[3], "auto", CommandJigsawAdopt.JigsawAdoptionStrategyHandler.class);
        assertEquals(CommandJigsawAdopt.JigsawAdoptionPlanHandler.class,
                apply.getParameters()[0].getAnnotation(Param.class).customHandler());

        CommandJigsawAdopt.JigsawAdoptionStrategyHandler strategyHandler =
                new CommandJigsawAdopt.JigsawAdoptionStrategyHandler();
        assertEquals(List.of("auto", "in-place", "clone"), strategyHandler.getPossibilities());
        assertEquals("in-place", strategyHandler.parse("claim", false));
        assertEquals("clone", strategyHandler.parse("copy", false));
        assertThrows(DirectorParsingException.class, () -> strategyHandler.parse("overwrite", false));
    }

    @Test
    public void adoptionInputKindUsesOnlyOwnershipProvenance() throws Exception {
        Path root = temporaryFolder.newFolder("adoption-provenance").toPath();

        assertEquals(IrisStructureAdoptionInputKind.UNOWNED_IRIS,
                CommandJigsawAdopt.adoptionInputKind(root, "unowned"));
        writeManifest(root, "datapack-created", StructureSource.Kind.DATAPACK,
                StructureOwnershipManifest.Provenance.created());
        assertEquals(IrisStructureAdoptionInputKind.UNOWNED_IRIS,
                CommandJigsawAdopt.adoptionInputKind(root, "datapack-created"));
        writeManifest(root, "managed-provenance", StructureSource.Kind.IRIS, managedProvenance());
        assertEquals(IrisStructureAdoptionInputKind.MANAGED_DATAPACK,
                CommandJigsawAdopt.adoptionInputKind(root, "managed-provenance"));
    }

    @Test
    public void legacyGraphWritersShareTheServiceMutationContract() throws Exception {
        Method mutation = CommandJigsaw.class.getDeclaredMethod(
                "runGraphMutation",
                Player.class,
                CommandJigsaw.ActiveContext.class,
                JigsawStudioService.CommandGraphMutation.class);

        assertEquals(boolean.class, mutation.getReturnType());
        assertNotNull(JigsawStudioService.CommandGraphMutation.class.getDeclaredMethod("run"));
        assertNotNull(JigsawStudioService.CommandGraphMutationResult.class.getDeclaredConstructor(
                JigsawStudioLayout.class,
                String.class,
                String.class,
                String.class));
    }

    @Test
    public void exposesAutosaveDynamicEvaluationAndSelectedWorkcellResize() throws Exception {
        Method save = CommandJigsaw.class.getDeclaredMethod("save", String.class);
        Method status = CommandJigsaw.class.getDeclaredMethod("status");
        Method bounds = CommandJigsaw.class.getDeclaredMethod(
                "bounds", int.class, int.class, int.class);

        assertEquals("Flush the automatic save for a workcell now",
                save.getAnnotation(Director.class).description());
        assertEquals("Show active Jigsaw Studio and dynamic evaluation state",
                status.getAnnotation(Director.class).description());
        assertEquals("Set the selected Studio workcell capacity",
                bounds.getAnnotation(Director.class).description());
        assertThrows(NoSuchMethodException.class,
                () -> CommandJigsaw.class.getDeclaredMethod("validate"));
    }

    @Test
    public void createDefaultsToPlanarIrisWithCompleteCellAndSeedDefaults() throws Exception {
        Method create = CommandJigsaw.class.getDeclaredMethod(
                "create",
                IrisDimension.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                long.class);
        Parameter[] parameters = create.getParameters();

        assertParameter(parameters[2], "planar", CommandJigsaw.JigsawModeHandler.class);
        assertParameter(parameters[3], "iris", CommandJigsaw.JigsawCompatibilityHandler.class);
        assertParameter(parameters[4], "15", null);
        assertParameter(parameters[5], "15", null);
        assertParameter(parameters[6], "15", null);
        assertParameter(parameters[7], "1337", null);
    }

    @Test
    public void structureKeysExplainTheirResourceAndOpenSupportsEditingAliases() throws Exception {
        Method create = CommandJigsaw.class.getDeclaredMethod(
                "create",
                IrisDimension.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                int.class,
                long.class);
        Method open = CommandJigsaw.class.getDeclaredMethod(
                "open", IrisDimension.class, String.class, long.class);
        Param createKey = create.getParameters()[1].getAnnotation(Param.class);
        Param openKey = open.getParameters()[1].getAnnotation(Param.class);
        Director openCommand = open.getAnnotation(Director.class);

        assertEquals("key", createKey.name());
        assertEquals(List.of("structure", "name"), List.of(createKey.aliases()));
        assertEquals("New key written as structures/<key>.json", createKey.description());
        assertEquals("key", openKey.name());
        assertEquals(List.of("structure", "name"), List.of(openKey.aliases()));
        assertEquals("Existing key loaded from structures/<key>.json", openKey.description());
        assertEquals(IrisStructureHandler.class, openKey.customHandler());
        assertEquals(List.of("edit", "reopen"), List.of(openCommand.aliases()));
    }

    @Test
    public void createChoiceHandlersExposeCanonicalCompletionsAndRetainAliases() throws Exception {
        CommandJigsaw.JigsawModeHandler modeHandler = new CommandJigsaw.JigsawModeHandler();
        CommandJigsaw.JigsawCompatibilityHandler compatibilityHandler =
                new CommandJigsaw.JigsawCompatibilityHandler();

        assertEquals(List.of("planar", "spatial"), modeHandler.getPossibilities());
        assertEquals("planar", modeHandler.parse("2d", false));
        assertEquals("spatial", modeHandler.parse("3d", false));
        assertEquals(List.of("iris", "vanilla"), compatibilityHandler.getPossibilities());
        assertEquals("iris", compatibilityHandler.parse("extended", false));
        assertEquals("vanilla", compatibilityHandler.parse("portable", false));
        assertThrows(DirectorParsingException.class, () -> modeHandler.parse("volume", false));
        assertThrows(DirectorParsingException.class, () -> compatibilityHandler.parse("mixed", false));
    }

    @Test
    public void exposesNestedPieceVariantAndPreviewCommands() throws Exception {
        assertNotNull(CommandJigsawPool.class
                .getDeclaredMethod("create", String.class, String.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawConnector.class
                .getDeclaredMethod("channel", String.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPiece.class
                .getDeclaredMethod("create", String.class, String.class, int.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPiece.class
                .getDeclaredMethod("add", String.class, String.class, int.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPiece.class
                .getDeclaredMethod("remove", String.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPiece.class
                .getDeclaredMethod("rotatable", boolean.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPiece.class
                .getDeclaredMethod("expand")
                .getAnnotation(Director.class));
        assertEquals("Resize the selected piece object exactly to workcell capacity",
                CommandJigsawPiece.class
                        .getDeclaredMethod("expand")
                        .getAnnotation(Director.class)
                        .description());
        assertNotNull(CommandJigsawVariant.class
                .getDeclaredMethod("weight", String.class, int.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawVariant.class
                .getDeclaredMethod("resize", int.class, int.class, int.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawVariant.class
                .getDeclaredMethod("label", String.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawVariant.class
                .getDeclaredMethod("labelReset")
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawVariant.class
                .getDeclaredMethod("duplicate")
                .getAnnotation(Director.class));
        Method duplicateFamily = CommandJigsawVariant.class
                .getDeclaredMethod("duplicateFamily", String.class);
        assertNotNull(duplicateFamily.getAnnotation(Director.class));
        assertParameter(duplicateFamily.getParameters()[0], "next", null);
        assertNotNull(CommandJigsawWorkcell.class
                .getDeclaredMethod("capacity", int.class, int.class, int.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawWorkcell.class
                .getDeclaredMethod("label", String.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawWorkcell.class
                .getDeclaredMethod("labelReset")
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawRules.class
                .getDeclaredMethod("limits", int.class, int.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawRules.class
                .getDeclaredMethod("fallback", String.class, String.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPreview.class
                .getDeclaredMethod("assemble", long.class)
                .getAnnotation(Director.class));
        assertNotNull(CommandJigsawPreview.class
                .getDeclaredMethod("gotoPreview")
                .getAnnotation(Director.class));
    }

    @Test
    public void pieceAddUsesCanonicalAxesForRotatedRectangularPlanarWorkcells() {
        JigsawStudioLayout layout = nonuniformPlanarLayout();
        IrisJigsawPiece eastEnd = new IrisJigsawPiece();
        eastEnd.getConnectors().add(new IrisJigsawConnector().setDirection(IrisDirection.EAST_POSITIVE_X));
        IrisObject exactObject = new IrisObject(7, 5, 13);

        CommandJigsaw.PieceWorkcellResolution exact = CommandJigsaw.resolvePieceWorkcell(
                layout,
                eastEnd,
                exactObject);
        CommandJigsaw.PieceWorkcellResolution oversized = CommandJigsaw.resolvePieceWorkcell(
                layout,
                eastEnd,
                new IrisObject(8, 5, 13));

        assertEquals(JigsawPlanarArchetype.END.stableId(), exact.workcell().stableId());
        assertEquals(new IrisPosition(13, 5, 7), exact.requiredDimensions());
        assertEquals(new JigsawStudioCellDimensions(13, 5, 7), exact.workcell().capacity());
        assertTrue(exact.fits());
        assertTrue(exactObject.getD() > exact.workcell().capacity().depth());
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
                new IrisJigsawConnector().setDirection(IrisDirection.EAST_POSITIVE_X));

        CommandJigsaw.PieceWorkcellResolution resolution = CommandJigsaw.resolvePieceWorkcell(
                layout,
                spatialPiece,
                new IrisObject(7, 5, 13));

        assertEquals(JigsawStudioLayout.SPATIAL_WORKCELL_ID, resolution.workcell().stableId());
        assertEquals(new IrisPosition(7, 5, 13), resolution.requiredDimensions());
        assertEquals(capacity, resolution.workcell().capacity());
        assertTrue(resolution.fits());
    }

    @Test
    public void everyExecutableCommandRejectsNonPlayerOrigins() {
        Class<?>[] commandTypes = {
                CommandJigsaw.class,
                CommandJigsawConnector.class,
                CommandJigsawPool.class,
                CommandJigsawPiece.class,
                CommandJigsawVariant.class,
                CommandJigsawWorkcell.class,
                CommandJigsawRules.class,
                CommandJigsawPreview.class,
                CommandJigsawAdopt.class
        };

        for (Class<?> commandType : commandTypes) {
            for (Method method : commandType.getDeclaredMethods()) {
                Director director = method.getAnnotation(Director.class);
                if (director != null) {
                    assertEquals(method.toString(), DirectorOrigin.PLAYER, director.origin());
                }
            }
        }
    }

    @Test
    public void exportOutputIsOneSafeChildArtifact() {
        Path root = Path.of("build", "jigsaw-exports").toAbsolutePath().normalize();

        assertEquals(root.resolve("village.zip"), CommandJigsawExports.resolveExportDestination(
                root, "village", VanillaJigsawExportFormat.ZIP));
        assertEquals(root.resolve("village"), CommandJigsawExports.resolveExportDestination(
                root, "village", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> CommandJigsawExports.resolveExportDestination(
                root, "", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> CommandJigsawExports.resolveExportDestination(
                root, ".", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> CommandJigsawExports.resolveExportDestination(
                root, "../all-exports", VanillaJigsawExportFormat.DIRECTORY));
        assertThrows(IllegalArgumentException.class, () -> CommandJigsawExports.resolveExportDestination(
                root, "nested/export", VanillaJigsawExportFormat.DIRECTORY));
    }

    @Test
    public void exportLeaseRejectsDuplicatePlayerAndDestination() {
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        Path firstDestination = Path.of("build", "jigsaw-exports", "first.zip");
        Path secondDestination = Path.of("build", "jigsaw-exports", "second.zip");

        assertEquals(true, CommandJigsawExports.beginExport(firstPlayer, firstDestination));
        try {
            assertEquals(false, CommandJigsawExports.beginExport(firstPlayer, secondDestination));
            assertEquals(false, CommandJigsawExports.beginExport(secondPlayer, firstDestination));
        } finally {
            CommandJigsawExports.finishExport(firstPlayer, firstDestination);
        }

        assertEquals(true, CommandJigsawExports.beginExport(secondPlayer, firstDestination));
        CommandJigsawExports.finishExport(secondPlayer, firstDestination);
    }

    @Test
    public void exportStartFailuresHavePreciseOperatorMessages() {
        assertEquals("The active Jigsaw Studio is no longer available.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.NOT_ACTIVE));
        assertEquals("Only the Jigsaw Studio owner can export this project.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.NOT_OWNER));
        assertEquals("Wait for the pending autosave or discard the edits before exporting the on-disk graph.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.DIRTY));
        assertEquals("The active Jigsaw Studio is closing and cannot be exported.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.CLOSING));
        assertEquals("Wait for the current Jigsaw Studio save to finish before exporting.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.SAVE_IN_PROGRESS));
        assertEquals("Wait for the current Jigsaw Studio operation to finish before exporting.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.OPERATION_IN_PROGRESS));
        assertEquals("A Jigsaw Studio export is already in progress.",
                CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.IN_PROGRESS));
        assertThrows(IllegalArgumentException.class,
                () -> CommandJigsawExports.exportStartError(JigsawStudioService.ExportStart.STARTED));
    }

    @Test
    public void exportLeaseReleaseActionRunsExactlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        CommandJigsawExports.ExportLease lease = new CommandJigsawExports.ExportLease(releases::incrementAndGet);

        lease.release();
        lease.release();

        assertEquals(1, releases.get());
    }

    private static void assertCommand(String name, Class<?>... parameterTypes) throws Exception {
        Method method = CommandJigsaw.class.getDeclaredMethod(name, parameterTypes);
        assertNotNull(method.getAnnotation(Director.class));
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

    private static void assertParameter(Parameter parameter, String defaultValue, Class<?> customHandler) {
        Param annotation = parameter.getAnnotation(Param.class);

        assertNotNull(annotation);
        assertEquals(defaultValue, annotation.defaultValue());
        if (customHandler != null) {
            assertEquals(customHandler, annotation.customHandler());
        }
    }

    private static void writeManifest(
            Path root,
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
        Path manifestPath = new StructureTransactionWriter(root).ownershipManifestPath(structureKey);
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
