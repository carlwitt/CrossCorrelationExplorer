package Visualization;

import Data.ComplexSequence;
import Data.DataModel;
import Data.SharedData;
import Data.Statistics.EnsemblePercentiles;
import Data.TimeSeries;
import Gui.TimeSeriesViewController;
//import com.fastdtw.EnsembleDelayHistogram;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Another experiment, similar to the EnsembleDelayChart.
 * @author Carl Witt
 */
@Deprecated
public class EnsemblePercentilesChart extends TimeSeriesChart {

    EnsemblePercentiles ensemblePercentiles;

    public EnsemblePercentilesChart(){

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

        System.out.println("Computing ensemble percentiles...");
        ensemblePercentiles = new EnsemblePercentiles(sharedData.experiment.dataModel);
        System.out.println("Done.");



    }

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

        for (int ensembleID = 0; ensembleID < ensemblePercentiles.percentiles.length; ensembleID++) {

            // draw ensemble check boxes
            if( ! drawEnsemble[ensembleID].get()) continue;

            Color color = TimeSeriesViewController.ensembleColors[ensembleID];
            gc.setStroke(color.deriveColor(0,1,1,transparency));

            // for the x values
            double[] xValues = sharedData.experiment.dataModel.get(ensembleID, 1).getDataItems().re;

            double[][] data = ensemblePercentiles.percentiles[ensembleID];

            final int maxPercentileLines = Math.max(1, getDrawEachNthDataPoint());

            gc.beginPath();

            for (int p = 0; p <100; p+=maxPercentileLines) {

                //            double decayFactor = 1 - Math.abs(p - 50.) / 75;
                //            gc.setStroke(Color.grayRgb(0, decayFactor));
                //            gc.setLineWidth(decayFactor);

                Point2D curPoint, prevPoint = dataToScreen.transform(new Point2D(xValues[0], data[0][p]));

                gc.moveTo(prevPoint.getX(), prevPoint.getY());

                for (int i = 0; i < ensemblePercentiles.percentiles[ensembleID].length; i++) {
                    curPoint = dataToScreen.transform(new Point2D(xValues[i], data[i][p]));
                    gc.lineTo(curPoint.getX(),curPoint.getY());
//                    gc.strokePolyline(screenX, screenY, numPoints);
                }


            }
            gc.stroke();
        }

        xAxis.drawContents();
        yAxis.drawContents();
    }


}
