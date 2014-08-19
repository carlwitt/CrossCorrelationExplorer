package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.DataModel;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.controlsfx.control.CheckListView;
import org.controlsfx.dialog.Dialogs;

import java.net.URL;
import java.util.*;

/**
 * Method overview:
 *   compute()
 *      Passes the input parameters (correlation sets, window size, overlap, etc.) to the computation routines.
 *      Handles asynchronous computation of the result.
 *
 * @author Carl Witt
 */
public class ComputationController implements Initializable {
    
    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------
    
    // contains shared data between the linked views (data model, selections, brushed, etc.)
    // is set by the main controller on startup
    private SharedData sharedData;
    
    private DataModel dataModel;

    private TimeSeriesSelector setASelector;
    private TimeSeriesSelector setBSelector;

    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    // input file separator selection
//    @FXML private ToggleGroup nanStrategy;
//    @FXML private RadioButton nanSetToZeroRadio;
//    @FXML private RadioButton nanLeaveRadio;
    
    @FXML private GridPane inputGridPane;

    @FXML private TextField windowSizeText;
    @FXML private TextField baseWindowOffsetText;
    @FXML private TextField timeLagMinText;
    @FXML private TextField timeLagMaxText;
    @FXML private TextField timeLagStepText;
    @FXML private TextField significanceLevelText;

    @FXML private Button setAAllButton;
    @FXML private Button setANoneButton;
    @FXML private Button setARandomButton;
    @FXML private Button setBAllButton;
    @FXML private Button setBNoneButton;
    @FXML private Button setBRandomButton;

    @FXML private Button runButton;

    @FXML private TableView<WindowMetadata> correlogramCacheTable;
    @FXML private TableColumn<WindowMetadata,String> input1SeriesColumn;
    @FXML private TableColumn<WindowMetadata,String> input2SeriesColumn;
    @FXML private TableColumn<WindowMetadata,Integer> windowSizeColumn;
    @FXML private TableColumn<WindowMetadata,Integer> overlapColumn;
    @FXML private TableColumn<WindowMetadata,String> lagRangeColumn;
    @FXML private TableColumn<WindowMetadata,Integer> lagStepColumn;
    @FXML private TableColumn<WindowMetadata,Double> significanceColumn;
//    @FXML private TableColumn<WindowMetadata,Time> timeColumn;

    ProgressLayer progressLayer;

    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------

