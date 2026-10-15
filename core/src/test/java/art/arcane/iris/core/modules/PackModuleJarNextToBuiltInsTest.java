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
import art.arcane.iris.engine.modifier.CarveModule;
import com.volmit.host.LoadedPack;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Module jars in a test pack's {@code modules} folder load next to the compiled-in built-ins. This is the path the built-ins
 * take when they move into a pack module jar one by one: a jar may list several modules, the {@code iris} namespace is free,
 * and a module id stays unique across the jar and the compiled-in modules.
 */
public class PackModuleJarNextToBuiltInsTest {
    private static final String MODULE_TEMPLATE = """
            package pack.%1$s;

            import com.volmit.*;
            import com.volmit.stage.*;

            public final class %2$s implements IrisModule {
                public ModuleInfo info() { return ModuleInfo.of("%3$s", "%2$s", "1"); }
                public void install(StageGraph graph) {
                    graph.define(Stage.placement("%3$s/work")).run(context -> {});
                }
            }
            """;
    private static final String FIRST_ID = "iris:ported";
    private static final String SECOND_ID = "example:companion";
    private static final String PREPARE_STAGE = "iris:prepare/slope";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aJarOfSeveralModulesLoadsNextToTheCompiledInBuiltIns() throws IOException {
        Path packFolder = BuiltInPack.newPackFolder(folder);
        PackModuleJar.write(packFolder, "builtins.jar", List.of("pack.ported.Ported", "pack.companion.Companion"),
                Map.of("pack.ported.Ported", module("ported", "Ported", FIRST_ID),
                        "pack.companion.Companion", module("companion", "Companion", SECOND_ID)));

        try (LoadedPack pack = BuiltInPack.serve(mock(IrisData.class), folder, packFolder, BuiltInPack.jarModules())) {
            assertEquals(List.of(FIRST_ID, SECOND_ID), pack.packModules().stream().map(module -> module.info().id().toString()).toList());
            List<String> stages = pack.placement().stages().stream().map(stage -> stage.stage().id().toString()).toList();
            assertTrue(stages.toString(), stages.contains(PREPARE_STAGE));
            assertTrue(stages.toString(), stages.contains(FIRST_ID + "/work"));
            assertTrue(stages.toString(), stages.contains(SECOND_ID + "/work"));
        }
    }

    @Test
    public void aJarModuleWithTheIdOfACompiledInBuiltInFailsAsADuplicate() throws IOException {
        String builtInId = new CarveModule().info().id().toString();
        Path packFolder = BuiltInPack.newPackFolder(folder);
        PackModuleJar.write(packFolder, "carve.jar", "pack.ported.Ported",
                Map.of("pack.ported.Ported", module("ported", "Ported", builtInId)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> BuiltInPack.serve(mock(IrisData.class), folder, packFolder, List.of()));

        assertEquals("Duplicate module " + builtInId + " from " + Path.of("modules", "carve.jar") + " (pack.ported.Ported); already loaded from host classpath ("
                + CarveModule.class.getName() + ")", failure.getMessage());
    }

    private static String module(String packageName, String className, String moduleId) {
        return MODULE_TEMPLATE.formatted(packageName, className, moduleId);
    }
}
