package Gui;

import Data.*;
import Data.Correlation.CorrelogramStore;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.TreeMap;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
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
    ObservableList<TimeSeries> loadedTimeSeries;
    
    // data file input
    private File file = null;
    FileChooser fileChooser = new FileChooser();
    FileModel fileModel = new FileModel(null, null);
    
    // progress display layer controls (must be set from parent controller, because it blocks the entire input pane)
    ProgressLayer progressLayer;
    
    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    // displays the opened file name
    @FXML private Label selectedFileLabel;
    
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
    
    public void setSharedData(SharedData sharedData){
        
        this.sharedData = sharedData;
        loadedTimeSeries = sharedData.dataModel.getObservableLoadedSeries();
        loadedList.setItems(loadedTimeSeries);
    }
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // bind list views to the available time series and loaded time series list models
//        availableList.setCellFactory(new Callback<ListView<Integer>, ListCell<Integer>>() {
//            @Override public ListCell<Integer> call(ListView<Integer> p) {
//                
//            }
//        });
        availableList.setItems(availableTimeSeries);
        
        // bind label text to currently selected filepath...
        selectedFileLabel.textProperty().bind(fileModel.filenameProperty());
        // ...and enable the load button whenever the path is not null
        fileModel.filenameProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String oldFilepath, String newFilepath) {
                loadButton.setDisable( newFilepath == null );
            }
        });
        
         // enable multi-select on list views
        availableList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        loadedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // enable load on double click
        final FileInputController instance = this; 
        availableList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent t) {
                if( t.getClickCount() == 2){ instance.loadSelected(null); }
            }
        });
        loadedList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent t) {
                if( t.getClickCount() == 2){ instance.unloadSelected(null); }
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
        
        progressLayer.cancelButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) { fileModel.loadFileService.cancel(); }
        });
        
        fileModel.loadFileService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override public void handle(WorkerStateEvent t) {
                
                // clear existing data
                sharedData.correlationSetA.clear();
                sharedData.correlationSetB.clear();
                CorrelogramStore.clear();
                availableTimeSeries.clear();
                sharedData.dataModel.timeSeries.clear();
                
                ArrayList<Integer> toAdd = new ArrayList<>(fileModel.getNumberOfTimeSeries());
                // add representative list entries for the time series
                for (int i = 1; i <= fileModel.getNumberOfTimeSeries(); i++) {
                    toAdd.add(i);
                }
                availableTimeSeries.addAll(toAdd);
                
                // TODO: remove test support
                loadAllButton.fire();
                sharedData.correlationSetA.add(sharedData.dataModel.get(1));
                sharedData.correlationSetB.add(sharedData.dataModel.get(2));
                sharedData.correlationSetB.add(sharedData.dataModel.get(3));
                // /TODO
                
                progressLayer.hide();
            }
        });
        
        fileModel.loadFileService.setOnCancelled(new EventHandler<WorkerStateEvent>() {
            @Override public void handle(WorkerStateEvent t) {
                progressLayer.hide();
                // at this point, no change to the data model should have been made
            }
        });
        fileModel.loadFileService.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent t) {
                progressLayer.hide();

                sharedData.dataModel.clear();
                availableTimeSeries.clear();
            }
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
        
        Map<Integer, TimeSeries> newSeries = new TreeMap<>();
        
        for (Object item : selectionCopy) {
            
            // time series id and visible list elements are both 1-based indices
            Integer tsIndex = (Integer) item;
            
            // create time series and append to data model
            TimeSeries timeSeries = new TimeSeries(tsIndex, fileModel.getXValues(tsIndex), fileModel.getYValues(tsIndex));
            newSeries.put(tsIndex, timeSeries);
            
            availableTimeSeries.remove(tsIndex);
        }
        // by adding all time series in a single step, repeated updates of the observers are avoided
        sharedData.dataModel.timeSeries.putAll(newSeries);
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
            sharedData.dataModel.timeSeries.remove(timeSeries.getId());
            sharedData.previewTimeSeries.remove(timeSeries);
            sharedData.correlationSetA.remove(timeSeries);
            sharedData.correlationSetB.remove(timeSeries);
        }
        availableTimeSeries.sort(null);
    }
    
}
