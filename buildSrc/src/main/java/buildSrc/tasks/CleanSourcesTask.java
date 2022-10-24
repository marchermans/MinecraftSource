package buildSrc.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

public abstract class CleanSourcesTask extends DefaultTask {

    public CleanSourcesTask() {
        getProject().getTasks().configureEach(task -> {
            if (task instanceof CleanSourcesTask)
                return;

            if (task instanceof Delete)
                return;

            task.mustRunAfter(this);
        });
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDirectoryToClean();

    @TaskAction
    public void clean() {
        final File directory = getDirectoryToClean().get().getAsFile();
        if (directory.exists()) {
            setDidWork(directory.delete());
        }
    }
}
