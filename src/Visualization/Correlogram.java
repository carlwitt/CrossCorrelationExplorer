package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Data.Windowing.WindowMetadata;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.transform.*;

import java.awt.*;

import static Data.Correlation.CorrelationMatrix.*;

/**
 * Used to draw the correlogram. Takes an aggregated correlation matrix and renders its cells as a colored blocks.
 * There are three coordinate systems in use.
 * Cell coordinates refer to the indices a certain correlation matrix entry has - see {@link #cellToData(Data.Windowing.WindowMetadata)}.
 * Data coordinates refer to time (horizontal) and time lag (vertical). The logic to handle these is inherited from {@link Visualization.CanvasChart}.
 * Screen coordinates refer to the standard pixel coordinates on the screen.
 *
 * @author Carl Witt
 */
public class Correlogram extends CanvasChart {

    // -----------------------------------------------------------------------------------------------------------------
    // CONSTANTS
    // -----------------------------------------------------------------------------------------------------------------

    /** Defines how the data in the correlation matrix is visualized. */
    public static enum RENDER_MODE { // color each cell by
        MEAN_STD_DEV,           // mean and standard deviation
        MEDIAN_IQR,             // median and interquartile range
        NEGATIVE_SIGNIFICANT,   // percentage of significantly negative correlated window pairs
        POSITIVE_SIGNIFICANT,   // percentage of significantly positive correlated window pairs
        ABSOLUTE_SIGNIFICANT,    // percentage of significantly correlated window pairs
    }
    /** How to encode the second number (usually uncertainty) associated with each correlogram cell. */
    public static enum UNCERTAINTY_VISUALIZATION {
        COLUMN_WIDTH,   // manipulate column width
        COLOR           // manipulate base color (e.g. changing saturation)
    }

    /** default uncertainty visualization. */
    public final static UNCERTAINTY_VISUALIZATION DEFAULT_UNCERTAINTY_VISUALIZATION = UNCERTAINTY_VISUALIZATION.COLOR;

    /** default render mode. */
    private final static RENDER_MODE defaultRenderMode = RENDER_MODE.MEAN_STD_DEV;

    // -----------------------------------------------------------------------------------------------------------------
    // ATTRIBUTES
    // -----------------------------------------------------------------------------------------------------------------

    private SharedData sharedData;

    /** which method to use to visualize uncertainty. */
    private UNCERTAINTY_VISUALIZATION uncertaintyVisualization = DEFAULT_UNCERTAINTY_VISUALIZATION;
    /** which data dimensions to visualize. */
    RENDER_MODE renderMode = defaultRenderMode;

    /** encodes 2D values in a single color */
    private final MultiDimensionalPaintScale paintScale;
    /** The matrix for which the paint scale was computed. */
    private CorrelationMatrix paintScaleMatrix;
    /** The render mode for which the paint scale was computed. */
    private RENDER_MODE paintScaleRenderMode;

    /** width and height (in data coordinates) of a cell in the correlogram. */
    private double blockWidth, blockHeight;
    /** offset between two subsequent blocks. if there are no gaps, blockOffset equals blockWidth. */
    private double blockOffset;

    Affine cellToScreen;
    final Affine identity = new Affine(new Translate());

    private final Rectangle activeWindowRect = new javafx.scene.shape.Rectangle(10, 10);
    private final double activeWindowStrokeWidth = 2;

    Color backgroundColor = new Color(0.78, 0.78, 0.78, 1);
    Color filtered = Color.gray(0.176);

    // -----------------------------------------------------------------------------------------------------------------
    // METHODS
    // -----------------------------------------------------------------------------------------------------------------

    public Correlogram(MultiDimensionalPaintScale paintScale){
        this.paintScale=paintScale;
        xAxis.setMinTickUnit(1);
        yAxis.setMinTickUnit(1);

        chartCanvas.setOnMouseMoved(this::reportHighlightedCell);

        // initialize the active window highlight rectangle
        activeWindowRect.setFill(Color.rgb(0, 0, 0, 0));    // transparent fill
        activeWindowRect.setStroke(Color.web("#fff500")); // yellow border
        activeWindowRect.setStrokeType(StrokeType.OUTSIDE);
        activeWindowRect.setVisible(false);
        activeWindowRect.setStrokeWidth(activeWindowStrokeWidth);
        activeWindowRect.setMouseTransparent(true);
        canvasPane.getChildren().add(activeWindowRect);
    }

