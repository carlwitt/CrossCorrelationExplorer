package Gui;

import Data.IO.FileModel;
import Data.SharedData;
import Data.TimeSeries;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.Dialogs;

import java.net.URL;
import java.util.*;

/**
 * Controls the loading of text files and the selection of time series from the text file to work with.
 * For correlation input set A there's a file input controller and for set B there's another one.
 *
 *   (un)loadAll(), (un)loadSelected() and loadRandom()
 *      Manages the GUI elements to assign/remove time series to input set A and B.
 *
 * @author Carl Witt
 */
public class FileInputController implements Initializable {
    private static final int MAX_RECENT_FILES = 10;

    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------
    
    // contains shared data between the linked views (data model, selections, brushed, etc.)
    // is set by the main controller on startup
    private SharedData sharedData;
    
    /** time series available in the loaded file. storing them as overvable list makes displaying them in a list view very easy. */
    private final ObservableList<Integer> availableTimeSeries = FXCollections.observableArrayList();

    /** the list to which selected time series are added. */
    private ObservableList<TimeSeries> targetCollection;

    // progress display layer controls (must be set from parent controller, because it blocks the entire input pane)
    private ProgressLayer progressLayer;
    
    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    // displays the opened file name
    @FXML private Label selectedFileLabel;
    @FXML protected SplitMenuButton fileChooserButton;

    // displayes the time series contained in the file and which one the user wants to work with
    @FXML protected ListView<Integer> availableList;
    @FXML private ListView<TimeSeries> loadedList;
    
    // input file separator selection
    @FXML private ToggleGroup separatorSelection;
    @FXML private RadioButton fixedWidthSeparatorRadio;
    @FXML private TextField fixedWidthSeparatorText;
    @FXML private RadioButton characterSeparatorRadio;
    @FXML private RadioButton tabSeparatorRadio;
    @FXML private TextField characterSeparatorText;
    
    @FXML protected Button loadAllButton;           // adds all time series to the data model
    @FXML protected Button loadButton;              // loads the file
    
    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------

    /**
     * @param sharedData shared business logic objects
     * @param targetCollection the list where loaded time series should be put
     */
    public void setSharedData(SharedData sharedData, ObservableList<TimeSeries> targetCollection){
        this.sharedData = sharedData;
        this.targetCollection = targetCollection;
    }

    // data file input
    private final FileChooser fileChooser = new FileChooser();
    private final FileModel fileModel = new FileModel(null, null);

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        fileChooserButton.getItems().clear();

        availableList.setItems(availableTimeSeries);
        loadedList.setItems(targetCollection);

        // bind label text to currently selected filepath...
        selectedFileLabel.textProperty().bind(fileModel.filenameProperty());
        // ...and enable the load button whenever the path is not null
        fileModel.filenameProperty().addListener((ov, oldFilepath, newFilepath) -> loadButton.setDisable( newFilepath == null ));
        
