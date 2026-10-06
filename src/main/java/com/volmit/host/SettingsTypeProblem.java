package com.volmit.host;

/** Why a settings field's type cannot be described, phrased to follow the field's name, such as
 * "uses java.util.UUID, a JDK class, ...". {@link TypeDrafts} adds the field and its settings.
 */
final class SettingsTypeProblem extends RuntimeException {
    SettingsTypeProblem(String problem) {
        super(problem, null, false, false);
    }
}
