/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.service.JigsawStudioRequest;
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
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.TextColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** {@code adopt}: inspects an Iris graph for safe Studio ownership adoption, then applies the inspected plan. */
final class AdoptionCommands {
    private static final Map<UUID, AdoptionPlanBinding> ADOPTION_PLANS = new ConcurrentHashMap<>();
    private static final Map<Path, IrisStructureAdoptionService> ADOPTION_SERVICES = new ConcurrentHashMap<>();
    private static final String AUTO = "auto";

    private static final Argument<String> SOURCE = Argument.word("source").describe("Existing Iris structure key");
    private static final Argument<String> TARGET = Argument.word("target").orElse(AUTO)
            .describe("New owned Iris key, or auto");
    private static final Argument<String> STRATEGY = Argument.word("strategy").orElse(AUTO)
            .describe("auto, in-place, or clone").suggesting(context -> List.of(AUTO, "in-place", "clone"));
    private static final Argument<String> PLAN = Argument.word("plan").describe("Plan UUID returned by adopt inspect");

    private final Supplier<Optional<JigsawStudioHost>> hosts;

    AdoptionCommands(Supplier<Optional<JigsawStudioHost>> hosts) {
        this.hosts = hosts;
    }

    Command command() {
        return Command.named("adopt").describe("Inspect and apply safe ownership adoption plans")
                .child(Command.named("inspect").describe("Inspect an Iris graph for safe Studio ownership adoption")
                        .argument(SOURCE).argument(TARGET).argument(STRATEGY)
                        .runs(this::inspect))
                .child(Command.named("apply").describe("Apply one inspected adoption plan without overwriting")
                        .argument(PLAN).runs(this::apply));
    }

    private void inspect(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        String sourceKey;
        Optional<StructureKey> targetKey;
        IrisStructureAdoptionStrategy adoptionStrategy;
        Path root;
        try {
            sourceKey = parseAdoptionSource(context.get(SOURCE));
            targetKey = parseAdoptionTarget(context.get(TARGET));
            adoptionStrategy = parseAdoptionStrategy(context.get(STRATEGY));
            root = canonicalAdoptionRoot(JigsawStudioRequest.rootOf(context.pack()));
        } catch (IOException | IllegalArgumentException exception) {
            use.error("Could not inspect adoption source: " + exception.getMessage());
            return;
        }
        UUID ownerId = use.player().id();
        use.say(TextColor.GRAY, "Inspecting Jigsaw adoption source '" + sourceKey + "' asynchronously...");
        runAdoptionInspect(use, ownerId, root, sourceKey, targetKey, adoptionStrategy);
    }

    private void apply(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        UUID parsedPlanId;
        try {
            parsedPlanId = UUID.fromString(context.get(PLAN));
        } catch (IllegalArgumentException exception) {
            use.error("Adoption plan ID must be a UUID.");
            return;
        }
        if (studioIsActiveOrOpening(use)) {
            use.error("Close the active or opening Jigsaw Studio before applying an adoption plan.");
            return;
        }
        pruneAdoptionPlanBindings();
        AdoptionPlanBinding binding = ADOPTION_PLANS.get(parsedPlanId);
        UUID playerId = use.player().id();
        if (binding == null || !binding.ownerId().equals(playerId)) {
            use.error("No active adoption plan '" + parsedPlanId
                    + "' belongs to your player session. Run adopt inspect again.");
            return;
        }
        if (binding.service().plan(parsedPlanId).isEmpty()) {
            ADOPTION_PLANS.remove(parsedPlanId, binding);
            use.error("Adoption plan '" + parsedPlanId
                    + "' expired or was already consumed. Run adopt inspect again.");
            return;
        }
        if (!ADOPTION_PLANS.remove(parsedPlanId, binding)) {
            use.error("Adoption plan '" + parsedPlanId + "' is already being applied.");
            return;
        }
        use.say(TextColor.GRAY, "Applying adoption plan '" + parsedPlanId
                + "' asynchronously without overwrite...");
        runAdoptionApply(use, parsedPlanId, binding);
    }

    private static void pruneAdoptionPlanBindings() {
        for (Map.Entry<UUID, AdoptionPlanBinding> entry : ADOPTION_PLANS.entrySet()) {
            AdoptionPlanBinding binding = entry.getValue();
            if (binding.service().plan(entry.getKey()).isEmpty()) {
                ADOPTION_PLANS.remove(entry.getKey(), binding);
            }
        }
    }

    private static boolean studioIsActiveOrOpening(StudioUse use) {
        return use.registry().activeOwnerId() != null
                || use.registry().openingOwnerId() != null;
    }

