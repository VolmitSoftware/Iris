/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import art.arcane.iris.spi.IrisLogging;

import java.awt.EventQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Measures off the Swing thread, one measurement at a time, and only hands back the newest one asked for.
 */
final class LatestMeasurement<T> {
    private final ThreadPoolExecutor worker;
    private final AtomicLong generation = new AtomicLong();

    LatestMeasurement(String threadName) {
        this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    /**
     * Runs the measurement and shows its result on the Swing thread, unless another was asked for meanwhile or it
     * measured nothing.
     */
    void measure(Supplier<T> measurement, Consumer<T> show) {
        measureUntilSuperseded(stillWanted -> measurement.get(), show);
    }

    /**
     * Like {@link #measure}, for long measurements that ask {@code stillWanted} between steps and give up once another
     * measurement was asked for.
     */
    void measureUntilSuperseded(Function<BooleanSupplier, T> measurement, Consumer<T> show) {
        long requested = generation.incrementAndGet();
        BooleanSupplier stillWanted = () -> requested == generation.get();
        worker.execute(() -> {
            T result = measureSafely(() -> measurement.apply(stillWanted));
            EventQueue.invokeLater(() -> {
                if (requested == generation.get() && result != null) {
                    show.accept(result);
                }
            });
        });
    }

    /**
     * Drops the measurement under way, whose result no longer fits.
     */
    void discardPending() {
        generation.incrementAndGet();
    }

    void close() {
        worker.shutdownNow();
    }

    private static <T> T measureSafely(Supplier<T> measurement) {
        try {
            return measurement.get();
        } catch (Throwable error) {
            IrisLogging.reportError(error);
            return null;
        }
    }
}
