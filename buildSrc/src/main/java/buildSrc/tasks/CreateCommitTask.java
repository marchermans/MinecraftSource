package buildSrc.tasks;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;

public abstract class CreateCommitTask extends DefaultTask {

    @Input
    public abstract Property<String> getCommitMessage();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void doCreateCommit() throws IOException, GitAPIException {
        final File gitDirectory = getProjectDirectory().get().file(".git").getAsFile();
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDirectory)
                .build();
        if (!gitDirectory.exists()) {
            repository.create();
        }

        final Git git = new Git(repository);
        git.add().addFilepattern(".").call();
        git.add().setUpdate(true).addFilepattern(".").call();
        git.commit().setAll(true).setMessage(getCommitMessage().get()).call();
    }
}
