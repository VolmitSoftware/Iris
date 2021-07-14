package com.volmit.iris.scaffold.engine;

public interface Fallible {
    default void fail(String error) {
        try {
            throw new RuntimeException();
        } catch (Throwable e) {
            fail(error, e);
        }
    }

    default void fail(Throwable e) {
        fail("Failed to generate", e);
    }

    void fail(String error, Throwable e);

    boolean hasFailed();
}
