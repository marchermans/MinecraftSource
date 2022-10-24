package buildSrc.utils.constants;

public final class FartConstants {

    private FartConstants() {
        throw new IllegalStateException("Can not instantiate an instance of: FartConstants. This is a utility class");
    }

    public static final String SRG_FIX = "--src-fix";
    public static final String ANNOTATION_FIX = "--ann-fix";
    public static final String RECORD_FIX = "--record-fix";
    public static final String IDS_FIX = "--ids-fix";
    public static final String STRIP_SIGNATURES = "--strip-sigs";
    public static final String REVERSE = "--reverse";
}
