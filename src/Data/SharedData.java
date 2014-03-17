package Data;

import Data.Correlation.CorrelationMatrix;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;

/**
 * Stores the data that is shared between the different views. 
 * @author Carl Witt
 */
public class SharedData extends Observable {

    public DataModel dataModel = new DataModel();
    
    // each time series in input set A will be cross correlated with each time series in input set B
    public ObservableList<TimeSeries> correlationSetA = FXCollections.observableArrayList(), 
                                      correlationSetB = FXCollections.observableArrayList();
    
    // time series for which a preview shall be rendered (e.g. selected in the loaded series list)
    public ObservableList<TimeSeries> previewTimeSeries = FXCollections.observableArrayList();
    
    private final ObjectProperty<CorrelationMatrix> correlationMatrix  = new SimpleObjectProperty<>();
    public final void setcorrelationMatrix(CorrelationMatrix value) { correlationMatrix.set(value); }
    public final CorrelationMatrix getcorrelationMatrix() { return correlationMatrix.get(); }
    public final ObjectProperty<CorrelationMatrix> correlationMatrixProperty() { return correlationMatrix; }
    
    
}
