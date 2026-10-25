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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import static org.lwjgl.opengl.GL30.*;

/**
 * A picture the graphics card draws into instead of a window, read back as an {@link OrbitPicture} so Swing shows it
 * like any other. Only for the GPU thread.
 */
final class GpuFramebuffer implements GpuFrameTarget {
    private final int framebuffer = glGenFramebuffers();
    private final int colorBuffer = glGenRenderbuffers();
    private final int depthBuffer = glGenRenderbuffers();
    private int width;
    private int height;
    private int[] colorRows = new int[0];

    @Override
    public void bindSized(int nextWidth, int nextHeight) {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        if (nextWidth != width || nextHeight != height) {
            resizeTo(nextWidth, nextHeight);
        }
        glViewport(0, 0, width, height);
    }

    private void resizeTo(int nextWidth, int nextHeight) {
        width = nextWidth;
        height = nextHeight;
        glBindRenderbuffer(GL_RENDERBUFFER, colorBuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("The graphics card can't draw a " + width + "x" + height + " picture");
        }
        colorRows = new int[width * height];
    }

    // Reads what was drawn. Only the middle's depth is read back, the one depth a picture keeps.
    @Override
    public OrbitPicture finish(OrbitProjection projection, double depthRange) {
        glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, colorRows);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < height; row++) {
            System.arraycopy(colorRows, (height - 1 - row) * width, pixels, row * width, width);
        }
        return OrbitPicture.whole(image, GpuFrameTarget.depthInMiddle(projection, depthRange, width, height));
    }
}
