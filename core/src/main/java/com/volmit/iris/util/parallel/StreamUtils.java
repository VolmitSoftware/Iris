package com.volmit.iris.util.parallel;

import com.volmit.iris.util.math.Position2;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StreamUtils {

    public static Stream<Position2> streamRadius(int x, int z, int radius) {
        return streamRadius(x, z, radius, radius);
    }

    public static Stream<Position2> streamRadius(int x, int z, int radiusX, int radiusZ) {
        return IntStream.rangeClosed(-radiusX, radiusX)
                .mapToObj(xx -> IntStream.rangeClosed(-radiusZ, radiusZ)
                        .mapToObj(zz -> new Position2(x + xx, z + zz)))
                .flatMap(Function.identity());
    }

    public static <T, M> void forEach(Stream<T> stream, Function<T, Stream<M>> mapper, Consumer<M> consumer, @Nullable MultiBurst burst) {
        forEach(stream.flatMap(mapper), consumer, burst);
    }

    @SneakyThrows
    public static <T> void forEach(Stream<T> stream, Consumer<T> task, @Nullable MultiBurst burst) {
        if (burst == null) stream.forEach(task);
        else {
            var list = stream.toList();
            var exec = burst.burst(list.size());
            list.forEach(val -> exec.queue(() -> task.accept(val)));
            exec.complete();
        }
    }
}
