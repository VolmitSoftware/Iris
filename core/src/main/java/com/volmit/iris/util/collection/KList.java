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

import com.google.common.util.concurrent.AtomicDoubleArray;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("ALL")
public class KList<T> extends ArrayList<T> implements List<T> {
    private static final long serialVersionUID = -2892550695744823337L;

    @SafeVarargs
    public KList(T... ts) {
        super();
        add(ts);
    }

    public KList() {
        super();
    }

    public KList(int cap) {
        super(cap);
    }

    public KList(Collection<T> values) {
        super();
        add(values);
    }

    public KList(Enumeration<T> e) {
        super();
        add(e);
    }

    public static KList<String> fromJSONAny(JSONArray oo) {
        KList<String> s = new KList<String>();

        for (int i = 0; i < oo.length(); i++) {
            s.add(oo.get(i).toString());
        }

        return s;
    }

    public static KList<String> asStringList(List<?> oo) {
        KList<String> s = new KList<String>();

        for (Object i : oo) {
            s.add(i.toString());
        }

        return s;
    }

    public int indexOfAddIfNeeded(T v) {
        addIfMissing(v);
        return indexOf(v);
    }

    /**
     * Remove the last element
     */
    public KList<T> removeLast() {
        remove(last());

        return this;
    }

    public void addMultiple(T t, int c) {
        for (int i = 0; i < c; i++) {
            add(t);
        }
    }

    private KList<T> add(Enumeration<T> e) {
        while (e.hasMoreElements()) {
            add(e.nextElement());
        }

        return this;
    }

    public KList<T> add(Collection<T> values) {
        addAll(values);
        return this;
    }

    /**
     * Create a Map out of this list where this list becomes the values of the
     * returned map. You must specify each key for each value in this list. In the
     * function, returning null will not add the keyval pair.
     *
     * @param <K> the inferred key type
     * @param f   the function
     * @return the new map
     */
    public <K> KMap<K, T> asValues(Function<T, K> f) {
        KMap<K, T> m = new KMap<K, T>();
        forEach((i) -> m.putNonNull(f.apply(i), i));
        return m;
    }

    /**
     * Create a Map out of this list where this list becomes the keys of the
     * returned map. You must specify each value for each key in this list. In the
     * function, returning null will not add the keyval pair.
     *
     * @param <V> the inferred value type
     * @param f   the function
     * @return the new map
     */
    public <V> KMap<T, V> asKeys(Function<T, V> f) {
        KMap<T, V> m = new KMap<T, V>();
        forEach((i) -> m.putNonNull(i, f.apply(i)));
        return m;
    }

    /**
     * Cut this list into targetCount sublists
     *
     * @param targetCount the target count of sublists
     * @return the list of sublists
     */
    public KList<KList<T>> divide(int targetCount) {
        return split(size() / targetCount);
    }

    /**
     * Split this list into a list of sublists with roughly targetSize elements of T
     * per sublist
     *
     * @param targetSize the target size
     * @return the list of sublists
     */
    public KList<KList<T>> split(int targetSize) {
        targetSize = targetSize < 1 ? 1 : targetSize;
        KList<KList<T>> gg = new KList<>();
        KList<T> b = new KList<>();

        for (T i : this) {
            if (b.size() >= targetSize) {
                gg.add(b.copy());
                b.clear();
            }

            b.add(i);
        }

        if (!b.isEmpty()) {
            gg.add(b);
        }

        return gg;
    }

    /**
     * Rewrite this list by checking each value and changing the value (or not).
     * Return null to remove the element in the function
     *
     * @param t the function
     * @return the same list (not a copy)
     */
    public KList<T> rewrite(Function<T, T> t) {
        KList<T> m = copy();
        clear();

        for (T i : m) {
            addNonNull(t.apply(i));
        }

        return this;
    }

    /**
     * To array
     *
     * @return the array
     */
    @SuppressWarnings("unchecked")
    public T[] array() {
        return (T[]) toArray();
    }

