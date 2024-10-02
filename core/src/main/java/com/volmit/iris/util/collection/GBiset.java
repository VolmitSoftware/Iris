/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.collection;


import java.io.Serializable;

/**
 * A Biset
 *
 * @param <A> the first object type
 * @param <B> the second object type
 * @author cyberpwn
 */
@SuppressWarnings("hiding")
public class GBiset<A, B> implements Serializable {
    private static final long serialVersionUID = 1L;
    private A a;
    private B b;

    /**
     * Create a new Biset
     *
     * @param a the first object
     * @param b the second object
     */
    public GBiset(A a, B b) {
        this.a = a;
        this.b = b;
    }

    /**
     * Get the object of the type A
     *
     * @return the first object
     */
    public A getA() {
        return a;
    }

    /**
     * Set the first object
     *
     * @param a the first object A
     */
    public void setA(A a) {
        this.a = a;
    }

    /**
     * Get the second object
     *
     * @return the second object
     */
    public B getB() {
        return b;
    }

    /**
     * Set the second object
     */
    public void setB(B b) {
        this.b = b;
    }
}
