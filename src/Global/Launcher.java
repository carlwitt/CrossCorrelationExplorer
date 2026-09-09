package Global;

/**
 * Entry point that does not itself extend javafx.application.Application, so the
 * JVM's module-path check for JavaFX ("JavaFX runtime components are missing") is
 * bypassed when launching from a fat jar via {@code java -jar}.
 */
public class Launcher {

    public static void main(String[] args) {
        Main.main(args);
    }

}
