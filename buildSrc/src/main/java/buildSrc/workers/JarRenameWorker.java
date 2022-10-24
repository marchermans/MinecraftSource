package buildSrc.workers;

import buildSrc.utils.constants.FartConstants;
import net.minecraftforge.fart.Main;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class JarRenameWorker implements WorkAction<JarRenameWorker.Parameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JarRenameWorker.class);

    private static final List<String> OPTIONS = new ArrayList<>();

    static {
        OPTIONS.add(FartConstants.SRG_FIX);
        OPTIONS.add(FartConstants.ANNOTATION_FIX);
        OPTIONS.add(FartConstants.RECORD_FIX);
        OPTIONS.add(FartConstants.IDS_FIX);
        OPTIONS.add(FartConstants.STRIP_SIGNATURES);
        OPTIONS.add(FartConstants.REVERSE);
    }


    public static abstract class Parameters implements WorkParameters {
        public abstract RegularFileProperty getMappingFile();
        public abstract RegularFileProperty getInputJar();
        public abstract RegularFileProperty getOutputJar();
    }

    @Override
    public void execute() {
        final Parameters parameters = getParameters();

        final List<String> workingParameters = new ArrayList<>(OPTIONS);
        workingParameters.add("--input");
        workingParameters.add(parameters.getInputJar().get().getAsFile().getAbsolutePath());
        workingParameters.add("--output");
        workingParameters.add(parameters.getOutputJar().get().getAsFile().getAbsolutePath());
        workingParameters.add("--map");
        workingParameters.add(parameters.getMappingFile().get().getAsFile().getAbsolutePath());

        try {
            LOGGER.warn("Starting Fart");
            workingParameters.forEach(LOGGER::warn);

            Main.main(workingParameters.toArray(new String[0]));
        } catch (IOException e) {
            throw new RuntimeException("Failed to remap.", e);
        }
    }
}
