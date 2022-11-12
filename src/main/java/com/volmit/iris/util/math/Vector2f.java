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

/**
 * A 2-element vector that is represented by single-precision floating
 * point x,y coordinates.
 */
public class Vector2f extends Tuple2f implements java.io.Serializable {

    // Combatible with 1.1
    static final long serialVersionUID = -2168194326883512320L;

    /**
     * Constructs and initializes a Vector2f from the specified xy coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Vector2f(float x, float y) {
        super(x, y);
    }


    /**
     * Constructs and initializes a Vector2f from the specified array.
     *
     * @param v the array of length 2 containing xy in order
     */
    public Vector2f(float[] v) {
        super(v);
    }


    /**
     * Constructs and initializes a Vector2f from the specified Vector2f.
     *
     * @param v1 the Vector2f containing the initialization x y data
     */
    public Vector2f(Vector2f v1) {
        super(v1);
    }


    /**
     * Constructs and initializes a Vector2f from the specified Vector2d.
     *
     * @param v1 the Vector2d containing the initialization x y data
     */
    public Vector2f(Vector2d v1) {
        super(v1);
    }


    /**
     * Constructs and initializes a Vector2f from the specified Tuple2f.
     *
     * @param t1 the Tuple2f containing the initialization x y data
     */
    public Vector2f(Tuple2f t1) {
        super(t1);
    }


    /**
     * Constructs and initializes a Vector2f from the specified Tuple2d.
     *
     * @param t1 the Tuple2d containing the initialization x y data
     */
    public Vector2f(Tuple2d t1) {
        super(t1);
    }


    /**
     * Constructs and initializes a Vector2f to (0,0).
     */
    public Vector2f() {
        super();
    }


    /**
     * Computes the dot product of the this vector and vector v1.
     *
     * @param v1 the other vector
     */
    public final float dot(Vector2f v1) {
        return (this.x * v1.x + this.y * v1.y);
    }


    /**
     * Returns the length of this vector.
     *
     * @return the length of this vector
     */
    public final float length() {
        return (float) Math.sqrt(this.x * this.x + this.y * this.y);
    }

    /**
     * Returns the squared length of this vector.
     *
     * @return the squared length of this vector
     */
    public final float lengthSquared() {
        return (this.x * this.x + this.y * this.y);
    }

    /**
     * Sets the value of this vector to the normalization of vector v1.
     *
     * @param v1 the un-normalized vector
     */
    public final void normalize(Vector2f v1) {
        float norm;

        norm = (float) (1.0 / Math.sqrt(v1.x * v1.x + v1.y * v1.y));
        this.x = v1.x * norm;
        this.y = v1.y * norm;
    }

    /**
     * Normalizes this vector in place.
     */
    public final void normalize() {
        float norm;

        norm = (float)
                (1.0 / Math.sqrt(this.x * this.x + this.y * this.y));
        this.x *= norm;
        this.y *= norm;
    }


    /**
     * Returns the angle in radians between this vector and the vector
     * parameter; the return value is constrained to the range [0,PI].
     *
     * @param v1 the other vector
     * @return the angle in radians in the range [0,PI]
     */
    public final float angle(Vector2f v1) {
        double vDot = this.dot(v1) / (this.length() * v1.length());
        if (vDot < -1.0) vDot = -1.0;
        if (vDot > 1.0) vDot = 1.0;
        return ((float) (Math.acos(vDot)));
    }


}
