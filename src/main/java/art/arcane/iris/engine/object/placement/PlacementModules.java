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

package art.arcane.iris.engine.object.placement;

import com.volmit.IrisModule;

import java.util.List;

/** Iris's built-in object placement, as the IrisApi modules every pack's placement graph starts from. */
public final class PlacementModules {
    static final String VERSION = "built-in";

    private PlacementModules() {
    }

    public static List<IrisModule> all() {
        return List.of(new PrepareModule(), new AnchorModule(), new ValidateModule(), new BoreModule(),
                new BlocksModule(), new StiltModule(), new VacuumModule(), new SnowModule());
    }
}
