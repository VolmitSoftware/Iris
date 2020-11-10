package com.volmit.iris.scaffold.data;

import java.io.*;

public interface IOAdapter<T>
{
    public void write(T t, DataOutputStream dos) throws IOException;

    public T read(DataInputStream din) throws IOException;
}
