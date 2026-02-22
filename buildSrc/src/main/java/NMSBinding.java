import com.volmit.nmstools.NMSToolsExtension;
import com.volmit.nmstools.NMSToolsPlugin;
import io.papermc.paperweight.userdev.PaperweightUser;
import io.papermc.paperweight.userdev.PaperweightUserDependenciesExtension;
import io.papermc.paperweight.userdev.PaperweightUserExtension;
import io.papermc.paperweight.userdev.attribute.Obfuscation;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.papermc.paperweight.util.constants.ConstantsKt.REOBF_CONFIG;

public class NMSBinding implements Plugin<Project> {
    private static final String NEW_LINE = System.lineSeparator();
    private static final byte[] NEW_LINE_BYTES = NEW_LINE.getBytes(StandardCharsets.UTF_8);

    @Override
    public void apply(Project target) {
        ExtraPropertiesExtension extra = target.getExtensions().getExtraProperties();
        Object configValue = extra.has("nms") ? extra.get("nms") : null;
        if (!(configValue instanceof Config)) {
            throw new GradleException("No NMS binding configuration found");
        }

        Config config = (Config) configValue;
        int jvm = config.jvm;
        Type type = config.type;

        if (type == Type.USER_DEV) {
            target.getPlugins().apply(PaperweightUser.class);

            PaperweightUserDependenciesExtension dependenciesExtension =
                    target.getDependencies().getExtensions().findByType(PaperweightUserDependenciesExtension.class);
            if (dependenciesExtension != null) {
                dependenciesExtension.paperDevBundle(config.version);
            }

            JavaPluginExtension java = target.getExtensions().findByType(JavaPluginExtension.class);
            if (java == null) {
                throw new GradleException("Java plugin not found");
            }

            java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(jvm));
            JavaToolchainService javaToolchains = target.getExtensions().getByType(JavaToolchainService.class);
            target.getExtensions().configure(PaperweightUserExtension.class,
                    extension -> extension.getJavaLauncher().set(javaToolchains.launcherFor(java.getToolchain())));
        } else {
            extra.set("nmsTools.useBuildTools", type == Type.BUILD_TOOLS);
            target.getPlugins().apply(NMSToolsPlugin.class);
            target.getExtensions().configure(NMSToolsExtension.class, extension -> {
                extension.getJvm().set(jvm);
                extension.getVersion().set(config.version);
            });

            ObjectFactory objects = target.getObjects();
            target.getConfigurations().register(REOBF_CONFIG, configuration -> {
                configuration.setCanBeConsumed(true);
                configuration.setCanBeResolved(false);
                configuration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, named(objects, Usage.class, Usage.JAVA_RUNTIME));
                configuration.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, named(objects, Category.class, Category.LIBRARY));
                configuration.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(objects, LibraryElements.class, LibraryElements.JAR));
                configuration.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, named(objects, Bundling.class, Bundling.EXTERNAL));
                configuration.getAttributes().attribute(Obfuscation.Companion.getOBFUSCATION_ATTRIBUTE(), named(objects, Obfuscation.class, Obfuscation.OBFUSCATED));
                configuration.getOutgoing().artifact(target.getTasks().named("remap"));
            });
        }

        int[] version = parseVersion(config.version);
        int major = version[0];
        int minor = version[1];
        if (major <= 20 && minor <= 4) {
            return;
        }

        target.getTasks().register("convert", ConversionTask.class, type);
        target.getTasks().named("compileJava").configure(task -> task.dependsOn("convert"));
        target.getRootProject().getTasks()
                .matching(task -> task.getName().equals("prepareKotlinBuildScriptModel"))
                .configureEach(task -> task.dependsOn(target.getPath() + ":convert"));
    }

    public static void nmsBinding(Project project, Action<Config> action) {
        Config config = new Config();
        action.execute(config);
        project.getExtensions().getExtraProperties().set("nms", config);
        project.getPlugins().apply(NMSBinding.class);
    }

    private static int[] parseVersion(String version) {
        String trimmed = version;
        int suffix = trimmed.indexOf('-');
        if (suffix >= 0) {
            trimmed = trimmed.substring(0, suffix);
        }

        String[] parts = trimmed.split("\\.");
        return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static <T extends Named> T named(ObjectFactory objects, Class<T> type, String name) {
        return objects.named(type, name);
    }

    @DisableCachingByDefault
    public abstract static class ConversionTask extends DefaultTask {
        private final Pattern pattern;
        private final String replacement;

        @Inject
        public ConversionTask(Type type) {
            setGroup("nms");
            getInputs().property("type", type);

            JavaPluginExtension java = getProject().getExtensions().findByType(JavaPluginExtension.class);
            if (java == null) {
                throw new GradleException("Java plugin not found");
            }

            Provider<FileTree> source = java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getAllJava);
            getInputs().files(source);
            getOutputs().files(source);

            if (type == Type.USER_DEV) {
                this.pattern = Pattern.compile("org\\.bukkit\\.craftbukkit\\." + getProject().getName());
                this.replacement = "org.bukkit.craftbukkit";
            } else {
                this.pattern = Pattern.compile("org\\.bukkit\\.craftbukkit\\.(?!" + getProject().getName() + ")");
                this.replacement = "org.bukkit.craftbukkit." + getProject().getName() + ".";
            }
        }

        @TaskAction
        public void process() {
            ExecutorService executor = Executors.newFixedThreadPool(16);
            try {
                Set<File> files = getInputs().getFiles().getFiles();
                List<Future<?>> futures = new ArrayList<>(files.size());
                for (File file : files) {
                    if (!file.getName().endsWith(".java")) {
                        continue;
                    }
                    futures.add(executor.submit(() -> processFile(file)));
                }

                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            } finally {
                executor.shutdown();
            }
        }

        private void processFile(File file) {
            List<String> output = new ArrayList<>();
            boolean changed = false;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("package") || line.isBlank()) {
                        output.add(line);
                        continue;
                    }

                    if (!line.startsWith("import")) {
                        if (!changed) {
                            return;
                        }

                        output.add(line);
                        continue;
                    }

                    Matcher matcher = pattern.matcher(line);
                    if (!matcher.find()) {
                        output.add(line);
                        continue;
                    }

                    output.add(matcher.replaceAll(replacement));
                    changed = true;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (!changed) {
                return;
            }

            try {
                if (hasTrailingNewLine(file)) {
                    output.add("");
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    for (int i = 0; i < output.size(); i++) {
                        writer.append(output.get(i));
                        if (i + 1 < output.size()) {
                            writer.append(NEW_LINE);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean hasTrailingNewLine(File file) throws IOException {
            if (NEW_LINE_BYTES.length == 0) {
                return false;
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                if (raf.length() < NEW_LINE_BYTES.length) {
                    return false;
                }

                byte[] bytes = new byte[NEW_LINE_BYTES.length];
                raf.seek(raf.length() - bytes.length);
                raf.readFully(bytes);
                return Arrays.equals(bytes, NEW_LINE_BYTES);
            }
        }
    }

    public enum Type {
        USER_DEV,
        BUILD_TOOLS,
        DIRECT
    }
}
