package Visualization;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

/**
 * Base class for fast rendering charts using a canvas rather than the scene graph.
 * Is used by the time series and correlogram visualization.
 * @author Carl Witt
 */
abstract class CanvasChart extends AnchorPane {
    
    /** This is used to draw the data. Much faster than adding all the data elements to the scene graph. */
    public final Canvas chartCanvas;
    
    AnchorPane canvasPane;

    public NumberAxis xAxis,
                      yAxis;

    /** This field summarizes the current x- and y-axis bounds. The x-axis lower bound and range is stored in minX and width, and 
     * the y-axis lower bound and range is stored in minY and height. Listening to changes in this property is simpler and faster than listening
     * for all four (x and y, lower and upper) properties.
     * 
     * ! This field is currently updated manually! I.e. when changing the axis bounds, it is the developers duty to update this field as well.
     * On the other hand, the axis bounds listen to the axisRanges object and adapt their bounds automatically when the object has changed. (see constructor)
     */
    final ObjectProperty<Rectangle2D> axesRanges = new SimpleObjectProperty<>();

    /** whether the user can pan the viewe using {@link #PAN_MOUSE_BUTTON} */
    boolean allowPan = true;
    /** whether the user can zoom using the scroll wheel (or touchpad zoom). */
    boolean allowZoom = true;
    /** whether the user can make rectangular selections using {@link #AREA_SELECTION_MOUSE_BUTTON}. */
    boolean allowSelection = true;

    /** The position (screen coordinates) where the mouse was when the drag (pan gesture) started. Allows for live panning.
     *  The convention is that this field is always null, except if a drag process is going on. */
    protected Point2D dragStartMousePositionSC;
    /** The axis lower and upper bounds (data coordinates) when the pan gesture was started. Allows for live panning. */
    protected Rectangle2D panStartAxisBoundsDC;

    /** Mouse button used to pan the view. */
    protected MouseButton PAN_MOUSE_BUTTON = MouseButton.PRIMARY;
    /** Mouse button used to create a rectangle selection (e.g. for zooming). */
    protected MouseButton AREA_SELECTION_MOUSE_BUTTON = MouseButton.SECONDARY;

    /** Highlights the user selection */
    protected final Rectangle selectionRect = new javafx.scene.shape.Rectangle(10, 10, Color.gray(0, 0.33));
    protected String normalSelectionHint = "";  //Zoom
    protected String reverseSelectionHint = "";     //Reset View
    protected Text selectionHint = new Text(normalSelectionHint);

    CanvasChart(){
        
        xAxis = new NumberAxis();
        yAxis = new NumberAxis();
        
        chartCanvas = new Canvas();
        
        buildComponents();
        
        axesRangesProperty().addListener((ov, t, t1) -> {
//                    if(t1 == null) return;
                    // TODO check why t1 is null sometimes
            assert t1 != null : "something went terribly wrong.";
            Rectangle2D newRanges = (Rectangle2D) t1;

            xAxis.setLowerBound(newRanges.getMinX());
            xAxis.setUpperBound(newRanges.getMaxX());
            yAxis.setLowerBound(newRanges.getMinY());
            yAxis.setUpperBound(newRanges.getMaxY());
        }
        );
    }

    /** Core rendering routine. Draws the chart data. */
    protected abstract void drawContents();
    /** Resets the view to some initial state. Doesn't have to perform redraw by convention. */
    public abstract void resetView();

    /** Sets up the GUI components */
    private void buildComponents(){
        
        xAxis = new NumberAxis();
        xAxis.setIsHorizontal(true);
        
        yAxis = new NumberAxis();
        yAxis.setIsHorizontal(false);
        
        this.setMinHeight(100);
        this.setStyle("-fx-background-color: white;");

//        canvasPane = new BorderPane(chartCanvas, null, null, xAxis, yAxis);
        canvasPane = new AnchorPane(chartCanvas, xAxis, yAxis, selectionRect, selectionHint);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(chartCanvas.widthProperty());
        clip.heightProperty().bind(chartCanvas.heightProperty());
        selectionRect.setClip(clip);
        getChildren().add(canvasPane);

        layoutBoundsProperty().addListener(this::resizeComponents);

        chartCanvas.setOnScroll(this::zoomWithMouseWheel);
        chartCanvas.setOnMouseDragged(this::mouseDragged);
        chartCanvas.setOnMouseReleased(this::mouseReleased);

        // when zooming on the axis, zoom will be changed only along the axis
        xAxis.setOnScroll(this::zoomWithMouseWheel);
        yAxis.setOnScroll(this::zoomWithMouseWheel);
        xAxis.setOnMouseDragged(this::mouseDragged);
        yAxis.setOnMouseDragged(this::mouseDragged);
        xAxis.setOnMouseReleased(this::mouseReleased);
        yAxis.setOnMouseReleased(this::mouseReleased);

        // init selection rectangle and hint
        selectionRect.setMouseTransparent(true);
        selectionRect.setVisible(false);
        // position selection hint under the mouse cursor
        chartCanvas.setOnMouseMoved(event -> {
            selectionHint.setX(event.getX());
            selectionHint.setY(event.getY());
        });
        selectionHint.setVisible(false);
    }

