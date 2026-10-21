/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.gui;

import com.volmit.iris.core.gui.BlockTextures.BlockTexture;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisObject;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The map window's Objects and Procedural views: everything its source offers in a folder tree, the chosen one in 3D,
 * and what it is made of. A procedural object's variants can also be compared side by side, baked from any seed.
 * Inspired by the Procedural Objects workbench.
 */
final class ObjectExplorer {
    private static final NamespacedKey TINT_BIOME = NamespacedKey.minecraft("plains");
    private static final int UNKNOWN_BLOCK_COLOR = 0x8A8A8A;
    private static final String CAMERA_HINT = "Drag to orbit · Shift-drag to pan · Scroll to zoom · Page Up/Down cuts layers · R resets";
    private static final String ONE_VARIANT_CARD = "one variant";
    private static final String ALL_VARIANTS_CARD = "all variants";

    private final Engine startEngine;
    private final UUID worldId;
    private final ObjectSource source;
    private final ObjectBrowser browser;
    private final ObjectViewport viewport = new ObjectViewport();
    private final ObjectLayerCut layerCut = new ObjectLayerCut(shownLayers -> showLayerCut());
    private final ObjectVariantPicker variantPicker = new ObjectVariantPicker(this::showVariant, this::showSeed, this::toggleComparing);
    private final ObjectVariantGallery gallery = new ObjectVariantGallery(this::openVariant, this::redrawThumbnails);
    private final CardLayout viewCards = new CardLayout();
    private final JPanel views = new JPanel(viewCards);
    private final JComponent viewWithOverlays;
    private final ObjectOverviewPanel overview = new ObjectOverviewPanel(this::showSeeThrough);
    private final StudioStatusBar statusBar = new StudioStatusBar();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris Object Loader");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final ExecutorService thumbnailPainter = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris Variant Thumbnails");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final AtomicLong objectRevision = new AtomicLong();
    private final AtomicLong variantListRevision = new AtomicLong();
    private final AtomicLong meshRevision = new AtomicLong();
    private final Map<String, BlockModel> models = new ConcurrentHashMap<>();
    private final Runnable hotloadHook = this::reloadPack;
    private volatile BlockTextures textures;
    private volatile BiomeTintColors tintColors;
    private String shownKey;
    private List<IrisObject> shownVariants = List.of();
    private int shownVariantIndex;
    private OptionalLong packSeed = OptionalLong.empty();
    private OptionalLong shownSeed = OptionalLong.empty();
    private boolean comparingVariants;
    private long thumbnailsDrawnForRevision = -1;
    private LoadedObject shown;
    private ObjectMesh wholeMesh;
    private Set<String> seeThroughTypes = Set.of();

    ObjectExplorer(Engine engine, UUID worldId, ObjectSource source) {
        this.startEngine = engine;
        this.worldId = worldId;
        this.source = source;
        this.browser = new ObjectBrowser(source.noun(), this::showObject);
        views.add(layerCut.floatingOver(viewport), ONE_VARIANT_CARD);
        views.add(gallery, ALL_VARIANTS_CARD);
        this.viewWithOverlays = variantPicker.floatingOver(views);
        GuiHost.interruptOnPluginShutdown(loader);
        GuiHost.interruptOnPluginShutdown(thumbnailPainter);
        GuiHost.registerHotloadHook(hotloadHook);
        statusBar.showPosition(CAMERA_HINT);
        viewport.bindKey(KeyEvent.VK_PAGE_UP, "show-layer-above", () -> layerCut.stepBy(1));
        viewport.bindKey(KeyEvent.VK_PAGE_DOWN, "hide-top-layer", () -> layerCut.stepBy(-1));
        viewport.bindKey(KeyEvent.VK_OPEN_BRACKET, "previous-variant", () -> variantPicker.stepBy(-1));
        viewport.bindKey(KeyEvent.VK_CLOSE_BRACKET, "next-variant", () -> variantPicker.stepBy(1));
        viewport.bindKey(KeyEvent.VK_G, "compare-variants", this::toggleComparing);
        gallery.bindKey(KeyEvent.VK_OPEN_BRACKET, "previous-variant", () -> variantPicker.stepBy(-1));
        gallery.bindKey(KeyEvent.VK_CLOSE_BRACKET, "next-variant", () -> variantPicker.stepBy(1));
        gallery.bindKey(KeyEvent.VK_G, "show-one-variant", this::toggleComparing);
        gallery.bindKey(KeyEvent.VK_ESCAPE, "close-comparison", this::toggleComparing);
    }

    JComponent browser() {
        return browser;
    }

    /**
     * The 3D view with the slider that cuts away its top layers, or every variant side by side, under the bar that
     * steps through variants and seeds.
     */
    JComponent view() {
        return viewWithOverlays;
    }

    void setCoveredLeftWidth(int width) {
        viewport.setCoveredLeftWidth(width);
        gallery.setCoveredLeftWidth(width);
        variantPicker.setCoveredLeftWidth(width);
    }

    JComponent overview() {
        return overview;
    }

    StudioStatusBar statusBar() {
        return statusBar;
    }

    void resetCamera() {
        viewport.resetCamera();
    }

    // Textures load first, since the first object would otherwise be drawn in flat colours.
    void open() {
        statusBar.showActivity("Loading Minecraft textures...");
        loader.execute(() -> {
            loadTextures();
            List<String> keys = objectKeys();
            EventQueue.invokeLater(() -> showCatalog(keys));
        });
    }

    void close() {
        GuiHost.unregisterHotloadHook(hotloadHook);
        objectRevision.incrementAndGet();
        loader.shutdownNow();
        thumbnailPainter.shutdownNow();
        viewport.close();
    }

    private void loadTextures() {
        try {
            BlockTextures loaded = BlockTextures.shared().join();
            tintColors = loaded.biomeColors().vanilla(TINT_BIOME);
            textures = loaded;
        } catch (RuntimeException unavailable) {
            EventQueue.invokeLater(() -> statusBar.showError("Minecraft textures are unavailable, objects use plain colours"));
        }
    }

    private List<String> objectKeys() {
        return source.keys(engine().getData());
    }

    private void showCatalog(List<String> keys) {
        statusBar.showActivity("");
        browser.showObjects(keys);
        String key = shownKey != null && keys.contains(shownKey) ? shownKey : browser.firstObject();
        if (key == null) {
            viewport.showMessage("This pack has no " + source.noun() + "s");
            overview.clear();
            layerCut.showHeight(1);
            if (comparingVariants) {
                toggleComparing();
            }
            showVariantList(List.of(), OptionalLong.empty(), OptionalLong.empty(), 0);
            return;
        }
        browser.select(key);
        showObject(key);
    }

    /**
     * Shows the first variant of the entry, baked from the pack's seed. The pack reloading the shown entry keeps the
     * variant and the seed being looked at, unless the pack itself now sets another seed.
     */
    private void showObject(String key) {
        boolean reloadingShownObject = key.equals(shownKey);
        OptionalLong previousPackSeed = packSeed;
        OptionalLong exploredSeed = shownSeed;
        int variantIndex = reloadingShownObject ? shownVariantIndex : 0;
        shownKey = key;
        if (!reloadingShownObject) {
            variantPicker.forgetTriedSeeds();
            showVariantList(List.of(), OptionalLong.empty(), OptionalLong.empty(), 0);
        }
        long revision = startLoading(key);
        loader.execute(() -> {
            if (revision != objectRevision.get()) {
                return;
            }
            OptionalLong pack = source.seed(key);
            OptionalLong seed = reloadingShownObject && pack.equals(previousPackSeed) ? exploredSeed : pack;
            loadVariants(revision, key, pack, seed, variantIndex);
        });
    }

    // The seed shows at once, so stepping again continues from it even while this one bakes or fails.
    private void showSeed(long seed) {
        String key = shownKey;
        OptionalLong pack = packSeed;
        int variantIndex = shownVariantIndex;
        shownSeed = OptionalLong.of(seed);
        variantPicker.showSeed(shownSeed, pack);
        long revision = startLoading(key + " with seed " + seed);
        loader.execute(() -> {
            if (revision == objectRevision.get()) {
                loadVariants(revision, key, pack, OptionalLong.of(seed), variantIndex);
            }
        });
    }

    // On the loader thread.
    private void loadVariants(long revision, String key, OptionalLong pack, OptionalLong seed, int preferredIndex) {
        try {
            IrisData data = engine().getData();
            List<IrisObject> variants = seed.isPresent() ? source.variants(data, key, seed.getAsLong()) : source.variants(data, key);
            if (variants.isEmpty()) {
                throw new IllegalStateException("Nothing could be loaded for " + key);
            }
            int index = preferredIndex < variants.size() ? preferredIndex : 0;
            LoadedObject loaded = load(variants.get(index));
            EventQueue.invokeLater(() -> {
                if (revision != objectRevision.get()) {
                    return;
                }
                showVariantList(variants, pack, seed, index);
                showLoaded(revision, loaded);
            });
        } catch (RuntimeException error) {
            EventQueue.invokeLater(() -> showFailure(revision, key, error));
        }
    }

    private void showVariantList(List<IrisObject> variants, OptionalLong pack, OptionalLong seed, int index) {
        shownVariants = variants;
        shownVariantIndex = index;
        packSeed = pack;
        shownSeed = seed;
        variantListRevision.incrementAndGet();
        variantPicker.showVariants(variants.size(), index);
        variantPicker.showSeed(seed, pack);
        if (!comparingVariants) {
            return;
        }
        if (variants.isEmpty()) {
            gallery.showMessage("Loading " + shownKey + "...");
        } else if (variants.size() == 1) {
            toggleComparing();
        } else {
            drawThumbnails();
        }
    }

    private void showVariant(int index) {
        if (index >= shownVariants.size()) {
            return;
        }
        shownVariantIndex = index;
        gallery.select(index);
        IrisObject variant = shownVariants.get(index);
        long revision = startLoading(variant.getLoadKey());
        loader.execute(() -> {
            if (revision != objectRevision.get()) {
                return;
            }
            LoadedObject loaded = load(variant);
            EventQueue.invokeLater(() -> showLoaded(revision, loaded));
        });
    }

    private void openVariant(int index) {
        variantPicker.showVariants(shownVariants.size(), index);
        showVariant(index);
        toggleComparing();
    }

    private void toggleComparing() {
        if (!comparingVariants && shownVariants.size() < 2) {
            return;
        }
        comparingVariants = !comparingVariants;
        variantPicker.showComparing(comparingVariants);
        viewCards.show(views, comparingVariants ? ALL_VARIANTS_CARD : ONE_VARIANT_CARD);
        if (comparingVariants) {
            drawThumbnails();
            gallery.requestFocusInWindow();
        } else {
            viewport.requestFocusInWindow();
        }
    }

    private void redrawThumbnails() {
        thumbnailsDrawnForRevision = -1;
        if (comparingVariants) {
            drawThumbnails();
        }
    }

    /**
     * Fills the side-by-side view with every shown variant, all drawn at the scale that fits the largest, so their
     * sizes compare. Thumbnails arrive one at a time on their own thread, leaving the loader free for the 3D view.
     */
    private void drawThumbnails() {
        long revision = variantListRevision.get();
        if (revision == thumbnailsDrawnForRevision) {
            return;
        }
        thumbnailsDrawnForRevision = revision;
        List<IrisObject> variants = shownVariants;
        gallery.showVariants(galleryHeading(), variants.size(), shownVariantIndex);
        int size = gallery.thumbnailSize();
        thumbnailPainter.execute(() -> {
            List<ObjectMesh> meshes = new ArrayList<>();
            for (IrisObject variant : variants) {
                if (revision != variantListRevision.get()) {
                    return;
                }
                meshes.add(ObjectMesh.build(visibleBlocks(variant), this::modelOf));
            }
            ObjectMesh.Bounds sizeToFit = largestSize(meshes);
            for (int index = 0; index < meshes.size() && revision == variantListRevision.get(); index++) {
                BufferedImage thumbnail = ObjectRenderer.renderThumbnail(meshes.get(index), sizeToFit, size, size);
                int shownIndex = index;
                EventQueue.invokeLater(() -> {
                    if (revision == variantListRevision.get()) {
                        gallery.showThumbnail(shownIndex, thumbnail);
                    }
                });
            }
        });
    }

    private String galleryHeading() {
        return String.format(Locale.ROOT, "%s · %d variants%s", shownKey, shownVariants.size(), seedNote());
    }

    private static ObjectMesh.Bounds largestSize(List<ObjectMesh> meshes) {
        int sizeX = 1;
        int sizeY = 1;
        int sizeZ = 1;
        for (ObjectMesh mesh : meshes) {
            sizeX = Math.max(sizeX, mesh.bounds().sizeX());
            sizeY = Math.max(sizeY, mesh.bounds().sizeY());
            sizeZ = Math.max(sizeZ, mesh.bounds().sizeZ());
        }
        return new ObjectMesh.Bounds(0, 0, 0, sizeX, sizeY, sizeZ);
    }

    private long startLoading(String key) {
        shown = null;
        wholeMesh = null;
        meshRevision.incrementAndGet();
        viewport.showMessage("Loading " + key + "...");
        statusBar.showActivity("Loading " + key + "...");
        return objectRevision.incrementAndGet();
    }

    private LoadedObject load(IrisObject object) {
        Map<BlockVector, BlockData> blocks = visibleBlocks(object);
        ObjectMesh mesh = ObjectMesh.build(blocks, this::modelOf);
        ObjectSummary summary = ObjectSummary.of(object.getLoadKey(), mesh.bounds(), blocks.values(), this::iconOf);
        return new LoadedObject(blocks, mesh, summary);
    }

    private static Map<BlockVector, BlockData> visibleBlocks(IrisObject object) {
        Map<BlockVector, BlockData> blocks = new HashMap<>();
        object.getBlocks().forEach((position, block) -> {
            if (!block.getMaterial().isAir()) {
                blocks.put(position, block);
            }
        });
        return blocks;
    }


    private void showLoaded(long revision, LoadedObject loaded) {
        if (revision != objectRevision.get()) {
            return;
        }
        shown = loaded;
        wholeMesh = loaded.mesh();
        seeThroughTypes = Set.of();
        statusBar.showActivity("");
        viewport.show(loaded.mesh());
        overview.show(loaded.summary());
        layerCut.showHeight(loaded.mesh().bounds().sizeY());
        showMeshSummary(loaded.mesh());
    }

    private void showMeshSummary(ObjectMesh mesh) {
        ObjectSummary summary = shown.summary();
        String cut = layerCut.isCutting()
                ? String.format(Locale.ROOT, " · showing layers 1-%d of %d", layerCut.shownLayerCount(), layerCut.layerCount())
                : "";
        statusBar.showSummary(String.format(Locale.ROOT, "%s%s · %,d blocks · %,d faces drawn%s",
                summary.key(), seedNote(), summary.blockCount(), mesh.faceCount(), cut));
    }

    // A seed being tried is not in the pack until its "seed" field says so.
    private String seedNote() {
        if (shownSeed.isEmpty()) {
            return "";
        }
        String note = " · seed " + shownSeed.getAsLong();
        return shownSeed.equals(packSeed) ? note : note + " (the pack has " + packSeed.getAsLong() + ")";
    }

    private void showSeeThrough(Set<String> blockTypes) {
        seeThroughTypes = blockTypes;
        rebuildWholeMesh();
    }

    // Blocks behind see-through ones get their hidden faces back, so the mesh is built again.
    private void rebuildWholeMesh() {
        LoadedObject object = shown;
        if (object == null) {
            return;
        }
        long revision = meshRevision.incrementAndGet();
        Set<String> seeThrough = seeThroughTypes;
        loader.execute(() -> {
            if (revision != meshRevision.get()) {
                return;
            }
            ObjectMesh mesh = seeThrough.isEmpty()
                    ? object.mesh()
                    : ObjectMesh.build(object.blocks(), this::modelOf, object.mesh().bounds(),
                    block -> seeThrough.contains(block.getMaterial().getKey().getKey()));
            EventQueue.invokeLater(() -> {
                if (revision == meshRevision.get() && shown == object) {
                    wholeMesh = mesh;
                    showLayerCut();
                }
            });
        });
    }

    private void showLayerCut() {
        if (wholeMesh == null) {
            return;
        }
        int highestShownY = wholeMesh.bounds().minY() + layerCut.shownLayerCount() - 1;
        ObjectMesh mesh = layerCut.isCutting() ? wholeMesh.cutAbove(highestShownY) : wholeMesh;
        viewport.replaceMesh(mesh);
        showMeshSummary(mesh);
    }

    private void showFailure(long revision, String key, Throwable error) {
        if (revision != objectRevision.get()) {
            return;
        }
        statusBar.showActivity("");
        viewport.showMessage("Could not load " + key);
        gallery.showMessage("Could not load " + key);
        thumbnailsDrawnForRevision = -1;
        overview.clear();
        layerCut.showHeight(1);
        statusBar.showError(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    // The pack's files may have changed, so the list and the shown object are read again.
    private void reloadPack() {
        loader.execute(() -> {
            List<String> keys = objectKeys();
            EventQueue.invokeLater(() -> showCatalog(keys));
        });
    }

    private Engine engine() {
        Engine current = GuiHost.findEngine(worldId);
        return current != null && !current.isClosed() ? current : startEngine;
    }

    private BlockModel modelOf(BlockData block) {
        return models.computeIfAbsent(block.getAsString(false), state -> {
            BlockTextures available = textures;
            if (available == null) {
                return BlockModel.solidColor(UNKNOWN_BLOCK_COLOR);
            }
            return available.model(block).map(this::withBiomeTint).orElseGet(() -> BlockModel.solidColor(UNKNOWN_BLOCK_COLOR));
        });
    }

    private BufferedImage iconOf(BlockData block) {
        BlockTextures available = textures;
        if (available == null) {
            return null;
        }
        return available.iconTexture(block).map(this::withBiomeTint).map(BlockTexture::toImage).orElse(null);
    }

    // Objects have no biome of their own, so grass and leaves get plains' colours.
    private BlockModel withBiomeTint(BlockModel model) {
        return model.withTextures(this::withBiomeTint);
    }

    private BlockTexture withBiomeTint(BlockTexture texture) {
        BiomeTintColors colors = tintColors;
        if (texture.biomeTint() == null || colors == null) {
            return texture;
        }
        return texture.tinted(colors.colorAt(texture.biomeTint(), 0, 0));
    }

    private record LoadedObject(Map<BlockVector, BlockData> blocks, ObjectMesh mesh, ObjectSummary summary) {
    }
}
