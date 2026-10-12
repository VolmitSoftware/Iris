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

package art.arcane.iris.builtin.prepare;

import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.volmlib.util.math.IrisBlockVector;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** The spin kernel the placement stages turn blocks with gives exactly what Iris's rotation model gives. */
public class ObjectSpinKernelTest {
    private static final int[] SPINS = {0, 1, 4_096, 123_456, 2_147_483};
    private static final List<IrisBlockVector> POINTS = List.of(
            new IrisBlockVector(3, 5, -7), new IrisBlockVector(0, 2, 0), new IrisBlockVector(-4, 0, 9), new IrisBlockVector(11, -13, 2));

    @Test
    public void everyKindOfAxisTurnsVectorsAsTheRotationModelTurnsThem() {
        for (IrisObjectRotation model : rotations()) {
            ObjectRotation rotation = ObjectRotation.of(model.isEnabled(), model.getXAxis(), model.getYAxis(), model.getZAxis());
            for (int spin : SPINS) {
                ObjectSpinKernel kernel = new ObjectSpinKernel(rotation, spin, spin / 3, spin / 7);
                for (IrisBlockVector point : POINTS) {
                    IrisBlockVector expected = model.rotate(point, spin, spin / 3, spin / 7);
                    IrisBlockVector actual = point.clone();
                    kernel.rotate(actual);
                    String where = model + " spin " + spin + " at " + point;
                    assertEquals(where, expected.getX(), actual.getX(), 0);
                    assertEquals(where, expected.getY(), actual.getY(), 0);
                    assertEquals(where, expected.getZ(), actual.getZ(), 0);
                }
            }
        }
    }

    @Test
    public void aDisabledRotationLeavesVectorsAlone() {
        ObjectRotation rotation = ObjectRotation.of(false, AxisRotationClamp.locked(true, 90, 90), AxisRotationClamp.locked(true, 90, 90),
                AxisRotationClamp.locked(true, 90, 90));
        IrisBlockVector point = new IrisBlockVector(3, 5, -7);

        new ObjectSpinKernel(rotation, 5, 6, 7).rotate(point);

        assertEquals(new IrisBlockVector(3, 5, -7), point);
    }

    private static List<IrisObjectRotation> rotations() {
        IrisObjectRotation free = new IrisObjectRotation();
        free.setXAxis(AxisRotationClamp.of(true, false, 0, 0, 15));
        free.setYAxis(AxisRotationClamp.of(true, false, 0, 0, 7));
        free.setZAxis(AxisRotationClamp.of(true, false, -40, 40, 5));
        return List.of(new IrisObjectRotation(), IrisObjectRotation.of(0, 90, 0), IrisObjectRotation.of(0, 180, 0),
                IrisObjectRotation.of(0, -90, 0), IrisObjectRotation.of(90, 270, 180), IrisObjectRotation.of(-270, 0, 90),
                IrisObjectRotation.xFlip180(), IrisObjectRotation.xFlip180RandomY(), IrisObjectRotation.xFlip180WithY(270), free);
    }
}
