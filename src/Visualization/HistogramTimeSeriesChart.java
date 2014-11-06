package Visualization;

import Data.DataModel;
import Data.TimeSeriesAverager;
import Global.Util;
import Gui.TimeSeriesViewController;
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

    int numBins = 1;
    public boolean useLinearTransfer = true;

    public boolean drawPoly = false, drawGrid = true;

    public HistogramTimeSeriesChart(){
        super(); margins[TOP] = 5;
    }


    public void setNumBins(int numBins){
        this.numBins = numBins;
        sharedData.experiment.dataModel.correlationSetAAggregator.numBins = numBins;
        sharedData.experiment.dataModel.correlationSetBAggregator.numBins = numBins;
        sharedData.experiment.dataModel.correlationSetAAggregator.getXValues();
        sharedData.experiment.dataModel.correlationSetBAggregator.getXValues();
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
        double xAxisSpacing = xValues[1] - xValues[0];      // xValues change with binning, so this is already considered
        double leftPadding = Math.max(0, xAxis.getLowerBound() - xValues[0]);
        double rightPadding = Math.max(0, xValues[xValues.length-1] - xAxis.getUpperBound());
        int invisiblePointsFront = (int)Math.floor(leftPadding/xAxisSpacing);
        int invisiblePointsBack = (int)Math.floor(rightPadding/xAxisSpacing);
        int firstDataPointIdx = invisiblePointsFront;
        int lastDataPointIdx = xValues.length-1-invisiblePointsBack;

        float[] minValues = aggregator.minValues;
        float[] maxValues = aggregator.maxValues;

        gc.setStroke(ensembleColor);
        gc.setLineWidth(1);
        gc.setLineCap(StrokeLineCap.BUTT);

        // draw the area between min and max y value of each time step shaded (which also a hint that the view is an aggregation)
        drawHull(gc, dataToScreen, xValues, minValues, maxValues);

        short[][][] histograms = aggregator.histograms;
        int[][] drawOrders = aggregator.drawOrder;
        int numBins = histograms[0].length;

        // contains the transformed current x position (index 0), the minimum Y (index 1) and the maximum Y value (index 3)
        double[] lastXMinYMaxY = new double[]{ xValues[0], minValues[0], 0, maxValues[0] };
        dataToScreen.transform2DPoints(lastXMinYMaxY, 0, lastXMinYMaxY, 0, 2);

        double binHeightPxPrevious = (lastXMinYMaxY[3]-lastXMinYMaxY[1])/numBins;

        double[] xMinYMaxY = new double[4];
        double[] polygonXValues = new double[4];
        double[] polygonYValues = new double[4];

        double binHeightPx;

        // drawing shouldn't take longer than 10 seconds, otherwise, the loops aborts
        Util.TimeOutChecker timeOutChecker = new Util.TimeOutChecker(10000);

        // use global max bin value
        int maxPeakFrequency = 0;
        for (int i = 0; i < xValues.length - 1; i++)
            maxPeakFrequency = Math.max(maxPeakFrequency, aggregator.maxBinValue[i]);

        // start with second time step, drawing polygons connecting to the previous time step
        for (int i = Math.max(1,firstDataPointIdx); i < Math.min(xValues.length, lastDataPointIdx+1); i++) {
            xMinYMaxY[0] = xValues[i];
            xMinYMaxY[1] = minValues[i];
            xMinYMaxY[3] = maxValues[i];
            dataToScreen.transform2DPoints(xMinYMaxY, 0, xMinYMaxY, 0, 2);
            binHeightPx = (xMinYMaxY[3]-xMinYMaxY[1])/numBins;

            double minYSCPrevious = lastXMinYMaxY[1];
            double minYSC = xMinYMaxY[1];

            // x values of the polygon are constant for the interval between two time steps
            polygonXValues[0] = lastXMinYMaxY[0]-0.5;
            polygonXValues[1] = xMinYMaxY[0];
            polygonXValues[2] = xMinYMaxY[0];
            polygonXValues[3] = lastXMinYMaxY[0]-0.5;

            if(drawPoly && i <= histograms.length)
                drawPolygons(gc, histograms[i-1], drawOrders[i-1], numBins, binHeightPxPrevious, polygonXValues, polygonYValues, binHeightPx, minYSCPrevious, minYSC, maxPeakFrequency, ensembleColor);
            if(drawGrid)
                drawGrid(gc, histograms[i-1], numBins, lastXMinYMaxY, binHeightPxPrevious, xMinYMaxY, polygonXValues, polygonYValues, binHeightPx, minYSCPrevious, minYSC, maxPeakFrequency, ensembleColor);

            // copy current values to last values
            lastXMinYMaxY[0] = xMinYMaxY[0];
            lastXMinYMaxY[1] = xMinYMaxY[1];
            lastXMinYMaxY[3] = xMinYMaxY[3];
            binHeightPxPrevious = binHeightPx;

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

    protected void drawGrid(GraphicsContext gc, short[][] histogram, int numBins, double[] lastXMinYMaxY, double binHeightPxPrevious, double[] xMinYMaxY, double[] polygonXValues, double[] polygonYValues, double binHeightPx, double minYSCPrevious, double minYSC, int maxBinValue, Color ensembleColor) {
        for (int prevBinIdx = 0; prevBinIdx < numBins; prevBinIdx++) {

            // x values of the polygon are constant for the interval between two time steps
            polygonXValues[0] = lastXMinYMaxY[0];
            polygonXValues[1] = xMinYMaxY[0];
            polygonXValues[2] = xMinYMaxY[0];
            polygonXValues[3] = lastXMinYMaxY[0];

            // for a fixed left bin, the left y values of the polygon are constant
            polygonYValues[0] = minYSCPrevious + prevBinIdx*binHeightPxPrevious;
            polygonYValues[3] = polygonYValues[0] + binHeightPxPrevious;

            for (int nextBinIdx = 0; nextBinIdx < numBins; nextBinIdx++) {

                polygonYValues[1] = minYSC + nextBinIdx * binHeightPx;
                polygonYValues[2] = polygonYValues[1] + binHeightPx;
                double opacity;
                int numTimeSeriesInBin = Short.toUnsignedInt(histogram[prevBinIdx][nextBinIdx]);
                if(useLinearTransfer)
                    opacity = Math.min(0.8, 1. * numTimeSeriesInBin / maxBinValue + 0.3); // linear mapping
                else  {
                    double relativeFrequency = 1. * histogram[prevBinIdx][nextBinIdx] / maxBinValue;
                    opacity = 0.1 + 0.9 * relativeFrequency * relativeFrequency; // cubic mapping
//                     opacity = Math.min(1., Math.exp(histogram[prevBinIdx][nextBinIdx] / maxBinValue - 1)); // exponential mapping
//                      opacity = Math.max(0.3, Math.min(0.8, 0.5 * Math.log10(1. * numTimeSeriesInBin / maxBinValue) + 1)); // logarithmic mapping
                    }

                if(numTimeSeriesInBin == 0) continue;

                gc.setStroke(ensembleColor.deriveColor(0, 1, 1, opacity));
                gc.setLineWidth(opacity*2.5);
                gc.strokeLine(polygonXValues[0],0.5*(polygonYValues[0]+polygonYValues[3]),polygonXValues[1],0.5*(polygonYValues[1]+polygonYValues[2]));

//                        gc.setFill(ensembleColor.interpolate(Color.WHITE,1-opacity));
//                        gc.fillPolygon(polygonXValues, polygonYValues, 4);
//                        gc.strokePolygon(polygonXValues, polygonYValues, 4);

            }
        }
    }

    protected void drawPolygons(GraphicsContext gc, short[][] histogram, int[] drawOrder, int numBins, double binHeightPxPrevious, double[] polygonXValues, double[] polygonYValues, double binHeightPx, double minYSCPrevious, double minYSC, int maxBinValue, Color ensembleColor) {

        for (int orderIdx = 0; orderIdx < numBins * numBins; orderIdx++) {

            int prevBinIdx = drawOrder[orderIdx] / numBins;
            int nextBinIdx = drawOrder[orderIdx] % numBins;

            // for a fixed left bin, the left y values of the polygon are constant
            polygonYValues[0] = minYSCPrevious + prevBinIdx*binHeightPxPrevious;
            polygonYValues[3] = polygonYValues[0] + binHeightPxPrevious;


            polygonYValues[1] = minYSC + nextBinIdx * binHeightPx;
            polygonYValues[2] = polygonYValues[1] + binHeightPx;
            double opacity;

            int numTimeSeriesInBin = Short.toUnsignedInt(histogram[prevBinIdx][nextBinIdx]);

            if(useLinearTransfer)
                opacity = Math.min(0.8, 1. * numTimeSeriesInBin / maxBinValue + 0.3); // linear mapping
            else{
                double relativeFrequency = 1. * histogram[prevBinIdx][nextBinIdx] / maxBinValue;
                opacity = 0.1 + 0.9 * relativeFrequency * relativeFrequency; // quadratic mapping
//                opacity = Math.max(0.3, Math.min(0.8, 0.5 * Math.log10(1. * numTimeSeriesInBin / maxBinValue) + 1)); // logarithmic mapping
//                opacity = Math.max(0.3, Math.min(0.8, 0.5 * Math.log10(1. * numTimeSeriesInBin / maxBinValue) + 1)); // logarithmic mapping
            }

            if(numTimeSeriesInBin == 0) continue;

//                  gc.setStroke(ensembleColor.deriveColor(0,1,1,opacity));
//                  gc.setLineWidth(opacity*5);
//                  gc.strokeLine(polygonXValues[0],polygonYValues[0],polygonXValues[1],polygonYValues[1]);

            gc.setFill(ensembleColor.interpolate(Color.WHITE, 1 - opacity));
            gc.fillPolygon(polygonXValues, polygonYValues, 4);
//            gc.strokeText(""+ histogram[prevBinIdx][nextBinIdx], polygonXValues[0],polygonYValues[0]);
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
