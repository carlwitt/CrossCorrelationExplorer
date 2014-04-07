package Gui;

import Data.SharedData;
import Visualization.CorrelogramController;
import Visualization.TimeSeriesViewController;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;

/**
 * FXML Controller class
 *
 * @author Carl Witt
 */
public class MainWindowController implements Initializable {

    // data that all views use in common (e.g. the time series)
    private SharedData sharedData;
    
    // progress display layer
    @FXML private Pane progressPane;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button progressCancelButton;
    
    @FXML private TabPane inputTabPane;
    
    // controller responsible for loading files and filling the data model
    @FXML private FileInputController fileInputController;
    @FXML private TimeSeriesViewController timeSeriesViewController;
    @FXML private ComputationInputController computationInputController;
    @FXML private CorrelogramController correlationViewController;
    
    
    protected ProgressLayer progressLayer;
    /**
     * Called after the controls have been parsed from the XML. Sets up logic and components that could not be set up using the GUI builder.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
        sharedData = new SharedData();
        
        progressLayer = new ProgressLayer();
        progressLayer.overlay = progressPane;
        progressLayer.progressBar = progressBar;
        progressLayer.messageLabel = progressLabel;
        progressLayer.cancelButton = progressCancelButton;
        
        fileInputController.progressLayer = progressLayer;
        computationInputController.progressLayer = progressLayer;
        
        fileInputController.setSharedData(sharedData);
        timeSeriesViewController.setSharedData(sharedData);
        computationInputController.setSharedData(sharedData);
        correlationViewController.setSharedData(sharedData);

//        fileInputController.availableTimeSeries.addListener(new ListChangeListener<Integer>() {
//            @Override public void onChanged(ListChangeListener.Change<? extends Integer> change) {
//                    //                if(change.getAddedSize()>0){
//                    try { Thread.sleep(1000); } catch (InterruptedException ex) { Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex); }
//                    fileInputController.loadAllButton.fire();
//                    sharedData.correlationSetA.add(sharedData.dataModel.get(1));
//                    sharedData.correlationSetB.add(sharedData.dataModel.get(2));
//                    inputTabPane.getSelectionModel().select(1);
////                }
//            }
//        });
//        fileInputController.fileModel.loadFileService.onSucceededProperty().addListener(new ChangeListener<EventHandler<WorkerStateEvent>>() {
//            @Override public void changed(ObservableValue<? extends EventHandler<WorkerStateEvent>> ov, EventHandler<WorkerStateEvent> t, EventHandler<WorkerStateEvent> t1) {
//                
//            }
//        });
//        fileInputController.fileModel.setFilename("/Users/macbookdata/inputDataExcerpt.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/lianhua_realisations.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/dongge_realisations.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/inputDataSimple.txt");
//        fileInputController.fileModel.setFilename("/Users/macbookdata/inputDataTab.txt");
        
//        fileInputController.loadButton.fire();

        // Todo: remove test support
//        inputTabPane.getSelectionModel().select(1);
        
    }
    
}
