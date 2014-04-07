package Visualization;

import com.sun.javafx.tk.FontLoader;
import java.util.Locale;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import javafx.util.converter.NumberStringConverter;

/**
 * The component that draws an axis, its tick marks, tick labels and the axis label.
 * Everything is shown by drawing on the canvas that the objects extend.
 * 
 * Ticks are conceived as an infinite set of data coordinate values where tick marks should be placed.
 * The set is defined by an origin O and a tick unit U such that tick = { O + kU | k in N }.
 * See {@link NumberAxis#tickOrigin} and {@link NumberAxis#tickUnit}.
 * When the auto tick strategy is used, the S/R algorithms generates the tick units and zero is always used as origin.
 * Setting unit and origin manually allows tick marks at uneven (though not arbitrary) positions.
 * If you want to switch auto tick mark generation back on, use {@link NumberAxis#setTickPositionType(Visualization.NumberAxis.TICK_GENERATION_METHOD) }.
 * 
 * The term data coordinates refer to the intrinsic dimensions of the data (e.g. 12˚C, 3m, etc.) 
 * as opposed to sceen or view coordinates which refer to the graphics output.
 * 
 * @author Carl Witt
 */
public class NumberAxis extends Canvas {
    
    /** The axis label that is displayed below the tick marks and tick labels. */
    protected String label;

    /** Defines whether the axis is rendered horizontally or vertically. */
    protected boolean isHorizontal = true;
    
    /** Translates data domain coordinates into screen coordinates. */
    private Affine dataToScreen;
    
    // ticks -------------------------------------------------------------------
    
    /** Describes the strategy that the axis uses to */
    public enum TICK_GENERATION_METHOD {
        /** Tick positions are generated using the S/R algorithm */
        AUTO,    
        /** Either tick unit or tick origin has been altered */
        MANUAL   
    }
    /** How to find the data values at which to place tick marks. */
    protected TICK_GENERATION_METHOD tickPositionType = TICK_GENERATION_METHOD.AUTO;
    protected Font tickLabelFont = new Font(10d);
    /** Converts numbers to tick label strings. */
    NumberStringConverter tickLabelFormatter = new NumberStringConverter(Locale.ENGLISH);
    
    /** Specifies the minimum distance between two successive ticks. 
     * E.g. set to one for an axis that displays years to avoid tick marks at 1940.5 */
    protected double minTickUnit = Double.NEGATIVE_INFINITY;

    public NumberAxis(){
    
        getGraphicsContext2D().setFont(tickLabelFont);
    
    }
    
    // -------------------------------------------------------------------------
    // coordinate transformations
    // -------------------------------------------------------------------------
    
    /**
     * @return an affine transform that can be used to convert between data and screen coordinates.
     */
    protected Affine computeDataToScreen() {

        double offsetX, offsetY;    // translate
        double scaleX, scaleY;
        
        if(isHorizontal){
            offsetX = lowerBound.get();
            offsetY = 0;
            scaleX = getWidth() / getRange();
            scaleY = 1;
        } else {
            offsetX = 0;
            offsetY = lowerBound.get();
            scaleX = 1;
            scaleY = getHeight() / getRange();
        }
        
        Transform translate = new Translate(-offsetX, -offsetY);
        Transform scale = new Scale(scaleX, -scaleY);
        Transform mirror = new Translate(0, getHeight());
        return new Affine(mirror.createConcatenation(scale).createConcatenation(translate));
    }
    
    /**
     * Transforms a data coordinate into the according position on the screen.
     * @param value The data coordinate (usually in range [lowerBound, upperBound])
     * @return The pixel coordinate (usually in range [0, width])
     */
    public double toScreen(double value){
        if(isHorizontal)
            return computeDataToScreen().transform(value, 0).getX();
        else
            return computeDataToScreen().transform(0, value).getY();
    }
    
