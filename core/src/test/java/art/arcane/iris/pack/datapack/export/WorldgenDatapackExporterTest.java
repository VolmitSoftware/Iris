package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.CompileStage;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.ImageMapStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.object.ObjectStage;
import art.arcane.iris.pack.datapack.export.emit.WorldgenJsonWriter;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportIr;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WorldgenDatapackExporterTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    private final List<DataVersion> writerTargets = new ArrayList<>();

    @Test
    public void existingOutputWithoutReplaceFails() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        Path existing = root.resolve("taken.zip");
        Files.writeString(existing, "keep", StandardCharsets.UTF_8);

        WorldgenExportResult result = exporter().export(request(pack, root).output("taken").build(), WorldgenExportProgress.NONE);

        assertSame(WorldgenExportResult.Status.REJECTED, result.status());
        assertEquals(existing, result.output());
        assertTrue(result.message(), result.message().contains("already exists"));
        assertEquals("keep", Files.readString(existing, StandardCharsets.UTF_8));
        assertTrue(writerTargets.isEmpty());
        assertEquals(List.of("taken.zip"), entries(root));

        WorldgenExportResult replaced = exporter().export(request(pack, root).output("taken").replace(true).build(), WorldgenExportProgress.NONE);

        assertTrue(replaced.message(), replaced.isSuccess());
        assertNotNull(summary(existing));
        assertEquals(List.of("taken.zip"), entries(root));
    }

    @Test
    public void exportReportsTheLoadKeyOfEveryBiomeId() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();

        WorldgenExportResult result = exporter().export(request(pack, root).build(), WorldgenExportProgress.NONE);

        assertTrue(result.message(), result.isSuccess());
        assertEquals(Map.of("iris_export:forest_land", "Forest Land"), result.loadKeyByBiomeId());
    }

    @Test
    public void claimedDestinationIsRejected() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        Path destination = DatapackExportDestinations.resolve(root, "busy", DatapackExportFormat.ZIP);
        assertTrue(DatapackExportDestinations.claim(destination));
        try {
            WorldgenExportResult result = exporter().export(request(pack, root).output("busy").build(), WorldgenExportProgress.NONE);

            assertSame(WorldgenExportResult.Status.REJECTED, result.status());
            assertTrue(result.message(), result.message().contains("already running"));
            assertFalse(Files.exists(destination));
        } finally {
            DatapackExportDestinations.release(destination);
        }
    }

    @Test
    public void invalidPackIsRejected() throws Exception {
        File pack = ExportTestPacks.withDimension(temporaryFolder.newFolder("pack"), """
                {"regions": ["region"], "hydrology": {"enabled": true, "ponds": {"freeboard": [3, 1]}}}
                """);
        Path root = temporaryFolder.newFolder("exports").toPath();

        WorldgenExportResult result = exporter().export(request(pack, root).build(), WorldgenExportProgress.NONE);

        assertSame(WorldgenExportResult.Status.REJECTED, result.status());
        assertTrue(result.message(), result.message().contains("freeboard"));
        assertTrue(writerTargets.isEmpty());
        assertTrue(entries(root).isEmpty());
    }

    @Test
    public void exportIsByteIdenticalAcrossRuns() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        RecordingProgress progress = new RecordingProgress();

        WorldgenExportResult first = exporter().export(request(pack, root).build(), progress);
        WorldgenExportResult second = exporter().export(request(pack, root).output("second").build(), WorldgenExportProgress.NONE);

        assertTrue(first.message(), first.isSuccess());
        assertTrue(second.message(), second.isSuccess());
        assertEquals("main-26.3.zip", first.output().getFileName().toString());
        assertArrayEquals(Files.readAllBytes(first.output()), Files.readAllBytes(second.output()));
        assertEquals(List.of(DataVersion.V26_3, DataVersion.V26_3), writerTargets);
        assertTrue(summary(first.output()), summary(first.output()).contains("target=26.3"));
        assertEquals("validate", progress.stages.getFirst());
        assertTrue(progress.stages.toString(), progress.stages.contains("dimension"));
        assertEquals(List.of("write", "prune", "publish"), progress.stages.subList(progress.stages.size() - 3, progress.stages.size()));
        assertEquals(List.of(2), progress.resourceTotals);
        assertEquals(List.of(first.trim()), progress.trims);
        assertEquals(2, first.trim().filesBefore());
        assertEquals(0, first.trim().filesRemoved());
        assertEquals(List.of("main-26.3.zip", "second.zip"), entries(root));
    }

    @Test
    public void imageMapPackExportsWithDiagnostic() throws Exception {
        File pack = ExportTestPacks.withImageMaps(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();

        WorldgenExportResult result = exporter().export(request(pack, root).version("26.3").format("directory").build(), WorldgenExportProgress.NONE);

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        assertTrue(Files.isRegularFile(result.output().resolve("summary.txt")));
        assertEquals(List.of(DataVersion.V26_3), writerTargets);
        List<String> imageMapResources = new ArrayList<>();
        for (ExportDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.limit() == ExportLimit.IMAGE_MAP) {
                imageMapResources.add(diagnostic.resource());
            }
        }
        assertEquals(List.of("dimensions/main", "biomes/mapped"), imageMapResources);
    }

    @Test
    public void missingDimensionIsRejected() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();

        WorldgenExportResult result = exporter().export(request(pack, root).dimension("absent").build(), WorldgenExportProgress.NONE);

        assertSame(WorldgenExportResult.Status.REJECTED, result.status());
        assertTrue(result.message(), result.message().contains("'absent'"));
        assertTrue(entries(root).isEmpty());
    }

    @Test
    public void argumentErrorsAreRejectedBeforeThePackIsRead() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();

        WorldgenExportResult version = exporter().export(request(pack, root).version("1.21.1").build(), WorldgenExportProgress.NONE);
        WorldgenExportResult older = exporter().export(request(pack, root).version("26.2").build(), WorldgenExportProgress.NONE);
        WorldgenExportResult mode = exporter().export(request(pack, root).mode("flat").build(), WorldgenExportProgress.NONE);
        WorldgenExportResult format = exporter().export(request(pack, root).format("tar").build(), WorldgenExportProgress.NONE);
        WorldgenExportResult namespace = exporter().export(request(pack, root).namespace("Iris Export").build(), WorldgenExportProgress.NONE);

        assertEquals("Unsupported datapack export version '1.21.1'. Supported: 26.3", version.message());
        assertEquals("Unsupported datapack export version '26.2'. Supported: 26.3", older.message());
        assertEquals("Unknown export mode 'flat'. Expected: preset, dimension, overworld", mode.message());
        assertEquals("Export format must be directory or zip.", format.message());
        assertEquals("Invalid export namespace 'Iris Export'", namespace.message());
        for (WorldgenExportResult result : List.of(version, older, mode, format, namespace)) {
            assertSame(WorldgenExportResult.Status.REJECTED, result.status());
        }
        assertTrue(entries(root).isEmpty());
    }

    @Test
    public void objectsFlagReachesTheCompileContext() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        List<Boolean> objects = new ArrayList<>();
        CompileStage recorder = new CompileStage() {
            @Override
            public String name() {
                return "objects";
            }

            @Override
            public void apply(PackCompileContext context) {
                objects.add(context.objects());
            }
        };
        WorldgenDatapackExporter exporter = new WorldgenDatapackExporter(
                target -> new SummaryWriter(target, writerTargets),
                new PackCompiler(List.of(new DimensionStage(), recorder)));

        WorldgenExportResult without = exporter.export(request(pack, root).objects(false).build(), WorldgenExportProgress.NONE);
        WorldgenExportResult with = exporter.export(request(pack, root).output("with").build(), WorldgenExportProgress.NONE);

        assertTrue(without.message(), without.isSuccess());
        assertTrue(with.message(), with.isSuccess());
        assertEquals(List.of(false, true), objects);
    }

    @Test
    public void exportReportsGenerationCostAndFlagsClimateRootsOverBudget() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        CompileStage climate = new CompileStage() {
            @Override
            public String name() {
                return "biomes";
            }

            @Override
            public void apply(PackCompileContext context) {
                BiomeCodes base = context.biomeCodes();
                Field code = new Field.Noise("minecraft:temperature", 0.25, 0.0, null, null, null);
                for (int level = 0; level < 20; level++) {
                    code = new Field.Shared("climate/level/" + level, new Field.Binary(Field.BinaryOp.MAX, code, code));
                }
                context.setBiomeCodes(new BiomeCodes(code, base.caveCode(), base.class3d(), base.layout(), base.loadKeyByBiomeId()));
            }
        };
        WorldgenDatapackExporter exporter = new WorldgenDatapackExporter(WorldgenJsonWriter::forTarget,
                new PackCompiler(List.of(new DimensionStage(), climate)));

        WorldgenExportResult result = exporter.export(request(pack, root).format("directory").build(), WorldgenExportProgress.NONE);

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        GenerationCost.RootCost temperature = result.cost().root(GenerationCost.Root.TEMPERATURE).orElseThrow();
        assertEquals(3_145_726.0, temperature.expandedNodes(), 0.0);
        assertEquals(3_145_726.0, temperature.worstCaseNodes(), 0.0);
        assertEquals(42L, temperature.distinctNodes());
        assertTrue(result.cost().root(GenerationCost.Root.FINAL_DENSITY).isPresent());
        assertEquals(0.0, result.cost().root(GenerationCost.Root.RIDGES).orElseThrow().expandedNodes(), 0.0);
        List<ExportDiagnostic> cost = result.diagnostics().stream().filter(diagnostic -> diagnostic.limit() == ExportLimit.GENERATION_COST).toList();
        assertEquals(cost.toString(), 1, cost.size());
        assertEquals("noise_settings/" + ExportTestPacks.DIMENSION + "/temperature", cost.getFirst().resource());
        assertEquals(densityFunctionBytes(result.output()), result.cost().densityFunctionBytes());
        assertTrue(result.cost().densityFunctionBytes() > 0L);
    }

    private static long densityFunctionBytes(Path output) throws IOException {
        long bytes = 0L;
        try (Stream<Path> paths = Files.walk(output)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = output.relativize(path).toString().replace(File.separatorChar, '/');
                if (relative.contains("/worldgen/density_function/") && relative.endsWith(".json")) {
                    bytes += Files.size(path);
                }
            }
        }
        return bytes;
    }

    @Test
    public void createdExporterWritesTheTargetFileSet() throws Exception {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        WorldgenDatapackExporter exporter = WorldgenDatapackExporter.create();

        WorldgenExportResult current = exporter.export(request(pack, root).format("directory").build(), WorldgenExportProgress.NONE);

        assertTrue(current.message() + " " + current.diagnostics(), current.isSuccess());
        List<String> shared = List.of(
                "data/iris_export/dimension_type/main.json",
                "data/iris_export/worldgen/biome/forest_land.json",
                "data/iris_export/worldgen/biome/mapped.json",
                "data/iris_export/worldgen/biome/ocean.json",
                "data/iris_export/worldgen/density_function/main/aquifer/barrier.json",
                "data/iris_export/worldgen/density_function/main/aquifer/fluid_level_spread.json",
                "data/iris_export/worldgen/density_function/main/aquifer/lava.json",
                "data/iris_export/worldgen/density_function/main/final_density.json",
                "data/iris_export/worldgen/noise_settings/main.json",
                "data/minecraft/dimension/overworld.json",
                "pack.mcmeta",
                "pack.png");
        List<String> currentOnly = List.of(
                "data/iris_export/worldgen/density_function/main/chunk_surface_level.json",
                "data/iris_export/worldgen/density_function/main/spawn_elevation.json",
                "data/iris_export/worldgen/material_rule/main.json");
        List<String> currentFiles = files(current.output());
        assertTrue(currentFiles.toString(), currentFiles.containsAll(shared));
        assertTrue(currentFiles.toString(), currentFiles.containsAll(currentOnly));
        assertTrue(current.diagnostics().toString(), current.diagnostics().stream().noneMatch(diagnostic -> diagnostic.severity() == ExportDiagnostic.Severity.ERROR));
    }

    @Test
    public void onlyThePlacedObjectTemplateShips() throws Exception {
        File pack = objectPack(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        RecordingProgress progress = new RecordingProgress();
        List<String> written = new ArrayList<>();
        WorldgenDatapackExporter exporter = new WorldgenDatapackExporter(target -> ir -> {
            SortedMap<String, byte[]> output = WorldgenJsonWriter.forTarget(target).write(ir);
            written.addAll(output.keySet());
            return output;
        }, new PackCompiler(List.of(new DimensionStage(), new UnreachablePlainsStage(), new ObjectStage())));

        WorldgenExportResult result = exporter.export(request(pack, root).format("directory").build(), progress);

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        assertTrue(written.toString(), written.stream().anyMatch(file -> file.endsWith(".nbt") && file.contains("/rocks/pillar")));
        assertTrue(written.toString(), written.contains("data/iris_export/worldgen/biome/plains.json"));
        assertEquals(written.size(), result.trim().filesBefore());
        List<String> files = files(result.output());
        List<String> templates = files.stream().filter(file -> file.endsWith(".nbt")).toList();
        assertFalse(files.toString(), templates.isEmpty());
        assertTrue(templates.toString(), templates.stream().allMatch(file -> file.contains("/rocks/boulder")));
        assertTrue(files.toString(), files.stream().noneMatch(file -> file.contains("/rocks/pillar")));
        assertTrue(files.toString(), files.contains("data/iris_export/worldgen/biome/forest.json"));
        assertFalse(files.toString(), files.contains("data/iris_export/worldgen/biome/plains.json"));
        assertTrue(result.trim().toString(), result.trim().filesRemoved() > 0);
        assertEquals(files.size(), result.trim().filesAfter());
        assertEquals(List.of(result.trim()), progress.trims);
        List<ExportDiagnostic> trimmed = result.diagnostics().stream().filter(diagnostic -> diagnostic.limit() == ExportLimit.TRIMMED_CONTENT).toList();
        assertEquals(1, trimmed.size());
        assertSame(ExportDiagnostic.Severity.INFO, trimmed.getFirst().severity());
        assertTrue(trimmed.getFirst().message(), trimmed.getFirst().message().contains(result.trim().filesRemoved() + " of " + result.trim().filesBefore() + " files"));
    }

    private WorldgenDatapackExporter exporter() {
        return new WorldgenDatapackExporter(target -> new SummaryWriter(target, writerTargets),
                new PackCompiler(List.of(new DimensionStage(), new ImageMapStage())));
    }

    private static WorldgenExportRequest.Builder request(File pack, Path root) {
        return WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(root);
    }

    private static File objectPack(File pack) throws IOException {
        CompatTestPlatform.write(pack, "dimensions/" + ExportTestPacks.DIMENSION + ".json", "{\"regions\":[\"region\"]}");
        CompatTestPlatform.write(pack, "regions/region.json", "{\"landBiomes\":[\"forest\",\"plains\"]}");
        CompatTestPlatform.write(pack, "biomes/forest.json", """
                {"name":"Forest","objects":[{"place":["rocks/boulder"],"chance":0.5,"density":1}]}
                """);
        CompatTestPlatform.write(pack, "biomes/plains.json", """
                {"name":"Plains","objects":[{"place":["rocks/pillar"],"chance":0.5,"density":1}]}
                """);
        writeObject(pack, "rocks/boulder", "minecraft:stone", "minecraft:grass_block");
        writeObject(pack, "rocks/pillar", "minecraft:stone", "minecraft:stone", "minecraft:stone");
        return pack;
    }

    private static void writeObject(File pack, String key, String... column) throws IOException {
        File file = new File(pack, "objects/" + key + ".iob");
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
            throw new IOException("Could not create " + file.getParentFile());
        }
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(1);
            out.writeInt(column.length);
            out.writeInt(1);
            out.writeUTF("Iris V2 IOB;");
            out.writeShort(column.length);
            for (String entry : column) {
                out.writeUTF(entry);
            }
            out.writeInt(column.length);
            for (int index = 0; index < column.length; index++) {
                out.writeShort(0);
                out.writeShort(index - column.length / 2);
                out.writeShort(0);
                out.writeShort(index);
            }
            out.writeInt(0);
        }
    }

    private static List<String> entries(Path root) throws IOException {
        try (Stream<Path> children = Files.list(root)) {
            return children.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static List<String> files(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).map(file -> directory.relativize(file).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private static String summary(Path zip) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            return new String(file.getInputStream(file.getEntry("summary.txt")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record SummaryWriter(DataVersion target, List<DataVersion> targets) implements WorldgenJsonWriter {
        @Override
        public SortedMap<String, byte[]> write(ExportIr ir) {
            assertSame(target, ir.target());
            targets.add(target);
            String summary = "namespace=" + ir.namespace() + "\n"
                    + "target=" + ir.target().getVersion() + "\n"
                    + "mode=" + ir.mode() + "\n"
                    + "dimension=" + ir.dimension() + "\n"
                    + "noises=" + ir.noises() + "\n"
                    + "router=" + ir.router() + "\n"
                    + "climate=" + ir.climate() + "\n"
                    + "biomes=" + ir.biomes() + "\n"
                    + "material=" + ir.material() + "\n"
                    + "diagnostics=" + ir.diagnostics() + "\n";
            SortedMap<String, byte[]> files = new TreeMap<>();
            files.put("pack.mcmeta", ("{\"pack\":{\"description\":\"" + ir.namespace() + "\"}}\n").getBytes(StandardCharsets.UTF_8));
            files.put("summary.txt", summary.getBytes(StandardCharsets.UTF_8));
            return files;
        }
    }

    private static final class UnreachablePlainsStage implements CompileStage {
        private static final String FOREST = WorldgenExportRequest.DEFAULT_NAMESPACE + ":forest";
        private static final String PLAINS = WorldgenExportRequest.DEFAULT_NAMESPACE + ":plains";
        private static final Field ZERO = new Field.Const(0.0);

        @Override
        public String name() {
            return "biomes";
        }

        @Override
        public void apply(PackCompileContext context) {
            DataVersion target = context.capabilities().target();
            context.setBiomes(List.of(new BiomeDef("forest", ExportIrFixtures.biome()), new BiomeDef("plains", ExportIrFixtures.biome())));
            ClimateLayout layout = new ClimateLayout(List.of(new ClimateLayout.ClimateEntry(FOREST, ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 1.0)));
            context.setBiomeCodes(new BiomeCodes(ZERO, ZERO, ZERO, layout, Map.of(FOREST, "forest", PLAINS, "plains")));
        }
    }

    private static final class RecordingProgress implements WorldgenExportProgress {
        private final List<String> stages = new ArrayList<>();
        private final List<Integer> resourceTotals = new ArrayList<>();
        private final List<ContentTrim> trims = new ArrayList<>();

        @Override
        public void stage(String stage) {
            stages.add(stage);
        }

        @Override
        public void resources(int done, int total) {
            resourceTotals.add(total);
        }

        @Override
        public void trimmed(ContentTrim trim) {
            trims.add(trim);
        }
    }
}
