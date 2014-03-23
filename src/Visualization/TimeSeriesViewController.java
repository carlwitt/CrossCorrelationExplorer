package Visualization;

import Data.*;

import java.text.DecimalFormat;
import java.util.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.util.converter.NumberStringConverter;

/**
 * 
 * @author Carl Witt
 */
public class TimeSeriesViewController {
    
    SharedData sharedData;          // data that is shared between the views
    
    /** maps a color to each set of time series (for instance the time series in correlation set A, in correlation set B and temporary time series for preview). */
    HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
        
    Visualization.LineChart timeSeriesChart = new Visualization.LineChart();
            
    /** controls the level of detail with which time series are drawn. 
     * this is important since rendering all series with all points takes very long and is not the main purpose of the software. */
    @FXML protected Slider detailSlider;
    @FXML protected Label levelOfDetailLabel;
    
    @FXML protected AnchorPane timeSeriesPane;
    
    public void setSharedData(final SharedData sharedData){
        this.sharedData = sharedData;
        
        seriesSets.put(new Color(0, 0, 0, 0.5), sharedData.previewTimeSeries);
        seriesSets.put(Color.web("#00cc52").deriveColor(0, 1, 1, 0.5), sharedData.correlationSetA);
        seriesSets.put(Color.web("#4333ff").deriveColor(0, 1, 1, 0.5), sharedData.correlationSetB);
        
        timeSeriesChart.sharedData = sharedData;
        timeSeriesChart.seriesSets = seriesSets;
        
        timeSeriesChart.drawEachNthDataPointProperty().bind(detailSlider.valueProperty());
        
        // when loading additional time series, reset the view to show the whole time span
//        sharedData.dataModel.timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
//            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
//                resetView(null);
//                drawContents();
//            }
//        });
        
        ListChangeListener<TimeSeries> drawContentListener = new ListChangeListener<TimeSeries>() {
            @Override public void onChanged(ListChangeListener.Change<? extends TimeSeries> change) { 
                resetView(null);
            }
        };
        sharedData.previewTimeSeries.addListener(drawContentListener);
        sharedData.correlationSetA.addListener(drawContentListener);
        sharedData.correlationSetB.addListener(drawContentListener);
        
    }
    
    public void initialize(){
        
        timeSeriesPane.getChildren().add(timeSeriesChart.getNode());
        timeSeriesChart.getNode().toBack();
        
        AnchorPane.setTopAnchor(timeSeriesChart.getNode(), 0.);
        AnchorPane.setRightAnchor(timeSeriesChart.getNode(), 0.);
        AnchorPane.setBottomAnchor(timeSeriesChart.getNode(), 0.);
        AnchorPane.setLeftAnchor(timeSeriesChart.getNode(), 0.);
        
        detailSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
                levelOfDetailLabel.setText("show every N-th point: "+Math.round((Double)t1));
//                if( ! detailSlider.isPressed() || ! detailSlider.isValueChanging()){
                    timeSeriesChart.drawContents();
//                }
            }
        });
        
        timeSeriesChart.xAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("####")));
        timeSeriesChart.yAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("0,000")));
        
        // auto adjust tick labels and detail slider
        timeSeriesChart.xAxis.lowerBoundProperty().addListener(updateDetailSlider);
        timeSeriesChart.xAxis.upperBoundProperty().addListener(updateDetailSlider);
        timeSeriesChart.yAxis.lowerBoundProperty().addListener(updateDetailSlider);
        timeSeriesChart.yAxis.upperBoundProperty().addListener(updateDetailSlider);
        
        timeSeriesChart.xAxis.setLabel("Year as Geotime (t + 1950)");
        timeSeriesChart.yAxis.setLabel("Temperature ˚C");
//        timeSeriesChart.yAxis.setLowerBound(-10);
//        timeSeriesChart.yAxis.setUpperBound(10);
        
    }
    
    /** Adapts tick units and labels. Adapts level of detail slider */
     ChangeListener<Number> updateDetailSlider = new ChangeListener<Number>() {
         @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
             // max reduction to 50px per data point
             double totalNumPoints = sharedData.dataModel.getNumDataPointsInRange(timeSeriesChart.xAxis.getLowerBound(), timeSeriesChart.xAxis.getUpperBound());
             double pointsPerPix = totalNumPoints/timeSeriesChart.chartCanvas.getWidth();
             detailSlider.setMin(Math.max(1, 0.5*pointsPerPix)); // highest resolution is 2 points per pix
             detailSlider.setMax(timeSeriesChart.chartCanvas.getWidth()/20 * pointsPerPix); // lowest resolution is 40 points over the full width
             timeSeriesChart.xAxis.setTickUnit((timeSeriesChart.xAxis.getUpperBound() - timeSeriesChart.xAxis.getLowerBound()) / 20);
             timeSeriesChart.yAxis.setTickUnit((timeSeriesChart.yAxis.getUpperBound() - timeSeriesChart.yAxis.getLowerBound()) / 5);
         }
     };
     
    public void resetView(ActionEvent e) {
        // TODO: add a padding of max(5px, 2.5% of the pixel width/height of the canvas)
        timeSeriesChart.xAxis.setLowerBound(sharedData.dataModel.getMinX());
        timeSeriesChart.yAxis.setLowerBound(sharedData.dataModel.getMinY());
        timeSeriesChart.xAxis.setUpperBound(sharedData.dataModel.getMaxX());
        timeSeriesChart.yAxis.setUpperBound(sharedData.dataModel.getMaxY());
        timeSeriesChart.drawContents();
    }

    

}
