package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityBehavior;

import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.BeforeClass;
import net.minecraft.world.entity.Mob;
import org.junit.Test;
import org.junit.Before;
import art.arcane.volmlib.nativelib.modded.EntityBehaviorTags;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedEntityPersistenceTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Before
    public void configureEntityTags() {
        NativeEntityBehavior.bind(new EntityBehaviorTags("iris_non_persistent", "iris_unaware"));
    }

    @Test
    public void explicitlyExcludedEntityIsExcludedFromVanillaSaves() {
        Set<String> tags = new HashSet<>();

        NativeEntityBehavior.configureSavingTags(tags, false);

        assertFalse(NativeEntityBehavior.shouldSave(tags, true));
    }

    @Test
    public void positivePersistenceRemovesTheSaveExclusion() {
        Set<String> tags = new HashSet<>();
        NativeEntityBehavior.configureSavingTags(tags, false);

        NativeEntityBehavior.configureSavingTags(tags, true);

        assertTrue(NativeEntityBehavior.shouldSave(tags, true));
    }

    @Test
    public void interceptionNeverOverridesVanillaSaveRejection() {
        Set<String> tags = new HashSet<>();

        NativeEntityBehavior.configureSavingTags(tags, true);

        assertFalse(NativeEntityBehavior.shouldSave(tags, false));
    }

    @Test
    public void defaultAmbientMobSavesWithoutBecomingPermanent() {
        Mob mob = mob();
        NativeSpawnedEntity entity = new NativeSpawnedEntity(mob);
        IrisEntity options = new IrisEntity();

        ModdedEntitySpawner.applyPersistence(options, entity, false);
        entity.configureMob(options);

        assertTrue(NativeEntityBehavior.shouldSave(mob, true));
        verify(mob, never()).setPersistenceRequired();
    }

    @Test
    public void explicitKeepForceAndRemovalFlagsControlNativeDespawn() {
        for (boolean keep : new boolean[]{false, true}) {
            for (boolean force : new boolean[]{false, true}) {
                for (boolean removable : new boolean[]{false, true}) {
                    Mob mob = mob();
                    NativeSpawnedEntity entity = new NativeSpawnedEntity(mob);
                    IrisEntity options = new IrisEntity().setKeepEntity(keep).setRemovable(removable);

                    ModdedEntitySpawner.applyPersistence(options, entity, force);
                    entity.configureMob(options);

                    assertTrue(NativeEntityBehavior.shouldSave(mob, true));
                    if (keep || force || !removable) {
                        verify(mob, atLeastOnce()).setPersistenceRequired();
                    } else {
                        verify(mob, never()).setPersistenceRequired();
                    }
                }
            }
        }
    }

    private static Mob mob() {
        Mob mob = mock(Mob.class);
        EntityType<?> type = mock(EntityType.class);
        Set<String> tags = new HashSet<>();
        when(type.canSerialize()).thenReturn(true);
        doReturn(type).when(mob).getType();
        when(mob.entityTags()).thenReturn(tags);
        when(mob.shouldBeSaved()).thenReturn(true);
        when(mob.removeTag(anyString())).thenAnswer(invocation -> tags.remove(invocation.getArgument(0)));
        when(mob.addTag(anyString())).thenAnswer(invocation -> tags.add(invocation.getArgument(0)));
        return mob;
    }
}
