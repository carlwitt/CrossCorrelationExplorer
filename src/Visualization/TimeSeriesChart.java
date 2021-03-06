package Visualization;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix;
import Data.SharedData;
import Data.TimeSeries;
import Global.Util;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to draw the time series. Supports basic aggregation by drawing only each N-th data point.
 * Serves as a base class for different versions of the chart.
 * @author Carl Witt
 */
public class TimeSeriesChart extends CanvasChart implements DeferredDrawing {

    public SharedData sharedData;
    public HashMap<Color, ObservableList<TimeSeries>> seriesSets = new HashMap<>();
    
    private final IntegerProperty drawEachNthDataPoint = new SimpleIntegerProperty(20);
    public final void setDrawEachNthDataPoint(int step){ drawEachNthDataPoint.set(step); }
    final int getDrawEachNthDataPoint(){ return drawEachNthDataPoint.get(); }
    public final IntegerProperty drawEachNthDataPointProperty(){ return drawEachNthDataPoint; }

    protected final BooleanProperty[] drawEnsemble = new BooleanProperty[]{new SimpleBooleanProperty(true), new SimpleBooleanProperty(true)};
    public final BooleanProperty drawEnsemble1Property(){ return drawEnsemble[0]; }
    public final BooleanProperty drawEnsemble2Property(){ return drawEnsemble[1]; }

    public TimeSeriesChart(){

        xAxis.setMinTickUnit(1);

        // configure min width and height
        setMinHeight(10); setMinWidth(10);
        // the correlogram is kept large enough by the container constraints, but setting the preferred width to
        // the computed size can cause the correlogram to get stuck on a width that's too large for the container
        // (because there's no inherent way to compute the necessary space for a canvas).
        setPrefWidth(10); setPrefHeight(10);

        drawEnsemble1Property().addListener((observable, oldValue, newValue) -> drawContents());
        drawEnsemble2Property().addListener((observable, oldValue, newValue) -> drawContents());

        setAxisHinting(true, true);

    }

    // TODO check necessity for transparency
    public double transparency = 0.05;

