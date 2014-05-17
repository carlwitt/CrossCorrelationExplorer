package Visualization;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelationMatrix.Column;
import Data.SharedData;
import Data.TimeSeries;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

/**
 * Used to draw the time series. Supports basic aggregation by drawing only each N-th data point.
 * TODO: more sophisticated data aggregation. keeping peaks is important to provide an undistorted impression of the overall shape
 * @author Carl Witt
 */
public class TimeSeriesChart extends CanvasChart {

    //TODO sharedData should be refactored out (the line chart isn't reusable this way)
    public SharedData sharedData;
    //TODO seriesSets should be replaced with a more generic data structure that makes the class more reusable
    public HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
    
    private final IntegerProperty drawEachNthDataPoint = new SimpleIntegerProperty(1);
    public final void setDrawEachNthDataPoint(int step){ drawEachNthDataPoint.set(step); }
    final int getDrawEachNthDataPoint(){ return drawEachNthDataPoint.get(); }
    public final IntegerProperty drawEachNthDataPointProperty(){ return drawEachNthDataPoint; }

    public TimeSeriesChart(){
        xAxis.setMinTickUnit(1);
    }
    
    @Override public void drawContents() {
        
        GraphicsContext gc = chartCanvas.getGraphicsContext2D();
        
        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);
        
        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();
        
        // simple complexity reduction: display only every n-th point
        final int step = Math.max(1, getDrawEachNthDataPoint());

        Column activeColumn = sharedData.getActiveColumn();
        if(activeColumn != null){
            double activeWindowMin = activeColumn.windowStartIndex;
            int activeColumnLength = activeColumn.mean.length;
            double activeWindowMax = activeWindowMin + activeColumnLength;

            gc.setStroke(Color.YELLOW);
            gc.strokeRect(xAxis.toScreen(activeWindowMin), 0, xAxis.toScreen(activeWindowMax)-xAxis.toScreen(activeWindowMin), getHeight());
        }


        // draw each set
        Color setBColor = Color.web("#4333ff").deriveColor(0, 1, 1, 0.5);
        for (Map.Entry<Color, ObservableList<TimeSeries>> coloredSet : seriesSets.entrySet()) {

            gc.setStroke(coloredSet.getKey());
            gc.setLineWidth(1.5);

            // draw each time series
            for (TimeSeries ts : coloredSet.getValue()) {
                
                ComplexSequence data = ts.getDataItems();
                Point2D nextPoint, lastPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));
                
                boolean nextIsInWindow = false, lastIsInWindow = false; // whether the next/last point are within a highlight window (shifted by time lag and wrapped)
                boolean xIsWrapped = false, lastXIsWrapped = false;
                for (int i = step; i < data.re.length; i += step) {
                    
                    double x = data.re[i];
                    double y = data.im[i];
                    
                    // shift only those in correlation set B
                    if(coloredSet.getKey().equals(setBColor) && activeColumn != null){
                        double activeWindowMin = activeColumn.windowStartIndex;
                        int activeColumnLength = activeColumn.mean.length;
                        double activeWindowMax = activeWindowMin + activeColumnLength;
                        int activeTimeLag = sharedData.getHighlightedCell().y;
                        if(x >= activeWindowMin && x <= activeWindowMax){
                            x -= activeTimeLag;
                            xIsWrapped = x < activeWindowMin || x > activeWindowMax;
                            if(x < activeWindowMin) x+= activeColumnLength;
                            if(x > activeWindowMax) x-= activeColumnLength;
                            nextIsInWindow = true;
                        } else {
                            nextIsInWindow = false;
                            xIsWrapped = false;
                        }
                    }
                    nextPoint = dataToScreen.transform(new Point2D(x, y));
                    // only connect dots outside the highlighted window
                    // or inside the highlighted window but not across the border
                    if(!lastIsInWindow && !nextIsInWindow || lastIsInWindow && nextIsInWindow ){
                        if(!lastXIsWrapped && !xIsWrapped || lastXIsWrapped && xIsWrapped)
                            gc.strokeLine(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY());
                    }
                        
                    lastPoint = nextPoint;
                    lastXIsWrapped = xIsWrapped;
                    lastIsInWindow = nextIsInWindow;
                }
            }
        }
        
        xAxis.drawContents();
        yAxis.drawContents();
        
    }
    
