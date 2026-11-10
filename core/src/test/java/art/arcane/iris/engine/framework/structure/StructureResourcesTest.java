package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.core.modules.IrisModuleHost;
import art.arcane.iris.core.project.ModuleResourceSchemas;
import art.arcane.iris.core.project.SchemaTestPlatform;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.host.ResourceKindRegistration;
import com.volmit.host.ValidatorRegistration;
import com.volmit.resource.ResourceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The pilot: structures, jigsaw pools and pieces are resource kinds of module {@code iris:structure}, read from the
 * folders and files packs have always had, and Iris has no loaders of its own for them any more.
 */
public class StructureResourcesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void theStructureModuleOwnsTheKindsAndTheirValidator() throws Exception {
        IrisData data = IrisData.openValidation(castlePack("owned").toFile());
        try {
            var catalog = data.getModules().pack().catalog();

            var kinds = catalog.resourceKinds().stream().map(ResourceKindRegistration::kind)
                    .filter(kind -> kind.id().toString().startsWith("iris:structure/")).toList();
            assertEquals(List.of("iris:structure/piece", "iris:structure/pool", "iris:structure/structure"),
                    kinds.stream().map(kind -> kind.id().toString()).toList());
            assertEquals(List.of("jigsaw-pieces", "jigsaw-pools", "structures"), kinds.stream().map(kind -> kind.folder()).toList());
            assertTrue(catalog.validators().stream().map(ValidatorRegistration::validator).anyMatch(StructureValidator.class::isInstance));
            assertFalse(data.getLoaders().containsKey(IrisStructure.class));
            assertFalse(data.getLoaders().containsKey(IrisJigsawPool.class));
            assertFalse(data.getLoaders().containsKey(IrisJigsawPiece.class));
        } finally {
            data.close();
        }
    }

    @Test
    public void theStructureModuleListsThePacksStructuresForIrissIndexThroughItsLocator() throws Exception {
        IrisData data = IrisData.openValidation(castlePack("listed").toFile());
        try {
            assertEquals(Set.of(new ResourceId("listed", "castle")), new StructureLocating().structures(data.getPackResources()));
            assertEquals(Set.of("castle"), ModuleServices.structureKeys(data));
        } finally {
            data.close();
        }
    }

    @Test
    public void structuresPoolsAndPiecesAreReadThroughPackResources() throws Exception {
        Path pack = castlePack("castles");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            IrisStructure castle = StructureResources.structure(data.getPackResources(),"castle");

            assertEquals("castle/start", castle.getStartPool());
            assertEquals(5, castle.getMaxDepth());
            assertSame(castle, data.getPackResources().find(StructureResources.STRUCTURES, new ResourceId("castles", "castle")).orElseThrow());
            assertEquals("castle/start", StructureResources.pool(data.getPackResources(), "castle/start").getPieces().getFirst().getPiece());
            assertEquals("castle/start", StructureResources.piece(data.getPackResources(), "castle/start").getObject());
            assertEquals(List.of("castle"), StructureResources.structureKeys(data.getPackResources()));
            assertEquals(List.of("castle/start"), StructureResources.pieceKeys(data.getPackResources()));
            assertNull(StructureResources.structure(data.getPackResources(),"missing"));
            assertNull(StructureResources.structure(data.getPackResources()," "));
        } finally {
            data.close();
        }
    }

    @Test
    public void aBrokenStructureIsAbsentToLookupsButAnErrorToPackResources() throws Exception {
        Path pack = castlePack("broken");
        write(pack, "structures/ruin.json", "{\"maxDepth\":");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            assertNull(StructureResources.structure(data.getPackResources(),"ruin"));
            assertThrows(IllegalStateException.class,
                    () -> data.getPackResources().find(StructureResources.STRUCTURES, new ResourceId("broken", "ruin")));
        } finally {
            data.close();
        }
    }

    @Test
    public void rewrittenFilesAreReadAgainAfterInvalidationDumpAndHotload() throws Exception {
        Path pack = castlePack("reloaded");
        IrisData data = IrisData.openValidation(pack.toFile());
        try {
            assertEquals(5, StructureResources.structure(data.getPackResources(),"castle").getMaxDepth());
            write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\",\"maxDepth\":9}");
            write(pack, "structures/tower.json", "{\"startPool\":\"castle/start\"}");
            assertEquals("a snapshot keeps what it read", 5, StructureResources.structure(data.getPackResources(),"castle").getMaxDepth());

            data.invalidateStructureResources();
            assertEquals(9, StructureResources.structure(data.getPackResources(),"castle").getMaxDepth());
            assertEquals(List.of("castle", "tower"), StructureResources.structureKeys(data.getPackResources()));

            write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\",\"maxDepth\":3}");
            data.dump();
            assertEquals(3, StructureResources.structure(data.getPackResources(),"castle").getMaxDepth());
        } finally {
            data.close();
        }

        IrisData runtime = IrisData.openRuntime(pack.toFile());
        try {
            assertEquals(3, StructureResources.structure(runtime.getPackResources(),"castle").getMaxDepth());
            write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\",\"maxDepth\":2}");
            runtime.hotloaded();
            assertEquals(2, StructureResources.structure(runtime.getPackResources(),"castle").getMaxDepth());
        } finally {
            runtime.close();
        }
    }

    @Test
    public void studioDescribesStructuresAsIrisDoesAndCompletesTheirPools() throws Exception {
        Path pack = castlePack("studio");
        IrisData data = IrisData.openValidation(pack.toFile());
        try (SchemaTestPlatform ignored = SchemaTestPlatform.bind(temporaryFolder.getRoot())) {
            List<JSONObject> entries = ModuleResourceSchemas.entries(data, true);

            assertEquals(List.of("./.iris/schema/jigsaw-pieces-schema.json", "./.iris/schema/jigsaw-pools-schema.json",
                    "./.iris/schema/structures-schema.json"),
                    entries.stream().map(entry -> entry.getString("url")).filter(url -> url.contains("jigsaw") || url.contains("structures-")).toList());
            JSONObject structures = new JSONObject(Files.readString(pack.resolve(".iris/schema/structures-schema.json")));
            JSONObject startPool = structures.getJSONObject("properties").getJSONObject("startPool");
            assertEquals("#/definitions/erzjigsaw-pools", startPool.optString("$ref", startPool.toString()));
            assertEquals("[\"castle/start\"]",
                    structures.getJSONObject("definitions").getJSONObject("erzjigsaw-pools").getJSONArray("enum").toString());
        } finally {
            data.close();
        }
    }

    @Test
    public void irisReservesTheFoldersItStillReadsItself() {
        var reserved = IrisModuleHost.get().reservedFolders();

        assertTrue(reserved.containsAll(List.of("biomes", "regions", "dimensions", "objects", "snippet", "modules")));
        assertFalse(reserved.contains("structures"));
        assertFalse(reserved.contains("jigsaw-pools"));
        assertFalse(reserved.contains("jigsaw-pieces"));
    }

    private Path castlePack(String name) throws Exception {
        File pack = temporaryFolder.newFolder(name);
        write(pack.toPath(), "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack.toPath(), "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack.toPath(), "biomes/biome.json", "{\"name\":\"Biome\"}");
        write(pack.toPath(), "structures/castle.json", "{\"startPool\":\"castle/start\",\"maxDepth\":5}");
        write(pack.toPath(), "jigsaw-pools/castle/start.json", "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack.toPath(), "jigsaw-pieces/castle/start.json", "{\"object\":\"castle/start\",\"connectors\":[]}");
        return pack.toPath();
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
