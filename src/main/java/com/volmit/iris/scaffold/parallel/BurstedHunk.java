package com.volmit.iris.scaffold.parallel;

import com.volmit.iris.scaffold.hunk.Hunk;

public interface BurstedHunk<T> extends Hunk<T> {
    int getOffsetX();

    int getOffsetY();

    int getOffsetZ();
}
