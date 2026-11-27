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

package art.arcane.iris.studio.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ElectronRuntimeTest {
    private static final ElectronRuntime.Platform LINUX = new ElectronRuntime.Platform("linux-x64", "electron");
    private static final String ELECTRON_FIXTURE = "/studio-app/electron-fixture.zip";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void namesPlatformsTheWayElectronReleasesDo() {
        assertEquals("linux-x64", ElectronRuntime.Platform.of("Linux", "amd64").name());
        assertEquals("linux-arm64", ElectronRuntime.Platform.of("Linux", "aarch64").name());
        assertEquals("electron.exe", ElectronRuntime.Platform.of("Windows 11", "amd64").executable());
        assertEquals("Electron.app/Contents/MacOS/Electron", ElectronRuntime.Platform.of("Mac OS X", "aarch64").executable());
        assertNull(ElectronRuntime.Platform.of("FreeBSD", "amd64"));
        assertNull(ElectronRuntime.Platform.of("Linux", "riscv64"));
    }

    @Test
    public void downloadsFromTheElectronReleaseOfThePinnedVersion() throws IOException {
        ElectronRuntime electron = ElectronRuntime.pinned(pins("45.2.3", "0".repeat(64)), LINUX);

        assertEquals(URI.create("https://github.com/electron/electron/releases/download/v45.2.3/electron-v45.2.3-linux-x64.zip"),
                electron.source());
    }

    @Test
    public void refusesAPlatformTheBuildDidNotPin() {
        assertThrows(IOException.class, () -> ElectronRuntime.pinned(pins("45.2.3", "0".repeat(64)),
                new ElectronRuntime.Platform("darwin-arm64", "Electron.app/Contents/MacOS/Electron")));
    }

    @Test
    public void unpacksAVerifiedArchiveWithItsExecutableBitOnce() throws IOException {
        Path archive = electronArchive();
        Path root = temporary.newFolder("studio").toPath();
        ElectronRuntime electron = ElectronRuntime.pinned(pins("45.2.3", ElectronRuntime.sha256(archive)), LINUX);
        List<URI> downloads = new ArrayList<>();
        ElectronRuntime.Downloader downloader = (source, target) -> {
            downloads.add(source);
            Files.copy(archive, target);
        };

        Path executable = electron.install(root, downloader);
        electron.install(root, downloader);

        assertEquals(root.resolve("electron-v45.2.3-linux-x64/electron"), executable);
        Path lib = root.resolve("electron-v45.2.3-linux-x64/lib");
        assertTrue(Files.getPosixFilePermissions(executable).contains(PosixFilePermission.OWNER_EXECUTE));
        assertFalse(Files.getPosixFilePermissions(lib.resolve("libffmpeg.so")).contains(PosixFilePermission.OWNER_EXECUTE));
        assertEquals(Path.of("libffmpeg.so"), Files.readSymbolicLink(lib.resolve("libffmpeg.so.1")));
        assertEquals("library", Files.readString(lib.resolve("libffmpeg.so.1")));
        assertEquals(1, downloads.size());
        assertFalse(Files.exists(root.resolve("electron-v45.2.3-linux-x64.zip.part")));
    }

    @Test
    public void leavesNothingInstalledWhenTheChecksumIsWrong() throws IOException {
        Path archive = electronArchive();
        Path root = temporary.newFolder("studio").toPath();
        ElectronRuntime electron = ElectronRuntime.pinned(pins("45.2.3", "0".repeat(64)), LINUX);

        assertThrows(IOException.class, () -> electron.install(root, (source, target) -> Files.copy(archive, target)));

        try (var children = Files.list(root)) {
            assertEquals(List.of(), children.toList());
        }
    }

    /** Made by Info-ZIP, as Electron's archives are: an executable, a plain file and a symlink to it. */
    private Path electronArchive() throws IOException {
        Path archive = temporary.getRoot().toPath().resolve("electron.zip");
        try (InputStream fixture = ElectronRuntimeTest.class.getResourceAsStream(ELECTRON_FIXTURE)) {
            Files.copy(fixture, archive);
        }
        return archive;
    }

    private static Properties pins(String version, String linuxChecksum) {
        Properties pins = new Properties();
        pins.setProperty("electron.version", version);
        pins.setProperty("linux-x64.sha256", linuxChecksum);
        return pins;
    }
}
