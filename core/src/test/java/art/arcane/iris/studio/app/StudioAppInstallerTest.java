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

package art.arcane.iris.studio.app;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioAppInstallerTest {
    @Test
    public void seesADisplayOnlyWhenLinuxHasOne() {
        assertTrue(StudioAppInstaller.hasDisplay("Linux", Map.of("WAYLAND_DISPLAY", "wayland-1")));
        assertTrue(StudioAppInstaller.hasDisplay("Linux", Map.of("DISPLAY", ":0")));
        assertFalse(StudioAppInstaller.hasDisplay("Linux", Map.of()));
        assertFalse(StudioAppInstaller.hasDisplay("Linux", Map.of("DISPLAY", " ")));
        assertTrue(StudioAppInstaller.hasDisplay("Windows 11", Map.of()));
        assertTrue(StudioAppInstaller.hasDisplay("Mac OS X", Map.of()));
    }
}
