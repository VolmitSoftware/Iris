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
package art.arcane.iris.engine.procedural.part;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

@Desc("A rounded volume centered where the part sits: a leaf crown, boulder, cap, coral head or base. Values given as a curve run over where the blob sits on its parent.")
public final class PartBlob {
    @Desc("The horizontal radius in blocks.")
    private ProceduralValue radius = ProceduralValue.constant(2);

    @Desc("How far the blob reaches above its center, in blocks. Unset, it matches the radius.")
    private ProceduralValue height;

    @Desc("How far the blob reaches below its center, in blocks. Unset, it matches the height; 0 makes a dome.")
    private ProceduralValue depth;

    @MinNumber(0)
    @Desc("Stretches the blob along east-west; 0.1 flattens it into a north-south fan.")
    private double stretchX = 1;

    @MinNumber(0)
    @Desc("Stretches the blob along north-south; 0.1 flattens it into an east-west fan.")
    private double stretchZ = 1;

    @Desc("A multiplier on the radius from the bottom (0) to the top (1) of the blob. Unset, the blob is an ellipsoid; [[0, 1], [1, 0]] makes a cone, [[0, 1], [1, 1]] a cylinder.")
    private ProceduralValue profile;

    @Desc("The horizontal cross-section of the blob.")
    private PartSection section = PartSection.ROUND;

    @Desc("If above 0, the blob is hollow, keeping only the blocks this many blocks or fewer from its surface.")
    private ProceduralValue shell = ProceduralValue.constant(0);

    @Desc("If true the shell has no floor: only the surface above and around counts, like a mushroom cap.")
    private boolean open = false;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How lumpy the surface is, from 0 (smooth) to 1 (very lumpy).")
    private double roughness = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How many blocks along the surface are knocked off at random, from 0 to 1.")
    private double jitter = 0;

    public ProceduralValue radius() {
        return radius == null ? ProceduralValue.constant(2) : radius;
    }

    public ProceduralValue height() {
        return height;
    }

    public ProceduralValue depth() {
        return depth;
    }

    public double stretchX() {
        return stretchX;
    }

    public double stretchZ() {
        return stretchZ;
    }

    public ProceduralValue profile() {
        return profile;
    }

    public PartSection section() {
        return section == null ? PartSection.ROUND : section;
    }

    public ProceduralValue shell() {
        return shell == null ? ProceduralValue.constant(0) : shell;
    }

    public boolean open() {
        return open;
    }

    public double roughness() {
        return roughness;
    }

    public double jitter() {
        return jitter;
    }
}
