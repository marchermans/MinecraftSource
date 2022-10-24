package buildSrc.tasks;

import buildSrc.workers.JarRenameWorker;
import com.sun.management.OperatingSystemMXBean;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.lang.management.ManagementFactory;

public abstract class RenameJarTask extends DefaultTask {

    private static final String FART_VERSION ="0.1.24";

    @Input
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getMappingFile();

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
        }).submit(JarRenameWorker.class, parameters -> {
            parameters.getInputJar().set(getInputJar()); // Use the temporary jar
            parameters.getOutputJar().set(getOutputJar());
            parameters.getMappingFile().set(getMappingFile());
        });
        this.getWorkerExecutor().await();
    }

    private FileCollection getWorkerClasspath() {
        final Configuration forgeFlower = this.getProject().getConfigurations().detachedConfiguration();
        forgeFlower.defaultDependencies(deps -> deps.add(getProject().getDependencies().create("net.minecraftforge:ForgeAutoRenamingTool:" + FART_VERSION + ":all")));
        return forgeFlower.getIncoming().getFiles();
    }

}
