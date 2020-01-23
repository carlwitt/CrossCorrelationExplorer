package Gui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
* Handles the process of putting tab contents into an extra window and back.
 * This involves adding a button to the tab
*/
class TabToWindowManager {

    Tab tabToManage;
    Stage tabStage = new Stage();
    BorderPane borderPane = new BorderPane();
    Scene scene = new Scene(borderPane);
    Button restoreButton = new Button("Restore Tab");
    StackPane restorePane = new StackPane(restoreButton);
    ImageView imageView = new ImageView(getClass().getResource("img/glyphicons_211_right_arrow.png").toExternalForm());
    Button expandButton = new Button("", imageView);

    public TabToWindowManager(Tab tabToManage){

        this.tabToManage = tabToManage;

        tabStage.setAlwaysOnTop(true);

        // add expand/collapse button
        imageView.setRotate(315);
        imageView.setFitWidth(8); imageView.setFitHeight(8);

        expandButton.setMinWidth(0);
        expandButton.setMinHeight(0);
        expandButton.setMaxWidth(15);
        expandButton.setMaxHeight(15);

        tabStage.setOnCloseRequest(this::hideWindow);
        restoreButton.setOnAction(event -> hideWindow(null));
        tabStage.setScene(scene);

        tabToManage.setGraphic(expandButton);
        expandButton.setOnAction(this::expandWindow);

    }

    public void expandWindow(ActionEvent e){
        if(expandButton.isDisabled()) return;
        expandButton.setDisable(true);
        Node content = tabToManage.getContent();
        borderPane.setCenter(content);
        tabToManage.setContent(restorePane);

        tabStage.show();
    }

    public void hideWindow(WindowEvent e){
        expandButton.setDisable(false);
        tabToManage.setContent(borderPane.getCenter());
        tabStage.hide();
    }

    /** @return whether the tab content is currently floating in a window of its own or docked to its initial tab pane. */
    public boolean isDockedOff(){
        return expandButton.isDisabled();
    }

    public void close(){
        tabStage.hide();
    }

}
