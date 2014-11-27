package Gui;

import Data.Experiment;
import Data.IO.FileModel;
import Data.IO.LineParser;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.controlsfx.dialog.Dialogs;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/**
 * Method overview:
 *   compute()
 *      Passes the input parameters (correlation sets, window size, overlap, etc.) to the computation routines.
 *      Handles asynchronous computation of the result.
 *
 * @author Carl Witt
 */
public class StartUpWizardController extends WindowController implements Initializable{

    public HelpWindowController helpWindowController;
    public MainWindowController mainWindowController;

    private static final int MAX_RECENT_FILES = 8;

    // -------------------------------------------------------------------------
    // business logic data
    // -------------------------------------------------------------------------
    
    private FileSelector experimentFileSelector;
    private SeparatedValueFileSelector ts1Selector;
    private SeparatedValueFileSelector ts2Selector;

    // -------------------------------------------------------------------------
    // injected control elements
    // -------------------------------------------------------------------------
    
    // input file and separator selection
    @FXML private Button tsFile1SelectButton;
    @FXML private ToggleGroup file1Separator;
    @FXML private RadioMenuItem file1FixedWidthSeparatorRadio;
    @FXML private RadioMenuItem file1CharacterSeparatorRadio;
    @FXML private RadioMenuItem file1TabSeparatorRadio;
    @FXML private TextField file1CharacterSeparatorText;
    @FXML private TextField file1FixedWidthSeparatorText;
    @FXML private Button tsFile2SelectButton;
    @FXML private ToggleGroup file2Separator;
    @FXML private RadioMenuItem file2FixedWidthSeparatorRadio;
    @FXML private RadioMenuItem file2CharacterSeparatorRadio;
    @FXML private TextField file2CharacterSeparatorText;
    @FXML private TextField file2FixedWidthSeparatorText;
    @FXML private RadioMenuItem file2TabSeparatorRadio;
    @FXML private ProgressIndicator parserProgressIndicator;
    @FXML private Button experimentFileSelectButton;

    @FXML private GridPane inputPane; // contains the controls for both loading and creating experiment files.
    @FXML private Button loadButton;
    @FXML private Button createButton;

    @FXML private ListView<String> recentExperiments;

    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------

