package buildSrc.tasks;

import buildSrc.extensions.MinecraftArtifactCacheExtension;
import buildSrc.utils.ArtifactSide;
import buildSrc.utils.GameArtifact;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Map;

public abstract class MinecraftDownloadingTask extends DefaultTask {

    @Input
    public abstract Property<String> getMinecraftVersion();

    @OutputFiles
    public abstract MapProperty<GameArtifact, File> getOutputFiles();

    @TaskAction
    public void doDownload() throws Exception
    {
        final String minecraftVersion = getMinecraftVersion().get();
        final MinecraftArtifactCacheExtension cacheExtension = getProject().getExtensions().getByType(MinecraftArtifactCacheExtension.class);

        getOutputFiles().set(cacheExtension.cacheGameVersion(minecraftVersion, ArtifactSide.JOINED));
    }
}
