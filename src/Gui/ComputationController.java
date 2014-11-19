package Gui;

import Data.Correlation.CorrelationMatrix;
import Data.DataModel;
import Data.SharedData;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.controlsfx.control.CheckListView;
import org.controlsfx.control.IndexedCheckModel;
import org.controlsfx.dialog.Dialogs;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 *
 * The computation controller connects the input widgets in the computation parameters tab to the computation routines and passed the returned results to the rendering routines.
 *
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

    @FXML private Label file1Label;
    @FXML private Label file2Label;

    @FXML private TextField windowSizeText;
    @FXML private TextField baseWindowOffsetText;
//    @FXML private TextField timeLagMinText;
    @FXML private TextField timeLagMaxText;
    @FXML private TextField timeLagStepText;
    @FXML private TextField significanceLevelText;

    @FXML private Button setAAllButton;
    @FXML private Button setANoneButton;
    @FXML private Button setARandomButton;
    @FXML private Button setAInvertSelectedButton;
    @FXML private Button setBAllButton;
    @FXML private Button setBNoneButton;
    @FXML private Button setBRandomButton;
    @FXML private Button setBInvertSelectedButton;

    @FXML private Button runButton;

    @FXML private TableView<WindowMetadata> correlogramCacheTable;
    @FXML private TableColumn<WindowMetadata,String> input1SeriesColumn;
    @FXML private TableColumn<WindowMetadata,String> input2SeriesColumn;
    @FXML private TableColumn<WindowMetadata,Integer> windowSizeColumn;
    @FXML private TableColumn<WindowMetadata,Integer> overlapColumn;
    @FXML private TableColumn<WindowMetadata,String> lagRangeColumn;
    @FXML private TableColumn<WindowMetadata,Integer> lagStepColumn;
    @FXML private TableColumn<WindowMetadata,Double> significanceColumn;
    @FXML private TableColumn<WindowMetadata,String> approximateMemoryColumn;
    @FXML private MenuItem deleteSelectedResultsMenuItem;

    ProgressLayer progressLayer;

    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------

    @Override public void initialize(URL location, ResourceBundle resources) {

        // on selecting an entry in the results table, update the correlation matrix and restore the input parameters
        correlogramCacheTable.getSelectionModel().getSelectedIndices().addListener(this::correlogramCacheTableSelectionChanged);

        // bind table columns to string getters of the metadata class
        correlogramCacheTable.setPlaceholder(new Text("No computations performed yet."));
        input1SeriesColumn.setCellValueFactory(new PropertyValueFactory<>("inputSet1Size"));
        input2SeriesColumn.setCellValueFactory(new PropertyValueFactory<>("inputSet2Size"));
        windowSizeColumn.setCellValueFactory(new PropertyValueFactory<>("windowSize"));
        overlapColumn.setCellValueFactory(new PropertyValueFactory<>("overlap"));
        lagRangeColumn.setCellValueFactory(new PropertyValueFactory<>("lagRange"));
        lagStepColumn.setCellValueFactory(new PropertyValueFactory<>("lagStep"));
        significanceColumn.setCellValueFactory(new PropertyValueFactory<>("significanceLevel"));
        approximateMemoryColumn.setCellValueFactory(new PropertyValueFactory<>("approximateMemoryConsumption"));

        // initialize context menu action on table: delete selected results
        deleteSelectedResultsMenuItem.setOnAction((ActionEvent e)->deleteSelectedResults());

    }

    public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;
        dataModel = sharedData.experiment.dataModel;

        // label the time series selection widgets with the file names they draw their data from
        if(sharedData.experiment.tsAPath != null && sharedData.experiment.tsBPath != null){
            Path path1 = Paths.get(sharedData.experiment.tsAPath);
            Path path2 = Paths.get(sharedData.experiment.tsBPath);
            file1Label.setText(path1.getFileName().toString());
            file2Label.setText(path2.getFileName().toString());
        }

        // init time series selection helpers
        setASelector = new TimeSeriesSelector(dataModel.ensemble1TimeSeries, dataModel.correlationSetA, setAAllButton, setANoneButton, setARandomButton, setAInvertSelectedButton);
        setBSelector = new TimeSeriesSelector(dataModel.ensemble2TimeSeries, dataModel.correlationSetB, setBAllButton, setBNoneButton, setBRandomButton, setBInvertSelectedButton);
        inputGridPane.add(setASelector.listView, 0, 1);
        inputGridPane.add(setBSelector.listView, 1, 1);

        // enable computation run button only if both sets contain at least one element
        ListChangeListener<TimeSeries> checkNonEmpty = change ->
                runButton.setDisable(dataModel.correlationSetA.size() == 0 || dataModel.correlationSetB.size() == 0);
        dataModel.correlationSetA.addListener(checkNonEmpty);
        dataModel.correlationSetB.addListener(checkNonEmpty);

        // connect the computed results table to the data model
        correlogramCacheTable.setItems(sharedData.experiment.cacheKeySet);

        sharedData.experiment.cacheKeySet.addListener((ListChangeListener<WindowMetadata>) c -> {
            if (c.next() && c.wasAdded()) {
                assert (c.getAddedSize() == 1);
                WindowMetadata newEntry = c.getAddedSubList().get(0);
                correlogramCacheTable.getSelectionModel().select(newEntry);
                correlogramCacheTable.scrollTo(newEntry);
            }
        });

    }

    public void compute(){

        // assemble metadata from GUI input elements
        Optional<WindowMetadata> metadataFromGUIElements = createMetadataFromGUIElements();
        if(! metadataFromGUIElements.isPresent()) return;
        WindowMetadata metadata = metadataFromGUIElements.get();

        // get result from cache or execute an asynchronous compute service
        CorrelationMatrix result;
        if(sharedData.experiment.hasResult(metadata)){
            result = sharedData.experiment.getResult(metadata);
            sharedData.setcorrelationMatrix(result);
        } else {
            result = new CorrelationMatrix(metadata);
            computeMatrixWithProgressFeedback(result);
        }
        
    }

    /**
     * Assembles computation input parameters from the GUI input elements.
     * @return If the input parameters are valid, an Optional with the desired metadata. Otherwise this Optional is empty.
     */
    protected Optional<WindowMetadata> createMetadataFromGUIElements() {

        int initialWindowSize = parseOrError(Integer::parseInt, windowSizeText.getText()).intValue();
        int overlap = parseOrError(Double::parseDouble, baseWindowOffsetText.getText()).intValue();
        int tauStep = parseOrError(Double::parseDouble, timeLagStepText.getText()).intValue();
        int tauMax = parseOrError(Double::parseDouble, timeLagMaxText.getText()).intValue();
        double significanceLevel = parseOrError(Double::parseDouble, significanceLevelText.getText()).doubleValue();

        // derive other parameters such that the matrix will be symmetric
        // tauMax should be divisible by tauStep to assert that 0 is part of the lag range. Because if tauMax is not divisible by tauStep,
        // tauMin is not divisible by tauStep and consequently, 0 won't be in the lag range
        if(tauMax % tauStep != 0){
            tauMax = (Math.floorDiv(tauMax, tauStep) + 1) * tauStep;
            timeLagMaxText.setText("" + tauMax);
        }
        int tauMin = -tauMax; // (int) Double.parseDouble(timeLagMinText.getText());

        // check window size. Must be at least two (otherwise, the pearson correlation coefficient is undefined)
        // and at most the length of the time series (otherwise the correlation matrix will have no columns)
        int maxWindowSize = Math.min(sharedData.experiment.dataModel.getTimeSeriesLength(0), sharedData.experiment.dataModel.getTimeSeriesLength(1));
        int minWindowSize = 3;
        int windowSize = Math.min(maxWindowSize, Math.max(minWindowSize, initialWindowSize));
        if(windowSize != initialWindowSize){
            String info = String.format("The window size must be at least %s and at most %s.",minWindowSize, maxWindowSize);
            Alert invalidWindowSizeWarning = new Alert(Alert.AlertType.ERROR, info);
            invalidWindowSizeWarning.setTitle("Invalid window size");
            invalidWindowSizeWarning.show();

            // set the window size input to the maximum allowed value, if too large a value was requested
            if(initialWindowSize > maxWindowSize) windowSizeText.setText(""+maxWindowSize);

            return Optional.empty();
        }

        // check window overlap
        int baseWindowOffset = windowSize-overlap;
        // TODO: there's nothing wrong with negative overlaps (baseWindowOffsets larger than the window size), but the lag window cache can't handle it currently.
        if(baseWindowOffset <= 0 || baseWindowOffset > windowSize){
            String info = String.format("Invalid window overlap. Must be at least 0, at most %s.", windowSize-1);
            Alert invalidWindowOverlapError = new Alert(Alert.AlertType.ERROR, info);
            invalidWindowOverlapError.setTitle("Invalid window overlap");
            invalidWindowOverlapError.show();

            return Optional.empty();
        }

        WindowMetadata metadata = new WindowMetadata(dataModel.correlationSetA, dataModel.correlationSetB,
                windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, significanceLevel);
        return Optional.of(metadata);

    }

    /**
     * Attempts to parse a number. Displays an error dialog if a {@link java.lang.NumberFormatException} is thrown.
     * @param parserFunction The function to parse the number.
     * @param inputString The string containing the number.
     * @return A number or null, if a parser error occurred.
     */
    protected Number parseOrError(Function<String,Number> parserFunction, String inputString){
        try{
            return parserFunction.apply(inputString);
        } catch (NumberFormatException e){
            new Alert(Alert.AlertType.ERROR, String.format("Couldn't parse %s, please enter only numbers.", inputString)).show();
        }
        return null;
    }

    /**
     * Restores GUI input element states from the parameters of the selected computation result.
     */
    public void correlogramCacheTableSelectionChanged(ListChangeListener.Change<? extends Integer> c) {
        WindowMetadata metadata = correlogramCacheTable.getSelectionModel().getSelectedItem();
        if (metadata != null) {
            ComputationController.this.restoreComputationParameters(metadata);

            CorrelationMatrix correlationMatrix = sharedData.experiment.getResult(metadata);

            // check if the histograms for each cell have been computed yet
            if (correlationMatrix.getColumn(0).histogram == null) {

                Alert dlg = new Alert(Alert.AlertType.WARNING, "", ButtonType.NO, ButtonType.YES);
                dlg.setTitle("Missing data");
                dlg.setHeaderText("Recompute the selected correlation matrix?");
                dlg.setContentText(
                        "The selected correlation matrix was computed with an older software version. " +
                        "It lacks data that is needed for smooth live interaction. " +
                        "\nWould you like to compute it now? " +
                        "Otherwise interaction might be slow.");
                ButtonType recomputeMatrix = dlg.showAndWait().orElse(ButtonType.NO);

                if (recomputeMatrix == ButtonType.YES) {
                    correlationMatrix = new CorrelationMatrix(metadata);
                    computeMatrixWithProgressFeedback(correlationMatrix);
                } else {
                    sharedData.setcorrelationMatrix(correlationMatrix);
                }
            } else {
                sharedData.setcorrelationMatrix(correlationMatrix);
            } // end if histogram data is missing
        } // end if selected metadata is not null
    }

    /**
     * Executed when selecting an entry in the {@link #correlogramCacheTable}. Sets the computation parameter inputs to the values with which the correlation matrix was computed.
     * @param metadata Contains all parameters of the cross-correlation computation.
     */
    public void restoreComputationParameters(WindowMetadata metadata){

        windowSizeText.setText(""+metadata.windowSize);
        baseWindowOffsetText.setText(""+metadata.getOverlap());
//        timeLagMinText.setText(""+metadata.tauMin);
        timeLagMaxText.setText(""+metadata.tauMax);
        timeLagStepText.setText(""+metadata.tauStep);
        significanceLevelText.setText(""+CorrelationMatrix.getSignificanceLevel(metadata));

        // restore time series selection
        setASelector.setSample(metadata.setA);
        setBSelector.setSample(metadata.setB);
    }

    public void deleteSelectedResults(){

        // get selected results
        List<WindowMetadata> selectedResults = correlogramCacheTable.getSelectionModel().getSelectedItems();

        // request confirmation
        Alert confirmDeletion = new Alert(Alert.AlertType.CONFIRMATION, "All selected results will be deleted. Proceed?", ButtonType.YES, ButtonType.NO);
        ButtonType confirmDelete = confirmDeletion.showAndWait().orElse(ButtonType.NO);

        // delete if confirmed
        if(confirmDelete == ButtonType.YES)
            sharedData.experiment.removeAll(selectedResults);

    }

    protected void computeMatrixWithProgressFeedback(CorrelationMatrix matrix) {
        final CorrelationMatrix.ComputeService service = matrix.computeService;

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
        progressLayer.cancelButton.setOnAction(t -> { System.out.println(String.format("Computation cancelled. Success: %s", service.cancel())); progressLayer.hide(); });

        // bind progress display elements
        progressLayer.progressBar.progressProperty().bind(service.progressProperty());
        progressLayer.messageLabel.textProperty().bind(service.messageProperty());

        progressLayer.show();

        service.start();
    }

    /**
     * Handles the logic for adding and removing time series from the computation parameters.
     * Is put in a separate class because the entire thing is needed for both input files separately.
     * TODO: the entire thing could be generified and handle button creation on its own as well. ideally the entire thing could be programmed as a custom control.
     */
    private class TimeSeriesSelector{

        /** The domain from which time series can be selected. */
        final List<TimeSeries> domain;
        /** The list gathering the selected elements. */
        final List<TimeSeries> sample;

        final CheckListView<TimeSeries> listView;
        /** This can be used to update checkboxes without having to process the according selection changed event (and making unwanted changes to the sample). */
        boolean listeningToChanges = true;

        final Button addAll, removeAll, addRandom, invertSelected;

        final RandomDataGenerator rdg = new RandomDataGenerator();

        /**
         * @param domain   list of time series to select from
         * @param sample    list of time series that represents the selection
         * @param addAll    buttons to add all time series to selection
         * @param removeAll buttons to remove all time series from selection
         * @param addRandom buttons to add a number of random time series to selection
         */
        private TimeSeriesSelector(ObservableList<TimeSeries> domain, ObservableList<TimeSeries> sample, Button addAll, Button removeAll, Button addRandom, Button invertSelected) {

            this.domain = domain;
            this.sample = sample;

            this.addAll = addAll;
            this.addRandom = addRandom;
            this.removeAll = removeAll;
            this.invertSelected = invertSelected;

            addAll.setOnAction(event -> setSampleToDomain());
            removeAll.setOnAction(event -> clearSample());
            addRandom.setOnAction(event -> randomExtendSample());
            invertSelected.setOnAction(event -> invertCheckBoxes());

            this.listView = new CheckListView<>(domain);
            this.listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            listView.getCheckModel().getCheckedItems().addListener(this::checkBoxClicked);
        }

        public void checkBoxClicked(ListChangeListener.Change<? extends TimeSeries> c) {
            // evaluation change data would be more efficient but the data is unreliable and quirky. (E.g. null and -1 values when selecting a new single time series)
            if(!listeningToChanges) return;
            updateSample();
        }

        private void setSample(List<TimeSeries> ts){
            sample.clear();
            sample.addAll(ts);
            updateCheckboxes();
        }

        private void setSampleToDomain(){
            sample.clear();
            sample.addAll(domain);
            updateCheckboxes();
        }

        public void clearSample() {
            sample.clear();
            updateCheckboxes();
        }

        private void randomExtendSample(){

            // ask how many items to add
            Optional<String> response = Dialogs.create()
                    .title("Add the Following Number of Random Time Series to the Selection")
                    .showTextInput("200");

            // abort if the dialog was not confirmed or the input is not parseable
            if(! response.isPresent() ) return;
            int requestedToAdd;
            try{ requestedToAdd = Integer.parseInt(response.get()); }
            catch(Exception e){ return; }

            // compute how many items can be added
            int numItems = listView.getItems().size();
            int numUnchecked = numItems - listView.getCheckModel().getCheckedIndices().size();

            int toAdd = Math.min(requestedToAdd, numUnchecked);

            if(toAdd <= 0) return;

            // put unchecked indices in an array
            Integer[] uncheckedIndices = new Integer[numUnchecked];
            int j = 0;
            for (int i = 0; i < numItems; i++)
                if( ! listView.getCheckModel().isChecked(i)) uncheckedIndices[j++] = i;

            // generate a random sample of the unchecked indices and check them
            for(Object idx : rdg.nextSample(Arrays.asList(uncheckedIndices),toAdd)){
                listView.getCheckModel().check((Integer) idx);
            }

        }

        /** Sets the check box states according to the current sample. */
        private void updateCheckboxes() {
            listeningToChanges = false;
            IndexedCheckModel<TimeSeries> checkModel = listView.getCheckModel();
            checkModel.clearChecks();
            // selectIndices() seems to be a bit faster than multiple calls to select()
            checkModel.checkIndices(sample.stream().mapToInt(value -> value.getId() - 1).toArray());
//            sample.stream().forEach(timeSeries -> checkModel.select(timeSeries.getId() - 1));
            listeningToChanges = true;
        }

        /** Fills the sample list according to the current check box states. */
        private void updateSample() {
            IndexedCheckModel<TimeSeries> checkModel = listView.getCheckModel();
            sample.clear();
            sample.addAll(checkModel.getCheckedItems());
        }

        /** Goes through the selection and unchecks checked items and vice versa. */
        private void invertCheckBoxes() {

            IndexedCheckModel<TimeSeries> checkModel = listView.getCheckModel();

            for(int i : listView.getSelectionModel().getSelectedIndices()){
                if(checkModel.isChecked(i)){
                    checkModel.clearCheck(i);
                } else {
                    checkModel.check(i);
                }
            }

            updateSample();

        }
    }



}
