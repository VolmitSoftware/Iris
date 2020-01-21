package ninja.bytecode.iris.util;

public enum ObjectMode
{
	/**
	 * Technically the fasted mode. I.e. Do nothing
	 */
	NONE,

	/**
	 * The fastest placer. Places without updating lighting or physics
	 * 
	 * Lighting is applied later with packets
	 */
	FAST_LIGHTING,

	/**
	 * Somewhat slow but produces near-perfect results. Updates lighting.
	 */
	LIGHTING,

	/**
	 * Somewhat slow but produces near-perfect results. Updates lighting & physics
	 */
	LIGHTING_PHYSICS,

	/**
	 * Somewhat slow but never cascades and is instantly placed with terrain
	 * 
	 * Lighting is applied later with packets
	 */
	PARALLAX;
}
