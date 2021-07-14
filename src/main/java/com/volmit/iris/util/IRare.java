package com.volmit.iris.util;

public interface IRare {
    int getRarity();

    static int get(Object v) {
        return v instanceof IRare ? ((IRare) v).getRarity() : 1;
    }
}
