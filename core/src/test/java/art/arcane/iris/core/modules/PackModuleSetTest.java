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

import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
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
}
