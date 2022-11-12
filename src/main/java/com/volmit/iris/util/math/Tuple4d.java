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

import com.volmit.iris.Iris;

/**
 * A 4 element tuple represented by double precision floating point
 * x,y,z,w coordinates.
 */
public abstract class Tuple4d implements java.io.Serializable, Cloneable {

    static final long serialVersionUID = -4748953690425311052L;

    /**
     * The x coordinate.
     */
    public double x;

    /**
     * The y coordinate.
     */
    public double y;

    /**
     * The z coordinate.
     */
    public double z;

    /**
     * The w coordinate.
     */
    public double w;


    /**
     * Constructs and initializes a Tuple4d from the specified xyzw coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w coordinate
     */
    public Tuple4d(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }


    /**
     * Constructs and initializes a Tuple4d from the coordinates contained
     * in the array.
     *
     * @param t the array of length 4 containing xyzw in order
     */
    public Tuple4d(double[] t) {
        this.x = t[0];
        this.y = t[1];
        this.z = t[2];
        this.w = t[3];
    }


    /**
     * Constructs and initializes a Tuple4d from the specified Tuple4d.
     *
     * @param t1 the Tuple4d containing the initialization x y z w data
     */
    public Tuple4d(Tuple4d t1) {
        this.x = t1.x;
        this.y = t1.y;
        this.z = t1.z;
        this.w = t1.w;
    }


    /**
     * Constructs and initializes a Tuple4d from the specified Tuple4f.
     *
     * @param t1 the Tuple4f containing the initialization x y z w data
     */
    public Tuple4d(Tuple4f t1) {
        this.x = t1.x;
        this.y = t1.y;
        this.z = t1.z;
        this.w = t1.w;
    }


    /**
     * Constructs and initializes a Tuple4d to (0,0,0,0).
     */
    public Tuple4d() {
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.w = 0.0;
    }


    /**
     * Sets the value of this tuple to the specified xyzw coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w coordinate
     */
    public final void set(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }


    /**
     * Sets the value of this tuple to the specified xyzw coordinates.
     *
     * @param t the array of length 4 containing xyzw in order
     */
    public final void set(double[] t) {
        this.x = t[0];
        this.y = t[1];
        this.z = t[2];
        this.w = t[3];
    }


    /**
     * Sets the value of this tuple to the value of tuple t1.
     *
     * @param t1 the tuple to be copied
     */
    public final void set(Tuple4d t1) {
        this.x = t1.x;
        this.y = t1.y;
        this.z = t1.z;
        this.w = t1.w;
    }


    /**
     * Sets the value of this tuple to the value of tuple t1.
     *
     * @param t1 the tuple to be copied
     */
    public final void set(Tuple4f t1) {
        this.x = t1.x;
        this.y = t1.y;
        this.z = t1.z;
        this.w = t1.w;
    }


    /**
     * Gets the value of this tuple and places it into the array t of
     * length four in x,y,z,w order.
     *
     * @param t the array of length four
     */
    public final void get(double[] t) {
        t[0] = this.x;
        t[1] = this.y;
        t[2] = this.z;
        t[3] = this.w;
    }


    /**
     * Gets the value of this tuple and places it into the Tuple4d
     * argument of
     * length four in x,y,z,w order.
     *
     * @param t the Tuple into which the values will be copied
     */
    public final void get(Tuple4d t) {
        t.x = this.x;
        t.y = this.y;
        t.z = this.z;
        t.w = this.w;
    }


    /**
     * Sets the value of this tuple to the tuple sum of tuples t1 and t2.
     *
     * @param t1 the first tuple
     * @param t2 the second tuple
     */
    public final void add(Tuple4d t1, Tuple4d t2) {
        this.x = t1.x + t2.x;
        this.y = t1.y + t2.y;
        this.z = t1.z + t2.z;
        this.w = t1.w + t2.w;
    }


    /**
     * Sets the value of this tuple to the sum of itself and tuple t1.
     *
     * @param t1 the other tuple
     */
    public final void add(Tuple4d t1) {
        this.x += t1.x;
        this.y += t1.y;
        this.z += t1.z;
        this.w += t1.w;
    }


    /**
     * Sets the value of this tuple to the difference
     * of tuples t1 and t2 (this = t1 - t2).
     *
     * @param t1 the first tuple
     * @param t2 the second tuple
     */
    public final void sub(Tuple4d t1, Tuple4d t2) {
        this.x = t1.x - t2.x;
        this.y = t1.y - t2.y;
        this.z = t1.z - t2.z;
        this.w = t1.w - t2.w;
    }


