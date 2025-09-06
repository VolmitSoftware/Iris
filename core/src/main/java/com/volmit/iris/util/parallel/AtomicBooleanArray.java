package com.volmit.iris.util.parallel;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class AtomicBooleanArray implements Serializable {
    private static final VarHandle AA = MethodHandles.arrayElementVarHandle(boolean[].class);
    private final boolean[] array;

    public AtomicBooleanArray(int length) {
        array = new boolean[length];
    }

    public final int length() {
        return array.length;
    }

    public final boolean get(int index) {
        return (boolean) AA.getVolatile(array, index);
    }

    public final void set(int index, boolean newValue) {
        AA.setVolatile(array, index, newValue);
    }

    public final boolean compareAndSet(int index, boolean expectedValue, boolean newValue) {
        return (boolean) AA.compareAndSet(array, index, expectedValue, newValue);
    }

    @Override
    public String toString() {
        int iMax = array.length - 1;
        if (iMax == -1)
            return "[]";

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(get(i));
            if (i == iMax)
                return b.append(']').toString();
            b.append(',').append(' ');
        }
    }
}
