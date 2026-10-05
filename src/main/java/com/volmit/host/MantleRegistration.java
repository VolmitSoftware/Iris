package com.volmit.host;

import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import java.util.Objects;

public record MantleRegistration<T>(
        MantleKey<T> key,
        MantleLifetime lifetime,
        MantleCodec<T> codec,
        int version) {
    public MantleRegistration {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(codec, "codec");
        if (version < 0) {
            throw new IllegalArgumentException("Negative mantle codec version");
        }
    }
}
