/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.assets;

import art.arcane.iris.core.link.CustomBlockSource;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The block models, block states and textures of the Minecraft client the server runs, which the studio draws blocks
 * with. The server has none of them, so the client jar is fetched from Mojang the first time the studio asks, checked
 * against Mojang's checksum, and kept in Iris's cache for later runs. One per server: every world shares it.
 */
public final class MinecraftAssets {
    private static final String ASSET_FOLDER = "assets/";
    private static final String MINECRAFT_ASSET_FOLDER = ASSET_FOLDER + "minecraft/";
    private static final MinecraftAssets SHARED = new MinecraftAssets();

    private CompletableFuture<ZipFile> clientJar;

    private MinecraftAssets() {
    }

    public static MinecraftAssets shared() {
        return SHARED;
    }

    /**
     * The asset file at {@code path}, such as {@code assets/minecraft/textures/block/stone.png}, as players see it: from
     * the resource packs of the server's plugins, which replace vanilla block states with their blocks' models, and
     * otherwise from the client jar; empty when there is no such file or the path leaves the asset folder.
     *
     * @throws NotReadyException while the client jar is still being fetched, or when fetching it failed; the next call
     *                           after a failure tries again
     */
    public Optional<byte[]> read(String path) throws IOException {
        if (!isAssetPath(path)) {
            return Optional.empty();
        }
        Optional<byte[]> fromPlugins = readFromPacks(pluginResourcePacks(), path);
        if (fromPlugins.isPresent() || !path.startsWith(MINECRAFT_ASSET_FOLDER)) {
            return fromPlugins;
        }
        ZipFile jar = openedJar();
        ZipEntry entry = jar.getEntry(path);
        if (entry == null || entry.isDirectory()) {
            return Optional.empty();
        }
        try (InputStream content = jar.getInputStream(entry)) {
            return Optional.of(content.readAllBytes());
        }
    }

    private static List<Path> pluginResourcePacks() {
        CustomBlockSource plugins = IrisServices.getOrNull(CustomBlockSource.class);
        return plugins == null ? List.of() : plugins.resourcePacks();
    }

    static Optional<byte[]> readFromPacks(List<Path> packs, String path) throws IOException {
        for (Path pack : packs) {
            try (ZipFile zip = new ZipFile(pack.toFile())) {
                ZipEntry entry = zip.getEntry(path);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream content = zip.getInputStream(entry)) {
                        return Optional.of(content.readAllBytes());
                    }
                }
            }
        }
        return Optional.empty();
    }

    static boolean isAssetPath(String path) {
        return path.startsWith(ASSET_FOLDER) && !path.contains("..") && !path.contains("\\") && !path.contains("//");
    }

    private synchronized ZipFile openedJar() {
        if (clientJar == null) {
            clientJar = CompletableFuture.supplyAsync(MinecraftAssets::fetchAndOpen, MinecraftAssets::runInBackground);
        }
        if (!clientJar.isDone()) {
            throw new NotReadyException("Fetching the Minecraft " + minecraftVersion() + " client for its textures");
        }
        if (clientJar.isCompletedExceptionally()) {
            String reason = clientJar.handle((jar, failure) -> readableReason(failure)).join();
            clientJar = null;
            throw new NotReadyException("Could not fetch the Minecraft client: " + reason);
        }
        return clientJar.join();
    }

    private static ZipFile fetchAndOpen() {
        try {
            Path jar = cachedJar().toPath();
            if (!jar.toFile().exists()) {
                ClientJarDownload.download(minecraftVersion(), jar);
            }
            return new ZipFile(jar.toFile());
        } catch (IOException failure) {
            throw new IllegalStateException(readableReason(failure), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", interrupted);
        }
    }

    private static File cachedJar() {
        return new File(IrisPlatforms.get().dataFolder(), "cache/minecraft-client/" + minecraftVersion() + ".jar");
    }

    private static String minecraftVersion() {
        return IrisPlatforms.get().minecraftVersion();
    }

    private static void runInBackground(Runnable task) {
        Thread thread = new Thread(task, "Iris Minecraft Client Download");
        thread.setDaemon(true);
        thread.start();
    }

    private static String readableReason(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && (cause.getMessage() == null || cause instanceof java.util.concurrent.CompletionException)) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /** The assets cannot be read yet; the message says why, in a few words for the studio to show. */
    public static final class NotReadyException extends RuntimeException {
        NotReadyException(String message) {
            super(message);
        }
    }
}
