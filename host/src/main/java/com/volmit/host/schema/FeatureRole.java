package com.volmit.host.schema;

/** Why a module catalogued a settings type. */
public enum FeatureRole {
    /** Settings that stages read: named in {@code when(...)} or registered with {@code registerSettings}. */
    SETTINGS,
    /** Settings of an object generator, registered with {@code registerObject}. */
    OBJECT_GENERATOR
}
