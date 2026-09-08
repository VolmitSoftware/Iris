package art.arcane.iris.core.loader;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceLoaderPrefetchTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;
    private File platformRoot;
    private File packRoot;
    private IrisData manager;
    private Engine engine;

    @Before
    public void setUp() throws Exception {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        previousSettings = IrisSettings.settings;
        IrisPlatforms.unbind();
        platformRoot = temporaryFolder.newFolder("platform");
        packRoot = temporaryFolder.newFolder("pack");
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        when(platform.dataFolder()).thenReturn(platformRoot);
        when(platform.dataFile(any(String[].class))).thenAnswer(invocation -> {
            File file = platformRoot;
            for (Object segment : invocation.getArguments()) {
                file = new File(file, String.valueOf(segment));
            }
            return file;
        });
        IrisPlatforms.bind(platform);
        IrisSettings.settings = new IrisSettings();

        manager = mock(IrisData.class);
        when(manager.getId()).thenReturn(7);
        when(manager.getDataFolder()).thenReturn(packRoot);

        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        dimension.setVersion(9);
        engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(new SeedManager(42L));
        when(engine.getDimension()).thenReturn(dimension);
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void oversizedHistoricalSetIsNotAdmittedOnReopen() throws Exception {
        CountingLoader writer = loader(32);
        KSet<String> history = new KSet<>();
        for (int index = 0; index < 4_096; index++) {
            history.add("resource-" + index);
        }
        writer.setFirstAccess(history);

        writer.saveFirstAccess(engine);
        CountingLoader reader = loader(32);
        reader.loadFirstAccess(engine);

        assertTrue(reader.loadedKeys().isEmpty());
    }

    @Test
    public void liveHistoryStopsGrowingAtTheAdmissionLimit() {
        CountingLoader loader = loader(4_096);

        for (int index = 0; index < 4_096; index++) {
            loader.load("resource-" + index, false);
        }

        assertEquals(1_024, loader.getFirstAccess().size());
        assertTrue(loader.isFirstAccessOverflowed());
    }

    @Test
    public void admittedHistoryLoadsWithoutPerEntryTaskFanout() throws Exception {
        CountingLoader writer = loader(32);
        KSet<String> history = new KSet<>();
        for (int index = 0; index < 32; index++) {
            history.add("resource-" + index);
        }
        writer.setFirstAccess(history);
        writer.saveFirstAccess(engine);

        CountingLoader reader = loader(32);
        String loadingThread = Thread.currentThread().getName();
        reader.loadFirstAccess(engine);

        assertEquals(32, reader.loadedKeys().size());
        assertEquals(Set.of(loadingThread), reader.loadingThreads());
    }

    @Test
    public void dataManagerLoadsHistoriesOnTheCallingThread() throws Exception {
        CountingLoader writer = loader(32);
        writer.setFirstAccess(new KSet<>("resource"));
        writer.saveFirstAccess(engine);
        CountingLoader reader = loader(32);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(TestRegistrant.class, reader);
        IrisData data = mock(IrisData.class, Answers.CALLS_REAL_METHODS);
        data.setLoaders(loaders);
        String loadingThread = Thread.currentThread().getName();

        data.loadPrefetch(engine);

        assertEquals(List.of("resource"), reader.loadedKeys());
        assertEquals(Set.of(loadingThread), reader.loadingThreads());
    }

    @Test
    public void legacyIdentityIsNotRead() throws Exception {
        String identity = "DIM" + Math.abs(42L + 9L + "overworld".hashCode());
        File legacy = new File(platformRoot,
                "prefetch/" + identity + "/" + Math.abs("test-resources".hashCode()) + ".ipfch");
        assertTrue(legacy.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(legacy);
             GZIPOutputStream gzip = new GZIPOutputStream(output);
             DataOutputStream data = new DataOutputStream(gzip)) {
            data.writeInt(1);
            data.writeUTF("legacy-resource");
        }

        CountingLoader reader = loader(32);
        reader.loadFirstAccess(engine);

        assertTrue(reader.loadedKeys().isEmpty());
    }

    @Test
    public void equalDimensionIdentitiesDoNotCrossPackRoots() throws Exception {
        CountingLoader writer = loader(32);
        writer.setFirstAccess(new KSet<>("source-only"));
        writer.saveFirstAccess(engine);
        File otherRoot = temporaryFolder.newFolder("other-pack");
        IrisData otherManager = mock(IrisData.class);
        when(otherManager.getId()).thenReturn(8);
        when(otherManager.getDataFolder()).thenReturn(otherRoot);

        CountingLoader reader = loader(otherRoot, otherManager, 32);
        reader.loadFirstAccess(engine);

        assertTrue(reader.loadedKeys().isEmpty());
    }

    @Test
    public void binaryObjectLoaderDoesNotContributeToJsonPrefetchHistory() {
        ObjectResourceLoader loader = new ObjectResourceLoader(
                packRoot,
                manager,
                "objects",
                "Object",
                ResourceLoader.Options.datapackCompiler());

        IrisObject object = loader.load("missing", false);

        assertNull(object);
        assertTrue(loader.getFirstAccess().isEmpty());
    }

    private CountingLoader loader(int cacheSize) {
        return loader(packRoot, manager, cacheSize);
    }

    private CountingLoader loader(File root, IrisData data, int cacheSize) {
        return new CountingLoader(
                root,
                data,
                new ResourceLoader.Options(cacheSize, false, true));
    }

    private static final class CountingLoader extends ResourceLoader<TestRegistrant> {
        private final List<String> loaded = Collections.synchronizedList(new ArrayList<>());
        private final Set<String> threads = ConcurrentHashMap.newKeySet();

        private CountingLoader(File root, IrisData manager, Options options) {
            super(root, manager, "test-resources", "Test Resource", TestRegistrant.class, options);
        }

        @Override
        public TestRegistrant load(String name) {
            loaded.add(name);
            threads.add(Thread.currentThread().getName());
            return new TestRegistrant();
        }

        private List<String> loadedKeys() {
            return List.copyOf(loaded);
        }

        private Set<String> loadingThreads() {
            return Set.copyOf(threads);
        }
    }

    public static final class TestRegistrant extends IrisRegistrant {
        @Override
        public String getFolderName() {
            return "test-resources";
        }

        @Override
        public String getTypeName() {
            return "Test Resource";
        }
    }
}
