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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;

/**
 * A frame drawn straight into a view's window. Of what was drawn, only the patches Swing paints where panels float
 * over the window are read back. Only for the GPU thread.
 */
final class GpuWindowFrame implements GpuFrameTarget {
    private static final int WINDOW_FRAMEBUFFER = 0;

    private final GlxDisplay windows;
    private final long window;
    private final List<Rectangle> seeThrough;
    private int width;
    private int height;

    /**
     * @param seeThrough the parts of the view panels float over, which Swing paints instead of the window
     */
    GpuWindowFrame(GlxDisplay windows, long window, List<Rectangle> seeThrough) {
        this.windows = windows;
        this.window = window;
        this.seeThrough = seeThrough;
    }

    // The context was first made current without any window, which left it drawing into no buffer; NVIDIA's driver
    // keeps that once a window comes along.
    @Override
    public void bindSized(int nextWidth, int nextHeight) {
        width = nextWidth;
        height = nextHeight;
        glBindFramebuffer(GL_FRAMEBUFFER, WINDOW_FRAMEBUFFER);
        glDrawBuffer(GL_BACK);
        glReadBuffer(GL_BACK);
        glViewport(0, 0, width, height);
    }

    @Override
    public OrbitPicture finish(OrbitProjection projection, double depthRange) {
        OrbitPicture picture = OrbitPicture.onScreen(width, height, readPatches(),
                GpuFrameTarget.depthInMiddle(projection, depthRange, width, height));
        if (!windows.show(window)) {
            throw new IllegalStateException("The window didn't take the frame");
        }
        return picture;
    }

    private List<PicturePatch> readPatches() {
        Rectangle frame = new Rectangle(0, 0, width, height);
        List<PicturePatch> patches = new ArrayList<>(seeThrough.size());
        for (Rectangle part : seeThrough) {
            Rectangle inFrame = part.intersection(frame);
            if (!inFrame.isEmpty()) {
                patches.add(read(inFrame));
            }
        }
        return patches;
    }

    // Rows come bottom up.
    private PicturePatch read(Rectangle area) {
        int[] rows = new int[area.width * area.height];
        glReadPixels(area.x, height - area.y - area.height, area.width, area.height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, rows);
        BufferedImage image = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < area.height; row++) {
            System.arraycopy(rows, (area.height - 1 - row) * area.width, pixels, row * area.width, area.width);
        }
        return new PicturePatch(area.x, area.y, image);
    }
}
