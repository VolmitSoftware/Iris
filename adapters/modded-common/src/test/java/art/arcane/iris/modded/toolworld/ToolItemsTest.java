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

package art.arcane.iris.modded.toolworld;

import com.volmit.resource.ResourceId;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolText;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertThrows;

/**
 * Making and reading a stack needs the item components a running server binds, which a bootstrapped registry does not have;
 * that round trip is checked in the game.
 */
public class ToolItemsTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void anItemThePlatformLacksIsRefused() {
        ToolItem missing = new ToolItem(ResourceId.parse("iris:no_such_item"), ToolText.EMPTY, List.of(), false, "");

        assertThrows(IllegalArgumentException.class, () -> ToolItems.create(missing));
    }
}
