package buildSrc.extensions;

import buildSrc.utils.Manifest;
import buildSrc.utils.ReflectionUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.srgutils.MinecraftVersion;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.collect;

public abstract class MinecraftSourceExtension {

    private final Project project;

    @Inject
    public MinecraftSourceExtension(Project project) {
        this.project = project;

        this.getVersionsToGenerate().convention(Collections.emptyList());
        this.getIsIncremental().convention(true);
    }

    public abstract ListProperty<String> getVersionsToGenerate();

    public abstract Property<Boolean> getIsIncremental();

    public void all(final Function<List<MinecraftVersion>, List<MinecraftVersion>> filter) throws FileNotFoundException {
        final MinecraftArtifactCacheExtension cacheExtension = project.getExtensions().getByType(MinecraftArtifactCacheExtension.class);
        final File launcherManifest = cacheExtension.cacheLauncherMetadata();

        final Gson gson = new Gson();
        final Manifest manifest = gson.fromJson(new FileReader(launcherManifest), Manifest.class);
        final List<MinecraftVersion> versions = Arrays.stream(manifest.getVersions()).map(v -> MinecraftVersion.from(v.getId())).collect(Collectors.toList());
        Collections.reverse(versions);

        final List<MinecraftVersion> toGenerate = filter.apply(versions);
        getVersionsToGenerate().set(toGenerate.stream().map(MinecraftVersion::toString).collect(Collectors.toList()));
    }

    public void from(final String version, final Predicate<MinecraftVersion> filter) throws FileNotFoundException {
        all(versions -> {
            final MinecraftVersion mcVersion = MinecraftVersion.from(version);
            if (!versions.contains(mcVersion)) {
                throw new IllegalArgumentException("Invalid version: " + version);
            }

            if (versions.indexOf(mcVersion) == versions.size() - 1) {
                return Collections.emptyList();
            }

            return versions.subList(versions.indexOf(mcVersion), versions.size()).stream().filter(filter).collect(Collectors.toList());
        });
    }

    public void after(final String version, final Predicate<MinecraftVersion> filter) throws FileNotFoundException {
        all(versions -> {
            final MinecraftVersion mcVersion = MinecraftVersion.from(version);
            if (!versions.contains(mcVersion)) {
                throw new IllegalArgumentException("Invalid version: " + version);
            }

            if (versions.indexOf(mcVersion) == versions.size() - 1) {
                return Collections.emptyList();
            }

            return versions.subList(versions.indexOf(mcVersion) + 1, versions.size()).stream().filter(filter).collect(Collectors.toList());
        });
    }

    public void fullReleasesFrom(final String version) throws FileNotFoundException {
        from(version, MinecraftSourceExtension::isRelease);
    }

    public void noneSnapshotsFrom(final String version) throws FileNotFoundException {
        from(version, v -> !isSnapshot(v));
    }

    public void allFrom(final String version) throws FileNotFoundException {
        from(version, v -> true);
    }

    public void automaticallyIncrementalFrom(final String version) throws IOException {
        String currentVersion = version;
        boolean exists = false;
        final File versionJsonFile = project.getLayout().getProjectDirectory().dir("game/src/main/resources").file("version.json").getAsFile();
        final File sharedConstantsFile = project.getLayout().getProjectDirectory().dir("game/src/main/java/net/minecraft").file("SharedConstants.java").getAsFile();
        if (versionJsonFile.exists()) {
            try {
                final JsonObject versionJson = new Gson().fromJson(new FileReader(versionJsonFile), JsonObject.class);
                currentVersion = versionJson.get("id").getAsString();
                exists = true;
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Failed to read the version.json file", e);
            }
        } else if (sharedConstantsFile.exists()) {
            final List<String> lines = Files.readAllLines(sharedConstantsFile.toPath());
            final String versionLine = lines.stream().filter(line -> line.contains("public static final String VERSION_STRING")).findFirst().orElse(null);
            if (versionLine != null) {
                currentVersion = versionLine.substring(versionLine.indexOf('"') + 1, versionLine.lastIndexOf('"'));
                exists = true;
            }
        }

        if (exists) {
            getIsIncremental().set(true);
            after(currentVersion, v -> true);
        } else {
            getIsIncremental().set(false);
            from(version, v -> true);
        }
    }

    public void fromFile(final File file) throws IOException {
        final List<String> versions = Files.readAllLines(file.toPath()).stream()
                .filter(l -> !l.startsWith("#"))
                .map(String::trim).collect(Collectors.toList());

        all(minecraftVersions -> minecraftVersions.stream().filter(version -> versions.contains(version.toString())).collect(Collectors.toList()));
    }

    public void fromJson(final File file) throws IOException {
        final JsonObject json = new Gson().fromJson(new FileReader(file), JsonObject.class);
        final JsonArray jsonVersions = json.getAsJsonArray("versions");
        final List<String> versions = IntStream.range(0, jsonVersions.size()).mapToObj(i -> jsonVersions.get(i).getAsString()).collect(Collectors.toList());

        all(minecraftVersions -> minecraftVersions.stream().filter(version -> versions.contains(version.toString())).collect(Collectors.toList()));
    }

    public static boolean isRelease(final MinecraftVersion version) {
        final Object type = ReflectionUtils.get(version, "type");
        final int pre = ReflectionUtils.get(version, "pre");
        return type.toString().equalsIgnoreCase("release") && pre == 0;
    }

    public static boolean isPreRelease(final MinecraftVersion version) {

        final Object type = ReflectionUtils.get(version, "type");
        final int pre = ReflectionUtils.get(version, "pre");
        return type.toString().equalsIgnoreCase("release") && pre > 0;
    }

    public static boolean isReleaseCandidate(final MinecraftVersion version) {

        final Object type = ReflectionUtils.get(version, "type");
        final int pre = ReflectionUtils.get(version, "pre");
        return type.toString().equalsIgnoreCase("release") && pre < 0;
    }

    public static boolean isSnapshot(final MinecraftVersion version) {
        final Object type = ReflectionUtils.get(version, "type");
        return type.toString().equalsIgnoreCase("snapshot");
    }

}
