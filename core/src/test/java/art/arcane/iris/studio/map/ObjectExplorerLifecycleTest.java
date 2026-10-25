package art.arcane.iris.studio.map;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectExplorerLifecycleTest {
    private final List<ObjectExplorer> explorers = new ArrayList<>();
    private final TextureFuture textureFuture = new TextureFuture();
    private Object previousTextures;

    @Before
    public void installTextures() throws Exception {
        Field shared = field(BlockTextures.class, "shared");
        previousTextures = shared.get(null);
        shared.set(null, textureFuture);
    }

    @After
    public void closeExplorers() throws Exception {
        SwingUtilities.invokeAndWait(() -> explorers.forEach(ObjectExplorer::close));
        textureFuture.completeExceptionally(new IllegalStateException("Test texture download ended"));
        for (ObjectExplorer explorer : explorers) {
            executor(explorer, "loader").awaitTermination(3, TimeUnit.SECONDS);
            executor(explorer, "thumbnailPainter").awaitTermination(3, TimeUnit.SECONDS);
        }
        SwingUtilities.invokeAndWait(() -> { });
        field(BlockTextures.class, "shared").set(null, previousTextures);
    }

    @Test
    public void closingWhileTexturesArePendingReleasesTheLoaderWithoutCancellingSharedTextures() throws Exception {
        Catalog catalog = new Catalog();
        ObjectExplorer explorer = open(catalog);
        assertTrue(textureFuture.started.await(3, TimeUnit.SECONDS));

        SwingUtilities.invokeAndWait(explorer::close);

        assertTrue("Texture waiting retained the closed preview's loader", executor(explorer, "loader").awaitTermination(3, TimeUnit.SECONDS));
        assertFalse(textureFuture.isDone());
        assertEquals(0, catalog.reads.get());
    }

    @Test
    public void textureSuccessAfterClosureDoesNotPublishOrRestartLoading() throws Exception {
        Catalog catalog = new Catalog();
        ObjectExplorer explorer = open(catalog);
        assertTrue(textureFuture.started.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(explorer::close);

        textureFuture.complete(textures());
        assertTrue(executor(explorer, "loader").awaitTermination(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(0, catalog.reads.get());
        assertNull(field(ObjectExplorer.class, "textures").get(explorer));
        assertStopped(explorer);
    }

    @Test
    public void textureFailureAfterClosureDoesNotPublishAnError() throws Exception {
        ObjectExplorer explorer = open(new Catalog());
        assertTrue(textureFuture.started.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(explorer::close);

        textureFuture.completeExceptionally(new IllegalStateException("Texture download failed"));
        assertTrue(executor(explorer, "loader").awaitTermination(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });

        JLabel summary = (JLabel) field(StudioStatusBar.class, "summary").get(explorer.statusBar());
        assertEquals("", summary.getText());
        assertStopped(explorer);
    }

    @Test
    public void repeatedClosureAndLateOpenDoNotSubmitWorkOrRestartTimers() throws Exception {
        ObjectExplorer explorer = open(new Catalog());
        assertTrue(textureFuture.started.await(3, TimeUnit.SECONDS));

        SwingUtilities.invokeAndWait(() -> {
            explorer.close();
            explorer.close();
            explorer.open();
        });

        assertTrue(executor(explorer, "loader").awaitTermination(3, TimeUnit.SECONDS));
        assertStopped(explorer);
    }

    @Test
    public void anotherPreviewStillReceivesTheSharedTexturesAfterOneCloses() throws Exception {
        Catalog firstCatalog = new Catalog();
        Catalog secondCatalog = new Catalog();
        ObjectExplorer first = open(firstCatalog);
        ObjectExplorer second = open(secondCatalog);
        assertTrue(textureFuture.bothStarted.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(first::close);
        assertFalse(textureFuture.isCancelled());

        textureFuture.complete(textures());
        assertTrue(secondCatalog.read.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(0, firstCatalog.reads.get());
        assertEquals(1, secondCatalog.reads.get());
        assertTrue(executor(first, "loader").awaitTermination(3, TimeUnit.SECONDS));
        assertFalse(executor(second, "loader").isShutdown());
    }

    @Test
    public void closingStopsVariantGalleryAnimation() throws Exception {
        ObjectExplorer explorer = open(new Catalog());
        ObjectVariantGallery gallery = (ObjectVariantGallery) field(ObjectExplorer.class, "gallery").get(explorer);
        SwingUtilities.invokeAndWait(() -> {
            gallery.showVariants("Variants", 2, 0);
            explorer.close();
        });

        assertFalse(((Timer) field(ObjectVariantGallery.class, "loadingTicker").get(gallery)).isRunning());
    }

    private ObjectExplorer open(Catalog catalog) throws Exception {
        Engine engine = mock(Engine.class);
        SwingUtilities.invokeAndWait(() -> {
            ObjectExplorer explorer = new ObjectExplorer(engine, null, catalog);
            explorers.add(explorer);
            explorer.open();
        });
        return explorers.getLast();
    }

    private static BlockTextures textures() {
        BlockTextures textures = mock(BlockTextures.class);
        MinecraftBiomeColors colors = mock(MinecraftBiomeColors.class);
        when(textures.biomeColors()).thenReturn(colors);
        return textures;
    }

    private static void assertStopped(ObjectExplorer explorer) throws Exception {
        OrbitViewport viewport = (OrbitViewport) field(ObjectExplorer.class, "viewport").get(explorer);
        assertFalse(((Timer) field(OrbitViewport.class, "loadingTicker").get(viewport)).isRunning());
        assertFalse(((Timer) field(OrbitViewport.class, "loadingCloser").get(viewport)).isRunning());
        assertTrue(executor(viewport, "renderThread").isShutdown());
    }

    private static ExecutorService executor(Object owner, String name) throws Exception {
        return (ExecutorService) field(owner.getClass(), name).get(owner);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class TextureFuture extends CompletableFuture<BlockTextures> {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch bothStarted = new CountDownLatch(2);

        @Override
        public BlockTextures get() throws InterruptedException, ExecutionException {
            started.countDown();
            bothStarted.countDown();
            return super.get();
        }

        @Override
        public BlockTextures join() {
            started.countDown();
            bothStarted.countDown();
            return super.join();
        }
    }

    private static final class Catalog implements ObjectSource {
        private final AtomicInteger reads = new AtomicInteger();
        private final CountDownLatch read = new CountDownLatch(1);

        @Override
        public String noun() {
            return "object";
        }

        @Override
        public EmptyState emptyState() {
            return null;
        }

        @Override
        public List<String> keys(IrisData data) {
            reads.incrementAndGet();
            read.countDown();
            return List.of();
        }

        @Override
        public List<IrisObject> variants(IrisData data, String key) {
            return List.of();
        }
    }
}