    @Override public void initialize(URL location, ResourceBundle resources) {

        // on selecting an entry in the results table, update the correlation matrix and restore the input parameters
        correlogramCacheTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) c -> {
            WindowMetadata selectedItem = correlogramCacheTable.getSelectionModel().getSelectedItem();
            if(selectedItem != null){
                setParameters(selectedItem);
                sharedData.setcorrelationMatrix(sharedData.experiment.getResult(selectedItem));
            }
        });

        // bind table columns to string getters of the metadata class
        correlogramCacheTable.setPlaceholder(new Text("No computations performed yet."));
        input1SeriesColumn.setCellValueFactory(new PropertyValueFactory<>("inputSet1Size"));
        input2SeriesColumn.setCellValueFactory(new PropertyValueFactory<>("inputSet2Size"));
        windowSizeColumn.setCellValueFactory(new PropertyValueFactory<>("windowSize"));
        overlapColumn.setCellValueFactory(new PropertyValueFactory<>("overlap"));
        lagRangeColumn.setCellValueFactory(new PropertyValueFactory<>("lagRange"));
        lagStepColumn.setCellValueFactory(new PropertyValueFactory<>("lagStep"));
        significanceColumn.setCellValueFactory(new PropertyValueFactory<>("significanceLevel"));

    }

    public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;
        dataModel = sharedData.experiment.dataModel;

        // init time series selection helpers
        setASelector = new TimeSeriesSelector(dataModel.timeSeriesA, dataModel.correlationSetA, setAAllButton, setANoneButton, setARandomButton);
        setBSelector = new TimeSeriesSelector(dataModel.timeSeriesB, dataModel.correlationSetB, setBAllButton, setBNoneButton, setBRandomButton);
        inputGridPane.add(setASelector.listView, 0, 1);
        inputGridPane.add(setBSelector.listView, 1, 1);

        // enable computation run button only if both sets contain at least one element
        ListChangeListener<TimeSeries> checkNonEmpty = change ->
                runButton.setDisable(dataModel.correlationSetA.size() == 0 || dataModel.correlationSetB.size() == 0);
        dataModel.correlationSetA.addListener(checkNonEmpty);
        dataModel.correlationSetB.addListener(checkNonEmpty);

        correlogramCacheTable.setItems(sharedData.experiment.cacheKeySet);

    }

    public void compute(){
    
        // execute values from text inputs
        int initialWindowSize = Integer.parseInt(windowSizeText.getText());
        int overlap = (int) Double.parseDouble(baseWindowOffsetText.getText());
        int tauMin = (int) Double.parseDouble(timeLagMinText.getText());
        int tauMax = (int) Double.parseDouble(timeLagMaxText.getText());
        int tauStep = (int) Double.parseDouble(timeLagStepText.getText());
        double significanceLevel = Double.parseDouble(significanceLevelText.getText());

        // the window size must be at least two (otherwise, the pearson correlation coefficient is undefined)
        // and at most the length of the time series (otherwise the correlation matrix will have no columns)
        int maxWindowSize = Math.min(sharedData.experiment.dataModel.getTimeSeriesLength(0), sharedData.experiment.dataModel.getTimeSeriesLength(1));
        int minWindowSize = 2;
        int windowSize = Math.min(maxWindowSize, Math.max(minWindowSize, initialWindowSize));
        if(windowSize != initialWindowSize){
            Dialogs.create().title("Invalid window size.")
                    .masthead(String.format("The window size must be at least %s and at most %s.\n%s given, using %s instead.",minWindowSize,maxWindowSize,initialWindowSize,windowSize))
                    .showInformation();
        }
        int baseWindowOffset = windowSize-overlap;
        // TODO: there's nothing wrong with negative overlaps (baseWindowOffsets larger than the window size), but the lag window cache can't handle it currently.
        if(baseWindowOffset <= 0 || baseWindowOffset > windowSize){
            Dialogs.create().title("Invalid window overlap.").masthead(String.format("Invalid window overlap. Must be at least 0, at most %s.\n%s given.", windowSize-1, overlap)).showError();
            return;
        }

        // display the parsed values
        windowSizeText.setText(""+windowSize); // to display what was parsed

        WindowMetadata metadata = new WindowMetadata(dataModel.correlationSetA, dataModel.correlationSetB,
                windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, significanceLevel);

        // get result from cache or execute an asynchronous compute service
        CorrelationMatrix result;
        if(sharedData.experiment.hasResult(metadata)){
            result = sharedData.experiment.getResult(metadata);
            sharedData.setcorrelationMatrix(result);
        } else {
            result = new CorrelationMatrix(metadata);
            final CorrelationMatrix.ComputeService service = result.computeService;
            
            // remove partial state if previous computation was cancelled
            service.reset();
            
            // after the computation, put correlation result in the shared data object and save the result
            service.setOnSucceeded(t -> {
                progressLayer.hide();
                sharedData.experiment.addResult(service.getValue());
                sharedData.setcorrelationMatrix(service.getValue());
            });
            
            // on cancel: hide the progress layer. wire the cancel button to that action.
            service.setOnCancelled(t -> progressLayer.hide());
            progressLayer.cancelButton.setOnAction(t -> { System.err.println(String.format("cancel computation! success: %s",service.cancel())); progressLayer.hide(); });
            
            // bind progress display elements
            progressLayer.progressBar.progressProperty().bind(service.progressProperty());
            progressLayer.messageLabel.textProperty().bind(service.messageProperty());
        
            progressLayer.show();
            
            service.start();
        }
        
    }

    public void setParameters(WindowMetadata metadata){

        windowSizeText.setText(""+metadata.windowSize);
        baseWindowOffsetText.setText(""+metadata.getOverlap());
        timeLagMinText.setText(""+metadata.tauMin);
        timeLagMaxText.setText(""+metadata.tauMax);
        significanceLevelText.setText(""+CorrelationMatrix.getSignificanceLevel(metadata));

        // restore time series selection
        setASelector.removeAll();
        setBSelector.removeAll();
        setASelector.addGiven(metadata.setA);     // restore the logical selection
        setBSelector.addGiven(metadata.setB);
        setASelector.selectAll(metadata.setA);      // restore the check marks
        setBSelector.selectAll(metadata.setB);
        setASelector.flushAddBuffer();
        setBSelector.flushAddBuffer();
    }

    /**
     * Handles the logic for adding and removing time series from the computation parameters.
     * Is put in a separate class because the entire thing is needed for both input files separately.
     */
    private class TimeSeriesSelector{

        final List<TimeSeries> baseSet, targetSet;

        final CheckListView<TimeSeries> listView;
        boolean listeningToChanges = true;

        final Button addAll;
        final Button removeAll;
        final Button addRandom;

        final RandomDataGenerator rdg = new RandomDataGenerator();

        /**
         * @param baseSet   list of time series to select from
         * @param targetSet list of time series that represents the selection
         * @param addAll    buttons to add all time series to selection
         * @param removeAll buttons to remove all time series from selection
         * @param addRandom buttons to add a number of random time series to selection
         */
        private TimeSeriesSelector(ObservableList<TimeSeries> baseSet, ObservableList<TimeSeries> targetSet, Button addAll, Button removeAll, Button addRandom) {

            this.baseSet = baseSet;
            this.targetSet = targetSet;

            this.addAll = addAll;
            this.addRandom = addRandom;
            this.removeAll = removeAll;
            addAll.setOnAction(event -> addAll());
            removeAll.setOnAction(event -> unselectAll());
            addRandom.setOnAction(event -> addRandom());

            this.listView = new CheckListView<>(baseSet);
            this.listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.getCheckModel().getSelectedItems().addListener((ListChangeListener<TimeSeries>) c -> {
                // trying to access the change (and only the change) directly gives only null and -1 values when selecting new time series
                if(!listeningToChanges)return;
                removeAll();
                for(Integer selectedIdx : listView.getCheckModel().getSelectedIndices())
                    addLater(listView.getItems().get(selectedIdx));
                flushAddBuffer();
            });
        }

        final List<TimeSeries> addBuffer = new ArrayList<>();
//        private void add(TimeSeries ts){ if(! targetSet.contains(ts)) targetSet.add(ts); }
        private void addAll(){
            targetSet.clear();
            targetSet.addAll(baseSet);
            listView.getCheckModel().selectAll();
        }
        private void addGiven(List<TimeSeries> ts){ ts.forEach(this::addLater); }
        private void addLater(TimeSeries ts){ if(! (addBuffer.contains(ts) || targetSet.contains(ts))) addBuffer.add(ts); }
        private void flushAddBuffer(){ targetSet.addAll(addBuffer); addBuffer.clear(); }

//        final List<TimeSeries> removeBuffer = new ArrayList<>();
        private void removeAll(){ targetSet.clear(); }
//        private void remove(TimeSeries ts){ targetSet.remove(ts); }
//        private void removeLater(TimeSeries ts){ removeBuffer.add(ts); }
//        private void flushRemoveBuffer(){
//            targetSet.removeAll(removeBuffer);
//        }

//        private void addSelected(){
//            List<TimeSeries> ts = listView.getItems();
//            for(Integer selectedIndex : listView.getSelectionModel().getSelectedIndices()){
//                listView.getCheckModel().select(selectedIndex);
//            }
//        }
//        private void removeSelected(){
//            List<TimeSeries> ts = listView.getItems();
//            for(Integer selectedIndex : listView.getSelectionModel().getSelectedIndices())
//                listView.getCheckModel().clearSelection(selectedIndex);
//        }
        private void addRandom(){

            // ask how many items to add
            Optional<String> response = Dialogs.create()
                    .title("Number of Random Time Series")
                    .showTextInput("200");

            if(! response.isPresent()) return;

            int requestedToAdd;
            try{requestedToAdd = Integer.parseInt(response.get());}
            catch(Exception e){ return; }

            // compute how many items can be added
            int numItems = listView.getItems().size();
            int numUnchecked = numItems - listView.getCheckModel().getSelectedIndices().size();

            int toAdd = Math.min(requestedToAdd, numUnchecked);

            if(toAdd <= 0) return;

            // put unchecked indices in an array
            Integer[] uncheckedIndices = new Integer[numUnchecked];
            int j = 0;
            for (int i = 0; i < numItems; i++)
                if( ! listView.getCheckModel().isSelected(i)) uncheckedIndices[j++] = i;

//            List<TimeSeries> ts = listView.getItems();
            // generate a random sample of the unchecked indices and check them
            for(Object idx : rdg.nextSample(Arrays.asList(uncheckedIndices),toAdd)){
//                addLater(ts.get((Integer)idx));
                listView.getCheckModel().select((Integer) idx);
            }
//            flushAddBuffer();
        }

        public void selectAll(List<TimeSeries> set) {
            listeningToChanges = false;
            listView.getCheckModel().clearSelection();
            set.forEach(timeSeries -> listView.getCheckModel().select(timeSeries.getId()-1));
            listeningToChanges = true;
        }
        public void unselectAll() {
            listeningToChanges = false;
            listView.getCheckModel().clearSelection();
            listeningToChanges = true;
        }
    }



}
