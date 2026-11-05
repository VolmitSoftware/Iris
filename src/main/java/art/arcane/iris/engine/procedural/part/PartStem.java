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

@Desc("A line that grows from where the part sits: a trunk, branch, spire, stalk, root or shard. Values given as a curve run over where the stem sits on its parent, except taper, lean curve, bend curve and helix radius, which run from the stem's base (0) to its tip (1).")
public final class PartStem {
    @Desc("How long the stem grows, in blocks.")
    private ProceduralValue length = ProceduralValue.constant(8);

    @MinNumber(0)
    @Desc("If above 0, the stem is this fraction of its parent stem's length instead, for branches that shrink as they split.")
    private double lengthScale = 0;

    @Desc("The radius at the base in blocks. 0.5 is one block wide.")
    private ProceduralValue radius = ProceduralValue.constant(0.5);

    @Desc("A multiplier on the radius from the base (0) to the tip (1), such as [[0, 1], [1, 0]] to taper to a point.")
    private ProceduralValue taper;

    @Desc("The cross-section of the stem.")
    private PartSection section = PartSection.ROUND;

    @Desc("The angle in degrees above the horizon the stem grows toward: 90 is straight up, -90 straight down. Unset, it carries on its parent's direction (up for a root part).")
    private ProceduralValue pitch;

    @Desc("The compass direction in degrees the stem grows toward when pitch is set. Unset, it keeps its parent's direction, or grows toward its facing.")
    private ProceduralValue azimuth;

    @Desc("The compass direction in degrees the stem leans and bends toward. Unset, it leans away from the center of its cluster or branch, or a random way.")
    private ProceduralValue facing;

    @Desc("How far the stem leans, in degrees from its direction, by the time it reaches its tip.")
    private ProceduralValue lean = ProceduralValue.constant(0);

    @Desc("How the lean builds up from base (0) to tip (1), from 0 to 1. Unset, it grows evenly.")
    private ProceduralValue leanCurve;

    @Desc("How far the stem turns, in degrees, by its tip: 90 turns an upright stem sideways, 180 brings it back down like an arch.")
    private ProceduralValue bend = ProceduralValue.constant(0);

    @Desc("How the bend builds up from base (0) to tip (1), from 0 to 1. Unset, it turns evenly.")
    private ProceduralValue bendCurve;

    @Desc("How much the stem sags under its own weight, as a fraction of its length: 0.2 drops the tip a fifth of the length.")
    private ProceduralValue droop = ProceduralValue.constant(0);

    @Desc("How far the stem wanders sideways at random, in blocks.")
    private ProceduralValue sway = ProceduralValue.constant(0);

    @Desc("How far the stem waves from side to side, in blocks.")
    private ProceduralValue wave = ProceduralValue.constant(0);

    @Desc("How many full waves the stem makes over its length.")
    private ProceduralValue wavePeriods = ProceduralValue.constant(1);

    @Desc("The radius in blocks of a corkscrew the stem winds around, from base (0) to tip (1). It starts on the facing side, so copies facing apart wind as a double helix.")
    private ProceduralValue helixRadius = ProceduralValue.constant(0);

    @Desc("How many full turns the corkscrew makes over the stem's length.")
    private ProceduralValue helixTurns = ProceduralValue.constant(1);

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How rough the surface is, from 0 (smooth) to 1 (heavily pitted).")
    private double roughness = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How many blocks along the surface are knocked off at random, from 0 to 1.")
    private double jitter = 0;

    public ProceduralValue length() {
        return length == null ? ProceduralValue.constant(8) : length;
    }

    public double lengthScale() {
        return lengthScale;
    }

    public ProceduralValue radius() {
        return radius == null ? ProceduralValue.constant(0.5) : radius;
    }

    public ProceduralValue taper() {
        return taper;
    }

    public PartSection section() {
        return section == null ? PartSection.ROUND : section;
    }

    public ProceduralValue pitch() {
        return pitch;
    }

    public ProceduralValue azimuth() {
        return azimuth;
    }

    public ProceduralValue facing() {
        return facing;
    }

    public ProceduralValue lean() {
        return lean;
    }

    public ProceduralValue leanCurve() {
        return leanCurve;
    }

    public ProceduralValue bend() {
        return bend;
    }

    public ProceduralValue bendCurve() {
        return bendCurve;
    }

    public ProceduralValue droop() {
        return droop;
    }

    public ProceduralValue sway() {
        return sway;
    }

    public ProceduralValue wave() {
        return wave;
    }

    public ProceduralValue wavePeriods() {
        return wavePeriods;
    }

    public ProceduralValue helixRadius() {
        return helixRadius;
    }

    public ProceduralValue helixTurns() {
        return helixTurns;
    }

    public double roughness() {
        return roughness;
    }

    public double jitter() {
        return jitter;
    }
}
