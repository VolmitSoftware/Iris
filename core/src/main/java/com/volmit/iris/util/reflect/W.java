package com.volmit.iris.util.reflect;

import lombok.Getter;

public class W {
    @Getter
    private static final StackWalker stack = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
}
