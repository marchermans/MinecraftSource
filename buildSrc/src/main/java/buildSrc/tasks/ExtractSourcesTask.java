package buildSrc.tasks;

import buildSrc.utils.Utils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;

public abstract class ExtractSourcesTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDecompiledJar();

    @OutputDirectory
    public abstract DirectoryProperty getSourceFilesDirectory();
    @OutputDirectory
    public abstract DirectoryProperty getResourceFilesDirectory();

    @TaskAction
    public void doExtract() throws IOException {
        Utils.extractZip(getDecompiledJar().get().getAsFile(), getSourceFilesDirectory().get().getAsFile(), true, true);

        Utils.moveFiles(getSourceFilesDirectory().get(), getResourceFilesDirectory().get(), filter -> {
            filter.include("assets/**");
            filter.include("data/**");
            filter.include("META-INF/**");
            filter.include("*.*");
        });
    }
}
