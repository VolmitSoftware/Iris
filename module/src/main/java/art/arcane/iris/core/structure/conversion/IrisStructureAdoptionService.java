package art.arcane.iris.core.structure.conversion;

import art.arcane.iris.core.pack.StructurePackageClosure;
import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionReadSet;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteOptions;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

public final class IrisStructureAdoptionService {
    private static final String STRUCTURES = "structures";
    private static final String POOLS = "jigsaw-pools";
    private static final String PIECES = "jigsaw-pieces";
    private static final String OBJECTS = "objects";
    private static final String LOOT = "loot";
    private static final String MANIFESTS = ".iris/structure-manifests";
    private static final String ADOPTION_VERSION = "iris-adoption-1";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path packRoot;
    private final IrisStructureAdoptionLimits limits;
    private final Clock clock;
    private final StructureTransactionWriter writer;
    private final ConcurrentMap<UUID, IrisStructureAdoptionPlan> plans = new ConcurrentHashMap<>();

    public IrisStructureAdoptionService(Path packRoot) {
        this(packRoot, IrisStructureAdoptionLimits.defaults(), Clock.systemUTC());
    }

    public IrisStructureAdoptionService(
            Path packRoot,
            IrisStructureAdoptionLimits limits,
            Clock clock
    ) {
        this.packRoot = canonicalRoot(Objects.requireNonNull(packRoot, "packRoot"));
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
        writer = new StructureTransactionWriter(this.packRoot);
    }

