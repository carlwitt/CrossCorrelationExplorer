package Gui;

import Data.DataModel;
import Data.SharedData;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;

/**
 * FXML Controller class
 *
 * @author Carl Witt
 */
public class MainController implements Initializable {

    private SharedData sharedData;
    
    // progress display layer
    @FXML private Pane progressPane;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button progressCancelButton;
    
    // controller responsible for loading files and filling the data model
    @FXML private FileInputController fileInputController;
    @FXML private TimeSeriesViewController timeSeriesViewController;
    
    // file input: time series selection lists
    
    
    public DataModel dataModel;
    
    /**
     * Called after the controls have been parsed from the XML. Sets up logic and components that could not be set up using the GUI builder.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
        sharedData = new SharedData();
        
        fileInputController.setSharedData(sharedData);
        timeSeriesViewController.setSharedData(sharedData);
        
        fileInputController.progressPane = progressPane;
        fileInputController.progressBar = progressBar;
        fileInputController.progressLabel = progressLabel;
        fileInputController.progressCancelButton = progressCancelButton;
        
    }
    
}
