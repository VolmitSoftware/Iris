import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.*
import java.io.File
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ApiGenerator : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("generateApi", GenerateApiTask::class.java)
    }
}

abstract class GenerateApiTask : DefaultTask() {
    init {
        group = "iris"
        dependsOn("jar")
    }

    @InputFile
    val inputFile: Provider<File> = project.tasks
        .named("jar", Jar::class.java)
        .flatMap { it.archiveFile }
        .map { it.asFile }

    @OutputFile
    val outputFile: Provider<File> = inputFile.map { targetDirectory().resolve(it.name) }

    @TaskAction
    fun generate() {
        JarFile(inputFile.get()).use { jar ->
            JarOutputStream(outputFile.get().outputStream()).use { out ->
                jar.stream()
                    .parallel()
                    .filter { !it.isDirectory }
                    .filter { it.name.endsWith(".class") }
                    .forEach {
                        val bytes = jar.getInputStream(it).use { input ->
                            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
                            val visitor = MethodClearingVisitor(writer)
                            ClassReader(input).accept(visitor, 0)
                            writer.toByteArray()
                        }

                        synchronized(out) {
                            out.putNextEntry(it)
                            out.write(bytes)
                            out.closeEntry()
                        }
                    }
            }
        }
    }

    fun targetDirectory(): File {
        val dir = System.getenv("DEPLOY_DIR") ?: return project.layout.buildDirectory.dir("api").get().asFile
        return File(dir)
    }
}

private class MethodClearingVisitor(
    cv: ClassVisitor
) : ClassVisitor(Opcodes.ASM9, cv) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ) = ExceptionThrowingMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions))
}

private class ExceptionThrowingMethodVisitor(
    mv: MethodVisitor
) : MethodVisitor(Opcodes.ASM9, mv) {

    override fun visitCode() {
        if (mv == null) return
        mv.visitCode()

        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("Only API")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/IllegalStateException",
            "<init>", "(Ljava/lang/String;)V", false
        )
        mv.visitInsn(Opcodes.ATHROW)

        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }
}