    public synchronized IrisStructureAdoptionPlan inspect(IrisStructureAdoptionRequest request) {
        Objects.requireNonNull(request, "request");
        pruneExpiredPlans();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(limits.planTtl());
        UUID planId = UUID.randomUUID();
        PlanDiagnostics diagnostics = new PlanDiagnostics(limits.maxDiagnostics());
        boolean capacityAvailable = plans.size() < limits.maxActivePlans();
        if (!capacityAvailable) {
            diagnostics.error(
                    IrisStructureAdoptionDiagnostic.Code.PLAN_BLOCKED,
                    "",
                    "The active adoption plan limit of " + limits.maxActivePlans() + " was reached",
                    "Apply or wait for an existing plan to expire.");
        }
        StructureKey sourceKey = request.sourceOwnershipKey();
        StructureKey initialTarget = request.requestedTarget().orElse(sourceKey);
        if (!initialTarget.namespace().equals("iris")) {
            diagnostics.error(
                    IrisStructureAdoptionDiagnostic.Code.TARGET_NAMESPACE_UNSUPPORTED,
                    initialTarget.value(),
                    "Iris graph ownership targets must use the iris namespace",
                    "Choose an iris:<path> target.");
        }

        StructurePackageClosure closure = StructurePackageClosure.collect(
                packRoot.toFile(),
                List.of(request.sourceStructure()),
                new StructurePackageClosure.Limits(limits.maxResources(), limits.maxJsonBytes()));
        for (String error : closure.errors()) {
            diagnostics.error(
                    error.contains("exceeds")
                            ? IrisStructureAdoptionDiagnostic.Code.SOURCE_RESOURCE_LIMIT
                            : IrisStructureAdoptionDiagnostic.Code.SOURCE_GRAPH_INVALID,
                    request.sourceStructure(),
                    error,
                    "Repair the source graph before adoption.");
        }

        Map<String, String> sourceHashes = new TreeMap<>();
        long sourceBytes = 0L;
        if (!closure.structures().isEmpty()) {
            for (String relativePath : resourcePaths(closure)) {
                try {
                    HashedResource resource = hashResource(relativePath);
                    sourceHashes.put(relativePath, resource.contentHash());
                    sourceBytes = Math.addExact(sourceBytes, resource.size());
                    if (sourceBytes > limits.maxTotalBytes()) {
                        diagnostics.error(
                                IrisStructureAdoptionDiagnostic.Code.SOURCE_RESOURCE_LIMIT,
                                relativePath,
                                "Source closure exceeds " + limits.maxTotalBytes() + " bytes",
                                "Reduce the graph or raise a safe configured limit.");
                        break;
                    }
                } catch (IOException | RuntimeException exception) {
                    diagnostics.error(
                            IrisStructureAdoptionDiagnostic.Code.SOURCE_RESOURCE_UNSAFE,
                            relativePath,
                            describe(exception),
                            "Repair or replace this resource before adoption.");
                }
            }
        }
        String closureHash = canonicalMapHash(sourceHashes);

        String sourceManifestPath = StructureOwnershipManifest.relativePath(sourceKey);
        Map<String, String> ownershipReadHashes = new TreeMap<>();
        Path sourceManifest = null;
        try {
            sourceManifest = resolveSafe(sourceManifestPath);
        } catch (IllegalArgumentException exception) {
            diagnostics.error(
                    IrisStructureAdoptionDiagnostic.Code.SOURCE_RESOURCE_UNSAFE,
                    sourceManifestPath,
                    describe(exception),
                    "Repair the structure authoring metadata path before adoption.");
        }
        if (sourceManifest != null && Files.exists(sourceManifest, LinkOption.NOFOLLOW_LINKS)) {
            if (request.inputKind() == IrisStructureAdoptionInputKind.MANAGED_DATAPACK) {
                try {
                    byte[] manifestContent = readResource(sourceManifestPath, limits.maxJsonBytes());
                    StructureOwnershipManifest ownership = StructureOwnershipManifest.fromJson(manifestContent);
                    if (!ownership.structure().equals(sourceKey)
                            || ownership.backend() != StructureBackend.IRIS_ASSEMBLY) {
                        diagnostics.error(
                                IrisStructureAdoptionDiagnostic.Code.SOURCE_ALREADY_OWNED,
                                sourceManifestPath,
                                "The managed source manifest does not identify this Iris assembly graph",
                                "Refresh or repair the managed import before cloning it.");
                    } else if ((ownership.source().kind() != StructureSource.Kind.DATAPACK
                            && ownership.source().kind() != StructureSource.Kind.VANILLA)
                            || ownership.provenance().origin()
                            != StructureOwnershipManifest.Origin.MANAGED_DATAPACK) {
                        diagnostics.error(
                                IrisStructureAdoptionDiagnostic.Code.SOURCE_ALREADY_OWNED,
                                sourceManifestPath,
                                "The source manifest is not owned by managed datapack ingest",
                                "Clone from the correct managed source or open its existing owner.");
                    } else if (!manifestMatchesSource(ownership, sourceHashes)) {
                        diagnostics.error(
                                IrisStructureAdoptionDiagnostic.Code.SOURCE_RESOURCE_CHANGED,
                                sourceManifestPath,
                                "Managed source ownership resources do not match their manifest",
                                "Refresh or repair the managed import before cloning it.");
                    } else {
                        ownershipReadHashes.put(sourceManifestPath, StructureHash.sha256(manifestContent));
                    }
                } catch (IOException | RuntimeException exception) {
                    diagnostics.error(
                            IrisStructureAdoptionDiagnostic.Code.SOURCE_ALREADY_OWNED,
                            sourceManifestPath,
                            "Cannot verify managed source ownership: " + describe(exception),
                            "Repair the managed import before cloning it.");
                }
            } else {
                diagnostics.error(
                        IrisStructureAdoptionDiagnostic.Code.SOURCE_ALREADY_OWNED,
                        sourceManifestPath,
                        "The source graph already has an ownership manifest",
                        "Open the owned project or choose a different source.");
            }
        }

        boolean differentTarget = request.requestedTarget().filter(target -> !target.equals(sourceKey)).isPresent();
        if (request.strategy() == IrisStructureAdoptionStrategy.IN_PLACE && differentTarget) {
            diagnostics.error(
                    IrisStructureAdoptionDiagnostic.Code.TARGET_REQUIRED_FOR_CLONE,
                    initialTarget.value(),
                    "In-place adoption cannot change the structure key",
                    "Use clone strategy for a different target.");
        }
        boolean clone = request.strategy() == IrisStructureAdoptionStrategy.CLONE
                || request.strategy() != IrisStructureAdoptionStrategy.IN_PLACE && differentTarget;
        if (request.inputKind() == IrisStructureAdoptionInputKind.MANAGED_DATAPACK) {
            if (request.strategy() == IrisStructureAdoptionStrategy.IN_PLACE) {
                diagnostics.error(
                        IrisStructureAdoptionDiagnostic.Code.MANAGED_INPUT_REQUIRES_CLONE,
                        request.sourceStructure(),
                        "Managed datapack graphs cannot be adopted in place",
                        "Inspect a clone target so managed cleanup remains isolated.");
            } else {
                diagnostics.warning(
                        IrisStructureAdoptionDiagnostic.Code.MANAGED_INPUT_REQUIRES_CLONE,
                        request.sourceStructure(),
                        "Managed datapack ownership requires a private clone",
                        "The source will remain managed and unchanged.");
                clone = true;
            }
        }

        ExclusivitySnapshot exclusivity = ExclusivitySnapshot.empty();
        if (!clone && !diagnostics.blocked()) {
            exclusivity = inspectExclusivity(request.sourceStructure(), sourceHashes);
            for (ExclusivityIssue issue : exclusivity.issues()) {
                if (request.strategy() == IrisStructureAdoptionStrategy.IN_PLACE) {
                    diagnostics.error(issue.code(), issue.resource(), issue.detail(),
                            "Choose clone adoption instead.");
                } else {
                    diagnostics.warning(issue.code(), issue.resource(), issue.detail(),
                            "A private clone will avoid overlapping ownership.");
                    clone = true;
                }
            }
        }

        StructureKey target = initialTarget;
        if (clone && request.requestedTarget().isEmpty()) {
            try {
                target = firstAvailableCloneTarget(request.sourceStructure(), sourceHashes.keySet());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                diagnostics.error(
                        IrisStructureAdoptionDiagnostic.Code.TARGET_RESOURCE_UNSAFE,
                        request.sourceStructure(),
                        describe(exception),
                        "Choose an explicit safe target after repairing the pack path.");
                target = new StructureKey("iris", request.sourceStructure() + "-studio-unavailable");
            }
        }
        if (clone && target.equals(sourceKey)) {
            diagnostics.error(
                    IrisStructureAdoptionDiagnostic.Code.TARGET_REQUIRED_FOR_CLONE,
                    target.value(),
                    "Clone adoption cannot write over the source graph",
                    "Choose a different iris:<path> target.");
        }
        if (!clone && !target.equals(sourceKey)) {
            diagnostics.error(
                    IrisStructureAdoptionDiagnostic.Code.TARGET_REQUIRED_FOR_CLONE,
                    target.value(),
                    "In-place adoption must keep the source structure key",
                    "Use clone strategy for a different target.");
        }

        Map<String, String> mappings = sourceHashes.isEmpty()
                ? Map.of()
                : clone ? cloneMappings(request.sourceStructure(), target.path(), sourceHashes.keySet())
                : identityMappings(sourceHashes.keySet());
        StructureTransactionReadSet.Builder readSet = StructureTransactionReadSet.builder()
                .files(sourceHashes)
                .files(ownershipReadHashes);
        if (clone) {
            TreeSet<String> targetPaths = new TreeSet<>(mappings.values());
            targetPaths.add(StructureOwnershipManifest.relativePath(target));
            TreeSet<String> absentTargetPaths = new TreeSet<>(targetPaths);
            absentTargetPaths.removeAll(sourceHashes.keySet());
            absentTargetPaths.removeAll(ownershipReadHashes.keySet());
            readSet.absent(absentTargetPaths);
            for (String targetPath : targetPaths) {
                Path resolvedTarget;
                try {
                    resolvedTarget = resolveSafe(targetPath);
                } catch (IllegalArgumentException exception) {
                    diagnostics.error(
                            IrisStructureAdoptionDiagnostic.Code.TARGET_RESOURCE_UNSAFE,
                            targetPath,
                            describe(exception),
                            "Choose a target whose resource paths stay inside the pack.");
                    continue;
                }
                if (Files.exists(resolvedTarget, LinkOption.NOFOLLOW_LINKS)) {
                    diagnostics.error(
                            IrisStructureAdoptionDiagnostic.Code.TARGET_RESOURCE_EXISTS,
                            targetPath,
                            "Clone target path already exists",
                            "Choose a different target key.");
                }
            }
            if (!diagnostics.blocked()) {
                diagnostics.info(
                        IrisStructureAdoptionDiagnostic.Code.CLONE_SELECTED,
                        target.value(),
                        "The graph will be copied into project-private resource paths",
                        "Apply the inspected plan ID to commit it.");
            }
        } else {
            readSet.absent(sourceManifestPath);
            readSet.files(exclusivity.fileHashes());
            for (Map.Entry<String, List<String>> directory : exclusivity.directoryEntries().entrySet()) {
                readSet.directory(directory.getKey(), directory.getValue());
            }
            if (!diagnostics.blocked()) {
                diagnostics.info(
                        IrisStructureAdoptionDiagnostic.Code.IN_PLACE_AVAILABLE,
                        target.value(),
                        "The exclusive source graph can be claimed without rewriting resource bytes",
                        "Apply the inspected plan ID to install ownership.");
            }
        }

        IrisStructureAdoptionDisposition disposition = diagnostics.blocked()
                ? IrisStructureAdoptionDisposition.BLOCKED
                : clone
                ? IrisStructureAdoptionDisposition.CLONE_REQUIRED
                : IrisStructureAdoptionDisposition.IN_PLACE;
        StructureTransactionReadSet transactionReadSet = readSet.build();
        String planHash = planHash(
                planId,
                createdAt,
                expiresAt,
                request,
                target,
                disposition,
                diagnostics.values(),
                sourceHashes,
                mappings,
                transactionReadSet,
                closureHash);
        IrisStructureAdoptionPlan plan = new IrisStructureAdoptionPlan(
                planId,
                createdAt,
                expiresAt,
                request,
                target,
                disposition,
                diagnostics.values(),
                sourceHashes,
                mappings,
                transactionReadSet,
                sourceBytes,
                closureHash,
                planHash
        );
        if (capacityAvailable) {
            plans.put(planId, plan);
        }
        return plan;
    }

