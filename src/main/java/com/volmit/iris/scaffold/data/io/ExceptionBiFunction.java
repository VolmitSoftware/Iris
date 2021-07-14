package com.volmit.iris.scaffold.data.io;

@FunctionalInterface
public interface ExceptionBiFunction <T, U, R, E extends Exception> {

	R accept(T t, U u) throws E;
}
