package buildSrc.workers;

import buildSrc.utils.constants.FartConstants;
import net.minecraftforge.fart.Main;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class JarRenameWorker implements WorkAction<JarRenameWorker.Parameters> {

    static final Set<String> OPTIONS = new HashSet<>();
    private static final String TRUE = "1";
    private static final String FALSE = "0";

    static {
        JarDecompileWorker.OPTIONS.put(FartConstants.SRG_FIX, TRUE);
    }


    public static abstract class Parameters implements WorkParameters {
        public abstract RegularFileProperty getMappingFile();
        public abstract RegularFileProperty getInputJar();
        public abstract RegularFileProperty getOutputJar();
    }

    @Override
    public void execute() {
        final Parameters parameters = getParameters();

        final Set<String> workingParameters = new HashSet<>(OPTIONS);
        workingParameters.add("--input");
        workingParameters.add(parameters.getInputJar().get().getAsFile().getAbsolutePath());
        workingParameters.add("--output");
        workingParameters.add(parameters.getOutputJar().get().getAsFile().getAbsolutePath());
        workingParameters.add("--map");
        workingParameters.add(parameters.getMappingFile().get().getAsFile().getAbsolutePath());

        try {
            Main.main(workingParameters.toArray(new String[0]));
        } catch (IOException e) {
            throw new RuntimeException("Failed to remap.", e);
        }
    }
}
