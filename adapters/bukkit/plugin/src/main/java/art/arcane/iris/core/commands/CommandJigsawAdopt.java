package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionDiagnostic;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionInputKind;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionPlan;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionRequest;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionResult;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionService;
import art.arcane.iris.core.structure.conversion.IrisStructureAdoptionStrategy;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Director(name = "adopt", description = "Inspect and apply safe ownership adoption plans",
        origin = DirectorOrigin.PLAYER)
public class CommandJigsawAdopt implements DirectorExecutor {
    private static final Map<UUID, AdoptionPlanBinding> ADOPTION_PLANS = new ConcurrentHashMap<>();
    private static final Map<Path, IrisStructureAdoptionService> ADOPTION_SERVICES = new ConcurrentHashMap<>();

    @Director(description = "Inspect an Iris graph for safe Studio ownership adoption", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void inspect(
            @Param(description = "Pack dimension") IrisDimension dimension,
            @Param(description = "Existing Iris structure key") String source,
            @Param(description = "New owned Iris key, or auto", defaultValue = "auto") String target,
            @Param(description = "auto, in-place, or clone", defaultValue = "auto",
                    customHandler = JigsawAdoptionStrategyHandler.class) String strategy
    ) {
        VolmitSender commandSender = sender();
        IrisData data = CommandJigsaw.requireData(dimension, commandSender);
        if (data == null) {
            return;
        }
        String sourceKey;
        Optional<StructureKey> targetKey;
        IrisStructureAdoptionStrategy adoptionStrategy;
        Path root;
        try {
            sourceKey = parseAdoptionSource(source);
            targetKey = parseAdoptionTarget(target);
            adoptionStrategy = parseAdoptionStrategy(strategy);
            root = canonicalAdoptionRoot(data.getDataFolder().toPath());
        } catch (IOException | IllegalArgumentException exception) {
            CommandJigsaw.sendError(commandSender, "Could not inspect adoption source: " + exception.getMessage());
            return;
        }
        Player targetPlayer = player();
        UUID ownerId = targetPlayer.getUniqueId();
        commandSender.sendMessage(C.GRAY + "Inspecting Jigsaw adoption source '" + sourceKey
                + "' asynchronously...");
        try {
            J.a(() -> runAdoptionInspect(
                    targetPlayer,
                    commandSender,
                    ownerId,
                    dimension,
                    data,
                    root,
                    sourceKey,
                    targetKey,
                    adoptionStrategy));
        } catch (RuntimeException exception) {
            Iris.reportError("Failed to schedule Jigsaw adoption inspection for '"
                    + sourceKey + "'.", exception);
            CommandJigsaw.sendError(commandSender, "Jigsaw adoption inspection could not be scheduled: "
                    + exception.getMessage());
        }
    }

    @Director(description = "Apply one inspected adoption plan without overwriting", sync = true,
            origin = DirectorOrigin.PLAYER)
    public void apply(
            @Param(description = "Plan UUID returned by adopt inspect",
                    customHandler = JigsawAdoptionPlanHandler.class) String planId
    ) {
        VolmitSender commandSender = sender();
        UUID parsedPlanId;
        try {
            parsedPlanId = UUID.fromString(planId);
        } catch (IllegalArgumentException exception) {
            CommandJigsaw.sendError(commandSender, "Adoption plan ID must be a UUID.");
            return;
        }
        if (CommandJigsaw.studioIsActiveOrOpening()) {
            CommandJigsaw.sendError(commandSender,
                    "Close the active or opening Jigsaw Studio before applying an adoption plan.");
            return;
        }
        pruneAdoptionPlanBindings();
        AdoptionPlanBinding binding = ADOPTION_PLANS.get(parsedPlanId);
        UUID playerId = player().getUniqueId();
        if (binding == null || !binding.ownerId().equals(playerId)) {
            CommandJigsaw.sendError(commandSender, "No active adoption plan '" + parsedPlanId
                    + "' belongs to your player session. Run adopt inspect again.");
            return;
        }
        if (binding.service().plan(parsedPlanId).isEmpty()) {
            ADOPTION_PLANS.remove(parsedPlanId, binding);
            CommandJigsaw.sendError(commandSender, "Adoption plan '" + parsedPlanId
                    + "' expired or was already consumed. Run adopt inspect again.");
            return;
        }
        if (!ADOPTION_PLANS.remove(parsedPlanId, binding)) {
            CommandJigsaw.sendError(commandSender, "Adoption plan '" + parsedPlanId + "' is already being applied.");
            return;
        }
        Player targetPlayer = player();
        commandSender.sendMessage(C.GRAY + "Applying adoption plan '" + parsedPlanId
                + "' asynchronously without overwrite...");
        try {
            J.a(() -> runAdoptionApply(targetPlayer, commandSender, parsedPlanId, binding));
        } catch (RuntimeException exception) {
            ADOPTION_PLANS.putIfAbsent(parsedPlanId, binding);
            Iris.reportError("Failed to schedule Jigsaw adoption plan '" + parsedPlanId + "'.", exception);
            CommandJigsaw.sendError(commandSender, "Jigsaw adoption could not be scheduled: " + exception.getMessage());
        }
    }

    private static void pruneAdoptionPlanBindings() {
        for (Map.Entry<UUID, AdoptionPlanBinding> entry : ADOPTION_PLANS.entrySet()) {
            AdoptionPlanBinding binding = entry.getValue();
            if (binding.service().plan(entry.getKey()).isEmpty()) {
                ADOPTION_PLANS.remove(entry.getKey(), binding);
            }
        }
    }

    private static void runAdoptionApply(
            Player targetPlayer,
            VolmitSender commandSender,
            UUID planId,
            AdoptionPlanBinding binding
    ) {
        if (CommandJigsaw.studioIsActiveOrOpening()) {
            ADOPTION_PLANS.putIfAbsent(planId, binding);
            J.runEntity(targetPlayer, () -> CommandJigsaw.sendError(commandSender,
                    "Close the active or opening Jigsaw Studio before applying an adoption plan."));
            return;
        }
        IrisStructureAdoptionResult result;
        Throwable failure = null;
        try {
            result = binding.service().apply(binding.plan());
        } catch (Throwable throwable) {
            result = null;
            failure = throwable;
            Iris.reportError("Failed to apply Jigsaw adoption plan '" + planId + "'.", throwable);
        }
        IrisStructureAdoptionResult completedResult = result;
        Throwable completedFailure = failure;
        J.runEntity(targetPlayer, () -> finishAdoptionApply(
                targetPlayer,
                commandSender,
                binding,
                completedResult,
                completedFailure));
    }

    private static void runAdoptionInspect(
            Player targetPlayer,
            VolmitSender commandSender,
            UUID ownerId,
            IrisDimension dimension,
            IrisData data,
            Path root,
            String sourceKey,
            Optional<StructureKey> targetKey,
            IrisStructureAdoptionStrategy strategy
    ) {
        IrisStructureAdoptionService service = null;
        IrisStructureAdoptionPlan plan = null;
        Throwable failure = null;
        try {
            IrisStructureAdoptionInputKind inputKind = adoptionInputKind(root, sourceKey);
            service = ADOPTION_SERVICES.computeIfAbsent(root, IrisStructureAdoptionService::new);
            plan = service.inspect(new IrisStructureAdoptionRequest(
                    sourceKey,
                    targetKey,
                    strategy,
                    inputKind));
        } catch (Throwable throwable) {
            failure = throwable;
            Iris.reportError("Failed to inspect Jigsaw adoption source '" + sourceKey + "'.", throwable);
        }
        IrisStructureAdoptionService completedService = service;
        IrisStructureAdoptionPlan completedPlan = plan;
        Throwable completedFailure = failure;
        J.runEntity(targetPlayer, () -> finishAdoptionInspect(
                commandSender,
                ownerId,
                dimension,
                data,
                completedService,
                completedPlan,
                completedFailure));
    }

    private static void finishAdoptionInspect(
            VolmitSender commandSender,
            UUID ownerId,
            IrisDimension dimension,
            IrisData data,
            IrisStructureAdoptionService service,
            IrisStructureAdoptionPlan plan,
            Throwable failure
    ) {
        if (failure != null || service == null || plan == null) {
            CommandJigsaw.sendError(commandSender, "Jigsaw adoption inspection failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        pruneAdoptionPlanBindings();
        if (service.plan(plan.planId()).isPresent()) {
            ADOPTION_PLANS.put(plan.planId(), new AdoptionPlanBinding(
                    ownerId, dimension, data, service, plan));
        }
        reportAdoptionPlan(commandSender, plan);
    }

    private static void finishAdoptionApply(
            Player targetPlayer,
            VolmitSender commandSender,
            AdoptionPlanBinding binding,
            IrisStructureAdoptionResult result,
            Throwable failure
    ) {
        if (failure != null || result == null) {
            CommandJigsaw.sendError(commandSender, "Jigsaw adoption failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        reportAdoptionResult(commandSender, result);
        if (!result.successful()) {
            return;
        }
        StructureKey target = result.receipt().orElseThrow().targetStructure();
        binding.data().invalidateStructureResources();
        CommandJigsawOpen.openProject(targetPlayer, commandSender, binding.dimension(), target.path(), CommandJigsaw.DEFAULT_STUDIO_SEED);
    }

    private static void reportAdoptionPlan(VolmitSender commandSender, IrisStructureAdoptionPlan plan) {
        C statusColor = plan.canApply() ? C.GREEN : C.RED;
        commandSender.sendMessage(statusColor + "Adoption plan " + plan.planId() + " -> "
                + plan.disposition() + " for " + plan.targetStructure().value() + ".");
        commandSender.sendMessage(C.GRAY + "Resources=" + plan.resourceCount()
                + ", bytes=" + plan.totalSourceBytes()
                + ", errors=" + plan.errorCount()
                + ", warnings=" + plan.warningCount() + ".");
        for (IrisStructureAdoptionDiagnostic diagnostic : plan.diagnostics()) {
            commandSender.sendMessage(adoptionColor(diagnostic).toString() + diagnostic.summary());
        }
        if (plan.canApply()) {
            commandSender.sendMessage(C.AQUA + "Apply with /iris jigsaw adopt apply " + plan.planId());
        }
    }

    private static void reportAdoptionResult(VolmitSender commandSender, IrisStructureAdoptionResult result) {
        commandSender.sendMessage((result.successful() ? C.GREEN : C.RED)
                + "Adoption plan " + result.planId() + ": " + result.status() + ".");
        for (IrisStructureAdoptionDiagnostic diagnostic : result.diagnostics()) {
            commandSender.sendMessage(adoptionColor(diagnostic).toString() + diagnostic.summary());
        }
    }

    private static C adoptionColor(IrisStructureAdoptionDiagnostic diagnostic) {
        return switch (diagnostic.severity()) {
            case ERROR -> C.RED;
            case WARNING -> C.YELLOW;
            case INFO -> C.GRAY;
        };
    }

    static String parseAdoptionSource(String value) {
        String normalized = Objects.requireNonNull(value, "Adoption source").trim()
                .toLowerCase(Locale.ROOT);
        StructureKey source = StructureKey.parse(normalized, "iris");
        if (!"iris".equals(source.namespace())) {
            throw new IllegalArgumentException("Adoption sources must use the iris namespace.");
        }
        return source.path();
    }

    static Optional<StructureKey> parseAdoptionTarget(String value) {
        String normalized = Objects.requireNonNull(value, "Adoption target").trim()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "auto".equals(normalized)) {
            return Optional.empty();
        }
        StructureKey target = StructureKey.parse(normalized, "iris");
        if (!"iris".equals(target.namespace())) {
            throw new IllegalArgumentException("Adoption targets must use the iris namespace.");
        }
        return Optional.of(target);
    }

    private static IrisStructureAdoptionStrategy parseAdoptionStrategy(String value) {
        return switch (Objects.requireNonNull(value, "Adoption strategy").trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> IrisStructureAdoptionStrategy.AUTO;
            case "in-place", "in_place", "inplace", "claim" -> IrisStructureAdoptionStrategy.IN_PLACE;
            case "clone", "copy" -> IrisStructureAdoptionStrategy.CLONE;
            default -> throw new IllegalArgumentException("Adoption strategy must be auto, in-place, or clone.");
        };
    }

    static IrisStructureAdoptionInputKind adoptionInputKind(Path packRoot, String source) throws IOException {
        Path root = canonicalAdoptionRoot(packRoot);
        StructureKey sourceKey = new StructureKey("iris", parseAdoptionSource(source));
        Path manifestPath = new StructureTransactionWriter(root).ownershipManifestPath(sourceKey);
        if (!Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return IrisStructureAdoptionInputKind.UNOWNED_IRIS;
        }
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ownership manifest is not a regular file: " + manifestPath);
        }
        StructureOwnershipManifest manifest;
        try {
            manifest = StructureOwnershipManifest.fromJson(Files.readAllBytes(manifestPath));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid ownership manifest at " + manifestPath, exception);
        }
        if (!manifest.structure().equals(sourceKey)) {
            throw new IOException("Ownership manifest at " + manifestPath
                    + " belongs to " + manifest.structure().value());
        }
        return manifest.provenance().origin() == StructureOwnershipManifest.Origin.MANAGED_DATAPACK
                ? IrisStructureAdoptionInputKind.MANAGED_DATAPACK
                : IrisStructureAdoptionInputKind.UNOWNED_IRIS;
    }

    private static Path canonicalAdoptionRoot(Path packRoot) throws IOException {
        Path normalized = Objects.requireNonNull(packRoot, "Adoption pack root").toAbsolutePath().normalize();
        return Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) ? normalized.toRealPath() : normalized;
    }

    private record AdoptionPlanBinding(
            UUID ownerId,
            IrisDimension dimension,
            IrisData data,
            IrisStructureAdoptionService service,
            IrisStructureAdoptionPlan plan
    ) {
    }

    public static final class JigsawAdoptionStrategyHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>("auto", "in-place", "clone");
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            try {
                return switch (parseAdoptionStrategy(input)) {
                    case AUTO -> "auto";
                    case IN_PLACE -> "in-place";
                    case CLONE -> "clone";
                };
            } catch (IllegalArgumentException exception) {
                throw new DirectorParsingException(exception.getMessage());
            }
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }

    public static final class JigsawAdoptionPlanHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>();
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            try {
                return UUID.fromString(input).toString();
            } catch (IllegalArgumentException exception) {
                throw new DirectorParsingException("Adoption plan ID must be a UUID.");
            }
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }
    }
}
