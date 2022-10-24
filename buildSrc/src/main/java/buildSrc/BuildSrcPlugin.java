package buildSrc;

import buildSrc.extensions.MinecraftArtifactCacheExtension;
import buildSrc.extensions.MinecraftSourceExtension;
import buildSrc.tasks.*;
import buildSrc.utils.GameArtifact;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class BuildSrcPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull final Project project) {
        final File sourcesDir = project.mkdir("minecraft");

        final MinecraftSourceExtension minecraftSourceExtension = project.getExtensions().create("minecraft", MinecraftSourceExtension.class, project);
        final MinecraftArtifactCacheExtension minecraftArtifactCacheExtension = project.getExtensions().create("minecraftArtifact", MinecraftArtifactCacheExtension.class, project);

        project.getTasks().register("cleanMinecraft", CleanSourcesTask.class, task -> {
            task.getDirectoryToClean().set(sourcesDir);
        });

        project.afterEvaluate(postEvalProject -> {
            TaskProvider<?> previous = null;
            for (String version : minecraftSourceExtension.getVersionsToGenerate().get()) {
                previous = buildVersionTaskTree(project, version, previous);
            }
        });
    }

    private static TaskProvider<?> buildVersionTaskTree(final Project project, final String minecraftVersion, TaskProvider<?> previous) {
        final TaskProvider<MinecraftDownloadingTask> downloader = project.getTasks().register("downloadMinecraft" + minecraftVersion, MinecraftDownloadingTask.class, task -> {
            task.getMinecraftVersion().set(minecraftVersion);

            if (previous != null) {
                task.dependsOn(previous);
            }
        });

        final TaskProvider<RenameJarTask> renamer = project.getTasks().register("renameMinecraft" + minecraftVersion, RenameJarTask.class, task -> {
            task.getInputJar().fileProvider(downloader.flatMap(downloadingTask -> downloadingTask.getOutputFiles().map(files -> files.get(GameArtifact.CLIENT_JAR))));
            task.getOutputJar().set(project.getLayout().getBuildDirectory().map(build -> build.dir("minecraft").dir(minecraftVersion).file("renamed.jar")));
            task.getMappingFile().fileProvider(downloader.flatMap(downloadingTask -> downloadingTask.getOutputFiles().map(files -> files.get(GameArtifact.CLIENT_MAPPINGS))));

            task.dependsOn(downloader);
        });

        final TaskProvider<DecompileJarTask> decompiler = project.getTasks().register("decompileMinecraft" + minecraftVersion, DecompileJarTask.class, task -> {
            task.getInputJar().set(renamer.flatMap(RenameJarTask::getOutputJar));
            task.getOutputJar().set(project.getLayout().getBuildDirectory().map(build -> build.dir("minecraft").dir(minecraftVersion).file("decompiled.jar")));

            task.dependsOn(renamer);
        });

        final TaskProvider<CleanSourcesTask> existingCleaner = project.getTasks().register("cleanMinecraft" + minecraftVersion, CleanSourcesTask.class, task -> {
            task.getDirectoryToClean().set(project.getLayout().getProjectDirectory().dir("game"));

            if (previous != null) {
                task.dependsOn(previous);
            }
        });

        final TaskProvider<ExtractSourcesTask> extractSources = project.getTasks().register("extractMinecraft" + minecraftVersion, ExtractSourcesTask.class, task -> {
            task.getDecompiledJar().set(decompiler.flatMap(DecompileJarTask::getOutputJar));
            task.getOutputDirectory().set(project.getLayout().getProjectDirectory().dir("game").dir("src/main/java"));

            task.dependsOn(decompiler);
            task.dependsOn(existingCleaner);
        });

        final TaskProvider<EnvironmentSetupTask> setupEnvironment = project.getTasks().register("setupEnvironment" + minecraftVersion, EnvironmentSetupTask.class, task -> {
            task.getMinecraftVersion().set(minecraftVersion);
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory().dir("game"));

            task.dependsOn(extractSources);
        });

        return setupEnvironment;
    }
}