    /**
     * Sets the value of this tuple to the difference of itself
     * and tuple t1 (this = this - t1).
     *
     * @param t1 the other tuple
     */
    public final void sub(Tuple4d t1) {
        this.x -= t1.x;
        this.y -= t1.y;
        this.z -= t1.z;
        this.w -= t1.w;
    }


    /**
     * Sets the value of this tuple to the negation of tuple t1.
     *
     * @param t1 the source tuple
     */
    public final void negate(Tuple4d t1) {
        this.x = -t1.x;
        this.y = -t1.y;
        this.z = -t1.z;
        this.w = -t1.w;
    }


    /**
     * Negates the value of this tuple in place.
     */
    public final void negate() {
        this.x = -this.x;
        this.y = -this.y;
        this.z = -this.z;
        this.w = -this.w;
    }


    /**
     * Sets the value of this tuple to the scalar multiplication
     * of the scale factor with the tuple t1.
     *
     * @param s  the scalar value
     * @param t1 the source tuple
     */
    public final void scale(double s, Tuple4d t1) {
        this.x = s * t1.x;
        this.y = s * t1.y;
        this.z = s * t1.z;
        this.w = s * t1.w;
    }


    /**
     * Sets the value of this tuple to the scalar multiplication
     * of the scale factor with this.
     *
     * @param s the scalar value
     */
    public final void scale(double s) {
        this.x *= s;
        this.y *= s;
        this.z *= s;
        this.w *= s;
    }


    /**
     * Sets the value of this tuple to the scalar multiplication by s
     * of tuple t1 plus tuple t2 (this = s*t1 + t2).
     *
     * @param s  the scalar value
     * @param t1 the tuple to be multipled
     * @param t2 the tuple to be added
     */
    public final void scaleAdd(double s, Tuple4d t1, Tuple4d t2) {
        this.x = s * t1.x + t2.x;
        this.y = s * t1.y + t2.y;
        this.z = s * t1.z + t2.z;
        this.w = s * t1.w + t2.w;
    }


    /**
     * @deprecated Use scaleAdd(double,Tuple4d) instead
     */
    @Deprecated
    public final void scaleAdd(float s, Tuple4d t1) {
        scaleAdd((double) s, t1);
    }


    /**
     * Sets the value of this tuple to the scalar multiplication
     * of itself and then adds tuple t1 (this = s*this + t1).
     *
     * @param s  the scalar value
     * @param t1 the tuple to be added
     */
    public final void scaleAdd(double s, Tuple4d t1) {
        this.x = s * this.x + t1.x;
        this.y = s * this.y + t1.y;
        this.z = s * this.z + t1.z;
        this.w = s * this.w + t1.w;
    }


