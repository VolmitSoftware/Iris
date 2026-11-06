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

import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/** The world of a tool's placement shares its state with every log view and closes it with the placement. */
public class ToolWorldTest {
    private static final long SEED = 42L;

    static final class Tracked implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void theWorldKeepsItsStateUntilItIsClosed() {
        PackResources resources = mock(PackResources.class);
        Tracked tracked;
        try (ToolWorld world = new ToolWorld(SEED, resources, mock(PackSettings.class), ignored -> mock(Placements.class))) {
            tracked = world.state(Tracked.class, ignored -> new Tracked());

            assertSame(tracked, world.state(Tracked.class, ignored -> new Tracked()));
            assertSame(resources, world.resources());
            assertEquals(SEED, world.seed());
            assertEquals(0, world.minWorldY());
            assertFalse(tracked.closed);
        }

        assertTrue(tracked.closed);
    }
}
