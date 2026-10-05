package com.volmit.generation;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DependenciesTest {
    @Test
    void accumulatesAndDeduplicatesWithoutMutatingEarlierDeclarations() {
        var original = Dependencies.requires("iris:terrain", "iris:terrain");
        var result = original.andRequires("iris:carving")
                .afterIfPresent("example:caves").afterIfPresent("example:lakes")
                .beforeIfPresent("iris:ores").beforeIfPresent("iris:ores", "iris:objects");
        assertEquals(Set.of("iris:terrain"), original.required());
        assertTrue(original.after().isEmpty());
        assertTrue(original.before().isEmpty());
        assertEquals(Set.of("iris:terrain", "iris:carving"), result.required());
        assertEquals(Set.of("example:caves", "example:lakes"), result.after());
        assertEquals(Set.of("iris:ores", "iris:objects"), result.before());
    }

    @Test
    void copiesInputsAndExposesImmutableSets() {
        var ids = new HashSet<>(Set.of("iris:terrain"));
        var result = new Dependencies(ids, ids, ids);
        ids.clear();
        assertEquals(Set.of("iris:terrain"), result.required());
        assertThrows(UnsupportedOperationException.class, () -> result.required().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.after().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.before().clear());
        String[] input = {"iris:terrain"};
        var fromArray = Dependencies.requires(input);
        input[0] = "iris:ores";
        assertEquals(Set.of("iris:terrain"), fromArray.required());
    }

    @Test
    void supportsEmptyAndOptionalOnlyDeclarations() {
        assertEquals(new Dependencies(Set.of(), Set.of(), Set.of()), Dependencies.none());
        assertEquals(Dependencies.none(), Dependencies.requires());
        assertEquals(Set.of("iris:ores"), Dependencies.none().beforeIfPresent("iris:ores").before());
    }

    @Test
    void rejectsNullAndBlankIds() {
        assertThrows(IllegalArgumentException.class, () -> Dependencies.requires(" "));
        assertThrows(IllegalArgumentException.class, () -> Dependencies.none().afterIfPresent(""));
        assertThrows(IllegalArgumentException.class, () -> Dependencies.none().beforeIfPresent("\n"));
        assertThrows(NullPointerException.class, () -> Dependencies.requires((String) null));
        assertThrows(NullPointerException.class, () -> Dependencies.requires((String[]) null));
        assertThrows(NullPointerException.class, () -> new Dependencies(null, Set.of(), Set.of()));
    }
}
