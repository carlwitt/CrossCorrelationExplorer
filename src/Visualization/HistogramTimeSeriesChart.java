package Visualization;

import Data.DataModel;
import Data.SharedData;
import Data.Statistics.AggregatedCorrelationMatrix;
import Data.TimeSeriesAverager;
import Data.Windowing.WindowMetadata;
import Global.Util;
import Gui.TimeSeriesViewController;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import org.apache.commons.lang.ArrayUtils;

/**
 * Used to draw the time series. Better aggregation by averaging each n-th data point if there are more data points to render than pixels available.
 * @author Carl Witt
 */
public class HistogramTimeSeriesChart extends TimeSeriesChart {

    private DoubleProperty binSize = new SimpleDoubleProperty(0.1);
    public double getBinSize(){return binSize.get();}
    public void setBinSize(double newBinSize){
        sharedData.experiment.dataModel.correlationSetAAggregator.setBinSize(newBinSize);
        sharedData.experiment.dataModel.correlationSetBAggregator.setBinSize(newBinSize);
        binSize.set(newBinSize);
    }
    public DoubleProperty binSizeProperty(){return binSize;}

    public boolean useLinearTransfer = true;
    public boolean drawPoly = false, drawGrid = true;

    public HistogramTimeSeriesChart(){
        super();
    }

    @Override public void setSharedData(SharedData sharedData){
        super.setSharedData(sharedData);
        setBinSize(0.1);
    }

    @Override public void drawContents() {

        if(isDeferringDrawRequests){
            redrawPending = true;
            return;
        }

        if(sharedData == null || sharedData.experiment == null) return;

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        AggregatedCorrelationMatrix.MatrixRegionData activeRegion = sharedData.getActiveCorrelationMatrixRegion();
        boolean visualizeInputWindows = activeRegion != null && !activeRegion.isAggregated && ! Double.isNaN(activeRegion.medianCorrelation);

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.setFill( visualizeInputWindows ? Color.gray(0.96) : Color.WHITE);
        gc.fillRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);

        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();

        DataModel dataModel = sharedData.experiment.dataModel;
        TimeSeriesAverager[] aggregators = new TimeSeriesAverager[]{dataModel.correlationSetAAggregator, dataModel.correlationSetBAggregator};

        // determine bin size from screen space
        int numDataPointsInRange = dataModel.getNumDataPointsInRange(0, xAxis.getLowerBound(), xAxis.getUpperBound());
        int optimalBinSize = (int) Math.max(1, Math.ceil(5. * numDataPointsInRange / getWidth()));
        aggregators[0].setGroupSize(optimalBinSize);
        aggregators[1].setGroupSize(optimalBinSize);

        Color[] ensembleColors = TimeSeriesViewController.ensembleColors;

        for (int ensembleID = 0; ensembleID < aggregators.length; ensembleID++) {

            // these boolean properties are bound the draw ensemble check boxes
            if( ! drawEnsemble[ensembleID].get()) continue;
            int[] timeSpan = findHorizontalClipping(aggregators[ensembleID]);

            // draw the area between min and max y value of each time step shaded (which also a hint that the view is an aggregation)
            drawHull(gc, dataToScreen, aggregators[ensembleID]);
            // draw the time series ensemble approximation
            drawEnsemble(gc, dataToScreen, aggregators[ensembleID], ensembleColors[ensembleID], timeSpan, 0);

        }

        // visualize the time windows that served as input for the currently selected correlation matrix cell
        if(visualizeInputWindows)
            visualizeInputWindows(gc, dataToScreen, aggregators, activeRegion);

        xAxis.drawContents();
        yAxis.drawContents();

