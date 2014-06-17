package Gui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;

/**
 * Contains controls to indicate the progress - is displayed as an overlay in the tab view (file input, computation input, ...)
 * @author Carl Witt
 */
class ProgressLayer {
    
    public Pane overlay;                // container (has a background color and blocks underlying elements)
    public ProgressBar progressBar;
    public Label messageLabel;          // indicates what is currently happening
    public Button cancelButton;
    
    public void show(){
        overlay.setVisible(true);
        cancelButton.setDisable(false);
    }
    public void hide(){
        overlay.setVisible(false);
        cancelButton.setDisable(false);
    }
    
}
