package com.volmit.host;

import com.volmit.generation.Dependencies;
import com.volmit.mantle.BlockBox;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementAccess;
import com.volmit.placement.PlacementContext;
import com.volmit.placement.PlacementEdit;
import com.volmit.placement.PlacementModifier;
import com.volmit.placement.PlacementPlan;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Reference placement orchestration, resolved at pack load. Same-ID instances must agree on
 * implementation and dependencies; occurrences are unique and sorted lexically within an ID.
 * The host supplies prepared, serialized storage and enforces platform/placement policies.
 * This runner buffers all mapped candidates; host validation must reject collisions or unsupported
 * writes before execution. It does not silently skip writes, transform geometry or place live blocks.
 */
public final class PlacementPipeline {
    private final List<ConfiguredPlacementModifier> ordered;

    private PlacementPipeline(List<ConfiguredPlacementModifier> ordered) {
        this.ordered = List.copyOf(ordered);
    }

    public static PlacementPipeline resolve(List<ConfiguredPlacementModifier> modifiers) {
        var instances = List.copyOf(modifiers);
        var declarations = collectDeclarations(instances);
        var modifierIds = DependencyOrder.resolve(declarations);
        var orderedOccurrences = OccurrenceOrder.order(
                modifierIds,
                instances,
                ConfiguredPlacementModifier::id,
                ConfiguredPlacementModifier::occurrence);
        return new PlacementPipeline(orderedOccurrences);
    }

    private static Map<ResourceId, Dependencies> collectDeclarations(
            List<ConfiguredPlacementModifier> instances) {
        var representatives = new HashMap<ResourceId, ConfiguredPlacementModifier>();
        var occurrences = new HashSet<ResourceId>();
        var declarations = new HashMap<ResourceId, Dependencies>();

        for (var modifier : instances) {
            checkUniqueOccurrence(modifier, occurrences);
            checkCompatibleIdentity(modifier, representatives);
            declarations.put(modifier.id(), modifier.dependencies());
        }
        return declarations;
    }

    private static void checkUniqueOccurrence(
            ConfiguredPlacementModifier modifier,
            Set<ResourceId> occurrences) {
        if (!occurrences.add(modifier.occurrence())) {
            throw new IllegalArgumentException("Duplicate placement occurrence: " + modifier.occurrence());
        }
    }

    private static void checkCompatibleIdentity(
            ConfiguredPlacementModifier modifier,
            Map<ResourceId, ConfiguredPlacementModifier> representatives) {
        var previous = representatives.putIfAbsent(modifier.id(), modifier);
        if (previous == null || isCompatible(previous, modifier)) {
            return;
        }
        throw new IllegalArgumentException("Conflicting placement identity: " + modifier.id());
    }

    private static boolean isCompatible(
            ConfiguredPlacementModifier first,
            ConfiguredPlacementModifier second) {
        return first.modifier().getClass().equals(second.modifier().getClass())
                && first.dependencies().equals(second.dependencies());
    }

    public List<ConfiguredPlacementModifier> ordered() {
        return ordered;
    }

    public int writeRadius() {
        return ordered.stream()
                .mapToInt(ConfiguredPlacementModifier::writeRadius)
                .max()
                .orElse(0);
    }

    /** Takes ownership of a fresh edit, closing it on success, rejection or exception.
     * Contexts are opened once per occurrence and must use dedicated RNGs from one pack snapshot.
     * The caller releases those invocation views after this call. The host must prepare the full
     * footprint plus declared reach; a prepare hook that moves outside it fails without clipping.
     * hostValidation must be read-only and covers collisions, support, protection, state/payload
     * validity and any policies beyond modifier validation. Callbacks must not commit the edit.
     */
    public Optional<PlacementPlan> place(
            PlacementRequest request,
            PlacementEdit edit,
            Function<ConfiguredPlacementModifier, PlacementContext> contexts,
            Predicate<PlacementPlan> hostValidation) {
        Objects.requireNonNull(edit, "edit");
        try (edit) {
            requireInputs(request, contexts, hostValidation);
            var invocations = openInvocations(contexts);
            var preparedRequest = prepareAll(request, invocations);
            if (preparedRequest.isEmpty()) {
                return Optional.empty();
            }

            var prepared = preparedRequest.orElseThrow();
            checkPreparedArea(prepared, edit);
            var plan = createPlan(prepared, invocations, edit);
            if (!validatePlan(plan, invocations, hostValidation)) {
                return Optional.empty();
            }

            var hooks = openHookInvocations(prepared, invocations, edit);
            runBeforeHooks(plan, hooks);
            writePlan(plan, edit);
            runAfterHooks(plan, hooks);
            edit.commit();
            return Optional.of(plan);
        }
    }

