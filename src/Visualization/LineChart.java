package Visualization;

import Data.ComplexSequence;
import Data.SharedData;
import Data.TimeSeries;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

/**
 * Used to draw the time series. Supports basic aggregation by drawing only each N-th data point.
 * TODO: more sophisticated data aggregation. keeping peaks is important to provide an undistorted impression of the overall shape
 * @author Carl Witt
 */
public class LineChart extends CanvasChart {

    //TODO sharedData should be refactored out (the line chart isn't reusable this way)
    public SharedData sharedData;
    //TODO seriesSets should be replaced with a more generic data structure that makes the class more reusable
    public HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
    
    private final IntegerProperty drawEachNthDataPoint = new SimpleIntegerProperty(1);
    public final void setDrawEachNthDataPoint(int step){ drawEachNthDataPoint.set(step); }
    public final int getDrawEachNthDataPoint(){ return drawEachNthDataPoint.get(); }
    public final IntegerProperty drawEachNthDataPointProperty(){ return drawEachNthDataPoint; }

    @Override public void drawContents() {
        
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        
        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);
        
        // for testing: mark outline of the canvas
//            Bounds b = chartCanvas.getLayoutBounds();
//            gc.setStroke(new Color(1, 0.5, 0.25, 0.7));
//            gc.setLineWidth(2);
//            gc.strokeRect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        
        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();
        
        // simple complexity reduction: display only every n-th point
        final int step = Math.max(1, getDrawEachNthDataPoint());

        Random randomColors = new Random(0);
//System.out.println("draw: "+seriesSets.entrySet());
        for (Map.Entry<Color, ObservableList<TimeSeries>> coloredSet : seriesSets.entrySet()) {
//System.out.println("has: "+coloredSet.getValue().size());
            // different color for each series (but always the same for each series)
//            gc.setStroke(new Color(randomColors.nextDouble(), randomColors.nextDouble(), randomColors.nextDouble(), 0.6));
            gc.setStroke(coloredSet.getKey());
            gc.setLineWidth(randomColors.nextDouble()+1);
            
//System.out.println(String.format("Drawing each %s datapoint", step));
            for (TimeSeries ts : coloredSet.getValue()) {
//System.out.println(String.format("ts: %s in datamodel: %s", ts, sharedData.dataModel.containsValue(ts)));
                
                if( ! sharedData.dataModel.containsValue(ts))
                    continue;
                ComplexSequence data = ts.getDataItems();
                Point2D nextPoint, lastPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));
                // TODO: render only visible points
                for (int i = 1 * step; i < data.re.length; i += step) {
                    nextPoint = dataToScreen.transform(new Point2D(data.re[i], data.im[i]));
                    gc.strokeLine(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY());
                    lastPoint = nextPoint;
                }
            }
        }
        positionChartCanvas();
    }
    
}
