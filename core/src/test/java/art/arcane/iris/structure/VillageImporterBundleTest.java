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

package art.arcane.iris.structure;

import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureLoss;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteMode;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class VillageImporterBundleTest {
    private static final StructureKey BUNDLE_KEY = StructureKey.parse("iris:minecraft_village_plains");
    private static final StructureSource SOURCE = new StructureSource(
            StructureSource.Kind.VANILLA,
            StructureKey.parse("minecraft:village_plains"),
            "1.21.9-R0.1-SNAPSHOT",
            ""
    );

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bundleOwnsObjectsPiecesPoolsAndRootAsOneTransaction() throws IOException {
        Path root = temporaryFolder.newFolder("village-bundle").toPath();
        StructureResourceBundle bundle = bundle("object-v1");
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.write(bundle, StructureWriteMode.ADD_ONLY);

        assertEquals(StructureWriteResult.Status.ADDED, result.status());
        assertEquals(4, bundle.resources().size());
        assertTrue(bundle.resources().containsKey("objects/minecraft_village_plains/piece/minecraft/house.iob"));
        assertTrue(bundle.resources().containsKey("jigsaw-pieces/minecraft_village_plains/piece/minecraft/house.json"));
        assertTrue(bundle.resources().containsKey("jigsaw-pools/minecraft_village_plains/pool/minecraft/houses.json"));
        assertTrue(bundle.resources().containsKey("structures/minecraft_village_plains.json"));
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(writer.ownershipManifestPath(BUNDLE_KEY))
        );
        assertEquals(SOURCE, manifest.source());
        assertEquals(StructureBackend.IRIS_ASSEMBLY, manifest.backend());
        assertEquals(bundle.resources().keySet(), manifest.resourceHashes().keySet());
    }

    @Test
    public void overwriteRefusesOneModifiedVillageResourceAndPreservesTheWholeClosure() throws IOException {
        Path root = temporaryFolder.newFolder("village-overwrite").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1"), StructureWriteMode.ADD_ONLY);
        Path object = root.resolve("objects/minecraft_village_plains/piece/minecraft/house.iob");
        Path structure = root.resolve("structures/minecraft_village_plains.json");
        byte[] originalStructure = Files.readAllBytes(structure);
        byte[] handEdit = "hand-edit".getBytes(StandardCharsets.UTF_8);
        Files.write(object, handEdit);

        StructureWriteResult overwrite = writer.write(bundle("object-v2"), StructureWriteMode.OVERWRITE);

        assertEquals(StructureWriteResult.Status.ADDED, initial.status());
        assertEquals(StructureWriteResult.Status.OWNERSHIP_CONFLICT, overwrite.status());
        assertArrayEquals(handEdit, Files.readAllBytes(object));
        assertArrayEquals(originalStructure, Files.readAllBytes(structure));
    }

    @Test
    public void sourcePathsDoNotCollapseDifferentNamespacedKeys() {
        String nestedPath = VillageImporter.pieceName("village", "mod:a/b");
        String underscoredNamespace = VillageImporter.pieceName("village", "mod_a:b");

        assertEquals("village/piece/mod/a/b", nestedPath);
        assertEquals("village/piece/mod_a/b", underscoredNamespace);
        assertNotEquals(nestedPath, underscoredNamespace);
    }

    @Test
    public void registeredJigsawMetadataIncludesAncientCitySourceAndAssemblyContract() {
        Map<String, Object> structure = VillageImporter.structureJson(
                "minecraft:ancient_city",
                "village/pool/minecraft/start",
                6,
                81
        );

        assertEquals(6, structure.get("maxDepth"));
        assertEquals(6, structure.get("maxSizeChunks"));
        assertEquals("STRUCTURE_PIECE", structure.get("placeMode"));
        assertEquals(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH.name(),
                structure.get("branchFailurePolicy"));
        assertEquals("minecraft:ancient_city", structure.get("vanillaSource"));
    }

    private StructureResourceBundle bundle(String objectContent) {
        String pieceKey = "minecraft_village_plains/piece/minecraft/house";
        String poolKey = "minecraft_village_plains/pool/minecraft/houses";
        Map<String, byte[]> objects = Map.of(pieceKey, objectContent.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> piece = new LinkedHashMap<>();
        piece.put("object", pieceKey);
        piece.put("connectors", List.of());
        piece.put("rotatable", true);
        Map<String, Map<String, Object>> pieces = Map.of(pieceKey, piece);
        Map<String, Object> poolEntry = new LinkedHashMap<>();
        poolEntry.put("piece", pieceKey);
        poolEntry.put("weight", 1);
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("pieces", List.of(poolEntry));
        Map<String, Map<String, Object>> pools = Map.of(poolKey, pool);
        Map<String, Object> structure = VillageImporter.structureJson(
                SOURCE.key().value(),
                poolKey,
                6,
                80
        );
        Set<StructureCapability> capabilities = EnumSet.of(
                StructureCapability.BLOCKS,
                StructureCapability.CONNECTORS,
                StructureCapability.IRIS_PLACEMENT
        );
        List<StructureLoss> losses = List.of(StructureLoss.warning(
                StructureCapability.NATIVE_PLACEMENT,
                "native_placement_settings_not_imported",
                "Native placement metadata outside the Iris assembly contract was not imported."
        ));
        return VillageImporter.buildBundle(
                BUNDLE_KEY,
                SOURCE,
                objects,
                pieces,
                pools,
                structure,
                capabilities,
                losses
        );
    }
}
