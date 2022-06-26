package com.volmit.iris.engine.object.block;

import com.volmit.iris.engine.object.Namespaced;
import com.volmit.iris.engine.object.NSKey;
import lombok.Data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Data
public class IrisBlock implements Namespaced {
    private static final Map<String, String> EMPTY_PROPERTIES = Map.of();
    private final NSKey key;
    private final Map<String, String> properties;

    public IrisBlock(NSKey key, Map<String, String> properties)
    {
        this.key = key;
        this.properties = Collections.unmodifiableMap(properties);
    }

    public IrisBlock(NSKey key)
    {
        this(key, EMPTY_PROPERTIES);
    }

    public IrisBlock property(String key, String value) {
        Map<String, String> map = new HashMap<>(getProperties());
        map.put(key, value);
        return new IrisBlock(getKey(), map);
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for(String i : getProperties().keySet())
        {
            sb.append(",").append(i).append("=").append(getProperties().get(i));
        }

        return sb.append(getKey()).append("[") + sb.append("]").substring(1);
    }
}
