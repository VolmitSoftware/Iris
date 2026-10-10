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

import art.arcane.iris.spi.IrisPlatforms;
import com.google.gson.Gson;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PackModuleSetTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void loadsOnFirstUseAndClosesWithItsOwner() throws IOException {
        PackModuleSet modules = new PackModuleSet(folder.newFolder("plain"), Gson::new);

        assertSame(modules.pack(), modules.pack());
        assertTrue(modules.pack().packModules().isEmpty());

        modules.close();
        assertThrows(IllegalStateException.class, modules::pack);
    }

    @Test
    public void aFailedLoadIsRememberedWithThePackNamed() throws IOException {
        File pack = folder.newFolder("broken");
        Files.writeString(Files.createDirectories(pack.toPath().resolve("modules")).resolve("empty.jar"), "not a jar");
        PackModuleSet modules = new PackModuleSet(pack, Gson::new);

        PackModulesException first = assertThrows(PackModulesException.class, modules::pack);
        PackModulesException second = assertThrows(PackModulesException.class, modules::settings);

        assertSame(first, second);
        assertTrue(first.getMessage(), first.getMessage().startsWith("Pack broken cannot load its modules: "));
        modules.close();
    }

    @Test
    public void aPackWithoutTheBuiltInsJarIsToldHowToGetThem() throws IOException {
        File pack = folder.newFolder("without-builtins");

        String warnings = warningsWhile(() -> new PackModuleSet(pack, Gson::new).pack());

        assertTrue(warnings, warnings.contains("[Iris/WARN] Pack without-builtins loads without the built-in modules (iris-builtins.jar)"));
        assertTrue(warnings, warnings.contains("Copy iris-builtins.jar into " + new File(pack, "modules").getPath()));
    }

    @Test
    public void aPackIsToldOnceHoweverOftenItLoadsAndAWorldCopyOfItIsToldToo() throws IOException {
        File pack = folder.newFolder("original");
        File worldCopy = folder.newFolder("world", "iris", "pack");

        String warnings = warningsWhile(() -> {
            new PackModuleSet(pack, Gson::new).pack();
            new PackModuleSet(pack, Gson::new).pack();
            new PackModuleSet(worldCopy, Gson::new).pack();
        });

        assertEquals(warnings, 1, occurrences(warnings, "Pack original loads without"));
        assertEquals(warnings, 1, occurrences(warnings, "Pack pack loads without"));
    }

    @Test
    public void aPackWithTheBuiltInsJarIsNotWarned() throws IOException {
        File pack = folder.newFolder("with-builtins");
        BuiltInModulesJar.writeInto(pack.toPath());

        String warnings = warningsWhile(() -> assertFalse(new PackModuleSet(pack, Gson::new).pack().packModules().isEmpty()));

        assertFalse(warnings, warnings.contains("built-in modules"));
    }

    /** What Iris's log said at WARN while {@code load} ran: with no platform bound it goes to the error stream. */
    private static String warningsWhile(Runnable load) {
        Assume.assumeFalse("a platform is bound, so the log does not go to the error stream", IrisPlatforms.isBound());
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            load.run();
        } finally {
            System.setErr(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }
}
