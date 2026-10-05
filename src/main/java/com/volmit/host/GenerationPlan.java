package com.volmit.host;

import com.volmit.generation.Dependencies;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable pack-load ordering across output types. Instances sharing a modifier ID must
 * have the same implementation, output type and dependencies. Occurrence IDs must be unique.
 * Instances within one identity use lexical occurrence order. Resolve once, dispatch many times.
 * The host includes its built-in lifecycle steps in this graph and rejects incompatible edges.
 */
public final class GenerationPlan {
    private final List<ConfiguredModifier<?>> ordered;

    private GenerationPlan(List<ConfiguredModifier<?>> ordered) {
        this.ordered = List.copyOf(ordered);
    }

    public static GenerationPlan resolve(List<? extends ConfiguredModifier<?>> modifiers) {
        List<ConfiguredModifier<?>> instances = new ArrayList<>(modifiers);
        var declarations = collectDeclarations(instances);
        var modifierIds = DependencyOrder.resolve(declarations);
        var orderedOccurrences = OccurrenceOrder.order(
                modifierIds,
                instances,
                ConfiguredModifier::id,
                ConfiguredModifier::occurrence);
        return new GenerationPlan(orderedOccurrences);
    }

    private static Map<ResourceId, Dependencies> collectDeclarations(List<ConfiguredModifier<?>> instances) {
        var representatives = new HashMap<ResourceId, ConfiguredModifier<?>>();
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
            ConfiguredModifier<?> modifier,
            Set<ResourceId> occurrences) {
        if (!occurrences.add(modifier.occurrence())) {
            throw new IllegalArgumentException("Duplicate occurrence: " + modifier.occurrence());
        }
    }

    private static void checkCompatibleIdentity(
            ConfiguredModifier<?> modifier,
            Map<ResourceId, ConfiguredModifier<?>> representatives) {
        var previous = representatives.putIfAbsent(modifier.id(), modifier);
        if (previous == null || isCompatible(previous, modifier)) {
            return;
        }
        throw new IllegalArgumentException("Conflicting modifier identity: " + modifier.id());
    }

    private static boolean isCompatible(ConfiguredModifier<?> first, ConfiguredModifier<?> second) {
        return first.implementationType().equals(second.implementationType())
                && first.outputType().equals(second.outputType())
                && first.dependencies().equals(second.dependencies());
    }

    public List<ConfiguredModifier<?>> ordered() {
        return ordered;
    }

    /** Sequential dispatch only. Executor prepares/releases a dedicated context and typed output
     * for each applicable occurrence; applicability is host-owned. Failures stop dispatch.
     * This reference planner does not implement chunk scheduling, rollback or lifecycle bridging.
     */
    public void execute(Executor executor) {
        java.util.Objects.requireNonNull(executor, "executor");
        for (var modifier : ordered) {
            executor.execute(modifier);
        }
    }

    public interface Executor {
        <T> void execute(ConfiguredModifier<T> modifier);
    }
}
