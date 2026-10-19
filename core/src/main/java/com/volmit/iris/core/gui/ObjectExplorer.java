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
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisObject;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import javax.swing.JComponent;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The map window's Objects view: every object in the pack in a folder tree, the chosen one in 3D, and what it is made
 * of. Inspired by the Procedural Objects workbench.
 */
final class ObjectExplorer {
    private static final NamespacedKey TINT_BIOME = NamespacedKey.minecraft("plains");
    private static final int UNKNOWN_BLOCK_COLOR = 0x8A8A8A;
    private static final String CAMERA_HINT = "Drag to orbit · Shift-drag to pan · Scroll to zoom · R resets";

    private final Engine startEngine;
    private final UUID worldId;
    private final ObjectBrowser browser = new ObjectBrowser(this::showObject);
    private final ObjectViewport viewport = new ObjectViewport();
    private final ObjectOverviewPanel overview = new ObjectOverviewPanel();
    private final StudioStatusBar statusBar = new StudioStatusBar();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris Object Loader");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final AtomicLong objectRevision = new AtomicLong();
    private final Map<String, BlockModel> models = new ConcurrentHashMap<>();
    private final Runnable hotloadHook = this::reloadPack;
    private volatile BlockTextures textures;
    private volatile BiomeTintColors tintColors;
    private String shownKey;

    ObjectExplorer(Engine engine, UUID worldId) {
        this.startEngine = engine;
        this.worldId = worldId;
        GuiHost.interruptOnPluginShutdown(loader);
        GuiHost.registerHotloadHook(hotloadHook);
        statusBar.showPosition(CAMERA_HINT);
    }

    JComponent browser() {
        return browser;
    }

    ObjectViewport viewport() {
        return viewport;
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
        return Arrays.asList(engine().getData().getObjectLoader().getPossibleKeys());
    }

    private void showCatalog(List<String> keys) {
        statusBar.showActivity("");
        browser.showObjects(keys);
        String key = shownKey != null && keys.contains(shownKey) ? shownKey : browser.firstObject();
        if (key == null) {
            viewport.showMessage("This pack has no objects");
            overview.clear();
            return;
        }
        browser.select(key);
        showObject(key);
    }

    private void showObject(String key) {
        shownKey = key;
        long revision = objectRevision.incrementAndGet();
        viewport.showMessage("Loading " + key + "...");
        statusBar.showActivity("Loading " + key + "...");
        loader.execute(() -> {
            if (revision != objectRevision.get()) {
                return;
            }
            try {
                LoadedObject loaded = load(key);
                EventQueue.invokeLater(() -> showLoaded(revision, loaded));
            } catch (RuntimeException error) {
                EventQueue.invokeLater(() -> showFailure(revision, key, error));
            }
        });
    }

    private LoadedObject load(String key) {
        IrisObject object = engine().getData().getObjectLoader().load(key);
        if (object == null) {
            throw new IllegalStateException("The object file could not be read");
        }
        Map<BlockVector, BlockData> blocks = new HashMap<>();
        object.getBlocks().forEach((position, block) -> {
            if (!block.getMaterial().isAir()) {
                blocks.put(position, block);
            }
        });
        ObjectMesh mesh = ObjectMesh.build(blocks, this::modelOf);
        ObjectSummary summary = ObjectSummary.of(key, mesh.bounds(), blocks.values(), this::iconOf);
        return new LoadedObject(mesh, summary);
    }

    private void showLoaded(long revision, LoadedObject loaded) {
        if (revision != objectRevision.get()) {
            return;
        }
        statusBar.showActivity("");
        viewport.show(loaded.mesh());
        overview.show(loaded.summary());
        statusBar.showSummary(String.format(Locale.ROOT, "%s · %,d blocks · %,d faces drawn",
                loaded.summary().key(), loaded.summary().blockCount(), loaded.mesh().faceCount()));
    }

    private void showFailure(long revision, String key, Throwable error) {
        if (revision != objectRevision.get()) {
            return;
        }
        statusBar.showActivity("");
        viewport.showMessage("Could not load " + key);
        overview.clear();
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

    private record LoadedObject(ObjectMesh mesh, ObjectSummary summary) {
    }
}
