package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CrossCorrelation;
import Data.DataModel;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

/**
 * Method overview:
 *   compute()
 *      Passes the input parameters (correlation sets, window size, overlap, etc.) to the computation routines.
 *      Handles asynchronous computation of the result.
 *
 * @author Carl Witt
 */
public class StartUpWizardController {
    
    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------
    
    // contains shared data between the linked views (data model, selections, brushed, etc.)
    // is set by the main controller on startup
    private SharedData sharedData;
    
    ProgressLayer progressLayer;

    DataModel dataModel;

    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    // input file separator selection
    @FXML private ToggleGroup nanStrategy;
    @FXML private RadioButton nanSetToZeroRadio;
    @FXML private RadioButton nanLeaveRadio;
    
    @FXML private TextField windowSizeText;
    @FXML private TextField baseWindowOffsetText;
    @FXML private TextField timeLagMinText;
    @FXML private TextField timeLagMaxText;
    @FXML private TextField significanceLevelText;

    @FXML private Button runButton;
    
    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------
    
    public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;
        
        // bind loaded series and correlation input sets
        dataModel = sharedData.dataModel;

        // enable computation run button only if both sets contain at least one element
        ListChangeListener<TimeSeries> checkNonEmpty = change ->
                runButton.setDisable(dataModel.correlationSetA.size() == 0 || dataModel.correlationSetB.size() == 0);
        dataModel.correlationSetA.addListener(checkNonEmpty);
        dataModel.correlationSetB.addListener(checkNonEmpty);
        
        
    }

    public void compute(){
    
        // execute values from text inputs
        int windowSize = Integer.parseInt(windowSizeText.getText());
        int baseWindowOffset = (int) Double.parseDouble(baseWindowOffsetText.getText());
        int tauMin = (int) Double.parseDouble(timeLagMinText.getText());
        int tauMax = (int) Double.parseDouble(timeLagMaxText.getText());
        double significanceLevel = Double.parseDouble(significanceLevelText.getText());

        // the window size must be at least two (otherwise, the pearson correlation coefficient is undefined)
        // and at most the length of the time series (otherwise the correlation matrix will have no columns)
        // TODO: inform the user if the window size is invalid
        windowSize = Math.min(sharedData.dataModel.getTimeSeriesLength(sharedData), Math.max(2, windowSize));

        // display the parsed values
        windowSizeText.setText(""+windowSize); // to display what was parsed
//        baseWindowOffsetText.setText(""+baseWindowOffset); // to display what was parsed
//        timeLagMinText.setText(""+tauMin); // to display what was parsed
//        timeLagMaxText.setText(""+tauMax); // to display what was parsed

        
        // NaN handle strategy
//        Toggle selectedNanStrategy = nanStrategy.getSelectedToggle();
//        CrossCorrelation.NA_ACTION naAction = CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED;
//        if(selectedNanStrategy == nanLeaveRadio){
//            naAction = CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED;
//        } else if(selectedNanStrategy == nanSetToZeroRadio){
//            naAction = CrossCorrelation.NA_ACTION.REPLACE_WITH_ZERO;
//        }

        WindowMetadata metadata = new WindowMetadata(dataModel.correlationSetA, dataModel.correlationSetB, windowSize, tauMin, tauMax, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, significanceLevel);

    }

}
