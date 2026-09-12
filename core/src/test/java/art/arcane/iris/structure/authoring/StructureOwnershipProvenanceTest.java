package art.arcane.iris.structure.authoring;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StructureOwnershipProvenanceTest {
    private static final StructureKey KEY = new StructureKey("iris", "village");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void legacyManifestWithoutProvenanceLoadsAsCreated() {
        String resourceHash = StructureHash.sha256("object".getBytes(StandardCharsets.UTF_8));
        String json = "{"
                + "\"schemaVersion\":1,"
                + "\"structure\":{\"namespace\":\"iris\",\"path\":\"village\"},"
                + "\"source\":{\"kind\":\"IRIS\",\"key\":{\"namespace\":\"iris\",\"path\":\"village\"},"
                + "\"version\":\"\",\"contentHash\":\"\"},"
                + "\"backend\":\"IRIS_ASSEMBLY\","
                + "\"capabilities\":[\"BLOCKS\"],"
                + "\"losses\":[],"
                + "\"resourceHashes\":{\"objects/village.iob\":\"" + resourceHash + "\"}"
                + "}";

        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                json.getBytes(StandardCharsets.UTF_8));

        assertEquals(StructureOwnershipManifest.Origin.CREATED, manifest.provenance().origin());
        assertTrue(manifest.provenance().sourceResourceHashes().isEmpty());
    }

    @Test
    public void adoptedProvenanceSurvivesOrdinaryOwnedOverwrite() throws Exception {
        Path root = temporaryFolder.newFolder("preserve").toPath();
        StructureResourceBundle first = bundle("object-v1", "structure-v1");
        Map<String, String> hashes = Map.of(
                "objects/village.iob", first.resources().get("objects/village.iob").contentHash(),
                "structures/village.json", first.resources().get("structures/village.json").contentHash());
        StructureOwnershipManifest.Provenance provenance = new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.ADOPTED_CLONE,
                UUID.randomUUID().toString(),
                StructureHash.sha256("plan".getBytes(StandardCharsets.UTF_8)),
                StructureHash.sha256("closure".getBytes(StandardCharsets.UTF_8)),
                1_754_740_800_000L,
                hashes,
                Map.of(
                        "objects/village.iob", "objects/village.iob",
                        "structures/village.json", "structures/village.json"),
                StructureOwnershipManifest.RollbackDisposition.DELETE_CREATED_IF_UNCHANGED);
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.writeVerified(
                first,
                StructureWriteOptions.addOnly(),
                StructureTransactionReadSet.empty(),
                provenance);
        assertEquals(initial.failure().map(Throwable::toString).orElse(""),
                StructureWriteResult.Status.ADDED, initial.status());

        StructureWriteResult overwrite = writer.write(bundle("object-v2", "structure-v2"), StructureWriteMode.OVERWRITE);

        assertEquals(overwrite.failure().map(Throwable::toString).orElse(""),
                StructureWriteResult.Status.OVERWRITTEN, overwrite.status());
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(writer.ownershipManifestPath(KEY)));
        assertEquals(provenance, manifest.provenance());
    }

    @Test
    public void managedDatapackWriteCommitsManagedOriginWithTheManifest() throws Exception {
        Path root = temporaryFolder.newFolder("managed").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.writeManagedDatapack(
                datapackBundle("object", "structure"),
                StructureWriteMode.ADD_ONLY
        );

        assertEquals(result.failure().map(Throwable::toString).orElse(""),
                StructureWriteResult.Status.ADDED, result.status());
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(writer.ownershipManifestPath(KEY)));
        assertEquals(StructureOwnershipManifest.Origin.MANAGED_DATAPACK, manifest.provenance().origin());
        assertEquals(manifest.resourceHashes(), manifest.provenance().sourceResourceHashes());
    }

    @Test
    public void managedDatapackOverwriteUpgradesMatchingOrdinaryImportProvenance() throws Exception {
        Path root = temporaryFolder.newFolder("managed-upgrade").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        assertEquals(StructureWriteResult.Status.ADDED,
                writer.write(datapackBundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY).status());

        StructureWriteResult result = writer.writeManagedDatapack(
                datapackBundle("object-v2", "structure-v2"),
                StructureWriteMode.OVERWRITE
        );

        assertEquals(result.failure().map(Throwable::toString).orElse(""),
                StructureWriteResult.Status.OVERWRITTEN, result.status());
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(writer.ownershipManifestPath(KEY)));
        assertEquals(StructureOwnershipManifest.Origin.MANAGED_DATAPACK, manifest.provenance().origin());
    }

    private StructureResourceBundle bundle(String object, String structure) {
        return StructureResourceBundle.builder(KEY)
                .source(StructureSource.of(StructureSource.Kind.IRIS, KEY))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capabilities(List.of(StructureCapability.BLOCKS, StructureCapability.CONNECTORS))
                .resource("objects/village.iob", object.getBytes(StandardCharsets.UTF_8))
                .textResource("structures/village.json", structure)
                .build();
    }

    private StructureResourceBundle datapackBundle(String object, String structure) {
        return StructureResourceBundle.builder(KEY)
                .source(StructureSource.of(
                        StructureSource.Kind.DATAPACK,
                        new StructureKey("example", "village")))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capabilities(List.of(StructureCapability.BLOCKS, StructureCapability.CONNECTORS))
                .resource("objects/village.iob", object.getBytes(StandardCharsets.UTF_8))
                .textResource("structures/village.json", structure)
                .build();
    }
}
