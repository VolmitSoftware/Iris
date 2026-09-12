/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack.validation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;

import java.util.ArrayList;
import java.util.List;

/**
 * Registrant reference pools filtered by the version-content gate. A reference to a registrant the gate EXCLUDED is
 * dropped from every pool that could pick it and reported once against the containing registrant; a container whose
 * required pool ends up empty cascades to EXCLUDED through {@link #cascade}. Cascade and drop findings reuse the
 * registry and key of the cause so the console groups everything under the one missing content key.
 * <p>
 * Surviving entries keep their declaration order, so only the removed entries change what a pool picks.
 */
public final class CompatPools {
    private CompatPools() {
    }

    static boolean excluded(IrisRegistrant registrant) {
        return registrant != null && registrant.isCompatExcluded();
    }

    /**
     * Loads a registrant reference pool, skipping entries that fail to load and entries the gate excluded. Each
     * excluded reference produces one DROPPED finding in {@code sink} (when given) and in the pack report.
     *
     * @param field the JSON field the keys came from, used for the finding detail ({@code landBiomes[2] key})
     */
    public static <T extends IrisRegistrant> KList<T> load(ResourceLoader<T> loader,
                                                    List<String> keys,
                                                    IrisData data,
                                                    String subjectType,
                                                    String subjectKey,
                                                    String field,
                                                    List<CompatFinding> sink) {
        KList<T> kept = new KList<>();

        if (loader == null || keys == null) {
            return kept;
        }

        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            T loaded = loader.load(key);

            if (loaded == null) {
                continue;
            }

            if (loaded.isCompatExcluded()) {
                drop(data, loaded, subjectType, subjectKey, field + "[" + index + "] " + key, sink);
                continue;
            }

            kept.add(loaded);
        }

        return kept;
    }

    /** The surviving keys of a reference pool, in declaration order. */
    public static <T extends IrisRegistrant> KList<String> surviving(ResourceLoader<T> loader,
                                                              List<String> keys,
                                                              IrisData data,
                                                              String subjectType,
                                                              String subjectKey,
                                                              String field) {
        return surviving(loader, keys, data, subjectType, subjectKey, field, null);
    }

    public static <T extends IrisRegistrant> KList<String> surviving(ResourceLoader<T> loader,
                                                              List<String> keys,
                                                              IrisData data,
                                                              String subjectType,
                                                              String subjectKey,
                                                              String field,
                                                              Runnable unresolvedReference) {
        KList<String> kept = new KList<>();

        if (keys == null) {
            return kept;
        }

        if (loader == null) {
            kept.addAll(keys);
            return kept;
        }

        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            T loaded = loader.load(key);
            if (loaded == null && unresolvedReference != null) {
                unresolvedReference.run();
            }

            if (loaded != null && loaded.isCompatExcluded()) {
                drop(data, loaded, subjectType, subjectKey, field + "[" + index + "] " + key, null);
                continue;
            }

            kept.add(key);
        }

        return kept;
    }

    /**
     * Records one DROPPED finding for a reference to an excluded registrant, reusing the registry and key of the
     * child's exclusion cause. Returns null when the child carries no cause to attribute the drop to.
     */
    public static CompatFinding drop(IrisData data,
                              IrisRegistrant child,
                              String subjectType,
                              String subjectKey,
                              String detail,
                              List<CompatFinding> sink) {
        CompatFinding cause = causeOf(child == null ? null : child.getCompat());

        if (cause == null) {
            return null;
        }

        CompatFinding finding = new CompatFinding(cause.registry(), cause.key(), CompatAction.DROPPED,
                subjectType, subjectKey, detail);
        record(data, finding);

        if (sink != null) {
            sink.add(finding);
        }

        return finding;
    }

    /**
     * The EXCLUDED status for a container whose required pool lost every entry. Returns {@code base} untouched when no
     * cause was collected, so a container that never declared the pool is not excluded for being empty.
     */
    public static CompatStatus cascade(IrisData data,
                                CompatStatus base,
                                List<CompatFinding> causes,
                                String subjectType,
                                String subjectKey,
                                String detail) {
        if (causes == null || causes.isEmpty()) {
            return base;
        }

        CompatFinding cause = causes.getFirst();
        CompatFinding finding = new CompatFinding(cause.registry(), cause.key(), CompatAction.EXCLUDED,
                subjectType, subjectKey, detail);
        record(data, finding);

        List<CompatFinding> reasons = new ArrayList<>(base == null ? List.of() : base.reasons());
        reasons.add(finding);
        return CompatStatus.excludedBy(reasons);
    }

    /** The findings on a status that dropped an entry, used as the cause set of a cascade. */
    public static List<CompatFinding> droppedReasons(CompatStatus status) {
        List<CompatFinding> dropped = new ArrayList<>();

        if (status == null) {
            return dropped;
        }

        for (CompatFinding reason : status.reasons()) {
            if (reason.action() == CompatAction.DROPPED) {
                dropped.add(reason);
            }
        }

        return dropped;
    }

    static void record(IrisData data, CompatFinding finding) {
        if (data == null || finding == null) {
            return;
        }

        try {
            data.getCompatReport().record(finding);
        } catch (Throwable ignored) {
            // A report is best effort: never let reporting break a pool build.
        }
    }

    /** The finding that explains an exclusion: the EXCLUDED reason if there is one, otherwise the first reason. */
    private static CompatFinding causeOf(CompatStatus status) {
        if (status == null) {
            return null;
        }

        CompatFinding fallback = null;

        for (CompatFinding reason : status.reasons()) {
            if (reason.action() == CompatAction.EXCLUDED) {
                return reason;
            }

            if (fallback == null) {
                fallback = reason;
            }
        }

        return fallback;
    }
}
