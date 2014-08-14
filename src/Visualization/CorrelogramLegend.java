package Visualization;

import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import com.sun.javafx.tk.FontLoader;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import javafx.util.converter.NumberStringConverter;

import java.awt.*;
import java.util.Locale;

import static Data.Correlation.CorrelationMatrix.*;

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

    /** The representative values rendered in the legend. The third dimension contains the values for the input statistics. */
    private double[/*row*/][/*col*/][/*direction*/] values;

    /** Constants for addressing the two dimensions of the legend. */
    final static int VERTICAL = 0, HORIZONTAL = 1;
    private final static int MINIMUM = 0;
    private final static int MAXIMUM = 1;

    /** Whether to render an overlay scatter plot that shows the distribution of the matrix values. */
    private boolean drawScatterPlot = false;

    /** determines from which data to draw from the correlation matrix.
     * e.g. sourceStatistic[VERTICAL] = {@link Data.Correlation.CorrelationMatrix#STD_DEV}.
     * can be null, if the legend is to be rendered in only one dimension. */
    final Integer[] sourceStatistic = new Integer[2/*vertical, horizontal*/];

    /** These values are shown explicitly in the legend using a small cross mark.
     * Used to make reading the correlogram easier by displaying the mean and std dev under the mouse pointer. */
    private final Double[] highlightValues = new Double[2/*vertical, horizontal*/];

    private final NumberStringConverter legendTipConverter = new NumberStringConverter(Locale.ENGLISH, "#.###");
    private final Font legendTipFont = new Font(10);

    /** The number of colors used to encode the mean/median/etc. dimension in the correlation matrix. */
    private final int horizontalResolution = 13;
    /** The number of colors used to encode the standard deviation/IQR/etc. dimension in the legend. */
    private final int verticalResolution = 4;
    private double xTickUnit, yTickUnit;

    Correlogram.UNCERTAINTY_VISUALIZATION uncertaintyVisualization = Correlogram.DEFAULT_UNCERTAINTY_VISUALIZATION;

    /** The paintscale used to convert multi-dimensional values into colors.
     *  Using the correlograms paintscale is not possible, since when extending the displayed value range (e.g. to center the zero value along a dimension)
     *  additional values (not occurring in the correlogram) occur which then can not be converted to colors by the correlogram's paintscale. */
    private final MultiDimensionalPaintScale paintScale;

    public CorrelogramLegend(Correlogram correlogram, MultiDimensionalPaintScale paintScale){

        this.correlogram = correlogram;
        this.paintScale = paintScale;

        // configure data source and axis labels
        updateRenderMode();

        // the labels are placed manually at the middle of the blocks
        xAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);
        yAxis.setTickPositionType(NumberAxis.TICK_GENERATION_METHOD.MANUAL);

        this.allowPan = false;
        this.allowZoom = false;
        this.allowSelection = false;
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

        // when the use hovers over a correlogram cell, extract the mean and standard deviation of this cell for display in the legend (legendTip)
        sharedData.highlightedCellProperty().addListener((ov, t, t1) -> {
            Point activeCell = (Point) t1;
            // check whether the column exists the
            if(activeCell.x >= 0 && activeCell.x < sharedData.getCorrelationMatrix().getResultItems().size() ){
                CorrelationMatrix.CorrelationColumn activeColumn = sharedData.getCorrelationMatrix().getResultItems().get(activeCell.x);
                // check whether the row exists
                if(activeCell.y >= 0 && activeCell.y < activeColumn.data[MEAN].length){
                    // get data according to the render mode
                    highlightValues[HORIZONTAL] = activeColumn.data[sourceStatistic[HORIZONTAL]][activeCell.y];
                    if(sourceStatistic[VERTICAL] != null)
                        highlightValues[VERTICAL] = activeColumn.data[sourceStatistic[VERTICAL]][activeCell.y];
                    drawContents();
                }
            }
        });

        sharedData.uncertaintyVisualizationProperty().addListener((observable, oldValue, newValue) -> {
            if(this.uncertaintyVisualization != newValue){
                this.uncertaintyVisualization = (Correlogram.UNCERTAINTY_VISUALIZATION) newValue;
                drawContents();
            }
        });


    }

    /**
     * Changes the source statistic and recomputes the value ranges. Should be called whenever the correlogram render mode is changed.
     * The reason why the correlogram doesn't do it, is that correlogams without legend might be o.k. in another context,
     * so the correlogram shouldn't have to know anything about its legend.
     */
    public void updateRenderMode() {

        // update data source
        switch (correlogram.renderMode){
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
        xAxis.setLabel(CorrelationMatrix.statisticsLabels[sourceStatistic[HORIZONTAL]]);
        if(sourceStatistic[VERTICAL] != null){
            yAxis.setLabel(CorrelationMatrix.statisticsLabels[sourceStatistic[VERTICAL]]);
//            yAxis.setVisible(true);   // doesn't work!
        }
        else
//            yAxis.setVisible(false); // doesn't work!
            yAxis.setLabel("");

    }


    void setValueRangeSample(double[][][] valueRangeSample){

        this.values = valueRangeSample;

    }

    /**
     * Adapts the bounds and resolutions of the axes.
     */
    private void adaptAxes(Double horizontalMin, Double horizontalMax, Double verticalMin, Double verticalMax) {

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
            xAxis.setLowerBound(horizontalMin - xAxis.getTickUnit()/2);
            xAxis.setUpperBound(horizontalMax + xAxis.getTickUnit()/2);

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
            yAxis.setLowerBound(verticalMin - yAxis.getTickUnit()/2);
            yAxis.setUpperBound(verticalMax + yAxis.getTickUnit()/2);
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
                if(sourceStatistic[VERTICAL] == null || uncertaintyVisualization == Correlogram.UNCERTAINTY_VISUALIZATION.COLUMN_WIDTH)
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

        drawLegendTip(gc, dataToScreen);
        
        xAxis.drawContents();
        yAxis.drawContents();

    }

    void drawMatrixValuesScatter(GraphicsContext gc, Affine dataToScreen) {

        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
        if(matrix == null) return;

        Integer srcHorizontal = sourceStatistic[HORIZONTAL], srcVertical = sourceStatistic[VERTICAL];
        Integer notNullDirection = sourceStatistic[VERTICAL] == null ? sourceStatistic[HORIZONTAL] : sourceStatistic[VERTICAL];
        assert notNullDirection != null;

        gc.save();
        gc.setFill(Color.BLACK.deriveColor(0,1,1,0.8));
//        gc.setFill(Color.BLACK);
        for (CorrelationMatrix.CorrelationColumn column : matrix.getResultItems()){
            for (int lag = 0; lag < column.data[notNullDirection].length; lag++) {
                double valX = srcHorizontal == null ? 0 : column.data[srcHorizontal][lag],
                       valY = srcVertical   == null ? 0 : column.data[srcVertical][lag] ;
                Point2D screen = dataToScreen.transform(valX, valY);
                gc.fillRect(screen.getX(), screen.getY(), 1, 1 );
            }
        }
        gc.restore();
    }

    /**
     * Highlights the position of a certain mean and standard deviation by drawing a cross and rendering the values as text.
     * @param gc canvas to draw on
     * @param dataToScreen transformation between legend (mean, std dev) and screen coordinates
     */
    private void drawLegendTip(GraphicsContext gc, Affine dataToScreen) {

        // cross size in pixels
        double crossSize = 5;

        // handle no highlight value: return
        if(highlightValues[VERTICAL] == null || highlightValues[HORIZONTAL] == null) return;

        String horizontalValueString = Double.isNaN(highlightValues[HORIZONTAL]) ? "Not a Number" : legendTipConverter.toString(highlightValues[HORIZONTAL]);
        String verticalValueString = Double.isNaN(highlightValues[VERTICAL]) ? "Not a Number" : legendTipConverter.toString(highlightValues[VERTICAL]);

        // handle NaN: position label in center of legend
        highlightValues[HORIZONTAL] = Double.isNaN(highlightValues[HORIZONTAL]) ? 0 : highlightValues[HORIZONTAL];
        highlightValues[VERTICAL]   = Double.isNaN(highlightValues[VERTICAL]) ? 0 : highlightValues[VERTICAL];

        // format values
        String label;
        if(sourceStatistic[VERTICAL] != null)
            label = String.format("%s = %s, %s = %s", statisticsLabels[sourceStatistic[HORIZONTAL]], horizontalValueString, statisticsLabels[sourceStatistic[VERTICAL]], verticalValueString);
        else
            label = String.format("%s = %s", statisticsLabels[sourceStatistic[HORIZONTAL]], horizontalValueString);

        // position label relative to the crosshair (left/right, top/bottom) where the most space is available
        Point2D labelSize = renderedTextSize(label, legendTipFont);
        Point2D sc = dataToScreen.transform(highlightValues[HORIZONTAL], highlightValues[VERTICAL]);
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

        Double[][] ranges = getValueRanges(matrix);

        double[] horizontalValueExtrema = new double[2]; // MINIMUM and MAXIMUM
        double horizontalValueRange = 0, verticalValueRange = 0;

        switch (correlogram.renderMode){
            case MEAN_STD_DEV:
            case MEDIAN_IQR:
                // center horizontal zero value at the middle of the axis
                double horizontalRangeAbsMax = Math.max(Math.abs(ranges[HORIZONTAL][MINIMUM]), Math.abs(ranges[HORIZONTAL][MAXIMUM]));
                horizontalValueExtrema = new double[]{-horizontalRangeAbsMax, horizontalRangeAbsMax};
                break;
            case NEGATIVE_SIGNIFICANT:
            case POSITIVE_SIGNIFICANT:
            case ABSOLUTE_SIGNIFICANT:
                horizontalValueExtrema = new double[]{0, 1};
        }
        verticalValueRange = ranges[VERTICAL] != null ? ranges[VERTICAL][MAXIMUM] - ranges[VERTICAL][MINIMUM] : 0;
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
                
                sample[rowIdx][colIdx][HORIZONTAL] = horizontalValueExtrema[0] + colIdx*horizontalStep;
                sample[rowIdx][colIdx][VERTICAL] = ranges[VERTICAL] != null ? ranges[VERTICAL][MINIMUM] + rowIdx*verticalStep : 0;

            }
        }
