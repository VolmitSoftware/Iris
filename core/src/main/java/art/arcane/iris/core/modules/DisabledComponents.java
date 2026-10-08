/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.core.modules;

import art.arcane.iris.spi.IrisLogging;
import com.volmit.host.ResolvedStage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The names in a dimension's {@code disabledComponents}: the flag of one of Iris's own mantle stages
 * ({@code CARVED}) or the id of any mantle stage ({@code example:moss/veins}).
 */
final class DisabledComponents {
    private final Set<String> names;

    DisabledComponents(Collection<String> names) {
        this.names = Set.copyOf(names);
    }

    boolean disables(ResolvedStage stage) {
        return names.stream().anyMatch(name -> isNamed(stage, name));
    }

    /** The names that match none of {@code stages}, sorted. */
    List<String> unknown(List<ResolvedStage> stages) {
        return names.stream().filter(name -> stages.stream().noneMatch(stage -> isNamed(stage, name))).sorted().toList();
    }

    void warnAboutUnknown(List<ResolvedStage> stages) {
        for (String name : unknown(stages)) {
            IrisLogging.warn("disabledComponents names \"%s\", which is neither a mantle flag of Iris (%s) nor the id of a mantle stage of the pack; ignored.",
                    name, MantleStageFlags.builtIn());
        }
    }

    private static boolean isNamed(ResolvedStage stage, String name) {
        return stage.stage().id().toString().equals(name)
                || MantleStageFlags.builtInFlag(stage.stage().id()).filter(flag -> flag.name().equals(name)).isPresent();
    }
}
