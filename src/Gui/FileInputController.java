package Gui;

import Data.ComplexSequence;
import Data.DataModel;
import Data.FileModel;
import Data.SharedData;
import Data.TimeSeries;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.ResourceBundle;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.Dialogs;

/**
 * Controls the loading of text files and the selection of time series from the text file to work with.
 *
 * @author Carl Witt
 */
public class FileInputController implements Initializable {
    
    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------
    
    // contains shared data between the linked views (data model, selections, brushed, etc.)
    // is set by the main controller on startup
    SharedData sharedData;
    
    // time series available in the loaded file. partitioned into available (in file) and loaded (in data model) time series.
    ObservableList<Integer> availableTimeSeries = FXCollections.observableArrayList();
    ObservableList<Integer> loadedTimeSeries = FXCollections.observableArrayList();
    
    // data file input
    private File file = null;
    FileChooser fileChooser = new FileChooser();
    FileModel fileModel = new FileModel(null, null);
    
    // progress display layer controls (must be set from parent controller, because it blocks the entire input pane)
    public Pane progressPane;               // container (has a background color and blocks underlying elements)
    public ProgressBar progressBar;
    public Label progressLabel;             // status message element (what is happening?)
    public Button progressCancelButton;
    
    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    // displays the opened file name
    @FXML private Label selectedFileLabel;
    
    // displayes the time series contained in the file and which one the user wants to work with
    @FXML private ListView availableList;
    @FXML private ListView loadedList;
    
    // input file separator selection
    @FXML private ToggleGroup separatorSelection;
    @FXML private RadioButton fixedWidthSeparatorRadio;
    @FXML private TextField fixedWidthSeparatorText;
    @FXML private RadioButton characterSeparatorRadio;
    @FXML private RadioButton tabSeparatorRadio;
    @FXML private TextField characterSeparatorText;
    
    @FXML private Button loadButton;
    
    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------
    