    public void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;

        // listen to changes in the correlation result matrix
        sharedData.correlationMatrixProperty().addListener((ov, t, m) -> {
            cellToScreen = cellToScreen(m.metadata);
            yAxis.setMinTickUnit(m.metadata.tauStep);
//            yAxis.setTickOrigin(m.metadata.tauMin);   // good for small lag ranges (exact tick labels), bad for large lag ranges (odd tick labels)
            resetView();
            drawContents();
        });

        // listen to changes in the matrix filter ranges
        sharedData.matrixFilterRangesProperty().addListener((ov, t, m) -> drawContents());

        sharedData.uncertaintyVisualizationProperty().addListener((observable, oldValue, newValue) -> {
            if(this.uncertaintyVisualization != newValue){
                this.uncertaintyVisualization = (UNCERTAINTY_VISUALIZATION) newValue;
                drawContents();
            }
        });

    }

    /**
     * The coordinate system applied to the matrix cells is (zero based column index, zero based row index).
     * This transformation computes the data coordinates of a single cell. So for instance, the first cell in a column (with index zero, because it is the first in the array)
     * is mapped to the minimum time lag, the last cell in a column is mapped to the maximum time lag.
     * The horizontal mapping centers each column in the time window it represents. Usually, columns overlap,
     * so their width should be reduced (although overplotting would solve the problem, if drawn in correct order).
     * If they don't overlap, there are gaps between the columns of the matrix.
     * @param metadata contains the information where to plot the blocks (minimum x value in time series, window size, etc.)
     * @return a transformation that converts matrix cell coordinates of the form (column idx, row idx) to data coordinates.
     */
    protected Affine cellToData(WindowMetadata metadata){

        computeBlockDimensions(metadata);
        // there can be gaps in horizontal direction (blockOffset > blockWidth), but not in vertical direction ("blockOffsetY" always equals blockHeight)
        Transform scale = new Scale(blockOffset, metadata.tauStep*blockHeight);

        // if the blocks do not overlap in horizontal direction, blockWidth equals windowSize and the translate is only metadata.getMinXValue()
        double tx = metadata.getMinXValue() + 0.5 * metadata.windowSize - 0.5 * blockWidth;
        double ty = metadata.tauStep + metadata.tauMin;
        return new Affine(new Translate(tx, ty).createConcatenation(scale));

    }

    protected Affine cellToScreen(WindowMetadata metadata){ return new Affine(dataToScreen().createConcatenation(cellToData(metadata))); }

    /*
     This handler listens to mouse moves on the correlogram and informs the shared data object about
     the correlation matrix cell index (window index index and lag index) under the mouse cursor.
    */
    private void reportHighlightedCell(MouseEvent t) {

        // get matrix
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if (matrix == null) return;

        // transform from mouse position into cell coordinates
        Point2D cellCoordinates;
        try { cellCoordinates = cellToScreen.inverseTransform(t.getX(), t.getY()); }
        catch (NonInvertibleTransformException e) { e.printStackTrace(); return; }

        int columnIdx = (int) Math.floor(cellCoordinates.getX());
        int lagIdx    = (int) Math.ceil(cellCoordinates.getY());

        Point activeCell = new Point(columnIdx, lagIdx);
        if(columnIdx < 0 || columnIdx >= matrix.getSize() || lagIdx < 0 || lagIdx > matrix.metadata.getNumberOfDifferentTimeLags())
            activeCell = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);

        // report only if changes have occured
        if (!sharedData.getHighlightedCell().equals(activeCell)) {
            sharedData.setHighlightedCell(activeCell);
            highlightActiveWindow();
        }
    }



    /**
     * Renders the correlogram.
     * The width of a window is windowSize - overlap (overlap = |w| - baseWindowOffset)
     * Columns are centered around the window average. E.g. [1956..1958] -> 1956 + (1958-1956+1)/2 = 1957.5
     * The offset between two subsequent windows is still baseWindowOffset.
     */
    @Override public void drawContents() {

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        gc.setTransform(identity);
        gc.setFill(backgroundColor);
        gc.fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());

        if (sharedData.getCorrelationMatrix()== null || sharedData.getCorrelationMatrix().metadata.setA.size() == 0) return;

        // retrieve data to render
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();

        // depending on the render mode, configure the paintscale (expensive for high-resolution color scales)
        if(matrix != paintScaleMatrix || !renderMode.equals(paintScaleRenderMode)){
            configurePaintscale(matrix, paintScale);
            paintScaleMatrix = matrix;
            paintScaleRenderMode = renderMode;
        }

        cellToScreen = cellToScreen(matrix.metadata);

        // ------------------------------------------------------------
        // clipping the contents to render
        // ------------------------------------------------------------
        // horizontally and vertically
        int maxColIdx = matrix.getSize() - 1;
        int maxLagIdx = matrix.metadata.getNumberOfDifferentTimeLags() - 1;
        Point minColMinLag = new Point(0, 0),
              maxColMaxLag = new Point(maxColIdx, maxLagIdx);

        Rectangle2D axesRanges = getAxesRanges();
        if(axesRanges != null){
            try {
                Affine cellToData = cellToData(matrix.metadata);
                Point2D minColMinLagDouble = cellToData.inverseTransform(axesRanges.getMinX(), axesRanges.getMinY());
                Point2D maxColMaxLagDouble = cellToData.inverseTransform(axesRanges.getMaxX(), axesRanges.getMaxY());
                minColMinLag = new Point(Math.max(0, (int) Math.floor(minColMinLagDouble.getX())), Math.max(0, (int) Math.ceil(minColMinLagDouble.getY())));
                maxColMaxLag = new Point(Math.min(maxColIdx, (int) Math.floor(maxColMaxLagDouble.getX())), Math.min(maxLagIdx, (int) Math.ceil(maxColMaxLagDouble.getY())));
            } catch (NonInvertibleTransformException e) { e.printStackTrace(); }
        }

        // clipping on the resolution
        Point2D blockDimensionsScreen = cellToScreen.transform(1,0);
        int windowStep = Math.max(1, (int) Math.floor(1. / blockDimensionsScreen.getX()));
        int lagStep    = Math.max(1, (int) Math.floor(1. / blockDimensionsScreen.getY()));
        blockHeight *= lagStep;
        blockWidth  *= windowStep;

        switch(renderMode){
            case MEAN_STD_DEV:
                drawContentsMultivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, MEAN, STD_DEV);
                break;
            case MEDIAN_IQR:
                drawContentsMultivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, MEDIAN, IQR);
                break;
            case NEGATIVE_SIGNIFICANT:
                drawContentsUnivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, NEGATIVE_SIGNIFICANT);
                break;
            case POSITIVE_SIGNIFICANT:
                drawContentsUnivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, POSITIVE_SIGNIFICANT);
                break;
            case ABSOLUTE_SIGNIFICANT:
                drawContentsUnivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, ABSOLUTE_SIGNIFICANT);
                break;
        }

        xAxis.drawContents();
        yAxis.drawContents();
        highlightActiveWindow();

    }

    void drawContentsUnivariate(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int DIM) {// for each column of the matrix (or, equivalently, for each time window)

        Point2D blockSizeSC = cellToScreen.deltaTransform(1,-1);
        double widthSC = blockSizeSC.getX()  +1, // +1: to avoid gaps between the blocks
               heightSC = blockSizeSC.getY() +1;

        double[] srcPts = new double[2], dstPts = new double[2];

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getResultItems().get(i);

            for (int lag = minColMinLag.y; lag <= maxColMaxLag.y; lag += lagStep) {

                // upper left corner, bottom right corner of the cell, the same for the last drawn cell
                srcPts[0] = i; srcPts[1] = lag;
                cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                // draw cell
                gc.setFill(paintScale.getPaint(column.data[DIM][lag]));
                gc.fillRect(dstPts[0], dstPts[1], widthSC, heightSC);

            }
        }
    }

    /**
     * Performance note: computing the width and height of a block in screen coordinates only once and adding up coordinates might introduce rounding errors (not tested).
     * By now, performance is satisfactory anyway.
     */
    void drawContentsMultivariate(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int DIM1, int DIM2) {

        double uncertainty;     // relative uncertainty (compared to the maximum uncertainty present in the matrix) in the current cell (only used with UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH)
        double slimDown = 0;    // results from the relative uncertainty. A high uncertainty will make the cell much slimmer, no uncertainty will leave it at its full width.
        double minUncertainty = 0;         // matrix.getMin(DIM2) would actually underestimate the uncertainty! (if the minimum uncertainty is high, a full width column would actually represent an uncertain value)
        double maxUncertainty = matrix.getMax(DIM2);   // this overestimates the uncertainty but makes the differences much better visible (since an sd of 1 almost never occurs)


        Point2D blockSizeSC = cellToScreen.deltaTransform(1,-1);
        double widthSC = blockSizeSC.getX()   +1, // +1: to avoid gaps between the blocks
                heightSC = blockSizeSC.getY() +1;

        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();

        double[] srcPts = new double[2], dstPts = new double[2];

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getResultItems().get(i);

            for (int lag = minColMinLag.y; lag <= maxColMaxLag.y; lag += lagStep) {

                if(uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH){
                    uncertainty = (column.data[DIM2][lag] - minUncertainty) / (maxUncertainty - minUncertainty);
                    slimDown = widthSC * uncertainty;
                    if(Double.isNaN(slimDown)) slimDown = 0;
                }

                srcPts[0] = i; srcPts[1] = lag;
                cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                // draw cell
                switch(uncertaintyVisualization){
                    case COLOR:         gc.setFill(paintScale.getPaint(column.data[DIM1][lag], column.data[DIM2][lag]));  break;
                    case COLUMN_WIDTH:  gc.setFill(paintScale.getPaint(column.data[DIM1][lag]));                          break;
                }

                for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
                    if(matrixFilterRanges[STAT] == null) continue;
                    if(column.data[STAT][lag] < matrixFilterRanges[STAT][0] ||
                       column.data[STAT][lag] > matrixFilterRanges[STAT][1]){
                        gc.setFill(filtered);
                        break;
                    }
                }
                gc.fillRect(dstPts[0], dstPts[1], widthSC - slimDown, heightSC);

            }

