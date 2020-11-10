package com.volmit.iris.scaffold.engine;

public interface Fallible
{
    public default void fail(String error)
    {
        try
        {
            throw new RuntimeException();
        }

        catch(Throwable e)
        {
            fail(error, e);
        }
    }

    public default void fail(Throwable e)
    {
        fail("Failed to generate", e);
    }

    public void fail(String error, Throwable e);

    public boolean hasFailed();
}
