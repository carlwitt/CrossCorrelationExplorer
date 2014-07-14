package Gui;

import Data.Experiment;
import Data.SharedData;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * FXML Controller class
 *
 * @author Carl Witt
 */
public class MainWindowController extends WindowController implements Initializable {

    private SharedData sharedData = new SharedData();
    private Experiment experiment;

    /** Controller objects for other windows. */
    public StartUpWizardController startUpWizardController;
    public HelpWindowController helpWindowController;

    FileChooser fileChooser = new FileChooser();

    // progress display layer
    @FXML private Pane progressPane;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button progressCancelButton;
    @FXML private TabPane inputTabPane;

    // controller responsible for loading files and filling the data model
    @FXML private TimeSeriesViewController timeSeriesViewController;
    @FXML private ComputationController computationController;
    @FXML private CorrelogramController correlationViewController;
    @FXML private CellDistributionViewController cellDistributionViewController;

    /**
     * Called after the controls have been parsed from the XML. Sets up logic and components that could not be set up using the GUI builder.
     */
    @Override public void initialize(URL url, ResourceBundle rb) {

        ProgressLayer progressLayer = new ProgressLayer();
        progressLayer.overlay = progressPane;
        progressLayer.progressBar = progressBar;
        progressLayer.messageLabel = progressLabel;
        progressLayer.cancelButton = progressCancelButton;

        computationController.progressLayer = progressLayer;

        timeSeriesViewController.setSharedData(sharedData);
        computationController.setSharedData(sharedData);
        cellDistributionViewController.setSharedData(sharedData);
        correlationViewController.setSharedData(sharedData);

        inputTabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if((int) newValue == 1) timeSeriesViewController.drawContents();
        });

    }

    public void setExperiment(Experiment experiment) {
        this.stage.setTitle(experiment.filename);
        this.experiment = experiment;
        sharedData = new SharedData(experiment);
        initialize(null, null);
    }

    public void save(){
        if(experiment.isNew()) saveAs();
        else experiment.save();
    }

    public void saveAs(){
        File experimentFile = new File(experiment.filename);
        if(experimentFile.exists()) fileChooser.setInitialDirectory(experimentFile.getParentFile());
        File selection = fileChooser.showSaveDialog(stage);
        if(selection != null){
            experiment.save(selection.getPath());
            stage.setTitle(experiment.filename);
        }
    }
    public void exportCorrelogramImage(){
        fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("PNG Image (*.png)", "*.png"));
        File out = fileChooser.showSaveDialog(stage);
        if(out != null) correlationViewController.saveCorrelogramImage(out.getPath());
    }

    public void showStartUpWizard(){
        this.hideWindow();
        startUpWizardController.showWindow();
    }
    public void showGitHubWiki(){
//        helpWindowController.homePage = "https://github.com/carlwitt/CrossCorrelationExplorer/wiki";
        helpWindowController.homePage = "src/Gui/histo.html";

        helpWindowController.goHome();
        helpWindowController.showWindow();
    }

    public void showGitHubIssues(){
        helpWindowController.homePage = "https://github.com/login?return_to=https%3A%2F%2Fgithub.com%2Fcarlwitt%2FCrossCorrelationExplorer%2Fissues%2Fnew";
        helpWindowController.goHome();
        helpWindowController.showWindow();
    }

    public void quit(){

        Dialogs confirmDialog = Dialogs.create().title("There are unsaved changes. Save before exit?");
        if(experiment.isChanged()){
            Action userChoice = confirmDialog.showConfirm();
            if(userChoice == Dialog.Actions.OK) save();             // exit below (on Actions.NO exit without saving)
            else if(userChoice == Dialog.Actions.CANCEL) return;    // don't exit
        }

        System.exit(0);
    }
}
