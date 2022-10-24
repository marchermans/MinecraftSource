package buildSrc.tasks;

import buildSrc.extensions.MinecraftArtifactCacheExtension;
import buildSrc.utils.VersionJson;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class EnvironmentSetupTask extends DefaultTask {

    @Input
    public abstract Property<String> getMinecraftVersion();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void doSetup() throws IOException {
        final MinecraftArtifactCacheExtension extension = getProject().getExtensions().getByType(MinecraftArtifactCacheExtension.class);

        try (InputStream in = getClass().getResourceAsStream("/build.gradle.template");
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in)))) {

            final String template = reader.lines().collect(Collectors.joining("\n"));
            final File versionMetadata = extension.cacheVersionManifest(getMinecraftVersion().get());
            final VersionJson versionJson = VersionJson.get(versionMetadata);

            final String dependenciesString = Arrays.stream(versionJson.getLibraries())
                    .map(library -> library.getArtifact().toString())
                    .map(artifactId -> "    implementation \"" + artifactId + "\"")
                    .collect(Collectors.joining("\n"));

            final String templateWithDependencies = template.replace("%%dependencies%%", dependenciesString);

            final File outputFile = getProjectDirectory().get().file("build.gradle").getAsFile();
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(templateWithDependencies);
            }

            final File settingsFile = getProjectDirectory().get().file("build.gradle").getAsFile();
            if (!settingsFile.exists())
                settingsFile.createNewFile();
        }
    }
}
