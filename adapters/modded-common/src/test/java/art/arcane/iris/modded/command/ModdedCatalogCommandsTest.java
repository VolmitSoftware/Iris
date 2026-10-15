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

package art.arcane.iris.modded.command;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModdedCatalogCommandsTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void catalogAndIngestCommandsAreRegistered() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        IrisModdedCommands.register(new NativeCommandRegistration(dispatcher));

        CommandNode<CommandSourceStack> iris = child(dispatcher.getRoot(), "iris");
        CommandNode<CommandSourceStack> catalog = child(iris, "catalog");
        assertNotNull(catalog.getCommand());
        assertNotNull(child(catalog, "refresh").getCommand());
        assertNotNull(child(catalog, "namespace").getCommand());
        CommandNode<CommandSourceStack> ingest = child(iris, "ingest");
        assertNotNull(ingest.getCommand());
        CommandNode<CommandSourceStack> mods = child(ingest, "mods");
        assertNotNull(child(mods, "arguments").getCommand());
    }

    @Test
    public void helpDocumentsCatalogAndIngest() {
        assertTrue(ModdedCommandHelp.documents("", "catalog"));
        assertTrue(ModdedCommandHelp.documents("", "ingest"));
        assertTrue(ModdedCommandHelp.documents("ingest", "mods"));
        List<String> lines = ModdedCommandHelp.consoleLines("ingest");
        assertTrue(lines.stream().anyMatch((String line) ->
                line.startsWith("/iris ingest mods <source> <target> [dimension=<key>] [namespaces=<a,b>] [excludeRegions=<a,b>] [replace=true] - ")));
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(name, child);
        return child;
    }
}
