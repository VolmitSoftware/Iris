package com.volmit.iris.util.mantle.flag;

import org.jetbrains.annotations.NotNull;

record CustomFlag(String name, int ordinal) implements MantleFlag {

    @Override
    public @NotNull String toString() {
        return name;
    }

    @Override
    public boolean isCustom() {
        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof CustomFlag that))
            return false;
        return ordinal == that.ordinal;
    }

    @Override
    public int hashCode() {
        return ordinal;
    }
}
