package com.volmit.iris.scaffold.stream;

import com.volmit.iris.util.KList;

public interface Significance<T> {
    KList<T> getFactorTypes();

    double getSignificance(T t);

    T getMostSignificantType();
}
