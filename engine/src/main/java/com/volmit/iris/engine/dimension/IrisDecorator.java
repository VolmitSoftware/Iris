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

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "decorator")
public class IrisDecorator extends IrisResolvable {
    @Builder.Default
    private IrisPalette palette = IrisPalette.flat("minecraft:grass");

    @Builder.Default
    private IrisChance chance = IrisChance.white(0.25);
}
