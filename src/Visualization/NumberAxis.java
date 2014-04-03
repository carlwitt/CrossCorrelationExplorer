package Visualization;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * The data model for a number axis, i.e. independent of any visualization. Computes 
 *  - the display value range to for a given value range (padding)
 *  - the values where tick marks should be placed
 *  - the tick labels
 * 
 * Ticks are conceived as an infinite set of data coordinate values where tick marks should be placed.
 * {@link NumberAxis#getTickMarks} returns those marks that are within the current range of the axis.
 * The set is defined by an origin O and a tick unit U such that tick = { O + kU | k in N }.
 * See {@link NumberAxis#tickOrigin} and {@link NumberAxis#tickUnit}.
 * When the auto tick strategy is used, the S/R algorithms generates the tick units and zero is always used as origin.
 * Setting unit and origin manually allows tick marks at uneven (though not arbitrary) positions.
 * 
 * The term data coordinates refer to the intrinsic dimensions of the data (e.g. 12˚C, 3m, etc.) 
 * as opposed to sceen or view coordinates that translate it to a position in the graphics output.
 * 
 * @author Carl Witt
 */
public class NumberAxis {
    
    /** Describes the strategy that the axis uses to */
    public enum TICK_POSITIONS {
        /** Tick positions are generated using the S/R algorithm */
        AUTO,    
        /** Either tick unit or tick origin has been altered */
        MANUAL   
    }
    
    protected TICK_POSITIONS tickPositionType = TICK_POSITIONS.AUTO;
    /** @return the tick position generation strategy 
    public TICK_POSITIONS getTickPositionType(){ return tickPositionType; }
     
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

    /** Defines a value (data coordinates) that is used to derive the set of tick marks. */
    private final DoubleProperty tickOrigin = new SimpleDoubleProperty();
    public double getTickOrigin() { return tickOrigin.get(); }
    public DoubleProperty tickOriginProperty() { return tickOrigin; }
    public void setTickOrigin(double value) {
        tickOrigin.set(value);
    }

    /** Defines the distance between ticks. */
    private final DoubleProperty tickUnit = new SimpleDoubleProperty();
    public double getTickUnit() { return tickUnit.get(); }
    public DoubleProperty tickUnitProperty() { return tickUnit; }
    public void setTickUnit(double value) {
        this.tickPositionType = TICK_POSITIONS.MANUAL;
        tickUnit.set(value);
    }

    // -------------------------------------------------------------------------
    // custom code
    // -------------------------------------------------------------------------
    
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
        
//System.out.println(String.format("extended range: [%s, %s] = %s", bounds[0], bounds[1], extendedRange));

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

    
    
    /** Returns the total range that is covered by the axis. */
    public double getRange(){
        return upperBound.get() - lowerBound.get();
    }
    
    


    
    /** The mark position and its label */
    public static class TickMark{
        public double position; /** The position where to place the tick mark (data coordinates) */
        public String label;
    }
}
