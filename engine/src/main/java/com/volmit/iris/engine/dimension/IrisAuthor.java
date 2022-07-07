package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.engine.resolver.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "author")
public class IrisAuthor extends EngineResolvable
{
    private String name;
    private Map<String, String> social;
}