package Gui;

import Data.SharedData;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * FXML Controller class
 *
 * @author Carl Witt
 */
public class MainWindowController implements Initializable {

    // progress display layer
    @FXML private Pane progressPane;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button progressCancelButton;
    @FXML private TabPane inputTabPane;
    
    // controller responsible for loading files and filling the data model
    @FXML private FileInputController fileInputAController;
    @FXML private FileInputController fileInputBController;
    @FXML private TimeSeriesViewController timeSeriesViewController;
    @FXML private ComputationInputController computationInputController;
    @FXML private ComputationOutputController computationOutputController;
    @FXML private CorrelogramController correlationViewController;


    /**
     * Called after the controls have been parsed from the XML. Sets up logic and components that could not be set up using the GUI builder.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        SharedData sharedData = new SharedData();

        ProgressLayer progressLayer = new ProgressLayer();
        progressLayer.overlay = progressPane;
        progressLayer.progressBar = progressBar;
        progressLayer.messageLabel = progressLabel;
        progressLayer.cancelButton = progressCancelButton;
        
        fileInputAController.progressLayer = progressLayer;
        fileInputBController.progressLayer = progressLayer;
        computationInputController.progressLayer = progressLayer;
        computationOutputController.progressLayer = progressLayer;

        fileInputAController.setSharedData(sharedData, sharedData.dataModel.correlationSetA);
        fileInputBController.setSharedData(sharedData, sharedData.dataModel.correlationSetB);
        timeSeriesViewController.setSharedData(sharedData);
        computationInputController.setSharedData(sharedData);

        computationOutputController.setSharedData(sharedData);
        computationOutputController.setCorrelogramController(correlationViewController);

        correlationViewController.setSharedData(sharedData);

//        fileInputController.availableTimeSeries.addListener(new ListChangeListener<Integer>() {
//            @Override public void onChanged(ListChangeListener.Change<? extends Integer> change) {
//                    //                if(change.getAddedSize()>0){
//                    try { Thread.sleep(1000); } catch (InterruptedException ex) { Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex); }
//                    fileInputController.loadAllButton.fire();
//                    sharedData.dataModel.correlationSetA.add(sharedData.dataModel.get(1));
//                    sharedData.dataModel.correlationSetB.add(sharedData.dataModel.get(2));
//                    inputTabPane.getSelectionModel().select(1);
////                }
//            }
//        });
//        fileInputController.fileModel.loadFileService.onSucceededProperty().addListener(new ChangeListener<EventHandler<WorkerStateEvent>>() {
//            @Override public void changed(ObservableValue<? extends EventHandler<WorkerStateEvent>> ov, EventHandler<WorkerStateEvent> t, EventHandler<WorkerStateEvent> t1) {
//                
//            }
//        });
//          fileInputController.fileModel.setFilename("/Users/macbookdata/IdeaProjects/CrossCorrelationExplorer/data/simpleNumbers.txt");
//        fileInputAController.fileModel.setFilename("/Users/macbookdata/Documents/Arbeit/GFZ/CrossCorrelationExplorer/data/dongge_realisations.txt");

//        fileInputController.fileModel.setFilename("/Users/macbookdata/lianhua_realisations.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/dongge_realisations.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/inputDataSimple.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/inputDataTab.txt");
        
//        fileInputAController.loadButton.fire();

        // Todo: remove test support
//        inputTabPane.getSelectionModel().select(2);

    }
    
}
