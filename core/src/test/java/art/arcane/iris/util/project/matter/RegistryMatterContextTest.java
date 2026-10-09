package art.arcane.iris.util.project.matter;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.iris.util.project.matter.slices.SpawnerMatter;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class RegistryMatterContextTest {
    @Test
    public void registrySliceResolvesThroughExplicitDataOnIoWorker() throws Exception {
        IrisSpawner firstResolved = new IrisSpawner();
        IrisSpawner secondResolved = new IrisSpawner();
        IrisData firstData = dataReturning(firstResolved);
        IrisData secondData = dataReturning(secondResolved);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<IrisSpawner> firstRead = executor.submit(() -> readSpawner(firstData));
            Future<IrisSpawner> secondRead = executor.submit(() -> readSpawner(secondData));
            assertSame(firstResolved, firstRead.get());
            assertSame(secondResolved, secondRead.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private IrisSpawner readSpawner(IrisData data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("test-spawner");
        }
        try (IrisMatterContext.Scope scope = IrisMatterContext.open(data);
             DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return new SpawnerMatter().readNode(input);
        }
    }

    private IrisData dataReturning(IrisSpawner resolved) {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serve(data).resource(SpawnResources.SPAWNERS, "test-spawner", resolved);
        return data;
    }
}
