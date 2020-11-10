package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class ReadOnlyHunk<T> implements Hunk<T> {
    private final Hunk<T> src;

    public ReadOnlyHunk(Hunk<T> src)
    {
        this.src = src;
    }

    @Override
    public void setRaw(int x, int y, int z, T t)
    {
        throw new IllegalStateException("This hunk is read only!");
    }

    @Override
    public T getRaw(int x, int y, int z)
    {
        return src.getRaw(x, y, z);
    }

    @Override
    public void set(int x1, int y1, int z1, int x2, int y2, int z2, T t)
    {
        throw new IllegalStateException("This hunk is read only!");
    }

    @Override
    public void fill(T t)
    {
        throw new IllegalStateException("This hunk is read only!");
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
