package Global;

import javafx.application.Platform;

import java.util.Locale;

/**
 * Contains global variables that influence the program behavior.
 * @author Carl Witt
 */
public class RuntimeConfiguration {
    /** controls the verbosity of the console output (progress feedback, cache behavior, etc.) */
    public static final boolean VERBOSE = true;

    public static Locale DEFAULT_LOCALE = Locale.ENGLISH;

    public static void configure(){

        Locale.setDefault(DEFAULT_LOCALE);

        Platform.setImplicitExit(true);

    }
}
