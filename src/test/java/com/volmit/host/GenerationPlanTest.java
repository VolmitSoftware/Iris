package com.volmit.host;

import com.volmit.generation.*;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GenerationPlanTest {
    private static ResourceId id(String path) { return ResourceId.parse("test:" + path); }

    private static ConfiguredModifier<String> binding(String occurrence, String name, Dependencies dependencies) {
        return new ConfiguredModifier<>(id(occurrence), String.class, new Modifier(name, dependencies));
    }

    private static class Modifier extends GenerationModifier<String> {
        private final String name;
        private final Dependencies dependencies;
        Modifier(String name, Dependencies dependencies) { this.name = name; this.dependencies = dependencies; }
        public String id() { return "test:" + name; }
        public Dependencies depends() { return dependencies; }
        public void modify(GenerationContext context, Hunk<String> output) {}
    }

    @Test
    void orderingIsIndependentOfDiscoveryAndIncludesRepeatedOccurrences() {
        var first = binding("terrain/1", "terrain", Dependencies.none());
        var second = binding("terrain/0", "terrain", Dependencies.none());
        var caves = binding("caves/0", "caves", Dependencies.requires("test:terrain"));
        var decoration = binding("decoration/0", "decoration", Dependencies.requires("test:caves").beforeIfPresent("test:missing"));
        var a = GenerationPlan.resolve(List.of(decoration, first, caves, second));
        var b = GenerationPlan.resolve(List.of(second, caves, decoration, first));
        var expected = List.of(id("terrain/0"), id("terrain/1"), id("caves/0"), id("decoration/0"));
        assertEquals(expected, a.ordered().stream().map(ConfiguredModifier::occurrence).toList());
        assertEquals(expected, b.ordered().stream().map(ConfiguredModifier::occurrence).toList());
        var executed = new ArrayList<ResourceId>();
        a.execute(new GenerationPlan.Executor() {
            public <T> void execute(ConfiguredModifier<T> modifier) { executed.add(modifier.occurrence()); }
        });
        assertEquals(expected, executed);
        assertThrows(UnsupportedOperationException.class, () -> a.ordered().clear());
    }

    @Test
    void rejectsMissingRequirementsSelfEdgesAndCycles() {
        assertThrows(IllegalArgumentException.class, () -> DependencyOrder.resolve(Map.of(id("a"), Dependencies.requires("test:missing"))));
        assertThrows(IllegalArgumentException.class, () -> DependencyOrder.resolve(Map.of(id("a"), Dependencies.none().beforeIfPresent("test:a"))));
        assertThrows(IllegalArgumentException.class, () -> DependencyOrder.resolve(Map.of(id("a"), Dependencies.requires("test:b"), id("b"), Dependencies.requires("test:a"))));
    }

    @Test
    void rejectsDuplicateOccurrencesAndConflictingImplementationsOrDeclarations() {
        var a = binding("a/0", "a", Dependencies.none());
        assertThrows(IllegalArgumentException.class, () -> GenerationPlan.resolve(List.of(a, a)));
        assertThrows(IllegalArgumentException.class, () -> GenerationPlan.resolve(List.of(a,
                binding("a/1", "a", Dependencies.none().afterIfPresent("test:b")))));
        var otherImplementation = new ConfiguredModifier<>(id("a/2"), String.class, new Modifier("a", Dependencies.none()) {});
        assertThrows(IllegalArgumentException.class, () -> GenerationPlan.resolve(List.of(a, otherImplementation)));
    }

    @Test
    void repeatedEdgesAreCountedOnceAndIndependentIdsUseLexicalOrder() {
        var graph = Map.of(id("a"), Dependencies.none(), id("z"), Dependencies.none(),
                id("b"), Dependencies.requires("test:a").afterIfPresent("test:a"));
        assertEquals(List.of(id("a"), id("b"), id("z")), DependencyOrder.resolve(graph));
    }

    @Test
    void failuresStopDispatchAndNegativeReachFailsAtPackLoad() {
        var plan = GenerationPlan.resolve(List.of(binding("a", "a", Dependencies.none()), binding("b", "b", Dependencies.none())));
        var visited = new ArrayList<ResourceId>();
        assertThrows(IllegalStateException.class, () -> plan.execute(new GenerationPlan.Executor() {
            public <T> void execute(ConfiguredModifier<T> modifier) {
                visited.add(modifier.id());
                throw new IllegalStateException("Failed output");
            }
        }));
        assertEquals(List.of(id("a")), visited);
        assertThrows(IllegalArgumentException.class, () -> new ConfiguredModifier<>(id("negative"), String.class,
                new Modifier("a", Dependencies.none()) { public int mantleRadius() { return -1; } }));
    }
}
