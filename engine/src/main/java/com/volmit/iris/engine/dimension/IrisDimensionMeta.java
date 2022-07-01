package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Accessors(fluent = true, chain = true)
public class IrisDimensionMeta implements Resolvable
{
    private String name;
    private String description;
    private String version;
    @Singular
    private List<IrisDimensionAuthor> authors = new ArrayList<>();
}