    public synchronized IrisStructureAdoptionResult apply(IrisStructureAdoptionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        IrisStructureAdoptionPlan stored = plans.remove(plan.planId());
        if (stored == null || !stored.planHash().equals(plan.planHash())) {
            return result(
                    IrisStructureAdoptionResult.Status.UNKNOWN_PLAN,
                    plan.planId(),
                    diagnostic(
                            IrisStructureAdoptionDiagnostic.Severity.ERROR,
                            IrisStructureAdoptionDiagnostic.Code.PLAN_UNKNOWN,
                            "",
                            "The adoption plan is unknown or has already been consumed",
                            "Run inspect again."),
                    Optional.empty(),
                    Optional.empty());
        }
        if (stored.expiredAt(clock.instant())) {
            return result(
                    IrisStructureAdoptionResult.Status.EXPIRED,
                    stored.planId(),
                    diagnostic(
                            IrisStructureAdoptionDiagnostic.Severity.ERROR,
                            IrisStructureAdoptionDiagnostic.Code.PLAN_EXPIRED,
                            "",
                            "The adoption plan expired at " + stored.expiresAt(),
                            "Run inspect again."),
                    Optional.empty(),
                    Optional.empty());
        }
        if (!stored.canApply()) {
            return new IrisStructureAdoptionResult(
                    IrisStructureAdoptionResult.Status.BLOCKED,
                    stored.planId(),
                    stored.diagnostics(),
                    Optional.empty(),
                    Optional.empty());
        }

        try {
            return stored.disposition() == IrisStructureAdoptionDisposition.IN_PLACE
                    ? applyInPlace(stored)
                    : applyClone(stored);
        } catch (StalePlanException exception) {
            return result(
                    IrisStructureAdoptionResult.Status.STALE,
                    stored.planId(),
                    diagnostic(
                            IrisStructureAdoptionDiagnostic.Severity.ERROR,
                            IrisStructureAdoptionDiagnostic.Code.PLAN_STALE,
                            exception.resource(),
                            exception.getMessage(),
                            "Run inspect again."),
                    Optional.empty(),
                    Optional.empty());
        } catch (IOException | RuntimeException exception) {
            return result(
                    IrisStructureAdoptionResult.Status.FAILED,
                    stored.planId(),
                    diagnostic(
                            IrisStructureAdoptionDiagnostic.Severity.ERROR,
                            IrisStructureAdoptionDiagnostic.Code.TRANSACTION_FAILED,
                            "",
                            describe(exception),
                            "No adoption receipt was committed."),
                    Optional.empty(),
                    Optional.empty());
        }
    }

    public synchronized Optional<IrisStructureAdoptionPlan> plan(UUID planId) {
        Objects.requireNonNull(planId, "planId");
        pruneExpiredPlans();
        return Optional.ofNullable(plans.get(planId));
    }

