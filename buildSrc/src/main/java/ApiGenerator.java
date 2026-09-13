import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class ApiGenerator implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.getPlugins().apply("maven-publish");
        TaskProvider<GenerateApiTask> task = target.getTasks().register("irisApi", GenerateApiTask.class);

        PublishingExtension publishing = target.getExtensions().findByType(PublishingExtension.class);
        if (publishing == null) {
            throw new GradleException("Publishing extension not found");
        }

        publishing.getRepositories().maven(repository -> {
            repository.setName("deployDir");
            repository.setUrl(targetDirectory(target).toURI());
        });

        publishing.getPublications().create("maven", MavenPublication.class, publication -> {
            publication.setGroupId(target.getGroup().toString());
            publication.setArtifactId("iris");
            publication.setVersion(target.getVersion().toString());
            publication.artifact(task);
        });
    }

    public static File targetDirectory(Project project) {
        String dir = System.getenv("DEPLOY_DIR");
        if (dir == null) {
            return project.getLayout().getBuildDirectory().dir("api").get().getAsFile();
        }
        return new File(dir);
    }
}

abstract class GenerateApiTask extends DefaultTask {
    public GenerateApiTask() {
        setGroup("iris");
        dependsOn("jar");
        finalizedBy("publishMavenPublicationToDeployDirRepository");
        doLast(task -> getLogger().lifecycle("The API is located at " + getOutputFile().get().getAsFile().getAbsolutePath()));

        TaskProvider<Jar> jarTask = getProject().getTasks().named("jar", Jar.class);
        getInputFile().convention(jarTask.flatMap(Jar::getArchiveFile));
        getOutputFile().convention(getProject().getLayout().file(getInputFile().getLocationOnly().map(input ->
                new File(ApiGenerator.targetDirectory(getProject()), input.getAsFile().getName()))));
    }

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        File inputFile = getInputFile().get().getAsFile();
        File outputFile = getOutputFile().get().getAsFile();
        File parent = outputFile.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        try (JarFile jar = new JarFile(inputFile);
             JarOutputStream out = new JarOutputStream(new FileOutputStream(outputFile))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    writeStrippedClass(jar, out, entry);
                }
            }
        }
    }

    private static void writeStrippedClass(JarFile jar, JarOutputStream out, JarEntry entry) throws IOException {
        byte[] bytes;
        try (InputStream input = jar.getInputStream(entry)) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = new MethodClearingVisitor(writer);
            ClassReader reader = new ClassReader(input);
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            bytes = writer.toByteArray();
        }

        JarEntry outputEntry = new JarEntry(entry.getName());
        outputEntry.setTime(0L);
        out.putNextEntry(outputEntry);
        out.write(bytes);
        out.closeEntry();
    }
}

class MethodClearingVisitor extends ClassVisitor {
    public MethodClearingVisitor(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (method == null || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return method;
        }
        return new ExceptionThrowingMethodVisitor(method);
    }
}

class ExceptionThrowingMethodVisitor extends MethodVisitor {
    public ExceptionThrowingMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitEnd() {
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("Only API");
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/IllegalStateException",
                "<init>",
                "(Ljava/lang/String;)V",
                false
        );
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
