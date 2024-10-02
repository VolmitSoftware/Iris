package com.volmit.iris.engine.framework;

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.apache.commons.lang3.function.TriFunction;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@FunctionalInterface
public interface ResultLocator<T> {
    static void cancelSearch() {
        if (LocatorCanceller.cancel != null) {
            LocatorCanceller.cancel.run();
            LocatorCanceller.cancel = null;
        }
    }

    static ResultLocator<IrisJigsawStructure> locateStructure(Collection<String> keys) {
        return (e, pos) -> {
            var structure = e.getStructureAt(pos.getX(), pos.getZ());
            return structure != null && keys.contains(structure.getLoadKey()) ? structure : null;
        };
    }

    static ResultLocator<IrisObject> locateObject(Collection<String> keys) {
        return (e, pos) -> {
            Set<String> objects = e.getObjectsAt(pos.getX(), pos.getZ());
            for (String object : objects) {
                if (!keys.contains(object)) continue;
                return e.getData().getObjectLoader().load(object);
            }
            return null;
        };
    }

    T find(Engine e, Position2 chunkPos);

    default <R> ResultLocator<R> then(TriFunction<Engine, Position2, T, R> filter) {
        return (e, pos) -> {
            var t = find(e, pos);
            return t != null ? filter.apply(e, pos, t) : null;
        };
    }

    default Future<Result<T>> find(Engine engine, Position2 pos, long timeout, Consumer<Integer> checks, boolean cancelable) throws WrongEngineBroException {
        if (engine.isClosed()) {
            throw new WrongEngineBroException();
        }

        cancelSearch();

        return MultiBurst.burst.completeValue(() -> {
            int tc = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()) * 17;
            MultiBurst burst = MultiBurst.burst;
            AtomicBoolean found = new AtomicBoolean(false);
            AtomicReference<Result<T>> foundObj = new AtomicReference<>();
            Position2 cursor = pos;
            AtomicInteger searched = new AtomicInteger();
            AtomicBoolean stop = new AtomicBoolean(false);
            PrecisionStopwatch px = PrecisionStopwatch.start();
            if (cancelable) LocatorCanceller.cancel = () -> stop.set(true);
            AtomicReference<Position2> next = new AtomicReference<>(cursor);
            Spiraler s = new Spiraler(100000, 100000, (x, z) -> next.set(new Position2(x, z)));
            s.setOffset(cursor.getX(), cursor.getZ());
            s.next();
            while (!found.get() && !stop.get() && px.getMilliseconds() < timeout) {
                BurstExecutor e = burst.burst(tc);

                for (int i = 0; i < tc; i++) {
                    Position2 p = next.get();
                    s.next();
                    e.queue(() -> {
                        var o = find(engine, p);
                        if (o != null) {
                            if (foundObj.get() == null) {
                                foundObj.set(new Result<>(o, p));
                            }

                            found.set(true);
                        }
                        searched.incrementAndGet();
                    });
                }

                e.complete();
                checks.accept(searched.get());
            }

            LocatorCanceller.cancel = null;

            if (found.get() && foundObj.get() != null) {
                return foundObj.get();
            }

            return null;
        });
    }

    record Result<T>(T obj, Position2 pos) {
        public int getBlockX() {
            return (pos.getX() << 4) + 8;
        }

        public int getBlockZ() {
            return (pos.getZ() << 4) + 8;
        }
    }
}
