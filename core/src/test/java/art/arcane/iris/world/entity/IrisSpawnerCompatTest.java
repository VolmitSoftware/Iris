package art.arcane.iris.world.entity;

import art.arcane.iris.testsupport.PackCompatFixtures;

import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Map;

import static art.arcane.iris.testsupport.PackCompatFixtures.MISSING_ENTITY;
import static art.arcane.iris.testsupport.PackCompatFixtures.entity;
import static art.arcane.iris.testsupport.PackCompatFixtures.excludeEntity;
import static art.arcane.iris.testsupport.PackCompatFixtures.find;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisSpawnerCompatTest {
    @SuppressWarnings("unchecked")
    private static IrisData dataWith(PackCompatReport report, IrisEntity... entities) {
        IrisData data = PackCompatFixtures.data(report);
        ResourceLoader<IrisEntity> entityLoader = mock(ResourceLoader.class);
        when(data.getEntityLoader()).thenReturn(entityLoader);
        for (IrisEntity entity : entities) {
            when(entityLoader.load(entity.getLoadKey())).thenReturn(entity);
        }
        return data;
    }

    private static ContentGate gate(PackCompatReport report) {
        return new ContentGate(null, Map.of(), report);
    }

    @Test
    public void evaluateCompatDropsSpawnsWhoseEntityIsExcluded() {
        PackCompatReport report = new PackCompatReport();
        IrisEntity zombie = entity("zombie");
        IrisEntity camel = excludeEntity(entity("camel"));
        IrisData data = dataWith(report, zombie, camel);
        IrisSpawner spawner = new IrisSpawner();
        spawner.setLoadKey("desert");
        spawner.setLoader(data);
        spawner.setSpawns(new KList<>(
                new IrisEntitySpawn().setEntity("zombie"),
                new IrisEntitySpawn().setEntity("camel")));

        CompatStatus status = spawner.evaluateCompat(gate(report));

        assertFalse(status.excluded());
        assertEquals(1, spawner.getSpawns().size());
        assertEquals("zombie", spawner.getSpawns().get(0).getEntity());
        CompatFinding dropped = find(report, CompatAction.DROPPED, "spawner", "desert");
        assertNotNull(dropped);
        assertEquals(CompatRegistry.ENTITY, dropped.registry());
        assertEquals(MISSING_ENTITY, dropped.key());
        assertEquals("spawns[1] camel", dropped.detail());
    }

    @Test
    public void evaluateCompatExcludesSpawnerWithNoSpawnsLeft() {
        PackCompatReport report = new PackCompatReport();
        IrisEntity camel = excludeEntity(entity("camel"));
        IrisData data = dataWith(report, camel);
        IrisSpawner spawner = new IrisSpawner();
        spawner.setLoadKey("desert");
        spawner.setLoader(data);
        spawner.setSpawns(new KList<>(new IrisEntitySpawn().setEntity("camel")));
        spawner.setInitialSpawns(new KList<>(new IrisEntitySpawn().setEntity("camel")));

        CompatStatus status = spawner.evaluateCompat(gate(report));

        assertTrue(status.excluded());
        assertTrue(spawner.getSpawns().isEmpty());
        assertTrue(spawner.getInitialSpawns().isEmpty());
        CompatFinding cascade = find(report, CompatAction.EXCLUDED, "spawner", "desert");
        assertNotNull(cascade);
        assertEquals(MISSING_ENTITY, cascade.key());
        assertEquals("no entity spawns remain", cascade.detail());
    }

    @Test
    public void evaluateCompatKeepsSpawnerThatNeverDeclaredSpawns() {
        PackCompatReport report = new PackCompatReport();
        IrisData data = dataWith(report);
        IrisSpawner spawner = new IrisSpawner();
        spawner.setLoadKey("empty");
        spawner.setLoader(data);

        assertFalse(spawner.evaluateCompat(gate(report)).excluded());
        assertTrue(report.isEmpty());
    }

    @Test
    public void entitySpawnResolvesNoEntityWhenExcluded() {
        PackCompatReport report = new PackCompatReport();
        IrisEntity camel = excludeEntity(entity("camel"));
        IrisData data = dataWith(report, camel);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);

        assertNull(new IrisEntitySpawn().setEntity("camel").getRealEntity(engine));
    }

    @Test
    public void entitySpawnResolvesPresentEntity() {
        PackCompatReport report = new PackCompatReport();
        IrisEntity zombie = entity("zombie");
        IrisData data = dataWith(report, zombie);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);

        assertSame(zombie, new IrisEntitySpawn().setEntity("zombie").getRealEntity(engine));
    }
}
