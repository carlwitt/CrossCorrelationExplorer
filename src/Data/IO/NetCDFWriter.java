package Data.IO;

/**
 * Base class to help with versioning of the custom NetCDF file format for experimental results.
 * Helps warning the user if she tries to use a file that has been written with a previous version of the program.
 * Created by Carl Witt on 09.08.14.
 */
public class NetCDFWriter {

    /** To detect conflicts in the file structure, arising from the evolution of the file format. */
    public static final String VERSION_NUMBER = "0.8";

}