    private static void requireInputs(
            PlacementRequest request,
            Function<ConfiguredPlacementModifier, PlacementContext> contexts,
            Predicate<PlacementPlan> hostValidation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(hostValidation, "hostValidation");
    }

    private List<PlacementInvocation> openInvocations(
            Function<ConfiguredPlacementModifier, PlacementContext> contexts) {
        var invocations = new ArrayList<PlacementInvocation>(ordered.size());
        for (var modifier : ordered) {
            var context = Objects.requireNonNull(contexts.apply(modifier), "context");
            invocations.add(new PlacementInvocation(modifier, context));
        }
        return List.copyOf(invocations);
    }

    private static Optional<PlacementRequest> prepareAll(
            PlacementRequest initialRequest,
            List<PlacementInvocation> invocations) {
        var attemptId = initialRequest.id();
        var request = initialRequest;

        for (var invocation : invocations) {
            var prepared = Objects.requireNonNull(
                    invocation.modifier().modifier().prepare(invocation.context(), request),
                    "prepared request");
            if (prepared.isEmpty()) {
                return Optional.empty();
            }

            request = prepared.orElseThrow();
            if (!request.id().equals(attemptId)) {
                throw new IllegalArgumentException("Prepare must preserve placement attempt ID");
            }
        }
        return Optional.of(request);
    }

    private PlacementPlan createPlan(
            PlacementRequest request,
            List<PlacementInvocation> invocations,
            PlacementEdit edit) {
        var blocks = mapBlocks(request, invocations);
        var markers = mapMarkers(request);
        var plan = new PlacementPlan(request, blocks, markers);

        // Compute checked half-open bounds before any mutation, including integer extremes.
        plan.writtenBounds();
        validateMappedPositions(plan, request, edit, writeRadius());
        return plan;
    }

    private static List<PlacedBlock> mapBlocks(
            PlacementRequest request,
            List<PlacementInvocation> invocations) {
        var mappedBlocks = new ArrayList<PlacedBlock>();
        for (var block : request.object().blocks()) {
            var mapped = mapBlock(request, block, invocations);
            mapped.ifPresent(mappedBlocks::add);
        }
        return mappedBlocks;
    }

    private static Optional<PlacedBlock> mapBlock(
            PlacementRequest request,
            ObjectBlock block,
            List<PlacementInvocation> invocations) {
        Optional<PlacedBlock> mapped = Optional.of(new PlacedBlock(
                request.worldPosition(block.position()),
                block.state(),
                block.blockEntity()));

        for (var invocation : invocations) {
            var modifier = invocation.modifier().modifier();
            var next = Objects.requireNonNull(
                    modifier.mapBlock(invocation.context(), mapped.orElseThrow()),
                    "mapped block");
            if (next.isEmpty()) {
                return Optional.empty();
            }
            mapped = next;
        }
        return mapped;
    }

    private static List<PlacedMarker> mapMarkers(PlacementRequest request) {
        var markers = new ArrayList<PlacedMarker>();
        for (var marker : request.object().markers()) {
            markers.add(new PlacedMarker(
                    request.worldPosition(marker.position()),
                    marker.type(),
                    marker.attributes()));
        }
        return markers;
    }

    private static void validateMappedPositions(
            PlacementPlan plan,
            PlacementRequest request,
            PlacementEdit edit,
            int radius) {
        for (var block : plan.blocks()) {
            checkPosition(block.position(), request.footprint(), edit.bounds(), radius);
        }
        for (var marker : plan.markers()) {
            checkPosition(marker.position(), request.footprint(), edit.bounds(), radius);
        }
    }

