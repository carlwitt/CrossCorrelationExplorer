package Visualization;

import java.awt.Color;
import java.awt.Paint;
import javafx.scene.chart.ValueAxis;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramParameters {

    /** Specifies axis labels, ticks, etc. 
     * The time axis is displayed horizontally, 
     * time lag axis is displayed vertically, 
     * the legend axis is displayed vertically alongside the color legend. */
    public ValueAxis timeAxis, timeLagAxis, legendAxis;
    
    public int minTimeLag, maxTimeLag;
    
    /** The scale that determines the color to which a value is mapped. */
    MultiDimensionalPaintScale paintScale = new MultiDimensionalPaintScale(null, null);
    
    /** This object can be manipulated after creation to specify properties of the legend, such as position, width, etc. */
//    public PaintScaleLegend legend;
    
    /** Which color to use to render minimum and maximum correlation values. Other values are color interpolated. */
    public Color minColor = Color.BLUE, maxColor = Color.RED;
    
//    private int numColors = 12; 
    
    public CorrelogramParameters(){
//        updatePaintScale();
    }
    
//    protected void updatePaintScale(){
//        // TODO: add color initialization code
//    }
    
//    public void setNumberOfColors(int number){
//        numColors = number;
//        updatePaintScale();
//    }
            
    public CorrelogramParameters setTimeAxis(ValueAxis timeAxis){
        this.timeAxis = timeAxis;
        return this;
    }
    public CorrelogramParameters setTimeLagAxis(ValueAxis timeLagAxis){
        this.timeLagAxis = timeLagAxis;
        return this;
    }
    public CorrelogramParameters setLegendAxis(ValueAxis legendAxis){
        this.legendAxis = legendAxis;
        return this;
    }
    
    public CorrelogramParameters setMinColor(Color minColor){
        this.minColor = minColor;
        return this;
    }
    public CorrelogramParameters setMaxColor(Color maxColor){
        this.maxColor = maxColor;
        return this;
    }
    
}
