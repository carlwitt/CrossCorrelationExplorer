package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Gui.CorrelogramController;
import com.sun.javafx.tk.FontLoader;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import javafx.util.converter.NumberStringConverter;

import java.util.Locale;

import static Data.Correlation.CorrelationMatrix.*;
import static Data.Statistics.AggregatedCorrelationMatrix.MatrixRegionData;
import static Visualization.Correlogram.UNCERTAINTY_VISUALIZATION;

/**
 * Draws the legend for the correlogram by taking the extrema in its dimensions (mean/std dev, median/iqr, etc.)
 * and assigning equal-sized intervals a color.
 *
 * Support a cross hair that highlights the exact values the user points to in the correlogram.
 *
 * @author Carl Witt
 */
public class CorrelogramLegend extends CanvasChart {

    private SharedData sharedData;

    /** The correlogram for which this is the legend. */
    private final Correlogram correlogram;

    /** The sampled values (value range sample) rendered in the legend.
     * The first dimensions refers to the row, the second dimension to the col and
     * the third dimension contains the values for the input statistics. */
    private double[][][] values;

    /** Constants for addressing the two dimensions of the legend. */
    final static int VERTICAL = 0, HORIZONTAL = 1;

    /** Whether to render an overlay scatter plot that shows the distribution of the matrix values. */
    private boolean drawScatterPlot = false;

    /** determines from which data to draw from the correlation matrix.
     * e.g. sourceStatistic[VERTICAL] = {@link Data.Correlation.CorrelationMatrix#STD_DEV}.
     * can be null, if the legend is to be rendered in only one dimension. */
    final Integer[] sourceStatistic = new Integer[2/*vertical, horizontal*/];

    private final NumberStringConverter legendTipConverter = new NumberStringConverter(Locale.ENGLISH, "#.###");
    private final Font legendTipFont = new Font(10);

    /** The number of colors used to encode the mean/median/etc. dimension in the correlation matrix. */
    private final int horizontalResolution = 13;
    /** The number of colors used to encode the standard deviation/IQR/etc. dimension in the legend. */
    private final int verticalResolution = 4;
    private double xTickUnit, yTickUnit;

    UNCERTAINTY_VISUALIZATION uncertaintyVisualization = Correlogram.DEFAULT_UNCERTAINTY_VISUALIZATION;

    /** The paintscale used to convert multi-dimensional values into colors.
     *  Using the correlograms paintscale is not possible, since when extending the displayed value range (e.g. to center the zero value along a dimension)
     *  additional values (not occurring in the correlogram) occur which then can not be converted to colors by the correlogram's paintscale. */
    private final MultiDimensionalPaintScale paintScale;