    public void setSharedData(SharedData sharedData){
        
        this.sharedData = sharedData;

        // the list of loaded time series listens to changes in the data model
        sharedData.timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
//                System.out.println(String.format("Map change: key %s value added %s value removed %s", change.getKey(), change.getValueAdded(),change.getValueRemoved()));
                if(change.wasRemoved() && ! change.wasAdded()){
                    loadedTimeSeries.remove(change.getKey());
                } else if( ! change.wasRemoved() && change.wasAdded()){
                    loadedTimeSeries.add(change.getKey());
                }
            }
        });
    
    }
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
        // bind list views to the available time series and loaded time series list models
        availableList.setItems(availableTimeSeries);
        loadedList.setItems(loadedTimeSeries);
        
        // enable multi-select on list views
        availableList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // bind label text to currently selected filepath...
        selectedFileLabel.textProperty().bind(fileModel.filenameProperty());
        // ...and enable the load button whenever the path is not null
        fileModel.filenameProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String oldFilepath, String newFilepath) {
                loadButton.setDisable( newFilepath == null );
            }
        });
        
        // update separator on text change in on of the radio buttons text fields
        characterSeparatorText.textProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String t, String newCharacter) {
                if(!newCharacter.equals("")){
                    fileModel.setSeparatorCharacter(newCharacter);
                    characterSeparatorRadio.selectedProperty().set(true);
                }
            }
        });
        fixedWidthSeparatorText.textProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String t, String newWidth) {
                if( ! newWidth.equals("")){
                    fileModel.setSeparatorWidth(Integer.parseInt(newWidth));
                    fixedWidthSeparatorRadio.selectedProperty().set(true);
                }
            }
        });
        // manually update separator when selecting the tab property
        separatorSelection.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            @Override public void changed(ObservableValue<? extends Toggle> ov, Toggle oldRadioButton, Toggle newRadioButton) {
                if(newRadioButton == tabSeparatorRadio){
                    fileModel.setSeparatorCharacter("\t");
                }
            }
        });
        
        // set up radio button usability details
        // TODO: these handlers would be better implemented in scripting (no important logic going on)
        // activate radio option when focussing the input
        // when selecting a separator radio option, propagate the focus to the input field
        characterSeparatorRadio.focusedProperty().addListener(new ChangeListener<Object>() {
            @Override public void changed(ObservableValue<? extends Object> ov, Object t, Object t1) {
                characterSeparatorText.requestFocus();
            }
        });
        fixedWidthSeparatorRadio.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                fixedWidthSeparatorText.requestFocus();
            }
        });
    }
    
    // -------------------------------------------------------------------------
    // input file loading logic 
    // -------------------------------------------------------------------------
    
    // pick a file for loading 
    public void selectFile(ActionEvent e){
        // start in the directory where the last file was loaded
        if(file != null)
            fileChooser.setInitialDirectory(file.getParentFile());
        
        fileChooser.setTitle("Open Time Series Data File");
        file = fileChooser.showOpenDialog(null);
        
        // also updates display and loader service
        fileModel.filenameProperty().set( file != null ? file.getAbsolutePath() : null);
    }
    
    // load the file, applying the separator options
    public void loadFile(ActionEvent e) {
    
        if(separatorSelection.getSelectedToggle() == fixedWidthSeparatorRadio){
            fileModel.setSeparatorWidth(Integer.parseInt(fixedWidthSeparatorText.getText()));
        } else if(separatorSelection.getSelectedToggle() == characterSeparatorRadio) {
            fileModel.setSeparatorCharacter(characterSeparatorText.getText());
        } else if(separatorSelection.getSelectedToggle() == tabSeparatorRadio) {
            fileModel.setSeparatorCharacter("\t");
        }
        
        fileModel.loadFileService.reset();
        
        progressCancelButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) {
                fileModel.loadFileService.cancel();
            }
        });
        
        fileModel.loadFileService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent t) {
                
                // TODO: should be encapsulated in a separate class that can be used to control the UI
                progressPane.setVisible(false);
                progressCancelButton.setDisable(false);
                availableTimeSeries.clear();
                sharedData.timeSeries.clear();
                
                // add representative list entries for the time series
                for (int i = 1; i <= fileModel.getNumberOfTimeSeries(); i++) {
                    availableTimeSeries.add(i);
                }
            }
        });
        
        fileModel.loadFileService.setOnCancelled(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent t) {
                progressPane.setVisible(false);
                progressCancelButton.setDisable(false);
                // at this point, no change to the data model should have been made
//                availableTimeSeries.clear();
//                loadedTimeSeries.clear();
            }
        });
        
        progressPane.setVisible(true);
//        progressCancelButton.setDisable(true);
        
        progressBar.progressProperty().bind(fileModel.loadFileService.progressProperty());
        progressLabel.textProperty().bind(fileModel.loadFileService.messageProperty());
        fileModel.loadFileService.start();
        
    }
    
    // -------------------------------------------------------------------------
    // time series list controls 
    // -------------------------------------------------------------------------
    
    // loads all time series from the file into the data model
    public void loadAll(ActionEvent e){
        availableList.getSelectionModel().selectAll();
        loadSelected(e);
    }
    // loads a random number of available time series
    public void loadRandom(ActionEvent e){
        String response = Dialogs.create()
                .title("Number of random time series")
                .showTextInput("200");
        int number = Integer.parseInt(response);
        
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
        loadSelected(e);
    }
    public void loadSelected(ActionEvent e){
        // make a copy of the selection, because removing list items changes the selection
        Object[] selectionCopy = availableList.getSelectionModel().getSelectedItems().toArray();
        for (Object item : selectionCopy) {
            
            // time series id and visible list elements are both 1-based indices
            Integer tsIndex = (Integer) item;
            
            // create time series and append to data model
            TimeSeries timeSeries = new TimeSeries(tsIndex, fileModel.getXValues(tsIndex), fileModel.getYValues(tsIndex));
            sharedData.timeSeries.put(tsIndex, timeSeries);
            
            availableTimeSeries.remove(tsIndex);
        }
        loadedTimeSeries.sort(null);
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
            Integer tsIndex = (Integer) item;
            availableTimeSeries.add(tsIndex); // insert sorted
            sharedData.timeSeries.remove(tsIndex);
        }
        availableTimeSeries.sort(null);
    }
    
}
