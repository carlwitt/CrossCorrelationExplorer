package Gui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Common fields and methods for application windows (e.g. startup wizard, help window, main window).
 * Created by Carl Witt on 30.06.14.
 */
public class WindowController {
    protected Stage stage;

    public void createStage(Parent root, String stageTitle){
        stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle(stageTitle);
    }

    public void showWindow(){
        stage.show();
        stage.toFront();
    }

    void hideWindow(){
        stage.hide();
    }

}