    /**
     * Returns a string that contains the values of this Tuple4d.
     * The form is (x,y,z,w).
     *
     * @return the String representation
     */
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ", " + this.w + ")";
    }


    /**
     * Returns true if all of the data members of Tuple4d t1 are
     * equal to the corresponding data members in this Tuple4d.
     *
     * @param t1 the tuple with which the comparison is made
     * @return true or false
     */
    public boolean equals(Tuple4d t1) {
        try {
            return (this.x == t1.x && this.y == t1.y && this.z == t1.z
                    && this.w == t1.w);
        } catch (NullPointerException e2) {
            Iris.reportError(e2);
            return false;
        }
    }

    /**
     * Returns true if the Object t1 is of type Tuple4d and all of the
     * data members of t1 are equal to the corresponding data members in
     * this Tuple4d.
     *
     * @param t1 the object with which the comparison is made
     * @return true or false
     */
    public boolean equals(Object t1) {
        try {

            Tuple4d t2 = (Tuple4d) t1;
            return (this.x == t2.x && this.y == t2.y &&
                    this.z == t2.z && this.w == t2.w);
        } catch (NullPointerException | ClassCastException e2) {
            Iris.reportError(e2);
            return false;
        }
    }


    /**
     * Returns true if the L-infinite distance between this tuple
     * and tuple t1 is less than or equal to the epsilon parameter,
     * otherwise returns false.  The L-infinite
     * distance is equal to
     * MAX[abs(x1-x2), abs(y1-y2), abs(z1-z2), abs(w1-w2)].
     *
     * @param t1      the tuple to be compared to this tuple
     * @param epsilon the threshold value
     * @return true or false
     */
    public boolean epsilonEquals(Tuple4d t1, double epsilon) {
        double diff;

        diff = x - t1.x;
        if (Double.isNaN(diff)) return false;
        if ((diff < 0 ? -diff : diff) > epsilon) return false;

        diff = y - t1.y;
        if (Double.isNaN(diff)) return false;
        if ((diff < 0 ? -diff : diff) > epsilon) return false;

        diff = z - t1.z;
        if (Double.isNaN(diff)) return false;
        if ((diff < 0 ? -diff : diff) > epsilon) return false;

        diff = w - t1.w;
        if (Double.isNaN(diff)) return false;
        return !((diff < 0 ? -diff : diff) > epsilon);

    }


    /**
     * Returns a hash code value based on the data values in this
     * object.  Two different Tuple4d objects with identical data values
     * (i.e., Tuple4d.equals returns true) will return the same hash
     * code value.  Two objects with different data members may return the
     * same hash value, although this is not likely.
     *
     * @return the integer hash code value
     */
    public int hashCode() {
        long bits = 1L;
        bits = 31L * bits + VecMathUtil.doubleToLongBits(x);
        bits = 31L * bits + VecMathUtil.doubleToLongBits(y);
        bits = 31L * bits + VecMathUtil.doubleToLongBits(z);
        bits = 31L * bits + VecMathUtil.doubleToLongBits(w);
        return (int) (bits ^ (bits >> 32));
    }


    /**
     * @deprecated Use clamp(double,double,Tuple4d) instead
     */
    @Deprecated
    public final void clamp(float min, float max, Tuple4d t) {
        clamp(min, (double) max, t);
    }


    /**
     * Clamps the tuple parameter to the range [low, high] and
     * places the values into this tuple.
     *
     * @param min the lowest value in the tuple after clamping
     * @param max the highest value in the tuple after clamping
     * @param t   the source tuple, which will not be modified
     */
    public final void clamp(double min, double max, Tuple4d t) {
        if (t.x > max) {
            x = max;
        } else x = Math.max(t.x, min);

        if (t.y > max) {
            y = max;
        } else y = Math.max(t.y, min);

        if (t.z > max) {
            z = max;
        } else z = Math.max(t.z, min);

        if (t.w > max) {
            w = max;
        } else w = Math.max(t.w, min);

    }


    /**
     * @deprecated Use clampMin(double,Tuple4d) instead
     */
    @Deprecated
    public final void clampMin(float min, Tuple4d t) {
        clampMin((double) min, t);
    }


    /**
     * Clamps the minimum value of the tuple parameter to the min
     * parameter and places the values into this tuple.
     *
     * @param min the lowest value in the tuple after clamping
     * @param t   the source tuple, which will not be modified
     */
    public final void clampMin(double min, Tuple4d t) {
        x = Math.max(t.x, min);

        y = Math.max(t.y, min);

        z = Math.max(t.z, min);

        w = Math.max(t.w, min);

    }


    /**
     * @deprecated Use clampMax(double,Tuple4d) instead
     */
    @Deprecated
    public final void clampMax(float max, Tuple4d t) {
        clampMax((double) max, t);
    }


    /**
     * Clamps the maximum value of the tuple parameter to the max
     * parameter and places the values into this tuple.
     *
     * @param max the highest value in the tuple after clamping
     * @param t   the source tuple, which will not be modified
     */
    public final void clampMax(double max, Tuple4d t) {
        x = Math.min(t.x, max);

        y = Math.min(t.y, max);

        z = Math.min(t.z, max);

        if (t.w > max) {
            w = max;
        } else {
            w = t.z;
        }

    }


    /**
     * Sets each component of the tuple parameter to its absolute
     * value and places the modified values into this tuple.
     *
     * @param t the source tuple, which will not be modified
     */
    public final void absolute(Tuple4d t) {
        x = Math.abs(t.x);
        y = Math.abs(t.y);
        z = Math.abs(t.z);
        w = Math.abs(t.w);

    }


    /**
     * @deprecated Use clamp(double,double) instead
     */
    @Deprecated
    public final void clamp(float min, float max) {
        clamp(min, (double) max);
    }


    /**
     * Clamps this tuple to the range [low, high].
     *
     * @param min the lowest value in this tuple after clamping
     * @param max the highest value in this tuple after clamping
     */
    public final void clamp(double min, double max) {
        if (x > max) {
            x = max;
        } else if (x < min) {
            x = min;
        }

        if (y > max) {
            y = max;
        } else if (y < min) {
            y = min;
        }

        if (z > max) {
            z = max;
        } else if (z < min) {
            z = min;
        }

        if (w > max) {
            w = max;
        } else if (w < min) {
            w = min;
        }

    }


    /**
     * @deprecated Use clampMin(double) instead
     */
    @Deprecated
    public final void clampMin(float min) {
        clampMin((double) min);
    }


    /**
     * Clamps the minimum value of this tuple to the min parameter.
     *
     * @param min the lowest value in this tuple after clamping
     */
    public final void clampMin(double min) {
        if (x < min) x = min;
        if (y < min) y = min;
        if (z < min) z = min;
        if (w < min) w = min;
    }


    /**
     * @deprecated Use clampMax(double) instead
     */
    @Deprecated
    public final void clampMax(float max) {
        clampMax((double) max);
    }


    /**
     * Clamps the maximum value of this tuple to the max parameter.
     *
     * @param max the highest value in the tuple after clamping
     */
    public final void clampMax(double max) {
        if (x > max) x = max;
        if (y > max) y = max;
        if (z > max) z = max;
        if (w > max) w = max;

    }


    /**
     * Sets each component of this tuple to its absolute value.
     */
    public final void absolute() {
        x = Math.abs(x);
        y = Math.abs(y);
        z = Math.abs(z);
        w = Math.abs(w);

    }


    /**
     * @deprecated Use interpolate(Tuple4d,Tuple4d,double) instead
     */
    @Deprecated
    public void interpolate(Tuple4d t1, Tuple4d t2, float alpha) {
        interpolate(t1, t2, (double) alpha);
    }


    /**
     * Linearly interpolates between tuples t1 and t2 and places the
     * result into this tuple:  this = (1-alpha)*t1 + alpha*t2.
     *
     * @param t1    the first tuple
     * @param t2    the second tuple
     * @param alpha the alpha interpolation parameter
     */
    public void interpolate(Tuple4d t1, Tuple4d t2, double alpha) {
        this.x = (1 - alpha) * t1.x + alpha * t2.x;
        this.y = (1 - alpha) * t1.y + alpha * t2.y;
        this.z = (1 - alpha) * t1.z + alpha * t2.z;
        this.w = (1 - alpha) * t1.w + alpha * t2.w;
    }


    /**
     * @deprecated Use interpolate(Tuple4d,double) instead
     */
    @Deprecated
    public void interpolate(Tuple4d t1, float alpha) {
        interpolate(t1, (double) alpha);
    }


    /**
     * Linearly interpolates between this tuple and tuple t1 and
     * places the result into this tuple: this = (1-alpha)*this + alpha*t1.
     *
     * @param t1    the first tuple
     * @param alpha the alpha interpolation parameter
     */
    public void interpolate(Tuple4d t1, double alpha) {
        this.x = (1 - alpha) * this.x + alpha * t1.x;
        this.y = (1 - alpha) * this.y + alpha * t1.y;
        this.z = (1 - alpha) * this.z + alpha * t1.z;
        this.w = (1 - alpha) * this.w + alpha * t1.w;
    }

    /**
     * Creates a new object of the same class as this object.
     *
     * @return a clone of this instance.
     * @throws OutOfMemoryError if there is not enough memory.
     * @see java.lang.Cloneable
     * @since vecmath 1.3
     */
    public Object clone() {
        // Since there are no arrays we can just use Object.clone()
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            Iris.reportError(e);
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /**
     * Get the <i>x</i> coordinate.
     *
     * @return the x coordinate.
     * @since vecmath 1.5
     */
    public final double getX() {
        return x;
    }


    /**
     * Set the <i>x</i> coordinate.
     *
     * @param x value to <i>x</i> coordinate.
     * @since vecmath 1.5
     */
    public final void setX(double x) {
        this.x = x;
    }


    /**
     * Get the <i>y</i> coordinate.
     *
     * @return the <i>y</i> coordinate.
     * @since vecmath 1.5
     */
    public final double getY() {
        return y;
    }


    /**
     * Set the <i>y</i> coordinate.
     *
     * @param y value to <i>y</i> coordinate.
     * @since vecmath 1.5
     */
    public final void setY(double y) {
        this.y = y;
    }

    /**
     * Get the <i>z</i> coordinate.
     *
     * @return the <i>z</i> coordinate.
     * @since vecmath 1.5
     */
    public final double getZ() {
        return z;
    }


    /**
     * Set the <i>z</i> coordinate.
     *
     * @param z value to <i>z</i> coordinate.
     * @since vecmath 1.5
     */
    public final void setZ(double z) {
        this.z = z;
    }


    /**
     * Get the <i>w</i> coordinate.
     *
     * @return the <i>w</i> coordinate.
     * @since vecmath 1.5
     */
    public final double getW() {
        return w;
    }


    /**
     * Set the <i>w</i> coordinate.
     *
     * @param w value to <i>w</i> coordinate.
     * @since vecmath 1.5
     */
    public final void setW(double w) {
        this.w = w;
    }
}
