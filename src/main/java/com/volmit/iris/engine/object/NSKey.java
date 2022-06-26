package com.volmit.iris.engine.object;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class NSKey
{
    private final String key;

    /**
     * accepts dirt as minecraft:dirt
     * accepts somemod:ground as somemod:ground
     * @param key the key
     */
    public NSKey(String key)
    {
        this(key.contains(":") ? key.split("\\Q:\\E")[0] : "minecraft", key.contains(":") ? key.split("\\Q:\\E")[1] : key);
    }

    public NSKey(String namespace, String key)
    {
        this.key = namespace + ":" + key;
    }

    public String getNamespace()
    {
        return key.split("\\Q:\\E")[0];
    }

    public String getKey()
    {
        return key.split("\\Q:\\E")[1];
    }

    public NSKey withKey(String key)
    {
        return new NSKey(getNamespace(), key);
    }

    public NSKey withNamespace(String namespace)
    {
        return new NSKey(namespace, getKey());
    }

    @Override
    public String toString()
    {
        return key;
    }
}
