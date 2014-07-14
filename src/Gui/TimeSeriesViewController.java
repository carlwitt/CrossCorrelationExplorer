package Gui;

import Data.SharedData;
import Data.TimeSeries;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
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
    
    private final Visualization.TimeSeriesChart timeSeriesChart = new Visualization.TimeSeriesChart();
    
    /** controls the level of detail with which time series are drawn.
     * this is important since rendering all series with all points takes very long and is not the main purpose of the software. */
    @FXML protected Slider detailSlider;
    @FXML protected Label levelOfDetailLabel;
    
    @FXML protected AnchorPane timeSeriesPane;
    
    public void setSharedData(final SharedData sharedData){
        this.sharedData = sharedData;
        
        seriesSets.put(new Color(0, 0, 0, 0.5), sharedData.previewTimeSeries);
        seriesSets.put(Color.web("#00cc52").deriveColor(0, 1, 1, 0.5), sharedData.experiment.dataModel.correlationSetA);
        seriesSets.put(Color.web("#4333ff").deriveColor(0, 1, 1, 0.5), sharedData.experiment.dataModel.correlationSetB);
        
        timeSeriesChart.sharedData = sharedData;
        timeSeriesChart.seriesSets = seriesSets;
        
        timeSeriesChart.drawEachNthDataPointProperty().bind(detailSlider.valueProperty());

        // TODO remove transparency test code
        timeSeriesChart.setOnMouseClicked(new EventHandler<MouseEvent>() {
            int curIdx = 0;
            double[] transparencies = new double[]{0.01, 0.05, 0.1, 0.2, 0.5, 1};
            @Override public void handle(MouseEvent event) {
               timeSeriesChart.transparency = transparencies[(++curIdx)%transparencies.length];
               timeSeriesChart.drawContents();
                System.out.println(String.format("timeSeriesChart.transparency: %s", timeSeriesChart.transparency));
//                File file = new File("./outTransparency"+timeSeriesChart.transparency+".png");
//                try {
//                    ImageIO.write(SwingFXUtils.fromFXImage(timeSeriesChart.getCurrentViewAsImage(), null), "png", file);
//                } catch (Exception s) {
//                    System.out.println("Couldn't write the correlogram image.");
//                    s.printStackTrace();
//                }
            }
        });

        sharedData.highlightedCellProperty().addListener((ov, t, t1) -> timeSeriesChart.drawContents());
        
        // listen to and report changes in zoom and pan 
        sharedData.visibleTimeRangeProperty().bindBidirectional(timeSeriesChart.axesRangesProperty());
        sharedData.visibleTimeRangeProperty().addListener((ov, t, t1) -> timeSeriesChart.drawContents());
        
        // when loading additional time series, reset the view to show the whole time span
//        sharedData.dataModel.timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
//            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
//                resetView(null);
//                drawContents();
//            }
//        });
        
        // when a new correlation matrix has been computed, reset the view 
        sharedData.correlationMatrixProperty().addListener((ov, t, t1) -> resetView());
        
//        ListChangeListener<TimeSeries> drawContentListener = change -> {
//            if(timeSeriesChart.getAxesRanges() == null) timeSeriesChart.resetView();
//            timeSeriesChart.drawContents();
//        };
//        sharedData.previewTimeSeries.addListener(drawContentListener);
//        sharedData.dataModel.correlationSetA.addListener(drawContentListener);
//        sharedData.dataModel.correlationSetB.addListener(drawContentListener);
        
    }

    public void initialize(){

        timeSeriesPane.getChildren().add(timeSeriesChart);
        timeSeriesChart.toBack(); // the reset button etc. are to be displayed on top of the chart
        AnchorPane.setTopAnchor(timeSeriesChart, 0.);
        AnchorPane.setRightAnchor(timeSeriesChart, 0.);
        AnchorPane.setBottomAnchor(timeSeriesChart, 0.);
        AnchorPane.setLeftAnchor(timeSeriesChart, 0.);

        // when changing the level of detail, show the results immediately
        detailSlider.valueProperty().addListener((ov, t, t1) -> {
            levelOfDetailLabel.setText("show every N-th point: "+Math.round((Double)t1));
//                if( ! detailSlider.isPressed() || ! detailSlider.isValueChanging()){
            timeSeriesChart.drawContents();
//                }
        });
        // auto adjust tick labels and detail slider
        timeSeriesChart.xAxis.lowerBoundProperty().addListener(axisRangeChanged);
        timeSeriesChart.xAxis.upperBoundProperty().addListener(axisRangeChanged);
        timeSeriesChart.yAxis.lowerBoundProperty().addListener(axisRangeChanged);
        timeSeriesChart.yAxis.upperBoundProperty().addListener(axisRangeChanged);

        timeSeriesChart.xAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("####")));
//        timeSeriesChart.yAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("0,000")));

        timeSeriesChart.xAxis.setLabel("Year as Geotime (t + 1950)");
        timeSeriesChart.yAxis.setLabel("Temperature ˚C");

        // this fires listeners which try to access not yet set properties (data model?)
//        timeSeriesChart.yAxis.setLowerBound(-10);
//        timeSeriesChart.yAxis.setUpperBound(10);

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
    private final ChangeListener<Number> axisRangeChanged = new ChangeListener<Number>() {
        @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
            
            // adjust axes level of detail
            updateTickUnits();
                    
            // adjust detail slider: max reduction to 50px per data point, max detail 0.5px per data paint
            double maxPixPerPoint = 30;
            double availableTimeSteps = sharedData.experiment.dataModel.getNumDataPointsInRange(timeSeriesChart.xAxis.getLowerBound(), timeSeriesChart.xAxis.getUpperBound());
            double availableWidth = timeSeriesChart.chartCanvas.getWidth();
            double timeStepsToShow = availableWidth / maxPixPerPoint;
            int maxSkipPoints = (int) Math.ceil(availableTimeSteps / timeStepsToShow);

//            detailSlider.setMin(1);
//            detailSlider.setMin(Math.max(1, 0.5*pointsPerPix)); // highest resolution is 2 points per pix
//            detailSlider.setMax(Math.max(1, maxSkipPoints));
        }
    };
    
    public void resetView() {
        timeSeriesChart.resetView();
    }


    public void drawContents() {
        if(timeSeriesChart.getAxesRanges() == null) timeSeriesChart.resetView();
        timeSeriesChart.drawContents();
    }
}
