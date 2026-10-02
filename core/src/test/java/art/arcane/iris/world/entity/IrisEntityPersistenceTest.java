package art.arcane.iris.world.entity;

import art.arcane.iris.configuration.IrisSettings;
import com.google.gson.Gson;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class IrisEntityPersistenceTest {
    @Test
    public void defaultAmbientMobsSaveAndAllowDistanceDespawn() {
        IrisEntity options = new IrisEntity();
        LivingEntity entity = mock(LivingEntity.class);

        assertTrue(options.isRemovable());
        assertFalse(new IrisSettings.IrisSettingsWorld().isForcePersistEntities());
        options.applyPersistence(entity, false);

        verify(entity).setPersistent(true);
        verify(entity).setRemoveWhenFarAway(true);
    }

    @Test
    public void explicitRetentionOverridesDistanceRemoval() {
        for (boolean keep : new boolean[]{false, true}) {
            for (boolean force : new boolean[]{false, true}) {
                for (boolean removable : new boolean[]{false, true}) {
                    IrisEntity options = new IrisEntity().setKeepEntity(keep).setRemovable(removable);
                    LivingEntity entity = mock(LivingEntity.class);

                    options.applyPersistence(entity, force);

                    verify(entity).setPersistent(true);
                    verify(entity).setRemoveWhenFarAway(removable && !keep && !force);
                }
            }
        }
    }

    @Test
    public void nonMobEntitiesStillSaveNormally() {
        Entity entity = mock(Entity.class);

        new IrisEntity().applyPersistence(entity, false);

        verify(entity).setPersistent(true);
    }

    @Test
    public void authoredRetentionChoicesSurviveChangedDefaults() {
        Gson gson = new Gson();
        IrisEntity retained = gson.fromJson("{\"removable\":false,\"keepEntity\":true}", IrisEntity.class);
        IrisSettings.IrisSettingsWorld settings = gson.fromJson("{\"forcePersistEntities\":true}", IrisSettings.IrisSettingsWorld.class);
        assertFalse(retained.isRemovable());
        assertTrue(retained.isKeepEntity());
        assertTrue(settings.isForcePersistEntities());
    }
}
