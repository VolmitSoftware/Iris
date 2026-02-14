package art.arcane.iris.util.math;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class Spiral implements Iterable<Position2> {
    private final Position2 start;
    private final long max;
    private final art.arcane.volmlib.util.math.Spiral delegate;

    public Spiral(Position2 start, long max) {
        this.start = start;
        this.max = max;
        this.delegate = new art.arcane.volmlib.util.math.Spiral(start, max);
    }

    public static Position2 next(Position2 p) {
        art.arcane.volmlib.util.math.Position2 n = art.arcane.volmlib.util.math.Spiral.next(p);
        return new Position2(n.getX(), n.getZ());
    }

    public static Spiral from(Position2 p, long iterations) {
        return new Spiral(p, iterations);
    }

    public Position2 getStart() {
        return start;
    }

    public long getMax() {
        return max;
    }

    @NotNull
    @Override
    public Iterator<Position2> iterator() {
        Iterator<art.arcane.volmlib.util.math.Position2> it = delegate.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Position2 next() {
                art.arcane.volmlib.util.math.Position2 p = it.next();
                return new Position2(p.getX(), p.getZ());
            }
        };
    }
}
