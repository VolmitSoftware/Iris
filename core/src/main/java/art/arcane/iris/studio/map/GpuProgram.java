/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.map;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/**
 * A vertex and a fragment shader linked into one program, with its uniforms set by name. Only for the GPU thread.
 */
final class GpuProgram {
    private final int id;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    private GpuProgram(int id) {
        this.id = id;
    }

    static GpuProgram of(String vertexSource, String fragmentSource) {
        int vertexShader = compile(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Linking a shader program failed: " + log);
        }
        return new GpuProgram(program);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Compiling a shader failed: " + log);
        }
        return shader;
    }

    void use() {
        glUseProgram(id);
    }

    void set(String name, int value) {
        glUniform1i(location(name), value);
    }

    void set(String name, int x, int y) {
        glUniform2i(location(name), x, y);
    }

    void set(String name, int x, int y, int z) {
        glUniform3i(location(name), x, y, z);
    }

    void set(String name, int[] values) {
        glUniform1iv(location(name), values);
    }

    void set(String name, double value) {
        glUniform1f(location(name), (float) value);
    }

    void set(String name, Vec3 value) {
        glUniform3f(location(name), (float) value.x(), (float) value.y(), (float) value.z());
    }

    void set(String name, double red, double green, double blue, double alpha) {
        glUniform4f(location(name), (float) red, (float) green, (float) blue, (float) alpha);
    }

    void setMatrix(String name, float[] columnMajor) {
        glUniformMatrix4fv(location(name), false, columnMajor);
    }

    private int location(String name) {
        return uniformLocations.computeIfAbsent(name, uniform -> glGetUniformLocation(id, uniform));
    }
}
