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

package art.arcane.iris.core.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GuiMarkerTest {
    private static final float EXACT = 0F;

    @Test
    public void playerYawIsRoundedToTheStep() {
        assertEquals(90F, GuiMarker.player("a", 0, 0, 91.4F).yaw(), EXACT);
        assertEquals(95F, GuiMarker.player("a", 0, 0, 92.6F).yaw(), EXACT);
        assertEquals(-45F, GuiMarker.player("a", 0, 0, -44F).yaw(), EXACT);
    }

    @Test
    public void nearbyYawsCompareEqualSoTurningSlightlyResendsNothing() {
        assertEquals(GuiMarker.player("a", 1, 2, 180.2F), GuiMarker.player("a", 1, 2, 181.9F));
    }

    @Test
    public void entitiesHaveNoFacing() {
        assertEquals(0F, GuiMarker.entity("zombie", 0, 64, 0, 20, 20).yaw(), EXACT);
    }
}
