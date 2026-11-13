package com.volmit.config;

import java.lang.annotation.*;

/**
 * A settings value packs may write in more than one JSON shape, such as a number, a {@code [min, max]} range or an
 * object. The host reads the JSON as plain values and hands it to {@link #value()}, and writes the value back
 * through it; it does not read the class's fields, create it, or describe more of it than "any JSON value". A class
 * without this annotation is read field by field (DESIGN.md, "Decoding contract").
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonForms {
    /** The codec, created by the host with its no-argument constructor. */
    Class<? extends Codec<?>> value();

    /**
     * Reads and writes one class's JSON forms as plain values: {@code Map<String, Object>}, {@code List<Object>},
     * {@code Double} for every number, {@code String} and {@code Boolean}.
     */
    interface Codec<T> {
        /**
         * The value {@code json} writes; never null, since a member written as {@code null} counts as left out.
         *
         * @param parts reads a part of a form that is a type of its own, such as a {@code NoiseStyle}
         * @throws IllegalArgumentException when {@code json} is none of the forms, saying which forms there are
         */
        T read(Object json, Parts parts);

        /** {@code value} as plain values {@link #read} reads back as an equal value. */
        Object write(T value);
    }

    /** Reads a part of a form exactly as the host reads that type anywhere else, so a style in a form may name a pack's expression. */
    interface Parts {
        /**
         * {@code json}, as plain values, read as a {@code type}: a settings class, a value type or another
         * {@code @JsonForms} class.
         *
         * @throws IllegalArgumentException when {@code json} does not read as a {@code type}
         */
        <V> V read(Object json, Class<V> type);
    }
}
