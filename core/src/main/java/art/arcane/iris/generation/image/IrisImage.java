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

package art.arcane.iris.generation.image;

import art.arcane.iris.pack.loading.IrisRegistrant;

import java.awt.image.ColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.util.Objects;

public final class IrisImage extends IrisRegistrant {
    private final BufferedImage image;
    private final Raster raster;
    private final ColorModel colorModel;
    private final String format;

    public IrisImage() {
        this(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png");
    }

    public IrisImage(BufferedImage image) {
        this(image, "png");
    }

    public IrisImage(BufferedImage image, String format) {
        this.image = Objects.requireNonNull(image, "IrisImage requires a decoded image (the source file was unreadable or not an image)");
        this.raster = image.getRaster();
        this.colorModel = image.getColorModel();
        this.format = Objects.requireNonNull(format, "Image format").toLowerCase();
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public String getFormat() {
        return format;
    }

    public IrisImageColorMode getColorMode() {
        if (colorModel instanceof IndexColorModel) {
            return IrisImageColorMode.INDEXED;
        }
        int colorComponents = colorModel.getNumColorComponents();
        if (colorComponents == 1) {
            return colorModel.hasAlpha() ? IrisImageColorMode.UNSUPPORTED : IrisImageColorMode.GRAYSCALE;
        }
        if (colorComponents == 3) {
            return colorModel.hasAlpha() ? IrisImageColorMode.RGBA : IrisImageColorMode.RGB;
        }
        return IrisImageColorMode.UNSUPPORTED;
    }

    public int getColorComponentCount() {
        return colorModel.getNumColorComponents();
    }

    public int getChannelCount() {
        return raster.getNumBands();
    }

    public int getBitDepth() {
        int[] sampleSizes = raster.getSampleModel().getSampleSize();
        int maximum = 0;
        int colorBands = Math.min(getColorComponentCount(), sampleSizes.length);
        for (int index = 0; index < colorBands; index++) {
            maximum = Math.max(maximum, sampleSizes[index]);
        }
        return maximum;
    }

    public boolean hasAlpha() {
        return colorModel.hasAlpha();
    }

    public double getBandNormalized(int x, int z, int band) {
        requireCoordinate(x, z);
        if (band < 0 || band >= raster.getNumBands()) {
            throw new IllegalArgumentException("Image band " + band + " is outside 0.." + (raster.getNumBands() - 1));
        }
        int bits = raster.getSampleModel().getSampleSize(band);
        long maximum = (1L << bits) - 1L;
        return raster.getSample(x, z, band) / (double) maximum;
    }

    public int getBandSample(int x, int z, int band) {
        requireCoordinate(x, z);
        if (band < 0 || band >= raster.getNumBands()) {
            throw new IllegalArgumentException("Image band " + band + " is outside 0.." + (raster.getNumBands() - 1));
        }
        return raster.getSample(x, z, band);
    }

    public double getAlphaNormalized(int x, int z) {
        requireCoordinate(x, z);
        if (!hasAlpha()) {
            return 1D;
        }
        int alphaBand = raster.getNumBands() - 1;
        return getBandNormalized(x, z, alphaBand);
    }

    public int getRawRgb8(int x, int z) {
        requireCoordinate(x, z);
        IrisImageColorMode mode = getColorMode();
        if (mode != IrisImageColorMode.RGB && mode != IrisImageColorMode.RGBA) {
            throw new IllegalStateException("Raw RGB sampling requires an RGB or RGBA image, not " + mode);
        }
        int red = scaleTo8Bit(x, z, 0);
        int green = scaleTo8Bit(x, z, 1);
        int blue = scaleTo8Bit(x, z, 2);
        return red << 16 | green << 8 | blue;
    }

    public int getPreviewArgb(int x, int z) {
        requireCoordinate(x, z);
        return image.getRGB(x, z);
    }

    private int scaleTo8Bit(int x, int z, int band) {
        int bits = raster.getSampleModel().getSampleSize(band);
        int sample = raster.getSample(x, z, band);
        if (bits == 8) {
            return sample;
        }
        long maximum = (1L << bits) - 1L;
        return (int) Math.round(sample * 255D / maximum);
    }

    private void requireCoordinate(int x, int z) {
        if (x < 0 || z < 0 || x >= getWidth() || z >= getHeight()) {
            throw new IndexOutOfBoundsException("Image coordinate " + x + "," + z + " is outside " + getWidth() + "x" + getHeight());
        }
    }

    @Override
    public String getFolderName() {
        return "images";
    }

    @Override
    public String getTypeName() {
        return "Image";
    }

}