    private static void runAdoptionApply(StudioUse use, UUID planId, AdoptionPlanBinding binding) {
        if (studioIsActiveOrOpening(use)) {
            ADOPTION_PLANS.putIfAbsent(planId, binding);
            use.error("Close the active or opening Jigsaw Studio before applying an adoption plan.");
            return;
        }
        IrisStructureAdoptionResult result;
        Throwable failure = null;
        try {
            result = binding.service().apply(binding.plan());
        } catch (Throwable throwable) {
            result = null;
            failure = throwable;
            binding.pack().log().error("Failed to apply Jigsaw adoption plan '" + planId + "'.", throwable);
        }
        finishAdoptionApply(use, binding, result, failure);
    }

    private static void runAdoptionInspect(
            StudioUse use,
            UUID ownerId,
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
            use.pack().log().error("Failed to inspect Jigsaw adoption source '" + sourceKey + "'.", throwable);
        }
        finishAdoptionInspect(use, ownerId, service, plan, failure);
    }

    private static void finishAdoptionInspect(
            StudioUse use,
            UUID ownerId,
            IrisStructureAdoptionService service,
            IrisStructureAdoptionPlan plan,
            Throwable failure
    ) {
        if (failure != null || service == null || plan == null) {
            use.error("Jigsaw adoption inspection failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        pruneAdoptionPlanBindings();
        if (service.plan(plan.planId()).isPresent()) {
            ADOPTION_PLANS.put(plan.planId(), new AdoptionPlanBinding(
                    ownerId, use.pack(), service, plan));
        }
        reportAdoptionPlan(use, plan);
    }

    private static void finishAdoptionApply(
            StudioUse use,
            AdoptionPlanBinding binding,
            IrisStructureAdoptionResult result,
            Throwable failure
    ) {
        if (failure != null || result == null) {
            use.error("Jigsaw adoption failed: " + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        reportAdoptionResult(use, result);
        if (!result.successful()) {
            return;
        }
        StructureKey target = result.receipt().orElseThrow().targetStructure();
        JigsawStudioRequest.reload(binding.pack());
        StudioLifecycleCommands.openProject(use.onPack(binding.pack()), target.path());
    }

    private static void reportAdoptionPlan(StudioUse use, IrisStructureAdoptionPlan plan) {
        use.say(plan.canApply() ? TextColor.GREEN : TextColor.RED,
                "Adoption plan " + plan.planId() + " -> "
                        + plan.disposition() + " for " + plan.targetStructure().value() + ".");
        use.say(TextColor.GRAY, "Resources=" + plan.resourceCount()
                + ", bytes=" + plan.totalSourceBytes()
                + ", errors=" + plan.errorCount()
                + ", warnings=" + plan.warningCount() + ".");
        for (IrisStructureAdoptionDiagnostic diagnostic : plan.diagnostics()) {
            use.say(adoptionColor(diagnostic), diagnostic.summary());
        }
        if (plan.canApply()) {
            use.say(TextColor.AQUA, "Apply with /iris jigsaw adopt apply " + plan.planId());
        }
    }

    private static void reportAdoptionResult(StudioUse use, IrisStructureAdoptionResult result) {
        use.say(result.successful() ? TextColor.GREEN : TextColor.RED,
                "Adoption plan " + result.planId() + ": " + result.status() + ".");
        for (IrisStructureAdoptionDiagnostic diagnostic : result.diagnostics()) {
            use.say(adoptionColor(diagnostic), diagnostic.summary());
        }
    }

    private static TextColor adoptionColor(IrisStructureAdoptionDiagnostic diagnostic) {
        return switch (diagnostic.severity()) {
            case ERROR -> TextColor.RED;
            case WARNING -> TextColor.YELLOW;
            case INFO -> TextColor.GRAY;
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
        if (normalized.isEmpty() || AUTO.equals(normalized)) {
            return Optional.empty();
        }
        StructureKey target = StructureKey.parse(normalized, "iris");
        if (!"iris".equals(target.namespace())) {
            throw new IllegalArgumentException("Adoption targets must use the iris namespace.");
        }
        return Optional.of(target);
    }

    static IrisStructureAdoptionStrategy parseAdoptionStrategy(String value) {
        return switch (Objects.requireNonNull(value, "Adoption strategy").trim().toLowerCase(Locale.ROOT)) {
            case AUTO -> IrisStructureAdoptionStrategy.AUTO;
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
            ToolPack pack,
            IrisStructureAdoptionService service,
            IrisStructureAdoptionPlan plan
    ) {
    }
}