//        System.out.println("CorrelogramLegend.valueRangeSample");
//        for (int row = 0; row < sample.length; row++) {
//            for (int col = 0; col < sample[0].length; col++)
//                System.out.print(String.format("(%s, %s)", sample[row][col][CorrelogramLegend.HORIZONTAL], sample[row][col][CorrelogramLegend.VERTICAL]));
//            System.out.println();
//        }
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
     * determines the range of values that the legend covers.
     * @return the extreme values in each dimension. the first dimension refers to the direction, index with {@link #VERTICAL}, {@link #HORIZONTAL}.
     * The second dimension refers to minimum maximum, index with {@link #MINIMUM}, {@link #MAXIMUM}.
     */
    Double[/*vertical, horizontal*/][/*min, max*/] getValueRanges(CorrelationMatrix m) {

        /** these values describe the extreme values the legend covers. */

        Double[][] ranges = new Double[2][2];

        if(sourceStatistic[VERTICAL] == null)
            ranges[VERTICAL] = null;

        ranges[HORIZONTAL][MINIMUM] = m.getMin(sourceStatistic[HORIZONTAL]);
        ranges[HORIZONTAL][MAXIMUM] = m.getMax(sourceStatistic[HORIZONTAL]);

        if(sourceStatistic[VERTICAL] != null){
            ranges[VERTICAL][MINIMUM] = m.getMin(sourceStatistic[VERTICAL]);
            ranges[VERTICAL][MAXIMUM] = m.getMax(sourceStatistic[VERTICAL]);
        }

        switch (correlogram.renderMode){
            case MEAN_STD_DEV:
            case MEDIAN_IQR:
                // center horizontal zero value at the middle of the axis
                double horizontalRangeAbsMax = Math.max(Math.abs(ranges[HORIZONTAL][MINIMUM]), Math.abs(ranges[HORIZONTAL][MAXIMUM]));
                ranges[HORIZONTAL][MINIMUM] = -horizontalRangeAbsMax;
                ranges[HORIZONTAL][MAXIMUM] = horizontalRangeAbsMax;
                break;
            case NEGATIVE_SIGNIFICANT:
            case POSITIVE_SIGNIFICANT:
            case ABSOLUTE_SIGNIFICANT:
                ranges[HORIZONTAL][MINIMUM] = 0.;
                ranges[HORIZONTAL][MAXIMUM] = 1.;
                if(sourceStatistic[VERTICAL] != null){
                    ranges[VERTICAL][MINIMUM] = null;
                    ranges[VERTICAL][MAXIMUM] = null;
                }
                break;
            default:
                assert false : String.format("Render mode %s not supported.", correlogram.renderMode);

        }

        return ranges;
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