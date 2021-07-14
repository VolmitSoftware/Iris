package com.volmit.iris.scaffold.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface IOAdapter<T> {
    void write(T t, DataOutputStream dos) throws IOException;

    T read(DataInputStream din) throws IOException;
}
