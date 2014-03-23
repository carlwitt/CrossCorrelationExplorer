package Gui;

import Data.*;
import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelogramMetadata;
import Data.Correlation.DFT;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.ResourceBundle;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.controlsfx.dialog.Dialogs;

/**
 * Controls the loading of text files and the selection of time series from the text file to work with.
 *
 * @author Carl Witt
 */
public class ComputationInputController implements Initializable {
    
    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------
    
    // contains shared data between the linked views (data model, selections, brushed, etc.)
    // is set by the main controller on startup
    SharedData sharedData;
    
    // time series for computation input. set A and set B are cross correlated (in cross-product manner) and the available series is a view on the data model
    ObservableList<TimeSeries> loadedTimeSeries;
    
    // progress display layer controls (must be set from parent controller, because it blocks the entire input pane)
    public Pane progressPane;               // container (has a background color and blocks underlying elements)
    public ProgressBar progressBar;
    public Label progressLabel;             // status message element (what is happening?)
    public Button progressCancelButton;
    
    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    @FXML private ListView<TimeSeries> setAList;
    @FXML private ListView<TimeSeries> loadedList;
    @FXML private ListView<TimeSeries> setBList;
    
    // input file separator selection
    @FXML private ToggleGroup nanStrategy;
    @FXML private RadioButton nanSetToZeroRadio;
    @FXML private RadioButton nanLeaveRadio;
    
    @FXML private TextField windowSizeText;
    
    // list control buttons
    @FXML private Button loadAllSetAButton;
    @FXML private Button loadSelectedSetAButton;
    @FXML private Button loadSelectedSetBButton;
    @FXML private Button loadRandomSetAButton;
    @FXML private Button unloadAllSetAButton;
    @FXML private Button unloadSelectedSetAButton;
    
    @FXML private Button runButton;
    
    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------
    
