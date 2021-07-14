package com.volmit.iris.util;

/**
 * Callback for async workers
 *
 * @param <T> the type of object to be returned in the runnable
 * @author cyberpwn
 */
@FunctionalInterface
public interface Callback<T> {
    /**
     * Called when the callback calls back...
     *
     * @param t the object to be called back
     */
    void run(T t);
}
