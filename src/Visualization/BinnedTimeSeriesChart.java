package Visualization;

import Data.DataModel;
import Data.TimeSeriesAverager;
import Gui.TimeSeriesViewController;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

/**
 * Used to draw the time series. Better aggregation by averaging each n-th data point if there are more data points to render than pixels available.
 * @author Carl Witt
 */
@Deprecated
public class BinnedTimeSeriesChart extends TimeSeriesChart {

    public BinnedTimeSeriesChart(){

        xAxis.setMinTickUnit(1);

        // configure min width and height
        setMinHeight(10); setMinWidth(10);
        // the correlogram is kept large enough by the container constraints, but setting the preferred width to
        // the computed size can cause the correlogram to get stuck on a width that's too large for the container
        // (because there's no inherent way to compute the necessary space for a canvas).
        setPrefWidth(10); setPrefHeight(10);

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

            Color color = TimeSeriesViewController.ensembleColors[ensembleID];
            gc.setStroke(color.deriveColor(0,1,1,transparency));

            // for the x values
            double[] xValues = aggregators[ensembleID].getXValues();

            // find the horizontal clipping
            double xAxisSpacing = xValues[1] - xValues[0];
            double leftPadding = Math.max(0, xAxis.getLowerBound() - xValues[0]);
            double rightPadding = Math.max(0, xValues[xValues.length-1] - xAxis.getUpperBound());
            int invisiblePointsFront = (int)Math.floor(leftPadding/xAxisSpacing);
            int invisiblePointsBack = (int)Math.floor(rightPadding/xAxisSpacing);
            int firstDataPointIdx = invisiblePointsFront;
            int lastDataPointIdx = xValues.length-1-invisiblePointsBack;

            // draw each series
            for (int tsID = 0; tsID < aggregators[ensembleID].getNumberOfTimeSeries(); tsID++) {

                double[] yValues = aggregators[ensembleID].getYValues(tsID+1);
                Point2D curPoint, lastPoint = dataToScreen.transform(new Point2D(xValues[0], yValues[0]));


                for (int j = firstDataPointIdx; j <= lastDataPointIdx; j++) {
                    curPoint = dataToScreen.transform(new Point2D(xValues[j], yValues[j]));
                    gc.strokeLine(lastPoint.getX(), lastPoint.getY(), curPoint.getX(), curPoint.getY());
                    lastPoint=curPoint;
                }
                gc.stroke();
            }
        }

        xAxis.drawContents();
        yAxis.drawContents();

        redrawPending = false;
    }


}
