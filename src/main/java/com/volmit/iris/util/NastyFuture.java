package com.volmit.iris.util;

public interface NastyFuture<R> {
    R run() throws Throwable;
}
