package art.arcane.iris.util.parallel;

import art.arcane.volmlib.util.parallel.StreamUtilsSupport;
import art.arcane.volmlib.util.math.Position2;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class StreamUtils {

    public static Stream<Position2> streamRadius(int x, int z, int radius) {
        return streamRadius(x, z, radius, radius);
    }

    public static Stream<Position2> streamRadius(int x, int z, int radiusX, int radiusZ) {
        return StreamUtilsSupport.streamRadius(x, z, radiusX, radiusZ)
                .map(p -> new Position2(p.getX(), p.getZ()));
    }

    public static <T, M> void forEach(Stream<T> stream, Function<T, Stream<M>> mapper, Consumer<M> consumer, @Nullable MultiBurst burst) {
        StreamUtilsSupport.forEach(stream, mapper, consumer, burst == null ? null : burst::burst);
    }

    public static <T> void forEach(Stream<T> stream, Consumer<T> task, @Nullable MultiBurst burst) {
        StreamUtilsSupport.forEach(stream, task, burst == null ? null : burst::burst);
    }
}