    /**
     * Return a copy of this list
     *
     * @return the copy
     */
    public KList<T> copy() {
        return new KList<T>().add(this);
    }

    /**
     * Shuffle the list
     *
     * @return the same list
     */
    public KList<T> shuffle() {
        Collections.shuffle(this);
        return this;
    }

    public KList<T> shuffle(Random rng) {
        Collections.shuffle(this, rng);
        return this;
    }

    /**
     * Sort the list (based on toString comparison)
     *
     * @return the same list
     */
    public KList<T> sort() {
        Collections.sort(this, (a, b) -> a.toString().compareTo(b.toString()));
        return this;
    }

    /**
     * Reverse this list
     *
     * @return the same list
     */
    public KList<T> reverse() {
        Collections.reverse(this);
        return this;
    }

    @Override
    public String toString() {
        return "[" + toString(", ") + "]";
    }

    /**
     * Tostring with a seperator for each item in the list
     *
     * @param split the seperator
     * @return the string representing this object
     */
    public String toString(String split) {
        if (isEmpty()) {
            return "";
        }

        if (size() == 1) {
            return get(0) + "";
        }

        StringBuilder b = new StringBuilder();

        for (String i : toStringList()) {
            b.append(split).append(i == null ? "null" : i);
        }

        return b.substring(split.length());
    }

    /**
     * Invoke tostring on each value in the list into a string list
     *
     * @return the string list
     */
    public KList<String> toStringList() {
        return convert((t) -> t + "");
    }

    /**
     * Add the contents of the given list (v) into this list using a converter
     *
     * @param <V>       the type of the forign list
     * @param v         the forign (given) list
     * @param converter the converter that converts the forign type into this list type
     * @return this list (builder)
     */
    public <V> KList<T> addFrom(List<V> v, Function<V, T> converter) {
        v.forEach((g) -> add(converter.apply(g)));
        return this;
    }

    /**
     * Convert this list into another list type. Such as GList<Integer> to
     * GList<String>. list.convert((i) -> "" + i);
     */
    public <V> KList<V> convert(Function<T, V> converter) {
        KList<V> v = new KList<V>();
        forEach((t) -> v.addNonNull(converter.apply(t)));
        return v;
    }

    public KList<T> removeWhere(Predicate<T> t) {
        for (T i : copy()) {
            if (t.test(i)) {
                remove(i);
            }
        }

        return this;
    }

    /**
     * Adds T to the list, ignores if null
     *
     * @param t the value to add
     * @return the same list
     */
    public KList<T> addNonNull(T t) {
        if (t != null) {
            super.add(t);
        }

        return this;
    }

    /**
     * Swaps the values of index a and b. For example "hello", "world", "!" swap(1,
     * 2) would change the list to "hello", "!", "world"
     *
     * @param a the first index
     * @param b the second index
     * @return the same list (builder), not a copy
     */
    public KList<T> swapIndexes(int a, int b) {
        T aa = remove(a);
        T bb = get(b);
        add(a, bb);
        remove(b);
        add(b, aa);

        return this;
    }

    /**
     * Remove a number of elements from the list
     *
     * @param t the elements
     * @return this list
     */
    @SuppressWarnings("unchecked")
    public KList<T> remove(T... t) {
        for (T i : t) {
            super.remove(i);
        }

        return this;
    }

    /**
     * Add another glist's contents to this one (addall builder)
     *
     * @param t the list
     * @return the same list
     */
    public KList<T> add(KList<T> t) {
        super.addAll(t);
        return this;
    }

    /**
     * Add a number of values to this list
     *
     * @param t the list
     * @return this list
     */
    @SuppressWarnings("unchecked")
    public KList<T> add(T... t) {
        for (T i : t) {
            super.add(i);
        }

        return this;
    }

    /**
     * Check if this list has an index at the given index
     *
     * @param index the given index
     * @return true if size > index
     */
    public boolean hasIndex(int index) {
        return size() > index && index >= 0;
    }