    public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;
        loadedTimeSeries = sharedData.dataModel.getObservableLoadedSeries();
        loadedList.setItems(loadedTimeSeries);
        setAList.setItems(sharedData.correlationSetA);
        setBList.setItems(sharedData.correlationSetB);
        loadedList.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<TimeSeries>() {
            @Override public void onChanged(ListChangeListener.Change<? extends TimeSeries> change) {
                sharedData.previewTimeSeries.setAll(loadedList.getSelectionModel().getSelectedItems());
            }
        });
        
        // enable computation run button only if both sets contain at least one element
        ListChangeListener<TimeSeries> checkNonEmpty = new ListChangeListener<TimeSeries>() {
            @Override public void onChanged(ListChangeListener.Change<? extends TimeSeries> change) {
                runButton.setDisable(sharedData.correlationSetA.size() == 0 || sharedData.correlationSetB.size() == 0);
            }
        };
        sharedData.correlationSetA.addListener(checkNonEmpty);
        sharedData.correlationSetB.addListener(checkNonEmpty);
    }
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
         // enable multi-select on list views
        loadedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setAList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setBList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // enable load on double click
        setAList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent t) {
                if( t.getClickCount() == 2){ unloadSelected(new ActionEvent(unloadSelectedSetAButton, null)); }
            }
        });
        setBList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent t) {
                if( t.getClickCount() == 2){ unloadSelected(new ActionEvent(null, null)); }
            }
        });
        
    }
    
    public void loadedListKeyTyped(KeyEvent t) {
        if(t.getCode() ==  KeyCode.LEFT){
            loadSelectedSetAButton.fire();
        } else if(t.getCode() ==  KeyCode.RIGHT){
            loadSelectedSetBButton.fire();
        }
    }
    
    public void compute(){
    
        int windowSize = Integer.parseInt(windowSizeText.getText());
        windowSizeText.setText(""+windowSize); // to display what was parsed
        
        Toggle selectedNanStrategy = nanStrategy.getSelectedToggle();
        DFT.NA_ACTION naAction = DFT.NA_ACTION.LEAVE_UNCHANGED;
        if(selectedNanStrategy == nanLeaveRadio){
            naAction = DFT.NA_ACTION.LEAVE_UNCHANGED;
        } else if(selectedNanStrategy == nanSetToZeroRadio){
            naAction = DFT.NA_ACTION.REPLACE_WITH_ZERO;
        }
        
        CorrelogramMetadata metadata = new CorrelogramMetadata(sharedData.correlationSetA, sharedData.correlationSetB, windowSize, naAction);
        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();
        sharedData.setcorrelationMatrix(result);
    }
    
    // -------------------------------------------------------------------------
    // time series list controls 
    // -------------------------------------------------------------------------
    
    // loads all time series from the file into the data model
    public void loadAll(ActionEvent e){
        ListView<TimeSeries> sourceView = loadedList;
        ObservableList<TimeSeries> targetList;
        if(e.getSource() == loadAllSetAButton){
            targetList = sharedData.correlationSetA;
        } else  /*if(e.getSource() == loadAllSetBButton)*/{
            targetList = sharedData.correlationSetB;
        }
        sourceView.getSelectionModel().selectAll();
        loadSelected(sourceView, targetList);
    }
    // loads a random number of available time series
    public void loadRandom(ActionEvent e){
        
        ListView<TimeSeries> sourceView = loadedList;
        ObservableList<TimeSeries> targetList;
        if(e.getSource() == loadRandomSetAButton){
            targetList = sharedData.correlationSetA;
        } else  /*if(e.getSource() == loadRandomSetBButton)*/{
            targetList = sharedData.correlationSetB;
        }
        
        String response = Dialogs.create()
                .title("Number of random time series")
                .showTextInput("200");
        int number = Integer.parseInt(response);
        
        sourceView.getSelectionModel().clearSelection();
        
        ArrayList<TimeSeries> availableValues = new ArrayList<>(loadedTimeSeries);
        ArrayList<TimeSeries> randomSelection = new ArrayList<>(number);
        
        // pick random elements
        Random r = new Random(System.currentTimeMillis());
        while(number > 0 && availableValues.size() > 0){
            int randomIndex = r.nextInt(availableValues.size());
            randomSelection.add(availableValues.get(randomIndex));
            availableValues.remove(randomIndex);
            number--;
        }
        
        // select those in the 
        for (TimeSeries element : randomSelection) {
            sourceView.getSelectionModel().select(element);
        }
        loadSelected(sourceView, targetList);
    }
    public void loadSelected(ActionEvent e){
        ListView<TimeSeries> sourceView = loadedList;
        ObservableList<TimeSeries> targetList;
        if(e.getSource() == loadSelectedSetAButton){
            targetList = sharedData.correlationSetA;
        } else  /*if(e.getSource() == loadSelectedSetBButton)*/{
            targetList = sharedData.correlationSetB;
        }
        loadSelected(sourceView, targetList);
    }
    public void loadSelected(ListView<TimeSeries> sourceView, ObservableList<TimeSeries> targetList){
        // make a copy of the selection, because removing list items changes the selection
        Object[] selectionCopy = sourceView.getSelectionModel().getSelectedItems().toArray();
        
        ArrayList<TimeSeries> newIndices = new ArrayList<>();
        
        for (Object item : selectionCopy) {
            // time series id and visible list elements are both 1-based indices
            TimeSeries ts = (TimeSeries) item;
            if(targetList.contains(ts)){
                continue; // don't add time series more than once to a correlation input set
            }
            newIndices.add(ts);
        }
        // by adding all time series in a single step, repeated updates of the observers are avoided
        targetList.addAll(newIndices);
        targetList.sort(null);
    }
    
    // removes all loaded time series from the data model
    public void unloadAll(ActionEvent e){
        ListView<TimeSeries> sourceView;
        if(e.getSource() == unloadAllSetAButton){
            sourceView = setAList;
        } else  /*if(e.getSource() == unloadAllSetBButton)*/{
            sourceView = setBList;
        }
        sourceView.getSelectionModel().selectAll();
        unloadSelected(sourceView);
    }
    public void unloadSelected(ActionEvent e){
        ListView<TimeSeries> sourceView;
        if(e.getSource() == unloadSelectedSetAButton){
            sourceView = setAList;
        } else  /*if(e.getSource() == unloadSelectedSetBButton)*/{
            sourceView = setBList;
        }
        unloadSelected(sourceView);
    }
    // removes the selected time series
    public void unloadSelected(ListView<TimeSeries> sourceView){
        // make a copy of the selection, because removing list items changes the selection
        Object[] selectionCopy = sourceView.getSelectionModel().getSelectedItems().toArray();
        for (Object item : selectionCopy) {
            sourceView.getItems().remove((TimeSeries) item);
        }
    }
    
}
