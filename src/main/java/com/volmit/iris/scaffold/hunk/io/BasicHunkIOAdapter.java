package com.volmit.iris.scaffold.hunk.io;

import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.Function3;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BasicHunkIOAdapter<T> implements HunkIOAdapter<T> {
    @Override
    public void write(Hunk<T> t, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeShort(t.getWidth() + Short.MIN_VALUE);
        dos.writeShort(t.getHeight() + Short.MIN_VALUE);
        dos.writeShort(t.getDepth() + Short.MIN_VALUE);
        dos.writeInt(t.getNonNullEntries() + Integer.MIN_VALUE);

        AtomicBoolean failure = new AtomicBoolean(false);
        t.iterate(0, (x,y,z,w) -> {
            if(w != null)
            {
                try
                {
                    dos.writeShort(x + Short.MIN_VALUE);
                    dos.writeShort(y + Short.MIN_VALUE);
                    dos.writeShort(z + Short.MIN_VALUE);
                    write(w, dos);
                }

                catch(Throwable e)
                {
                    e.printStackTrace();
                    failure.set(true);
                }
            }
        });

        dos.close();
    }

    @Override
    public Hunk<T> read(Function3<Integer,Integer,Integer,Hunk<T>> factory, InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        int w = din.readShort() - Short.MIN_VALUE;
        int h = din.readShort() - Short.MIN_VALUE;
        int d = din.readShort() - Short.MIN_VALUE;
        int e = din.readInt() - Integer.MIN_VALUE;
        Hunk<T> t = factory.apply(w, h, d);

        for(int i = 0; i < e; i++)
        {
            int x = din.readShort() - Short.MIN_VALUE;
            int y = din.readShort() - Short.MIN_VALUE;
            int z = din.readShort() - Short.MIN_VALUE;
            T v = read(din);

            if(v == null)
            {
                throw new IOException("NULL VALUE AT " + x + " " + y + " " + z);
            }

            t.setRaw(x,y,z, v);
        }

        in.close();
        return t;
    }
}
