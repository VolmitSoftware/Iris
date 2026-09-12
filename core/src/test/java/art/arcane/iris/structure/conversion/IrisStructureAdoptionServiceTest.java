package art.arcane.iris.structure.conversion;

import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureAdoptionServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exclusiveGraphIsClaimedWithoutRewritingResourceBytes() throws Exception {
        Path root = temporaryFolder.newFolder("in-place").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        Path object = root.resolve("objects/village/house.iob");
        byte[] original = Files.readAllBytes(object);
        FileTime originalTime = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(object, originalTime);
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));

        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.unowned("village"));
        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionDisposition.IN_PLACE, plan.disposition());
        assertTrue(plan.summaryLines().toString(), plan.canApply());
        assertEquals(IrisStructureAdoptionResult.Status.APPLIED, result.status());
        assertArrayEquals(original, Files.readAllBytes(object));
        assertEquals(originalTime, Files.getLastModifiedTime(object));
        StructureOwnershipManifest manifest = readManifest(root, new StructureKey("iris", "village"));
        assertEquals(StructureOwnershipManifest.Origin.ADOPTED_EXISTING, manifest.provenance().origin());
        assertEquals(StructureOwnershipManifest.RollbackDisposition.NONE,
                manifest.provenance().rollbackDisposition());
        assertEquals(plan.planHash(), manifest.provenance().planHash());
    }

    @Test
    public void changedSourceRejectsApplyWithoutInstallingOwnership() throws Exception {
        Path root = temporaryFolder.newFolder("stale-source").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));
        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.unowned("village"));
        Files.writeString(root.resolve("objects/village/house.iob"), "changed", StandardCharsets.UTF_8);

        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionResult.Status.STALE, result.status());
        assertFalse(Files.exists(manifestPath(root, new StructureKey("iris", "village"))));
    }

    @Test
    public void newStructureRootInvalidatesExclusiveClaimReadSet() throws Exception {
        Path root = temporaryFolder.newFolder("stale-directory").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));
        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.unowned("village"));
        write(root, "structures/new-root.json", "{\"startPool\":\"village/start\"}");

        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionResult.Status.STALE, result.status());
        assertFalse(Files.exists(manifestPath(root, new StructureKey("iris", "village"))));
    }

    @Test
    public void sharedGraphUsesPrivateCloneAndRewritesEveryReference() throws Exception {
        Path root = temporaryFolder.newFolder("clone").toPath();
        writeGraph(root, "village", "shared/start", "shared/house", "shared/house");
        write(root, "structures/village.json",
                "{\"startPool\":\"shared/start\",\"branchFailurePolicy\":\"TERMINATE_BRANCH\"}");
        write(root, "structures/outpost.json", "{\"startPool\":\"shared/start\"}");
        byte[] sourceObject = Files.readAllBytes(root.resolve("objects/shared/house.iob"));
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));

        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.unowned("village"));
        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionDisposition.CLONE_REQUIRED, plan.disposition());
        assertEquals(new StructureKey("iris", "village-studio"), plan.targetStructure());
        assertEquals(IrisStructureAdoptionResult.Status.APPLIED, result.status());
        Path targetStructure = root.resolve("structures/village-studio.json");
        JsonObject structure = JsonParser.parseString(Files.readString(targetStructure)).getAsJsonObject();
        assertEquals("village-studio/pool/shared/start", structure.get("startPool").getAsString());
        assertEquals("TERMINATE_BRANCH", structure.get("branchFailurePolicy").getAsString());
        Path targetPool = root.resolve("jigsaw-pools/village-studio/pool/shared/start.json");
        JsonObject pool = JsonParser.parseString(Files.readString(targetPool)).getAsJsonObject();
        assertEquals("village-studio/piece/shared/house",
                pool.getAsJsonArray("pieces").get(0).getAsJsonObject().get("piece").getAsString());
        Path targetPiece = root.resolve("jigsaw-pieces/village-studio/piece/shared/house.json");
        JsonObject piece = JsonParser.parseString(Files.readString(targetPiece)).getAsJsonObject();
        assertEquals("village-studio/object/shared/house", piece.get("object").getAsString());
        assertArrayEquals(sourceObject,
                Files.readAllBytes(root.resolve("objects/village-studio/object/shared/house.iob")));
        StructureOwnershipManifest manifest = readManifest(root, plan.targetStructure());
        assertEquals(StructureOwnershipManifest.Origin.ADOPTED_CLONE, manifest.provenance().origin());
        assertEquals(StructureOwnershipManifest.RollbackDisposition.DELETE_CREATED_IF_UNCHANGED,
                manifest.provenance().rollbackDisposition());
        assertTrue(manifest.provenance().sourceToTargetPaths().containsKey("objects/shared/house.iob"));
    }

    @Test
    public void managedInputCannotBecomeAnInPlaceClaim() throws Exception {
        Path root = temporaryFolder.newFolder("managed").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));
        IrisStructureAdoptionRequest request = new IrisStructureAdoptionRequest(
                "village",
                Optional.empty(),
                IrisStructureAdoptionStrategy.AUTO,
                IrisStructureAdoptionInputKind.MANAGED_DATAPACK);

        IrisStructureAdoptionPlan plan = service.inspect(request);

        assertEquals(IrisStructureAdoptionDisposition.CLONE_REQUIRED, plan.disposition());
        assertEquals(new StructureKey("iris", "village-studio"), plan.targetStructure());
        assertTrue(plan.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == IrisStructureAdoptionDiagnostic.Code.MANAGED_INPUT_REQUIRES_CLONE));
    }

    @Test
    public void managedImportManifestIsVerifiedAndPrivateClonePreservesItsOrigin() throws Exception {
        Path root = temporaryFolder.newFolder("managed-manifest").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        writeManagedManifest(root, "village");
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));
        IrisStructureAdoptionRequest request = new IrisStructureAdoptionRequest(
                "village",
                Optional.empty(),
                IrisStructureAdoptionStrategy.AUTO,
                IrisStructureAdoptionInputKind.MANAGED_DATAPACK);

        IrisStructureAdoptionPlan plan = service.inspect(request);
        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionDisposition.CLONE_REQUIRED, plan.disposition());
        assertTrue(plan.summaryLines().toString(), plan.canApply());
        assertEquals(IrisStructureAdoptionResult.Status.APPLIED, result.status());
        StructureOwnershipManifest manifest = readManifest(root, plan.targetStructure());
        assertEquals(StructureOwnershipManifest.Origin.ADOPTED_MANAGED_CLONE,
                manifest.provenance().origin());
        assertEquals(StructureOwnershipManifest.RollbackDisposition.DELETE_CREATED_IF_UNCHANGED,
                manifest.provenance().rollbackDisposition());
    }

    @Test
    public void ordinaryDatapackManifestCannotBeForgedIntoManagedInput() throws Exception {
        Path root = temporaryFolder.newFolder("forged-managed").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        writeSourceManifest(root, "village", false);
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));
        IrisStructureAdoptionRequest request = new IrisStructureAdoptionRequest(
                "village",
                Optional.empty(),
                IrisStructureAdoptionStrategy.AUTO,
                IrisStructureAdoptionInputKind.MANAGED_DATAPACK);

        IrisStructureAdoptionPlan plan = service.inspect(request);

        assertEquals(IrisStructureAdoptionDisposition.BLOCKED, plan.disposition());
        assertFalse(plan.canApply());
        assertTrue(plan.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == IrisStructureAdoptionDiagnostic.Code.SOURCE_ALREADY_OWNED));
    }

    @Test
    public void unsafeCloneTargetPathReturnsBlockedDiagnostic() throws Exception {
        Path root = temporaryFolder.newFolder("unsafe-target").toPath();
        Path outside = temporaryFolder.newFolder("outside-target").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        Files.createDirectories(root.resolve("objects"));
        Files.createSymbolicLink(root.resolve("objects/copy"), outside);
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));

        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.cloneTo(
                "village",
                new StructureKey("iris", "copy")));

        assertEquals(IrisStructureAdoptionDisposition.BLOCKED, plan.disposition());
        assertFalse(plan.canApply());
        assertTrue(plan.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == IrisStructureAdoptionDiagnostic.Code.TARGET_RESOURCE_UNSAFE));
    }

    @Test
    public void cloneTargetAppearingAfterInspectionRejectsApply() throws Exception {
        Path root = temporaryFolder.newFolder("stale-target").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));
        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.cloneTo(
                "village",
                new StructureKey("iris", "copy")));
        write(root, "structures/copy.json", "user-content");

        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionResult.Status.STALE, result.status());
        assertEquals("user-content", Files.readString(root.resolve("structures/copy.json")));
        assertFalse(Files.exists(manifestPath(root, new StructureKey("iris", "copy"))));
    }

    @Test
    public void expiredPlanIsConsumedWithoutWriting() throws Exception {
        Path root = temporaryFolder.newFolder("expiry").toPath();
        writeGraph(root, "village", "village/start", "village/house", "village/house");
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        IrisStructureAdoptionLimits limits = limits(Duration.ofSeconds(1L));
        IrisStructureAdoptionService service = new IrisStructureAdoptionService(root, limits, clock);
        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.unowned("village"));
        clock.set(Instant.parse("2026-08-09T12:00:02Z"));

        IrisStructureAdoptionResult result = service.apply(plan);

        assertEquals(IrisStructureAdoptionResult.Status.EXPIRED, result.status());
        assertFalse(Files.exists(manifestPath(root, new StructureKey("iris", "village"))));
    }

    @Test
    public void malformedGraphReturnsStructuredBlockedPlan() throws Exception {
        Path root = temporaryFolder.newFolder("blocked").toPath();
        write(root, "structures/broken.json", "{\"startPool\":\"broken/missing\"}");
        IrisStructureAdoptionService service = service(root, Duration.ofMinutes(15L));

        IrisStructureAdoptionPlan plan = service.inspect(IrisStructureAdoptionRequest.unowned("broken"));

        assertEquals(IrisStructureAdoptionDisposition.BLOCKED, plan.disposition());
        assertFalse(plan.canApply());
        assertTrue(plan.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == IrisStructureAdoptionDiagnostic.Code.SOURCE_GRAPH_INVALID));
    }

    private IrisStructureAdoptionService service(Path root, Duration ttl) {
        return new IrisStructureAdoptionService(
                root,
                limits(ttl),
                Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC));
    }

    private IrisStructureAdoptionLimits limits(Duration ttl) {
        return new IrisStructureAdoptionLimits(
                1_000,
                1024 * 1024,
                1024 * 1024,
                16L * 1024L * 1024L,
                1_000,
                100,
                100,
                ttl);
    }

    private void writeGraph(
            Path root,
            String structure,
            String pool,
            String piece,
            String object
    ) throws IOException {
        write(root, "structures/" + structure + ".json", "{\"startPool\":\"" + pool + "\"}");
        write(root, "jigsaw-pools/" + pool + ".json",
                "{\"pieces\":[{\"piece\":\"" + piece + "\",\"weight\":3}]}");
        write(root, "jigsaw-pieces/" + piece + ".json",
                "{\"object\":\"" + object + "\",\"connectors\":[]}");
        write(root, "objects/" + object + ".iob", "object-" + object);
    }

    private void writeManagedManifest(Path root, String structure) throws IOException {
        writeSourceManifest(root, structure, true);
    }

    private void writeSourceManifest(Path root, String structure, boolean managed) throws IOException {
        TreeMap<String, String> hashes = new TreeMap<>();
        List<String> paths = List.of(
                "structures/" + structure + ".json",
                "jigsaw-pools/" + structure + "/start.json");
        for (String path : paths) {
            hashes.put(path, StructureHash.sha256(Files.readAllBytes(root.resolve(path))));
        }
        TreeMap<String, String> mappings = new TreeMap<>();
        for (String path : paths) {
            mappings.put(path, path);
        }
        StructureOwnershipManifest.Provenance provenance = managed
                ? new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.MANAGED_DATAPACK,
                UUID.randomUUID().toString(),
                StructureHash.sha256("managed-plan".getBytes(StandardCharsets.UTF_8)),
                StructureHash.sha256("managed-source".getBytes(StandardCharsets.UTF_8)),
                1_754_740_800_000L,
                hashes,
                mappings,
                StructureOwnershipManifest.RollbackDisposition.NONE)
                : StructureOwnershipManifest.Provenance.created();
        StructureOwnershipManifest manifest = new StructureOwnershipManifest(
                StructureOwnershipManifest.CURRENT_SCHEMA_VERSION,
                new StructureKey("iris", structure),
                StructureSource.of(
                        StructureSource.Kind.VANILLA,
                        new StructureKey("minecraft", structure)),
                StructureBackend.IRIS_ASSEMBLY,
                List.of(StructureCapability.BLOCKS, StructureCapability.CONNECTORS),
                List.of(),
                Map.copyOf(hashes),
                provenance);
        Path manifestPath = manifestPath(root, manifest.structure());
        Files.createDirectories(manifestPath.getParent());
        Files.write(manifestPath, manifest.toJson());
    }

    private void write(Path root, String relativePath, String content) throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private StructureOwnershipManifest readManifest(Path root, StructureKey key) throws IOException {
        return StructureOwnershipManifest.fromJson(Files.readAllBytes(manifestPath(root, key)));
    }

    private Path manifestPath(Path root, StructureKey key) {
        return root.resolve(StructureOwnershipManifest.relativePath(key));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
