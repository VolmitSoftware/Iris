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

import art.arcane.iris.builtin.carve.CarveStages;
import art.arcane.iris.builtin.floating.FloatingStages;
import art.arcane.iris.builtin.objects.ObjectStages;
import art.arcane.iris.builtin.structure.StructureStages;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import com.volmit.host.ResolvedStage;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The mantle flag of each mantle stage: it marks a mantle chunk whose stage has run, and names the stage in a
 * dimension's {@code disabledComponents}. Iris's own mantle stages keep the flags their mantle components had,
 * so packs that disable {@code CARVED} keep doing so and existing worlds stay marked. Every other stage gets a
 * custom flag in resolved order, so adding or removing a pack mantle stage can renumber the stages after it.
 */
public final class MantleStageFlags {
    private static final List<BuiltIn> BUILT_IN = List.of(
            new BuiltIn(CarveStages.CAVES.id(), ReservedFlag.CARVED),
            new BuiltIn(ObjectStages.PLACE.id(), ReservedFlag.OBJECT),
            new BuiltIn(FloatingStages.OBJECTS.id(), ReservedFlag.FLOATING_OBJECT),
            new BuiltIn(StructureStages.PLACE.id(), ReservedFlag.JIGSAW));

    private MantleStageFlags() {
    }

    /** The flags of Iris's own mantle stages, in their order, which a dimension can list in {@code disabledComponents}. */
    public static List<MantleFlag> builtIn() {
        return BUILT_IN.stream().map(BuiltIn::flag).toList();
    }

    /** One flag per stage, in the order of {@code stages}. */
    static List<MantleFlag> assign(List<ResolvedStage> stages) {
        List<MantleFlag> flags = new ArrayList<>(stages.size());
        int nextCustom = MantleFlag.MIN_ORDINAL;
        for (ResolvedStage stage : stages) {
            Optional<MantleFlag> builtIn = builtInFlag(stage.stage().id());
            if (builtIn.isPresent()) {
                flags.add(builtIn.get());
                continue;
            }
            if (nextCustom > MantleFlag.MAX_ORDINAL) {
                throw new IllegalStateException("The pack has more mantle stages than Iris has mantle flags for; stage "
                        + stage.stage().id() + " of module " + stage.module() + " is one too many");
            }
            flags.add(MantleFlag.of(nextCustom++));
        }
        return List.copyOf(flags);
    }

    private static Optional<MantleFlag> builtInFlag(ResourceId stage) {
        return BUILT_IN.stream().filter(builtIn -> builtIn.stage().equals(stage)).map(BuiltIn::flag).findFirst();
    }

    private record BuiltIn(ResourceId stage, MantleFlag flag) {
    }
}
