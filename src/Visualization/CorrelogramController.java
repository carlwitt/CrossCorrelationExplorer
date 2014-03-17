package Visualization;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelationMatrix.Column;
import Data.SharedData;
import Data.TimeSeries;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
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
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import javafx.util.converter.NumberStringConverter;

/**
 * 
 * @author Carl Witt
 */
public class CorrelogramController {
    
    SharedData sharedData;          // data that is shared between the views
    
    Node chartPlotBackground;       // is a Region object with exactly the size and position the equals the desired draw region on the chart
    
    @FXML protected LineChart lineChart;
    @FXML protected NumberAxis xAxis; // time axis
    @FXML protected NumberAxis yAxis; // temperature axis
    Node xTickMarks, yTickMarks;
    
    @FXML protected Canvas chartCanvas;
    
    public void setSharedData(SharedData sharedData){
        this.sharedData = sharedData;
        
        // when loading additional time series, reset the view to show the whole time span
        sharedData.dataModel.timeSeries.addListener(new MapChangeListener<Integer, TimeSeries>() {
            @Override public void onChanged(MapChangeListener.Change<? extends Integer, ? extends TimeSeries> change) {
 
                resetView(null);
                drawContents();
            }
            
        });
        
        sharedData.correlationMatrixProperty().addListener(new ChangeListener<CorrelationMatrix>() {
            @Override public void changed(ObservableValue<? extends CorrelationMatrix> ov, CorrelationMatrix t, CorrelationMatrix t1) {
                System.out.println("correlation matrix changed "+t1);
                drawContents();
            }
        });
    }
    
