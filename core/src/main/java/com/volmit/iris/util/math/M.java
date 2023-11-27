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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.regex.Matcher;

/**
 * Math
 *
 * @author cyberpwn
 */
public class M {
    private static final int precision = 128;
    private static final int modulus = 360 * precision;
    private static final float[] sin = new float[modulus];
    public static int tick = 0;

    static {
        for (int i = 0; i < sin.length; i++) {
            sin[i] = (float) Math.sin((i * Math.PI) / (precision * 180));
        }
    }

    /**
     * Scales B by an external range change so that <br/>
     * <br/>
     * BMIN < B < BMAX <br/>
     * AMIN < RESULT < AMAX <br/>
     * <br/>
     * So Given rangeScale(0, 20, 0, 10, 5) -> 10 <br/>
     * 0 < 5 < 10 <br/>
     * 0 < ? < 20 <br/>
     * <br/>
     * would return 10
     *
     * @param amin the resulting minimum
     * @param amax the resulting maximum
     * @param bmin the initial minimum
     * @param bmax the initial maximum
     * @param b    the initial value
     * @return the resulting value
     */
    public static double rangeScale(double amin, double amax, double bmin, double bmax, double b) {
        return amin + ((amax - amin) * ((b - bmin) / (bmax - bmin)));
    }

    /**
     * Get the percent (inverse lerp) from "from" to "to" where "at".
     * <p>
     * If from = 0 and to = 100 and at = 25 then it would return 0.25
     *
     * @param from the from
     * @param to   the to
     * @param at   the at
     * @return the percent
     */
    public static double lerpInverse(double from, double to, double at) {
        return M.rangeScale(0, 1, from, to, at);
    }

    /**
     * Linear interpolation from a to b where f is the percent across
     *
     * @param a the first pos (0)
     * @param b the second pos (1)
     * @param f the percent
     * @return the value
     */
    public static double lerp(double a, double b, double f) {
        return a + (f * (b - a));
    }

    /**
     * Bilinear interpolation
     *
     * @param a the first point (0, 0)
     * @param b the second point (1, 0)
     * @param c the third point (0, 1)
     * @param d the fourth point (1, 1)
     * @return the bilerped value
     */
    public static double bilerp(double a, double b, double c, double d, double x, double y) {
        return lerp(lerp(a, b, x), lerp(c, d, x), y);
    }

    /**
     * Trilinear interpolation
     *
     * @param a the first point (0, 0, 0)
     * @param b the second point (1, 0, 0)
     * @param c the third point (0, 0, 1)
     * @param d the fourth point (1, 0, 1)
     * @param e the fifth point (0, 1, 0)
     * @param f the sixth point (1, 1, 0)
     * @param g the seventh point (0, 1, 1)
     * @param h the eighth point (1, 1, 1)
     * @param x the x
     * @param y the y
     * @param z the z
     * @return the trilerped value
     */
    public static double trilerp(double a, double b, double c, double d, double e, double f, double g, double h, double x, double y, double z) {
        return lerp(bilerp(a, b, c, d, x, y), bilerp(e, f, g, h, x, y), z);
    }

    /**
     * Clip a value
     *
     * @param value the value
     * @param min   the min
     * @param max   the max
     * @return the clipped value
     */
    @SuppressWarnings("unchecked")
    public static <T extends Number> T clip(T value, T min, T max) {
        return (T) Double.valueOf(Math.min(max.doubleValue(), Math.max(min.doubleValue(), value.doubleValue())));
    }

    /**
     * Get true or false based on random percent
     *
     * @param d between 0 and 1
     * @return true if true
     */
    public static boolean r(Double d) {
        //noinspection ReplaceNullCheck
        if (d == null) {
            return Math.random() < 0.5;
        }

        return Math.random() < d;
    }

    /**
     * Get the ticks per second from a time in nanoseconds, the rad can be used for
     * multiple ticks
     *
     * @param ns  the time in nanoseconds
     * @param rad the radius of the time
     * @return the ticks per second in double form
     */
    public static double tps(long ns, int rad) {
        return (20.0 * (ns / 50000000.0)) / rad;
    }

    /**
     * Get the number of ticks from a time in nanoseconds
     *
     * @param ns the nanoseconds
     * @return the amount of ticks
     */
    public static double ticksFromNS(long ns) {
        return (ns / 50000000.0);
    }

    /**
     * Get a random int from to (inclusive)
     *
     * @param f the from
     * @param t the to
     * @return the value
     */
    public static int irand(int f, int t) {
        return f + (int) (Math.random() * ((t - f) + 1));
    }

