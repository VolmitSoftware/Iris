package com.volmit.iris.manager.gui;

import java.awt.Color;

@FunctionalInterface
public interface Renderer
{
	public Color draw(double x, double z);
}
