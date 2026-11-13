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

package art.arcane.iris.modules.carve;

import com.volmit.resource.ResourceId;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;
import com.volmit.validation.ValidationContext;
import com.volmit.validation.Validator;

/**
 * Module {@code iris:carve}'s check of a pack's caves: every value lies within the numbers its key allows. A
 * {@link CaveValue} is not a plain number, so the host's own bounds check cannot see it.
 */
public final class CaveValidator implements Validator {
    private static final String JSON_EXTENSION = ".json";

    @Override
    public void validate(ValidationContext pack) {
        for (ResourceId id : pack.resources().ids(CaveResources.CAVES)) {
            Cave cave = CaveResources.cave(pack.resources(), id);
            if (cave == null) {
                continue;
            }
            Subject file = Subject.file(CaveResources.CAVES.folder() + "/" + id.path() + JSON_EXTENSION);
            cave.valuesOutOfBounds().forEach(problem -> pack.report(Problem.error(file, problem)));
        }
    }
}
