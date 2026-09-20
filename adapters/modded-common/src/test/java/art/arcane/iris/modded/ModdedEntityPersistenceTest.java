package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityBehavior;

import org.junit.Test;
import org.junit.Before;
import art.arcane.volmlib.nativelib.modded.EntityBehaviorTags;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedEntityPersistenceTest {
    @Before
    public void configureEntityTags() {
        NativeEntityBehavior.bind(new EntityBehaviorTags("iris_non_persistent", "iris_unaware"));
    }

    @Test
    public void generatedNonPersistentEntityIsExcludedFromVanillaSaves() {
        Set<String> tags = new HashSet<>();

        NativeEntityBehavior.configurePersistenceTags(tags, false);

        assertFalse(NativeEntityBehavior.shouldSave(tags, true));
    }

    @Test
    public void positivePersistenceRemovesTheSaveExclusion() {
        Set<String> tags = new HashSet<>();
        NativeEntityBehavior.configurePersistenceTags(tags, false);

        NativeEntityBehavior.configurePersistenceTags(tags, true);

        assertTrue(NativeEntityBehavior.shouldSave(tags, true));
    }

    @Test
    public void interceptionNeverOverridesVanillaSaveRejection() {
        Set<String> tags = new HashSet<>();

        NativeEntityBehavior.configurePersistenceTags(tags, true);

        assertFalse(NativeEntityBehavior.shouldSave(tags, false));
    }
}
