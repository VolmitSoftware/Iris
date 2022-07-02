package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "dimension-meta")
public class IrisDimensionMeta  extends IrisResolvable
{
    private String name;
    private String description;
    private String version;
    @Singular
    @Resolvable.Type(IrisAuthor.class)
    private List<IrisAuthor> authors = new ArrayList<>();
}