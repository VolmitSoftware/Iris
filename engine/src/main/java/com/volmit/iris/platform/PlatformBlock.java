package com.volmit.iris.platform;

import java.util.Map;

public interface PlatformBlock extends PlatformNamespaced {
    Map<String, String> getProperties();
}
