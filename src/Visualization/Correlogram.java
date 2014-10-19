package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Data.Windowing.WindowMetadata;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
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
        HINTON,         // manipulate column width and height
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
    protected double blockWidthDC, blockHeightDC;
    /** offset between two subsequent blocks. if there are no gaps, blockOffsetDC equals blockWidthDC. */
    private double blockOffsetDC;

    final Affine identity = new Affine(new Translate());

    private final Rectangle activeWindowRect = new javafx.scene.shape.Rectangle(10, 10);
    private final double activeWindowStrokeWidth = 2;

    Color backgroundColor = Color.GRAY;//Color.gray(0.176);//new Color(0.78, 0.78, 0.78, 1);
    Color filtered = backgroundColor;

    // how to draw the border of the correlogram
    int borderwidthPx = 1;                  // line width in pixels
    Color borderColor=Color.gray(0.176);    // line color

    /** Determines whether mouse overs will highlight cells. Cells can be frozen (will turn hoverSensitive to false) and unfrozen by double clicking. */
    private boolean hoverSensitive = true;

    // -----------------------------------------------------------------------------------------------------------------
    // METHODS
    // -----------------------------------------------------------------------------------------------------------------

    public Correlogram(MultiDimensionalPaintScale paintScale){

        margins[TOP] = 0;

        this.paintScale=paintScale;
        xAxis.setMinTickUnit(1);
        yAxis.setMinTickUnit(1);

        chartCanvas.setOnMouseMoved(this::reportHighlightedCell);
        chartCanvas.setOnMouseClicked(this::freezeHighlightedCell);
        // initialize the active window highlight rectangle
        activeWindowRect.setFill(Color.rgb(0, 0, 0, 0));    // transparent fill
        activeWindowRect.setStroke(Color.web("#fff500")); // yellow border
        activeWindowRect.setStrokeType(StrokeType.OUTSIDE);
        activeWindowRect.setVisible(false);
        activeWindowRect.setStrokeWidth(activeWindowStrokeWidth);
        activeWindowRect.setMouseTransparent(true);
        chartPane.getChildren().add(activeWindowRect);

        // configure min width and height
        setMinHeight(10); setMinWidth(10);
        // the correlogram is kept large enough by the container constraints, but setting the preferred width to
        // the computed size can cause the correlogram to get stuck on a width that's too large for the container
        // (because there's no inherent way to compute the necessary space for a canvas).
        setPrefWidth(10); setPrefHeight(10);

    }

    public void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;

        // listen to changes in the correlation result matrix
        sharedData.correlationMatrixProperty().addListener((ov, t, m) -> {
            yAxis.setMinTickUnit(m.metadata.tauStep);
            yAxis.setTickOrigin(m.metadata.tauMin);   // good for small lag ranges (exact tick labels), bad for large lag ranges (odd tick labels)
            xAxis.setScrollBarBoundsDC(new BoundingBox(m.metadata.getMinXValue(),0,m.metadata.baseWindowOffset*m.metadata.getTimeInterval()*m.getSize(),0));
            yAxis.setScrollBarBoundsDC(new BoundingBox(0,m.metadata.tauMin,0,m.metadata.getNumberOfDifferentTimeLags()*m.metadata.tauStep));
            if(aspectRatioFixed()) adaptAspectRatioForMatrix(m);
            resetView();
            drawContents();
        });

        // listen to changes in the matrix filter ranges
        sharedData.matrixFilterRangesProperty().addListener((ov, t, m) -> drawContents());

        sharedData.uncertaintyVisualizationProperty().addListener((observable, oldValue, newValue) -> {
            if(uncertaintyVisualization != newValue){
                setUncertaintyVisualization(newValue);
                drawContents();
            }
        });

    }

    /**
     * The coordinate system applied to the matrix cells is (zero based column index, zero based row index).
     * The cellToData transformation computes the data coordinates of a single cell. So for instance, the first cell in a column
     * is mapped to the minimum time lag, the last cell in a column is mapped to the maximum time lag.
     * The horizontal mapping aligns each column with the start of the time window it represents. Usually, columns overlap,
     * so their width should be reduced. (Overplotting would solve the problem, if plotted in correct order, but this reduces performance.)
     * If they don't overlap, there are gaps between the columns of the matrix.
     * @param metadata contains the information where to plot the blocks (minimum x value in time series, window size, etc.)
     * @return a transformation that converts matrix cell coordinates of the form (column idx, row idx) to data coordinates.
     */
    protected Affine cellToData(WindowMetadata metadata){

        // all widths are measured in time units, window size and base window offset refer to the number of data points
        double yearsPerStep = metadata.getTimeInterval();

        double nonOverlappingBlockWidth = (metadata.windowSize - 1) * yearsPerStep; // k data points -> k-1 intervals
        double overlappingBlockWidth = metadata.baseWindowOffset * yearsPerStep;
        blockWidthDC = Math.min(nonOverlappingBlockWidth, overlappingBlockWidth); // blocks shouldn't overlap if they must not
        blockHeightDC = metadata.tauStep;

        double blockOffset = metadata.baseWindowOffset * yearsPerStep;

        // there can be gaps in horizontal direction (blockOffsetDC > blockWidthDC)
        Transform scale = new Scale(blockOffset, blockHeightDC);

        double tx = metadata.getMinXValue();// + 0.5 * metadata.windowSize - 0.5 * blockWidthDC;
        double ty = metadata.tauMin;
        return new Affine(new Translate(tx, ty).createConcatenation(scale));

    }

    protected Affine cellToScreen(WindowMetadata metadata){
        // we want the upper left corner. since for instance the cell for time lag 100 should end at y value 100, the upper left corner must be at y value 100 + tauStep
        Transform toUpperLeftCorner = new Translate(0, metadata.tauStep);
        return new Affine(dataToScreen().createConcatenation(toUpperLeftCorner.createConcatenation(cellToData(metadata))));
    }

    private void freezeHighlightedCell(MouseEvent mouseEvent) {

        if(mouseEvent.getClickCount()==2){
            if(hoverSensitive){
                hoverSensitive = false;
            } else {
                hoverSensitive = true;
            }
        }

    }

    /*
     This handler listens to mouse moves on the correlogram and informs the shared data object about
     the correlation matrix cell index (window index index and lag index) under the mouse cursor.
    */
    private void reportHighlightedCell(MouseEvent t) {

        if(! hoverSensitive) return;

        // get matrix
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if (matrix == null) return;

        // transform from mouse position into cell coordinates
        Point2D cellCoordinates;
        try { cellCoordinates = cellToScreen(matrix.metadata).inverseTransform(t.getX(), t.getY()); }
        catch (NonInvertibleTransformException e) { e.printStackTrace(); return; }

        int columnIdx = (int) Math.floor(cellCoordinates.getX());
        int lagIdx    = (int) Math.ceil(cellCoordinates.getY());

        Point activeCell = new Point(columnIdx, lagIdx);
        if(columnIdx < 0 || columnIdx >= matrix.getSize() ||
              lagIdx < 0 || lagIdx    >= matrix.metadata.getNumberOfDifferentTimeLags())
            activeCell = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);

        // report only if changes have occured
        if (!sharedData.getHighlightedCell().equals(activeCell)) {
            sharedData.setHighlightedCell(activeCell);
            highlightActiveCell();
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

        if (sharedData == null || sharedData.getCorrelationMatrix()== null || sharedData.getCorrelationMatrix().metadata.setA.size() == 0) return;

        // retrieve data to render
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();

        // depending on the render mode, configure the paintscale (expensive for high-resolution color scales)
        if(matrix != paintScaleMatrix || !renderMode.equals(paintScaleRenderMode)){
            configurePaintscale(matrix, paintScale);
            paintScaleMatrix = matrix;
            paintScaleRenderMode = renderMode;
        }

        Affine cellToScreen = cellToScreen(matrix.metadata);

        // compute boundaries of the correlogram
        int maxColIdx = matrix.getSize() - 1;
        int maxLagIdx = matrix.metadata.getNumberOfDifferentTimeLags() - 1;
        Point minColMinLag = new Point(0, 0),
              maxColMaxLag = new Point(maxColIdx, maxLagIdx);
        Point2D boundaryULC = cellToScreen.transform(0, maxLagIdx);  // upper left corner of the boundary rectangle
        Point2D widthHeight = cellToScreen.deltaTransform(maxColIdx+1, -maxLagIdx-1);

        // ------------------------------------------------------------
        // clipping the contents to render
        // ------------------------------------------------------------
        // horizontally and vertically
        Bounds axesRanges = getAxesRanges();
        if(axesRanges != null){
            try {
                Affine cellToData = cellToData(matrix.metadata);
                Point2D minColMinLagDouble = cellToData.inverseTransform(xAxis.getLowerBound(), yAxis.getLowerBound());
                Point2D maxColMaxLagDouble = cellToData.inverseTransform(xAxis.getUpperBound(), yAxis.getUpperBound());
                int minCol = Math.max(0, (int) Math.floor(minColMinLagDouble.getX()));
                int minLag = Math.max(0, (int) Math.ceil(minColMinLagDouble.getY())-1);
                minColMinLag = new Point(minCol, minLag);
                int maxCol = Math.min(maxColIdx, (int) Math.floor(maxColMaxLagDouble.getX()));
                int maxLag = Math.min(maxLagIdx, (int) Math.ceil(maxColMaxLagDouble.getY()));
                maxColMaxLag = new Point(maxCol, maxLag);
            } catch (NonInvertibleTransformException e) { e.printStackTrace(); }
        }

        // clipping on the resolution
        Point2D blockDimensionsScreen = cellToScreen.deltaTransform(1, -1);
        int windowStep = Math.max(1, (int) Math.floor(1. / blockDimensionsScreen.getX()));
        int lagStep    = Math.max(1, (int) Math.floor(1. / blockDimensionsScreen.getY()));

        switch(renderMode){
            case MEAN_STD_DEV:
                if(uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON)
                    drawContentsHinton(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, MEAN, STD_DEV);
                else
                    drawContentsMultivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, MEAN, STD_DEV);
                break;
            case MEDIAN_IQR:
                if(uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON)
                    drawContentsHinton(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, MEDIAN, IQR);
                else
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

        // draw boundaries of the correlogram
        gc.setStroke(borderColor);
        gc.setLineWidth(borderwidthPx);
        gc.strokeRect(boundaryULC.getX()-1, boundaryULC.getY()-1, widthHeight.getX()+2, widthHeight.getY()+2);

        xAxis.drawContents();
        yAxis.drawContents();
        highlightActiveCell();

    }

    void drawContentsUnivariate(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int DIM) {// for each column of the matrix (or, equivalently, for each time window)

        Affine cellToScreen = cellToScreen(matrix.metadata);
        Point2D blockSizeSC = cellToScreen.deltaTransform(1,-1);
        double widthSC = blockSizeSC.getX()  +1, // +1: to avoid gaps between the blocks
               heightSC = blockSizeSC.getY() +1;

        double[] srcPts = new double[2], dstPts = new double[2];
        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getResultItems().get(i);

            for (int lag = minColMinLag.y; lag <= maxColMaxLag.y; lag += lagStep) {

                // upper left corner, bottom right corner of the cell, the same for the last drawn cell
                // even though the array contains only one point, object creation is avoided (might putting some pressure of the GC?)
                srcPts[0] = i; srcPts[1] = lag;
                cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                gc.setFill(paintScale.getPaint(column.data[DIM][lag]));
                for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
                    if(matrixFilterRanges[STAT] == null) continue;
                    if(column.data[STAT][lag] < matrixFilterRanges[STAT][0] ||
                            column.data[STAT][lag] > matrixFilterRanges[STAT][1]){
                        gc.setFill(filtered);
                        break;
                    }
                }

                // draw cell
                gc.fillRect(dstPts[0], dstPts[1], widthSC, heightSC);

            }
        }
    }

    /**
     * Performance note: computing the width and height of a block in screen coordinates only once and adding up coordinates might introduce rounding errors (not tested).
     * By now, performance is satisfactory anyway.
     */
    void drawContentsMultivariate(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int CORRELATION_DIM, int UNCERTAINTY_DIM) {

        double uncertainty;     // relative uncertainty (compared to the maximum uncertainty present in the matrix) in the current cell (only used with UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH)
        double slimDown = 0;    // results from the relative uncertainty. A high uncertainty will make the cell much slimmer, no uncertainty will leave it at its full width.
        double minUncertainty = 0;         // matrix.getMin(UNCERTAINTY_DIM) would actually underestimate the uncertainty! (if the minimum uncertainty is high, a full width column would actually represent an uncertain value)
        double maxUncertainty = matrix.getMax(UNCERTAINTY_DIM);   // this overestimates the uncertainty but makes the differences much better visible (since an sd of 1 almost never occurs)

        Affine cellToScreen = cellToScreen(matrix.metadata);
        Point2D blockSizeSC = cellToScreen.deltaTransform(1,-1);
        double widthSC = blockSizeSC.getX()  + (uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.COLOR ? 1 : 0), // +1: to avoid gaps between the blocks
               heightSC = blockSizeSC.getY() + (uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.COLOR ? 1 : 0);

        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();

        double[] srcPts = new double[2], dstPts = new double[2];

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getResultItems().get(i);

            for (int lag = minColMinLag.y; lag <= maxColMaxLag.y; lag += lagStep) {

                if(uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH){
                    uncertainty = (column.data[UNCERTAINTY_DIM][lag] - minUncertainty) / (maxUncertainty - minUncertainty);
                    slimDown = widthSC >= heightSC ? widthSC * uncertainty : heightSC * uncertainty;
                    if (Double.isNaN(slimDown)) slimDown = 0; // NaNs occur naturally in empty cells
                }

                srcPts[0] = i; srcPts[1] = lag;
                cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                // draw cell
                switch(uncertaintyVisualization){
                    case COLOR:
                        gc.setFill(paintScale.getPaint(column.data[CORRELATION_DIM][lag], column.data[UNCERTAINTY_DIM][lag]));
                        break;
                    default:
                        gc.setFill(paintScale.getPaint(column.data[CORRELATION_DIM][lag]));
                        break;
                }

                for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
                    if(matrixFilterRanges[STAT] == null) continue;
                    if(column.data[STAT][lag] < matrixFilterRanges[STAT][0] ||
                       column.data[STAT][lag] > matrixFilterRanges[STAT][1]){
                        gc.setFill(filtered);
                        break;
                    }
                }

                if(widthSC >= heightSC)
                    gc.fillRect(dstPts[0], dstPts[1], widthSC - slimDown, heightSC);
                else
                    gc.fillRect(dstPts[0], dstPts[1] + slimDown, widthSC, heightSC - slimDown);


            }

        }
    }

    /**
     * Aggregates and renders the matrix data in a hinton diagram style.
     */
    void drawContentsHinton(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int CORRELATION_DIM, int UNCERTAINTY_DIM) {

        double uncertainty;     // relative uncertainty (compared to the maximum uncertainty present in the matrix) in the current cell (only used with UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH)
        double slimDown = 0;    // results from the relative uncertainty. A high uncertainty will make the cell much slimmer, no uncertainty will leave it at its full width.
        double minUncertainty = 0;         // matrix.getMin(UNCERTAINTY_DIM) would actually underestimate the uncertainty! (if the minimum uncertainty is high, a full width column would actually represent an uncertain value)
        double maxUncertainty = matrix.getMax(UNCERTAINTY_DIM);   // this overestimates the uncertainty but makes the differences much better visible (since an sd of 1 almost never occurs)
        if(maxUncertainty < 1e-15) maxUncertainty = 1;

        Affine cellToScreen = cellToScreen(matrix.metadata);
        Point2D blockSizeSC = cellToScreen.deltaTransform(1,-1);
        double widthSC = blockSizeSC.getX(),
               heightSC = blockSizeSC.getY();
//        assert Math.abs(widthSC - heightSC) < 0.5 : "width height difference too large: "+Math.abs(widthSC - heightSC); // height and width shouldn't differ by more than one tenth of a pixel

        //        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();

        // aggregation
        double minCellSize = 8;   // pixels. a cell is an unaggregated item of the correlation matrix
        double minGlyphSize = 50; // pixels. a glyph represents aggregated items

        double[] srcPts = new double[2], dstPts = new double[2];

        // draw unaggregated
        if(widthSC >= minCellSize){
            for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

                CorrelationMatrix.CorrelationColumn column = matrix.getResultItems().get(i);

                for (int lag = minColMinLag.y; lag <= maxColMaxLag.y; lag += lagStep) {

                    if(matrix.metadata.setA.size() == 1 && matrix.metadata.setB.size()==0) uncertainty=0;
                    else uncertainty = (column.data[UNCERTAINTY_DIM][lag] - minUncertainty) / (maxUncertainty - minUncertainty);
                    slimDown = widthSC * (1-Math.sqrt(1-uncertainty)) / 2;
                    if(Double.isNaN(uncertainty)) slimDown = 0; // NaNs occur naturally in empty cells

                    srcPts[0] = i; srcPts[1] = lag;
                    cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                    gc.setFill(paintScale.getPaint(column.data[CORRELATION_DIM][lag]));

                    gc.fillRect(dstPts[0]+slimDown, dstPts[1]+slimDown, widthSC - 2*slimDown, heightSC-2*slimDown);


                }
            }
        }
        // draw aggregated
        else {

            int groupSize = (int) Math.ceil(minGlyphSize / widthSC);
            groupSize = Math.min(groupSize, Math.min(matrix.getSize(), matrix.metadata.getNumberOfDifferentTimeLags()));

            Point2D glyphSizeSC = cellToScreen.deltaTransform(groupSize,0);
            double glyphSizePx = glyphSizeSC.getX();

            int minCol = minColMinLag.x - Math.abs(minColMinLag.x % groupSize);
            int minLag = minColMinLag.y - Math.abs(minColMinLag.y % groupSize);

            MatrixRegionAggregation matrixRegionAggregation = new MatrixRegionAggregation();
            matrixRegionAggregation.size = groupSize;
            matrixRegionAggregation.CORRELATION_DIM = CORRELATION_DIM;
            matrixRegionAggregation.UNCERTAINTY_DIM = UNCERTAINTY_DIM;

            double minUncertaintyRelative = Double.NaN,
                   averageUncertaintyRelative = Double.NaN,
                   maxUncertaintyRelative = Double.NaN,
                   uncertaintyRange = maxUncertainty - minUncertainty;

            for (int col = minCol; col <= maxColMaxLag.x; col += groupSize) {

                for (int lag = minLag; lag <= maxColMaxLag.y; lag += groupSize) {

                    // aggregate square range
                    matrixRegionAggregation.column = col;
                    matrixRegionAggregation.row = lag;
                    matrix.aggregate(matrixRegionAggregation);

                    minUncertaintyRelative = (matrixRegionAggregation.minUncertainty - minUncertainty) / uncertaintyRange;
                    averageUncertaintyRelative = (matrixRegionAggregation.averageUncertainty - minUncertainty) / uncertaintyRange;
                    maxUncertaintyRelative = (matrixRegionAggregation.maxUncertainty - minUncertainty) / uncertaintyRange;

                    // render
                    srcPts[0] = col; srcPts[1] = lag-1;       // lower left corner of a single cell
                    cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

//                    gc.strokeRect(dstPts[0], dstPts[1]-glyphSizePx, glyphSizePx, glyphSizePx);

                    // min uncertainty
                    slimDown = glyphSizePx * (1-Math.sqrt(1-minUncertaintyRelative)) / 2;

                    if(Double.isNaN(averageUncertaintyRelative)) continue;

                    if(Double.isNaN(minUncertaintyRelative)) slimDown = 0; // NaNs occur naturally in empty cells
                    assert slimDown >= 0: String.format("Min uncertainty %s invalid, slimdown %s raw minUC %s",minUncertainty,slimDown,matrixRegionAggregation.minUncertainty);
                    gc.strokeRect(dstPts[0]+slimDown, dstPts[1]-glyphSizePx+slimDown, glyphSizePx - 2*slimDown, glyphSizePx-2*slimDown);

                    // average uncertainty
                    slimDown = glyphSizePx * (1-Math.sqrt(1-averageUncertaintyRelative)) / 2;
                    assert slimDown >= 0: "Avg uncertainty "+averageUncertaintyRelative+" invalid, slimdown: "+slimDown;
                    gc.setFill(paintScale.getPaint(matrixRegionAggregation.averageCorrelation));
                    gc.fillRect(dstPts[0]+slimDown, dstPts[1]-glyphSizePx+slimDown, glyphSizePx - 2*slimDown, glyphSizePx-2*slimDown);

                    // max uncertainty
                    slimDown = glyphSizePx * (1-Math.sqrt(1-maxUncertaintyRelative)) / 2;
                    if(Double.isNaN(maxUncertaintyRelative)) slimDown = 0; // NaNs occur naturally in empty cells
                    assert slimDown >= 0: "Max uncertainty "+maxUncertaintyRelative+" invalid, slimdown: "+slimDown;
                    gc.strokeRect(dstPts[0]+slimDown, dstPts[1]-glyphSizePx+slimDown, glyphSizePx - 2*slimDown, glyphSizePx-2*slimDown);

                }
            }
        }

    }

    public static class MatrixRegionAggregation {
        // input parameters
        /** The upper left corner of the region to aggregate. */
        public int column, row;
        /** The side length (rows/columns) of the square region to aggregate. */
        public int size;
        /** Which data dimensions to use as source for the correlation and uncertainty data. */
        public int CORRELATION_DIM, UNCERTAINTY_DIM;
        // output parameters
        /** Number of matrix columns/rows actually covered by the aggregation region (can be influenced by clipping to the matrix size). */
        public int width, height;
        /** The average correlation of the cells in the region. */
        public double averageCorrelation;
        public double minUncertainty, averageUncertainty, maxUncertainty;

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

    /**
     * Highlights the active cell, i.e. the (time,lag) coordinate in the correlogram that is hovered by the user.
     * This is done by moving a rectangle in the scene graph over the according position.
     */
    void highlightActiveCell(){

        Point activeWindow = sharedData.getHighlightedCell();
        // check whether the active window is a valid coordinate
        boolean drawWindow = activeWindow.x >= 0 && activeWindow.x != Integer.MAX_VALUE && activeWindow.y >= 0 && activeWindow.y != Integer.MAX_VALUE;

        // position rectangle over the window in the correlogram
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(drawWindow && matrix != null){

            // transform data to screen coordinates
            Affine cellToScreen = cellToScreen(matrix.metadata);
            Point2D anchorScreen = cellToScreen.transform(activeWindow.getX(), activeWindow.getY());
            Point2D dimensionsScreen = cellToScreen.deltaTransform(1, -1);

            // check that the rectangle doesn't extend the panes
            BoundingBox boundsScreen = new BoundingBox(anchorScreen.getX(), anchorScreen.getY(), dimensionsScreen.getX(), dimensionsScreen.getY());
            if(chartPane.getLayoutBounds().contains(boundsScreen)){
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

        if(sharedData == null || sharedData.getCorrelationMatrix() == null) return;
        CorrelationMatrix m = sharedData.getCorrelationMatrix();

        if(aspectRatioFixed()) fitWithAspectRatioAxesBounds(m);
        else                   axesRanges.set(useAllSpaceAxesBounds(m));

    }

    /**
     * Computes the bounds of the x and y axes such that the plot fills all available plotting space.
     * @param m the matrix to take the shape from (number of columns and rows)
     * @return min/max values for the x and y axes
     */
    protected Bounds useAllSpaceAxesBounds(CorrelationMatrix m) {
        Affine cellToData = cellToData(m.metadata);
        Point2D minXminY = cellToData.transform(0,0);
        Point2D maxXmaxY = cellToData.transform(m.getSize(), m.metadata.getNumberOfDifferentTimeLags());  // x: the last one is getSize()-1 but we want to see it (not its start point to be the upper bound of the axis)
        double xRange = maxXmaxY.getX() - minXminY.getX();
        double yRange = maxXmaxY.getY() - minXminY.getY();
        return new BoundingBox(minXminY.getX(), minXminY.getY(), xRange, yRange);
    }

    /**
     * Computes the bounds of the x and y axes such that the entire correlation matrix can be plotted while keeping the cells quadratic
     * @param m the matrix to take the shape from (number of columns and rows)
     * @return min/max values for the x and y axes
     */
    protected void fitWithAspectRatioAxesBounds(CorrelationMatrix m) {

        Bounds fullFitBounds = useAllSpaceAxesBounds(m);
        setAxesRanges(fullFitBounds);   // start with a full fit
        cellToData(m.metadata);         // recompute block dimensions

        // since blocks are quadratic, the chart will be elongation times wider than high
        double elongation = 1. * m.getSize() / m.metadata.getNumberOfDifferentTimeLags();
        // if the available canvas is not wide enough to plot the entire chart, use the full width as reduce the height accordingly
        boolean fitChartToEntireWidth = elongation >= xAxis.getWidth() / yAxis.getHeight();

        if (fitChartToEntireWidth) adaptYAxis(fullFitBounds);
        else                       adaptXAxis(fullFitBounds);

    }

    /** @param renderMode see {@link Visualization.Correlogram.RENDER_MODE} */
    public void setRenderMode(RENDER_MODE renderMode) { this.renderMode = renderMode; }

    /** @param uncertaintyVisualization see {@link Visualization.Correlogram.UNCERTAINTY_VISUALIZATION} */
    public void setUncertaintyVisualization(UNCERTAINTY_VISUALIZATION uncertaintyVisualization) {
        this.uncertaintyVisualization = uncertaintyVisualization;

        // fix the aspect ratio for the hinton visualization method
        if(uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON && sharedData.getCorrelationMatrix() != null){
            adaptAspectRatioForMatrix(sharedData.getCorrelationMatrix());
            resetView();
        } else {
            // unlock aspect ratio
            setDataPointsPerPixelRatio(Double.NaN);
        }
    }

    /** Selects a width/height ratio such that each cell is rendered as a square on the screen. */
    protected void adaptAspectRatioForMatrix(CorrelationMatrix matrix) {
        // one column (blockWidth) should be exactly as large as one row (tau step)
        cellToData(matrix.metadata);

        double ratio = 1.*blockWidthDC/blockHeightDC;
        assert ! Double.isNaN(ratio) : "Ratio shouldn't be NaN.";
        setDataPointsPerPixelRatio(ratio);
    }

}