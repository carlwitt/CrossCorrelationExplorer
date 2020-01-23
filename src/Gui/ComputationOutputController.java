package Gui;

import Data.*;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

/**
 * Controls the loading of text files and the selection of time series from the text file to work with.
 *
 * @author Carl Witt
 */
public class ComputationOutputController implements Initializable {
    
    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------

    ProgressLayer progressLayer;
    
    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------

    @FXML private Button saveCorrelogramButton;
    
    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------

    private CorrelogramController correlogramController;
    public void setCorrelogramController(CorrelogramController correlogramController){
        this.correlogramController = correlogramController;
    }

    public void setSharedData(final SharedData sharedData){

        SharedData sharedData1 = sharedData;

    }
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        

    }

    // -------------------------------------------------------------------------
    // image output
    // -------------------------------------------------------------------------

    public void saveCorrelogramImage(){

        correlogramController.saveCorrelogramImage("./out.png");

    }

    
}
