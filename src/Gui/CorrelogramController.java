package Gui;

import Data.SharedData;
import Visualization.Correlogram;
import Visualization.CorrelogramLegend;
import Visualization.MultiDimensionalPaintScale;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.NumberStringConverter;

import javax.imageio.ImageIO;
import java.io.File;
import java.text.DecimalFormat;

/**
 * Controller for the correlogram view. Manages the correlation matrix and the legend.
 * Listens to changes in the correlation matrix that is stored in the shared data between the views.
 * @author Carl Witt
 */
public class CorrelogramController {

    /** Data that is shared between views to implement linked views. */
    private SharedData sharedData;

    protected MultiDimensionalPaintScale paintScale;
    
    private final Correlogram correlogram = new Correlogram(new MultiDimensionalPaintScale(1200, 400));
    protected final CorrelogramLegend legend = new CorrelogramLegend(new MultiDimensionalPaintScale(1200, 400));
    
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
        correlogram.setSharedData(sharedData);
        legend.setSharedData(sharedData);
        
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
        
        // listen to changes in the min/max time lag
        sharedData.timeLagBoundsProperty().addListener(new ChangeListener() {
            @Override public void changed(ObservableValue ov, Object t, Object t1) {
                Rectangle2D currentRanges = correlogram.getAxesRanges();
                Point2D newTimeLagBounds = (Point2D) t1;
                correlogram.setAxesRanges(new Rectangle2D(currentRanges.getMinX(), newTimeLagBounds.getX(), currentRanges.getHeight(), newTimeLagBounds.getY()));
            }
        });
        
    } // set shared data
    
    // report changes in the correlogram axis bounds to sync the time series view
    private final ChangeListener<Object> pushCorrelogramNavigation = new ChangeListener<Object>() {
        @Override public void changed(ObservableValue<?> ov, Object t, Object t1) {

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

    public void resetView(ActionEvent e) {
        correlogram.resetView();
        correlogram.drawContents();
//        correlogram.setAxesRanges(new Rectangle2D(correlogram.xAxis.getLowerBound(), correlogram.yAxis.getLowerBound(), correlogram.xAxis.getRange(), correlogram.yAxis.getRange()));
        legend.resetView();
        legend.drawContents();
    }

    /**
     * Writes the current content of the correlogram view to an image file (in png format).
     * @param outputFilePath where to store the image, should include the extension png.
     */
    public void saveCorrelogramImage(String outputFilePath){

        WritableImage wim = correlogram.getCurrentViewAsImage();

        File file = new File(outputFilePath);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(wim, null), "png", file);
        } catch (Exception s) {
            System.out.println("Couldn't write the correlogram image.");
            s.printStackTrace();
        }

    }

}
