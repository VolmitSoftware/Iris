package com.volmit.host;

import com.volmit.tooling.PatternView;
import com.volmit.tooling.SceneView;
import com.volmit.tooling.StudioView;

import java.util.Objects;

/** A studio view and the module that registered it. */
public record StudioViewRegistration(StudioView view, DiscoveredModule module) {
    public StudioViewRegistration {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return kindOf(view) + " " + view.id() + " of module " + module;
    }

    static String kindOf(StudioView view) {
        return switch (view) {
            case SceneView ignored -> "scene view";
            case PatternView ignored -> "pattern view";
        };
    }
}
