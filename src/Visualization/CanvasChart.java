package Visualization;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

/**
 * Base class for fast rendering charts using a canvas rather than the scene graph.
 * The idea is to use a standard chart to generate axes, legend and title and position a canvas over it on which the content is drawn.
 * TODO: much of the hassle aligning the canvas to the axes stems from the fact that axis labels grow to the right, thus pushing the axis to right when increasing precision. Custom axes rendering code should be written (using the scene graph to profit from simplified interaction, e.g. via picking)
 * @author Carl Witt
 */
public abstract class CanvasChart extends AnchorPane {
    
    /** This is used to draw the data. Much faster than adding all the data elements to the scene graph. */
    public Canvas chartCanvas;
    
    protected AnchorPane canvasPane;
    
    protected NumberAxis xAxis, yAxis;
    
    /** Toggles for basic interaction; */
    public boolean allowScroll = true, allowZoom = true;
    
    /** The position where the mouse was when the drag (pan gesture) started. Allows for live panning. */
    Point2D dragStartMousePositionSC;
    /** The axis lower and upper bounds when the pan gesture was started. Allows for live panning. */
    Rectangle2D dragStartAxisBoundsDC;
    
    public CanvasChart(){
        
        xAxis = new NumberAxis();
        yAxis = new NumberAxis();
        
        chartCanvas = new Canvas();
        
        buildComponents();
    }
    
    /** Sets up the GUI components */
    private void buildComponents(){
        
        xAxis = new NumberAxis();
        xAxis.setIsHorizontal(true);
        
        yAxis = new NumberAxis();
        yAxis.setIsHorizontal(false);
        
        this.setMinHeight(100);
        
        canvasPane = new AnchorPane(chartCanvas, xAxis, yAxis);
        this.setStyle("-fx-background-color: white;");
        getChildren().add(canvasPane);
        
        layoutBoundsProperty().addListener(resizeComponents);
        
        chartCanvas.setOnMousePressed(this.recordDragStartPosition);
        chartCanvas.setOnMouseDragged(this.panViaMouseDrag);
        chartCanvas.setOnScroll(this.zoomWithMouseWheel);
        
        // when zooming on the axis, zoom will be changed only along the axis
        xAxis.setOnScroll(this.zoomWithMouseWheel);
        yAxis.setOnScroll(this.zoomWithMouseWheel);
    }
    
    /** Core rendering routine. Draws the chart data. */
    public abstract void drawContents();
    
    /** @return An affine transformation that transforms points in data space (e.g. year/temperature) into coordinates on the canvas */
    protected Affine dataToScreen() {
        Transform translate = new Translate(-xAxis.getLowerBound(), -yAxis.getLowerBound());
        double sx = chartCanvas.getWidth() / (xAxis.getUpperBound() -xAxis.getLowerBound());
        double sy = chartCanvas.getHeight() / (yAxis.getUpperBound() -yAxis.getLowerBound());
        Transform scale = new Scale(sx, -sy);
        //        Transform mirror = new Scale(1, -1).createConcatenation(new Translate(0, chartCanvas.getHeight()));
        Transform mirror = new Translate(0, chartCanvas.getHeight());
        return new Affine(mirror.createConcatenation(scale).createConcatenation(translate));
    }
    
    // pan --------------------------------------------------------------------
    
