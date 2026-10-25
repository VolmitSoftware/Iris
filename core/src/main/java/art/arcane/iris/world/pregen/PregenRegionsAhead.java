package art.arcane.iris.world.pregen;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

final class PregenRegionsAhead {
    private final Iterator<long[]> order;
    private final Predicate<long[]> done;
    private final ArrayDeque<Pending> buffered = new ArrayDeque<>();
    private Pending current;
    private Pending announced;

    PregenRegionsAhead(Iterator<long[]> order, Predicate<long[]> done) {
        this.order = Objects.requireNonNull(order, "order");
        this.done = Objects.requireNonNull(done, "done");
    }

    long[] next() {
        if (!buffered.isEmpty()) {
            current = buffered.pollFirst();
            return current.region();
        }
        current = order.hasNext() ? new Pending(order.next()) : null;
        return current == null ? null : current.region();
    }

    List<long[]> starting() {
        if (current == null || current == announced || current.done(done)) {
            return List.of();
        }
        announced = current;
        return List.of(current.region());
    }

    List<long[]> following() {
        Pending pending = nextPending();
        if (pending == null || pending == announced) {
            return List.of();
        }
        announced = pending;
        return List.of(pending.region());
    }

    private Pending nextPending() {
        for (Pending pending : buffered) {
            if (!pending.done(done)) {
                return pending;
            }
        }
        while (order.hasNext()) {
            Pending pending = new Pending(order.next());
            buffered.addLast(pending);
            if (!pending.done(done)) {
                return pending;
            }
        }
        return null;
    }

    private static final class Pending {
        private final long[] region;
        private Boolean done;

        private Pending(long[] region) {
            this.region = region;
        }

        private long[] region() {
            return region;
        }

        private boolean done(Predicate<long[]> test) {
            if (done == null) {
                done = test.test(region);
            }
            return done;
        }
    }
}
