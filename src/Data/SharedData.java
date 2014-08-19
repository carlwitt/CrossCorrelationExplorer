package Data;

import Data.Correlation.CorrelationMatrix;
import Visualization.Correlogram;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

import java.awt.*;
import java.util.Observable;

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
    private final ObjectProperty<Rectangle2D> visibleTimeRange = new SimpleObjectProperty<>();
    public Rectangle2D getVisibleTimeRange() { return visibleTimeRange.get(); }
    public void setVisibleTimeRange(Rectangle2D value) { visibleTimeRange.set(value); }
    public ObjectProperty visibleTimeRangeProperty() { return visibleTimeRange; }

    /** Specifies the current time window and time lag in the correlation matrix which is currently under the mouse cursor.
     * The x component specifies the 0-based index of the column, the y component specifies the 0-based index of the cell.
     * No time lag splitting here! Simply the elements with their raw time lags in range [0..column length].
     * Integer.MAX_VALUE indicates that a component has no sensible range. */
    private final ObjectProperty<Point> highlightedCell = new SimpleObjectProperty<>(new Point(-1, -1));
    public ObjectProperty highlightedCellProperty() { return highlightedCell; }
    public Point getHighlightedCell() { return highlightedCell.get(); }
    public void setHighlightedCell(Point value) { highlightedCell.set(value); }

    /**
     * Specifies filter ranges on the cells of the correlogram.
     * The first dimension refers to the statistic index (see e.g. {@link Data.Correlation.CorrelationMatrix#MEAN}).
     * If the second level array is null, no filter shall be applied.
     * The second dimension refers to the bound: 0: lower bound, 1: upper bound.
     */
    private final ObjectProperty<double[][]> matrixFilterRanges = new SimpleObjectProperty<>(new double[CorrelationMatrix.NUM_STATS][]);
    public ObjectProperty matrixFilterRangesProperty(){ return matrixFilterRanges; }
    public double[][] getMatrixFilterRanges(){ return matrixFilterRanges.get(); }
    public void setMatrixFilterRanges(double[][] matrixFilterRanges){ this.matrixFilterRanges.set(matrixFilterRanges); }

    /**
     * Specifies how uncertainty is visualized in the correlogam.
     * See {@link Visualization.Correlogram.UNCERTAINTY_VISUALIZATION}
     */
    private final ObjectProperty<Correlogram.UNCERTAINTY_VISUALIZATION> uncertaintyVisualization = new SimpleObjectProperty<>();
    public ObjectProperty uncertaintyVisualizationProperty(){return uncertaintyVisualization;}
    public Correlogram.UNCERTAINTY_VISUALIZATION getUncertaintyVisualization(){return uncertaintyVisualization.get();}
    public void setUncertaintyVisualization(Correlogram.UNCERTAINTY_VISUALIZATION uncertaintyVisualization){this.uncertaintyVisualization.set(uncertaintyVisualization);}

    // -----------------------------------------------------------------------------------------------------------------
    // METHODS
    // -----------------------------------------------------------------------------------------------------------------

    public  SharedData(){ experiment = new Experiment(); }
    public SharedData(Experiment experiment) {
        this.experiment = experiment;
    }

    public CorrelationMatrix.CorrelationColumn getHighlightedColumn() {
        if(getCorrelationMatrix() == null) return null;
        int x = getHighlightedCell().x;
        if(x < 0 || x >= getCorrelationMatrix().getResultItems().size()) return null;
        return getCorrelationMatrix().getResultItems().get(x);
    }


}
