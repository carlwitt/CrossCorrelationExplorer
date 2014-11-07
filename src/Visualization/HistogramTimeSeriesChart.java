package Visualization;

import Data.DataModel;
import Data.TimeSeriesAverager;
import Global.Util;
import Gui.TimeSeriesViewController;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;

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
        super(); margins[TOP] = 5;
    }

    @Override public void drawContents() {

        if(isDeferringDrawRequests){
            redrawPending = true;
            return;
        }

        if(sharedData == null || sharedData.experiment == null || sharedData.experiment.dataModel.correlationSetA.isEmpty() && sharedData.experiment.dataModel.correlationSetB.isEmpty()) return;

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);

        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();

        DataModel dataModel = sharedData.experiment.dataModel;
        TimeSeriesAverager[] aggregators = new TimeSeriesAverager[]{dataModel.correlationSetAAggregator, dataModel.correlationSetBAggregator};

        // determine bin size from screen space
        int numDataPointsInRange = dataModel.getNumDataPointsInRange(0, xAxis.getLowerBound(), xAxis.getUpperBound());
        int optimalBinSize = (int) Math.max(1, Math.ceil(10. * numDataPointsInRange / getWidth()));
        aggregators[0].setGroupSize(optimalBinSize);
        aggregators[1].setGroupSize(optimalBinSize);

        for (int ensembleID = 0; ensembleID < aggregators.length; ensembleID++) {

            // draw ensemble check boxes
            if( ! drawEnsemble[ensembleID].get()) continue;

            Color ensembleColor = TimeSeriesViewController.ensembleColors[ensembleID];
            drawEnsemble(gc, dataToScreen, aggregators[ensembleID], ensembleColor);

        }

        xAxis.drawContents();
        yAxis.drawContents();

        redrawPending = false;
    }

    protected void drawEnsemble(GraphicsContext gc, Affine dataToScreen, TimeSeriesAverager aggregator, Color ensembleColor) {// for the x values

        double[] xValues = aggregator.getXValues();

        // find the horizontal clipping
        double xAxisSpacing = xValues[1] - xValues[0];      // xValues of the time series have already been subject to time aggregation, so no need to adjust for group size
        double leftPadding = Math.max(0, xAxis.getLowerBound() - xValues[0]);
        double rightPadding = Math.max(0, xValues[xValues.length-1] - xAxis.getUpperBound());
        int invisiblePointsFront = (int)Math.floor(leftPadding/xAxisSpacing);
        int invisiblePointsBack = (int)Math.floor(rightPadding/xAxisSpacing);
        int lastDataPointIdx = xValues.length-1-invisiblePointsBack;

        float[] minValues = aggregator.minValues;
        float[] maxValues = aggregator.maxValues;

        gc.setStroke(ensembleColor);
        gc.setLineWidth(1);
        gc.setLineCap(StrokeLineCap.SQUARE);

        // draw the area between min and max y value of each time step shaded (which also a hint that the view is an aggregation)
        drawHull(gc, dataToScreen, xValues, minValues, maxValues);

        short[][][] histograms = aggregator.histograms;
        int[][] drawOrders = aggregator.drawOrder;
        double[] lowestBinStartsAt = aggregator.lowestBinStartsAt;

        // contains the transformed current x position (index 0), the minimum Y (index 1) and the maximum Y value (index 3)
        // storing all values in a two point array is faster than transforming them separately
        double[] lastXLowerY = new double[]{ xValues[0], lowestBinStartsAt[0] };
        dataToScreen.transform2DPoints(lastXLowerY, 0, lastXLowerY, 0, 1);

        double binHeightPx = dataToScreen.deltaTransform(0, -getBinSize()).getY();
        assert binHeightPx > 0 : "Negative bin height in pixels: "+binHeightPx;

        // like lastXMinYMaxY but for the current time step
        double[] xLowerY = new double[2];
        // the x and y values that describe the are within the bin
        double[] polygonXValues = new double[4];
        double[] polygonYValues = new double[4];

        // drawing shouldn't take longer than 10 seconds, otherwise, the loops aborts
        Util.TimeOutChecker timeOutChecker = new Util.TimeOutChecker(10000);

        // find global max bin value (within this ensemble) to normalize input values to the transfer function
        int maxPeakFrequency = 0;
        for (int i = 0; i < xValues.length - 1; i++)
            maxPeakFrequency = Math.max(maxPeakFrequency, aggregator.maxBinValue[i]);

        // start with second time step, drawing polygons connecting to the previous time step
        for (int i = Math.max(1, invisiblePointsFront); i < Math.min(xValues.length-1, lastDataPointIdx+1); i++) {

            xLowerY[0] = xValues[i];
            xLowerY[1] = lowestBinStartsAt[i];
            dataToScreen.transform2DPoints(xLowerY, 0, xLowerY, 0, 1);

            // x values of the polygon are constant for the interval between two time steps
            polygonXValues[0] = lastXLowerY[0];   // without adding some minor constant, there are gaps between the polygons
            polygonXValues[1] = xLowerY[0];
            polygonXValues[2] = xLowerY[0];
            polygonXValues[3] = lastXLowerY[0];

            if(drawPoly && i < histograms.length)
                drawTimeStepPolygons(gc, histograms[i - 1], drawOrders[i - 1], polygonXValues, polygonYValues, binHeightPx, lastXLowerY[1], xLowerY[1], maxPeakFrequency, ensembleColor);
            if(drawGrid)
                drawTimeStepGrid(gc, histograms[i - 1], lastXLowerY, xLowerY, polygonXValues, polygonYValues, binHeightPx, maxPeakFrequency, ensembleColor);

            // copy current values to last values
            lastXLowerY[0] = xLowerY[0];
            lastXLowerY[1] = xLowerY[1];

            if(timeOutChecker.isTimeOut()) {
                System.out.println(String.format("Time out in drawing polygons"));
                break;
            }
        }

    }

    protected void drawHull(GraphicsContext gc, Affine dataToScreen, double[] xValues, float[] minValues, float[] maxValues) {

        //TODO: drawHull() needs cleanup

        // the number of available data points (= time series length)
        int numPoints = xValues.length;

        // draw upper hull
        double[] maxLinePoints = Util.zip(xValues, maxValues);
        dataToScreen.transform2DPoints(maxLinePoints, 0, maxLinePoints, 0, numPoints);
        double[] maxValuesTransformedX = new double[numPoints], maxValuesTransformedY = new double[numPoints];
        Util.unzip(maxLinePoints, maxValuesTransformedX, maxValuesTransformedY);
//            gc.strokePolyline(maxValuesTransformedX, maxValuesTransformedY, numPoints);

        // draw lower hull
        double[] minLinePoints = Util.zip(xValues, minValues);
        dataToScreen.transform2DPoints(minLinePoints, 0, minLinePoints, 0, numPoints);
        double[] minValuesTransformedX = new double[numPoints], minValuesTransformedY = new double[numPoints];
        Util.unzip(minLinePoints, minValuesTransformedX, minValuesTransformedY);
//            gc.strokePolyline(minValuesTransformedX, minValuesTransformedY, minValues.length);

        // draw cover area

        double[] reverseXValuesTransformed = Arrays.copyOf(maxValuesTransformedX, numPoints);
        ArrayUtils.reverse(reverseXValuesTransformed);
        double[] xValuesTransformed2Times = new double[numPoints *2];
        System.arraycopy(maxValuesTransformedX, 0, xValuesTransformed2Times, 0, numPoints);
        System.arraycopy(reverseXValuesTransformed, 0, xValuesTransformed2Times, numPoints, numPoints);

        double[] yValuesAllTransformed = new double[numPoints *2];
        System.arraycopy(minValuesTransformedY, 0, yValuesAllTransformed, 0, minValues.length);
        double[] maxValuesTransformedYReversed = Arrays.copyOf(maxValuesTransformedY, numPoints);
        ArrayUtils.reverse(maxValuesTransformedYReversed);
        System.arraycopy(maxValuesTransformedYReversed, 0, yValuesAllTransformed, minValues.length, minValues.length);

        // handle NaN induced infinite values
        for (int i = 0; i < numPoints * 2; i++) {
            if(! Double.isFinite(yValuesAllTransformed[i])) yValuesAllTransformed[i] = chartCanvas.getHeight()/2;
        }
//        assert ! com.fastdtw.util.Arrays.contains(xValuesTransformed2Times, Double.POSITIVE_INFINITY) : "Invalid x values";
//        assert ! com.fastdtw.util.Arrays.contains(yValuesAllTransformed, Double.NEGATIVE_INFINITY) : "Invalid y values";

        gc.setFill(Color.gray(0.5, 0.15));
        gc.fillPolygon(xValuesTransformed2Times, yValuesAllTransformed, numPoints *2);

    }

    /**
     * Visualizes the histogram data between two time steps. Each bin frequency is mapped to a line width (transfer function).
     * A line with that width is then drawn, starting halfway between the source bin lower and upper bounds (y value) and ending
     * halfway between the sink bin lower and upper bound. Centering the line in the parallelogram also better reflects the average slope
     * of the line segments in that bin. Connecting the lower bounds or connecting the upper bounds could give a slope 0 line, although the average slope might not be 0.
     * @param lastXLowerY contains the screen coordinate where the lowest bin of the previous time step starts
     * @param xLowerY contains the screen coordinate where the lowest bin of the current time step starts
     * @param polygonXValues
     * @param polygonYValues
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
                    double relativeFrequency = 1. * histogram[prevBinIdx][nextBinIdx] / maxBinValue;
                    opacity = 0.1 + 0.9 * relativeFrequency * relativeFrequency; // cubic mapping
                }

                gc.setStroke(ensembleColor.deriveColor(0, 1, 1, opacity));
                gc.setLineWidth(opacity*2.5);
                gc.strokeLine(polygonXValues[0],0.5*(polygonYValues[0]+polygonYValues[3]),polygonXValues[1],0.5*(polygonYValues[1]+polygonYValues[2]));

            } // for sink bins

        } // for source bins

    }

    protected void drawTimeStepPolygons(GraphicsContext gc, short[][] histogram, int[] drawOrder, double[] polygonXValues, double[] polygonYValues, double binHeightPx, double minYSCPrevious, double minYSC, int maxBinValue, Color ensembleColor) {

        int numSinkBins = histogram[0].length;

        for (int orderIdx = 0; orderIdx < drawOrder.length; orderIdx++) {

            int sourceBinIdx = drawOrder[orderIdx] / numSinkBins;
            int sinkBinIdx = drawOrder[orderIdx] % numSinkBins;

            // for a fixed left bin, the left y values of the polygon are constant
            // we're working with screen coordinates so moving upwards means subtracting pixels
            polygonYValues[0] = minYSCPrevious - sourceBinIdx * binHeightPx;
            polygonYValues[3] = polygonYValues[0] - binHeightPx;

            polygonYValues[1] = minYSC - sinkBinIdx * binHeightPx;
            polygonYValues[2] = polygonYValues[1] - binHeightPx;
            double opacity;

            int numTimeSeriesInBin = Short.toUnsignedInt(histogram[sourceBinIdx][sinkBinIdx]);

            if(numTimeSeriesInBin == 0) continue;   // if no time series fall within this bin, skip the bin

            if(useLinearTransfer)
                opacity = Math.min(0.8, 1. * numTimeSeriesInBin / maxBinValue + 0.3); // linear mapping
            else{
                double relativeFrequency = 1. * histogram[sourceBinIdx][sinkBinIdx] / maxBinValue;
                opacity = 0.1 + 0.9 * relativeFrequency * relativeFrequency; // quadratic mapping
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

        }

//        gc.setStroke(Color.BLACK);
//        gc.setLineWidth(0.7);
//        gc.setLineCap(StrokeLineCap.BUTT);
//        if(lastDataPointIdx-firstDataPointIdx<100){z
//
//            // draw each series
//            double[] points = new double[4];
//            for (int tsID = 0; tsID < aggregator.getNumberOfTimeSeries(); tsID++) {
//
//                //                gc.beginPath();
//                double[] yValues = aggregator.getYValues(tsID + 1);
//                points[0] = xValues[0];
//                points[1] = yValues[0];
//                dataToScreen.transform2DPoints(points, 0, points, 0, 1);
//
//                //                gc.moveTo(firstPoint.getX(), firstPoint.getY());
//
//                for (int j = firstDataPointIdx; j <= lastDataPointIdx; j++) {
//                    points[2] = xValues[j];
//                    points[3] = yValues[j];
//                    dataToScreen.transform2DPoints(points, 2, points, 2, 1);
//                    gc.strokeLine(points[0], points[1], points[2], points[3]);
//                    //                    gc.lineTo(curPoint.getX(),curPoint.getY());
//                    points[0] = points[2];
//                    points[1] = points[3];
//                }
//                gc.stroke();
//            }
//
//        }
    }
}
