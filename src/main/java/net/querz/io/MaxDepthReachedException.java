package net.querz.io;

/**
 * Exception indicating that the maximum (de-)serialization depth has been reached.
 */
@SuppressWarnings("serial")
public class MaxDepthReachedException extends RuntimeException {

	public MaxDepthReachedException(String msg) {
		super(msg);
	}
}