    /** @return An affine transformation that transforms points in data space (e.g. year/temperature) into coordinates on the canvas */
    Affine dataToScreen() {
        Transform translate = new Translate(-xAxis.getLowerBound(), -yAxis.getLowerBound());
        double sx = chartCanvas.getWidth() / (xAxis.getUpperBound() -xAxis.getLowerBound());
        double sy = chartCanvas.getHeight() / (yAxis.getUpperBound() -yAxis.getLowerBound());
        Transform scale = new Scale(sx, -sy);
        //        Transform mirror = new Scale(1, -1).createConcatenation(new Translate(0, chartCanvas.getHeight()));
        Transform mirror = new Translate(0, chartCanvas.getHeight());
        return new Affine(mirror.createConcatenation(scale).createConcatenation(translate));
    }

    protected void mouseDragged(MouseEvent t){

        if(dragStartMousePositionSC == null) recordDragStartPosition(t);

        if(t.getButton() == PAN_MOUSE_BUTTON && allowPan)
            panViaMouseDrag(t);
        else if(t.getButton() == AREA_SELECTION_MOUSE_BUTTON && allowSelection)
            if(t.getSource() == chartCanvas)    // TODO: when adding rectangle selection behavior for axes, remove this
                modifyRectangleSelection(t);

    }

    protected void mouseReleased(MouseEvent t) {

        // a release is also triggered by simple clicks
        boolean mouseDragging = dragStartMousePositionSC != null;
        if(t.getButton() == AREA_SELECTION_MOUSE_BUTTON && allowSelection && mouseDragging)
            finalizeRectangleSelection(t);
        // if the release was triggered by some other mouse button, no action is necessary

        dragStartMousePositionSC = null; // to avoid that a single click triggers a drag release event

    }

    // rectangle selection -----------------------------------------------------

    protected void modifyRectangleSelection(MouseEvent t){

        // this would be necessary only once, but avoids introducing a startRectangleSelection method.
        selectionRect.setX(dragStartMousePositionSC.getX());
        selectionRect.setY(dragStartMousePositionSC.getY());
        selectionHint.setVisible(true);

        double width = t.getX() - dragStartMousePositionSC.getX();
        double height= t.getY() - dragStartMousePositionSC.getY();

        // restricted selection if on one of the axes
//        if(t.getSource() == xAxis){
//            height = chartCanvas.getHeight();
//            selectionRect.setY(0);
//        } else if(t.getSource() == yAxis){
//            width = chartCanvas.getWidth();
//            selectionRect.setX(0);
//        }

        if(width > 0 && height > 0 && t.getX() < chartCanvas.getWidth() && t.getY() < chartCanvas.getHeight()){
            selectionRect.setVisible(true);
            selectionRect.setWidth(width);
            selectionRect.setHeight(height);
            selectionHint.setText(normalSelectionHint);
        } else {
            selectionRect.setVisible(false);
            selectionHint.setText(reverseSelectionHint);
        }

    }

    protected void finalizeRectangleSelection(MouseEvent t){
        selectionHint.setVisible(false);
        selectionRect.setVisible(false);

        double minXSC = dragStartMousePositionSC.getX();
        double minYSC = dragStartMousePositionSC.getY();
        double maxXSC = t.getX();
        double maxYSC = t.getY();

        double widthSC = maxXSC - minXSC;
        double heightSC= maxYSC - minYSC;

        // restricted selection if on one of the axes
//        if(t.getSource() == xAxis){
//            minYSC = chartCanvas.getHeight();
//            maxYSC = 0;
//        } else if(t.getSource() == yAxis){
//            minXSC = 0;
//            maxXSC = chartCanvas.getWidth();
//        }

        if(widthSC > 0 && heightSC > 0){
            double minXDC = xAxis.fromScreen(minXSC);
            double minYDC = yAxis.fromScreen(maxYSC);
            double maxXDC = xAxis.fromScreen(maxXSC);
            double maxYDC = yAxis.fromScreen(minYSC);
            axesRanges.set(new Rectangle2D(minXDC, minYDC, maxXDC - minXDC, maxYDC - minYDC));
            drawContents();
        } else {
            resetView();
            drawContents();
        }

    }

