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

import com.volmit.host.ResolvedStage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** The names in a dimension's {@code disabledComponents}: ids of mantle stages, such as {@code iris:carve/caves}. */
final class DisabledComponents {
    private final Set<String> names;

    DisabledComponents(Collection<String> names) {
        this.names = Set.copyOf(names);
    }

    boolean disables(ResolvedStage stage) {
        return names.contains(stage.stage().id().toString());
    }

    /** The names that match none of {@code stages}, sorted. */
    List<String> unknown(List<ResolvedStage> stages) {
        List<String> stageIds = MantleStageFlags.stageIds(stages);
        return names.stream().filter(name -> !stageIds.contains(name)).sorted().toList();
    }

    /** Fails when a name matches no mantle stage of the pack, listing the ids it could name. */
    void requireKnown(List<ResolvedStage> stages) {
        List<String> unknown = unknown(stages);
        if (unknown.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException("disabledComponents names " + unknown + ", which "
                + (unknown.size() == 1 ? "is not the id" : "are not ids") + " of mantle stages of the pack: "
                + MantleStageFlags.stageIds(stages));
    }
}
