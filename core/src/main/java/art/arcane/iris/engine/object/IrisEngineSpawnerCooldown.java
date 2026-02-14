/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.math.M;
import lombok.Data;

@Data
public class IrisEngineSpawnerCooldown {
    private long lastSpawn = 0;
    private String spawner;

    public void spawn(Engine engine) {
        lastSpawn = M.ms();
    }

    public boolean canSpawn(IrisRate s) {
        return M.ms() - lastSpawn > s.getInterval();
    }
}
