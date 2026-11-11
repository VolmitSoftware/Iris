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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.IrisLogging;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackModulesListingTest {
    private static final String RIVERS_MODULE = """
            package pack.rivers;

            import com.volmit.*;

            public final class Rivers implements IrisModule {
                public ModuleInfo info() { return ModuleInfo.of("acme:rivers", "Rivers", "2.1.0"); }
                public void setup(ModuleSetup setup) {}
            }
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void listsEveryModuleWithVersionIdAndJar() throws IOException {
        File pack = folder.newFolder("rivers-pack");
        PackModuleJar.write(pack.toPath(), "rivers.jar", "pack.rivers.Rivers", Map.of("pack.rivers.Rivers", RIVERS_MODULE));

        PackModulesListing.Listing listing = PackModulesListing.describe(IrisData.get(pack));
        List<String> lines = plain(listing);

        assertTrue(listing.loaded());
        assertEquals(List.of(
                "Pack rivers-pack loads 1 module:",
                "  Rivers 2.1.0 (acme:rivers, " + Path.of("modules", "rivers.jar") + ")"), lines);
    }

    @Test
    public void aPackWithoutModuleJarsSaysSo() throws IOException {
        List<String> lines = plain(PackModulesListing.describe(IrisData.get(folder.newFolder("plain"))));

        assertEquals(List.of("Pack plain loads 0 modules:", "  none"), lines);
    }

    @Test
    public void aPackWhoseModulesFailToLoadGetsOneLineWithTheReason() throws IOException {
        File pack = folder.newFolder("broken");
        Files.writeString(Files.createDirectories(pack.toPath().resolve("modules")).resolve("empty.jar"), "not a jar");

        PackModulesListing.Listing listing = PackModulesListing.describe(IrisData.get(pack));
        List<String> lines = plain(listing);

        assertFalse(listing.loaded());
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).startsWith("Pack broken cannot load its modules: "));
        assertFalse(lines.get(0), lines.get(0).contains("cannot load its modules: Pack broken"));
    }

    private static List<String> plain(PackModulesListing.Listing listing) {
        return listing.lines().stream().map(IrisLogging::clean).toList();
    }
}