    private static boolean validatePlan(
            PlacementPlan plan,
            List<PlacementInvocation> invocations,
            Predicate<PlacementPlan> hostValidation) {
        if (!hostValidation.test(plan)) {
            return false;
        }
        for (var invocation : invocations) {
            if (!invocation.modifier().modifier().validate(invocation.context(), plan)) {
                return false;
            }
        }
        return true;
    }

    private List<HookInvocation> openHookInvocations(
            PlacementRequest request,
            List<PlacementInvocation> invocations,
            PlacementEdit edit) {
        var hooks = new ArrayList<HookInvocation>(invocations.size());
        for (var invocation : invocations) {
            var access = hookAccess(invocation.modifier(), request, edit);
            hooks.add(new HookInvocation(invocation, access));
        }
        return List.copyOf(hooks);
    }

    private static void runBeforeHooks(PlacementPlan plan, List<HookInvocation> hooks) {
        for (var hook : hooks) {
            hook.invocation().modifier().modifier().beforePlace(
                    hook.invocation().context(),
                    hook.access(),
                    plan);
        }
    }

    private static void writePlan(PlacementPlan plan, PlacementEdit edit) {
        for (var block : plan.blocks()) {
            edit.set(block);
        }
        for (var marker : plan.markers()) {
            edit.marker(marker);
        }
    }

    private static void runAfterHooks(PlacementPlan plan, List<HookInvocation> hooks) {
        for (var hook : hooks) {
            hook.invocation().modifier().modifier().afterPlace(
                    hook.invocation().context(),
                    hook.access(),
                    plan);
        }
    }

    private static PlacementAccess hookAccess(
            ConfiguredPlacementModifier modifier,
            PlacementRequest request,
            PlacementEdit edit) {
        var footprint = request.footprint();
        int radius = modifier.writeRadius();
        var bounds = new BlockBox(
                Math.subtractExact(footprint.minX(), radius),
                edit.bounds().minY(),
                Math.subtractExact(footprint.minZ(), radius),
                Math.addExact(footprint.maxX(), radius),
                edit.bounds().maxY(),
                Math.addExact(footprint.maxZ(), radius));
        return PlacementViews.restrict(edit, bounds);
    }

    private void checkPreparedArea(PlacementRequest request, PlacementEdit edit) {
        var footprint = request.footprint();
        var preparedBounds = edit.bounds();
        if (!preparedBounds.contains(footprint)
                || !containsHorizontalReach(preparedBounds, footprint, writeRadius())) {
            throw new IndexOutOfBoundsException("Placement footprint/reach outside prepared bounds");
        }
    }

    private static void checkPosition(
            BlockPosition position,
            BlockBox footprint,
            BlockBox preparedBounds,
            int radius) {
        if (!preparedBounds.contains(position.x(), position.y(), position.z())
                || !isWithinHorizontalReach(position.x(), position.z(), footprint, radius)) {
            throw new IndexOutOfBoundsException("Mapped placement write outside declared reach: " + position);
        }
    }

    private static boolean containsHorizontalReach(BlockBox outer, BlockBox footprint, int radius) {
        return containsReachOnAxis(outer.minX(), outer.maxX(), footprint.minX(), footprint.maxX(), radius)
                && containsReachOnAxis(outer.minZ(), outer.maxZ(), footprint.minZ(), footprint.maxZ(), radius);
    }

    private static boolean isWithinHorizontalReach(
            int x,
            int z,
            BlockBox footprint,
            int radius) {
        return isWithinReachOnAxis(x, footprint.minX(), footprint.maxX(), radius)
                && isWithinReachOnAxis(z, footprint.minZ(), footprint.maxZ(), radius);
    }

    private static boolean containsReachOnAxis(
            int outerMin,
            int outerMax,
            int footprintMin,
            int footprintMax,
            int radius) {
        return (long) footprintMin - radius >= outerMin
                && (long) footprintMax + radius <= outerMax;
    }

    private static boolean isWithinReachOnAxis(int position, int min, int max, int radius) {
        return position >= (long) min - radius && position < (long) max + radius;
    }

    private record PlacementInvocation(
            ConfiguredPlacementModifier modifier,
            PlacementContext context) {}

    private record HookInvocation(PlacementInvocation invocation, PlacementAccess access) {}
}
