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

package com.volmit.iris.core.gui;

import java.awt.Color;

final class PaletteLayerScale implements NoiseColorScale {
    static final int ABSENT_COLOR = 0x161A24;
    private static final double OVERLAY_DARKEST_SHADE = 0.3D;

    private final View view;
    private final PaletteLayerPreview preview;
    private final NoisePalette noisePalette;
    private final boolean showTexturePixels;

    PaletteLayerScale(View view, PaletteLayerPreview preview, NoisePalette noisePalette, boolean showTexturePixels) {
        this.view = view;
        this.preview = preview;
        this.noisePalette = noisePalette;
        this.showTexturePixels = showTexturePixels;
    }

    @Override
    public int colorAt(double value, double worldX, double worldZ) {
        BlockTextures.BlockTexture texture = showTexturePixels && view != View.NOISE ? preview.textureForNoise(value) : null;
        if (texture == null) {
            return colorFinite(value);
        }
        int texel = texture.texelAt(worldX, worldZ);
        return view == View.BLOCKS ? texel : BlockPreviewColors.shade(texel, overlayShade(value));
    }

    @Override
    public int colorFinite(double value) {
        return switch (view) {
            case BLOCKS -> preview.colorForNoise(value);
            case NOISE -> noisePalette.colorFinite(value);
            case BLOCKS_SHADED_BY_NOISE -> BlockPreviewColors.shade(preview.colorForNoise(value), overlayShade(value));
        };
    }

    @Override
    public int colorInvalid() {
        return ABSENT_COLOR;
    }

    @Override
    public Color displayColorNormalized(double normalized) {
        return new Color(colorFinite(minimum() + normalized * (maximum() - minimum())));
    }

    @Override
    public String label() {
        return view == View.NOISE ? noisePalette.label() : view.toString();
    }

    @Override
    public double minimum() {
        return view == View.NOISE ? noisePalette.minimum() : 0D;
    }

    @Override
    public double maximum() {
        return view == View.NOISE ? noisePalette.maximum() : 1D;
    }

    private static double overlayShade(double value) {
        return OVERLAY_DARKEST_SHADE + (1D - OVERLAY_DARKEST_SHADE) * Math.clamp(value, 0D, 1D);
    }

    enum View {
        BLOCKS("Blocks"),
        NOISE("Noise"),
        BLOCKS_SHADED_BY_NOISE("Blocks + noise");

        private final String label;

        View(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
