package buildSrc.tasks;

import buildSrc.workers.JarDecompileWorker;
import com.sun.management.OperatingSystemMXBean;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.lang.management.ManagementFactory;
import java.util.Collections;

public abstract class DecompileJarTask extends DefaultTask {

    private static final String FORGE_FLOWER_VERSION="1.5.605.9";

    @Input
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDecompileClasspath();

    @Nested
    @Optional
    public abstract MapProperty<String, String> getExtraFernFlowerArgs();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void doDecompile() throws Exception {
        // Execute in an isolated JVM that can access our customized classpath
        // This actually performs the decompile
        final long totalSystemMemoryBytes =
                ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize() / (1024L * 1024L);

        this.getWorkerExecutor().processIsolation(spec -> {
            spec.forkOptions(options -> {
                options.setMaxHeapSize(Math.max(totalSystemMemoryBytes / 4, 4096) + "M");
                // Enable toolchain support
                if (this.getJavaLauncher().isPresent()) {
                    final JavaLauncher launcher = this.getJavaLauncher().get();
                    options.setExecutable(launcher.getExecutablePath());
                }
            });
            spec.getClasspath().from(this.getWorkerClasspath());
        }).submit(JarDecompileWorker.class, parameters -> {
            parameters.getDecompileClasspath().from(getDecompileClasspath());
            parameters.getExtraArgs().set(this.getExtraFernFlowerArgs().orElse(Collections.emptyMap()));
            parameters.getInputJar().set(getInputJar()); // Use the temporary jar
            parameters.getOutputJar().set(getOutputJar());
        });
        this.getWorkerExecutor().await();
    }

    private FileCollection getWorkerClasspath() {
        final Configuration forgeFlower = this.getProject().getConfigurations().detachedConfiguration();
        forgeFlower.defaultDependencies(deps -> deps.add(getProject().getDependencies().create("net.minecraftforge:forgeflower:" + FORGE_FLOWER_VERSION)));
        return forgeFlower.getIncoming().getFiles();
    }
}
