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

package com.volmit.iris.util.nbt.tag;

import com.volmit.iris.engine.data.io.MaxDepthIO;
import com.volmit.iris.util.collection.KList;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * ListTag represents a typed List in the nbt structure.
 * An empty {@link ListTag} created using {@link ListTag#createUnchecked(Class)} will be of unknown type
 * and returns an {@link EndTag}{@code .class} in {@link ListTag#getTypeClass()}.
 * The type of an empty untyped {@link ListTag} can be set by using any of the {@code add()}
 * methods or any of the {@code as...List()} methods.
 */
@SuppressWarnings("ALL")
public class ListTag<T extends Tag<?>> extends Tag<List<T>> implements Iterable<T>, Comparable<ListTag<T>>, MaxDepthIO {

    public static final byte ID = 9;

    private Class<?> typeClass = null;

    private ListTag() {
        super(createEmptyValue(3));
    }

    /**
     * @param typeClass The exact class of the elements
     * @throws IllegalArgumentException When {@code typeClass} is {@link EndTag}{@code .class}
     * @throws NullPointerException     When {@code typeClass} is {@code null}
     */
    public ListTag(Class<? super T> typeClass) throws IllegalArgumentException, NullPointerException {
        super(createEmptyValue(3));
        if (typeClass == EndTag.class) {
            throw new IllegalArgumentException("cannot create ListTag with EndTag elements");
        }
        this.typeClass = Objects.requireNonNull(typeClass);
    }

    /**
     * <p>Creates a non-type-safe ListTag. Its element type will be set after the first
     * element was added.</p>
     *
     * <p>This is an internal helper method for cases where the element type is not known
     * at construction time. Use {@link #ListTag(Class)} when the type is known.</p>
     *
     * @return A new non-type-safe ListTag
     */
    public static ListTag<?> createUnchecked(Class<?> typeClass) {
        ListTag<?> list = new ListTag<>();
        list.typeClass = typeClass;
        return list;
    }

    /**
     * <p>Creates an empty mutable list to be used as empty value of ListTags.</p>
     *
     * @param <T>             Type of the list elements
     * @param initialCapacity The initial capacity of the returned List
     * @return An instance of {@link List} with an initial capacity of 3
     */
    private static <T> List<T> createEmptyValue(@SuppressWarnings("SameParameterValue") int initialCapacity) {
        return new KList<>(initialCapacity);
    }

    public ListTag<T> makeAtomic() {
        setValue(new CopyOnWriteArrayList<>(getValue()));
        return this;
    }

    @Override
    public byte getID() {
        return ID;
    }

    public Class<?> getTypeClass() {
        return typeClass == null ? EndTag.class : typeClass;
    }

    public int size() {
        return getValue().size();
    }

    public T remove(int index) {
        return getValue().remove(index);
    }

    public void clear() {
        getValue().clear();
    }

    public boolean contains(T t) {
        return getValue().contains(t);
    }

    public boolean containsAll(Collection<Tag<?>> tags) {
        return getValue().containsAll(tags);
    }

    public void sort(Comparator<T> comparator) {
        getValue().sort(comparator);
    }

    @Override
    public Iterator<T> iterator() {
        return getValue().iterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        getValue().forEach(action);
    }

    public T set(int index, T t) {
        return getValue().set(index, Objects.requireNonNull(t));
    }

    /**
     * Adds a Tag to this ListTag after the last index.
     *
     * @param t The element to be added.
     */
    public void add(T t) {
        add(size(), t);
    }

    public void add(int index, T t) {
        Objects.requireNonNull(t);
        if (typeClass == null || typeClass == EndTag.class) {
            typeClass = t.getClass();
        } else if (typeClass != t.getClass()) {
            throw new ClassCastException(
                    String.format("cannot add %s to ListTag<%s>",
                            t.getClass().getSimpleName(),
                            typeClass.getSimpleName()));
        }
        getValue().add(index, t);
    }

    public void addAll(Collection<T> t) {
        for (T tt : t) {
            add(tt);
        }
    }

    public void addAll(int index, Collection<T> t) {
        int i = 0;
        for (T tt : t) {
            add(index + i, tt);
            i++;
        }
    }

    public void addBoolean(boolean value) {
        addUnchecked(new ByteTag(value));
    }

    public void addByte(byte value) {
        addUnchecked(new ByteTag(value));
    }

    public void addShort(short value) {
        addUnchecked(new ShortTag(value));
    }

    public void addInt(int value) {
        addUnchecked(new IntTag(value));
    }

    public void addLong(long value) {
        addUnchecked(new LongTag(value));
    }

    public void addFloat(float value) {
        addUnchecked(new FloatTag(value));
    }

    public void addDouble(double value) {
        addUnchecked(new DoubleTag(value));
    }

    public void addString(String value) {
        addUnchecked(new StringTag(value));
    }

    public void addByteArray(byte[] value) {
        addUnchecked(new ByteArrayTag(value));
    }

    public void addIntArray(int[] value) {
        addUnchecked(new IntArrayTag(value));
    }

    public void addLongArray(long[] value) {
        addUnchecked(new LongArrayTag(value));
    }

    public T get(int index) {
        return getValue().get(index);
    }

    public int indexOf(T t) {
        return getValue().indexOf(t);
    }

    @SuppressWarnings("unchecked")
    public <L extends Tag<?>> ListTag<L> asTypedList(Class<L> type) {
        checkTypeClass(type);
        typeClass = type;
        return (ListTag<L>) this;
    }

    public ListTag<ByteTag> asByteTagList() {
        return asTypedList(ByteTag.class);
    }

    public ListTag<ShortTag> asShortTagList() {
        return asTypedList(ShortTag.class);
    }

    public ListTag<IntTag> asIntTagList() {
        return asTypedList(IntTag.class);
    }

    public ListTag<LongTag> asLongTagList() {
        return asTypedList(LongTag.class);
    }

    public ListTag<FloatTag> asFloatTagList() {
        return asTypedList(FloatTag.class);
    }

    public ListTag<DoubleTag> asDoubleTagList() {
        return asTypedList(DoubleTag.class);
    }

    public ListTag<StringTag> asStringTagList() {
        return asTypedList(StringTag.class);
    }

    public ListTag<ByteArrayTag> asByteArrayTagList() {
        return asTypedList(ByteArrayTag.class);
    }

    public ListTag<IntArrayTag> asIntArrayTagList() {
        return asTypedList(IntArrayTag.class);
    }

    public ListTag<LongArrayTag> asLongArrayTagList() {
        return asTypedList(LongArrayTag.class);
    }

    @SuppressWarnings("unchecked")
    public ListTag<ListTag<?>> asListTagList() {
        checkTypeClass(ListTag.class);
        typeClass = ListTag.class;
        return (ListTag<ListTag<?>>) this;
    }

    public ListTag<CompoundTag> asCompoundTagList() {
        return asTypedList(CompoundTag.class);
    }

    @Override
    public String valueToString(int maxDepth) {
        StringBuilder sb = new StringBuilder("{\"type\":\"").append(getTypeClass().getSimpleName()).append("\",\"list\":[");
        for (int i = 0; i < size(); i++) {
            sb.append(i > 0 ? "," : "").append(get(i).valueToString(decrementMaxDepth(maxDepth)));
        }
        sb.append("]}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other) || size() != ((ListTag<?>) other).size() || getTypeClass() != ((ListTag<?>) other).getTypeClass()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!get(i).equals(((ListTag<?>) other).get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue().hashCode());
    }

    @Override
    public int compareTo(ListTag<T> o) {
        return Integer.compare(size(), o.getValue().size());
    }

    @SuppressWarnings("unchecked")
    @Override
    public ListTag<T> clone() {
        ListTag<T> copy = new ListTag<>();
        // assure type safety for clone
        copy.typeClass = typeClass;
        for (T t : getValue()) {
            copy.add((T) t.clone());
        }
        return copy;
    }

    //TODO: make private
    @SuppressWarnings("unchecked")
    public void addUnchecked(Tag<?> tag) {
        if (typeClass != null && typeClass != tag.getClass() && typeClass != EndTag.class) {
            throw new IllegalArgumentException(String.format(
                    "cannot add %s to ListTag<%s>",
                    tag.getClass().getSimpleName(), typeClass.getSimpleName()));
        }
        add(size(), (T) tag);
    }

    private void checkTypeClass(Class<?> clazz) {
        if (typeClass != null && typeClass != EndTag.class && typeClass != clazz) {
            throw new ClassCastException(String.format(
                    "cannot cast ListTag<%s> to ListTag<%s>",
                    typeClass.getSimpleName(), clazz.getSimpleName()));
        }
    }
}