    // pan --------------------------------------------------------------------

    protected void recordDragStartPosition(MouseEvent t) {
        dragStartMousePositionSC = new Point2D(t.getX(), t.getY());
        panStartAxisBoundsDC = new Rectangle2D(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());
    }

    protected void panViaMouseDrag(MouseEvent t) {

        if(allowPan){

            chartCanvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
            chartCanvas.getGraphicsContext2D().fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());

            double offsetX = xAxis.fromScreen(dragStartMousePositionSC.getX()) - xAxis.fromScreen(t.getX()),
                   offsetY = yAxis.fromScreen(dragStartMousePositionSC.getY()) - yAxis.fromScreen(t.getY());

            if(t.getSource() == xAxis || t.isAltDown()){
                offsetY = 0;
            } else if(t.getSource() == yAxis || t.isShiftDown()){
                offsetX = 0;
            }

            xAxis.setLowerBound(panStartAxisBoundsDC.getMinX() + offsetX);
            xAxis.setUpperBound(panStartAxisBoundsDC.getMaxX() + offsetX);
            yAxis.setLowerBound(panStartAxisBoundsDC.getMinY() + offsetY);
            yAxis.setUpperBound(panStartAxisBoundsDC.getMaxY() + offsetY);
            xAxis.drawContents();
            yAxis.drawContents();
            axesRanges.set(new Rectangle2D(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange()));

            drawContents();
        }

    }

    // zoom --------------------------------------------------------------------

    protected void zoomWithMouseWheel(ScrollEvent t) {

        if(allowZoom){
            chartCanvas.getGraphicsContext2D().setFill(new Color(1.,1.,1.,1.));
            chartCanvas.getGraphicsContext2D().fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());

            Bounds boundsData = new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());

            double scrollAmount = t.getDeltaY() / 1000;
            double zoomFactorX = 1 - scrollAmount;
            double zoomFactorY = 1 - scrollAmount;

            if(t.getSource() == xAxis || t.isAltDown()){
                zoomFactorY = 1;
            } else if(t.getSource() == yAxis || t.isShiftDown()){
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
            axesRanges.set(new Rectangle2D(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange()));

            drawContents();
        }
    }

    // element positioning  ----------------------------------------------------


//    @Override public void resize(double width, double height){
//        super.resize(width, height);
//        resizeComponents(null, null, null);
//    }

    // resizes the canvas elements and positions them
    public void resizeComponents(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {

//        if(getClass() == Correlogram.class)
//            System.out.println(String.format("t  %s\nt1 %s\n", t, t1));

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
        chartCanvas.setWidth(getWidth()-margins[LEFT]-margins[RIGHT]-1);
        chartCanvas.setHeight(getHeight()-margins[TOP]-margins[BOTTOM]-1); // not to occlude the border of the containing pane (the axis line)
        chartCanvas.setTranslateY(1);

        xAxis.setWidth(getWidth()-margins[LEFT]-margins[RIGHT]);
        xAxis.setHeight(margins[BOTTOM]);
        xAxis.setTranslateY(getHeight()-margins[TOP]-margins[BOTTOM]);
        xAxis.setTranslateX(-1);

        yAxis.setHeight(getHeight()-margins[TOP]-margins[BOTTOM]);
        yAxis.setWidth(margins[LEFT]);
        yAxis.setTranslateX(-margins[LEFT]);

//                .setLayoutX(20);    // doesn't have any effect

        xAxis.drawContents();
        yAxis.drawContents();

        drawContents();

    }

    /**
     * @return an image of the current contents of the visualization window
     */
    public WritableImage getCurrentViewAsImage(){

        int width = (int) chartCanvas.getWidth(),
            height = (int) chartCanvas.getHeight();

        WritableImage wim = new WritableImage(width, height);

        chartCanvas.snapshot(null, wim);

        return wim;
    }

    public Rectangle2D getAxesRanges() { return axesRanges.get(); }
    public void setAxesRanges(Rectangle2D value) { axesRanges.set(value); }
    public ObjectProperty axesRangesProperty() { return axesRanges; }
    
}
