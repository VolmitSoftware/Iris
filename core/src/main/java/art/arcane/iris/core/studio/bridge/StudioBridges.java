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

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The open bridges, one per world. Each is announced in a connection file under {@code ~/.iris/studio/bridges}, which
 * a studio app on the same machine lists to connect without a pasted link; the file goes when the bridge closes.
 */
public final class StudioBridges {
    private static final Path CONNECTION_FOLDER = Path.of(System.getProperty("user.home"), ".iris", "studio", "bridges");
    private static final String OWNER_ONLY = "rw-------";
    private static final Map<String, StudioBridge> OPEN = new ConcurrentHashMap<>();

    private StudioBridges() {
    }

    /**
     * The bridge to {@code worldName}, started when it has none yet.
     *
     * @param engines  the world's engine now, or null while it has none
     * @param openerId the player the studio's teleports move, or null for the console
     */
    public static synchronized StudioBridge open(String worldName, Supplier<Engine> engines, UUID openerId) throws IOException {
        StudioBridge open = OPEN.get(worldName);
        if (open != null) {
            return open;
        }
        StudioBridge bridge = StudioBridge.start(new StudioWorld(worldName, engines, openerId));
        OPEN.put(worldName, bridge);
        announce(bridge);
        return bridge;
    }

    public static synchronized void closeAll() {
        for (StudioBridge bridge : OPEN.values()) {
            bridge.close();
            withdraw(bridge);
        }
        OPEN.clear();
    }

    private static void announce(StudioBridge bridge) {
        try {
            Files.createDirectories(CONNECTION_FOLDER);
            Path file = connectionFile(bridge);
            Connection connection = new Connection(bridge.worldName(), bridge.port(), bridge.token(), bridge.link(),
                    ProcessHandle.current().pid(), System.currentTimeMillis());
            Files.deleteIfExists(file);
            createReadableByOwnerOnly(file);
            Files.writeString(file, new Gson().toJson(connection), StandardCharsets.UTF_8);
        } catch (IOException error) {
            IrisLogging.warn("Could not write the studio connection file: %s", error.getMessage());
        }
    }

    // The file holds the token, so other users of the machine must not read it, not even before it is written.
    private static void createReadableByOwnerOnly(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY)));
            return;
        }
        Files.createFile(file);
    }

    private static void withdraw(StudioBridge bridge) {
        try {
            Files.deleteIfExists(connectionFile(bridge));
        } catch (IOException error) {
            IrisLogging.debug("Could not remove the studio connection file: " + error.getMessage());
        }
    }

    private static Path connectionFile(StudioBridge bridge) {
        return CONNECTION_FOLDER.resolve(bridge.port() + ".json");
    }

    /** @param pid the server's process, so the studio can skip files a crashed server left */
    private record Connection(String world, int port, String token, String link, long pid, long startedMillis) {
    }
}