//    private void drawNanValues(){
//    
//        // for displaying NaN values, draw a red 10x10px circle
////        Color nanValueColor = Color.RED;
////        Point2D tenTimesTenPixels;
////        try {
////            tenTimesTenPixels = dataToScreen.inverseTransform(10,0);
////        } catch (NonInvertibleTransformException ex) {
////            tenTimesTenPixels = new Point2D(1, 1);
////        }
//        
//        // simple complexity reduction: display only every n-th point
//        final int step = Math.max(1, getDrawEachNthDataPoint());
//
//        Random randomColors = new Random(0);
//        for (Map.Entry<Color, ObservableList<TimeSeries>> coloredSet : seriesSets.entrySet()) {
//            // slightly different stroke width for each set
//            gc.setStroke(coloredSet.getKey());
//            gc.setLineWidth(randomColors.nextDouble()+1);
//            
//            for (TimeSeries ts : coloredSet.getValue()) {
//                
////                if( ! sharedData.dataModel.containsValue(ts))
////                    continue;
//                
//                ComplexSequence data = ts.getDataItems();
//                Point2D nextPoint, lastPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));
//                
////                boolean lastPointHasNaN = false, nextPointHasNaN = false;
////                // fix and mark first point if it contains NaN components
////                if(Double.isNaN(lastPoint.getX()) || Double.isNaN(lastPoint.getY())){
////                    lastPointHasNaN = true;
////                    if(Double.isNaN(lastPoint.getX())) lastPoint = new Point2D(0, data.im[0]);
////                    if(Double.isNaN(lastPoint.getY())) lastPoint = new Point2D(lastPoint.getX(), 0);
////                    lastPoint = dataToScreen.transform(lastPoint);
////                    gc.save(); gc.setStroke(Color.YELLOW); 
////                    gc.strokeOval(lastPoint.getX()-tenTimesTenPixels.getX()/2, chartCanvas.getHeight()-tenTimesTenPixels.getY()/2, tenTimesTenPixels.getX(), tenTimesTenPixels.getY());
////                    gc.restore();
////                }
//                
//                for (int i = 1 * step; i < data.re.length; i += step) {
//                    
//                    nextPoint = dataToScreen.transform(new Point2D(data.re[i], data.im[i]));
//                    
////                    if(Double.isNaN(nextPoint.getX()) || Double.isNaN(nextPoint.getY())){
////                        nextPointHasNaN = true;
////                        if(Double.isNaN(nextPoint.getX())) nextPoint = new Point2D(0, data.im[i]);
////                        if(Double.isNaN(nextPoint.getY())) nextPoint = new Point2D(nextPoint.getX(), 0);
////                        nextPoint = dataToScreen.transform(nextPoint);
////                        // draw a red circle around the placeholder location
////                        gc.save(); gc.setStroke(nanValueColor); 
////                        gc.strokeOval(nextPoint.getX()-tenTimesTenPixels.getX()/2, chartCanvas.getHeight()-tenTimesTenPixels.getY()/2, tenTimesTenPixels.getX(), tenTimesTenPixels.getY());
////                        gc.restore();
////                    } else {
////                        nextPointHasNaN = false;
////                        if(! lastPointHasNaN)
//                            gc.strokeLine(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY());
////                    }
//                    
////                    lastPointHasNaN = nextPointHasNaN;
//                    lastPoint = nextPoint;
//                }
//            }
//        }
//        
//    }
    
    public void resetView() {
        // TODO: add a padding of max(5px, 2.5% of the pixel width/height of the canvas)
        double xRange = sharedData.dataModel.getMaxX() - sharedData.dataModel.getMinX();
        double yRange = sharedData.dataModel.getMaxY() - sharedData.dataModel.getMinY();
        if(xRange < 0 || yRange < 0){
            xRange = 1;
            yRange = 1;
                    
        }
        axesRanges.set(new Rectangle2D(sharedData.dataModel.getMinX(), sharedData.dataModel.getMinY(), xRange, yRange));
        drawContents();
    }
    
}
