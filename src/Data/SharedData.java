package Data;

import Data.Correlation.CorrelationMatrix;
import Data.Statistics.AggregatedCorrelationMatrix;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;

import java.util.Observable;

import static Data.Statistics.AggregatedCorrelationMatrix.MatrixRegionData;
import static Visualization.Correlogram.UNCERTAINTY_VISUALIZATION;

/**
 * Stores the data that is shared between the different views. 
 * @author Carl Witt
 */
public class SharedData extends Observable {

    // -----------------------------------------------------------------------------------------------------------------
    // PROPERTIES
    // -----------------------------------------------------------------------------------------------------------------

    /** The globally available time series. */
    public final Experiment experiment;

    /** time series for which a preview shall be rendered (e.g. selected in the loaded series list) */
    public final ObservableList<TimeSeries> previewTimeSeries = FXCollections.observableArrayList();
    
    /** Represents the current min/max time lag. The minimum is stored in getX() the maximum is stored in getY() */
    private final ObjectProperty<Point2D> timeLagBounds = new ReadOnlyObjectWrapper<>(new Point2D(-10,10));

    public Point2D getTimeLagBounds() { return timeLagBounds.get(); }
    public void setTimeLagBounds(Point2D value) { timeLagBounds.set(value); }

    /** The cross correlation result. It can be listened to a change in the result. */
    private final ObjectProperty<CorrelationMatrix> correlationMatrix  = new SimpleObjectProperty<>();
    public final void setcorrelationMatrix(CorrelationMatrix value) { correlationMatrix.set(value); }
    public final CorrelationMatrix getCorrelationMatrix() { return correlationMatrix.get(); }
    public final ObjectProperty<CorrelationMatrix> correlationMatrixProperty() { return correlationMatrix; }

    /** The min/max time (in the x component) that is visible in both the time series and correlogram view. */
    private final ObjectProperty<Bounds> visibleTimeRange = new SimpleObjectProperty<>();
    public Bounds getVisibleTimeRange() { return visibleTimeRange.get(); }
    public void setVisibleTimeRange(Bounds value) { visibleTimeRange.set(value); }
    public ObjectProperty<Bounds> visibleTimeRangeProperty() { return visibleTimeRange; }

    /** Specifies the region in the correlation matrix which is currently selected by the user.
     * A null value specifies that no selection is present. */
    private final ObjectProperty<AggregatedCorrelationMatrix.MatrixRegionData> activeCorrelationMatrixRegion = new SimpleObjectProperty<>(null);
    public ObjectProperty<AggregatedCorrelationMatrix.MatrixRegionData> activeCorrelationMatrixRegionProperty() { return activeCorrelationMatrixRegion; }
    public MatrixRegionData getActiveCorrelationMatrixRegion() { return activeCorrelationMatrixRegion.get(); }
    public void setActiveCorrelationMatrixRegion(MatrixRegionData value) { activeCorrelationMatrixRegion.set(value); }

    /**
     * Specifies filter ranges on the cells of the correlogram.
     * The first dimension refers to the statistic index (see e.g. {@link Data.Correlation.CorrelationMatrix#MEAN}).
     * If the second level array is null, no filter shall be applied.
     * The second dimension refers to the bound: 0: lower bound, 1: upper bound.
     */
    public double[][] getMatrixFilterRanges(){ return matrixFilterRanges.get(); }
    private final ObjectProperty<double[][]> matrixFilterRanges = new SimpleObjectProperty<>(new double[CorrelationMatrix.NUM_STATS][]);
    public ObjectProperty<double[][]> matrixFilterRangesProperty(){ return matrixFilterRanges; }
    public void setMatrixFilterRanges(double[][] matrixFilterRanges){ this.matrixFilterRanges.set(matrixFilterRanges); }

    /**
     * Specifies how uncertainty is visualized in the correlogam.
     * See {@link UNCERTAINTY_VISUALIZATION}
     */
    private final ObjectProperty<UNCERTAINTY_VISUALIZATION> uncertaintyVisualization = new SimpleObjectProperty<>();
    public ObjectProperty<UNCERTAINTY_VISUALIZATION> uncertaintyVisualizationProperty(){return uncertaintyVisualization;}
    public UNCERTAINTY_VISUALIZATION getUncertaintyVisualization(){return uncertaintyVisualization.get();}
    public void setUncertaintyVisualization(UNCERTAINTY_VISUALIZATION uncertaintyVisualization){this.uncertaintyVisualization.set(uncertaintyVisualization);}

    // -----------------------------------------------------------------------------------------------------------------
    // METHODS
    // -----------------------------------------------------------------------------------------------------------------

    public  SharedData(){ experiment = new Experiment(); }
    public SharedData(Experiment experiment) {
        this.experiment = experiment;
    }

    public CorrelationMatrix.CorrelationColumn getHighlightedColumn() {
        if(getCorrelationMatrix() == null) return null;
        int x = getActiveCorrelationMatrixRegion().column;
        if(x < 0 || x >= getCorrelationMatrix().getSize()) return null;
        return getCorrelationMatrix().getColumns().get(x);
    }


}
