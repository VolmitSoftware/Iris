package com.volmit.validation;

/** How much a {@link Problem} matters to the pack. */
public enum Severity {
    /** The pack cannot be used as it is: the host refuses to load it. */
    ERROR,
    /** The pack works, but probably not as its author meant: the host logs it and loads the pack. */
    WARNING
}
