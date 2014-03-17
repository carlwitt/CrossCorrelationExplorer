package Visualization;

import Data.ComplexSequence;
import Data.SharedData;
import Data.TimeSeries;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
public class TimeSeriesViewController {
    
    SharedData sharedData;          // data that is shared between the views
    // sets of time series to draw in a specific color (e.g. the time series in correlation set A, in correlation set B and temporary time series for preview)
    HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
        
    Node chartPlotBackground;       // is a Region object with exactly the size and position the equals the desired draw region on the chart
    
    @FXML protected LineChart lineChart;
    @FXML protected NumberAxis xAxis; // time axis
    @FXML protected NumberAxis yAxis; // temperature axis
    Node xTickMarks, yTickMarks;
    
    @FXML protected Canvas chartCanvas;
    
    @FXML protected Label levelOfDetailLabel;
    @FXML protected Slider detailSlider;
    
    public void setSharedData(final SharedData sharedData){
        this.sharedData = sharedData;
        
        seriesSets.put(new Color(0, 0, 0, 0.5), sharedData.previewTimeSeries);
        seriesSets.put(Color.web("#00cc52").deriveColor(0, 1, 1, 0.5), sharedData.correlationSetA);
        seriesSets.put(Color.web("#4333ff").deriveColor(0, 1, 1, 0.5), sharedData.correlationSetB);
        
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
                drawContents(); 
            }
        };
        sharedData.previewTimeSeries.addListener(drawContentListener);
        sharedData.correlationSetA.addListener(drawContentListener);
        sharedData.correlationSetB.addListener(drawContentListener);
        
    }
    
    public void initialize(){
        
        detailSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
                levelOfDetailLabel.setText("show every N-th point: "+Math.round((Double)t1));
//                if( ! detailSlider.isPressed() || ! detailSlider.isValueChanging()){
                    drawContents();
//                }
            }
        });
        
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
        
        // for testing: mark outline of the canvas
        //        Bounds b = chartCanvas.getLayoutBounds();
        //        gc.setStroke(new Color(1, 0.5, 0.25, 0.7));
        //        gc.setLineWidth(2);
        //        gc.strokeRect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        // compute affine transform that maps data coordinates to screen coordinates
        
        Affine dataToScreen = dataToScreen();
        
        // simple complexity reduction: display only every n-th point
        int step = Math.max(1, (int) detailSlider.getValue());

        Random randomColors = new Random(0);
        for (Map.Entry<Color, ObservableList<TimeSeries>> coloredSet : seriesSets.entrySet()) {

            // different color for each series (but always the same for each series)
//            gc.setStroke(new Color(randomColors.nextDouble(), randomColors.nextDouble(), randomColors.nextDouble(), 0.6));
            gc.setStroke(coloredSet.getKey());
            gc.setLineWidth(randomColors.nextDouble()+1);
            
            for (TimeSeries ts : coloredSet.getValue()) {

                if( ! sharedData.dataModel.containsValue(ts))
                    continue;
                ComplexSequence data = ts.getDataItems();
                Point2D nextPoint, lastPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));
                // TODO: render only visible points
                for (int i = 1 * step; i < data.re.length; i += step) {
                    nextPoint = dataToScreen.transform(new Point2D(data.re[i - step], data.im[i - step]));
                    gc.strokeLine(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY());
                    lastPoint = nextPoint;
                }
            }
        }
        positionChartCanvas();
    }
    
    /** Adapts tick units and labels. Adapts level of detail slider */
     ChangeListener<Number> axisBoundsChanged = new ChangeListener<Number>() {
         @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
             // max reduction to 50px per data point
             double totalNumPoints = sharedData.dataModel.getNumDataPointsInRange(xAxis.getLowerBound(), xAxis.getUpperBound());
             double pointsPerPix = totalNumPoints/chartCanvas.getWidth();
             detailSlider.setMin(Math.max(1, 0.5*pointsPerPix)); // highest resolution is 2 points per pix
             detailSlider.setMax(chartCanvas.getWidth()/20 * pointsPerPix); // lowest resolution is 40 points over the full width
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
