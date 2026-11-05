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
import com.volmit.config.Required;

@Desc("Grows copies of a part out of its parent: branches off a trunk, arms off a coral stalk, forks at a tip. Values given as a curve run from the parent's base (0) to its tip (1).")
public final class PartBranches {
    @Desc("How many copies grow. Ignored when chance is set.")
    private ProceduralValue count = ProceduralValue.constant(4);

    @Desc("If set, every block along the parent rolls this chance to grow a copy, such as {\"shape\": \"power\", \"exponent\": 2} for more toward the top.")
    private ProceduralValue chance;

    @Desc("The stretch of the parent copies grow from; from 1 to 1 grows them all at the tip.")
    private ProceduralSpan along = new ProceduralSpan();

    @Desc("Which way around the parent each copy points.")
    private BranchAzimuth azimuth = BranchAzimuth.RANDOM;

    @Desc("Turns every copy this many degrees further around the parent.")
    private ProceduralValue azimuthOffset = ProceduralValue.constant(0);

    @MinNumber(1)
    @Desc("How many copies make a ring for the WHORL azimuth.")
    private int whorl = 4;

    @Desc("The degrees between copies for the FAN azimuth.")
    private double fan = 40;

    @Desc("The angle in degrees above the horizon each copy grows toward; negative droops. With relative set, the angle is added to the parent's own.")
    private ProceduralValue pitch = ProceduralValue.constant(0);

    @Desc("If true the copies point relative to the parent's direction instead of the compass, as forks and twigs do.")
    private boolean relative = false;

    @MinNumber(1)
    @MaxNumber(6)
    @Desc("How many times branching repeats: 2 grows copies off the copies too, and so on.")
    private int depth = 1;

    @Required
    @Desc("The part each copy is made of.")
    private ProceduralPart part;

    public ProceduralValue count() {
        return count == null ? ProceduralValue.constant(4) : count;
    }

    public ProceduralValue chance() {
        return chance;
    }

    public ProceduralSpan along() {
        return along == null ? new ProceduralSpan() : along;
    }

    public BranchAzimuth azimuth() {
        return azimuth == null ? BranchAzimuth.RANDOM : azimuth;
    }

    public ProceduralValue azimuthOffset() {
        return azimuthOffset == null ? ProceduralValue.constant(0) : azimuthOffset;
    }

    public int whorl() {
        return Math.max(1, whorl);
    }

    public double fan() {
        return fan;
    }

    public ProceduralValue pitch() {
        return pitch == null ? ProceduralValue.constant(0) : pitch;
    }

    public boolean relative() {
        return relative;
    }

    public int depth() {
        return Math.max(1, Math.min(6, depth));
    }

    public ProceduralPart part() {
        return part;
    }
}
