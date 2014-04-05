package Data;

import Data.Correlation.CorrelationMatrix;
import java.util.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;

/**
 * Stores the data that is shared between the different views. 
 * @author Carl Witt
 */
public class SharedData extends Observable {

    /** The globally available time series. */
    public DataModel dataModel = new DataModel();
    
    /** each time series in input set A will be cross correlated with each time series in input set B */
    public ObservableList<TimeSeries> correlationSetA = FXCollections.observableArrayList(), 
                                      correlationSetB = FXCollections.observableArrayList();
    
    /** time series for which a preview shall be rendered (e.g. selected in the loaded series list) */
    public ObservableList<TimeSeries> previewTimeSeries = FXCollections.observableArrayList();
    
    /** The cross correlation result. It can be listened to a change in the result. */
    private final ObjectProperty<CorrelationMatrix> correlationMatrix  = new SimpleObjectProperty<>();
    public final void setcorrelationMatrix(CorrelationMatrix value) { correlationMatrix.set(value); }
    public final CorrelationMatrix getcorrelationMatrix() { return correlationMatrix.get(); }
    public final ObjectProperty<CorrelationMatrix> correlationMatrixProperty() { return correlationMatrix; }
   
    /** The min/max time (in the x component) that is visible in both the time series and correlogram view. */
    private final ObjectProperty<Rectangle2D> visibleTimeRange = new SimpleObjectProperty<>();
    public Rectangle2D getVisibleTimeRange() { return visibleTimeRange.get(); }
    public void setVisibleTimeRange(Rectangle2D value) { visibleTimeRange.set(value); }
    public ObjectProperty visibleTimeRangeProperty() { return visibleTimeRange; }
    
    
}
