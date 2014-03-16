/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package Gui;

import Data.ComplexSequence;
import Data.SharedData;
import Data.TimeSeries;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.*;
import javafx.util.converter.NumberStringConverter;

/**
 * TODO: reasonable tick units
 * TODO: zoom and scroll cause either the chart background to report strange layout bounds or cause the canvas otherwise to be positioned strangely
 * @author Carl Witt
 */
public class TimeSeriesViewController implements Initializable {
    
    private SharedData sharedData;
    
    @FXML private LineChart lineChart;
    @FXML private NumberAxis xAxis;     // time axis
    @FXML private NumberAxis yAxis;     // temperature axis
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
 
                //TODO: readjust level of detail slider here
                
                // on adding a new time series 
                xAxis.setLowerBound(sharedData.dataModel.getMinX());
                yAxis.setLowerBound(sharedData.dataModel.getMinY());
                
                xAxis.setUpperBound(sharedData.dataModel.getMaxX());
                
                yAxis.setUpperBound(sharedData.dataModel.getMaxY());
                
                xAxis.setTickUnit((xAxis.getUpperBound()-xAxis.getLowerBound())/20);
                yAxis.setTickUnit((yAxis.getUpperBound()-yAxis.getLowerBound())/5);
                
                fillCanvas(null);
            }
            
        });
        
        detailSlider.valueProperty().addListener(new ChangeListener<Number>() {

            @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
                levelOfDetailLabel.setText("show every N-th point: "+Math.round((Double)t1));
//                if( ! detailSlider.isPressed() || ! detailSlider.isValueChanging()){
                    fillCanvas(null);
//                }s
            }
        });
        
        chartPlotBackground.layoutBoundsProperty().addListener(new ChangeListener<Bounds>() {
            @Override public void changed(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {
                // max reduction to 50px per data point
                double totalNumPoints = sharedData.dataModel.getTimeSeriesLength();
                double pointsPerPix = totalNumPoints/chartCanvas.getWidth();
                detailSlider.setMin(Math.max(1, 0.5*pointsPerPix)); // highest resolution is 2 points per pix
                detailSlider.setMax(chartCanvas.getWidth()/40 * pointsPerPix); // lowest resolution is 40 points over the full width
            }
        });
        
        // TODO: chart background changes are not always fired and not always correctly
        positionChartCanvas();
        
//        sharedData.dataModel.timeSeries.put(1, new TimeSeries(ComplexSequence.create(new double[]{1,2,3,4,5,10}, new double[]{1,2,3,4,5,-5})));
        
    }
    
    @Override public void initialize(URL url, ResourceBundle rb) {
        
        xTickMarks = xAxis.lookup(".axis-tick-mark"); 
        yTickMarks = yAxis.lookup(".axis-tick-mark");
        
        xAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("####")));
        yAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("0.000")));
        xTickMarks.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
//                new ChangeListener<Bounds>() {
//            @Override public void changed(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {
//                System.out.println(String.format("new x tickmarks bounds: %s", t1));
//            }
//        });
        yTickMarks.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
