package Visualization;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import java.text.DecimalFormat;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.NumberStringConverter;

/**
 * Controller for the correlogram view. Listens to changes in the correlation matrix that is stored in the shared data between the views.
 * Renders the correlation matrix and the legend.
 * @author Carl Witt
 */
public class CorrelogramController {

    /** Data that is shared between views to implement linked views. */
    SharedData sharedData;          

    /** The number of colors used to encode the mean dimension in the correlation matrix. */
    private final int meanColorResolution = 12;
    /** The number of colors used to encode the standard deviation dimension in the correlation matrix. */
    private final int standardDeviationColorResolution = 4;
    
    protected MultiDimensionalPaintScale paintScale;
    
    protected Correlogram correlogram = new Correlogram(new MultiDimensionalPaintScale(1200, 400));
    protected CorrelogramLegend legend = new CorrelogramLegend(new MultiDimensionalPaintScale(meanColorResolution, standardDeviationColorResolution));;
    
    @FXML VBox correlogramView;
    @FXML StackPane correlogramPane;
    @FXML StackPane legendPane;
    @FXML ToggleButton linkWithTimeSeriesViewToggle;
    
    public void initialize() {
        
//        legendPaintScale = new MultiDimensionalPaintScale(meanColorResolution, standardDeviationColorResolution);
//        correlogram = new Correlogram(new MultiDimensionalPaintScale(1200, 400));
//        legend = new CorrelogramLegend(new MultiDimensionalPaintScale(meanColorResolution, standardDeviationColorResolution));
         
        correlogramPane.getChildren().add(correlogram);
        correlogram.toBack();
        legendPane.getChildren().add(legend);
        legend.toBack();
        
        correlogram.xAxis.setTickLabelFormatter(new NumberStringConverter(new DecimalFormat("####")));
        correlogram.xAxis.setLabel("Year as Geotime (t + 1950)");
        correlogram.yAxis.setLabel("Years Time Lag");
        correlogram.yAxis.setTickLabelFormatter(new NumberStringConverter(new DecimalFormat("#")));
        
        legend.xAxis.setLabel("μ");//Mean
        legend.yAxis.setLabel("σ");//Standard Deviation
        Tooltip xAxisTip = new Tooltip("Average correlation within all aligned time windows.");
        Tooltip yAxisTip = new Tooltip("Standard deviation of the correlation within all aligned time windows.");
        Tooltip.install(legend.xAxis, xAxisTip);
        Tooltip.install(legend.yAxis, yAxisTip);
        
//        correlogram.setPaintScale(paintScale);
//        legend.setPaintScale(paintScale);
        
    }
    
    public void setSharedData(final SharedData sharedData) {
        
        this.sharedData = sharedData;
        correlogram.matrixProperty().bind(sharedData.correlationMatrixProperty());
        
        // render the correlogram if the result matrix has changed
        sharedData.correlationMatrixProperty().addListener(new ChangeListener<CorrelationMatrix>() {
            @Override
            public void changed(ObservableValue<? extends CorrelationMatrix> ov, CorrelationMatrix t, CorrelationMatrix t1) {
                
                // Computes a sample from the full range of the current correlation matrix (its correlation means and standard deviations).
                CorrelationMatrix valueRangeSample = valueRangeSample(meanColorResolution, standardDeviationColorResolution);
                legend.setMatrix(valueRangeSample);
//                legend.setPaintScale(new MultiDimensionalPaintScale(meanColorResolution, standardDeviationColorResolution));
                
                // reset correlogram and legend
                resetView(null);
            }
        });
        
        // report navigation in the correlogram to the time series view (via the shared data)
        correlogram.axesRangesProperty().addListener(pushCorrelogramNavigation);
        
        // listen to navigation in the time series view (via shared data)
        sharedData.visibleTimeRangeProperty().addListener(new ChangeListener() {
            @Override
            public void changed(ObservableValue ov, Object t, Object t1) {
                if(linkWithTimeSeriesViewToggle.isSelected()){
                    Rectangle2D newBounds = (Rectangle2D) t1;
                    correlogram.xAxis.setLowerBound(newBounds.getMinX());
                    correlogram.xAxis.setUpperBound(newBounds.getMaxX());
                    correlogram.drawContents();
                }
            }
        });
        
    }
    
    ChangeListener<Object> pushCorrelogramNavigation = new ChangeListener<Object>() {
        @Override public void changed(ObservableValue<? extends Object> ov, Object t, Object t1) {

            Rectangle2D oldTimeSeriesBounds = sharedData.getVisibleTimeRange();

            if(linkWithTimeSeriesViewToggle.isSelected() && oldTimeSeriesBounds != null){
                Rectangle2D newCorrelogramBounds = (Rectangle2D) t1;
                Rectangle2D newTimeSeriesBounds = new Rectangle2D(
                        newCorrelogramBounds.getMinX(), 
                        oldTimeSeriesBounds.getMinY(), 
                        newCorrelogramBounds.getWidth(), 
                        oldTimeSeriesBounds.getHeight());
                sharedData.setVisibleTimeRange(newTimeSeriesBounds);
            }
        }
    };

    /** 
     * Computes evenly spaced samples from the two value ranges of the current correlation matrix.
     * @param meanResolution number of samples taken from the value range of the first dimension (correlation mean). Must be at least 2.
     * @param stdDevResolution number of samples taken from the value range of the second dimension (correlation standard deviation). Must be at least 2.
     * @return a matrix comprised of the interpolated values or null if there is no current correlation matrix
     */
    protected CorrelationMatrix valueRangeSample(int meanResolution, int stdDevResolution){
        
        CorrelationMatrix m = sharedData.getcorrelationMatrix();
        if(m == null) return null;
        
        double[] meanRange = new double[]{m.getMeanMinValue(), m.getMeanMaxValue()};
        double[] stdDevRange = new double[]{m.getStdDevMinValue(), m.getStdDevMaxValue()};
        
        // increments of the values in each step. resolution is taken -1 because N requested values give N-1 intervals
        double meanStep = (meanRange[1]-meanRange[0])/(meanResolution-1);
        double stdDevStep = (stdDevRange[1]-stdDevRange[0])/(stdDevResolution-1);
        
        CorrelationMatrix result = new CorrelationMatrix(null);
        
        // put all combinations of sample values along the two dimensions in a matrix
        for (int i = 0; i < meanResolution; i++) {
            
            double currentMean = meanRange[0] + i*meanStep;
            
            // each column has the length of the standard deviation resolution
            double[] meanValues = new double[stdDevResolution];
            double[] stdDevValues = new double[stdDevResolution];
            
            for (int j = 0; j < stdDevResolution; j++) {
                
                double currentStdDev = stdDevRange[0] + j*stdDevStep;
                
                meanValues[j] = currentMean;
                stdDevValues[j] = currentStdDev;
                
            }
            ComplexSequence columnValues = ComplexSequence.create(meanValues, stdDevValues);
            result.append(new CorrelationMatrix.Column(columnValues, currentMean));
        }   
//System.out.println(String.format("value range sample: %s", result));
        return result;
    }
    
    public void resetView(ActionEvent e) {
        correlogram.resetView();
        correlogram.drawContents();
//        correlogram.setAxesRanges(new Rectangle2D(correlogram.xAxis.getLowerBound(), correlogram.yAxis.getLowerBound(), correlogram.xAxis.getRange(), correlogram.yAxis.getRange()));
        legend.resetView();
        legend.drawContents();
    }

}
