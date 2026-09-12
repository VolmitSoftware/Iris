package art.arcane.iris.world.storage.matter;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.volmlib.util.collection.KMap;
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
import static org.mockito.Mockito.when;

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

    @SuppressWarnings("unchecked")
    private IrisData dataReturning(IrisSpawner resolved) {
        ResourceLoader<IrisSpawner> loader = mock(ResourceLoader.class);
        when(loader.load("test-spawner")).thenReturn(resolved);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisSpawner.class, loader);
        IrisData data = mock(IrisData.class);
        when(data.getLoaders()).thenReturn(loaders);
        return data;
    }
}
