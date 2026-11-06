package com.volmit.data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Value serialization for data the host stores for a module: mantle layers ({@link com.volmit.mantle.MantleKey}) and
 * saved world state ({@link SavedKey}). The host frames payloads and stores the key ID and version
 * separately. Implementations must support concurrent calls, must not close streams, and must reject malformed data or
 * unsupported stored versions with IOException. Values cannot be null.
 */
public interface StorageCodec<T> {
    /** Nonnegative format version, stable for the registration lifetime. */
    int version();

    void write(DataOutput output, T value) throws IOException;

    T read(DataInput input, int storedVersion) throws IOException;
}
