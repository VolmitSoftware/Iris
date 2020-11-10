package com.volmit.iris.scaffold.stream;

public abstract class BasicStream<T> extends BasicLayer implements ProceduralStream<T>
{
    private final ProceduralStream<T> source;

    public BasicStream(ProceduralStream<T> source)
    {
        super();
        this.source = source;
    }

    public BasicStream()
    {
        this(null);
    }


    @Override
    public ProceduralStream<T> getTypedSource() {
        return source;
    }

    @Override
    public ProceduralStream<?> getSource() {
        return getTypedSource();
    }

    @Override
    public abstract T get(double x, double z);

    @Override
    public abstract T get(double x, double y, double z);

    @Override
    public abstract double toDouble(T t);

    @Override
    public abstract T fromDouble(double d);
}