//            Point2D low = dataToScreen.transform(minX,column.tauMin + 1 + blockYOffset);
//            Point2D high = dataToScreen.transform(minX,column.tauMin + column.getSize() + blockYOffset);
//            gc.strokeLine(low.getX(), low.getY(), high.getX(), high.getY());
        }
    }

    /** Defines the domain and the range of the paint scale.
     * @param matrix the matrix to draw the minimum/maximum values for the target statistics from
     * @param paintScale the paintScale to configure
     */
    public void configurePaintscale(CorrelationMatrix matrix, MultiDimensionalPaintScale paintScale) {

        switch (renderMode){
            case MEAN_STD_DEV:
                paintScale.setBiPolar(true);
                paintScale.setPrimaryColor(Color.BLUE);
                paintScale.setSecondaryColor(Color.RED);
                // center mean = zero at the middle of the axis
                double meanRangeMax = Math.max(Math.abs(matrix.getMin(MEAN)), Math.abs(matrix.getMax(MEAN)));
                paintScale.setLowerBounds(-meanRangeMax, matrix.getMin(STD_DEV));
                paintScale.setUpperBounds(meanRangeMax, matrix.getMax(STD_DEV));
                break;
            case MEDIAN_IQR:
                paintScale.setBiPolar(true);
                paintScale.setPrimaryColor(Color.BLUE);
                paintScale.setSecondaryColor(Color.RED);
                // center median = zero at the middle of the axis
                double medianRangeMax = Math.max(Math.abs(matrix.getMin(MEDIAN)), Math.abs(matrix.getMax(MEDIAN)));
                paintScale.setLowerBounds(-medianRangeMax, matrix.getMin(IQR));
                paintScale.setUpperBounds(medianRangeMax, matrix.getMax(IQR));
                break;
            case NEGATIVE_SIGNIFICANT:
                paintScale.setPrimaryColor(Color.BLUE);
                paintScale.setBiPolar(false);
                paintScale.setLowerBounds(0., 0.);
                paintScale.setUpperBounds(1., 1.);
                break;
            case POSITIVE_SIGNIFICANT:
                paintScale.setPrimaryColor(Color.RED);
                paintScale.setBiPolar(false);
                paintScale.setLowerBounds(0., 0.);
                paintScale.setUpperBounds(1., 1.);
                break;
            case ABSOLUTE_SIGNIFICANT:
                paintScale.setPrimaryColor(Color.GREEN);
                paintScale.setBiPolar(false);
                paintScale.setLowerBounds(0., 0.);
                paintScale.setUpperBounds(1., 1.);
        }
        paintScale.compute();
    }

    private void computeBlockDimensions(WindowMetadata metadata) {
        double yearsPerStep = metadata.getTimeInterval();
        blockWidth = Math.min(metadata.windowSize * yearsPerStep, metadata.baseWindowOffset * yearsPerStep);
        blockOffset = metadata.baseWindowOffset * yearsPerStep;
        blockHeight = 1;
    }

    /**
     * Highlights the active window, i.e. the (time,lag) coordinate in the correlogram that is hovered by the user.
     * This is done by moving a rectangle in the scene graph over the according position.
     */
    void highlightActiveWindow(){

        Point activeWindow = sharedData.getHighlightedCell();
        // check whether the active window is a valid coordinate
        boolean drawWindow = activeWindow.x >= 0 && activeWindow.x != Integer.MAX_VALUE && activeWindow.y >= 0 && activeWindow.y != Integer.MAX_VALUE;

        // position rectangle over the window in the correlogram
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(drawWindow && matrix != null){

            computeBlockDimensions(matrix.metadata);

            // transform data to screen coordinates
            Point2D anchorScreen = cellToScreen.transform(activeWindow.getX(), activeWindow.getY());
            Point2D dimensionsScreen = cellToScreen.deltaTransform(1, -1);

            // check that the rectangle doesn't extend the panes
            BoundingBox boundsScreen = new BoundingBox(anchorScreen.getX()-activeWindowStrokeWidth, anchorScreen.getY()-activeWindowStrokeWidth, dimensionsScreen.getX()+2*activeWindowStrokeWidth, dimensionsScreen.getY()+2*activeWindowStrokeWidth);
            if(canvasPane.getLayoutBounds().contains(boundsScreen)){
                activeWindowRect.setLayoutX(anchorScreen.getX());
                activeWindowRect.setLayoutY(anchorScreen.getY());
                activeWindowRect.setWidth(dimensionsScreen.getX());
                activeWindowRect.setHeight(dimensionsScreen.getY());
            } else drawWindow = false;

        }

        activeWindowRect.setVisible(drawWindow);
    }

    /** Resets the axes such that they fit the matrix bounds. */
    @Override public void resetView() {

        CorrelationMatrix m = sharedData.getCorrelationMatrix();
        if(m == null) return;

        Affine cellToData = cellToData(m.metadata);
        Point2D minXminY = cellToData.transform(0,-1);
        Point2D maxXmaxY = cellToData.transform(m.getSize(),m.metadata.getNumberOfDifferentTimeLags()-1);

        axesRanges.set(new Rectangle2D(minXminY.getX(), minXminY.getY(), maxXmaxY.getX() - minXminY.getX(), maxXmaxY.getY() - minXminY.getY()));
    }

    /** @param renderMode see {@link Visualization.Correlogram.RENDER_MODE} */
    public void setRenderMode(RENDER_MODE renderMode) { this.renderMode = renderMode; }

    /** @param uncertaintyVisualization see {@link Visualization.Correlogram.UNCERTAINTY_VISUALIZATION} */
    public void setUncertaintyVisualization(UNCERTAINTY_VISUALIZATION uncertaintyVisualization) { this.uncertaintyVisualization = uncertaintyVisualization; }

}