package Visualization;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

/**
 * Base class for 
 * @author Carl Witt
 */
public abstract class CanvasChart extends Region {

    /** This is used to draw the data. Much faster than adding all the data elements to the scene graph. */
    public Canvas chartCanvas;
    
    /** The LineChart is used to display the axes, title and maybe legend. */
    public LineChart chart;
    public NumberAxis xAxis, yAxis;
     /** These elements are created by the JavaFX implementation but reused in this context. */
    public Node chartPlotBackground;       // is a Region object with exactly the size and position the equals the desired draw region on the chart
    public Node xTickMarks, yTickMarks;
    
    protected StackPane stackPane; // is used to display the canvas as an overlay to the chart
    
    public CanvasChart(){
        
        xAxis = new NumberAxis(1900, 2000, 10);
        yAxis = new NumberAxis(-5, 5, 1);
        chart = new LineChart(xAxis, yAxis); // the kind of the chart doesn't matter, because the data drawn with custom logic
        
        chartCanvas = new Canvas();
        chartCanvas.setOnScroll(scroll);
        chartCanvas.setOnZoom(zoom);
                
        // get the nodes from the chart via their css classes
        xTickMarks = xAxis.lookup(".axis-tick-mark"); 
        yTickMarks = yAxis.lookup(".axis-tick-mark");
        
        // listeners to keep the canvas aligned to the axes
        xTickMarks.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        yTickMarks.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        chartPlotBackground = chart.lookup(".chart-plot-background");
        chartPlotBackground.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        xAxis.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        yAxis.layoutBoundsProperty().addListener(positionCanvasOnChartBackground);
        
        // styles the components
        buildComponents();
    }
    
    /** Use this to add the chart to the scene graph. 
     * @return Node the node containing the chart. */
    public Node getNode(){ return stackPane; }
    
    /** Sets up the GUI components */
    private void buildComponents(){

        stackPane = new StackPane(chart, chartCanvas);
        
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(273.198974609375);
        chart.setPrefWidth(770.0);
        chart.setStyle("-fx-background-color: white;");
        chart.setMaxWidth(Double.MAX_VALUE);
        chart.setMaxHeight(Double.MAX_VALUE);
        chart.setMinHeight(0);
        chart.setMinWidth(0);
        
        xAxis.setAnimated(false);
        xAxis.setAutoRanging(false);
        xAxis.setTickLabelFont(new Font(10));
        
        yAxis.setAnimated(false);
        yAxis.setAutoRanging(false);
        yAxis.setTickLabelFont(new Font(10));
        
        chart.setPadding(new Insets(5, 5, 5, 5));
        
    }
    
    /** Core rendering routine. Draws the chart data. */
    public abstract void drawContents();
 
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
    
    ChangeListener<Number> updateAxisUnits = new ChangeListener<Number>() {
         @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
             xAxis.setTickUnit((xAxis.getUpperBound() - xAxis.getLowerBound()) / 20);
             yAxis.setTickUnit((yAxis.getUpperBound() - yAxis.getLowerBound()) / 5);
         }
     };
    
    public EventHandler<ScrollEvent> scroll = new EventHandler<ScrollEvent>() {
        @Override public void handle(ScrollEvent e) {
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
    };

    public EventHandler<ZoomEvent> zoom = new EventHandler<ZoomEvent>() {
        @Override public void handle(ZoomEvent e) {
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
    };
    
    /** Aligns the canvas node (where the data is drawn) to its correct overlay position above the axes.
     TODO: chartrt background chartnges are not always fired and not always correctly */
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
