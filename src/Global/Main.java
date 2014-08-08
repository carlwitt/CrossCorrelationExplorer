package Global;

import Gui.HelpWindowController;
import Gui.MainWindowController;
import Gui.StartUpWizardController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {

    public static void main(String[] args) {

        RuntimeConfiguration.configure();
        Application.launch(Main.class, (java.lang.String[])null);

    }

    @Override
    public void start(Stage primaryStage) {

        FXMLLoader mainWindowLoader    = new FXMLLoader(MainWindowController.class.getResource("fxml/MainWindow.fxml")),
                helpWindowLoader    = new FXMLLoader(MainWindowController.class.getResource("fxml/HelpWindow.fxml")),
                startUpWizardLoader = new FXMLLoader(MainWindowController.class.getResource("fxml/StartUpWizard.fxml"));

        try {
            // create controllers and their windows
            Parent mainWindowRoot = mainWindowLoader.load();
            MainWindowController mainWindowController = mainWindowLoader.<MainWindowController>getController();
            mainWindowController.createStage(mainWindowRoot, "Cross Correlation Explorer");

            Parent helpWindowRoot = helpWindowLoader.load();
            HelpWindowController helpWindowController = helpWindowLoader.<HelpWindowController>getController();
            helpWindowController.createStage(helpWindowRoot, "Help");

            Parent startUpWizardRoot = startUpWizardLoader.load();
            StartUpWizardController startUpWizardController = startUpWizardLoader.<StartUpWizardController>getController();
            startUpWizardController.createStage(startUpWizardRoot, "Create or Load Experiment Files");

            // link controllers with each other
            mainWindowController.helpWindowController = helpWindowController;
            mainWindowController.startUpWizardController = startUpWizardController;
            mainWindowController.globalMain = this;

            startUpWizardController.helpWindowController = helpWindowController;
            startUpWizardController.mainWindowController = mainWindowController;

            // show only the start up wizard when launching the program
            startUpWizardController.showWindow();

        } catch (Exception ex) {
            Logger.getLogger(Gui.MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
        

    }

}
