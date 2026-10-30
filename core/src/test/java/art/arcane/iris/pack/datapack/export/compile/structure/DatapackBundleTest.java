package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatapackBundleTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void bundledDatapackFilesAreMerged() throws Exception {
        File imports = temporaryFolder.newFolder("imports");
        File first = StructureTestPacks.zip(imports, "first.zip", Map.of(
                "first/pack.mcmeta", """
                        {"pack": {"description": "first", "min_format": 107, "max_format": 121},
                         "overlays": {"entries": [
                           {"directory": "mc_26_3", "min_format": 121, "max_format": 121},
                           {"directory": "mc_26_2", "min_format": 107, "max_format": 107}]}}
                        """,
                "first/pack.png", "png",
                "first/data/first/structure/hut.nbt", "hut-template",
                "first/data/first/worldgen/processor_list/hut.json", "{\"processors\": []}",
                "first/mc_26_3/data/first/worldgen/processor_list/hut.json", "{\"processors\": [{\"processor_type\": \"minecraft:nop\"}]}",
                "first/mc_26_2/data/first/worldgen/processor_list/old.json", "{\"processors\": []}"));
        File second = StructureTestPacks.zip(imports, "second.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/second/worldgen/structure_set/camp.json", "{\"structures\": []}"));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(first, second), ""), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            SortedMap<String, String> binaries = StructureTestPacks.binaries(context);
            assertEquals("hut-template", binaries.get("data/first/structure/hut.nbt"));
            assertEquals("{\"processors\": [{\"processor_type\": \"minecraft:nop\"}]}", binaries.get("data/first/worldgen/processor_list/hut.json"));
            assertEquals("{\"structures\": []}", binaries.get("data/second/worldgen/structure_set/camp.json"));
            assertFalse(binaries.containsKey("data/first/worldgen/processor_list/old.json"));
            for (String path : binaries.keySet()) {
                assertTrue(path, path.startsWith("data/"));
            }
            assertTrue(StructureTestPacks.diagnostics(context, ExportLimit.DATAPACK_VERSION).isEmpty());
            assertTrue(StructureTestPacks.diagnostics(context, ExportLimit.DATAPACK_COLLISION).isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void tagFilesAreUnionedRespectingReplace() throws Exception {
        File imports = temporaryFolder.newFolder("imports");
        File first = StructureTestPacks.zip(imports, "a.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/shared/tags/worldgen/structure/towns.json", "{\"values\": [\"a:one\"]}",
                "data/shared/tags/item/loot.json", "{\"values\": [\"a:x\"]}"));
        File second = StructureTestPacks.zip(imports, "b.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/shared/tags/worldgen/structure/towns.json", "{\"replace\": false, \"values\": [\"b:two\", \"a:one\"]}",
                "data/shared/tags/item/loot.json", "{\"replace\": true, \"values\": [\"b:y\"]}"));
        File third = StructureTestPacks.zip(imports, "c.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/shared/tags/item/loot.json", "{\"values\": [{\"id\": \"c:z\", \"required\": false}]}"));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(first, second, third), ""), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": [\"a:one\", \"b:two\"]}"),
                    StructureTestPacks.json(context).get("data/shared/tags/worldgen/structure/towns.json"));
            assertEquals(StructureTestPacks.parse("{\"replace\": true, \"values\": [\"b:y\", {\"id\": \"c:z\", \"required\": false}]}"),
                    StructureTestPacks.json(context).get("data/shared/tags/item/loot.json"));
            assertFalse(StructureTestPacks.binaries(context).containsKey("data/shared/tags/item/loot.json"));
        } finally {
            data.close();
        }
    }

    @Test
    public void pathCollisionKeepsLaterImportWithDiagnostic() throws Exception {
        File imports = temporaryFolder.newFolder("imports");
        File first = StructureTestPacks.zip(imports, "a.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/shared/worldgen/processor_list/rot.json", "{\"processors\": [\"first\"]}",
                "data/shared/worldgen/processor_list/same.json", "{\"processors\": []}"));
        File second = StructureTestPacks.zip(imports, "b.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/shared/worldgen/processor_list/rot.json", "{\"processors\": [\"second\"]}",
                "data/shared/worldgen/processor_list/same.json", "{\"processors\": []}"));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(first, second), ""), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            assertEquals("{\"processors\": [\"second\"]}", StructureTestPacks.binaries(context).get("data/shared/worldgen/processor_list/rot.json"));
            List<ExportDiagnostic> collisions = StructureTestPacks.diagnostics(context, ExportLimit.DATAPACK_COLLISION);
            assertEquals(collisions.toString(), 1, collisions.size());
            assertEquals("data/shared/worldgen/processor_list/rot.json", collisions.getFirst().resource());
            assertTrue(collisions.getFirst().message(), collisions.getFirst().message().contains(first.getName()));
            assertTrue(collisions.getFirst().message(), collisions.getFirst().message().contains(second.getName()));
        } finally {
            data.close();
        }
    }

    @Test
    public void unsupportedFormatIsBundledWithDiagnostic() throws Exception {
        File imports = temporaryFolder.newFolder("imports");
        File old = StructureTestPacks.zip(imports, "old.zip", Map.of(
                "pack.mcmeta", "{\"pack\": {\"description\": \"old\", \"pack_format\": 48}}",
                "data/old/worldgen/processor_list/rot.json", "{\"processors\": []}"));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(old), ""), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            assertTrue(StructureTestPacks.binaries(context).containsKey("data/old/worldgen/processor_list/rot.json"));
            List<ExportDiagnostic> versions = StructureTestPacks.diagnostics(context, ExportLimit.DATAPACK_VERSION);
            assertEquals(versions.toString(), 1, versions.size());
            assertTrue(versions.getFirst().message(), versions.getFirst().message().contains("48.0 to 48.*"));
        } finally {
            data.close();
        }
    }
}
