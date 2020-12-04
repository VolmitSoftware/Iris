package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

import java.util.concurrent.atomic.AtomicBoolean;

public class WriteTrackHunk<T> implements Hunk<T> {
    private final Hunk<T> src;
    private final AtomicBoolean b;

    public WriteTrackHunk(Hunk<T> src, AtomicBoolean b)
    {
        this.src = src;
        this.b = b;
    }

    @Override
    public void setRaw(int x, int y, int z, T t)
    {
        if(!b.get())
        {
            b.set(true);
        }

        src.setRaw(x,y,z,t);
    }

    @Override
    public T getRaw(int x, int y, int z)
    {
        return src.getRaw(x, y, z);
    }

    @Override
    public int getWidth()
    {
        return src.getWidth();
    }

    @Override
    public int getHeight()
    {
        return src.getHeight();
    }

    @Override
    public int getDepth()
    {
        return src.getDepth();
    }

    @Override
    public Hunk<T> getSource()
    {
        return src;
    }
}
