/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.pack.ingest;

import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.pack.ingest.catalog.CatalogNamespace;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModIngesterPackTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writesLoadableTargetPackWithoutTouchingSource() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Map<String, String> before = snapshot(source);
        Path target = root.resolve("packs").resolve("modded");

        ModIngestReport report = ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));

        assertEquals(before, snapshot(source));
        assertEquals(target, report.targetPack());
        assertTrue(Files.isRegularFile(target.resolve("dimensions/modded.json")));
        assertFalse(Files.exists(target.resolve("dimensions/overworld.json")));
        assertFalse(Files.exists(target.resolve(".git")));
        assertFalse(Files.exists(target.resolve("source.code-workspace")));
        PackValidationResult validation = PackValidator.validate(target.toFile());
        assertTrue(validation.getBlockingErrors().toString(), validation.isLoadable());
    }

    @Test
    public void manifestAndSummaryDescribeTheIngest() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("modded");

        ModIngestReport report = ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));
        JsonObject manifest = ModIngestFixture.readJson(target.resolve("ingest/manifest.json"));

        assertEquals("fingerprint-1", manifest.get("catalogFingerprint").getAsString());
        assertEquals("modded", manifest.get("dimension").getAsString());
        assertEquals(List.of("examplemod"), ModIngestFixture.strings(manifest.get("namespaces")));
        List<String> generated = ModIngestFixture.strings(manifest.get("generatedFiles"));
        List<String> modified = ModIngestFixture.strings(manifest.get("modifiedFiles"));
        assertTrue(generated.toString(), generated.contains("biomes/ingest/examplemod/scorched_mesa.json"));
        assertTrue(modified.toString(), modified.containsAll(List.of("dimensions/modded.json", "regions/hot.json",
                "regions/temperate.json", "biomes/temperate/forest.json", "biomes/temperate/plains.json")));
        assertFalse(modified.contains("biomes/unused/orphan.json"));
        assertEquals(report.items().size(), manifest.getAsJsonArray("items").size());
        assertTrue(report.summaryLines().toString(),
                report.summaryLines().contains("examplemod: 5 native, 6 adopted, 3 passthrough, 6 skipped"));
    }

    @Test
    public void existingTargetIsRejectedUnlessReplaceIsRequested() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("modded");
        ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));

        assertThrows(FileAlreadyExistsException.class,
                () -> ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false)));
    }

    @Test
    public void replacingTargetProducesIdenticalOutput() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("modded");
        ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), true));
        Map<String, String> first = snapshot(target);
        ModIngestFixture.write(target, "biomes/stale.json", "{\"name\": \"Stale\"}");

        ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), true));

        assertEquals(first, snapshot(target));
        assertEquals(List.of("modded"), directoryNames(root.resolve("packs")));
    }

    @Test
    public void replaceRefusesAPackThatWasNotWrittenByIngest() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path handmade = root.resolve("packs").resolve("handmade");
        ModIngestFixture.write(handmade, "dimensions/handmade.json", "{\"name\": \"Handmade\"}");

        IOException refused = assertThrows(IOException.class, () -> ModIngester.ingest(
                ModIngestFixture.request(source, handmade, Set.of(ModIngestFixture.MOD), true)));

        assertTrue(refused.getMessage(), refused.getMessage().contains("ingest/manifest.json"));
        assertEquals(Map.of("dimensions/handmade.json", "{\"name\": \"Handmade\"}"), snapshot(handmade));
    }

    @Test
    public void replaceNeverDeletesTheSourcePackThroughAnotherSpelling() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Map<String, String> before = snapshot(source);
        Path respelled = root.resolve("SOURCE");
        boolean caseInsensitive = Files.isDirectory(respelled);

        if (caseInsensitive) {
            assertThrows(IOException.class, () -> ModIngester.ingest(
                    ModIngestFixture.request(source, respelled, Set.of(ModIngestFixture.MOD), true)));
        } else {
            ModIngester.ingest(ModIngestFixture.request(source, respelled, Set.of(ModIngestFixture.MOD), true));
        }

        assertEquals(before, snapshot(source));
    }

    @Test
    public void failedReplaceKeepsThePreviousOutput() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("modded");
        ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));
        Map<String, String> first = snapshot(target);

        assertThrows(IOException.class, () -> ModIngester.ingest(new ModIngestRequest(
                source, target, "missing", ModIngestFixture.catalog(), Set.of(ModIngestFixture.MOD), Set.of(), true)));

        assertEquals(first, snapshot(target));
        assertEquals(List.of("modded"), directoryNames(root.resolve("packs")));
    }

    @Test
    public void targetInsideSourceIsRejected() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);

        assertThrows(IOException.class, () -> ModIngester.ingest(
                ModIngestFixture.request(source, source.resolve("nested"), Set.of(), true)));
        assertThrows(IOException.class, () -> ModIngester.ingest(
                ModIngestFixture.request(source, source, Set.of(), true)));
        assertTrue(Files.isRegularFile(source.resolve("dimensions/overworld.json")));
    }

    @Test
    public void ingestedPackCannotBeUsedAsSource() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("modded");
        ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(ModIngestFixture.MOD), false));

        IOException failure = assertThrows(IOException.class, () -> ModIngester.ingest(new ModIngestRequest(
                target, root.resolve("packs").resolve("again"), "modded", ModIngestFixture.catalog(), Set.of(), Set.of(), false)));

        assertTrue(failure.getMessage(), failure.getMessage().contains("original pack"));
        assertFalse(Files.exists(root.resolve("packs").resolve("again")));
    }

    @Test
    public void emptyNamespaceSelectionIngestsEveryModNamespace() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("everything");

        ModIngestReport report = ModIngester.ingest(ModIngestFixture.request(source, target, Set.of(), false));

        assertNotNull(ModIngestFixture.item(report, "othermod:bamboo_marsh"));
        assertEquals(ModIngestTier.NATIVE, ModIngestFixture.item(report, "othermod:daisies").tier());
        assertTrue(Files.isRegularFile(target.resolve("biomes/ingest/othermod/bamboo_marsh.json")));
        assertEquals(List.of("examplemod", "othermod"),
                ModIngestFixture.strings(ModIngestFixture.readJson(target.resolve("ingest/manifest.json")).get("namespaces")));
        assertTrue(report.items().stream().noneMatch(item -> item.namespace().equals("minecraft")));
    }

    @Test
    public void namespacesWithoutAnyIngestedContentAreLeftOutOfTheManifestAndSummary() throws Exception {
        Path root = folder.getRoot().toPath();
        Path source = ModIngestFixture.writeSourcePack(root);
        Path target = root.resolve("packs").resolve("quiet");
        WorldgenCatalog fixture = ModIngestFixture.catalog();
        List<CatalogNamespace> namespaces = new ArrayList<>(fixture.namespaces());
        namespaces.add(new CatalogNamespace("quietmod", "Quiet Mod", "1.0.0"));
        WorldgenCatalog catalog = new WorldgenCatalog(fixture.format(), fixture.minecraftVersion(), fixture.platform(),
                fixture.capturedAtEpochMillis(), fixture.contentFingerprint(), namespaces, fixture.biomes(),
                fixture.placedFeatures(), fixture.structures(), fixture.dimensionTypes(), fixture.levelStems());

        ModIngestReport report = ModIngester.ingest(new ModIngestRequest(source, target, "overworld", catalog, Set.of(), Set.of(), false));

        assertEquals(List.of("examplemod", "othermod"),
                ModIngestFixture.strings(ModIngestFixture.readJson(target.resolve("ingest/manifest.json")).get("namespaces")));
        assertTrue(report.summaryLines().stream().noneMatch(line -> line.startsWith("quietmod")));
        assertEquals(3, report.summaryLines().size());
    }

    private static List<String> directoryNames(Path root) throws IOException {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.map((Path entry) -> entry.getFileName().toString()).sorted().toList();
        }
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
