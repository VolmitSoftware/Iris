package com.volmit.iris.manager.gui;

import java.awt.*;

@FunctionalInterface
public interface Renderer {
    Color draw(double x, double z);
}
