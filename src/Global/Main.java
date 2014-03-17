package Global;

import Gui.MainWindowController;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class Main extends Application {

    public static void main(String[] args) {
        Application.launch(Main.class, (java.lang.String[])null);
    }

    @Override
    public void start(Stage primaryStage) {
        
        try {
            BorderPane rootNode = (BorderPane) FXMLLoader.load(MainWindowController.class.getResource("MainWindow.fxml"));          
            Scene scene = new Scene(rootNode);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Cross Correlation Explorer");
            primaryStage.show();
        } catch (Exception ex) {
            Logger.getLogger(Gui.MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
        }
        

    }    
     
}
