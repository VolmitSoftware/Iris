package com.volmit.iris.engine.dimension;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class IrisDimension
{
    private String name;

    @Data
    public static class IrisDimensionMeta
    {
        private String name;
        private String description;
        private String version;
        private List<IrisDimensionAuthor> authors = new ArrayList<>();
    }

    @Data
    public static class IrisDimensionAuthor
    {
        private String name;
        private Map<String, String> social;
    }
}