        redrawPending = false;
    }

    protected void visualizeInputWindows(GraphicsContext gc, Affine dataToScreen, TimeSeriesAverager[] aggregators, AggregatedCorrelationMatrix.MatrixRegionData activeRegion) {

        WindowMetadata metadata = sharedData.getCorrelationMatrix().metadata;
        int groupSize = aggregators[0].getGroupSize();

        int timeLag = metadata.getTimeLagByIdx(activeRegion.row) / groupSize;

        int firstAggregatedDataPointIdx = sharedData.getCorrelationMatrix().getColumn(activeRegion.column).windowStartIndex / groupSize;
        int[] timeSpan = new int[]{firstAggregatedDataPointIdx, firstAggregatedDataPointIdx + metadata.windowSize / groupSize} ;

        Color[] ensembleColors = TimeSeriesViewController.ensembleColors;

        double[] xValues = aggregators[0].getXValues();

        // if the lag shifts the ensemble beyond its limits, don't attempt to draw (the correlation is NaN anyway)
        if(Double.isNaN(activeRegion.medianCorrelation) || timeSpan[0] < 0 || timeSpan[1] > xValues.length-1) return;

        // clear the previous rendering in the window's area for the shifted rendering
        Point2D minX = dataToScreen.transform(xValues[timeSpan[0]], 0);
        Point2D maxX = dataToScreen.transform(xValues[timeSpan[1]-1], 0);
        double windowWidthSC = maxX.getX() - minX.getX();
        gc.setFill(Color.WHITE);
        gc.fillRect(minX.getX(), 0, windowWidthSC, getHeight());
        // highlight the start of the area
        gc.setLineWidth(2);
        gc.setStroke(Color.DARKGRAY);
        gc.strokeLine(minX.getX(), 0, minX.getX(), getHeight());

        Point2D lagPositionXMinY;
        Point2D lagPositionMaxY;
        if(timeLag >= 0){

            // compute the position from where the shifted ensemble B window is taken from
            lagPositionXMinY = dataToScreen.transform(xValues[timeSpan[0]-timeLag], aggregators[1].minValues[timeSpan[0]-timeLag]);
            lagPositionMaxY = dataToScreen.transform(0, aggregators[1].maxValues[timeSpan[0]-timeLag]);
            gc.setStroke(ensembleColors[1]);

            // draw right-shifted time series ensemble B
            drawEnsemble(gc, dataToScreen, aggregators[0], ensembleColors[0], timeSpan, 0);            // redraw unshifted ensemble: has been deleted by highlight window
            drawEnsemble(gc, dataToScreen, aggregators[1], ensembleColors[1], timeSpan, -timeLag);
        } else {

            // compute the position from where the shifted ensemble A window is taken from
            lagPositionXMinY = dataToScreen.transform(xValues[timeSpan[0]+timeLag], aggregators[0].minValues[timeSpan[0]+timeLag]);
            lagPositionMaxY = dataToScreen.transform(0, aggregators[0].maxValues[timeSpan[0]+timeLag]);
            gc.setStroke(ensembleColors[0]);

            // draw right-shifted time series ensemble A
            drawEnsemble(gc, dataToScreen, aggregators[0], ensembleColors[0], timeSpan, timeLag);
            drawEnsemble(gc, dataToScreen, aggregators[1], ensembleColors[1], timeSpan, 0);            // redraw unshifted ensemble: has been deleted by highlight window
        }

        // mark the position from where the shifted ensemble window is taken from

        // lag window start marker (vertical line)
        gc.setLineWidth(1);
        gc.strokeLine(lagPositionXMinY.getX(), 0, lagPositionXMinY.getX(), getHeight());

        // highlight the source and end y-axis interval (thick line around min/max where the window is taken from)
        gc.setLineWidth(3.45);
        gc.strokeLine(lagPositionXMinY.getX(), lagPositionXMinY.getY()+25, lagPositionXMinY.getX(), lagPositionMaxY.getY()-25);
        gc.strokeLine(minX.getX(), lagPositionXMinY.getY()+25, minX.getX(), lagPositionMaxY.getY()-25);

        // shade the source window (x-axis range that extends over the source window)
        gc.setFill(ensembleColors[timeLag >= 0 ? 1 : 0].deriveColor(0,1,1,0.10)); // Color.gray(.5,0.13);
        gc.fillRect(lagPositionXMinY.getX(), 0, Math.min(windowWidthSC, minX.getX()-lagPositionXMinY.getX()), getHeight());

    }

    /**
     * @return the currently visible time span expressed as an integer array.
     *         Contains, at offset 0, the index of the first data point to consider when drawing.
     *         Contains, at offset 1, the index of the last (exclusive) data point to consider when drawing.
     */
    protected int[] findHorizontalClipping(TimeSeriesAverager aggregator) {
        double[] xValues = aggregator.getXValues();
        // find the horizontal clipping
        double xAxisSpacing = xValues[1] - xValues[0];      // xValues of the time series have already been subject to time aggregation, so no need to adjust for group size
        double leftPadding = Math.max(0, xAxis.getLowerBound() - xValues[0]);
        double rightPadding = Math.max(0, xValues[xValues.length-1] - xAxis.getUpperBound());
        int invisiblePointsFront = (int)Math.floor(leftPadding/xAxisSpacing);
        int invisiblePointsBack = (int)Math.floor(rightPadding/xAxisSpacing);

        return new int[]{
                Math.max(0, invisiblePointsFront),
                Math.min(xValues.length, xValues.length-invisiblePointsBack+1)
        };
    }

    /**
     * Visualizes the time series of an ensemble within a given time span.
     * @param timeSpan contains, at offset 0, the index of the first data point to consider when drawing.
     *                 contains, at offset 1, the index (exclusive) of the last data point to consider when drawing.
     * @param lag a relative offset to shift the entire ensemble. The ensemble will be shifted by this many data points before being drawn.
     *            Instead of drawing A[t] at time t, A[t + lag] will be drawn.
     *            A positive lag is equivalent to shifting the ensemble to the left, a negative lag is equivalent to shifting the ensemble to the right.
     */
    protected void drawEnsemble(GraphicsContext gc, Affine dataToScreen, TimeSeriesAverager aggregator, Color ensembleColor, int[] timeSpan, int lag) {

        // each of these arrays has length N = number of data points
        double[] xValues = aggregator.getXValues();
        double[] minValues = aggregator.minValues;
        double[] maxValues = aggregator.maxValues;
        double[] lowestBinStartsAt = aggregator.lowestBinStartsAt;

        // each of these arrays has length N-1
        short[][][] histograms = aggregator.histograms;
        int[][] drawOrders = aggregator.drawOrder;

        int numPoints = xValues.length;

        // assert proper counts of values and histograms
        assert xValues.length == numPoints && minValues.length == numPoints && maxValues.length == numPoints && lowestBinStartsAt.length == numPoints: String.format("Illegal count of min/max values: %s/%s should be %s. Or illegal count of lower bounds: %s should be %s.", minValues.length, maxValues.length, numPoints, lowestBinStartsAt.length, numPoints);
        assert histograms.length == numPoints-1 && drawOrders.length == numPoints-1;
        // if the lag shifts the ensemble beyond its limits, don't attempt to draw (the correlation is NaN anyway)
        assert (timeSpan[0]+lag >= 0 && timeSpan[1]+lag <= numPoints) : String.format("Render time span [%s, %s] (lag: %s) is outside the ensemble data bounds [0, %s[.", timeSpan[0]+lag, timeSpan[1]+lag, lag, numPoints);

        gc.save();
        gc.setStroke(ensembleColor);
        gc.setLineWidth(1);
        gc.setLineCap(StrokeLineCap.SQUARE);

        // Transforming the values per hand is ugly but reduces the transformation costs by half,
        // since each transformed coordinate is needed twice: once as source time step and once as target time step.
        // Nevertheless, this doesn't _necessarily_ mean that it speeds up rendering: drawing polygons seems to be the most costly operation.
        // Storing all values in a two point array is faster than repeatedly creating Point2D objects as result objects.

//        // contains the transformed current x position (at index 0) and transformed minimum Y value (at index 1)
        double[] lastXLowerY = new double[]{ xValues[timeSpan[0]], lowestBinStartsAt[timeSpan[0]+lag] };
        dataToScreen.transform2DPoints(lastXLowerY, 0, lastXLowerY, 0, 1);

        double binHeightPx = dataToScreen.deltaTransform(0, -getBinSize()).getY();

        // TODO: This happens when zooming in too fast, although it shouldn't. It's not clear why. This is no good solution to the problem, because the resetView() calls are obviously not what the user wants when zooming in.
        if(binHeightPx <= 0) {
            System.err.println("HistogramTimeSeriesChart.drawEnsemble: Negative bin height. Abort.");
            resetView();
            return;
        }

        // like lastXLowerY but for the current time step
        double[] xLowerY = new double[2];
        // the x and y values that describe the time series segments within this bin
        double[] polygonXValues = new double[4];
        double[] polygonYValues = new double[4];

        // drawing shouldn't take longer than 5 seconds, otherwise, the loops aborts
        Util.TimeOutChecker timeOutChecker = new Util.TimeOutChecker(5000);

        // find global max bin value (within this ensemble) to normalize input values to the transfer function
        int maxPeakFrequency = 0;
        for (int i = 0; i < xValues.length - 1; i++)
            maxPeakFrequency = Math.max(maxPeakFrequency, aggregator.maxBinValue[i]);

        // start with second time step, drawing polygons connecting to the previous time step
        for (int i = timeSpan[0]; i < timeSpan[1]-1; i++) {     // timeSpan[1] - 1 because there are only N-1 histograms for N data points

            xLowerY[0] = xValues[i+1];
            xLowerY[1] = lowestBinStartsAt[i+1+lag];
            dataToScreen.transform2DPoints(xLowerY, 0, xLowerY, 0, 1);

            // x values of the polygon are constant for the interval between two time steps
            polygonXValues[0] = lastXLowerY[0];   // without adding some minor constant, there are gaps between the polygons
            polygonXValues[1] = xLowerY[0];
            polygonXValues[2] = xLowerY[0];
            polygonXValues[3] = lastXLowerY[0];

            if(drawPoly)
                drawTimeStepPolygons(gc, histograms[i+lag], drawOrders[i+lag], polygonXValues, polygonYValues, binHeightPx, lastXLowerY[1], xLowerY[1], maxPeakFrequency, ensembleColor);
            if(drawGrid)
                drawTimeStepGrid(gc, histograms[i+lag], lastXLowerY, xLowerY, polygonXValues, polygonYValues, binHeightPx, maxPeakFrequency, ensembleColor);

            // copy current values to last values
            lastXLowerY[0] = xLowerY[0];
            lastXLowerY[1] = xLowerY[1];

            if(timeOutChecker.isTimeOut()) {
                new Alert(Alert.AlertType.ERROR, "Sorry, rendering with the given parameters took more than five seconds. The operation timed out.").show();
                break;
            }
        }

        gc.restore();

    }

    protected void drawHull(GraphicsContext gc, Affine dataToScreen, TimeSeriesAverager aggregator) {

        double[] xValues = aggregator.getXValues();
        double[] minValues = aggregator.minValues;
        double[] maxValues = aggregator.maxValues;

        // the x values of the polygon are formed by the concatenation of the x values and the reversed x values (counter clockwise polygon traversal)
        double[] allXValues = ArrayUtils.clone(xValues);
        double[] xValuesReverse = ArrayUtils.clone(xValues);
        ArrayUtils.reverse(xValuesReverse);
        allXValues = ArrayUtils.addAll(allXValues, xValuesReverse);

        // the y values of the polygon are formed by the minimum y values concatenated with the maximum y values in reverse order (counter clockwise polygon traversal)
        double[] allYValues = ArrayUtils.clone(minValues);
        double[] maxYValuesReversed = ArrayUtils.clone(maxValues);
        ArrayUtils.reverse(maxYValuesReversed);
        allYValues = ArrayUtils.addAll(allYValues, maxYValuesReversed);

        // handle NaN induced infinite values
        for (int i = 0; i < allYValues.length; i++)
            if (!Double.isFinite(allYValues[i])) allYValues[i] = chartCanvas.getHeight() / 2;

        gc.save();
        gc.setTransform(dataToScreen);
        gc.setFill(Color.gray(0.5, 0.15));
        gc.fillPolygon(allXValues, allYValues, allXValues.length);
        gc.restore();

    }

    /**
     * Visualizes the histogram data between two time steps. Each bin frequency is mapped to a line width (transfer function).
     * A line with that width is then drawn, starting halfway between the source bin lower and upper bounds (y value) and ending
     * halfway between the sink bin lower and upper bound. Centering the line in the parallelogram also better reflects the average slope
     * of the line segments in that bin. Connecting the lower bounds or connecting the upper bounds could give a slope 0 line, although the average slope might not be 0.
     * @param lastXLowerY contains the screen coordinate where the lowest bin of the previous time step starts
     * @param xLowerY contains the screen coordinate where the lowest bin of the current time step starts
     * @param polygonXValues the x values of the polygons points coordinates
     * @param polygonYValues the y values of the polygons points coordinates
     */
    protected void drawTimeStepGrid(GraphicsContext gc, short[][] histogram, double[] lastXLowerY, double[] xLowerY, double[] polygonXValues, double[] polygonYValues, double binHeightPx, int maxBinValue, Color ensembleColor) {

        int numSourceBins = histogram.length;
        int numSinkBins = histogram[0].length;

        for (int prevBinIdx = 0; prevBinIdx < numSourceBins; prevBinIdx++) {

            // for a fixed left bin, the left y values of the polygon are constant
            // we're working with screen coordinates so moving upwards means subtracting pixels
            polygonYValues[0] = lastXLowerY[1] - prevBinIdx * binHeightPx;
            polygonYValues[3] = polygonYValues[0] - binHeightPx;

            for (int nextBinIdx = 0; nextBinIdx < numSinkBins; nextBinIdx++) {

                polygonYValues[1] = xLowerY[1] - nextBinIdx * binHeightPx;
                polygonYValues[2] = polygonYValues[1] - binHeightPx;
                double opacity;
                int numTimeSeriesInBin = Short.toUnsignedInt(histogram[prevBinIdx][nextBinIdx]);

                if(numTimeSeriesInBin == 0) continue;   // if no line segments fall within this bin, skip the bin

                if(useLinearTransfer)
                    opacity = Math.min(0.8, 1. * numTimeSeriesInBin / maxBinValue + 0.3); // linear mapping
                else  {
                    double relativeFrequency = 1. * numTimeSeriesInBin / maxBinValue;
                    opacity = 0.1 + 0.9 * relativeFrequency * relativeFrequency; // cubic mapping
                }

                gc.setStroke(ensembleColor.deriveColor(0, 1, 1, opacity));
                gc.setLineWidth(opacity*2.5);
                gc.strokeLine(polygonXValues[0],0.5*(polygonYValues[0]+polygonYValues[3]),polygonXValues[1],0.5*(polygonYValues[1]+polygonYValues[2]));

            } // end for sink bins

        } // end for source bins

    }

    protected void drawTimeStepPolygons(GraphicsContext gc, short[][] histogram, int[] drawOrder, double[] polygonXValues, double[] polygonYValues, double binHeightPx, double minYSCPrevious, double minYSC, int maxBinValue, Color ensembleColor) {

        int numSinkBins = histogram[0].length;

        for (int orderIdx : drawOrder) {

            int sourceBinIdx = orderIdx / numSinkBins;
            int sinkBinIdx = orderIdx % numSinkBins;

            // for a fixed left bin, the left y values of the polygon are constant
            // we're working with screen coordinates so moving upwards means subtracting pixels
            polygonYValues[0] = minYSCPrevious - sourceBinIdx * binHeightPx;
            polygonYValues[3] = polygonYValues[0] - binHeightPx;

            polygonYValues[1] = minYSC - sinkBinIdx * binHeightPx;
            polygonYValues[2] = polygonYValues[1] - binHeightPx;
            double opacity;

            int numTimeSeriesInBin = Short.toUnsignedInt(histogram[sourceBinIdx][sinkBinIdx]);

            if (numTimeSeriesInBin == 0) continue;   // if no time series fall within this bin, skip the bin

            if (useLinearTransfer)
                opacity = Math.min(0.8, 1. * numTimeSeriesInBin / maxBinValue + 0.3); // linear mapping
            else {
                double relativeFrequency = 1. * numTimeSeriesInBin / maxBinValue;
                opacity = 0.3 + 0.7 * relativeFrequency * relativeFrequency; // quadratic mapping
//                opacity = Math.max(0.3, Math.min(0.8, 0.5 * Math.log10(1. * numTimeSeriesInBin / maxBinValue) + 1)); // logarithmic mapping
//                opacity = Math.max(0.3, Math.min(0.8, 0.5 * Math.log10(1. * numTimeSeriesInBin / maxBinValue) + 1)); // logarithmic mapping
            }

//                  gc.setStroke(ensembleColor.deriveColor(0,1,1,opacity));
//                  gc.setLineWidth(opacity*5);
//                  gc.strokeLine(polygonXValues[0],polygonYValues[0],polygonXValues[1],polygonYValues[1]);

            gc.setFill(ensembleColor.interpolate(Color.WHITE, 1 - opacity));
            gc.fillPolygon(polygonXValues, polygonYValues, 4);
//            gc.strokeText(""+ histogram[sourceBinIdx][sinkBinIdx], polygonXValues[0],polygonYValues[0]);
//                  gc.strokePolygon(polygonXValues, polygonYValues, 4);

        } // end for order index

    } // end method draw time step polygon

}
