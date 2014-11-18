package Visualization;

import Data.Cacheable;
import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Data.Statistics.AggregatedCorrelationMatrix;
import Data.Windowing.WindowMetadata;
import javafx.beans.value.ObservableValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.transform.*;

import java.awt.*;
import java.util.Arrays;

import static Data.Correlation.CorrelationMatrix.*;
import static Data.Statistics.AggregatedCorrelationMatrix.MatrixRegionData;

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
        COLUMN_WIDTH,       // manipulate column width
        HINTON,             // manipulate column width and height
        HINTON_AGGREGATED,  // aggregate cells instead of displaying them directly
        COLOR               // manipulate base color (e.g. changing saturation)
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
    RENDER_MODE renderMode;

    /** Contains parameters about the aggregation (how many rows and columns per region, etc.) */
    private MatrixRegionData aggregationPrototype = new MatrixRegionData();

    /** width and height (in data coordinates) of a cell in the correlogram. */
    protected double blockWidthDC, blockHeightDC;
//    /** offset between two subsequent blocks. if there are no gaps, blockOffsetDC equals blockWidthDC. */
//    private double blockOffsetDC;

    final Affine identity = new Affine(new Translate());

    private final Rectangle activeWindowRect = new javafx.scene.shape.Rectangle(10, 10);

    /** Determines whether mouse overs will highlight cells. Cells can be frozen (will turn hoverSensitive to false) and unfrozen by double clicking. */
    private boolean hoverSensitive = true;

    /** Affects the hinton render mode. If true, instead of the average correlation, the first and third quartiles are shown in a glyph. */
    public boolean hintonDrawQuartiles = false;
    /** The minimum side length of a glyph in pixels. A glyph represents a region of the correlation matrix, i.e. an aggregation of matrix cells. */
    double minGlyphSize = 25;
    /** The minimum size of a cell (in pixels) when using the hinton visualization.
     * A cell is an unaggregated item of the correlation matrix.
     * Defines the switching point to aggregated hinton visualization to avoid too small cells (because uncertainty becomes illegible then). */
    double minCellSize = 10;

    Color backgroundColor = Color.GRAY;//Color.gray(0.176);//new Color(0.78, 0.78, 0.78, 1);
    Color filteredColor = backgroundColor;
    // how to draw the border of the correlogram
    int borderwidthPx = 1;                  // line width in pixels
    Color borderColor=Color.gray(0.176);    // line color

    // -----------------------------------------------------------------------------------------------------------------
    // CACHEABLES
    // -----------------------------------------------------------------------------------------------------------------

    /** encodes 2D values in a single color */
    private final Cacheable<MultiDimensionalPaintScale> paintScale = new Cacheable<MultiDimensionalPaintScale>() {

        /** The matrix for which the paint scale was computed. */
        private CorrelationMatrix paintScaleMatrix;
        /** The render mode for which the paint scale was computed. */
        private RENDER_MODE paintScaleRenderMode;

        @Override public boolean isValid() {
            CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
            // the matrix is immutable, so comparing references is faster than comparing with equals()
            return matrix == paintScaleMatrix && renderMode.equals(paintScaleRenderMode);
        }

        @Override public void recompute() {
            CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
            // depending on the render mode, configure the paintscale (expensive for high-resolution color scales)
            Correlogram.this.configurePaintscale(matrix, cachedValue);
            paintScaleMatrix = matrix;
            paintScaleRenderMode = renderMode;
        }
    };

    final Cacheable<AggregatedCorrelationMatrix> aggregatedCorrelationMatrix = new Cacheable<AggregatedCorrelationMatrix>() {

        /** The deep hash code of the filter ranges array. Saves the hassle of making deep copies of the filter ranges array. */
        int filterRangesHash = Integer.MIN_VALUE;
        CorrelationMatrix aggregateOf;
        /** The render mode for which the paint scale was computed. */
        private RENDER_MODE cachedForRenderMode;

        // the group size for which the aggregation was done is implicitly stored in the aggregated matrix.

        @Override public boolean isValid() {
            CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
            int newHashCode = Arrays.deepHashCode(sharedData.getMatrixFilterRanges());
            int groupSize = computeGroupSize(sharedData.getCorrelationMatrix(), minGlyphSize);
            return cachedValue != null
                    && renderMode == cachedForRenderMode
                    && matrix == aggregateOf                    // matrices should be immutable, so comparing references should be fast enough
                    && groupSize == cachedValue.getColumnsPerRegion()
                    && groupSize == cachedValue.getRowsPerRegion()
                    && newHashCode == filterRangesHash;

        }

        @Override public void recompute() {
            CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
            double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();

            int groupSize = computeGroupSize(matrix, minGlyphSize);
            aggregationPrototype.width = groupSize;
            aggregationPrototype.height = groupSize;
            cachedValue = new AggregatedCorrelationMatrix(matrix, matrixFilterRanges, aggregationPrototype);

            filterRangesHash = Arrays.deepHashCode(matrixFilterRanges);
            aggregateOf = matrix;
            cachedForRenderMode = renderMode;
        }
    };

    // -----------------------------------------------------------------------------------------------------------------
    // METHODS
    // -----------------------------------------------------------------------------------------------------------------

    public Correlogram(MultiDimensionalPaintScale paintScale){

        margins[TOP] = 0;

        this.paintScale.set(paintScale);
        xAxis.setMinTickUnit(1);
        yAxis.setMinTickUnit(1);

        chartCanvas.setOnMouseMoved(this::reportActiveMatrixRegion);
        // toggle hover sensitivity on double click
        chartCanvas.setOnMouseClicked(event -> { if(event.getClickCount()==2) hoverSensitive = !hoverSensitive; });
        // initialize the active window highlight rectangle
        activeWindowRect.setFill(Color.rgb(0, 0, 0, 0));    // transparent fill
        activeWindowRect.setStroke(Color.web("#fff500")); // yellow border
        activeWindowRect.setStrokeType(StrokeType.OUTSIDE);
        activeWindowRect.setVisible(false);
        double activeWindowStrokeWidth = 2;
        activeWindowRect.setStrokeWidth(activeWindowStrokeWidth);
        activeWindowRect.setMouseTransparent(true);
        chartPane.getChildren().add(activeWindowRect);

        // configure min width and height
        setMinHeight(10); setMinWidth(10);
        // the correlogram is kept large enough by the container constraints, but setting the preferred width to
        // the computed size can cause the correlogram to get stuck on a width that's too large for the container
        // (because there's no inherent way to compute the necessary space for a canvas).
        setPrefWidth(10); setPrefHeight(10);

        setRenderMode(defaultRenderMode);

    }

    public void setSharedData(SharedData sharedData) {
        this.sharedData = sharedData;

        // listen to changes in the correlation result matrix
        sharedData.correlationMatrixProperty().addListener((ov, t, m) -> {
            yAxis.setMinTickUnit(m.metadata.tauStep);
            yAxis.setTickOrigin(m.metadata.tauMin);   // good for small lag ranges (exact tick labels), bad for large lag ranges (odd tick labels)
            xAxis.setScrollBarBoundsDC(new BoundingBox(m.metadata.getMinXValue(),0,m.metadata.baseWindowOffset*m.metadata.getTimeInterval()*m.getSize(),0));
            yAxis.setScrollBarBoundsDC(new BoundingBox(0,m.metadata.tauMin,0,m.metadata.getNumberOfDifferentTimeLags()*m.metadata.tauStep));
            aggregatedCorrelationMatrix.invalidate(); // the cached result refers to another matrix, it is no longer usable.
            if(aspectRatioFixed()) adaptAspectRatioForMatrix(m);
            resetView();
            drawContents();
        });

        // listen to changes in the matrix filter ranges
        sharedData.matrixFilterRangesProperty().addListener((ov, t, m) -> drawContents());

        // listen to the current uncertainty visualization method
        sharedData.uncertaintyVisualizationProperty().addListener((observable, oldValue, newValue) -> {
            if(uncertaintyVisualization != newValue){
                setUncertaintyVisualization(newValue);
                drawContents();
            }
        });

        // listen to changes in the highlighted matrix region
        sharedData.activeCorrelationMatrixRegionProperty().addListener(this::highlightActiveMatrixRegion);

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

    /**
     * Renders the correlogram.
     * The width of a window is windowSize - overlap (overlap = |w| - baseWindowOffset)
     * Columns are centered around the window average. E.g. [1956..1958] -> 1956 + (1958-1956+1)/2 = 1957.5
     * The offset between two subsequent windows is still baseWindowOffset.
     */
    @Override public void drawContents() {

        //TODO drawContents() is triggered by several events leading to up to over a dozen draws when e.g. selecting a correlogram from the list (performance? correctness?)

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        gc.setTransform(identity);
        gc.setFill(backgroundColor);
        gc.fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());

        if (sharedData == null || sharedData.getCorrelationMatrix()== null || sharedData.getCorrelationMatrix().metadata.setA.size() == 0) return;

        // retrieve data to render
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();

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
        Bounds axesRanges = getClipRegionDC();
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

        // draw boundaries of the correlogram
        gc.setStroke(borderColor);
        gc.setLineWidth(borderwidthPx);
        gc.strokeRect(boundaryULC.getX()-1, boundaryULC.getY()-1, widthHeight.getX()+2, widthHeight.getY()+2);

        // if a uncertainty statistic is defined, draw bivariate
        if(getUncertaintyStatistic() >= 0){
            if(usingHintonVisualization())
                drawContentsHinton(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, getCorrelationStatistic(), getUncertaintyStatistic());
            else
                drawContentsMultivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, getCorrelationStatistic(), getUncertaintyStatistic());
        // no uncertainty statistic defined, draw univariate
        } else {
            drawContentsUnivariate(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, getCorrelationStatistic());
        }

        xAxis.drawContents();
        yAxis.drawContents();

        MatrixRegionData activeRegion = sharedData.getActiveCorrelationMatrixRegion();

        // if the filter ranges triggered a redraw (and thus a reaggregation) publish the new region data
        if(activeRegion != null && uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON_AGGREGATED)
            sharedData.setActiveCorrelationMatrixRegion(aggregatedCorrelationMatrix.get().getRegion(activeRegion.column, activeRegion.row));

        // redraw the active matrix region rectangle, becaues zooming changes its screen size but usually doesn't change it
        highlightActiveMatrixRegion(null, null, sharedData.getActiveCorrelationMatrixRegion());

    }

    /** @return the matrix statistic that the render mode prescribes for the correlation value. if the value is -1, no statistic is defined. */
    protected int getCorrelationStatistic(){
        switch(renderMode){
            case MEAN_STD_DEV: return MEAN;
            case MEDIAN_IQR: return MEDIAN;
            case NEGATIVE_SIGNIFICANT: return NEGATIVE_SIGNIFICANT;
            case POSITIVE_SIGNIFICANT: return POSITIVE_SIGNIFICANT;
            case ABSOLUTE_SIGNIFICANT: return ABSOLUTE_SIGNIFICANT;
            default: return -1;
        }
    }

    /** @return the matrix statistic that the render mode prescribes for the uncertainty value. if the value is -1, no statistic is defined. */
    protected int getUncertaintyStatistic(){
        switch(renderMode){
            case MEAN_STD_DEV: return STD_DEV;
            case MEDIAN_IQR: return IQR;
            default: return -1;
        }
    }

    void drawContentsUnivariate(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int DIM) {// for each column of the matrix (or, equivalently, for each time window)

        Affine cellToScreen = cellToScreen(matrix.metadata);
        Point2D blockSizeSC = cellToScreen.deltaTransform(1,-1);
        double widthSC = blockSizeSC.getX()  +1, // +1: to avoid gaps between the blocks
               heightSC = blockSizeSC.getY() +1;

        double[] srcPts = new double[2], dstPts = new double[2];
        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();
        MultiDimensionalPaintScale paintScale = this.paintScale.get();

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getColumns().get(i);

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
                        gc.setFill(filteredColor);
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
        MultiDimensionalPaintScale paintScale = this.paintScale.get();

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getColumns().get(i);

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
                        gc.setFill(filteredColor);
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
     * TODO: refactor the decision logic for switching between aggregated and unaggregated views to an own method. Then move the assignments to this.uncertaintyVisualization one level up.
     */
    void drawContentsHinton(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int CORRELATION_DIM, int UNCERTAINTY_DIM) {

        double minUncertainty = 0;         // matrix.getMin(UNCERTAINTY_DIM) would actually underestimate the uncertainty! (if the minimum uncertainty is high, a full width column would actually represent an uncertain value)
        double maxUncertainty = matrix.getMax(UNCERTAINTY_DIM);   // this overestimates the uncertainty but makes the differences much better visible (since an sd of 1 almost never occurs)
        if(maxUncertainty < 1e-15) maxUncertainty = 1;            // if there is no uncertainty, avoid dividing by zero (no slimdown will be computed)
        else maxUncertainty *= 1.01;                              // adding one percent avoids reducing the size of a glyph (element) to zero
        double uncertaintyRange = maxUncertainty - minUncertainty;

        Affine cellToScreen = cellToScreen(matrix.metadata);
        Point2D blockSizeSC = cellToScreen.deltaTransform(1, -1);
        double singleCellWidth = blockSizeSC.getX(),    // width  (px) of a single correlation matrix cell on screen
               singleCellHeight = blockSizeSC.getY();   // height (px) of a single correlation matrix cell on screen

        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();
        MultiDimensionalPaintScale paintScale = this.paintScale.get();


        double[] srcPts = new double[2], dstPts = new double[2];

        boolean drawFilteredLikeBackground = filteredColor.equals(backgroundColor);

        // draw unaggregated
        if (singleCellWidth < minCellSize || singleCellHeight < minCellSize) {
            this.uncertaintyVisualization = UNCERTAINTY_VISUALIZATION.HINTON_AGGREGATED;
            drawContentsHintonAggregated(gc, minColMinLag, maxColMaxLag, minUncertainty, uncertaintyRange, cellToScreen, paintScale, srcPts, dstPts);
        } else {
            this.uncertaintyVisualization = UNCERTAINTY_VISUALIZATION.HINTON;
            drawContentsHintonUnaggregated(gc, matrix, minColMinLag, maxColMaxLag, windowStep, lagStep, CORRELATION_DIM, UNCERTAINTY_DIM, minUncertainty, uncertaintyRange, cellToScreen, singleCellWidth, singleCellHeight, matrixFilterRanges, paintScale, srcPts, dstPts, drawFilteredLikeBackground);
        }

    }

    private void drawContentsHintonUnaggregated(GraphicsContext gc, CorrelationMatrix matrix, Point minColMinLag, Point maxColMaxLag, int windowStep, int lagStep, int CORRELATION_DIM, int UNCERTAINTY_DIM, double minUncertainty, double uncertaintyRange, Affine cellToScreen, double singleCellWidth, double singleCellHeight, double[][] matrixFilterRanges, MultiDimensionalPaintScale paintScale, double[] srcPts, double[] dstPts, boolean drawFilteredLikeBackground) {
        double uncertainty;     // relative uncertainty (compared to the maximum uncertainty present in the matrix) in the current cell (only used with UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH)
        double slimDown;        // results from the relative uncertainty. A high uncertainty will make the cell much slimmer, no uncertainty will leave it at its full width.

        for (int i = minColMinLag.x; i <= maxColMaxLag.x; i += windowStep) {

            CorrelationMatrix.CorrelationColumn column = matrix.getColumns().get(i);

            renderColumn: for (int lag = minColMinLag.y; lag <= maxColMaxLag.y; lag += lagStep) {

                if(matrix.metadata.setA.size() == 1 && matrix.metadata.setB.size()==0) uncertainty=0;
                else uncertainty = (column.data[UNCERTAINTY_DIM][lag] - minUncertainty) / uncertaintyRange;
                slimDown = singleCellWidth * (1-Math.sqrt(1-uncertainty)) / 2;
                if(Double.isNaN(uncertainty)) slimDown = 0; // NaNs occur naturally in empty cells

                boolean isFiltered = false;
                for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
                    if(matrixFilterRanges[STAT] == null) continue;
                    if(column.data[STAT][lag] < matrixFilterRanges[STAT][0] ||
                            column.data[STAT][lag] > matrixFilterRanges[STAT][1]) {
                        if(drawFilteredLikeBackground) continue renderColumn;
                        isFiltered = true;
                        break;
                    }
                }

                srcPts[0] = i; srcPts[1] = lag;
                cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                gc.setFill(isFiltered ? filteredColor : paintScale.getPaint(column.data[CORRELATION_DIM][lag]));
                gc.fillRect(dstPts[0]+slimDown, dstPts[1]+slimDown, singleCellWidth - 2*slimDown, singleCellHeight-2*slimDown);

            }
        }
    }

    private void drawContentsHintonAggregated(GraphicsContext gc, Point minColMinLag, Point maxColMaxLag, double minUncertainty, double uncertaintyRange, Affine cellToScreen, MultiDimensionalPaintScale paintScale, double[] srcPts, double[] dstPts) {
        double slimDown;        // results from the relative uncertainty. A high uncertainty will make the cell much slimmer, no uncertainty will leave it at its full width.

        AggregatedCorrelationMatrix aggMatrix = aggregatedCorrelationMatrix.get();
        int groupSize = aggMatrix.getColumnsPerRegion();

        Point2D glyphSizeSC = cellToScreen.deltaTransform(groupSize,0);
        double glyphSizePx = glyphSizeSC.getX();

        int minCol = minColMinLag.x - Math.abs(minColMinLag.x % groupSize);
        int minLag = minColMinLag.y - Math.abs(minColMinLag.y % groupSize);

        double minUncertaintyRelative,
               averageUncertaintyRelative,
               maxUncertaintyRelative;

        MatrixRegionData matrixRegionData;

        gc.setStroke(borderColor);

        for (int col = minCol; col <= maxColMaxLag.x; col += groupSize) {

            for (int lag = minLag; lag <= maxColMaxLag.y; lag += groupSize) {

                // aggregate square range
                matrixRegionData = aggMatrix.getRegion(col,lag);

                minUncertaintyRelative = (matrixRegionData.minUncertainty - minUncertainty) / uncertaintyRange;
                averageUncertaintyRelative = (matrixRegionData.averageUncertainty - minUncertainty) / uncertaintyRange;
                maxUncertaintyRelative = (matrixRegionData.maxUncertainty - minUncertainty) / uncertaintyRange;

                // render
                srcPts[0] = matrixRegionData.column; srcPts[1] = matrixRegionData.row - 1;       // lower left corner of a single cell
                cellToScreen.transform2DPoints(srcPts, 0, dstPts, 0, 1);

                // skip regions containg only NaNs
                if(Double.isNaN(averageUncertaintyRelative)) continue;

                // min and max correlation and average uncertainty
                slimDown = glyphSizePx * (1-Math.sqrt(1-averageUncertaintyRelative)) / 2;
                assert slimDown >= 0: "Avg uncertainty "+averageUncertaintyRelative+" invalid, slimdown: "+slimDown;

                if(hintonDrawQuartiles){
                    // draw min max correlation and average uncertainty as two triangles
                    gc.setFill(paintScale.getPaint(matrixRegionData.firstQuartileCorrelation));
                    gc.fillPolygon(new double[]{dstPts[0]+slimDown,dstPts[0]+glyphSizePx-slimDown,dstPts[0]+slimDown},new double[]{dstPts[1]-glyphSizePx+slimDown,dstPts[1]-glyphSizePx+slimDown,dstPts[1]-slimDown},3);
                    gc.setFill(paintScale.getPaint(matrixRegionData.thirdQuartileCorrelation));
                    gc.fillPolygon(new double[]{dstPts[0]+slimDown,dstPts[0]+glyphSizePx-slimDown,dstPts[0]+glyphSizePx-slimDown},new double[]{dstPts[1]-slimDown,dstPts[1]-glyphSizePx+slimDown,dstPts[1]-slimDown},3);
                } else {
//                    draw average correlation and uncertainty as single rect
                    gc.setFill(paintScale.getPaint(matrixRegionData.medianCorrelation));
                    gc.fillRect(dstPts[0]+slimDown, dstPts[1]-glyphSizePx+slimDown, glyphSizePx - 2*slimDown, glyphSizePx-2*slimDown);

                }

                // min uncertainty
                slimDown = glyphSizePx * (1-Math.sqrt(1-minUncertaintyRelative)) / 2;
                if(Double.isNaN(minUncertaintyRelative)) slimDown = 0; // NaNs occur naturally in empty cells
                assert slimDown >= 0: String.format("Min uncertainty %s invalid, slimdown %s raw minUC %s",minUncertainty,slimDown, matrixRegionData.minUncertainty);
                gc.strokeRect(dstPts[0]+slimDown, dstPts[1]-glyphSizePx+slimDown, glyphSizePx - 2*slimDown, glyphSizePx-2*slimDown);

                // max uncertainty
                slimDown = glyphSizePx * (1-Math.sqrt(1-maxUncertaintyRelative)) / 2;
                if(Double.isNaN(maxUncertaintyRelative)) slimDown = 0; // NaNs occur naturally in empty cells
                assert slimDown >= 0: "Max uncertainty "+maxUncertaintyRelative+" invalid, slimdown: "+slimDown;
                gc.strokeRect(dstPts[0]+slimDown, dstPts[1]-glyphSizePx+slimDown, glyphSizePx - 2*slimDown, glyphSizePx-2*slimDown);

            } // end for row

        } // end for column

    }

    /** Computes the number of cells to group horizontally and vertically into a single region to satisfy the minimum glyph size. */
    private int computeGroupSize(CorrelationMatrix matrix, double minGlyphSize) {

        // compute the dimensions of a single cell on screen
        Point2D blockSizeSC = cellToScreen(matrix.metadata).deltaTransform(1, -1);

        // compute the number of cells that cover at least the width and height of one glyph
        int groupSize = (int) Math.ceil(minGlyphSize / blockSizeSC.getX());

        // the group size must be at most min(matrix width, matrix height) to be able to form complete groups
        groupSize = Math.min(groupSize, Math.min(matrix.getSize(), matrix.metadata.getNumberOfDifferentTimeLags()));

        assert groupSize > 0 : String.format("Negative group size %s, width of a single cell: %.5f px", groupSize, blockSizeSC.getX());
        return groupSize;
    }

    /** Defines the domain and the range of the paint scale.
     * @param matrix the matrix to draw the minimum/maximum values for the target statistics from
     * @param paintScale the paintScale to configure
     */
    public void configurePaintscale(CorrelationMatrix matrix, MultiDimensionalPaintScale paintScale) {

        switch(renderMode){
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

    /*
     This handler listens to mouse moves on the correlogram and informs the shared data object about
     the correlation matrix cell index (window index index and lag index) under the mouse cursor.
    */
    private void reportActiveMatrixRegion(MouseEvent t) {

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

        MatrixRegionData newRegion = null;
        if (columnIdx >= 0 && columnIdx < matrix.getSize() &&
                lagIdx >= 0 && lagIdx < matrix.metadata.getNumberOfDifferentTimeLags()) {

            if(uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON_AGGREGATED){
                newRegion = aggregatedCorrelationMatrix.get().getRegion(columnIdx, lagIdx);
            } else {
                newRegion = new MatrixRegionData();
                newRegion.column = columnIdx;
                newRegion.row = lagIdx;
                newRegion.width = 1;
                newRegion.height = 1;
                newRegion.isAggregated = false;
                CorrelationMatrix.CorrelationColumn column = matrix.getColumn(columnIdx);
                if(CorrelationMatrix.isValidStatistic(getCorrelationStatistic())) newRegion.medianCorrelation = column.data[getCorrelationStatistic()][lagIdx];
                if(CorrelationMatrix.isValidStatistic(getUncertaintyStatistic())) newRegion.averageUncertainty = column.data[getUncertaintyStatistic()][lagIdx];
                newRegion.cellDistribution = column.histogram == null ? null : column.histogram.getHistogram(lagIdx);
            }
        }

        sharedData.setActiveCorrelationMatrixRegion(newRegion);

//        // report only if necessary
//        MatrixRegionData currentRegion = sharedData.getActiveCorrelationMatrixRegion();
//        // is null has null -> no update
//        if (currentRegion != null || newRegion != null) {
//            // is null has something -> update
//            if(currentRegion == null) sharedData.setActiveCorrelationMatrixRegion(newRegion);
//                // is something has null -> update (equals fails)
//                // is something has something -> update if necessary (equals fails)
//            else if(!currentRegion.equals(newRegion)) sharedData.setActiveCorrelationMatrixRegion(newRegion);
//        }
    }

    /**
     * Highlights the active cell, i.e. the (time,lag) coordinate in the correlogram that is hovered by the user.
     * This is done by moving a rectangle in the scene graph over the according position.
     */
    void highlightActiveMatrixRegion(ObservableValue<? extends MatrixRegionData> observable, MatrixRegionData oldValue, MatrixRegionData activeRegion){

        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(activeRegion == null || matrix == null) {
            activeWindowRect.setVisible(false);
            return;
        }

        // transform data to screen coordinates
        Affine cellToScreen = cellToScreen(matrix.metadata);
        Bounds boundsScreen = cellToScreen.transform(new BoundingBox(activeRegion.column, activeRegion.row+activeRegion.height-1, activeRegion.width, -activeRegion.height));

        // position rectangle over the window in the correlogram
        activeWindowRect.setLayoutX(boundsScreen.getMinX());
        activeWindowRect.setLayoutY(boundsScreen.getMinY());
        activeWindowRect.setWidth(boundsScreen.getWidth());
        activeWindowRect.setHeight(boundsScreen.getHeight());

        activeWindowRect.setClip(null);
        // clip if rectangle extends outside the plot area
        if( ! chartCanvas.getLayoutBounds().contains(boundsScreen)){
            double cutX = chartCanvas.getLayoutX() - activeWindowRect.getLayoutX();
            double cutY = chartCanvas.getLayoutY() - activeWindowRect.getLayoutY();
            Node currentClip = activeWindowRect.getClip();
            if(currentClip != null){
                double currentCutX = currentClip.getLayoutX();
                double currentCutY = currentClip.getLayoutY();
                if(Math.abs(currentCutX-cutX) > 1e-5 || Math.abs(currentCutY-cutY) > 1e-5)
                    activeWindowRect.setClip(new Rectangle(cutX, cutY,chartCanvas.getWidth(),chartCanvas.getHeight()));
            } else activeWindowRect.setClip(new Rectangle(cutX, cutY, chartCanvas.getWidth(), chartCanvas.getHeight()));
        }

        activeWindowRect.setVisible(true);
    }

    /** Resets the axes such that they fit the matrix bounds. */
    @Override public void resetView() {

        if(sharedData == null || sharedData.getCorrelationMatrix() == null) return;
        CorrelationMatrix m = sharedData.getCorrelationMatrix();

        if(aspectRatioFixed()) fitWithAspectRatioAxesBounds(m);
        else                   clipRegionDC.set(useAllSpaceAxesBounds(m));

    }

    /**
     * Computes the bounds of the x and y axes such that the non-NaN part of the plot fills all available plotting space.
     * @param m the matrix to take the shape from (number of columns and rows)
     * @return min/max values for the x and y axes
     */
    protected Bounds useAllSpaceAxesBounds(CorrelationMatrix m) {
        Affine cellToData = cellToData(m.metadata);
        Point2D minXminY = cellToData.transform(0,0);
        int maxX = m.getLastFilledColumnIndex(getCorrelationStatistic())+1;
        int maxY = m.metadata.getNumberOfDifferentTimeLags();
        Point2D maxXmaxY = cellToData.transform(maxX, maxY);  // x: the last one is getSize()-1 but we want to see it (not its start point to be the upper bound of the axis)
        double xRange = maxXmaxY.getX() - minXminY.getX();
        double yRange = maxXmaxY.getY() - minXminY.getY();
        return new BoundingBox(minXminY.getX(), minXminY.getY(), xRange, yRange);
    }

    /**
     * Computes the bounds of the x and y axes such that the entire correlation matrix can be plotted while keeping the cells quadratic
     * @param m the matrix to take the shape from (number of columns and rows)
     */
    protected void fitWithAspectRatioAxesBounds(CorrelationMatrix m) {

        Bounds fullFitBounds = useAllSpaceAxesBounds(m);
        setClipRegionDC(fullFitBounds);   // start with a full fit
        cellToData(m.metadata);         // recompute block dimensions

        // since blocks are quadratic, the chart will be elongation times wider than high
        double elongation = 1. * m.getSize() / m.metadata.getNumberOfDifferentTimeLags();
        // if the available canvas is not wide enough to plot the entire chart, use the full width as reduce the height accordingly
        boolean fitChartToEntireWidth = elongation >= xAxis.getWidth() / yAxis.getHeight();

        if (fitChartToEntireWidth) adaptYAxis(fullFitBounds, getDataPointsPerPixelRatio());
        else                       adaptXAxis(fullFitBounds);

    }

    /** @param renderMode see {@link Visualization.Correlogram.RENDER_MODE} */
    public void setRenderMode(RENDER_MODE renderMode) {
        this.renderMode = renderMode;
            aggregationPrototype.CORRELATION_DIM = getCorrelationStatistic();
            aggregationPrototype.UNCERTAINTY_DIM = getUncertaintyStatistic();
    }

    public UNCERTAINTY_VISUALIZATION getUncertaintyVisualization() {
        return uncertaintyVisualization;
    }
    /** @param uncertaintyVisualization see {@link Visualization.Correlogram.UNCERTAINTY_VISUALIZATION} */
    public void setUncertaintyVisualization(UNCERTAINTY_VISUALIZATION uncertaintyVisualization) {
        this.uncertaintyVisualization = uncertaintyVisualization;

        // fix the aspect ratio for the hinton visualization method
        if(usingHintonVisualization() && sharedData.getCorrelationMatrix() != null){
            adaptAspectRatioForMatrix(sharedData.getCorrelationMatrix());
            resetView();
        } else {
            // unlock aspect ratio
            setDataPointsPerPixelRatio(Double.NaN);
        }
    }

    /** Whether the uncertainty visualization is aggregated or unaggregated hinton. */
    private boolean usingHintonVisualization() {
        return uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON || uncertaintyVisualization == UNCERTAINTY_VISUALIZATION.HINTON_AGGREGATED;
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