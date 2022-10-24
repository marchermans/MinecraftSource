package buildSrc.extensions;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.SetProperty;

public abstract class MinecraftSourceExtension {

    private final Project project;

    protected MinecraftSourceExtension(Project project) {
        this.project = project;
    }

    public abstract ListProperty<String> getVersionsToGenerate();
}
