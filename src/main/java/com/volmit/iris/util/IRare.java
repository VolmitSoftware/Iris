package com.volmit.iris.util;

public interface IRare {
    int getRarity();

    static int get(Object v) {
		return v instanceof IRare ? Math.max(1, ((IRare) v).getRarity()) : 1;
	}
}
