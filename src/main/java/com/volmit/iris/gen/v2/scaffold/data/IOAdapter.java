package com.volmit.iris.gen.v2.scaffold.data;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.Function3;

import java.io.*;

public interface IOAdapter<T>
{
    public void write(T t, DataOutputStream dos) throws IOException;

    public T read(DataInputStream din) throws IOException;
}