    public synchronized List<UUID> activePlanIds() {
        pruneExpiredPlans();
        ArrayList<UUID> ids = new ArrayList<>(plans.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    public synchronized int pruneExpiredPlans() {
        Instant now = clock.instant();
        int removed = 0;
        for (Map.Entry<UUID, IrisStructureAdoptionPlan> entry : plans.entrySet()) {
            if (entry.getValue().expiredAt(now) && plans.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    private IrisStructureAdoptionResult applyInPlace(IrisStructureAdoptionPlan plan) {
        IrisStructureAdoptionReceipt receipt = receipt(
                plan,
                StructureOwnershipManifest.Origin.ADOPTED_EXISTING,
                plan.sourceResourceHashes(),
                StructureOwnershipManifest.RollbackDisposition.NONE);
        StructureOwnershipManifest manifest = new StructureOwnershipManifest(
                StructureOwnershipManifest.CURRENT_SCHEMA_VERSION,
                plan.targetStructure(),
                source(plan),
                StructureBackend.IRIS_ASSEMBLY,
                defaultCapabilities(),
                List.of(),
                plan.sourceResourceHashes(),
                receipt.provenance());
        StructureWriteResult writeResult = writer.claimExisting(manifest, plan.readSet());
        return completeApply(plan, receipt, writeResult);
    }

    private IrisStructureAdoptionResult applyClone(IrisStructureAdoptionPlan plan) throws IOException {
        StructureResourceBundle bundle = cloneBundle(plan);
        Map<String, String> targetHashes = new TreeMap<>();
        for (StructureResourceBundle.Resource resource : bundle.resources().values()) {
            targetHashes.put(resource.relativePath(), resource.contentHash());
        }
        IrisStructureAdoptionReceipt receipt = receipt(
                plan,
                plan.request().inputKind() == IrisStructureAdoptionInputKind.MANAGED_DATAPACK
                        ? StructureOwnershipManifest.Origin.ADOPTED_MANAGED_CLONE
                        : StructureOwnershipManifest.Origin.ADOPTED_CLONE,
                targetHashes,
                StructureOwnershipManifest.RollbackDisposition.DELETE_CREATED_IF_UNCHANGED);
        StructureWriteResult writeResult = writer.writeVerified(
                bundle,
                StructureWriteOptions.addOnly(),
                plan.readSet(),
                receipt.provenance());
        return completeApply(plan, receipt, writeResult);
    }

    private IrisStructureAdoptionResult completeApply(
            IrisStructureAdoptionPlan plan,
            IrisStructureAdoptionReceipt receipt,
            StructureWriteResult writeResult
    ) {
        if (writeResult.committed()) {
            ArrayList<IrisStructureAdoptionDiagnostic> diagnostics = new ArrayList<>();
            diagnostics.add(diagnostic(
                    IrisStructureAdoptionDiagnostic.Severity.INFO,
                    IrisStructureAdoptionDiagnostic.Code.APPLIED,
                    plan.targetStructure().value(),
                    "Ownership and adoption provenance were committed",
                    "The graph is ready for owned authoring."));
            if (writeResult.status() == StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED) {
                diagnostics.add(diagnostic(
                        IrisStructureAdoptionDiagnostic.Severity.WARNING,
                        IrisStructureAdoptionDiagnostic.Code.TRANSACTION_FAILED,
                        writeResult.manifestPath(),
                        "The transaction committed but recovery cleanup remains",
                        "Run structure transaction recovery before another mutation."));
            }
            return new IrisStructureAdoptionResult(
                    IrisStructureAdoptionResult.Status.APPLIED,
                    plan.planId(),
                    diagnostics,
                    Optional.of(receipt),
                    Optional.of(writeResult));
        }
        boolean stale = writeResult.conflicts().stream().anyMatch(conflict ->
                conflict.reason() == StructureWriteResult.ConflictReason.STALE_READ_SET
                        || conflict.reason() == StructureWriteResult.ConflictReason.RESOURCE_EXISTS
                        || conflict.reason() == StructureWriteResult.ConflictReason.MANIFEST_EXISTS);
        IrisStructureAdoptionDiagnostic diagnostic = diagnostic(
                IrisStructureAdoptionDiagnostic.Severity.ERROR,
                stale
                        ? IrisStructureAdoptionDiagnostic.Code.PLAN_STALE
                        : IrisStructureAdoptionDiagnostic.Code.TRANSACTION_FAILED,
                writeResult.conflicts().isEmpty() ? writeResult.manifestPath()
                        : writeResult.conflicts().getFirst().relativePath(),
                failureDetail(writeResult),
                stale ? "Run inspect again." : "No adoption receipt was committed.");
        return result(
                stale ? IrisStructureAdoptionResult.Status.STALE : IrisStructureAdoptionResult.Status.FAILED,
                plan.planId(),
                diagnostic,
                Optional.empty(),
                Optional.of(writeResult));
    }

    private StructureResourceBundle cloneBundle(IrisStructureAdoptionPlan plan) throws IOException {
        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(plan.targetStructure())
                .source(source(plan))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capabilities(defaultCapabilities());
        for (Map.Entry<String, String> mapping : plan.sourceToTargetPaths().entrySet()) {
            String sourcePath = mapping.getKey();
            byte[] content = readResource(sourcePath);
            String observedHash = StructureHash.sha256(content);
            String expectedHash = plan.sourceResourceHashes().get(sourcePath);
            if (!observedHash.equals(expectedHash)) {
                throw new StalePlanException(sourcePath, "Source resource changed after inspection");
            }
            byte[] targetContent = rewriteResource(sourcePath, content, plan.sourceToTargetPaths());
            bundle.resource(mapping.getValue(), targetContent);
        }
        return bundle.build();
    }

    private byte[] rewriteResource(
            String sourcePath,
            byte[] content,
            Map<String, String> mappings
    ) throws IOException {
        if (sourcePath.startsWith(OBJECTS + "/") || sourcePath.startsWith(LOOT + "/")) {
            return content;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IOException("Graph resource is not a JSON object: " + sourcePath);
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Cannot parse graph resource " + sourcePath, exception);
        }
        if (sourcePath.startsWith(STRUCTURES + "/")) {
            rewriteString(root, "startPool", POOLS, ".json", mappings, sourcePath);
            rewriteArray(root, "loot", LOOT, ".json", mappings, sourcePath);
        } else if (sourcePath.startsWith(POOLS + "/")) {
            JsonArray pieces = root.getAsJsonArray("pieces");
            if (pieces != null) {
                for (JsonElement element : pieces) {
                    if (element.isJsonObject() && element.getAsJsonObject().has("piece")) {
                        rewriteString(element.getAsJsonObject(), "piece", PIECES, ".json", mappings, sourcePath);
                    }
                }
            }
            if (root.has("fallback")) {
                rewriteString(root, "fallback", POOLS, ".json", mappings, sourcePath);
            }
        } else if (sourcePath.startsWith(PIECES + "/")) {
            rewriteString(root, "object", OBJECTS, ".iob", mappings, sourcePath);
            JsonArray connectors = root.getAsJsonArray("connectors");
            if (connectors != null) {
                for (JsonElement element : connectors) {
                    if (element.isJsonObject()) {
                        rewriteString(element.getAsJsonObject(), "pool", POOLS, ".json", mappings, sourcePath);
                    }
                }
            }
        }
        return (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private void rewriteString(
            JsonObject object,
            String field,
            String folder,
            String extension,
            Map<String, String> mappings,
            String owner
    ) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Graph resource " + owner + " has invalid field " + field);
        }
        if (value.getAsString().isBlank()) {
            return;
        }
        String sourcePath = folder + "/" + value.getAsString() + extension;
        String targetPath = mappings.get(sourcePath);
        if (targetPath == null) {
            throw new IOException("Graph resource " + owner + " references unmapped resource " + sourcePath);
        }
        object.addProperty(field, internalKey(targetPath, folder, extension));
    }

    private void rewriteArray(
            JsonObject object,
            String field,
            String folder,
            String extension,
            Map<String, String> mappings,
            String owner
    ) throws IOException {
        if (!object.has(field)) {
            return;
        }
        JsonArray values = object.getAsJsonArray(field);
        if (values == null) {
            throw new IOException("Graph resource " + owner + " has invalid field " + field);
        }
        JsonArray rewritten = new JsonArray();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IOException("Graph resource " + owner + " has non-string " + field + " entry");
            }
            String sourcePath = folder + "/" + value.getAsString() + extension;
            String targetPath = mappings.get(sourcePath);
            if (targetPath == null) {
                throw new IOException("Graph resource " + owner + " references unmapped resource " + sourcePath);
            }
            rewritten.add(internalKey(targetPath, folder, extension));
        }
        object.add(field, rewritten);
    }

    private ExclusivitySnapshot inspectExclusivity(
            String sourceStructure,
            Map<String, String> sourceHashes
    ) {
        TreeMap<String, String> globalHashes = new TreeMap<>();
        TreeMap<String, List<String>> directoryEntries = new TreeMap<>();
        ArrayList<ExclusivityIssue> issues = new ArrayList<>();
        long inspectedBytes = 0L;
        int inspectedResources = 0;
        try {
            for (String directory : List.of(STRUCTURES, POOLS, PIECES, MANIFESTS)) {
                List<String> entries = snapshotDirectory(directory);
                inspectedResources = Math.addExact(inspectedResources, entries.size());
                if (inspectedResources > limits.maxResources()) {
                    throw new IOException("Exclusivity snapshot exceeds "
                            + limits.maxResources() + " resources");
                }
                directoryEntries.put(directory, entries);
                for (String relativePath : entries) {
                    int maximum = relativePath.startsWith(MANIFESTS + "/")
                            ? limits.maxJsonBytes()
                            : limits.maxJsonBytes();
                    HashedResource resource = hashResource(relativePath, maximum);
                    globalHashes.put(relativePath, resource.contentHash());
                    inspectedBytes = Math.addExact(inspectedBytes, resource.size());
                    if (inspectedBytes > limits.maxTotalBytes()) {
                        throw new IOException("Exclusivity snapshot exceeds "
                                + limits.maxTotalBytes() + " bytes");
                    }
                }
            }
            List<String> structurePaths = directoryEntries.getOrDefault(STRUCTURES, List.of());
            if (structurePaths.size() > limits.maxStructuresScanned()) {
                issues.add(new ExclusivityIssue(
                        IrisStructureAdoptionDiagnostic.Code.EXCLUSIVITY_UNPROVEN,
                        STRUCTURES,
                        "Structure scan exceeds " + limits.maxStructuresScanned() + " roots"));
            } else {
                for (String structurePath : structurePaths) {
                    if (!structurePath.endsWith(".json")) {
                        continue;
                    }
                    String structureKey = internalKey(structurePath, STRUCTURES, ".json");
                    if (structureKey.equals(sourceStructure)) {
                        continue;
                    }
                    JsonObject structure = readJsonObject(structurePath);
                    if (structure.has("startPool")) {
                        String startPool = requiredReference(structure, "startPool", structurePath);
                        String referencedPath = POOLS + "/" + startPool + ".json";
                        if (sourceHashes.containsKey(referencedPath)) {
                            issues.add(new ExclusivityIssue(
                                    IrisStructureAdoptionDiagnostic.Code.SHARED_DEPENDENCY,
                                    referencedPath,
                                    "Structure '" + structureKey + "' directly references the source graph"));
                        }
                    }
                    if (structure.has("loot")) {
                        JsonArray loot = structure.getAsJsonArray("loot");
                        if (loot == null) {
                            throw new IOException("Structure has invalid loot references: " + structurePath);
                        }
                        for (JsonElement lootEntry : loot) {
                            if (!lootEntry.isJsonPrimitive() || !lootEntry.getAsJsonPrimitive().isString()) {
                                throw new IOException("Structure has a non-string loot reference: " + structurePath);
                            }
                            addSharedBoundary(
                                    issues,
                                    sourceHashes,
                                    LOOT,
                                    lootEntry.getAsString(),
                                    ".json",
                                    structurePath);
                        }
                    }
                }
            }
            for (String poolPath : directoryEntries.getOrDefault(POOLS, List.of())) {
                if (!poolPath.endsWith(".json") || sourceHashes.containsKey(poolPath)) {
                    continue;
                }
                JsonObject pool = readJsonObject(poolPath);
                if (pool.has("fallback")) {
                    String fallback = requiredReference(pool, "fallback", poolPath);
                    if (!fallback.isBlank()) {
                        addSharedBoundary(issues, sourceHashes, POOLS, fallback, ".json", poolPath);
                    }
                }
                JsonArray entries = pool.getAsJsonArray("pieces");
                if (entries == null) {
                    throw new IOException("Jigsaw pool has no pieces array: " + poolPath);
                }
                for (JsonElement entry : entries) {
                    if (!entry.isJsonObject() || !entry.getAsJsonObject().has("piece")) {
                        continue;
                    }
                    String piece = requiredReference(entry.getAsJsonObject(), "piece", poolPath);
                    addSharedBoundary(issues, sourceHashes, PIECES, piece, ".json", poolPath);
                }
            }
            for (String piecePath : directoryEntries.getOrDefault(PIECES, List.of())) {
                if (!piecePath.endsWith(".json") || sourceHashes.containsKey(piecePath)) {
                    continue;
                }
                JsonObject piece = readJsonObject(piecePath);
                String object = requiredReference(piece, "object", piecePath);
                addSharedBoundary(issues, sourceHashes, OBJECTS, object, ".iob", piecePath);
                if (!piece.has("connectors")) {
                    continue;
                }
                JsonArray connectors = piece.getAsJsonArray("connectors");
                if (connectors == null) {
                    throw new IOException("Jigsaw piece has invalid connectors: " + piecePath);
                }
                for (JsonElement connector : connectors) {
                    if (!connector.isJsonObject()) {
                        throw new IOException("Jigsaw piece has a non-object connector: " + piecePath);
                    }
                    String pool = requiredReference(connector.getAsJsonObject(), "pool", piecePath);
                    addSharedBoundary(issues, sourceHashes, POOLS, pool, ".json", piecePath);
                }
            }
            for (String manifestPath : directoryEntries.getOrDefault(MANIFESTS, List.of())) {
                StructureOwnershipManifest manifest;
                try {
                    manifest = StructureOwnershipManifest.fromJson(readResource(manifestPath, limits.maxJsonBytes()));
                } catch (IOException | RuntimeException exception) {
                    issues.add(new ExclusivityIssue(
                            IrisStructureAdoptionDiagnostic.Code.EXCLUSIVITY_UNPROVEN,
                            manifestPath,
                            "An ownership manifest is invalid: " + describe(exception)));
                    continue;
                }
                Set<String> overlap = new TreeSet<>(manifest.resourceHashes().keySet());
                overlap.retainAll(sourceHashes.keySet());
                if (!overlap.isEmpty()) {
                    issues.add(new ExclusivityIssue(
                            IrisStructureAdoptionDiagnostic.Code.SHARED_DEPENDENCY,
                            overlap.iterator().next(),
                            "Owned structure '" + manifest.structure() + "' already claims "
                                    + overlap.size() + " source resources"));
                }
            }
        } catch (IOException | RuntimeException exception) {
            issues.add(new ExclusivityIssue(
                    IrisStructureAdoptionDiagnostic.Code.EXCLUSIVITY_UNPROVEN,
                    "",
                    describe(exception)));
        }
        return new ExclusivitySnapshot(globalHashes, directoryEntries, issues);
    }

    private void addSharedBoundary(
            List<ExclusivityIssue> issues,
            Map<String, String> sourceHashes,
            String folder,
            String key,
            String extension,
            String owner
    ) {
        String referencedPath = folder + "/" + key + extension;
        if (sourceHashes.containsKey(referencedPath)) {
            issues.add(new ExclusivityIssue(
                    IrisStructureAdoptionDiagnostic.Code.SHARED_DEPENDENCY,
                    referencedPath,
                    owner + " references a source graph resource"));
        }
    }

    private String requiredReference(JsonObject object, String field, String owner) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Graph resource " + owner + " has invalid field " + field);
        }
        return value.getAsString();
    }

    private List<String> snapshotDirectory(String relativePath) throws IOException {
        Path directory = resolveSafe(relativePath);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Snapshot path is not a directory: " + relativePath);
        }
        ArrayList<String> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (path.equals(directory)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Snapshot directory contains a symbolic link: " + path);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Snapshot directory contains a non-file entry: " + path);
                }
                entries.add(relative(path));
                if (entries.size() > limits.maxResources()) {
                    throw new IOException("Snapshot directory exceeds " + limits.maxResources() + " resources");
                }
            }
        }
        Collections.sort(entries);
        return List.copyOf(entries);
    }

    private StructureKey firstAvailableCloneTarget(String sourceStructure, Set<String> sourcePaths) {
        for (int suffix = 0; suffix < 100; suffix++) {
            String path = sourceStructure + "-studio" + (suffix == 0 ? "" : "-" + (suffix + 1));
            StructureKey candidate = new StructureKey("iris", path);
            Map<String, String> mappings = cloneMappings(sourceStructure, path, sourcePaths);
            boolean occupied = Files.exists(
                    resolveSafe(StructureOwnershipManifest.relativePath(candidate)),
                    LinkOption.NOFOLLOW_LINKS);
            if (!occupied) {
                occupied = mappings.values().stream().anyMatch(target ->
                        Files.exists(resolveSafe(target), LinkOption.NOFOLLOW_LINKS));
            }
            if (!occupied) {
                return candidate;
            }
        }
        throw new IllegalStateException("No available automatic clone target was found after 100 candidates");
    }

    private Map<String, String> cloneMappings(
            String sourceStructure,
            String targetStructure,
            Set<String> sourcePaths
    ) {
        TreeMap<String, String> mappings = new TreeMap<>();
        Set<String> targetPaths = new LinkedHashSet<>();
        for (String sourcePath : sourcePaths) {
            String targetPath;
            if (sourcePath.equals(STRUCTURES + "/" + sourceStructure + ".json")) {
                targetPath = STRUCTURES + "/" + targetStructure + ".json";
            } else if (sourcePath.startsWith(POOLS + "/")) {
                targetPath = POOLS + "/" + targetStructure + "/pool/"
                        + internalKey(sourcePath, POOLS, ".json") + ".json";
            } else if (sourcePath.startsWith(PIECES + "/")) {
                targetPath = PIECES + "/" + targetStructure + "/piece/"
                        + internalKey(sourcePath, PIECES, ".json") + ".json";
            } else if (sourcePath.startsWith(OBJECTS + "/")) {
                targetPath = OBJECTS + "/" + targetStructure + "/object/"
                        + internalKey(sourcePath, OBJECTS, ".iob") + ".iob";
            } else if (sourcePath.startsWith(LOOT + "/")) {
                targetPath = LOOT + "/" + targetStructure + "/loot/"
                        + internalKey(sourcePath, LOOT, ".json") + ".json";
            } else {
                throw new IllegalArgumentException("Unsupported graph resource path " + sourcePath);
            }
            StructureResourceBundle.validateRelativePath(targetPath);
            if (!targetPaths.add(targetPath)) {
                throw new IllegalArgumentException("Clone resource mapping collision at " + targetPath);
            }
            mappings.put(sourcePath, targetPath);
        }
        return Collections.unmodifiableMap(mappings);
    }

    private Map<String, String> identityMappings(Set<String> sourcePaths) {
        TreeMap<String, String> mappings = new TreeMap<>();
        for (String sourcePath : sourcePaths) {
            mappings.put(sourcePath, sourcePath);
        }
        return Collections.unmodifiableMap(mappings);
    }

    private Set<String> resourcePaths(StructurePackageClosure closure) {
        TreeSet<String> resources = new TreeSet<>();
        addPaths(resources, STRUCTURES, closure.structures(), ".json");
        addPaths(resources, POOLS, closure.pools(), ".json");
        addPaths(resources, PIECES, closure.pieces(), ".json");
        addPaths(resources, OBJECTS, closure.objects(), ".iob");
        addPaths(resources, LOOT, closure.loot(), ".json");
        return Collections.unmodifiableSet(resources);
    }

    private void addPaths(Set<String> resources, String folder, Set<String> keys, String extension) {
        for (String key : keys) {
            resources.add(folder + "/" + key + extension);
        }
    }

    private HashedResource hashResource(String relativePath) throws IOException {
        int maximum = relativePath.endsWith(".iob") ? limits.maxBinaryBytes() : limits.maxJsonBytes();
        return hashResource(relativePath, maximum);
    }

    private HashedResource hashResource(String relativePath, int maximumBytes) throws IOException {
        byte[] content = readResource(relativePath, maximumBytes);
        return new HashedResource(content.length, StructureHash.sha256(content));
    }

    private byte[] readResource(String relativePath) throws IOException {
        int maximum = relativePath.endsWith(".iob") ? limits.maxBinaryBytes() : limits.maxJsonBytes();
        return readResource(relativePath, maximum);
    }

    private byte[] readResource(String relativePath, int maximumBytes) throws IOException {
        Path resource = resolveRegularFile(relativePath);
        try (InputStream input = Files.newInputStream(
                resource,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(maximumBytes + 1);
            if (content.length > maximumBytes) {
                throw new IOException("Resource exceeds " + maximumBytes + " bytes");
            }
            return content;
        }
    }

    private JsonObject readJsonObject(String relativePath) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(readResource(relativePath, limits.maxJsonBytes()), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IOException("JSON resource is not an object: " + relativePath);
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Cannot parse JSON resource " + relativePath, exception);
        }
    }

    private Path resolveRegularFile(String relativePath) throws IOException {
        Path resource = resolveSafe(relativePath);
        if (!Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Resource is missing or not a regular file: " + relativePath);
        }
        Path realResource = resource.toRealPath();
        Path realRoot = packRoot.toRealPath();
        if (!realResource.startsWith(realRoot)) {
            throw new IOException("Resource escapes the pack through a symbolic link: " + relativePath);
        }
        return resource;
    }

    private Path resolveSafe(String relativePath) {
        Path target = packRoot.resolve(relativePath).normalize();
        if (!target.startsWith(packRoot) || target.equals(packRoot)) {
            throw new IllegalArgumentException("Resource path escapes the pack root: " + relativePath);
        }
        Path current = packRoot;
        for (Path segment : packRoot.relativize(target)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Resource path contains a symbolic link: " + current);
            }
            if (!current.equals(target) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Resource path contains a non-directory parent: " + current);
            }
        }
        return target;
    }

    private String relative(Path path) {
        return packRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String internalKey(String relativePath, String folder, String extension) {
        String prefix = folder + "/";
        if (!relativePath.startsWith(prefix) || !relativePath.endsWith(extension)) {
            throw new IllegalArgumentException("Resource path is outside " + folder + ": " + relativePath);
        }
        return relativePath.substring(prefix.length(), relativePath.length() - extension.length());
    }

    private StructureSource source(IrisStructureAdoptionPlan plan) {
        return new StructureSource(
                StructureSource.Kind.IRIS,
                plan.request().sourceOwnershipKey(),
                ADOPTION_VERSION,
                plan.sourceClosureHash());
    }

    private List<StructureCapability> defaultCapabilities() {
        return List.copyOf(EnumSet.of(
                StructureCapability.BLOCKS,
                StructureCapability.CONNECTORS,
                StructureCapability.IRIS_PLACEMENT));
    }

    private IrisStructureAdoptionReceipt receipt(
            IrisStructureAdoptionPlan plan,
            StructureOwnershipManifest.Origin origin,
            Map<String, String> targetHashes,
            StructureOwnershipManifest.RollbackDisposition rollbackDisposition
    ) {
        return new IrisStructureAdoptionReceipt(
                UUID.randomUUID(),
                plan.planId(),
                origin,
                clock.instant(),
                plan.request().sourceOwnershipKey(),
                plan.targetStructure(),
                plan.sourceClosureHash(),
                plan.planHash(),
                plan.sourceResourceHashes(),
                targetHashes,
                plan.sourceToTargetPaths(),
                rollbackDisposition);
    }

    private String planHash(
            UUID planId,
            Instant createdAt,
            Instant expiresAt,
            IrisStructureAdoptionRequest request,
            StructureKey target,
            IrisStructureAdoptionDisposition disposition,
            List<IrisStructureAdoptionDiagnostic> diagnostics,
            Map<String, String> sourceHashes,
            Map<String, String> mappings,
            StructureTransactionReadSet readSet,
            String closureHash
    ) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(planId).append('\n')
                .append(createdAt).append('\n')
                .append(expiresAt).append('\n')
                .append(request.sourceStructure()).append('\n')
                .append(request.strategy()).append('\n')
                .append(request.inputKind()).append('\n')
                .append(target.value()).append('\n')
                .append(disposition).append('\n')
                .append(closureHash).append('\n');
        appendMap(canonical, sourceHashes);
        appendMap(canonical, mappings);
        appendMap(canonical, readSet.fileHashes());
        for (String absentPath : readSet.absentPaths()) {
            canonical.append("absent=").append(absentPath).append('\n');
        }
        for (Map.Entry<String, List<String>> directory : readSet.directoryEntries().entrySet()) {
            canonical.append("directory=").append(directory.getKey()).append('\n');
            for (String entry : directory.getValue()) {
                canonical.append("entry=").append(entry).append('\n');
            }
        }
        ArrayList<IrisStructureAdoptionDiagnostic> orderedDiagnostics = new ArrayList<>(diagnostics);
        Collections.sort(orderedDiagnostics);
        for (IrisStructureAdoptionDiagnostic diagnostic : orderedDiagnostics) {
            canonical.append(diagnostic.summary()).append('\n');
        }
        return StructureHash.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void appendMap(StringBuilder canonical, Map<String, String> values) {
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
    }

    private boolean manifestMatchesSource(
            StructureOwnershipManifest manifest,
            Map<String, String> sourceHashes
    ) {
        if (manifest.resourceHashes().isEmpty()) {
            return false;
        }
        String rootStructurePath = STRUCTURES + "/" + manifest.structure().path() + ".json";
        if (!manifest.resourceHashes().containsKey(rootStructurePath)) {
            return false;
        }
        for (Map.Entry<String, String> owned : manifest.resourceHashes().entrySet()) {
            if (!owned.getValue().equals(sourceHashes.get(owned.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private String canonicalMapHash(Map<String, String> values) {
        StringBuilder canonical = new StringBuilder();
        appendMap(canonical, values);
        return StructureHash.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String failureDetail(StructureWriteResult writeResult) {
        if (!writeResult.conflicts().isEmpty()) {
            StructureWriteResult.Conflict conflict = writeResult.conflicts().getFirst();
            return conflict.detail().isBlank()
                    ? "Transaction conflict at " + conflict.relativePath() + ": " + conflict.reason()
                    : conflict.detail();
        }
        return writeResult.failure().map(IrisStructureAdoptionService::describe)
                .orElse("Structure transaction did not commit");
    }

    private IrisStructureAdoptionResult result(
            IrisStructureAdoptionResult.Status status,
            UUID planId,
            IrisStructureAdoptionDiagnostic diagnostic,
            Optional<IrisStructureAdoptionReceipt> receipt,
            Optional<StructureWriteResult> writeResult
    ) {
        return new IrisStructureAdoptionResult(
                status,
                planId,
                List.of(diagnostic),
                receipt,
                writeResult);
    }

    private IrisStructureAdoptionDiagnostic diagnostic(
            IrisStructureAdoptionDiagnostic.Severity severity,
            IrisStructureAdoptionDiagnostic.Code code,
            String resource,
            String detail,
            String recommendation
    ) {
        return new IrisStructureAdoptionDiagnostic(severity, code, resource, detail, recommendation);
    }

    private static Path canonicalRoot(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            return normalized;
        }
        try {
            return normalized.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot resolve adoption pack root " + normalized, exception);
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record HashedResource(long size, String contentHash) {
    }

    private record ExclusivityIssue(
            IrisStructureAdoptionDiagnostic.Code code,
            String resource,
            String detail
    ) {
    }

    private record ExclusivitySnapshot(
            Map<String, String> fileHashes,
            Map<String, List<String>> directoryEntries,
            List<ExclusivityIssue> issues
    ) {
        private ExclusivitySnapshot {
            fileHashes = Collections.unmodifiableMap(new TreeMap<>(fileHashes));
            directoryEntries = Collections.unmodifiableMap(new TreeMap<>(directoryEntries));
            issues = List.copyOf(issues);
        }

        private static ExclusivitySnapshot empty() {
            return new ExclusivitySnapshot(Map.of(), Map.of(), List.of());
        }
    }

    private static final class PlanDiagnostics {
        private final int maximum;
        private final List<IrisStructureAdoptionDiagnostic> values = new ArrayList<>();
        private boolean blocked;

        private PlanDiagnostics(int maximum) {
            this.maximum = maximum;
        }

        private void error(
                IrisStructureAdoptionDiagnostic.Code code,
                String resource,
                String detail,
                String recommendation
        ) {
            add(IrisStructureAdoptionDiagnostic.Severity.ERROR, code, resource, detail, recommendation);
        }

        private void warning(
                IrisStructureAdoptionDiagnostic.Code code,
                String resource,
                String detail,
                String recommendation
        ) {
            add(IrisStructureAdoptionDiagnostic.Severity.WARNING, code, resource, detail, recommendation);
        }

        private void info(
                IrisStructureAdoptionDiagnostic.Code code,
                String resource,
                String detail,
                String recommendation
        ) {
            add(IrisStructureAdoptionDiagnostic.Severity.INFO, code, resource, detail, recommendation);
        }

        private void add(
                IrisStructureAdoptionDiagnostic.Severity severity,
                IrisStructureAdoptionDiagnostic.Code code,
                String resource,
                String detail,
                String recommendation
        ) {
            if (severity == IrisStructureAdoptionDiagnostic.Severity.ERROR) {
                blocked = true;
            }
            if (values.size() < maximum) {
                values.add(new IrisStructureAdoptionDiagnostic(
                        severity,
                        code,
                        resource,
                        detail,
                        recommendation));
            }
        }

        private boolean blocked() {
            return blocked;
        }

        private List<IrisStructureAdoptionDiagnostic> values() {
            return List.copyOf(values);
        }
    }

    private static final class StalePlanException extends IOException {
        private final String resource;

        private StalePlanException(String resource, String message) {
            super(message);
            this.resource = resource;
        }

        private String resource() {
            return resource;
        }
    }
}