//                new ChangeListener<Bounds>() {
//            @Override public void changed(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {
//                System.out.println(String.format("new y tickmarks bounds: %s", t1));
//            }
//        });
        
        chartPlotBackground = lineChart.lookup(".chart-plot-background");
        chartPlotBackground.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        xAxis.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        yAxis.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        
    }
    
    protected Affine dataToScreen(){
        Transform translate = new Translate(-xAxis.getLowerBound(), -yAxis.getLowerBound());
        
        double sx = chartCanvas.getWidth() / (xAxis.getUpperBound() - xAxis.getLowerBound());
        double sy = chartCanvas.getHeight()/ (yAxis.getUpperBound() - yAxis.getLowerBound());
        Transform scale = new Scale(sx, -sy);

//        Transform mirror = new Scale(1, -1).createConcatenation(new Translate(0, chartCanvas.getHeight()));
        Transform mirror = new Translate(0, chartCanvas.getHeight());
        
        return new Affine(mirror.createConcatenation(scale).createConcatenation(translate));
    }
    
    public void fillCanvas(ActionEvent e){
        
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        
        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
        // for testing: mark outline of the canvas
//        Bounds b = chartCanvas.getLayoutBounds();
//        gc.setStroke(new Color(1, 0.5, 0.25, 0.7));
//        gc.setLineWidth(2);
//        gc.strokeRect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        
        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();
        
        // compute line width such that the line covers ~ 2px
//        double domainRange = yAxis.getUpperBound() - yAxis.getLowerBound();
//        double domain1Pixel = domainRange/chartCanvas.getHeight();
//        gc.setLineWidth(2*domain1Pixel);
        gc.setLineWidth(1);
        gc.setMiterLimit(0);
        
        // get level of detail
        int step = Math.max(1,(int) detailSlider.getValue());
        Random randomColors = new Random(0);
        for (TimeSeries timeSeries : sharedData.dataModel.timeSeries.values()) {

            // different color for each series
            gc.setStroke(new Color(randomColors.nextDouble(), randomColors.nextDouble(), randomColors.nextDouble(),0.4));
            ComplexSequence data = timeSeries.getDataItems();
            
            Point2D nextPoint, lastPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));
            // TODO: render only visible points
            for (int i = 1*step; i < data.re.length; i+=step) {
                nextPoint = dataToScreen.transform(new Point2D(data.re[i-step], data.im[i-step])); 
                gc.strokeLine(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY());
                lastPoint=nextPoint;
            }
        }
        positionChartCanvas();
    }
    
    private final ChangeListener<Bounds> positionCanvasOnChartBackground = new ChangeListener<Bounds>() {
        
        @Override public void changed(ObservableValue<? extends Bounds> ov, Bounds oldBounds, Bounds newBounds) {
            
            System.out.println(String.format("new background bounds: min ULC (%s,%s) BRC (%s,%s)", 
                    newBounds.getMinX(), newBounds.getMinY(),newBounds.getMaxX(), newBounds.getMaxY()));
            
            positionChartCanvas();
            
            fillCanvas(null);
        }
    };
    
    public void scroll(ScrollEvent e){
        // TODO: don't let the user zoom and scroll outside content areas
        // TODO: scroll amount depends on zoom level
        double xScrollMult = -0.1;
        double yScrollMult = 0.001;
        xAxis.setLowerBound(xAxis.getLowerBound()+xScrollMult*e.getDeltaX());
        xAxis.setUpperBound(xAxis.getUpperBound()+xScrollMult*e.getDeltaX());
        
        yAxis.setLowerBound(yAxis.getLowerBound()+yScrollMult*e.getDeltaY());
        yAxis.setUpperBound(yAxis.getUpperBound()+yScrollMult*e.getDeltaY());
        
        fillCanvas(null);
    }
    public void zoom(ZoomEvent e){
        // TODO: don't let the user zoom and scroll outside content areas
        // TODO: zoom y
        try {
            Affine screenToData = dataToScreen().createInverse();
            Point2D mousePositionScreen = new Point2D(e.getX(), e.getY());
            Point2D mousePositionData = screenToData.transform(mousePositionScreen);

            //TODO: constant zoom amount
            Bounds boundsData = new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getUpperBound()-xAxis.getLowerBound(), yAxis.getUpperBound()-yAxis.getLowerBound());
            Scale zoomScale = new Scale(1/e.getZoomFactor(), 1, mousePositionData.getX(), mousePositionData.getY());
            Bounds boundsZoomed = zoomScale.transform(boundsData);

            xAxis.setLowerBound(boundsZoomed.getMinX());
            xAxis.setUpperBound(boundsZoomed.getMaxX());

            yAxis.setLowerBound(boundsZoomed.getMinY());
            yAxis.setUpperBound(boundsZoomed.getMaxY());

            fillCanvas(null);

            // show computed mouse data position
//            Affine dataToScreen = screenToData.createInverse();
            Point2D reScreen = dataToScreen().transform(mousePositionData);
            chartCanvas.getGraphicsContext2D().strokeOval(reScreen.getX()-3, reScreen.getY()-3, 6,6);
            System.out.println(String.format("mouse position (data) %s\nscene: %s\nrescreen: %s", mousePositionData, mousePositionScreen, reScreen));
            
        } catch (NonInvertibleTransformException ex) {
            Logger.getLogger(TimeSeriesViewController.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("invert error");
        }
        
    }
    
    public void resetView(ActionEvent e){
        
        //reset axes
        xAxis.setLowerBound(sharedData.dataModel.getMinX());
        yAxis.setLowerBound(sharedData.dataModel.getMinY());
        xAxis.setUpperBound(sharedData.dataModel.getMaxX());
        yAxis.setUpperBound(sharedData.dataModel.getMaxY());
        xAxis.setTickUnit((xAxis.getUpperBound()-xAxis.getLowerBound())/20);
        
        positionChartCanvas();
        
        fillCanvas(null);
    }
    
    protected void positionChartCanvas(){
        final Transform localToView = chartPlotBackground.getLocalToParentTransform().createConcatenation(chartPlotBackground.getParent().getLocalToParentTransform());
        Bounds b = localToView.transform(chartPlotBackground.getLayoutBounds());

        chartCanvas.setLayoutX(b.getMinX());
        chartCanvas.setLayoutY(b.getMinY());
        chartCanvas.setWidth(b.getWidth());
        chartCanvas.setHeight(b.getHeight());
    }
    
    
}
