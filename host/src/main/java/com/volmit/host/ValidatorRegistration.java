package com.volmit.host;

import com.volmit.validation.Validator;
import java.util.Objects;

/** A validator and the module that registered it, which the host names when the validator fails. */
public record ValidatorRegistration(Validator validator, DiscoveredModule module) {
    public ValidatorRegistration {
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(module, "module");
    }
}
