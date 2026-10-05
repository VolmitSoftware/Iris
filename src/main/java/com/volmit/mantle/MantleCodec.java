package com.volmit.mantle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Value serialization. Host frames payloads and stores key ID and version separately.
 * Implementations must support concurrent calls, must not close streams, and must reject
 * malformed data or unsupported stored versions with IOException. Values cannot be null.
 */
public interface MantleCodec<T> {
    /** Nonnegative format version, stable for the registration lifetime. */
    int version();

    void write(DataOutput output, T value) throws IOException;

    T read(DataInput input, int storedVersion) throws IOException;
}
