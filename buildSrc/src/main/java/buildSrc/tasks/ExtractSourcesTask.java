package buildSrc.tasks;

import buildSrc.utils.Utils;
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
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void doExtract() throws IOException {
        Utils.extractZip(getDecompiledJar().get().getAsFile(), getOutputDirectory().get().getAsFile(), true, true);
    }
}
