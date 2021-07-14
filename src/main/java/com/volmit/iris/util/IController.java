package com.volmit.iris.util;

import org.bukkit.event.Listener;

public interface IController extends Listener {
    String getName();

    void start();

    void stop();

    void tick();

    int getTickInterval();

    void l(Object l);

    void w(Object l);

    void f(Object l);

    void v(Object l);
}
