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

import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import com.volmit.host.ResolvedStage;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;

/**
 * The mantle flag of each mantle stage, which marks a mantle chunk whose stage has run. Every stage gets a custom
 * flag in resolved order, so adding or removing a mantle stage can renumber the stages after it. The flags stay
 * internal: a dimension names a stage in {@code disabledComponents} by its id.
 */
public final class MantleStageFlags {
    private MantleStageFlags() {
    }

    /** The ids of {@code stages}, which a dimension names in {@code disabledComponents}. */
    public static List<String> stageIds(List<ResolvedStage> stages) {
        return stages.stream().map(stage -> stage.stage().id()).map(ResourceId::toString).toList();
    }

    /** One flag per stage, in the order of {@code stages}. */
    static List<MantleFlag> assign(List<ResolvedStage> stages) {
        List<MantleFlag> flags = new ArrayList<>(stages.size());
        int nextCustom = MantleFlag.MIN_ORDINAL;
        for (ResolvedStage stage : stages) {
            if (nextCustom > MantleFlag.MAX_ORDINAL) {
                throw new IllegalStateException("The pack has more mantle stages than Iris has mantle flags for; stage "
                        + stage.stage().id() + " of module " + stage.module() + " is one too many");
            }
            flags.add(MantleFlag.of(nextCustom++));
        }
        return List.copyOf(flags);
    }
}
