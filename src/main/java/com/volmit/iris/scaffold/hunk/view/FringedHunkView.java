package com.volmit.iris.scaffold.hunk.view;

import com.volmit.iris.scaffold.hunk.Hunk;

public class FringedHunkView<T> implements Hunk<T> {
    private final Hunk<T> src;
    private final Hunk<T> out;

    public FringedHunkView(Hunk<T> src, Hunk<T> out)
    {
        this.src = src;
        this.out = out;
    }

    @Override
    public void setRaw(int x, int y, int z, T t)
    {
        out.setRaw(x,y,z,t);
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
