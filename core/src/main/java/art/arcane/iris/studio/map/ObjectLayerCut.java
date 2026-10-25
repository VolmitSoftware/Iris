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

package art.arcane.iris.studio.map;

import javax.swing.JComponent;
import java.util.function.IntConsumer;

/**
 * A slider floating on the right of the 3D view that hides an object's top layers, to look inside buildings floor by
 * floor. Most objects never need it, so it stays faint until the mouse comes near or a cut is active.
 */
final class ObjectLayerCut {
    private static final String TOOLTIP = "Hide the top layers to look inside · Page Up / Page Down";

    private final FloatingSlider slider = new FloatingSlider(TOOLTIP, this::showCut);
    private final IntConsumer onLayersChanged;

    /**
     * @param onLayersChanged told how many layers from the bottom stay visible whenever the user moves the slider
     */
    ObjectLayerCut(IntConsumer onLayersChanged) {
        this.onLayersChanged = onLayersChanged;
        showHeight(1);
    }

    /**
     * The view with this slider floating over its right edge, halfway down.
     */
    JComponent floatingOver(JComponent view) {
        return slider.floatingOver(view);
    }

    /**
     * Shows every layer of a newly chosen object without telling anyone, since nothing is hidden yet.
     */
    void showHeight(int layers) {
        int layerCount = Math.max(1, layers);
        slider.showQuietly(1, layerCount, layerCount);
        showCutState();
        slider.setVisible(layers > 1);
    }

    void stepBy(int layers) {
        if (slider.isVisible()) {
            slider.moveBy(layers);
        }
    }

    boolean isCutting() {
        return slider.value() < slider.maximum();
    }

    int shownLayerCount() {
        return slider.value();
    }

    int layerCount() {
        return slider.maximum();
    }

    private void showCut(int shownLayers) {
        showCutState();
        onLayersChanged.accept(shownLayers);
    }

    private void showCutState() {
        slider.showLabel(isCutting() ? Integer.toString(slider.value()) : "");
        slider.setStandingOut(isCutting());
    }
}
