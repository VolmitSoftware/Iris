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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.C;
import java.util.function.Consumer;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.M;

/**
 * Derives human readable timing reports and the rolling generation rate for an {@link IrisEngine}.
 * Purely a reader of the engine's metric accumulators; it never mutates generation state.
 */
final class EngineMetricsReport {
    private final IrisEngine engine;

    EngineMetricsReport(IrisEngine engine) {
        this.engine = engine;
    }

    void printMetrics(Consumer<String> output) {
        KMap<String, Double> totals = new KMap<>();
        KMap<String, Double> weights = new KMap<>();
        double masterWallClock = engine.getWallClock().getAverage();
        KMap<String, Double> timings = engine.getMetrics().pull();
        double totalWeight = 0;
        double wallClock = engine.getMetrics().getTotal().getAverage();

        for (double j : timings.values()) {
            totalWeight += j;
        }

        for (String j : timings.k()) {
            weights.put(engine.getName() + "." + j, (wallClock / totalWeight) * timings.get(j));
        }

        totals.put(engine.getName(), wallClock);

        double mtotals = 0;

        for (double i : totals.values()) {
            mtotals += i;
        }

        for (String i : totals.k()) {
            totals.put(i, (masterWallClock / mtotals) * totals.get(i));
        }

        double v = 0;

        for (double i : weights.values()) {
            v += i;
        }

        for (String i : weights.k()) {
            weights.put(i, weights.get(i) / v);
        }

        output.accept(IrisLanguage.text(BukkitRuntimeMessages.IRIS_ENGINE_TOTAL, MessageArgument.untrusted("value", String.valueOf(Form.duration(masterWallClock, 0)))));

        for (String i : totals.k()) {
            output.accept(IrisLanguage.text(BukkitRuntimeMessages.IRIS_ENGINE_ENGINE, MessageArgument.untrusted("i", String.valueOf(i)), MessageArgument.untrusted("value", String.valueOf(Form.duration(totals.get(i), 0)))));
        }

        output.accept(IrisLanguage.text(BukkitRuntimeMessages.IRIS_ENGINE_DETAILS));

        for (String i : weights.sortKNumber().reverse()) {
            String befb = C.UNDERLINE + "" + C.GREEN + "" + i.split("\\Q[\\E")[0] + C.RESET + C.GRAY + "[";
            String num = C.GOLD + i.split("\\Q[\\E")[1].split("]")[0] + C.RESET + C.GRAY + "].";
            String afb = C.ITALIC + "" + C.AQUA + i.split("\\Q]\\E")[1].substring(1) + C.RESET + C.GRAY;

            output.accept(IrisLanguage.text(BukkitRuntimeMessages.IRIS_ENGINE_MESSAGE, MessageArgument.untrusted("befb", String.valueOf(befb)), MessageArgument.untrusted("num", String.valueOf(num)), MessageArgument.untrusted("afb", String.valueOf(afb)), MessageArgument.untrusted("value", String.valueOf(Form.pc(weights.get(i), 0)))));
        }
    }

    double getGeneratedPerSecond() {
        if (engine.getPerSecondLatch().flip()) {
            double g = engine.getGenerated() - engine.getGeneratedLast().get();
            engine.getGeneratedLast().set(engine.getGenerated());

            if (g == 0) {
                return 0;
            }

            long dur = M.ms() - engine.getLastGPS().get();
            engine.getLastGPS().set(M.ms());
            engine.getPerSecond().set(g / ((double) (dur) / 1000D));
        }

        return engine.getPerSecond().get();
    }
}
