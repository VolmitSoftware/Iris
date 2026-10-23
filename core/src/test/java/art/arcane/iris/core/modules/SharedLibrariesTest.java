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

import art.arcane.volmlib.util.math.RNG;
import com.google.gson.JsonParser;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Pack modules call the libraries Iris shares with them, VolmLib and Gson, and get Iris's own classes of them. */
public class SharedLibrariesTest {
    private static final String MODULE = "pack.shared.Shared";
    private static final String SOURCE = """
            package pack.shared;

            import com.volmit.*;

            public final class Shared implements IrisModule {
                public ModuleInfo info() { return ModuleInfo.of("example:shared", "Shared", "1"); }
                public void setup(ModuleSetup setup) {
                    new art.arcane.volmlib.util.math.RNG(7).nextInt();
                    com.google.gson.JsonParser.parseString("{\\"a\\": 1}");
                }
                public static Class<?>[] used() {
                    return new Class<?>[] {art.arcane.volmlib.util.math.RNG.class, com.google.gson.JsonParser.class};
                }
            }
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aPackModuleUsesIrissVolmLibAndGson() throws Exception {
        Path pack = packWithSharedModule();

        try (LoadedPack loaded = LoadedPack.load(pack, ModuleHost.of(List.of())
                .exposing(IrisModuleHost.EXPOSED_PACKAGES.toArray(String[]::new))
                .copyingJarsTo(folder.newFolder().toPath()))) {
            Object used = loaded.packModules().getFirst().module().getClass().getMethod("used").invoke(null);
            assertArrayEquals(new Class<?>[]{RNG.class, JsonParser.class}, (Class<?>[]) used);
        }
    }

    @Test
    public void withoutTheLibrariesExposedTheyAreIrissHiddenClasses() throws IOException {
        Path pack = packWithSharedModule();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> LoadedPack.load(pack,
                ModuleHost.of(List.of()).exposing(IrisModuleHost.BUILT_IN_API_PACKAGE).copyingJarsTo(folder.newFolder().toPath())));

        assertTrue(failure.getMessage(), failure.getMessage().contains("art.arcane.volmlib.util.math.RNG"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("com.google.gson.JsonParser"));
    }

    private Path packWithSharedModule() throws IOException {
        Path pack = BuiltInPack.newPackFolder(folder);
        PackModuleJar.write(pack, "shared.jar", MODULE, Map.of(MODULE, SOURCE));
        return pack;
    }
}