    public void initialize(){
        
        xAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("####")));
        yAxis.setTickLabelFormatter(new NumberStringConverter(new  DecimalFormat("0.000")));
        
        xTickMarks = xAxis.lookup(".axis-tick-mark"); 
        yTickMarks = yAxis.lookup(".axis-tick-mark");
        xTickMarks.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        yTickMarks.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        
        chartPlotBackground = lineChart.lookup(".chart-plot-background");
        chartPlotBackground.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        xAxis.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        yAxis.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        
        // auto adjust tick labels and detail slider
        xAxis.lowerBoundProperty().addListener(axisBoundsChanged);
        xAxis.upperBoundProperty().addListener(axisBoundsChanged);
        yAxis.lowerBoundProperty().addListener(axisBoundsChanged);
        yAxis.upperBoundProperty().addListener(axisBoundsChanged);
        
    }
    
    /** @return An affine transformation that transforms points in data space (e.g. year/temperature) into coordinates on the canvas */
    protected Affine dataToScreen() {
        Transform translate = new Translate(-xAxis.getLowerBound(), -yAxis.getLowerBound());
        double sx = chartCanvas.getWidth() / (xAxis.getUpperBound() - xAxis.getLowerBound());
        double sy = chartCanvas.getHeight() / (yAxis.getUpperBound() - yAxis.getLowerBound());
        Transform scale = new Scale(sx, -sy);
        //        Transform mirror = new Scale(1, -1).createConcatenation(new Translate(0, chartCanvas.getHeight()));
        Transform mirror = new Translate(0, chartCanvas.getHeight());
        return new Affine(mirror.createConcatenation(scale).createConcatenation(translate));
    }

    public void drawContents() {
        
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        
        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);
        
        Affine dataToScreen = dataToScreen();

        // data to render
        CorrelationMatrix matrix = sharedData.getcorrelationMatrix();
        if(matrix == null) return;
        List<CorrelationMatrix.Column> columns = matrix.getResultItems();
        
        // for each column of the matrix (a time window)
        Random randomColors = new Random(0);
        for(Column column : columns){
            // window covers a time period equal to the length of the resulting cross-correlation
            double width = column.mean.length;
            // center windows around their starting point because shifts extend in both directions
            double minX = column.windowXOffset - 0.5*width;
            
            double height = 1; // each cell represents a time lag applied to a window. time lags are discrete, as we operate on discrete functions
            
            Point2D ulc, widthHeight; // upper left corner, bottom right corner of the cell
            for (int lag = 0; lag < column.mean.length; lag++) {
                gc.setFill(new Color(randomColors.nextDouble(), randomColors.nextDouble(), randomColors.nextDouble(), 0.6));
                double minY = lag + 0.5; // center cells vertically around their time lag offset
                ulc = dataToScreen.transform(minX, minY);
                widthHeight = dataToScreen.transform(width, height);
                gc.fillRect(ulc.getX(), ulc.getY(), widthHeight.getX() , widthHeight.getY());
                
            }
//            double
//            gc.fillRect(d, d1, d2, d3);
        }
            
        
        positionChartCanvas();
    }
    
    /** Adapts tick units and labels. Adapts level of detail slider */
     ChangeListener<Number> axisBoundsChanged = new ChangeListener<Number>() {
         @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
             // max reduction to 50px per data point
             double totalNumPoints = sharedData.dataModel.getTimeSeriesLength();
             double pointsPerPix = totalNumPoints/chartCanvas.getWidth();
             xAxis.setTickUnit((xAxis.getUpperBound() - xAxis.getLowerBound()) / 20);
             yAxis.setTickUnit((yAxis.getUpperBound() - yAxis.getLowerBound()) / 5);
         }
     };

    public void scroll(ScrollEvent e) {
        // TODO: don't let the user zoom and scroll outside content areas
        // TODO: scroll amount depends on zoom level
        double xScrollMult = -0.1;
        double yScrollMult = 0.001;
        xAxis.setLowerBound(xAxis.getLowerBound() + xScrollMult * e.getDeltaX());
        xAxis.setUpperBound(xAxis.getUpperBound() + xScrollMult * e.getDeltaX());
        yAxis.setLowerBound(yAxis.getLowerBound() + yScrollMult * e.getDeltaY());
        yAxis.setUpperBound(yAxis.getUpperBound() + yScrollMult * e.getDeltaY());
        drawContents();
    }

    public void zoom(ZoomEvent e) {
        // TODO: don't let the user zoom and scroll outside content areas
        // TODO: zoom y, too
        try {
            Affine screenToData = dataToScreen().createInverse();
            Point2D mousePositionScreen = new Point2D(e.getX(), e.getY());
            Point2D mousePositionData = screenToData.transform(mousePositionScreen);
            //TODO: constant zoom amount
            Bounds boundsData = new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getUpperBound() - xAxis.getLowerBound(), yAxis.getUpperBound() - yAxis.getLowerBound());
            Scale zoomScale = new Scale(1 / e.getZoomFactor(), 1, mousePositionData.getX(), mousePositionData.getY());
            Bounds boundsZoomed = zoomScale.transform(boundsData);
            xAxis.setLowerBound(boundsZoomed.getMinX());
            xAxis.setUpperBound(boundsZoomed.getMaxX());
            yAxis.setLowerBound(boundsZoomed.getMinY());
            yAxis.setUpperBound(boundsZoomed.getMaxY());
            drawContents();
            // show computed mouse data position
            //            Affine dataToScreen = screenToData.createInverse();
            Point2D reScreen = dataToScreen().transform(mousePositionData);
            chartCanvas.getGraphicsContext2D().strokeOval(reScreen.getX() - 3, reScreen.getY() - 3, 6, 6);
            System.out.println(String.format("mouse position (data) %s\nscene: %s\nrescreen: %s", mousePositionData, mousePositionScreen, reScreen));
        } catch (NonInvertibleTransformException ex) {
            Logger.getLogger(TimeSeriesViewController.class.getName()).log(Level.SEVERE, null, ex);
            System.err.println("invert error");
        }
    }

    public void resetView(ActionEvent e) {
        // TODO: add a padding of max(5px, 2.5% of the pixel width/height of the canvas)
        xAxis.setLowerBound(sharedData.dataModel.getMinX());
        yAxis.setLowerBound(sharedData.dataModel.getMinY());
        xAxis.setUpperBound(sharedData.dataModel.getMaxX());
        yAxis.setUpperBound(sharedData.dataModel.getMaxY());
        drawContents();
    }

    /** Aligns the canvas node (where the data is drawn) to its correct overlay position above the axes.
     TODO: chart background changes are not always fired and not always correctly */
    protected void positionChartCanvas() {
        final Transform localToView = chartPlotBackground.getLocalToParentTransform().createConcatenation(chartPlotBackground.getParent().getLocalToParentTransform());
        Bounds b = localToView.transform(chartPlotBackground.getLayoutBounds());
        chartCanvas.setLayoutX(b.getMinX());
        chartCanvas.setLayoutY(b.getMinY());
        chartCanvas.setWidth(b.getWidth());
        chartCanvas.setHeight(b.getHeight());
    }
    private final ChangeListener<Bounds> positionCanvasOnChartBackground = new ChangeListener<Bounds>() {
        @Override public void changed(ObservableValue<? extends Bounds> ov, Bounds oldBounds, Bounds newBounds) { drawContents(); }
    };

}
