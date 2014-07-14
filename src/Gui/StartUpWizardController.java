package Gui;

import Data.DataModel;
import Data.Experiment;
import Data.IO.FileModel;
import Data.IO.LineParser;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
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
    
    DataModel dataModel;
    private FileSelector experimentFileSelector;
    private SVFileSelector ts1Selector;
    private SVFileSelector ts2Selector;

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

    @FXML private Button experimentFileSelectButton;

    @FXML private Button loadButton;
    @FXML private Button createButton;

    @FXML private ListView<String> recentExperiments;

    // -------------------------------------------------------------------------
    // initialization. register listeners
    // -------------------------------------------------------------------------

    @Override public void initialize(URL location, ResourceBundle resources) {

        // pass GUI elements to file selector logic objects
        experimentFileSelector = new FileSelector(experimentFileSelectButton);
        ts1Selector = new SVFileSelector(tsFile1SelectButton, file1Separator, file1FixedWidthSeparatorText, file1CharacterSeparatorText, file1CharacterSeparatorRadio, file1FixedWidthSeparatorRadio, file1TabSeparatorRadio);
        ts2Selector = new SVFileSelector(tsFile2SelectButton, file2Separator, file2FixedWidthSeparatorText, file2CharacterSeparatorText, file2CharacterSeparatorRadio, file2FixedWidthSeparatorRadio, file2TabSeparatorRadio);

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

        // TODO: remove file presets
        ts1Selector.setFile(new File("data/lianhua_realisations.txt"));
        ts2Selector.setFile(new File("data/dongge_realisations.txt"));
//        ts1Selector.setFile(new File("data/laggedBrownian.txt"));
//        ts2Selector.setFile(new File("data/laggedSines.txt"));
        createButton.setDisable(false);

    }

    public void loadExperiment(){

        // one of both input file options is available, because the load button is disabled otherwise
        File inputFile = experimentFileSelector.getFile();
        if(inputFile == null) inputFile = new File(recentExperiments.getSelectionModel().getSelectedItem());

        try {
            mainWindowController.setExperiment(new Experiment(inputFile.getPath()));
            mainWindowController.showWindow();
            this.hideWindow();
            addRecentExperimentFile(inputFile.getAbsolutePath());
        } catch(Exception e){Dialogs.create().title("Sorry for this technical error message...").showException(e); e.printStackTrace();}
    }

    public void createExperiment(){

        Task<Experiment> loadExperiment = new Task<Experiment>() { @Override protected Experiment call() throws Exception {
            return new Experiment(ts1Selector.fileModel, ts2Selector.fileModel);
        }};

        Dialogs.create().title("Parsing input time series files.").masthead(null).showWorkerProgress(loadExperiment);
        new Thread(loadExperiment).start();

        loadExperiment.setOnSucceeded(event -> {
            try { mainWindowController.setExperiment(loadExperiment.get()); }
            catch (InterruptedException | ExecutionException e) { e.printStackTrace(); }
            mainWindowController.showWindow();
            this.hideWindow();
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
    private class SVFileSelector extends FileSelector {

        public final FileModel fileModel = new FileModel(null, new LineParser(16));

        public SVFileSelector(Button loadButton, ToggleGroup separatorSelection, TextField fixedWidthSeparatorText, TextField characterSeparatorText, Toggle characterSeparatorRadio, Toggle fixedWidthSeparatorRadio, Toggle tabSeparatorRadio){//, TextField separatorCharacterTextField
            super(loadButton);

            fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("NetCDF Files", ".nc"));

            fileModel.filenameProperty().bind(fileProperty.asString());
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