    /**
     * Get a random float from to (inclusive)
     *
     * @param f the from
     * @param t the to
     * @return the value
     */
    public static float frand(float f, float t) {
        return f + (float) (Math.random() * ((t - f) + 1));
    }

    /**
     * Get a random double from to (inclusive)
     *
     * @param f the from
     * @param t the to
     * @return the value
     */
    public static double drand(double f, double t) {
        return f + (Math.random() * ((t - f) + 1));
    }

    /**
     * Get system Nanoseconds
     *
     * @return nanoseconds (current)
     */
    public static long ns() {
        return System.nanoTime();
    }

    /**
     * Get the current millisecond time
     *
     * @return milliseconds
     */
    public static long ms() {
        return System.currentTimeMillis();
    }

    /**
     * Fast sin function
     *
     * @param a the number
     * @return the sin
     */
    public static float sin(float a) {
        return sinLookup((int) (a * precision + 0.5f));
    }

    /**
     * Fast cos function
     *
     * @param a the number
     * @return the cos
     */
    public static float cos(float a) {
        return sinLookup((int) ((a + 90f) * precision + 0.5f));
    }

    /**
     * Fast tan function
     *
     * @param a the number
     * @return the tan
     */
    public static float tan(float a) {
        float c = cos(a);
        return sin(a) / (c == 0 ? 0.0000001f : c);
    }

    /**
     * Biggest number
     *
     * @return the biggest one
     */
    @SuppressWarnings("unchecked")
    public static <T extends Number> T max(T... doubles) {
        double max = Double.MIN_VALUE;

        for (T i : doubles) {
            if (i.doubleValue() > max) {
                max = i.doubleValue();
            }
        }

        return (T) Double.valueOf(max);
    }

    /**
     * Smallest number
     *
     * @param doubles the numbers
     * @return the smallest one
     */
    @SuppressWarnings("unchecked")
    public static <T extends Number> T min(T... doubles) {
        double min = Double.MAX_VALUE;

        for (T i : doubles) {
            if (i.doubleValue() < min) {
                min = i.doubleValue();
            }
        }

        return (T) Double.valueOf(min);
    }

    /**
     * Evaluates an expression using javascript engine and returns the double
     * result. This can take variable parameters, so you need to define them.
     * Parameters are defined as $[0-9]. For example evaluate("4$0/$1", 1, 2); This
     * makes the expression (4x1)/2 == 2. Keep note that you must use 0-9, you
     * cannot skip, or start at a number other than 0.
     *
     * @param expression the expression with variables
     * @param args       the arguments/variables
     * @return the resulting double value
     * @throws ScriptException           ... gg
     * @throws IndexOutOfBoundsException learn to count
     */
    public static double evaluate(String expression, Double... args) throws ScriptException, IndexOutOfBoundsException {
        for (int i = 0; i < args.length; i++) {
            String current = "$" + i;

            if (expression.contains(current)) {
                expression = expression.replaceAll(Matcher.quoteReplacement(current), args[i] + "");
            }
        }

        return evaluate(expression);
    }

    /**
     * Evaluates an expression using javascript engine and returns the double
     *
     * @param expression the mathimatical expression
     * @return the double result
     * @throws ScriptException ... gg
     */
    public static double evaluate(String expression) throws ScriptException {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine scriptEngine = mgr.getEngineByName("JavaScript");

        return Double.parseDouble(scriptEngine.eval(expression).toString());
    }

    /**
     * is the number "is" within from-to
     *
     * @param from the lower end
     * @param to   the upper end
     * @param is   the check
     * @return true if its within
     */
    public static boolean within(int from, int to, int is) {
        return is >= from && is <= to;
    }

    /**
     * Get the amount of days past since the epoch time (1970 jan 1 utc)
     *
     * @return the epoch days
     */
    public static long epochDays() {
        return epochDays(M.ms());
    }

    /**
     * Get the amount of days past since the epoch time (1970 jan 1 utc)
     *
     * @param ms the time in milliseconds
     * @return the epoch days
     */
    private static long epochDays(long ms) {
        return ms / 1000 / 60 / 60 / 24;
    }

    private static float sinLookup(int a) {
        return a >= 0 ? sin[a % (modulus)] : -sin[-a % (modulus)];
    }

    public static boolean interval(int tickInterval) {
        return tick % (tickInterval <= 0 ? 1 : tickInterval) == 0;
    }

}