         // enable multi-select on list views
        availableList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // enable load on double click
        final FileInputController instance = this; 
        availableList.setOnMouseClicked(t -> {
            if( t.getClickCount() == 2){ instance.loadSelected(); }
        });
        loadedList.setOnMouseClicked(t -> {
            if( t.getClickCount() == 2){ instance.unloadSelected(null); }
        });
        

        
        // set up radio button usability details
        // TODO: these handlers would be better implemented in scripting (no important logic going on)
        // activate radio option when focussing the input
        // when selecting a separator radio option, propagate the focus to the input field
        characterSeparatorRadio.focusedProperty().addListener((ov, t, t1) -> characterSeparatorText.requestFocus());
        fixedWidthSeparatorRadio.focusedProperty().addListener((ov, t, t1) -> fixedWidthSeparatorText.requestFocus());
        
    }
    
    // -------------------------------------------------------------------------
    // input file loading logic 
    // -------------------------------------------------------------------------
    

    // load the file, applying the separator options
    public void loadFile() {
    
        if(separatorSelection.getSelectedToggle() == fixedWidthSeparatorRadio){
            fileModel.setSeparatorWidth(Integer.parseInt(fixedWidthSeparatorText.getText()));
        } else if(separatorSelection.getSelectedToggle() == characterSeparatorRadio) {
            fileModel.setSeparatorCharacter(characterSeparatorText.getText());
        } else if(separatorSelection.getSelectedToggle() == tabSeparatorRadio) {
            fileModel.setSeparatorCharacter("\t");
        }
        
        fileModel.loadFileService.reset();
        
        progressLayer.cancelButton.setOnAction(t -> fileModel.loadFileService.cancel());
        
        fileModel.loadFileService.setOnSucceeded(t -> {

            // clear existing data
            targetCollection.clear();
            availableTimeSeries.clear();
            targetCollection.clear();
            // remove all previously loaded time series
//            for(TimeSeries ts : targetCollection)
//                sharedData.dataModel.remove(ts.getId());

            ArrayList<Integer> toAdd = new ArrayList<>(fileModel.getNumberOfTimeSeries());
            // add representative list entries for the time series
            for (int i = 1; i <= fileModel.getNumberOfTimeSeries(); i++) {
                toAdd.add(i);
            }
            availableTimeSeries.addAll(toAdd);

            progressLayer.hide();
        });
        
        fileModel.loadFileService.setOnCancelled(t -> progressLayer.hide());
        fileModel.loadFileService.setOnFailed(t -> {
            progressLayer.hide();

//            sharedData.dataModel.clear();
            availableTimeSeries.clear();
        });
        
        progressLayer.show();
        
        progressLayer.progressBar.progressProperty().bind(fileModel.loadFileService.progressProperty());
        progressLayer.messageLabel.textProperty().bind(fileModel.loadFileService.messageProperty());
        
        fileModel.loadFileService.start();
        
    }
    
    // -------------------------------------------------------------------------
    // time series list controls 
    // -------------------------------------------------------------------------
    
    // loads all time series from the file into the data model
    public void loadAll(ActionEvent e){
        availableList.getSelectionModel().selectAll();
        loadSelected();
    }
    // loads a random number of available time series
    public void loadRandom(ActionEvent e){
        Optional<String> response = Dialogs.create()
                .title("Number of random time series")
                .showTextInput("200");
        if(! response.isPresent()) return;
        int number = Integer.parseInt(response.get());
        
        availableList.getSelectionModel().clearSelection();
        
        ArrayList<Integer> availableValues = new ArrayList<>(availableTimeSeries);
        ArrayList<Integer> randomSelection = new ArrayList<>(number);
        
        // pick random elements
        Random r = new Random(System.currentTimeMillis());
        while(number > 0 && availableValues.size() > 0){
            int randomIndex = r.nextInt(availableValues.size());
            randomSelection.add(availableValues.get(randomIndex));
            availableValues.remove(randomIndex);
            number--;
        }
        
        // select those in the 
        for (Integer element : randomSelection) {
            availableList.getSelectionModel().select(element);
        }
        loadSelected();
    }
    public void loadSelected(){
        // make a copy of the selection, because removing list items changes the selection
        Object[] selectionCopy = availableList.getSelectionModel().getSelectedItems().toArray();
        
        Map<Integer, TimeSeries> newSeries = new TreeMap<>();
        
        for (Object item : selectionCopy) {
            
            // time series id and visible list elements are both 1-based indices
            Integer tsOffset = (Integer) item;
            // create time series and append to data model
            TimeSeries timeSeries = new TimeSeries(1, fileModel.getXValues(), fileModel.getYValues(tsOffset));
            newSeries.put(timeSeries.getId(), timeSeries);
            availableTimeSeries.remove(tsOffset);
        }

        // by adding all time series in a single step, repeated updates of the observers are avoided
//        sharedData.dataModel.timeSeries.putAll(newSeries);
        targetCollection.addAll(newSeries.values());
        loadedList.setItems(targetCollection);
//        Logger.getLogger(FileInputController.class.getName()).log(Level.INFO, String.format("targetCollection %s (%s elements)",targetCollection, targetCollection.size()));
    }
    
    // removes all loaded time series from the data model
    public void unloadAll(ActionEvent e){
        loadedList.getSelectionModel().selectAll();
        unloadSelected(e);
    }
    // removes the selected time series
    public void unloadSelected(ActionEvent e){
        // make a copy of the selection, because removing list items changes the selection
        Object[] selectionCopy = loadedList.getSelectionModel().getSelectedItems().toArray();
        for (Object item : selectionCopy) {
            TimeSeries timeSeries = (TimeSeries) item;
            availableTimeSeries.add(timeSeries.getId()); // insert sorted
//            sharedData.dataModel.timeSeries.remove(timeSeries.getId());
            sharedData.previewTimeSeries.remove(timeSeries);
            sharedData.experiment.dataModel.correlationSetA.remove(timeSeries);
            sharedData.experiment.dataModel.correlationSetB.remove(timeSeries);
        }
        availableTimeSeries.sort(null);
    }
    
}
