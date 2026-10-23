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

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.host.DiscoveredModule;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MissingBuiltInModulesTest {
    private static final File PACK = new File("packs", "overworld");

    @Test
    public void aPackWithNoModulesIsToldHowToGetThem() {
        String warning = MissingBuiltInModules.warning(PACK, List.of()).orElseThrow();

        assertTrue(warning, warning.startsWith("Pack overworld loads without the built-in modules (iris-builtins.jar)"));
        assertTrue(warning, warning.endsWith("does not bundle iris-builtins.jar; build the plugin with ./gradlew iris."));
    }

    @Test
    public void aPackWithOnlyOtherModulesIsToldToo() {
        assertTrue(MissingBuiltInModules.warning(PACK, List.of(module("pack:ruins"))).isPresent());
    }

    @Test
    public void anyOfTheJarsModulesCountsAsHavingTheJar() {
        for (String id : IrisModuleHost.JAR_MODULE_IDS) {
            assertEquals(id, Optional.empty(), MissingBuiltInModules.warning(PACK, List.of(module("pack:ruins"), module(id))));
        }
    }

    private static DiscoveredModule module(String id) {
        return DiscoveredModule.builtIn(new IrisModule() {
            @Override
            public ModuleInfo info() {
                return ModuleInfo.of(id, "Stand-in " + id, "test");
            }

            @Override
            public void setup(ModuleSetup setup) {
            }
        });
    }
}
