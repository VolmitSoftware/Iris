/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.world.loot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The modded item translator reads loot keys through these accessors instead of reflecting on IrisLoot fields, so
 * they are part of the platform-neutral contract.
 */
public class IrisLootKeyAccessorTest {
    @Test
    public void typeKeyReturnsAuthoredKeyVerbatim() {
        IrisLoot loot = new IrisLoot();
        loot.setType("mymod:ruby_sword");
        assertEquals("mymod:ruby_sword", loot.getTypeKey());
    }

    @Test
    public void typeKeyDefaultsToEmptyString() {
        assertEquals("", new IrisLoot().getTypeKey());
    }

    @Test
    public void dyeColorKeyReturnsAuthoredValueAndNullWhenUnset() {
        IrisLoot loot = new IrisLoot();
        assertNull(loot.getDyeColorKey());
        loot.setDyeColor("LIGHT_BLUE");
        assertEquals("LIGHT_BLUE", loot.getDyeColorKey());
    }
}