    @Override public void initialize(URL location, ResourceBundle resources) {

        // pass GUI elements to file selector logic objects
        experimentFileSelector = new FileSelector(experimentFileSelectButton);
        ts1Selector = new SeparatedValueFileSelector(tsFile1SelectButton, file1Separator, file1FixedWidthSeparatorText, file1CharacterSeparatorText, file1CharacterSeparatorRadio, file1FixedWidthSeparatorRadio, file1TabSeparatorRadio);
        ts2Selector = new SeparatedValueFileSelector(tsFile2SelectButton, file2Separator, file2FixedWidthSeparatorText, file2CharacterSeparatorText, file2CharacterSeparatorRadio, file2FixedWidthSeparatorRadio, file2TabSeparatorRadio);

        // create button disable logic
        ts1Selector.getFileProperty().addListener((observable, oldValue, newValue) -> createButton.setDisable(newValue == null || ts2Selector.getFile() == null));
        ts2Selector.getFileProperty().addListener((observable, oldValue, newValue) -> createButton.setDisable(newValue == null || ts1Selector.getFile() == null));

        // load button disable logic
        experimentFileSelector.getFileProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) recentExperiments.getSelectionModel().clearSelection();
            loadButton.setDisable(newValue == null && recentExperiments.getSelectionModel().getSelectedIndices().size() == 0);
        });
        recentExperiments.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null && (int) newValue >= 0) experimentFileSelector.setFile(null);
            loadButton.setDisable(newValue == null && recentExperiments.getSelectionModel().getSelectedIndices().size() == 0);
        });

        // fill recent experiment files list
        recentExperiments.getItems().addAll(getRecentExperimentFiles());
        recentExperiments.getSelectionModel().select(0);
        if(recentExperiments.getItems().size() > 0) {
            loadButton.setDisable(false);
        }

    }

    public void loadExperiment(){

        // one of both input file options is available, because the load button is disabled otherwise
        File inputFile = experimentFileSelector.getFile();
        if(inputFile == null) inputFile = new File(recentExperiments.getSelectionModel().getSelectedItem());

        try {
            inputPane.setDisable(true);
            mainWindowController.setExperiment(new Experiment(inputFile.getPath()));
            mainWindowController.showWindow();
            this.hideWindow();
            addRecentExperimentFile(inputFile.getAbsolutePath());
        }
        catch(IllegalArgumentException e){
            new Alert(Alert.AlertType.ERROR, "The data has been written with a previous version of the program. Sorry, can't read file.").show();
        }
        catch(Exception e){
            Dialogs.create().masthead("Sorry for this technical error message.\nIf you'd like to help improving the software, send the details to carl.witt@gfz-potsdam.de.").showException(e);
            e.printStackTrace();
        }
        finally { inputPane.setDisable(false); }

    }

    public void createExperiment(){

        inputPane.setDisable(true);
        Task<Experiment> loadExperiment = new Task<Experiment>() { @Override protected Experiment call() throws Exception{
            return new Experiment(ts1Selector.fileModel, ts2Selector.fileModel);
        }};

        new Thread(loadExperiment).start();

        loadExperiment.setOnFailed(event -> {
            Throwable exception = loadExperiment.getException();
            Alert parserFail = new Alert(Alert.AlertType.ERROR, exception.getLocalizedMessage());
            parserFail.setResizable(true);
            parserFail.setTitle("Invalid input configuration.");
            parserFail.show();
            inputPane.setDisable(false);
        });

        parserProgressIndicator.visibleProperty().bind(loadExperiment.stateProperty().isEqualTo(Worker.State.RUNNING));

        loadExperiment.setOnSucceeded(event -> {
            try {
                Experiment experiment = loadExperiment.get();
                mainWindowController.setExperiment(experiment);
                mainWindowController.showWindow();
                this.hideWindow();

                // inform the user that clipping has been performed to adapt offset and length of both ensembles.
                if(experiment.dataModel.ensembleClippings.isPresent()){
                    double[] xRange = new double[]{experiment.dataModel.getMinX(0), experiment.dataModel.getMaxX(0)};
                    Alert informAboutClipping = new Alert(Alert.AlertType.INFORMATION, String.format("The ensembles have been clipped to a common x value range of [%s, %s]", xRange[0], xRange[1]));
                    informAboutClipping.showAndWait();
                }
            }
            catch (InterruptedException | ExecutionException e) { e.printStackTrace(); }
            inputPane.setDisable(false);
        });

    }

    Preferences prefs = Preferences.userNodeForPackage(getClass());
    List<String> getRecentExperimentFiles(){
        List<String> recentFiles = new LinkedList<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String recentFileKey = "recentFile" + i;
            String recentFile = prefs.get(recentFileKey, null);
            if(recentFile == null) break;
            if(new File(recentFile).exists())
                recentFiles.add(recentFile);
            else
                prefs.remove(recentFileKey);
        }
        return recentFiles;
    }

    void addRecentExperimentFile(String filepath){
        List<String> recentFiles = getRecentExperimentFiles();
        if(recentFiles.size() == MAX_RECENT_FILES) recentFiles.remove(MAX_RECENT_FILES - 1);
        recentFiles.remove(filepath);
        recentFiles.add(0, filepath);
        for (int i = 0; i < recentFiles.size(); i++) {
//            System.out.println(String.format("recentFiles.peekFirst(): %s", recentFiles.peekFirst()));
            prefs.put("recentFile"+i,recentFiles.get(i));
        }
    }

    public void clearRecentExperimentFiles(){
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            prefs.remove("recentFile"+i);
        }
        recentExperiments.getItems().clear();
    }

    /** Initializes a given button with file selector behavior (show select dialog on click, bind selected file to button label.)*/
    private class FileSelector {

        protected ObjectProperty<File> fileProperty = new SimpleObjectProperty<>(null);
        public File getFile(){return fileProperty.get();}
        public void setFile(File f){fileProperty.set(f);}
        public ObjectProperty<File> getFileProperty(){return fileProperty; }

        final FileChooser fileChooser = new FileChooser();
        final Button loadButton;

        private FileSelector(Button loadButton) {

            this.loadButton = loadButton;
            loadButton.setOnAction(event -> selectFile());

            fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("Text Files", ".txt"));
            this.fileChooser.setTitle("Open Input File");

            fileProperty.addListener((observable, oldValue, newValue) -> {
                String buttonLabel = newValue == null ? "Choose File..." : newValue.toString();
                loadButton.setText(buttonLabel);
            });

        }

        // pick a file for loading
        public void selectFile(){
            // start in the directory where the last file was loaded
            if(getFile() != null) fileChooser.setInitialDirectory(getFile().getParentFile());
            setFile(fileChooser.showOpenDialog(null));
        }

    }

    /** Initializes given controls with separated value file selector behavior (show select dialog on click, bind selected file to button label.)*/
    private class SeparatedValueFileSelector extends FileSelector {

        public final FileModel fileModel = new FileModel(null, new LineParser(16));

        public SeparatedValueFileSelector(Button loadButton, ToggleGroup separatorSelection, TextField fixedWidthSeparatorText, TextField characterSeparatorText, Toggle characterSeparatorRadio, Toggle fixedWidthSeparatorRadio, Toggle tabSeparatorRadio){//, TextField separatorCharacterTextField
            super(loadButton);

            fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("NetCDF Files", ".nc"));

            fileProperty.addListener((ObservableValue<? extends File> observable, File oldValue, File newValue) ->
                    fileModel.setFilename(newValue.toString())
            );

            // update separator on text change in on of the radio buttons text fields
            characterSeparatorText.textProperty().addListener((ov, t, newCharacter) -> {
                if(!newCharacter.equals("")){
                    fileModel.setSeparatorCharacter(newCharacter);
                    characterSeparatorRadio.selectedProperty().set(true);
                }
            });
            fixedWidthSeparatorText.textProperty().addListener((ov, t, newWidth) -> {
                if( ! newWidth.equals("")){
                    fileModel.setSeparatorWidth(Integer.parseInt(newWidth));
                    fixedWidthSeparatorRadio.selectedProperty().set(true);
                }
            });
            // manually update separator when selecting the tab property
            separatorSelection.selectedToggleProperty().addListener((ov, oldRadioButton, newRadioButton) -> {
                if(newRadioButton == tabSeparatorRadio){
                    fileModel.setSeparatorCharacter("\t");
                }
            });
            
        }


    }

}
