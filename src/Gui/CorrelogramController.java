package Gui;

import Data.SharedData;
import Visualization.Correlogram;
import Visualization.CorrelogramLegend;
import Visualization.MultiDimensionalPaintScale;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.NumberStringConverter;

import javax.imageio.ImageIO;
import java.io.File;
import java.text.DecimalFormat;

/**
 * Controller for the correlogram view. Manages the correlogram and its legend.
 * Listens to changes in the correlation matrix that is stored in {@link #sharedData}.
 * @author Carl Witt
 */
public class CorrelogramController {


    public static final String[] statisticsLabels = new String[]{"mean", "standard deviation", "median", "interquartile range", "fraction positive significant", "fraction negative significant", "fraction significant"};
    /** Data that is shared between views to implement linked views. */
    private SharedData sharedData;

    public final Correlogram correlogram;
    public final CorrelogramLegend legend;

    @FXML Pane correlogramRoot;
    @FXML VBox correlogramView;
    @FXML StackPane correlogramPane;
    @FXML StackPane legendPane;
    @FXML ImageView correlogramHelpImg;
    @FXML ToggleButton linkWithTimeSeriesViewToggle;
    @FXML ToggleButton scatterPlotToggle;
    @FXML ToggleButton columnUncertaintyToggle;
    @FXML ToggleButton hintonUncertaintyToggle;
    @FXML ToggleButton hintonDrawQuartilesToggle;
    @FXML TabPane visualizationSelector;
    @FXML SplitPane correlogramLegendSplit;

    /** Captions for the tabs that allow switching between the render modes. */
    private static final String[] renderModeLabels = new String[]{
            "Mean/Std Dev",           // mean and standard deviation
            "Median/IQR",             // median and interquartile range
            "Negative Significant",   // percentage of significantly negative correlated window pairs
            "Positive Significant",   // percentage of significantly positive correlated window pairs
            "Absolute Significant"    // percentage of significantly correlated window pairs
    };

    public CorrelogramController() {
        correlogram = new Correlogram(new MultiDimensionalPaintScale(400,200));
        legend = new CorrelogramLegend(correlogram, new MultiDimensionalPaintScale(400,200));
    }

    public void initialize() {

        // configure and add correlogram chart to GUI
        correlogramLegendSplit.getItems().add(correlogram);
        correlogram.setMinWidth(10);
        correlogram.setPrefWidth(20);
        correlogram.xAxis.setTickLabelFormatter(new NumberStringConverter(new DecimalFormat("####")));
        correlogram.xAxis.setLabel("Year");
        correlogram.yAxis.setLabel("Time lag (years)");
        correlogram.yAxis.setTickLabelFormatter(new NumberStringConverter(new DecimalFormat("#")));

        // configure and add correlogram legend chart to GUI
        correlogramLegendSplit.getItems().add(legend);
        correlogramLegendSplit.setDividerPositions(0.85);
        legend.setPrefHeight(160);
        legend.xAxis.setLabel("μ");//Mean
        legend.yAxis.setLabel("σ");//Standard Deviation
        Tooltip xAxisTip = new Tooltip("Average correlation within all aligned time windows.");
        Tooltip yAxisTip = new Tooltip("Standard deviation of the correlation within all aligned time windows.");
        Tooltip.install(legend.xAxis, xAxisTip);
        Tooltip.install(legend.yAxis, yAxisTip);

        // label tabs according to render mode labels
        for(Correlogram.RENDER_MODE mode : Correlogram.RENDER_MODE.values()){
            int idx = mode.ordinal();
            visualizationSelector.getTabs().get(idx).setText(renderModeLabels[idx]);
        }

        // on selecting different tabs, change the correlogram display mode
        visualizationSelector.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            Correlogram.RENDER_MODE newMode = Correlogram.RENDER_MODE.values()[(int)newValue];
            correlogram.setRenderMode(newMode);
            correlogram.drawContents();
            legend.updateRenderMode(newMode);

            legend.resetView();
            legend.drawContents();
        });

        // push scatter plot toggle value to legend if changed.
        scatterPlotToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            legend.setDrawScatterPlot(newValue);
            legend.drawContents();
        });

        // switch between uncertainty visualization methods
        columnUncertaintyToggle.selectedProperty().addListener((observable, oldValue, toggleActive) -> {
            Correlogram.UNCERTAINTY_VISUALIZATION newVis;
            if(toggleActive) newVis = Correlogram.UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH;
            else newVis = hintonUncertaintyToggle.isSelected() ? Correlogram.UNCERTAINTY_VISUALIZATION.HINTON : Correlogram.UNCERTAINTY_VISUALIZATION.COLOR;
            sharedData.setUncertaintyVisualization(newVis);
        });
        hintonUncertaintyToggle.selectedProperty().addListener((observable, oldValue, toggleActive) -> {
            Correlogram.UNCERTAINTY_VISUALIZATION newVis;
            if(toggleActive) newVis = Correlogram.UNCERTAINTY_VISUALIZATION.HINTON;
            else newVis = columnUncertaintyToggle.isSelected() ? Correlogram.UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH : Correlogram.UNCERTAINTY_VISUALIZATION.COLOR;
            sharedData.setUncertaintyVisualization(newVis);
        });

        // switch between hinton variants
        hintonDrawQuartilesToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            correlogram.hintonDrawQuartiles = newValue;
            correlogram.drawContents();
        });


    }
    
    public void setSharedData(final SharedData sharedData) {
        
        this.sharedData = sharedData;
        correlogram.setSharedData(sharedData);
        legend.setSharedData(sharedData);
        
        // report navigation in the correlogram to the time series view (via the shared data)
        correlogram.clipRegionDCProperty().addListener(this::pushCorrelogramNavigation);
        
//        listen to navigation in the time series view (via shared data)
        sharedData.visibleTimeRangeProperty().addListener(this::receiveNewVisibleTimeRange);

        // enable correlogram controls only when a matrix is selected
        correlogramRoot.disableProperty().bind(sharedData.correlationMatrixProperty().isNull());
        
    }

    private void receiveNewVisibleTimeRange(ObservableValue<? extends Bounds> ov, Bounds t, Bounds newBounds){
        if(linkWithTimeSeriesViewToggle.isSelected()){
            if(correlogram.aspectRatioFixed()) correlogram.adaptYAxis(newBounds, correlogram.getDataPointsPerPixelRatio());
            else correlogram.xAxis.setAxisBoundsDC(new BoundingBox(newBounds.getMinX(), 0, newBounds.getWidth(), 0));
            correlogram.drawContents();
        }
    }

    // report changes in the correlogram axis bounds to sync the time series view
    private void pushCorrelogramNavigation(ObservableValue<?> ov, Bounds t, Bounds newBounds) {

        Bounds oldTimeSeriesBounds = sharedData.getVisibleTimeRange();

        if(linkWithTimeSeriesViewToggle.isSelected() && oldTimeSeriesBounds != null){
            Bounds newTimeSeriesBounds = new BoundingBox(
                    newBounds.getMinX(),
                    oldTimeSeriesBounds.getMinY(),
                    newBounds.getWidth(),
                    oldTimeSeriesBounds.getHeight());
            sharedData.setVisibleTimeRange(newTimeSeriesBounds);
        }
    }

    public void resetView() {
        correlogram.resetView();
        correlogram.drawContents();
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
            System.err.println("Couldn't write the correlogram image.");
            s.printStackTrace();
        }

    }

}
