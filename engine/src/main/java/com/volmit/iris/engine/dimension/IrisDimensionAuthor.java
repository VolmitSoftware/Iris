package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Accessors(fluent = true, chain = true)
public class IrisDimensionAuthor implements Resolvable
{
    private String name;
    private Map<String, String> social;
}