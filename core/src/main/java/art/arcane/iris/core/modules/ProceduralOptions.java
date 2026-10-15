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

import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;

/** The options a procedural object is scattered with, as Iris's own placement. */
public final class ProceduralOptions {
    private ProceduralOptions() {
    }

    public static IrisObjectPlacement of(ProceduralPlacement procedural) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(procedural.mode());
        placement.setRotation(irisRotation(procedural.rotation()));
        placement.setClamp(procedural.clamp());
        placement.setCarvingSupport(procedural.carvingSupport());
        placement.setUnderwater(procedural.underwater());
        placement.setTranslate(irisTranslate(procedural.translate()));
        placement.setStiltSettings(procedural.stiltSettings().orElse(null));
        placement.setVacuumSettings(procedural.vacuumSettings().orElse(null));
        placement.setChance(procedural.chance());
        placement.setDensity(procedural.density());
        placement.setSurfaceSupportBuffer(procedural.surfaceSupportBuffer());
        return placement;
    }

    private static IrisObjectRotation irisRotation(ObjectRotation rotation) {
        return new IrisObjectRotation()
                .setEnabled(rotation.enabled())
                .setXAxis(rotation.xAxis())
                .setYAxis(rotation.yAxis())
                .setZAxis(rotation.zAxis());
    }

    private static IrisObjectTranslate irisTranslate(ObjectTranslate translate) {
        return new IrisObjectTranslate(translate.x(), translate.y(), translate.yRandom(), translate.z());
    }
}