    public CorrelogramLegend(Correlogram correlogram, MultiDimensionalPaintScale paintScale){

        this.correlogram = correlogram;
        this.paintScale = paintScale;

        // configure data source and axis labels
        updateRenderMode(correlogram.renderMode);

        // the labels are placed manually at the middle of the blocks
        xAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);
        yAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);

        this.allowPan = false;
        this.allowZoom = false;
        this.allowSelection = false;

        // configure min width and height
        setMinHeight(10); setMinWidth(10);
        // the correlogram is kept large enough by the container constraints, but setting the preferred width to
        // the computed size can cause the correlogram to get stuck on a width that's too large for the container
        // (because there's no inherent way to compute the necessary space for a canvas).
        setPrefWidth(10); setPrefHeight(10);
    }

    public void setSharedData(final SharedData sharedData){

        this.sharedData = sharedData;

        // render the legend if the correlogram has changed
        sharedData.correlationMatrixProperty().addListener((ov, t, newMatrix) -> {
            assert sourceStatistic[HORIZONTAL] != null : "source statistic horizontal not set";
            // Computes a sample from the full range of the current correlation matrix (its correlation means and standard deviations).
            setValueRangeSample(valueRangeSample(newMatrix, horizontalResolution, verticalResolution));
            resetView();
            drawContents();
        });

        sharedData.matrixFilterRangesProperty().addListener((observable, oldValue, newValue) -> drawContents());

        // when the use hovers over a correlogram cell, extract the mean and standard deviation of this cell for display in the legend (legendTip)
        sharedData.activeCorrelationMatrixRegionProperty().addListener((observable, oldValue, activeRegion) -> {
            if(activeRegion == null) return;
            drawContents();
        });

        sharedData.uncertaintyVisualizationProperty().addListener((observable, oldValue, newValue) -> {
            if(this.uncertaintyVisualization != newValue){
                this.uncertaintyVisualization = newValue;
                drawContents();
            }
        });


    }

    /**
     * Changes the source statistic and recomputes the value ranges. Should be called whenever the correlogram render mode is changed.
     * The reason why the correlogram doesn't do it, is that correlogams without legend might be o.k. in another context,
     * so the correlogram shouldn't have to know anything about its legend.
     */
    public void updateRenderMode(Correlogram.RENDER_MODE renderMode) {

        // update data source
        switch (renderMode){
            case MEAN_STD_DEV:
                sourceStatistic[HORIZONTAL] = MEAN;
                sourceStatistic[VERTICAL] = STD_DEV;
                break;
            case MEDIAN_IQR:
                sourceStatistic[HORIZONTAL] = MEDIAN;
                sourceStatistic[VERTICAL] = IQR;
                break;
            case NEGATIVE_SIGNIFICANT:
                sourceStatistic[HORIZONTAL] = NEGATIVE_SIGNIFICANT;
                sourceStatistic[VERTICAL] = null;
                break;
            case POSITIVE_SIGNIFICANT:
                sourceStatistic[HORIZONTAL] = POSITIVE_SIGNIFICANT;
                sourceStatistic[VERTICAL] = null;
                break;
            case ABSOLUTE_SIGNIFICANT:
                sourceStatistic[HORIZONTAL] = ABSOLUTE_SIGNIFICANT;
                sourceStatistic[VERTICAL] = null;
                break;
        }

        if(sharedData != null && sharedData.getCorrelationMatrix() != null){
            setValueRangeSample(valueRangeSample(sharedData.getCorrelationMatrix(), horizontalResolution, verticalResolution));
            correlogram.configurePaintscale(sharedData.getCorrelationMatrix(), paintScale);
        }

        // update axis labels
        String xAxisLabel = CorrelogramController.statisticsLabels[sourceStatistic[HORIZONTAL]];
        // if the data source involves significance, add the significance level to the axis label
        if(sharedData != null
            && sharedData.getCorrelationMatrix() != null
            && (sourceStatistic[HORIZONTAL]==CorrelationMatrix.NEGATIVE_SIGNIFICANT
                    || sourceStatistic[HORIZONTAL]==CorrelationMatrix.ABSOLUTE_SIGNIFICANT
                    || sourceStatistic[HORIZONTAL]==CorrelationMatrix.POSITIVE_SIGNIFICANT))
            xAxisLabel += String.format(" (p = %s)", CorrelationMatrix.getSignificanceLevel(sharedData.getCorrelationMatrix().metadata));
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel(sourceStatistic[VERTICAL] == null ? "" : CorrelogramController.statisticsLabels[sourceStatistic[VERTICAL]]);

    }


    void setValueRangeSample(double[][][] valueRangeSample){

        this.values = valueRangeSample;

    }

    /**
     * Adapts the bounds and resolutions of the axes.
     */
    private void adaptAxes(Double horizontalMin, Double horizontalMax, Double verticalMin, Double verticalMax) {

        assert Double.isFinite(horizontalMin) && Double.isFinite(horizontalMax) : String.format("horizontalMin: %s, horizontalMax: %s", horizontalMin, horizontalMax);
        assert Double.isFinite(verticalMin) && Double.isFinite(verticalMax) : String.format("verticalMin: %s, verticalMax: %s", verticalMin, verticalMax);

        paintScale.setLowerBounds(horizontalMin, verticalMin);
        paintScale.setUpperBounds(horizontalMax, verticalMax);

        if(horizontalMin == null || horizontalMax == null){
            xAxis.setVisible(false);
        } else {
            xAxis.setVisible(true);
            // handle special case of only one value along the horizontal axis
            int numCols = values[0].length; // number of columns
            xTickUnit = numCols > 1 ? (horizontalMax-horizontalMin)/(numCols-1) : 1;

            xAxis.setTickOrigin(horizontalMin); // first row, first entry is min value in horizontal dimension
            xAxis.setTickUnit(xTickUnit);

            // the visible range is different from where the ticks are placed
            double lowerBound = horizontalMin - xAxis.getTickUnit() / 2;
            double upperBound = horizontalMax + xAxis.getTickUnit() / 2;
            xAxis.setAxisBoundsDC(new BoundingBox(lowerBound, 0, upperBound-lowerBound, 0));

        }

        if(verticalMin == null || verticalMax == null){
            yAxis.setVisible(false);
        } else {
            yAxis.setVisible(true);

            // handle special case of only one value along the vertical axis
            int numRows = values.length;
            yTickUnit = numRows > 1 ? (verticalMax-verticalMin)/(numRows-1) : 1;
            yAxis.setTickOrigin(verticalMin);
            yAxis.setTickUnit(yTickUnit);

            // the visible range is different from where the ticks are placed
            double lowerBound = verticalMin - yAxis.getTickUnit() / 2;
            double upperBound = verticalMax + yAxis.getTickUnit() / 2;
            yAxis.setAxisBoundsDC(new BoundingBox(0, lowerBound, 0, upperBound-lowerBound));
        }

    }


    /**
     * Draws the legend and the "tooltip"/cross hair with the value the user currently points to in the correlogram.
     */
    @Override public void drawContents() {

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        
        Affine dataToScreen = dataToScreen();

        if (values == null) return;

        double blockWidth  = xTickUnit;
        double blockHeight = yTickUnit;
        double startY = getMin(VERTICAL) + blockHeight/2;

        // for each column of the matrix (or, equivalently, for each distinct mean value)
        for (int rowIdx = 0; rowIdx < values.length; rowIdx++) {

            double minY = rowIdx*blockHeight + startY;

            Point2D ulc, brc; // upper left corner, bottom right corner of the cell
            for (int colIdx = 0; colIdx < values[rowIdx].length; colIdx++) {

                double minX = getMin(HORIZONTAL) + colIdx*blockWidth - blockWidth/2;
                ulc = dataToScreen.transform(minX, minY);
                brc = dataToScreen.transform(minX + blockWidth, minY + blockHeight);

//                System.out.println(String.format("row %s col %s value X %s value Y %s\nulc %s brc %s", rowIdx, colIdx, values[rowIdx][colIdx][HORIZONTAL], values[rowIdx][colIdx][VERTICAL], ulc, brc));

                // draw rectangle
                Paint paint;
                // if there's no source statistic for the vertical axis defined or if uncertainty is encoded via column width, use one dimensional color palette.
                if(sourceStatistic[VERTICAL] == null || uncertaintyVisualization != UNCERTAINTY_VISUALIZATION.COLOR)
                    paint = paintScale.getPaint(
                        values[rowIdx][colIdx][HORIZONTAL]
                    );
                else
                    paint = paintScale.getPaint(
                        values[rowIdx][colIdx][HORIZONTAL],
                        values[rowIdx][colIdx][VERTICAL]
                    );
                gc.setFill(paint);  gc.setStroke(paint);
                gc.fillRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());
                gc.strokeRect(ulc.getX(), ulc.getY(), brc.getX() - ulc.getX(), ulc.getY() - brc.getY());

            }
        }

        if(drawScatterPlot) drawMatrixValuesScatter(gc, dataToScreen);

        if(correlogram.getUncertaintyVisualization() == UNCERTAINTY_VISUALIZATION.HINTON_AGGREGATED){
            drawLegendTipAggregated(gc, dataToScreen);
        } else {
            drawLegendTipUnaggregated(gc, dataToScreen);
        }


        
        xAxis.drawContents();
        yAxis.drawContents();

    }

    void drawMatrixValuesScatter(GraphicsContext gc, Affine dataToScreen) {

        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(matrix == null) return;

        Integer srcHorizontal = sourceStatistic[HORIZONTAL], srcVertical = sourceStatistic[VERTICAL];
        Integer notNullDirection = sourceStatistic[VERTICAL] == null ? sourceStatistic[HORIZONTAL] : sourceStatistic[VERTICAL];
        assert notNullDirection != null;

        long timeOut = 1000; // stop drawing after one second
        long started = System.currentTimeMillis();

        gc.save();
        gc.setFill(Color.BLACK.deriveColor(0,1,1,0.5));
        for (CorrelationMatrix.CorrelationColumn column : matrix.getColumns()){
            for (int lag = 0; lag < column.data[notNullDirection].length; lag++) {
                double valX = srcHorizontal == null ? 0 : column.data[srcHorizontal][lag],
                       valY = srcVertical   == null ? 0 : column.data[srcVertical][lag] ;
                Point2D screen = dataToScreen.transform(valX, valY);
                gc.fillRect(screen.getX(), screen.getY(), 1, 1);
            }
            long elapsedTime = System.currentTimeMillis() - started;
            if(elapsedTime > timeOut) {
                System.out.println(String.format("Aborting scatter plot draw. Elapsed time: %s (time out set to %s ms)", elapsedTime, timeOut));
                break;
            }
        }
        gc.restore();
    }

    /**
     * Highlights the position of a certain mean and standard deviation by drawing a cross and rendering the values as text.
     * @param gc canvas to draw on
     * @param dataToScreen transformation between legend (mean, std dev) and screen coordinates
     */
    private void drawLegendTipUnaggregated(GraphicsContext gc, Affine dataToScreen) {

        // cross size in pixels
        double crossSize = 5;

        MatrixRegionData matrixRegionData = sharedData.getActiveCorrelationMatrixRegion();

        // handle no data: return
        if(matrixRegionData == null) return;

        double horizontalValue = matrixRegionData.medianCorrelation;
        double verticalValue = matrixRegionData.averageUncertainty;

        String horizontalValueString = Double.isNaN(horizontalValue) ? "Not a Number" : legendTipConverter.toString(horizontalValue);
        String verticalValueString = Double.isNaN(verticalValue) ? "Not a Number" : legendTipConverter.toString(verticalValue);

        // handle NaN: position label in center of legend
        horizontalValue = Double.isNaN(horizontalValue) ? 0 : horizontalValue;
        verticalValue   = Double.isNaN(verticalValue) ? 0 : verticalValue;

        // format values
        String label;
        if(sourceStatistic[VERTICAL] != null)
            label = String.format("%s = %s, %s = %s", CorrelogramController.statisticsLabels[sourceStatistic[HORIZONTAL]], horizontalValueString, CorrelogramController.statisticsLabels[sourceStatistic[VERTICAL]], verticalValueString);
        else
            label = String.format("%s = %s", CorrelogramController.statisticsLabels[sourceStatistic[HORIZONTAL]], horizontalValueString);

        // position label relative to the crosshair (left/right, top/bottom) where the most space is available
        Point2D labelSize = renderedTextSize(label, legendTipFont);
        Point2D sc = dataToScreen.transform(horizontalValue, verticalValue);
        double xOffset = sc.getX() < getWidth()/2 ?
                crossSize :                         // right
                - crossSize - labelSize.getX();     // left
        double yOffset = sc.getY() < getHeight()/2 ?
                crossSize + labelSize.getY() :      // bottom
                - crossSize;                        // top

        // prepare drawing: push current graphics context properties on a stack and set properties for legend drawing
        gc.save();
        gc.setFont(legendTipFont);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);

        // draw crosshair
        gc.strokeLine(sc.getX()-crossSize, sc.getY(), sc.getX()+crossSize, sc.getY());
        gc.strokeLine(sc.getX(), sc.getY()-crossSize, sc.getX(), sc.getY()+crossSize);

        // draw label
        gc.setStroke(Color.WHITE); gc.setLineWidth(2);
        gc.strokeText(label, sc.getX()+xOffset, sc.getY()+yOffset);
        gc.setStroke(Color.BLACK); gc.setLineWidth(1);
        gc.strokeText(label, sc.getX()+xOffset, sc.getY()+yOffset);
        gc.restore();
    }

    private void drawLegendTipAggregated(GraphicsContext gc, Affine dataToScreen) {

        MatrixRegionData matrixRegionData = sharedData.getActiveCorrelationMatrixRegion();

        // handle no data: return
        if(matrixRegionData == null) return;

        // boxplot height/width
        double whiskerSize = 25;    // pixels

        double yValue = yAxis.getLowerBound();
        double[] horizontalValues = new double[]{
            matrixRegionData.minCorrelation, yValue,
            matrixRegionData.firstQuartileCorrelation, yValue,
            matrixRegionData.medianCorrelation, yValue,
            matrixRegionData.thirdQuartileCorrelation, yValue,
            matrixRegionData.maxCorrelation, yValue
        };
        double xValue = xAxis.getLowerBound();
        double[] verticalValues = new double[]{
            xValue, matrixRegionData.minUncertainty,
            xValue, matrixRegionData.averageUncertainty,
            xValue, matrixRegionData.maxUncertainty
        };

        // position label relative to the crosshair (left/right, top/bottom) where the most space is available
        dataToScreen.transform2DPoints(horizontalValues, 0, horizontalValues, 0, horizontalValues.length/2);
        dataToScreen.transform2DPoints(verticalValues, 0, verticalValues, 0, verticalValues.length/2);

        // prepare drawing: push current graphics context properties on a stack and set properties for legend drawing
        gc.save();
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1);

        // -- box plot for correlation / horizontal dimension
        // min and max whisker
        gc.strokeLine(horizontalValues[0], horizontalValues[1]-whiskerSize, horizontalValues[0], horizontalValues[1]);
        gc.strokeLine(horizontalValues[8], horizontalValues[9]-whiskerSize, horizontalValues[8], horizontalValues[9]);
        // median line
        gc.strokeLine(horizontalValues[4], horizontalValues[5]-whiskerSize, horizontalValues[4], horizontalValues[5]);
        // box
        gc.strokeRect(horizontalValues[2], horizontalValues[3]-whiskerSize, horizontalValues[6]-horizontalValues[2], whiskerSize);
        // connectors
        gc.strokeLine(horizontalValues[0], horizontalValues[1]-whiskerSize/2, horizontalValues[2], horizontalValues[3]-whiskerSize/2);
        gc.strokeLine(horizontalValues[6], horizontalValues[7]-whiskerSize/2, horizontalValues[8], horizontalValues[9]-whiskerSize/2);

        // -- "box plot" for uncertainty / vertical dimension
        // min and max whisker
        gc.strokeLine(verticalValues[0], verticalValues[1], verticalValues[0]+whiskerSize, verticalValues[1]);
        gc.strokeLine(verticalValues[4], verticalValues[5], verticalValues[4]+whiskerSize, verticalValues[5]);
        // median line
        gc.strokeLine(verticalValues[2], verticalValues[3], verticalValues[2]+whiskerSize, verticalValues[3]);
        //connector
        gc.strokeLine(verticalValues[0]+whiskerSize/2, verticalValues[1], verticalValues[4]+whiskerSize/2, verticalValues[5]);

        // -- actual cell values / horizontal + vertical dimension
        double[] pointCoordinates = new double[2];
        double[][] matrixFilterRanges = sharedData.getMatrixFilterRanges();
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        for (int i = matrixRegionData.column; i < matrixRegionData.column+matrixRegionData.croppedWidth; i++) {
            CorrelationMatrix.CorrelationColumn column = matrix.getColumns().get(i);
            plotCell:
            for (int j = matrixRegionData.row; j < matrixRegionData.row+matrixRegionData.croppedHeight; j++) {

                for (int STAT = 0; STAT < NUM_STATS; STAT++) {
                    if(matrixFilterRanges[STAT] == null) continue;
                    if(column.data[STAT][j] < matrixFilterRanges[STAT][0] ||
                            column.data[STAT][j] > matrixFilterRanges[STAT][1])
                        continue plotCell; // filtered cell isn't plotted
                }

                pointCoordinates[0] = CorrelationMatrix.isValidStatistic(matrixRegionData.CORRELATION_DIM) ? column.data[matrixRegionData.CORRELATION_DIM][j] : 0;
                pointCoordinates[1] = CorrelationMatrix.isValidStatistic(matrixRegionData.UNCERTAINTY_DIM) ? column.data[matrixRegionData.UNCERTAINTY_DIM][j] : 0;
                dataToScreen.transform2DPoints(pointCoordinates, 0, pointCoordinates, 0, 1);
                gc.strokeRect(pointCoordinates[0]-0.5, pointCoordinates[1]-0.5, 1, 1);
            }
        }

        gc.restore();
    }

    /**
     * Computes evenly spaced samples of the given statistics value ranges of the current correlation matrix.
     * @param requestedHorizontalResolution number of samples taken from the value range of the first dimension (correlation mean). Must be at least two.
     * @param requestedVerticalResolution number of samples taken from the value range of the second dimension (correlation standard deviation). Must be at least two.
     * @return the first dimension refers to rows (row 0 contains the lowest values of the vertical dimension). the second dimension refers to columns. the third dimension corresponds to the two values that
     * are sampled (e.g. mean and standard deviation or median and IQR).
     * The resulting array might have a lower resolution, if there's only a single value along one dimension, or null if there is no current correlation matrix.
     */
    protected double[][][] valueRangeSample(CorrelationMatrix matrix, int requestedHorizontalResolution, int requestedVerticalResolution){

        assert requestedHorizontalResolution >= 2 : "for drawing the legend, the horizontal value/color resolution should be at least two.";
        assert requestedVerticalResolution   >= 2 : "for drawing the legend, the vertical value/color resolution should be at least two.";

        if(matrix == null) return null;

        Bounds ranges = getValueRanges(matrix);

        double[] horizontalValueExtrema = new double[2]; // MINIMUM and MAXIMUM
        double horizontalValueRange = 0, verticalValueRange = 0;

        switch (correlogram.renderMode){
            case MEAN_STD_DEV:
            case MEDIAN_IQR:
                // center horizontal zero value at the middle of the axis
                double horizontalRangeAbsMax = Math.max(Math.abs(ranges.getMinX()), Math.abs(ranges.getMaxX()));
                horizontalValueExtrema = new double[]{-horizontalRangeAbsMax, horizontalRangeAbsMax};
                break;
            case NEGATIVE_SIGNIFICANT:
            case POSITIVE_SIGNIFICANT:
            case ABSOLUTE_SIGNIFICANT:
                horizontalValueExtrema = new double[]{0, 1};
        }
        verticalValueRange = !Double.isNaN(ranges.getHeight()) ? ranges.getHeight() : 0;
        horizontalValueRange = horizontalValueExtrema[1] - horizontalValueExtrema[0];

        // if the lower bound equals the upper bound, use only one return value in that dimension
        int horizontalResolution = horizontalValueRange < 1e-10 ? 1 : requestedHorizontalResolution;
        int verticalResolution = verticalValueRange < 1e-10 ? 1 : requestedVerticalResolution;

        // return value
        double[][][] sample = new double[verticalResolution][horizontalResolution][2/*vertical,horizontal*/];

        // increments of the values in each step. resolution is taken -1 because N requested values give N-1 intervals
        double horizontalStep = horizontalResolution > 1 ? horizontalValueRange /(horizontalResolution-1) : 0,
               verticalStep = verticalResolution > 1 ? verticalValueRange /(verticalResolution-1) : 0;
        
        // put all combinations of sample values along the two dimensions in a matrix
        for (int colIdx = 0; colIdx < horizontalResolution; colIdx++) {
            
            for (int rowIdx = 0; rowIdx < verticalResolution; rowIdx++) {
                
                sample[rowIdx][colIdx][HORIZONTAL] = Double.isNaN(horizontalValueExtrema[0]) ? 0 : horizontalValueExtrema[0] + colIdx*horizontalStep;
                sample[rowIdx][colIdx][VERTICAL] = Double.isNaN(ranges.getHeight()) ? 0 : ranges.getMinY() + rowIdx * verticalStep;

                assert ! Double.isNaN(sample[rowIdx][colIdx][HORIZONTAL]) && ! Double.isNaN(sample[rowIdx][colIdx][VERTICAL]) : String.format("sample[rowIdx][colIdx][HORIZONTAL]: %s sample[rowIdx][colIdx][VERTICAL]: %s", sample[rowIdx][colIdx][HORIZONTAL], sample[rowIdx][colIdx][VERTICAL]);
            }
        }

        return sample;
    }

    /**
     * @param DIRECTION either {@link #HORIZONTAL} or {@link #VERTICAL}.
     * @return the minimum value of the source statistic assigned to the given direction.
     */
    double getMin(int DIRECTION){
        assert values != null : "No value range sample set.";
        return values[0][0][DIRECTION];
    }
    /**
     * @param DIRECTION either {@link #HORIZONTAL} or {@link #VERTICAL}.
     * @return the maximum value of the source statistic assigned to the given direction.
     */
    double getMax(int DIRECTION){
        assert values != null : "No value range sample set.";
        int numRows = values.length;
        int numCols = values[0].length;
        return values[numRows-1][numCols-1][DIRECTION];
    }


    /**
     * Resets the axes such that they fit the matrix bounds.
     */
    public void resetView() {

//        paintScale.setLowerBounds(ranges[HORIZONTAL][MINIMUM], ranges[VERTICAL][MINIMUM]);
//        paintScale.setUpperBounds(ranges[HORIZONTAL][MAXIMUM], ranges[VERTICAL][MAXIMUM]);
//        adaptAxes(ranges[HORIZONTAL][MINIMUM], ranges[HORIZONTAL][MAXIMUM], ranges[VERTICAL][MINIMUM], ranges[VERTICAL][MAXIMUM]);

        // value range sample should be recomputed at this point
        paintScale.setLowerBounds(getMin(HORIZONTAL), getMin(VERTICAL));
        paintScale.setUpperBounds(getMax(HORIZONTAL), getMax(VERTICAL));
        adaptAxes(getMin(HORIZONTAL), getMax(HORIZONTAL), getMin(VERTICAL), getMax(VERTICAL));

    }

    /**
     * Determines the range of values that the legend must cover.
     * @return the bounds of the values in the two source dimensions.
     */
    Bounds getValueRanges(CorrelationMatrix m) {

        /** these values describe the extreme values the legend covers. */
        double minX = Double.NaN, maxX = Double.NaN;
        double minY = Double.NaN, maxY = Double.NaN;

        if(sourceStatistic[HORIZONTAL] != null){
            minX = m.getMin(sourceStatistic[HORIZONTAL]);
            maxX = m.getMax(sourceStatistic[HORIZONTAL]);
        }
        if(sourceStatistic[VERTICAL] != null){
            minY = m.getMin(sourceStatistic[VERTICAL]);
            maxY = m.getMax(sourceStatistic[VERTICAL]);
        }

        switch (correlogram.renderMode){
            case MEAN_STD_DEV:
            case MEDIAN_IQR:
                // center horizontal zero value at the middle of the axis
                double horizontalRangeAbsMax = Math.max(Math.abs(minX), Math.abs(maxX));
                minX = -horizontalRangeAbsMax;
                maxX = horizontalRangeAbsMax;
                break;
            case NEGATIVE_SIGNIFICANT:
            case POSITIVE_SIGNIFICANT:
            case ABSOLUTE_SIGNIFICANT:
                minX = 0.;
                maxX = 1.;
                if(sourceStatistic[VERTICAL] != null){
                    minY = Double.NaN;
                    maxY = Double.NaN;
                }
                break;
            default:
                assert false : String.format("Render mode %s not supported.", correlogram.renderMode);

        }

        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    private final static FontLoader fontLoader = com.sun.javafx.tk.Toolkit.getToolkit().getFontLoader();
    /**
     * Computes the width and height of a string. Used to align tick labels.
     * @param string The string to draw.
     * @param font The font name and font size in which the string is drawn.
     * @return The width (x component) and height (y component) of the string if plotted.
     */
    Point2D renderedTextSize(String string, Font font){
        return new Point2D(fontLoader.computeStringWidth(string, font),fontLoader.getFontMetrics(font).getLineHeight());
    }

    public void setDrawScatterPlot(boolean drawScatterPlot) {
        this.drawScatterPlot = drawScatterPlot;
    }
    
}