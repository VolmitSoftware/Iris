package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityBehavior;

import org.junit.Test;
import org.junit.Before;
import art.arcane.volmlib.nativelib.modded.EntityBehaviorTags;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedEntityAwarenessTest {
    @Before
    public void configureEntityTags() {
        NativeEntityBehavior.bind(new EntityBehaviorTags("iris_non_persistent", "iris_unaware"));
    }

    @Test
    public void unawareConfigurationAddsThePartialAiTag() {
        Set<String> tags = new HashSet<>();

        NativeEntityBehavior.configureAwarenessTags(tags, false);

        assertFalse(NativeEntityBehavior.isAware(tags));
    }

    @Test
    public void awareConfigurationRemovesThePartialAiTag() {
        Set<String> tags = new HashSet<>();
        NativeEntityBehavior.configureAwarenessTags(tags, false);

        NativeEntityBehavior.configureAwarenessTags(tags, true);

        assertTrue(NativeEntityBehavior.isAware(tags));
    }

    @Test
    public void mobsAreAwareWithoutAnIrisTag() {
        assertTrue(NativeEntityBehavior.isAware(Set.of()));
    }
}