    EventHandler<MouseEvent> recordDragStartPosition = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent t) {
            if(allowScroll && t.getButton() == MouseButton.PRIMARY){
                dragStartMousePositionSC = new Point2D(t.getX(), t.getY());
                dragStartAxisBoundsDC = new Rectangle2D(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());
            }
        }
    };
    
    
    EventHandler<MouseEvent> panViaMouseDrag = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent t) {
            
            if(allowScroll){
                chartCanvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
                chartCanvas.getGraphicsContext2D().fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
                
                double offsetX = xAxis.fromScreen(dragStartMousePositionSC.getX()) - xAxis.fromScreen(t.getX()),
                        offsetY = yAxis.fromScreen(dragStartMousePositionSC.getY()) - yAxis.fromScreen(t.getY());
                
                xAxis.setLowerBound(dragStartAxisBoundsDC.getMinX() + offsetX);
                xAxis.setUpperBound(dragStartAxisBoundsDC.getMaxX() + offsetX);
                
                yAxis.setLowerBound(dragStartAxisBoundsDC.getMinY() + offsetY);
                yAxis.setUpperBound(dragStartAxisBoundsDC.getMaxY() + offsetY);
                
                xAxis.drawContents();
                yAxis.drawContents();
                
                drawContents();
            }
            
        }
    };
    
    // zoom --------------------------------------------------------------------
    
    EventHandler<ScrollEvent> zoomWithMouseWheel = new EventHandler<ScrollEvent>() {
        
        @Override
        public void handle(ScrollEvent t) {
            
            if(allowZoom){
                chartCanvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
                chartCanvas.getGraphicsContext2D().fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
                
                Bounds boundsData = new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());
                
                double scrollAmount = t.getDeltaY() / 1000;
                double zoomFactorX = 1 - scrollAmount;
                double zoomFactorY = 1 - scrollAmount;
                
                if(t.getSource() == xAxis){
                    zoomFactorY = 1;
                } else if(t.getSource() == yAxis){
                    zoomFactorX = 1;
                }
                
                Scale zoomScale = new Scale(zoomFactorX, zoomFactorY, xAxis.fromScreen(t.getX()), yAxis.fromScreen(t.getY()) );
                Bounds boundsZoomed = zoomScale.transform(boundsData);
                xAxis.setLowerBound(boundsZoomed.getMinX());
                xAxis.setUpperBound(boundsZoomed.getMaxX());
                yAxis.setLowerBound(boundsZoomed.getMinY());
                yAxis.setUpperBound(boundsZoomed.getMaxY());
                xAxis.drawContents();
                yAxis.drawContents();
                
                drawContents();
            }
        }
    };
    
    // element positioning  ----------------------------------------------------
    
    ChangeListener<Bounds> resizeComponents = new ChangeListener<Bounds>() {
        
        @Override
        public void changed(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {
            
            // the chart margins within the containing pane (can be used for legends, titles, labels, etc.
            double[] margins = new double[]{10, 10, 40, 60};
            int TOP = 0,
                RIGHT = 1,
                BOTTOM = 2,
                LEFT = 3;
            
            // setting to the width of the root pane layout bounds avoids getting out of sync with the actual canvas pane layout bounds
            AnchorPane.setTopAnchor(canvasPane, margins[TOP]);
            AnchorPane.setRightAnchor(canvasPane, margins[RIGHT]);
            AnchorPane.setBottomAnchor(canvasPane, margins[BOTTOM]);
            AnchorPane.setLeftAnchor(canvasPane, margins[LEFT]);
            
//            chartCanvas.setLayoutX(margins[LEFT]);
//            chartCanvas.setLayoutY(margins[TOP]);
            
            // is contained in the canvas pane
            chartCanvas.setWidth(t1.getWidth()-margins[LEFT]-margins[RIGHT]-1);
            chartCanvas.setHeight(t1.getHeight()-margins[TOP]-margins[BOTTOM]-1); // not to occlude the border of the containing pane (the axis line)
            chartCanvas.setTranslateY(1);
            
            xAxis.setWidth(t1.getWidth()-margins[LEFT]-margins[RIGHT]);
            xAxis.setHeight(margins[BOTTOM]);
            xAxis.setTranslateY(t1.getHeight()-margins[TOP]-margins[BOTTOM]);
            xAxis.setTranslateX(-1);
            
            yAxis.setHeight(t1.getHeight()-margins[TOP]-margins[BOTTOM]);
            yAxis.setWidth(margins[LEFT]);
            yAxis.setTranslateX(-margins[LEFT]);
            
//                .setLayoutX(20);    // doesn't have any effect
            
            xAxis.drawContents();
            yAxis.drawContents();
            
            drawContents();
            
        }
    };
    
}
