package Gui;

import Data.SharedData;
import Data.TimeSeries;
import Visualization.BinnedTimeSeriesChart;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.util.converter.NumberStringConverter;

import java.text.DecimalFormat;
import java.util.HashMap;

/**
 *
 * @author Carl Witt
 */
public class TimeSeriesViewController {
    
    private SharedData sharedData;          // data that is shared between the views
    
    /** maps a color to each set of time series (for instance the time series in correlation set A, in correlation set B and temporary time series for preview). */
    private final HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
    
    private final Visualization.TimeSeriesChart timeSeriesChart = new BinnedTimeSeriesChart();
    
    /** controls the level of detail with which time series are drawn.
     * this is important since rendering all series with all points takes very long and is not the main purpose of the software. */
//    @FXML protected Slider detailSlider;
    @FXML protected Label levelOfDetailLabel;
    @FXML protected Slider transparencySlider;
    @FXML protected Label transparencyLabel;
    @FXML CheckBox ensemble1CheckBox;
    @FXML CheckBox ensemble2CheckBox;

    @FXML protected AnchorPane timeSeriesPane;

    /** The color for ensemble 1 (green) and ensemble 2 (blue). */
    public static final Color[] ensembleColors = new Color[]{Color.web("#00cc52"), Color.web("#4333ff")};

    public void setSharedData(final SharedData sharedData){
        this.sharedData = sharedData;
        
        seriesSets.put(new Color(0, 0, 0, 0.5), sharedData.previewTimeSeries);
        seriesSets.put(ensembleColors[0], sharedData.experiment.dataModel.correlationSetA);
        seriesSets.put(ensembleColors[1], sharedData.experiment.dataModel.correlationSetB);
        
        timeSeriesChart.setSharedData(sharedData);
        timeSeriesChart.seriesSets = seriesSets;

        // axes scroll bars
        Bounds dataBounds = sharedData.experiment.dataModel.getDataBounds();
        if(dataBounds != null){
            timeSeriesChart.xAxis.setScrollBarBoundsDC(dataBounds);
            timeSeriesChart.yAxis.setScrollBarBoundsDC(dataBounds);
        }


//        timeSeriesChart.drawEachNthDataPointProperty().bind(detailSlider.valueProperty());

        sharedData.highlightedCellProperty().addListener((ov, t, t1) -> timeSeriesChart.drawContents());
        
        // listen to and report changes in zoom and pan 
        sharedData.visibleTimeRangeProperty().bindBidirectional(timeSeriesChart.axesRangesProperty());
        sharedData.visibleTimeRangeProperty().addListener((ov, t, t1) -> timeSeriesChart.drawContents());
        
        // when a new correlation matrix has been computed, reset the view
        sharedData.correlationMatrixProperty().addListener((ov, t, t1) -> resetView());

        sharedData.experiment.dataModel.correlationSetAAggregator.binSizeProperty().addListener((observable, oldValue, newValue) -> {
                levelOfDetailLabel.setText(
                        newValue.intValue() == 1 ? "Showing full resolution.":
                                     String.format("Averaging each %s data points into one.", newValue.intValue())
                );
        });

    }

    public void initialize(){

        // add chart component to the scene graph
        timeSeriesPane.getChildren().add(0, timeSeriesChart);
        timeSeriesChart.toBack(); // the reset button etc. are to be displayed on top of the chart
        AnchorPane.setTopAnchor(timeSeriesChart, 0.);
        AnchorPane.setRightAnchor(timeSeriesChart, 0.);
        AnchorPane.setBottomAnchor(timeSeriesChart, 20.);
        AnchorPane.setLeftAnchor(timeSeriesChart, 20.);

        // auto adjust tick labels and detail slider
        timeSeriesChart.xAxis.lowerBoundProperty().addListener(axisRangeChanged);
        timeSeriesChart.xAxis.upperBoundProperty().addListener(axisRangeChanged);
        timeSeriesChart.yAxis.lowerBoundProperty().addListener(axisRangeChanged);
        timeSeriesChart.yAxis.upperBoundProperty().addListener(axisRangeChanged);

        // axes configuration
        timeSeriesChart.xAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("####")));
        timeSeriesChart.xAxis.setLabel("Year");
        timeSeriesChart.yAxis.setLabel("Temperature ˚C");

        // ensemble check boxes
        timeSeriesChart.drawEnsemble1Property().bind(ensemble1CheckBox.selectedProperty());
        timeSeriesChart.drawEnsemble2Property().bind(ensemble2CheckBox.selectedProperty());

        // level of detail slider
//        detailSlider.valueProperty().addListener((ov, t, t1) -> {
//            levelOfDetailLabel.setText("show every N-th point: "+Math.round((Double)t1));
//            timeSeriesChart.drawContents();
//        });

        // transparency slider
        transparencySlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            float newTransparency = newValue.floatValue();
            transparencyLabel.setText(String.format("render transparency: %.2f", newTransparency));
            timeSeriesChart.transparency = newTransparency;
            timeSeriesChart.drawContents();
        });

    }
    
    void updateTickUnits(){
        double xRange = timeSeriesChart.xAxis.getUpperBound() - timeSeriesChart.xAxis.getLowerBound(),
               yRange = timeSeriesChart.yAxis.getUpperBound() - timeSeriesChart.yAxis.getLowerBound();
        
        // always display approximately the same number of ticks
        double xTickUnit = Math.max(1, xRange / 20),
               yTickUnit = Math.max(1, yRange / 5);
        timeSeriesChart.xAxis.setTickUnit(xTickUnit);
        timeSeriesChart.yAxis.setTickUnit(yTickUnit);
    }
    
    /** Adapts tick units and labels. Adapts level of detail slider */
    private final ChangeListener<Number> axisRangeChanged = (ov, t, t1) -> updateTickUnits();

    public void resetView() {
        timeSeriesChart.resetView();
    }


    public void drawContents() {
        if(timeSeriesChart.getAxesRanges() == null) timeSeriesChart.resetView();
        timeSeriesChart.drawContents();
    }
}
