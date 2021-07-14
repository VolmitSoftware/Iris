package com.volmit.iris.manager.gui;

import lombok.Builder;
import lombok.Data;

import java.awt.image.BufferedImage;

@Builder
@Data
public class TileRender {
    private BufferedImage image;
    private int quality;
}