    /**
     * Transforms a screen coordinate along the axis into a data coordinate position. 
     * @param value The pixel coordinate (usually in range [0, width])
     * @return 
     */
    public double fromScreen(double value){
        try {
            if(isHorizontal)
                return computeDataToScreen().inverseTransform(value, 0).getX();
            else
                return computeDataToScreen().inverseTransform(0, value).getY();
        } catch (NonInvertibleTransformException ex) {
            System.err.println(String.format("Axis is malconfigured, the data to screen transform can not be inverted.\n"
                    + "axis label: %s horizontal: %s lower bound: %s upper bound: %s", 
                    label, isHorizontal,lowerBound.get(),upperBound.get()));
            return 0;
        }
    }
    
    // -------------------------------------------------------------------------
    // content drawing 
    // -------------------------------------------------------------------------
    
    /**
     * The main drawing routine that calls all other drawing routines.
     */
    public void drawContents(){
        
        dataToScreen = computeDataToScreen();
        GraphicsContext g = getGraphicsContext2D();
        
        // clear
        g.setFill(new Color(1.,1.,1.,1.));
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // draw axis line
        g.setStroke(Color.GRAY);
        g.setLineWidth(2);
        if(isHorizontal){
            g.strokeLine(0, 0, getWidth(), 0);
        } else {
            g.strokeLine(getWidth(), 0, getWidth(), getHeight());
        }
        // draw axis label
        g.setStroke(Color.BLACK);
        g.setLineWidth(1);
        Point2D textSize = renderedTextSize(label, tickLabelFont);
        if(isHorizontal){
            g.strokeText(label, getWidth()/2 - textSize.getX()/2, getHeight()-5);
        } else {
            g.save();   
            double startX = textSize.getY(),
                   startY = getHeight()/2 + textSize.getX()/2;
            Rotate r = new Rotate(270, startX, startY);
            g.setTransform(r.getMxx(), r.getMyx(), r.getMxy(), r.getMyy(), r.getTx(), r.getTy());
            g.strokeText(label, startX, startY);
            g.restore();
        }
        
        // draw tick marks and labels
        g.setStroke(Color.GRAY);
        
        if(tickPositionType == TICK_GENERATION_METHOD.AUTO){
            
            // calculate a number of ticks that uses the space evenly
            double availableSpace = isHorizontal ? getWidth() : getHeight();
            // finding the width of a label requires a tick unit!
            double exampleTickUnit = tickUnit(20, getLowerBound(), getUpperBound());
            // guess the value with the longest string representation (especially not 0!)
            double largestValue = Math.max(Math.abs(getLowerBound()), getUpperBound());
            // use the string as generated by the formatter (that's why the above is just a guess)
            String exampleLabel = tickLabelFormatter.toString(nextLowerTickMarkValue(largestValue, 0, exampleTickUnit));

            double tickLabelSize = isHorizontal ? 
                    renderedTextSize(exampleLabel, tickLabelFont).getX() : 
                    renderedTextSize(exampleLabel, tickLabelFont).getY();

            int notTooManyTicks = (int) Math.floor(0.5 * availableSpace / tickLabelSize);
            double resultTickUnit = tickUnit(notTooManyTicks, getLowerBound(), getUpperBound());
            tickUnit.set(Math.max(minTickUnit, resultTickUnit));
        }
        
        double nextLower = nextLowerTickMarkValue(getLowerBound(), getTickOrigin(), getTickUnit());
        
        int numTicks = (int) Math.ceil( getRange()/tickUnit.get() );
        for (int i = 0; i < numTicks+5; i++) {
            drawTickMark(g, nextLower+i*tickUnit.get());
        }
        
    }
    
