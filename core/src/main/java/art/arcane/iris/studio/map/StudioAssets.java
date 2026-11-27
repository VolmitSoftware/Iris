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

package art.arcane.iris.studio.map;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The block models, block states and textures of the Minecraft client, which the Iris Studio app draws blocks with.
 * They come from the client jar the Swing studio keeps in Iris's cache, fetched from Mojang the first time.
 */
public final class StudioAssets {
    private static final String ASSET_FOLDER = "assets/minecraft/";
    private static CompletableFuture<ZipFile> clientJar;

    private StudioAssets() {
    }

    /**
     * @param path such as {@code assets/minecraft/textures/block/stone.png}; empty when there is no such file or the
     *             path leaves the minecraft asset folder
     * @throws NotReadyException while the client jar is still being fetched, or when fetching it failed
     */
    public static Optional<byte[]> read(String path) throws IOException {
        if (!path.startsWith(ASSET_FOLDER) || path.contains("..") || path.contains("\\") || path.contains("//")) {
            return Optional.empty();
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

    private static synchronized ZipFile openedJar() {
        if (clientJar == null) {
            clientJar = MinecraftClientJar.open();
        }
        if (!clientJar.isDone()) {
            throw new NotReadyException("Fetching the Minecraft client for its textures");
        }
        if (clientJar.isCompletedExceptionally()) {
            clientJar = null;
            throw new NotReadyException("Could not fetch the Minecraft client");
        }
        return clientJar.join();
    }

    /** The assets cannot be read yet; the message says why, for the studio to show. */
    public static final class NotReadyException extends RuntimeException {
        NotReadyException(String message) {
            super(message);
        }
    }
}
