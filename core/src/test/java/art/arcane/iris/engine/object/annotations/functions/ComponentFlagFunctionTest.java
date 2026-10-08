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

package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/** Studio offers the flags of Iris's own mantle stages for a dimension's disabledComponents, caves included. */
public class ComponentFlagFunctionTest {
    @Test
    public void offersTheFlagsOfIrisMantleStagesInTheirOrder() {
        assertEquals(List.of("CARVED", "OBJECT", "FLOATING_OBJECT", "JIGSAW"), new ComponentFlagFunction().apply(mock(IrisData.class)));
    }
}
