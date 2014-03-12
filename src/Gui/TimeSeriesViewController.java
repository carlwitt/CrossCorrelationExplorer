/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package Gui;

import Data.ComplexSequence;
import Data.SharedData;
import Data.TimeSeries;
import java.awt.geom.Point2D;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.transform.*;

/**
 * FXML Controller class
 *
 * @author macbookdata
 */
public class TimeSeriesViewController implements Initializable {
    
    private SharedData sharedData;
    
    @FXML private AnchorPane timeSeriesPane;
    
    @FXML private LineChart lineChart;
    @FXML private NumberAxis xAxis; // time axis
    @FXML private NumberAxis yAxis; // temperature axis
    @FXML private Canvas chartCanvas;
    
    @FXML private Label levelOfDetailLabel;
    @FXML private Slider detailSlider;
    
    Node xTickMarks, yTickMarks;
    Node chartPlotBackground;        //is a Region object
    
    public void setSharedData(final SharedData sharedData){
        
        this.sharedData = sharedData;
        
        // the list of loaded time series listens to changes in the data model
        sharedData.dataModel.timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
                
//                lineChart.getData().clear();
                xAxis.setLowerBound(sharedData.dataModel.getMinX());
                yAxis.setLowerBound(sharedData.dataModel.getMinY());
                
                xAxis.setUpperBound(sharedData.dataModel.getMaxX());
                
                yAxis.setUpperBound(sharedData.dataModel.getMaxY());
                xAxis.setTickUnit((xAxis.getUpperBound()-xAxis.getLowerBound())/20);
                
//                    XYChart.Series series = new XYChart.Series();
//                    series.setName("Series "+ts.id);
//                    ComplexSequence data = ts.getDataItems();
//                    for (int j = 0; j < data.re.length; j+=1) {
//                        series.getData().add(new XYChart.Data(data.re[j], data.im[j]));
//                    }
//                    lineChart.getData().add(series);
            }
        });
        
//        sharedData.dataModel.timeSeries.put(1, new TimeSeries(ComplexSequence.create(new double[]{1,2,3,4,5,10}, new double[]{1,2,3,4,5,-5})));
        
//        final Transform localToView = chartPlotBackground.getLocalToParentTransform().createConcatenation(chartPlotBackground.getParent().getLocalToParentTransform());
//        Bounds b;
//        b = localToView.transform(chartPlotBackground.getBoundsInParent());
//        
//        chartCanvas.setLayoutX(b.getMinX());
//        chartCanvas.setLayoutY(b.getMinY());
//        chartCanvas.setWidth(b.getWidth());
//        chartCanvas.setHeight(b.getHeight());
//        
//        fillCanvas(null);
        
//        fillCanvas(null);
//        positionCanvasOnChartBackground.changed(null, null, chartPlotBackground.getBoundsInLocal());
        
    }
    
    @Override public void initialize(URL url, ResourceBundle rb) {
        
//        xTickMarks = xAxis.lookup(".axis-tick-mark");
//        yTickMarks = yAxis.lookup(".axis-tick-mark");
        chartPlotBackground = lineChart.lookup(".chart-plot-background");
        chartPlotBackground.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        
        
    }
    
    public void fillCanvas(ActionEvent e){
        
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        
        // reset transform to identity
        gc.setTransform(new Affine(new Translate()));
        // clear previous contents
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
//        Bounds b = chartCanvas.getLayoutBounds();
//        gc.setStroke(new Color(1, 0.5, 0.25, 0.7));
//        gc.setLineWidth(2);
//        gc.strokeRect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        
        // compute affine transform that maps data coordinates to screen coordinates
        Transform translate = new Translate(-xAxis.getLowerBound(), -yAxis.getLowerBound());
        
        double sx = chartCanvas.getWidth() / (xAxis.getUpperBound() - xAxis.getLowerBound());
        double sy = chartCanvas.getHeight()/ (yAxis.getUpperBound() - yAxis.getLowerBound());
        Transform scale = new Scale(sx, -sy);

        Transform mirror = new Scale(1, -1).createConcatenation(new Translate(0, chartCanvas.getHeight()));
        
        Affine dataToScreen = new Affine(new Scale(1, -1).createConcatenation(mirror.createConcatenation(scale).createConcatenation(translate)));
        gc.setTransform(dataToScreen);
        
        // compute line width such that the line covers ~ 2px
        double domainRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        double domain1Pixel = domainRange/chartCanvas.getHeight();
        gc.setLineWidth(2*domain1Pixel);
        
//        ComplexSequence data = sharedData.dataModel.timeSeries.get(1).getDataItems();
//        for (int i = 1; i < data.re.length; i++) {
//            gc.strokeLine(data.re[i-1], data.im[i-1], data.re[i], data.im[i]);
//        }
        
        for (TimeSeries timeSeries : sharedData.dataModel.timeSeries.values()) {

            gc.setStroke(new Color(Math.random(), Math.random(), Math.random(),1));
            gc.setMiterLimit(0);
            ComplexSequence data = timeSeries.getDataItems();
            
            // get level of detail
            int step = Math.max(1,(int) (detailSlider.getMax() - detailSlider.getValue()));
            levelOfDetailLabel.setText("detail: "+step);
            
            for (int i = 1*step; i < data.re.length; i+=step) {
//                System.out.println(String.format("before: %s, after: %s", 
//                        new javafx.geometry.Point2D(data.re[i], data.im[i]),
//                        dataToScreen.transform(data.re[i], data.im[i])
//                ));
                gc.strokeLine(data.re[i-step], data.im[i-step], data.re[i], data.im[i]);
            }
        }
    }
    
    private ChangeListener<Bounds> positionCanvasOnChartBackground = new ChangeListener<Bounds>() {
        
        @Override public void changed(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {
            
            final Transform localToView = chartPlotBackground.getLocalToParentTransform().createConcatenation(chartPlotBackground.getParent().getLocalToParentTransform());
            Bounds b;
            b = localToView.transform(t1);
            
            chartCanvas.setLayoutX(b.getMinX());
            chartCanvas.setLayoutY(b.getMinY());
            chartCanvas.setWidth(b.getWidth());
            chartCanvas.setHeight(b.getHeight());
            
            fillCanvas(null);
        }
    };
    
    public void zoomIn(ActionEvent e){
        NumberAxis xAxis = (NumberAxis) lineChart.getXAxis(),
                yAxis = (NumberAxis) lineChart.getYAxis();
        xAxis.setAutoRanging(false);xAxis.setAnimated(false);
        yAxis.setAutoRanging(false);yAxis.setAnimated(false);
        
        xAxis.setLowerBound(xAxis.getLowerBound()*0.9);
        xAxis.setUpperBound(xAxis.getUpperBound()*0.9);
        
        yAxis.setLowerBound(yAxis.getLowerBound()*0.9);
        yAxis.setUpperBound(yAxis.getUpperBound()*0.9);
    }
    
    
}
