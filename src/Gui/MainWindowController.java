package Gui;

import Data.Experiment;
import Data.SharedData;
import Global.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

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
    @FXML private Pane mainWindowRoot;
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
    @FXML private MatrixFilterController matrixFilterController;

    public Main globalMain; // to restart the program

    @Override
    public void showWindow() {
        super.showWindow();

        // resize primary stage to full screen
        Screen primaryScreen = Screen.getPrimary();
        Rectangle2D bounds = primaryScreen.getVisualBounds();
        Stage mainWindowStage = (Stage) mainWindowRoot.getScene().getWindow();
        mainWindowStage.setX(bounds.getMinX());
        mainWindowStage.setY(bounds.getMinY());
        mainWindowStage.setWidth(bounds.getWidth());
        mainWindowStage.setHeight(bounds.getHeight());
//        mainWindowStage.toBack(); // this prevents hiding information dialogs from the start up wizard but it also results in sending the window to back relative to all other open application windows

        mainWindowStage.setOnCloseRequest(event -> checkForUncommitedChanges());
    }

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
        matrixFilterController.setSharedData(sharedData);

        // assert that tab labels have not changed and referencing tabs by labels still works
        String[] tabLabels = new String[]{"Parameters", "Cell Distribution", "Time Series", "Matrix Filter"};
        for (int i = 0; i < tabLabels.length; i++) assert inputTabPane.getTabs().get(i).getText().equals(tabLabels[i]) : "Tab labels changed, please review Main Window Controller code.";

        // activate/deactivate time series rendering based on the visibility of the tab
        inputTabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            Tab activeTab = inputTabPane.getTabs().get(newValue.intValue());
            timeSeriesViewController.setDeferringDrawRequests( ! activeTab.getText().equals("Time Series"));
            cellDistributionViewController.setDeferringDrawRequests(!activeTab.getText().equals("Cell Distribution"));
        });
        timeSeriesViewController.setDeferringDrawRequests(true);
        cellDistributionViewController.setDeferringDrawRequests(true);

    }

    public void setExperiment(Experiment experiment) {
        this.stage.setTitle(experiment.filename);
        this.experiment = experiment;
        sharedData = new SharedData(experiment);
        initialize(null, null);

        timeSeriesViewController.setScrollBarRangesToDataBounds(experiment.dataModel);
        experiment.dataModel.correlationSetA.addAll(experiment.dataModel.ensemble1TimeSeries);
        experiment.dataModel.correlationSetB.addAll(experiment.dataModel.ensemble2TimeSeries);
        timeSeriesViewController.resetView();
        timeSeriesViewController.drawChart();

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
            startUpWizardController.addRecentExperimentFile(experiment.filename);
        }
    }
    public void exportCorrelogramImage(){
        fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("PNG Image (*.png)", "*.png"));
        File out = fileChooser.showSaveDialog(stage);
        if(out != null) correlationViewController.saveCorrelogramImage(out.getPath());
    }

    public void showStartUpWizard(){
        globalMain.start(null);
    }
    public void showGitHubWiki(){
        helpWindowController.homePage = "https://github.com/carlwitt/CrossCorrelationExplorer/wiki";

        helpWindowController.goHome();
        helpWindowController.showWindow();
    }

    public void showGitHubRepository(){
        helpWindowController.homePage = "https://github.com/carlwitt/CrossCorrelationExplorer";
        helpWindowController.goHome();
        helpWindowController.showWindow();
    }

    /** Asks whether to save the experiment data if new computation results have been added. Saves the experiment, if confirmed. */
    public void checkForUncommitedChanges(){
        if(experiment.isChanged()){
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION, "You have unsaved computation results. Save before exit?", ButtonType.YES, ButtonType.NO);
            ButtonType saveBeforeQuit = confirmDialog.showAndWait().orElse(ButtonType.NO);
            if(saveBeforeQuit == ButtonType.YES) save();
        }
    }
    public void quit(){
        checkForUncommitedChanges();
        System.exit(0);
    }
}