    /**
     * Get the last index of this list (size - 1)
     *
     * @return the last index of this list
     */
    public int last() {
        return size() - 1;
    }

    /**
     * Deduplicate this list by converting to linked hash set and back
     *
     * @return the deduplicated list
     */
    public KList<T> dedupe() {
        LinkedHashSet<T> lhs = new LinkedHashSet<T>(this);
        return qclear().add(lhs);
    }

    /**
     * Clear this list (and return it)
     *
     * @return the same list
     */
    public KList<T> qclear() {
        super.clear();
        return this;
    }

    /**
     * Simply !isEmpty()
     *
     * @return true if this list has 1 or more element(s)
     */
    public boolean hasElements() {
        return !isEmpty();
    }

    /**
     * Simply !isEmpty()
     *
     * @return true if this list has 1 or more element(s)
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Pop the first item off this list and return it
     *
     * @return the popped off item or null if the list is empty
     */
    public T pop() {
        if (isEmpty()) {
            return null;
        }

        return remove(0);
    }

    /**
     * Pop the last item off this list and return it
     *
     * @return the popped off item or null if the list is empty
     */
    public T popLast() {
        if (isEmpty()) {
            return null;
        }

        return remove(last());
    }

    public T popRandom() {
        if (isEmpty()) {
            return null;
        }

        if (size() == 1) {
            return pop();
        }

        return remove(M.irand(0, last()));
    }

    public T popRandom(RNG rng) {
        if (isEmpty()) {
            return null;
        }

        if (size() == 1) {
            return pop();
        }

        return remove(rng.i(0, last()));
    }

    public KList<T> sub(int f, int t) {
        KList<T> g = new KList<>();

        for (int i = f; i < M.min(size(), t); i++) {
            g.add(get(i));
        }

        return g;
    }

    public JSONArray toJSONStringArray() {
        JSONArray j = new JSONArray();

        for (Object i : this) {
            j.put(i.toString());
        }

        return j;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(Object[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(int[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(double[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(AtomicDoubleArray values) {
        for (int i = 0; i < values.length(); i++) {
            add((T) ((Object) values.get(i)));
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(float[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(byte[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(short[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(long[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public KList<T> forceAdd(boolean[] values) {
        for (Object i : values) {
            add((T) i);
        }

        return this;
    }

    public T middleValue() {
        return get(middleIndex());
    }

    private int middleIndex() {
        return size() % 2 == 0 ? (size() / 2) : ((size() / 2) + 1);
    }

    public T getRandom() {
        if (isEmpty()) {
            return null;
        }

        if (size() == 1) {
            return get(0);
        }

        return get(M.irand(0, last()));
    }

    public KList<T> popRandom(RNG rng, int c) {
        KList<T> m = new KList<>();

        for (int i = 0; i < c; i++) {
            if (isEmpty()) {
                break;
            }

            m.add(popRandom());
        }

        return m;
    }

    public T getRandom(RNG rng) {
        if (isEmpty()) {
            return null;
        }

        if (size() == 1) {
            return get(0);
        }

        return get(rng.i(0, last()));
    }

    public KList<T> qdel(T t) {
        remove(t);
        return this;
    }

    public KList<T> qadd(T t) {
        add(t);
        return this;
    }

    public KList<T> qaddIfMissing(T t) {
        addIfMissing(t);
        return this;
    }

    public KList<T> removeDuplicates() {
        KSet<T> v = new KSet<>();
        v.addAll(this);
        KList<T> m = new KList<>();
        m.addAll(v);
        return m;
    }

    public boolean addIfMissing(T t) {
        if (!contains(t)) {
            add(t);
            return true;
        }

        return false;
    }

    public void addAllIfMissing(KList<T> t) {
        for (T i : t) {
            if (!contains(i)) {
                add(i);
            }
        }
    }

    public KList<T> shuffleCopy(Random rng) {
        KList<T> t = copy();
        t.shuffle(rng);
        return t;
    }

    public KList<T> qdrop() {
        pop();
        return this;
    }
}
