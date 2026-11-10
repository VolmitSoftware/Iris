package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.PackModuleSet;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import com.volmit.ModuleLog;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.LoadedPack;
import com.volmit.service.Providers;
import com.volmit.service.Service;
import com.volmit.tooling.world.ToolWorlds;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisToolPackTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private File packsFolder;
    private File packFolder;
    private IrisData data;
    private IrisToolPack pack;

    @Before
    public void aPackInsideAWorkspace() throws Exception {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        packsFolder = temporaryFolder.newFolder("packs");
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.packsFolder()).thenReturn(packsFolder);
        IrisPlatforms.bind(platform);
        packFolder = new File(packsFolder, "overworld");
        assertTrue(packFolder.mkdirs());
        data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(packFolder);
        pack = new IrisToolPack(data, mock(ModuleLog.class));
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void anEditChangesFilesBelowThePackRootAndReloadsTheResourcesOnce() throws Exception {
        Path seenRoot = pack.edit(root -> {
            java.nio.file.Files.writeString(root.resolve("note.txt"), "x");
            java.nio.file.Files.writeString(root.resolve("second.txt"), "y");
            return root;
        });

        assertEquals(packFolder.toPath(), seenRoot);
        assertTrue(new File(packFolder, "second.txt").isFile());
        verify(data, times(1)).invalidateStructureResources();
    }

    @Test
    public void anEditThatThrowsLeavesNoReloadBehind() {
        IOException failure = new IOException("disk full");

        IOException thrown = assertThrows(IOException.class, () -> pack.edit(root -> {
            throw failure;
        }));

        assertSame(failure, thrown);
        verify(data, never()).invalidateStructureResources();
    }

    @Test
    public void oneEditRunsAtATimeAndASecondWaitsForTheFirst() throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch firstInside = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicBoolean secondEntered = new AtomicBoolean();
            Future<?> first = threads.submit(() -> pack.edit(root -> {
                firstInside.countDown();
                awaitRelease(releaseFirst);
                return null;
            }));
            assertTrue(firstInside.await(10, TimeUnit.SECONDS));
            Future<?> second = threads.submit(() -> pack.edit(root -> {
                secondEntered.set(true);
                return null;
            }));

            Thread.sleep(200);
            assertFalse(secondEntered.get());
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertTrue(secondEntered.get());
            verify(data, times(2)).invalidateStructureResources();
        } finally {
            threads.shutdownNow();
        }
    }

    private static void awaitRelease(CountDownLatch release) throws IOException {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            throw new InterruptedIOException("Interrupted while the edit was held");
        }
    }

    @Test
    public void theOutputFolderIsTheWorkspaceExportsFolderOfThePack() {
        Path output = pack.outputFolder();

        assertEquals(new File(new File(packsFolder, "exports"), "overworld").toPath().toAbsolutePath().normalize(), output);
        assertTrue(output.toFile().isDirectory());
        assertFalse(output.startsWith(packFolder.toPath()));
    }

    @Test
    public void aPlatformWithoutToolWorldsAnswersThatTheyAreNotSupported() {
        IrisServices.remove(ToolWorldsHost.class);

        ToolWorlds worlds = pack.worlds();

        assertSame(UnsupportedToolWorlds.INSTANCE, worlds);
        assertTrue(worlds.find("any").isEmpty());
    }

    @Test
    public void aPlatformWithToolWorldsAnswersWithThoseOfThePacksFolder() {
        ToolWorlds overworldWorlds = mock(ToolWorlds.class);
        IrisServices.register(ToolWorldsHost.class, (ToolWorldsHost) packName -> "overworld".equals(packName) ? overworldWorlds : null);
        try {
            assertSame(overworldWorlds, pack.worlds());
        } finally {
            IrisServices.remove(ToolWorldsHost.class);
        }
    }

    @Test
    public void servicesAreTheProvidersOfThePacksModules() {
        Probe probe = new Probe() { };
        FeatureCatalog catalog = mock(FeatureCatalog.class);
        LoadedPack loaded = mock(LoadedPack.class);
        PackModuleSet modules = mock(PackModuleSet.class);
        when(catalog.services(Probe.class)).thenReturn(List.of(probe));
        when(loaded.catalog()).thenReturn(catalog);
        when(modules.pack()).thenReturn(loaded);
        when(data.getModules()).thenReturn(modules);

        assertEquals(List.of(probe), pack.services(Probe.class));
    }

    @Service(Providers.ALL)
    interface Probe {
    }

    @Test
    public void theObjectCodecReadsAndWritesIobFiles() {
        assertEquals(".iob", pack.objects().extension());
        assertThrows(IOException.class, () -> pack.objects().decode(new byte[] {1, 2, 3}));
    }
}
