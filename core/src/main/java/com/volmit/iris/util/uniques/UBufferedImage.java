package com.volmit.iris.util.uniques;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

public class UBufferedImage implements UImage {
    private final BufferedImage buf;

    public UBufferedImage(BufferedImage buf) {
        this.buf = buf;
    }

    @Override
    public int getWidth() {
        return buf.getWidth();
    }

    @Override
    public int getHeight() {
        return buf.getHeight();
    }

    @Override
    public UImage copy() {
        ColorModel cm = buf.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = buf.copyData(null);
        return new UBufferedImage(new BufferedImage(cm, raster, isAlphaPremultiplied, null));
    }

    @Override
    public Color get(int x, int y) {
        return new Color(buf.getRGB(x, y));
    }

    @Override
    public void set(int x, int y, Color color) {
        try {
            buf.setRGB(x, y, color.getRGB());
        } catch (Throwable e) {

        }
    }
}
