package Visualization;

import Gui.BidirectionalBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
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

    /** The chart pane positions the axes side to side with the canvas that contains the actual plot. */
    AnchorPane chartPane;

    public NumberAxis xAxis,
                      yAxis;

    /** the margins around the core plot within the containing pane (is used for axes, legends, title, etc.) */
    protected double[] margins = new double[]{10, 10, 50, 90};
    int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;

    /**
     * This field summarizes the current x- and y-axis bounds. The x-axis lower bound and range is stored in minX and width, and
     * the y-axis lower bound and range is stored in minY and height.
     */
    protected final ObjectProperty<Bounds> clipRegionDC = new SimpleObjectProperty<>();
    public Bounds getClipRegionDC() { return clipRegionDC.get(); }
    public void setClipRegionDC(Bounds newBounds) { clipRegionDC.set(newBounds); }
    public ObjectProperty<Bounds> clipRegionDCProperty() { return clipRegionDC; }

    /** Defines a fixed aspect ratio between the two axes. If the value is NaN, the aspect ratio is not fixed.
     * Otherwise, a block of width and height of 1 data point will be dataPointsPerPixelRatio higher than wide.
     * Example: dataPointsPerPixelRatio = 2. One unit on the x axis takes e.g. 10 px. Then the y axis is kept at a scale that ensures that one unit takes 20px. */
    protected final DoubleProperty dataPointsPerPixelRatio = new SimpleDoubleProperty(Double.NaN);
    public double getDataPointsPerPixelRatio() { return dataPointsPerPixelRatio.get(); }
    public void setDataPointsPerPixelRatio(double ratio) { dataPointsPerPixelRatio.set(ratio); }
    public DoubleProperty dataPointsPerPixelRatioProperty() { return dataPointsPerPixelRatio; }

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
    protected BoundingBox panStartAxisBoundsDC;

    /** Mouse button used to pan the view. */
    protected MouseButton PAN_MOUSE_BUTTON = MouseButton.PRIMARY;
    /** Mouse button used to create a rectangle selection (e.g. for zooming). */
    protected MouseButton AREA_SELECTION_MOUSE_BUTTON = MouseButton.SECONDARY;

    /** Highlights the user selection */
    protected final Rectangle selectionRect = new javafx.scene.shape.Rectangle(10, 10, Color.gray(0, 0.33));

    CanvasChart(){

        chartCanvas = new Canvas();

        buildComponents();

        BidirectionalBinding.<Bounds,Bounds>bindBidirectional(clipRegionDCProperty(), xAxis.axisBoundsDCProperty(), this::updateAxesRanges, this::updateClipRegionBounds);
        BidirectionalBinding.<Bounds,Bounds>bindBidirectional(clipRegionDCProperty(), yAxis.axisBoundsDCProperty(), this::updateAxesRanges, this::updateClipRegionBounds);

        dataPointsPerPixelRatio.addListener((observable, oldValue, newDataPointsPerPixelRatio) -> {
            adaptYAxis(xAxis.getAxisBoundsDC(), newDataPointsPerPixelRatio.doubleValue());
            xAxis.drawContents();
            yAxis.drawContents();
            drawContents();
        });
    }

    protected void updateAxesRanges(ObservableValue<? extends Bounds> observable, Bounds oldBounds, Bounds newBounds){
        xAxis.setAxisBoundsDC(new BoundingBox(newBounds.getMinX(), 0, newBounds.getWidth(), 0));
        yAxis.setAxisBoundsDC(new BoundingBox(0, newBounds.getMinY(), 0, newBounds.getHeight()));
    }
    protected void updateClipRegionBounds(ObservableValue<? extends Bounds> observable, Bounds oldBounds, Bounds newBounds){
        setClipRegionDC(new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange()));
    }

    /** Core rendering routine. Draws the chart data. */
    protected abstract void drawContents();
    /** Resets the view to some initial state. Doesn't have to perform redraw by convention. */
    public abstract void resetView();

    /** Sets up the GUI components */
    private void buildComponents(){

        xAxis = new NumberAxis(Orientation.HORIZONTAL);
        xAxis.setIsHorizontal(true);

        yAxis = new NumberAxis(Orientation.VERTICAL);
        yAxis.setIsHorizontal(false);

        this.setMinHeight(100);
        this.setStyle("-fx-background-color: white;");

        chartPane = new AnchorPane(chartCanvas, xAxis, yAxis, selectionRect);
        getChildren().add(chartPane);

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

//        xAxis.axisBoundsDCProperty().addListener((observable, oldValue, newValue) -> drawContents());
//        yAxis.axisBoundsDCProperty().addListener((observable, oldValue, newValue) -> drawContents());
        clipRegionDCProperty().addListener((observable, oldValue, newValue) -> drawContents() );

        // init selection rectangle
        selectionRect.setMouseTransparent(true);
        selectionRect.setVisible(false);
    }

    /**
     * Computes a transformation from data coordinates to screen coordinates, depending on the current axis bounds.
     * @return An affine transformation that transforms points in data space (e.g. year/temperature) into coordinates on the canvas.
     */
    Affine dataToScreen() {
        assert Double.isFinite(xAxis.getRange()) && Double.isFinite(yAxis.getRange()) : String.format("xAxis.getBounds: %s yAxis.getBounds: %s", xAxis.getAxisBoundsDC(), yAxis.getAxisBoundsDC());

        Transform translate = new Translate(-xAxis.getLowerBound(), -yAxis.getLowerBound());
        double sx = chartCanvas.getWidth() / xAxis.getRange();
        double sy = chartCanvas.getHeight() / yAxis.getRange();

//        if( Double.isInfinite(sx) ) sx = 0;
//        if( Double.isInfinite(sy) ) sy = 0;
//        assert Double.isFinite(sx) && Double.isFinite(sy) : String.format("Scale x %s Scale y %s",sx, sy);

        Transform scale = new Scale(sx, -sy);
        Transform mirror = new Translate(0, chartCanvas.getHeight());
        return new Affine(mirror.createConcatenation(scale).createConcatenation(translate));
    }

    // mouse listeners ---------------------------------------------------------

    /** Enables lightweight tooltips that show the current mouse position on the axes. The tooltip is given as an additional, bold font tick mark on the axis.
     * @param xAxisHinting whether to show axis hints on the x axis.
     * @param yAxisHinting whether to show axis hints on the y axis.
     */
    public void setAxisHinting(boolean xAxisHinting, boolean yAxisHinting){
        chartPane.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override public void handle(MouseEvent event) {
                if(xAxisHinting) xAxis.showCurrentMousePosition(event);
                if(yAxisHinting) yAxis.showCurrentMousePosition(event);
            }
        });
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

        double width = t.getX() - dragStartMousePositionSC.getX();
        double height= t.getY() - dragStartMousePositionSC.getY();
        if(aspectRatioFixed()){
            width = xAxis.getWidth()/yAxis.getHeight() * height;
        }

        if(width > 0 && height > 0 && t.getX() < chartCanvas.getWidth() && t.getY() < chartCanvas.getHeight()){
            selectionRect.setVisible(true);
            selectionRect.setWidth(width);
            selectionRect.setHeight(height);
        } else {
            selectionRect.setVisible(false);
        }

    }

    protected void finalizeRectangleSelection(MouseEvent t){
        selectionRect.setVisible(false);

        double minXSC = selectionRect.getX();
        double minYSC = selectionRect.getY();
        double maxXSC = minXSC + selectionRect.getWidth();
        double maxYSC = minYSC + selectionRect.getHeight();

        boolean cancel = t.getX() < minXSC || t.getY() < minYSC;

        if( ! cancel){
            double minXDC = xAxis.fromScreen(minXSC);
            double minYDC = yAxis.fromScreen(maxYSC);
            double maxXDC = xAxis.fromScreen(maxXSC);
            double maxYDC = yAxis.fromScreen(minYSC);
            setClipRegionDC(new BoundingBox(minXDC, minYDC, maxXDC - minXDC, maxYDC - minYDC));
        } else {
            resetView();
        }

        drawContents();

    }

    // pan --------------------------------------------------------------------

    protected void recordDragStartPosition(MouseEvent t) {
        dragStartMousePositionSC = new Point2D(t.getX(), t.getY());
        panStartAxisBoundsDC = new BoundingBox(xAxis.getLowerBound(), yAxis.getLowerBound(), xAxis.getRange(), yAxis.getRange());
    }

    Translate translate = new Translate(0,0);
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

            // translate.setX(offsetX); translate.setY(offsetY);
            // Bounds boundsTranslated = translate.transform(panStartAxisBoundsDC);
            Bounds boundsTranslated = new Translate(offsetX,offsetY).transform(panStartAxisBoundsDC);
            assert Double.isFinite(boundsTranslated.getWidth()) && Double.isFinite(boundsTranslated.getHeight()) : "Invalid bounds for clip region: " + boundsTranslated;
            setClipRegionDC(boundsTranslated);

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

            if( ! aspectRatioFixed() && (t.getSource() == xAxis || t.isAltDown())){
                zoomFactorY = 1;
            } else if( ! aspectRatioFixed() && (t.getSource() == yAxis || t.isShiftDown())){
                zoomFactorX = 1;
            }

            assert ! aspectRatioFixed() || Math.abs(zoomFactorX - zoomFactorY) < 1e-15 : "Aspect ratio violated: " + (zoomFactorX/zoomFactorY);

            Scale zoomScale = new Scale(zoomFactorX, zoomFactorY, xAxis.fromScreen(t.getX()), yAxis.fromScreen(t.getY()) );
            Bounds boundsZoomed = zoomScale.transform(boundsData);
            setClipRegionDC(boundsZoomed);

            drawContents();
        }
    }

    // element positioning  ----------------------------------------------------

    // resizes the canvas elements and positions them
    public void resizeComponents(ObservableValue<? extends Bounds> ov, Bounds t, Bounds t1) {

        // setting to the width of the root pane layout bounds avoids getting out of sync with the actual canvas pane layout bounds
        AnchorPane.setTopAnchor(chartPane, margins[TOP]);
        AnchorPane.setRightAnchor(chartPane, margins[RIGHT]);
        AnchorPane.setBottomAnchor(chartPane, margins[BOTTOM]);
        AnchorPane.setLeftAnchor(chartPane, margins[LEFT]);

        // is contained in the canvas pane
        chartCanvas.setWidth(getWidth() - margins[LEFT] - margins[RIGHT]);
        chartCanvas.setHeight(getHeight() - margins[TOP] - margins[BOTTOM]);

        xAxis.setWidth(chartCanvas.getWidth()-1);
        xAxis.setHeight(margins[BOTTOM]);
        xAxis.setTranslateY(chartCanvas.getHeight());
        xAxis.setTranslateX(-1);    // to fill the pixel gap at (0,0) where the axes should meet

        yAxis.setHeight(chartCanvas.getHeight());
        yAxis.setWidth(margins[LEFT]);
        yAxis.setTranslateX(-margins[LEFT]);

        if(aspectRatioFixed()) adaptYAxis(xAxis.getAxisBoundsDC(), getDataPointsPerPixelRatio());

        xAxis.drawContents();
        yAxis.drawContents();

        drawContents();

    }

    // fixed aspect ratio logic -------------------------------------------------

    /** @return Whether the scales of the x and y axes are fixed to a certain ratio. */
    public boolean aspectRatioFixed() {
        return ! Double.isNaN(getDataPointsPerPixelRatio());
    }

    /**
     * Adapts the range of the y axis such that the {@link #dataPointsPerPixelRatio} is satisfied.
     * @param xAxisBounds the desired bounds of the x axis
     */
    public void adaptYAxis(Bounds xAxisBounds, double dataPointsPerPixelRatio) {

        if(Double.isNaN(dataPointsPerPixelRatio)) return;
        // using full width, reducing height to maintain quadratic-shape cells
        // formula derivation
        //      pixels per data point Y / pixels per data point X = ratio
        //   => (height/rangeY) / (width/rangeX) = ratio
        //      solve for rangeY
        double newYRange = xAxisBounds.getWidth() * yAxis.getHeight() / xAxis.getWidth() / dataPointsPerPixelRatio;
        double diff = newYRange - yAxis.getRange();
        // extend bounds in both directions of the y axis equally.
        setClipRegionDC(new BoundingBox(xAxisBounds.getMinX(), yAxis.getLowerBound() - diff / 2, xAxisBounds.getWidth(), newYRange));
    }

    /**
     * Adapts the range of the x axis such that the {@link #dataPointsPerPixelRatio} is satisfied.
     * @param yAxisBounds the desired bounds for the y axis
     */
    public void adaptXAxis(Bounds yAxisBounds){
        // formula is derived as in adaptYAxis
        // using full height, reducing width to maintain quadratic-shape cells
        double newXRange = yAxisBounds.getHeight() * xAxis.getWidth() * getDataPointsPerPixelRatio() / yAxis.getHeight();
        setClipRegionDC(new BoundingBox(xAxis.getLowerBound(), yAxisBounds.getMinY(), newXRange, yAxisBounds.getHeight()));
    }

    /**
     * @return an image of the current contents of the visualization window
     */
    public WritableImage getCurrentViewAsImage(){

        int width = (int) getWidth(),
            height = (int) getHeight();

        WritableImage wim = new WritableImage(width, height);

        snapshot(null, wim);
        return wim;
    }

}
