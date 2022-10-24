package buildSrc;

import buildSrc.extensions.MinecraftArtifactCacheExtension;
import buildSrc.extensions.MinecraftSourceExtension;
import buildSrc.tasks.*;
import buildSrc.utils.GameArtifact;
import buildSrc.utils.Utils;
import buildSrc.utils.VersionJson;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

public class BuildSrcPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull final Project project) {
        final MinecraftSourceExtension minecraftSourceExtension = project.getExtensions().create("minecraft", MinecraftSourceExtension.class, project);
        project.getExtensions().create("minecraftArtifact", MinecraftArtifactCacheExtension.class, project);

        final TaskProvider<?> cleanMinecraft = project.getTasks().register("cleanMinecraft", Delete.class, task -> {
            task.delete(project.getLayout().getProjectDirectory().dir("game"));

            task.getOutputs().upToDateWhen(o -> false);
            task.getOutputs().doNotCacheIf("Always execute cleanMinecraft", o -> true);

            task.setGroup("build");
            task.setDescription("Cleans the Minecraft sources directory");

            task.onlyIf(t -> !minecraftSourceExtension.getIsIncremental().get());
        });

        final TaskProvider<?> clean = project.getTasks().register("clean", Delete.class, task -> {
            task.delete(project.getLayout().getBuildDirectory());

            task.dependsOn(cleanMinecraft);
            task.setGroup("build");
            task.setDescription("Cleans all build artifacts and the minecraft sources.");
            task.getOutputs().upToDateWhen(o -> false);
        });

        project.afterEvaluate(postEvalProject -> {
            TaskProvider<?> previous = cleanMinecraft;
            for (String version : minecraftSourceExtension.getVersionsToGenerate().get()) {
                previous = buildVersionTaskTree(project, version, previous);
            }

            TaskProvider<?> finalPrevious = previous;
            final TaskProvider<?> build = project.getTasks().register("build", task -> {
                task.dependsOn(finalPrevious);
                task.setGroup("build");
                task.setDescription("Builds all Minecraft versions.");
                task.getOutputs().upToDateWhen(o -> false);
            });
        });

        setupRepositories(project);
    }

    private static void setupRepositories(final Project project) {
        //Add Known repos
        project.getRepositories().maven(e -> {
            e.setUrl(Utils.FORGE_MAVEN);
            e.metadataSources(m -> {
                m.gradleMetadata();
                m.mavenPom();
                m.artifact();
            });
        });
        project.getRepositories().maven(e -> {
            e.setUrl(Utils.MOJANG_MAVEN);
            e.metadataSources(MavenArtifactRepository.MetadataSources::artifact);
        });
        project.getRepositories().mavenCentral(); //Needed for MCP Deps
    }

    private static TaskProvider<?> buildVersionTaskTree(final Project project, final String minecraftVersion, TaskProvider<?> previous) {
        final MinecraftArtifactCacheExtension cacheExtension = project.getExtensions().getByType(MinecraftArtifactCacheExtension.class);

        final TaskProvider<MinecraftDownloadingTask> downloader = project.getTasks().register("downloadMinecraft" + minecraftVersion, MinecraftDownloadingTask.class, task -> {
            task.getMinecraftVersion().set(minecraftVersion);

            if (previous != null) {
                task.dependsOn(previous);
            }

            task.setGroup("Setup Minecraft " + minecraftVersion);
            task.setDescription("Downloads the Minecraft " + minecraftVersion + " artifacts.");
        });

        final TaskProvider<RenameJarTask> renamer = project.getTasks().register("renameMinecraft" + minecraftVersion, RenameJarTask.class, task -> {
            task.getInputJar().fileProvider(downloader.map(downloadingTask -> downloadingTask.getOutputFiles().get(GameArtifact.CLIENT_JAR)));
            task.getOutputJar().set(project.getLayout().getBuildDirectory().map(build -> build.dir("minecraft").dir(minecraftVersion).file("renamed.jar")));
            task.getMappingFile().fileProvider(downloader.map(downloadingTask -> downloadingTask.getOutputFiles().get(GameArtifact.CLIENT_MAPPINGS)));

            task.dependsOn(downloader);

            task.setGroup("Setup Minecraft " + minecraftVersion);
            task.setDescription("Renames the Minecraft " + minecraftVersion + " raw jar.");
        });

        final TaskProvider<DecompileJarTask> decompiler = project.getTasks().register("decompileMinecraft" + minecraftVersion, DecompileJarTask.class, task -> {
            final VersionJson versionJson;
            try {
                versionJson = VersionJson.get(cacheExtension.cacheVersionManifest(minecraftVersion));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            task.getInputJar().set(renamer.flatMap(RenameJarTask::getOutputJar));
            task.getOutputJar().set(project.getLayout().getBuildDirectory().map(build -> build.dir("minecraft").dir(minecraftVersion).file("decompiled.jar")));

            final Configuration dependencies = project.getConfigurations().detachedConfiguration();
            Arrays.stream(versionJson.getLibraries()).forEach(library -> {
                dependencies.getDependencies().add(project.getDependencies().create(library.getArtifact().toString()));
            });
            task.getDecompileClasspath().setFrom(dependencies);

            task.dependsOn(renamer);

            task.setGroup("Setup Minecraft " + minecraftVersion);
            task.setDescription("Decompiles the Minecraft " + minecraftVersion + " renamed jar.");
        });

        final TaskProvider<ExtractSourcesTask> extractSources = project.getTasks().register("extractMinecraft" + minecraftVersion, ExtractSourcesTask.class, task -> {
            task.getDecompiledJar().set(decompiler.flatMap(DecompileJarTask::getOutputJar));
            task.getSourceFilesDirectory().set(project.getLayout().getProjectDirectory().dir("game").dir("src/main/java"));
            task.getResourceFilesDirectory().set(project.getLayout().getProjectDirectory().dir("game").dir("src/main/resources"));

            task.dependsOn(decompiler);

            task.setGroup("Setup Minecraft " + minecraftVersion);
            task.setDescription("Extracts the Minecraft " + minecraftVersion + " decompiled jar.");
        });

        final TaskProvider<EnvironmentSetupTask> setupEnvironment = project.getTasks().register("setupEnvironment" + minecraftVersion, EnvironmentSetupTask.class, task -> {
            task.getMinecraftVersion().set(minecraftVersion);
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory().dir("game"));

            task.dependsOn(extractSources);

            task.setGroup("Setup Minecraft " + minecraftVersion);
            task.setDescription("Sets up the Gradle environment for: " + minecraftVersion + ".");
        });

        final TaskProvider<CreateCommitTask> commit = project.getTasks().register("commitMinecraft" + minecraftVersion, CreateCommitTask.class, task -> {
            task.getCommitMessage().set("Minecraft " + minecraftVersion);
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory().dir("game"));

            task.dependsOn(setupEnvironment);

            task.setGroup("Setup Minecraft " + minecraftVersion);
            task.setDescription("Commits Minecraft " + minecraftVersion + " sources.");
        });

        return commit;
    }
}
