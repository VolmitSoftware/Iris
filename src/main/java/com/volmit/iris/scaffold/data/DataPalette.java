package com.volmit.iris.scaffold.data;

import com.volmit.iris.util.KList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DataPalette<T> {
    private final KList<T> palette;

    public DataPalette()
    {
        this(new KList<>(16));
    }

    public DataPalette(KList<T> palette)
    {
        this.palette = palette;
    }

    public KList<T> getPalette()
    {
        return palette;
    }

    public int getIndex(T t)
    {
        int v = 0;

       synchronized (palette)
       {
           v = palette.indexOf(t);

           if(v == -1)
           {
               v = palette.size();
               palette.add(t);
           }
       }

        return v;
    }

    public void write(IOAdapter<T> adapter, DataOutputStream dos) throws IOException
    {
        synchronized (palette)
        {
            dos.writeShort(getPalette().size() + Short.MIN_VALUE);

            for(int i = 0; i < palette.size(); i++)
            {
                adapter.write(palette.get(i), dos);
            }
        }
    }

    public static <T> DataPalette<T> getPalette(IOAdapter<T> adapter, DataInputStream din) throws IOException
    {
        KList<T> palette = new KList<>();
        int s = din.readShort() - Short.MIN_VALUE;

        for(int i = 0; i < s; i++)
        {
            palette.add(adapter.read(din));
        }

        return new DataPalette<>(palette);
    }
}
