package art.arcane.iris.core.project;

import art.arcane.iris.engine.object.entity.IrisEntitySpawn;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.volmlib.util.collection.KSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisProjectEntityDependencyTest {
    @Test
    public void collectsInitialOnlyEntityAlongsideNormalSpawnEntityForExport() {
        IrisSpawner spawner = new IrisSpawner();
        spawner.getSpawns().add(new IrisEntitySpawn().setEntity("standard/passive/cow"));
        spawner.getInitialSpawns().add(new IrisEntitySpawn().setEntity("beta/sentinel"));
        KSet<IrisSpawner> spawners = new KSet<>();
        spawners.add(spawner);

        KSet<String> entityKeys = IrisPackageCompiler.collectSpawnerEntityKeys(spawners);

        assertEquals(2, entityKeys.size());
        assertTrue(entityKeys.contains("standard/passive/cow"));
        assertTrue(entityKeys.contains("beta/sentinel"));
    }

    @Test
    public void returnsNoEntityDependenciesForEmptySpawnerSet() {
        KSet<String> entityKeys = IrisPackageCompiler.collectSpawnerEntityKeys(new KSet<>());

        assertTrue(entityKeys.isEmpty());
    }
}
