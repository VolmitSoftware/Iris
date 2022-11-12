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

package com.volmit.iris.util.math;

import org.bukkit.util.Vector;

import java.util.List;

public interface PathInterpolation {

    /**
     * Sets nodes to be used by subsequent calls to
     * {@link #getPosition(double)} and the other methods.
     *
     * @param nodes the nodes
     */
    void setNodes(List<INode> nodes);

    /**
     * Gets the result of f(position).
     *
     * @param position the position to interpolate
     * @return the result
     */
    Vector getPosition(double position);

    /**
     * Gets the result of f'(position).
     *
     * @param position the position to interpolate
     * @return the result
     */
    Vector get1stDerivative(double position);

    /**
     * Gets the result of &int;<sub>a</sub><sup style="position: relative; left: -1ex">b</sup>|f'(t)| dt.<br />
     * That means it calculates the arc length (in meters) between positionA
     * and positionB.
     *
     * @param positionA lower limit
     * @param positionB upper limit
     * @return the arc length
     */
    double arcLength(double positionA, double positionB);

    /**
     * Get the segment position.
     *
     * @param position the position
     * @return the segment position
     */
    int getSegment(double position);

}