    /** Draws a single tick mark.
     * @param g The graphics context to draw on.
     * @param value The data coordinates value to put a tick mark on.
     */
    protected void drawTickMark(GraphicsContext g, double value){
        
        double tickMarkLength = 3;
        g.setLineWidth(1);
        g.setFont(tickLabelFont);
        
        String tickLabel = tickLabelFormatter.toString(value);
        Point2D textDimensions = renderedTextSize(tickLabel, tickLabelFont);
        
        if(isHorizontal){
            double screenCoordinate = dataToScreen.transform(value, 0).getX();
            // tick mark
            g.strokeLine(screenCoordinate, 0, screenCoordinate, tickMarkLength);
            // tick label
            double labelStartPos = screenCoordinate - textDimensions.getX()/2;
            // don't draw labels that are only partially visible
            if(labelStartPos >= 0 && labelStartPos <= getWidth() - textDimensions.getX()){
                g.strokeText(tickLabel, labelStartPos, tickMarkLength+textDimensions.getY());
            }
        } else {
            double screenCoordinate = dataToScreen.transform(0, value).getY();
            // tick mark
            g.strokeLine(getWidth()-tickMarkLength, screenCoordinate, getWidth(), screenCoordinate);
            // tick label
            double labelStartPos = screenCoordinate + textDimensions.getY()/4;
            // don't draw labels that are only partially visible
            if(labelStartPos >= textDimensions.getY() && labelStartPos <= getHeight()){
                g.strokeText(tickLabel, getWidth() - tickMarkLength - textDimensions.getX() - 2, labelStartPos);
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // tick computation logic 
    // -------------------------------------------------------------------------
    
    /**
     * @param rawValue an arbitrary value on the axis
     * @param tickOrigin TODO: is currently ignored but should be considered when having other offsets than 0 (e.g. tick unit = 5, tick origin = 1 =&gt; 1,6,11)
     * @param tickUnit tick mark values are multiples of this value
     * @return find closest smaller tick mark value to rawValue
     */
    protected double nextLowerTickMarkValue(double rawValue, double tickOrigin, double tickUnit){

        // align raw value to new origin
        rawValue -= tickOrigin;
        
        // take "modulo" of the lower bound and the tick unit
        double div = rawValue / tickUnit;
        // use only the fractional part to get the "residual" of the division
        double mod = tickUnit * (div - Math.floor(div)); 
        
        double result = rawValue - mod;
        return result + tickOrigin;
        // there's nothing like an intrinsic precision: what about 1/3? 
//        double intrinsicPrecision = Math.ceil(Math.log10(Math.abs(result)));
//        // round to that precision to get rid of numerical errors that stem from raising to the power of a logarithm
//        double roundedToIntrinsic = Math.round(result*Math.pow(10, -intrinsicPrecision)) * Math.pow(10, intrinsicPrecision);
//        
//        return roundedToIntrinsic;
    }
    /**
     * S/R algorithm for tick units
     * @param tickIntervals The number of desired tick intervals (the number of ticks will be tickIntervals + 1)
     * @param bounds The value range (DC) to split into intervals
     * @return The distance (DC) between successive ticks
     */
    protected double tickUnit(int tickIntervals, Double... bounds){
        
        // extend range by 3% 
        double range = bounds[1] - bounds[0];
        bounds[0] -= range/100 * 3; 
        bounds[1] += range/100 * 3;
        double extendedRange = bounds[1] - bounds[0];
        
        // compute the step that results from even division of the range in N intervals
        double rawStep = extendedRange / tickIntervals;
//System.out.println(String.format("raw step %s", rawStep));

        // choose the nearest even power of 10
        double logStep = Math.log10(rawStep);
        
        // nur der ganzzahlige anteil -1.5 => -1;  1.3 => 1
        double integerPart = logStep > 0 ? Math.floor(logStep) : Math.ceil(logStep);
        // nur die Nachkommastellen -1.5 => 0.5; 1.3 => 0.3
//System.out.println(String.format("logStep %s, int part %s ", logStep, integerPart));
        
        double[] choicesPositive = new double[]{0d, Math.log10(2), Math.log10(5), 1d};
        double[] choicesNegative = new double[]{-1d, -Math.log10(5), -Math.log10(2), -0d};
        double[] choices = logStep > 0 ? choicesPositive : choicesNegative;
        
        double minDistance = Double.POSITIVE_INFINITY;
        int minDistanceIdx = 1;
        for (int i = 0; i < choices.length; i++) {
//System.out.println(String.format("distance logstep to %s = %s ", integerPart + choices[i], Math.abs(logStep - (integerPart + choices[i]))));
            double distance = Math.abs(logStep - (integerPart + choices[i]));
            if( distance < minDistance){
                minDistance = distance;
                minDistanceIdx = i;
            }
        }
        
        double niceStep = Math.pow(10, integerPart + choices[minDistanceIdx]);
        
        // the order of magnitude where the nice tick unit is located: 200 => 2; 0.005 => -3
        double intrinsicPrecision = Math.floor(logStep);
        // round to that precision to get rid of numerical errors that stem from raising to the power of a logarithm
        niceStep = Math.round( niceStep * Math.pow(10, -intrinsicPrecision)) * Math.pow(10, intrinsicPrecision);
        
//System.out.println(String.format("log step %s, closest log_10 base is %s + %s ", logStep, (int)integerPart, choices[minDistanceIdx]));
            
        return niceStep;
    }

    
    /**
     * Computes the width and height of string. Used to align tick labels.
     * @param string The string to draw.
     * @param font The font name and font size in which the string is drawn.
     * @return The width (x component) and height (y component) of the string if plotted.
     */
    FontLoader fontLoader = com.sun.javafx.tk.Toolkit.getToolkit().getFontLoader();
    protected Point2D renderedTextSize(String string, Font font){
        return new Point2D(fontLoader.computeStringWidth(string, font),fontLoader.getFontMetrics(font).getLineHeight());
    }
    
    // -------------------------------------------------------------------------
    // property definitions
    // -------------------------------------------------------------------------
    
    /** The smallest value on the axis (doesn't necessarily correspond to a tick position). */
    private final DoubleProperty lowerBound = new SimpleDoubleProperty();
    public DoubleProperty lowerBoundProperty() { return lowerBound; }
    public double getLowerBound() { return lowerBound.get(); }
    public void setLowerBound(double value) {
        lowerBound.set(value);
    }
    
    /** The largest value on the axis (doesn't necessarily correspond to a tick position). */
    private final DoubleProperty upperBound = new SimpleDoubleProperty();
    public DoubleProperty upperBoundProperty() { return upperBound; }
    public double getUpperBound() { return upperBound.get(); }
    public void setUpperBound(double value) {
        upperBound.set(value);
    }

    /** Returns the total range that is covered by the axis. */
    public double getRange(){
        return upperBound.get() - lowerBound.get();
    }
    
    /** Defines a value (data coordinates) that is used to derive the set of tick marks. */
    private final DoubleProperty tickOrigin = new SimpleDoubleProperty(0);
    public double getTickOrigin() { return tickOrigin.get(); }
    public DoubleProperty tickOriginProperty() { return tickOrigin; }
    public void setTickOrigin(double value) { tickOrigin.set(value); }

    /** Defines the distance between ticks. */
    private final DoubleProperty tickUnit = new SimpleDoubleProperty(1);
    public double getTickUnit() { return tickUnit.get(); }
    public DoubleProperty tickUnitProperty() { return tickUnit; }
    public void setTickUnit(double value) { tickUnit.set(value); }
    
    public void setLabel(String label) { this.label = label; }
    public void setIsHorizontal(boolean isHorizontal) { this.isHorizontal = isHorizontal; }

    public final void setTickLabelFormatter(NumberStringConverter sc) {
        this.tickLabelFormatter = sc;
    }
    
    /** @return the tick position generation strategy */
    public TICK_GENERATION_METHOD getTickPositionType(){ return tickPositionType; }
    public void setTickPositionType(TICK_GENERATION_METHOD m){ this.tickPositionType = m; }
    
    public double getMinTickUnit() { return minTickUnit; }
    public void setMinTickUnit(double minTickUnit) { this.minTickUnit = minTickUnit; }
    
}
