package Visualization;

import Data.ComplexSequence;
import Data.DataModel;
import Data.SharedData;
import com.fastdtw.EnsembleDelayHistogram;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

/**
 * Was an experiment on an alternative time series ensemble visualization using alignment as a preprocessing step.
 * @author Carl Witt
 */
@Deprecated
public class EnsembleDelayChart extends TimeSeriesChart {

    int ensembleID = 1;

    EnsembleDelayHistogram edh;

    public EnsembleDelayChart(){

        xAxis.setMinTickUnit(1);

        // configure min width and height
        setMinHeight(10); setMinWidth(10);
        // the correlogram is kept large enough by the container constraints, but setting the preferred width to
        // the computed size can cause the correlogram to get stuck on a width that's too large for the container
        // (because there's no inherent way to compute the necessary space for a canvas).
        setPrefWidth(10); setPrefHeight(10);

    }

    @Override
    public void setSharedData(SharedData sharedData){
        super.setSharedData(sharedData);

        edh = new EnsembleDelayHistogram(sharedData.experiment.dataModel, ensembleID);

    }

    public double transparency = 0.05;
    @Override public void drawContents() {

        if(sharedData == null || sharedData.experiment == null) return;

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);

        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();

        ComplexSequence data = sharedData.experiment.dataModel.get(ensembleID, 1).getDataItems();
        Point2D curPoint, prevPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));


        // draw the histograms
        gc.setLineWidth(1);

        double maxYSpan = data.getMax(ComplexSequence.Part.IMAGINARY) - data.getMin(ComplexSequence.Part.IMAGINARY);
        double maxDelay = Math.max(Math.abs(edh.maxPositiveDelay), Math.abs(edh.maxNegativeDelay));
        double delayScaleFactor = 0.5*maxYSpan/maxDelay;

        Color negativeColor = Color.BLUE.deriveColor(0, 1, 1, 0.5);
        Color positiveColor = Color.RED.deriveColor(0,1,1,0.5);

        for (int i = 0; i < data.re.length; i++) {
            curPoint = dataToScreen.transform(new Point2D(data.re[i], data.im[i]));

//            double maxNegDelayY = curPoint.getY() + dataToScreen.deltaTransform(0, edh.getMaxNegativeDelay(i) * delayScaleFactor).getY();
//            double maxPosDelayY = curPoint.getY() + dataToScreen.deltaTransform(0, edh.getMaxPositiveDelay(i) * delayScaleFactor).getY();

            double maxNegDelayX = curPoint.getX() + 10*dataToScreen.deltaTransform(edh.getMaxNegativeDelay(i),0).getX();
            double maxPosDelayX = curPoint.getX() + 10*dataToScreen.deltaTransform(edh.getMaxPositiveDelay(i),0).getX();

            gc.setStroke(negativeColor);
//            gc.strokeLine(curPoint.getX(), curPoint.getY(), curPoint.getX(), maxNegDelayY);
            gc.strokeLine(maxNegDelayX, curPoint.getY(), curPoint.getX(), curPoint.getY());

            gc.setStroke(positiveColor);
//            gc.strokeLine(curPoint.getX(),curPoint.getY(), curPoint.getX(), maxPosDelayY);
            gc.strokeLine(curPoint.getX(),curPoint.getY(), maxPosDelayX, curPoint.getY());
        }

        // draw the probe series
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        for (int i = 0; i < data.re.length; i++) {
            curPoint = dataToScreen.transform(new Point2D(data.re[i], data.im[i]));
            // connect current data point to previous data point with a line
            gc.strokeLine(prevPoint.getX(), prevPoint.getY(), curPoint.getX(), curPoint.getY());
            prevPoint = curPoint;
        }

        xAxis.drawContents();
        yAxis.drawContents();
    }


    public void resetView() {
        DataModel dataModel = sharedData.experiment.dataModel;
        double maxX = Math.max(dataModel.getMaxX(0), dataModel.getMaxX(1));
        double minX = Math.min(dataModel.getMinX(0), dataModel.getMinX(1));
        double xRange = maxX - minX;
        double maxY = Math.max(dataModel.getMaxY(0), dataModel.getMaxY(1));
        double minY = Math.min(dataModel.getMinY(0), dataModel.getMinY(1));
        double yRange = maxY - minY;
        if(xRange < 0 || yRange < 0){
            xRange = 1;
            yRange = 1;
                    
        }
        Bounds newVisibleRange = new BoundingBox(minX, minY, xRange, yRange);

        clipRegionDC.set(newVisibleRange);
        drawContents();
    }
    
}
