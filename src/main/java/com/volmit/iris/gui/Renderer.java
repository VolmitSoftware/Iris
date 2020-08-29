package com.volmit.iris.gui;

import java.awt.Color;

@FunctionalInterface
public interface Renderer
{
	public Color draw(double x, double z);
}
