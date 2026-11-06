package com.volmit.host;

import com.volmit.tooling.SceneView;

import java.util.Objects;

/** A studio scene view and the module that registered it. */
public record SceneViewRegistration(SceneView view, DiscoveredModule module) {
    public SceneViewRegistration {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(module, "module");
    }

    @Override
    public String toString() {
        return "scene view " + view.id() + " of module " + module;
    }
}
