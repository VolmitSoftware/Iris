/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.pack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisValidationContext;
import art.arcane.iris.core.modules.PackModulesException;
import art.arcane.iris.spi.IrisLogging;
import com.volmit.host.ValidatorRegistration;
import com.volmit.validation.Problem;
import com.volmit.validation.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the validators of a pack's modules, built-in ones included, on the pack as Iris loads it, opened just for
 * this ({@link IrisData#openValidation}) and closed afterwards. Each problem becomes the text
 * {@code <subject>: <message>}; errors block the pack like Iris's own validators' errors, warnings are only shown.
 * A pack whose modules fail to load gets that failure as its error. A validator that throws gets an error naming its
 * module, and the others still run.
 */
final class ModulePackValidation {
    private ModulePackValidation() {
    }

    /** What the pack's validators found. */
    record Findings(List<String> errors, List<String> warnings) {
        Findings {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    static Findings validate(File packFolder) {
        IrisData data = IrisData.openValidation(packFolder);
        try {
            return validate(data);
        } finally {
            data.close();
        }
    }

    private static Findings validate(IrisData data) {
        List<ValidatorRegistration> validators;
        try {
            validators = data.getModules().pack().catalog().validators();
        } catch (PackModulesException failure) {
            return new Findings(List.of(failure.getMessage()), List.of());
        }
        return validate(data, validators);
    }

    /** Runs {@code validators} on the pack {@code data} holds, in order. */
    static Findings validate(IrisData data, List<ValidatorRegistration> validators) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ValidatorRegistration validator : validators) {
            run(validator, data, errors, warnings);
        }
        return new Findings(errors, warnings);
    }

    private static void run(ValidatorRegistration registration, IrisData data, List<String> errors, List<String> warnings) {
        IrisValidationContext context = IrisValidationContext.forPack(data);
        try {
            registration.validator().validate(context);
        } catch (RuntimeException failure) {
            IrisLogging.reportError(failure);
            errors.add("Module " + registration.module() + " failed while validating the pack: " + failure);
        }
        for (Problem problem : context.problems()) {
            (problem.severity() == Severity.ERROR ? errors : warnings).add(text(problem));
        }
    }

    private static String text(Problem problem) {
        return problem.subject() + ": " + problem.message();
    }
}