    /**
     * Using the canvas path operators (beginPath, closePath, moveTo, lineTo) turns out to be much slower,
     * although it has the advantage of not creating render artifacts at the joints of the line segments which
     * occur when rendering with transparency.
     */
    @Override public void drawContents() {

        if(isDeferringDrawRequests){
            redrawPending = true;
            return;
        }

        GraphicsContext gc = chartCanvas.getGraphicsContext2D();

        // reset transform to identity, clear previous contents
        gc.setTransform(new Affine(new Translate()));
        gc.clearRect(0, 0, chartCanvas.getWidth(), chartCanvas.getHeight());
        gc.setLineWidth(1);
        gc.setMiterLimit(0);

        // compute affine transform that maps data coordinates to screen coordinates
        Affine dataToScreen = dataToScreen();

        // simple complexity reduction: display only every n-th point
        final int step = Math.max(1, getDrawEachNthDataPoint());    // step is at least one

        // highlight time window (selected by mouse hover from the correlogram)
        // time series value indices that are within the bounds of the currently highlighted window
        // the value at highlightWindowTo is not in the window, but the displayed window extends up to it
        int highlightTimeLag = 0, highlightWindowFrom = Integer.MAX_VALUE, highlightWindowTo = Integer.MAX_VALUE;

        CorrelationMatrix.CorrelationColumn highlightedColumn = sharedData.getHighlightedColumn();
        CorrelationMatrix matrix = sharedData.getCorrelationMatrix();
//        if(highlightedColumn != null && matrix != null && matrix.metadata.setA.size() > 0){
//
//            // find highlight window parameters
//            int windowSize = matrix.metadata.windowSize;
//            highlightWindowFrom = highlightedColumn.windowStartIndex;
//            highlightWindowTo = highlightWindowFrom + windowSize;
//            highlightTimeLag = highlightedColumn.tauMin + sharedData.getActiveCorrelationMatrixRegion().y; // render time series in set B in the highlighted window shifted by this lag
//            // draw the window
//            TimeSeries representativeTimeSeries = matrix.metadata.setA.get(0);
//            double[] xValues = representativeTimeSeries.getDataItems().re;
//            gc.setStroke(Color.YELLOW);
//            double widthOnScreen = xAxis.toScreen(xValues[highlightWindowTo]) - xAxis.toScreen(xValues[highlightWindowFrom]);
//            gc.strokeRect(
//                    xAxis.toScreen(xValues[highlightWindowFrom]), 0,
//                    widthOnScreen, getHeight());
//        }

        // draw each set
        for (Map.Entry<Color, ObservableList<TimeSeries>> coloredSet : seriesSets.entrySet()) {

            // whether to render the time series shifted by the selected lag in the highlight window
            // shift only those in correlation set B
            boolean drawShift  = false; //coloredSet.getKey().equals(setBColor) && highlightTimeLag < Integer.MAX_VALUE;

//            gc.setStroke(coloredSet.getKey().deriveColor(0,1,1,0.1));
            gc.setStroke(coloredSet.getKey().deriveColor(0,1,1,transparency));
//            gc.setStroke(coloredSet.getKey());
            gc.setLineWidth(1.5);

//System.out.print(String.format("\ndrawing: %s",coloredSet.getValue().size()));
            // draw each time series
            for (TimeSeries ts : coloredSet.getValue()) {
//System.out.print(String.format("%s ", ts));
                ComplexSequence data = ts.getDataItems();
                Point2D curPoint, prevPoint = dataToScreen.transform(new Point2D(data.re[0], data.im[0]));

                // whether the current point in the currently highlighted window (where time series are shifted by the highlighted lag)
                boolean inWindow = false;

                // render a given number of points
                int nextPointIdx = step;
                int xShift = 0;
                for (int i = step; i < data.re.length; i += step) {

                    // the index of the point that will be rendered as the next point

                    // if the renderer enters the highlighted window
                    if(drawShift && !inWindow && i >= highlightWindowFrom+1 && i <= highlightWindowTo){

                        inWindow = true;
                        // the next point to be displayed must be shifted
                        nextPointIdx += highlightTimeLag;
                        if(nextPointIdx >= data.re.length) break;
                        xShift = highlightTimeLag;
                        // also shift the previous point
                        int prevPointIdx = Math.max(0, nextPointIdx-step);
                        prevPoint = dataToScreen.transform(new Point2D(data.re[prevPointIdx-xShift], data.im[prevPointIdx]));
                    } else
                    // if the renderer leaves the highlighted window
                    if(drawShift && inWindow && i > highlightWindowTo){

                        inWindow = false;
                        // unshift current point
                        nextPointIdx -= highlightTimeLag;
                        xShift = 0;
                        // unshift previous point
                        int prevPointIdx = nextPointIdx-step;
                        prevPoint = dataToScreen.transform(new Point2D(data.re[prevPointIdx], data.im[prevPointIdx]));
                    }

                    double x=0,y=0;
                    try {
                         x = data.re[nextPointIdx-xShift];
                         y = data.im[nextPointIdx];
                    } catch(Exception e){}

                    curPoint = dataToScreen.transform(new Point2D(x, y));

                    // connect current data point to previous data point with a line
                    gc.strokeLine(prevPoint.getX(), prevPoint.getY(), curPoint.getX(), curPoint.getY());

                    prevPoint = curPoint;
                    nextPointIdx += step;
                }
            }
        }

        xAxis.drawContents();
        yAxis.drawContents();

        redrawPending = false;
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
//        DataModel dataModel = sharedData.experiment.dataModel;
//        double maxX = Math.max(dataModel.getMaxX(0), dataModel.getMaxX(1));
//        double minX = Math.min(dataModel.getMinX(0), dataModel.getMinX(1));
//        double xRange = maxX - minX;
//        double maxY = Math.max(dataModel.getMaxY(0), dataModel.getMaxY(1));
//        double minY = Math.min(dataModel.getMinY(0), dataModel.getMinY(1));
//        double yRange = maxY - minY;
//        if(xRange < 0 || yRange < 0){
//            xRange = 1;
//            yRange = 1;
//
//        }
//        Bounds newVisibleRange = new BoundingBox(minX, minY, xRange, yRange);

        clipRegionDC.set(Util.union(xAxis.getScrollBarBoundsDC(),yAxis.getScrollBarBoundsDC()));
//        drawContents();
    }

    public void setSharedData(SharedData sharedData){
        this.sharedData = sharedData;

        sharedData.activeCorrelationMatrixRegionProperty().addListener((observable, oldValue, newValue) -> drawContents());
    }

    /** Is true iff a draw has been issued while the component was deferring draw requests (e.g. not visible). */
    boolean redrawPending = false;
    /** If true, all draw requests will be reduced to one, executed as soon as the flag is set to false. If false, all draw requests are instantly executed. */
    boolean isDeferringDrawRequests = false;
    public boolean isDeferringDrawRequests() { return isDeferringDrawRequests; }
    public void setDeferringDrawRequests(boolean deferDrawRequests) {
        // defers and not defer: draw, set
        // defers and defer: nop (set)
        // doesn't defer and not defer: nop, (set)
        // doesn't defer and defer: set
        if(this.isDeferringDrawRequests && ! deferDrawRequests){
            this.isDeferringDrawRequests = false;               // set to false otherwise the draw will not be executed.
            drawContents();
        } else {
            this.isDeferringDrawRequests = deferDrawRequests;
        }
    }
}
