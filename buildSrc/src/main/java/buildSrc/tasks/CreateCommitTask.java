package buildSrc.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public abstract class CreateCommitTask extends DefaultTask {

    @Input
    public abstract Property<String> getCommitMessage();

    public void doCreateCommit() {

    }
}
