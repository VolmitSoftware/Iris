package com.volmit.validation;

/** Checks a pack for problems its module cares about, such as a structure that can never assemble. Modules
 * register validators with {@link com.volmit.FeatureRegistry#registerValidator}; the host runs every validator of
 * the pack wherever it validates packs (when a pack loads, when studio opens it, on request) and reports all
 * problems together. A validator reports what it finds and returns; it must not change the pack. One that throws
 * fails the validation of the pack, naming its module.
 */
@FunctionalInterface
public interface Validator {
    void validate(ValidationContext pack);
}
