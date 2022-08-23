package com.volmit.iris.platform.block;

import com.volmit.iris.platform.PlatformNamespaced;

import java.util.Map;

public interface PlatformBlock extends PlatformNamespaced {
    Map<String, String> getProperties();
}
