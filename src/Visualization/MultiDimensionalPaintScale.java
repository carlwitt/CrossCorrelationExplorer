package Visualization;

import Visualization.Color.Color_manager;
import Visualization.Color.Datatype;
import com.google.common.base.Joiner;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Vector;
/**
 * Converts value pairs into colors. The first dimension is encoded as a hue value, the second is encoded as decreasing saturation.
 * Inspected project phase 2 (16.5.2014).
 * @author Carl Witt
 */
public class MultiDimensionalPaintScale 
{
    
    /**
     * The two colors that define the range between which colors are interpolated.<br>
     * If the palette is bipolar, the interpolation will be primary color ... white ... secondary color.<br>
     * If the palette is not bipolar, the interpolation will be primary color ... white
     */
    private Color primaryColor = Color.BLUE,
                  secondaryColor = Color.RED;

    /** Whether the palette is a bipolar or a unipolar color palette. */
    private boolean biPolar = true;
    
    private Double[] lowerBounds = new Double[2];
    private Double[] upperBounds = new Double[2];
    
    /** How many discrete hue values are used to encode the first dimension. */
    private int hueDepth;
    /** How many saturation steps are used to encode the second dimension. */
    private int saturationDepth;
    /** The computed colors. First dimension refers to saturation, second to hue. */
    private Color colors[][];
    
    public MultiDimensionalPaintScale(int hueDepth, int saturationDepth){
        
        this.hueDepth = hueDepth;// == null ? DEFAULT_COLOR_DEPTH : hueDepth;
        this.saturationDepth  = saturationDepth; //== null ? DEFAULT_SAT_DEPTH : saturationDepth;

        compute();
        
    }
    
    /** Uses the state of the palette (primary and secondary color, resolution, bipolar or not) to compute the colors. */
    final void compute(){
        
        // use the color manager to generate an optically optimized gradient between colors
        Color_manager colorManager;
        Vector<Datatype.Color> palette;
        
        if(this.biPolar){
            // even color depth with bipolar scale is not allowed because the color manager always generates two-color scales with an odd number of colors
            if( hueDepth % 2 == 0 ){ hueDepth++; }
            
            colorManager = new Color_manager((int)Math.ceil((hueDepth +1)/2.f));
            colorManager.addColorPalette(new float[]{
                (float)primaryColor.getHue(), 
                (float)secondaryColor.getHue()
            });
            palette = colorManager.getColorPalettes().get(0);
        } else {
            colorManager = new Color_manager(hueDepth);
            colorManager.addColorPalette(new float[]{
                (float)primaryColor.getHue()
            });
            palette = colorManager.reverse(colorManager.getColorPalettes().get(0));
        }

        // allocate new space
        colors = new Color[this.saturationDepth][this.hueDepth];
        
        for (int i = 0; i < hueDepth; i++) {
            
            for (int j = 0; j < saturationDepth; j++) {
                Datatype.Color color = palette.get(i);
                
                // the factor (in range 0..1) by which the saturation is multiplied compared to the base color
                // the following gives stronger decay in the beginning and a softer decay in the end leading to a visually more continuous saturation loss
                double satPercent = (double)j/(saturationDepth-1);
                double saturationReduction = -Math.log(0.45*satPercent+1/Math.E);// some arbitrary function that gives a smooth fallof
                
                colors[j][i] = Color
                        .rgb((int)color.dimension_1,(int)color.dimension_2,(int)color.dimension_3)
                        .deriveColor(0, saturationReduction, 1, 1); // reduce saturation
            }
        }
    }

    double interpolate(double d, int dim){
        double offset = d - lowerBounds[dim];
        double range = upperBounds[dim] - lowerBounds[dim];
        return range < 1e-10 ? offset : offset / range;
    }
    

    /**
     * Returns the associated color
     * @return A color encoding the value pair.
     */
    Paint getPaint(double d0, double d1) {

        if(Double.isNaN(d0) || Double.isNaN(d1)) return Color.GRAY;

        float saturationPercent = (float) Math.min(1, interpolate(d1, 1));
        float huePercent = (float) Math.min(1, interpolate(d0, 0));

//        double saturationPercent = interpolate(d1, 1);
//        double huePercent = interpolate(d0, 0);

//        int satIndex = (int) Math.round((saturationDepth-1) * saturationPercent);
//        int hueIndex = (int) Math.round((hueDepth -1) * huePercent);

        int satIndex = (int) ((saturationDepth-1) * saturationPercent);
        int hueIndex = (int) ((hueDepth -1) * huePercent);

//        if(huePercent > 1 || saturationPercent > 1 || huePercent < 0 || saturationPercent < 0)
//        System.out.println(String.format(
//                "in values: %s\n"
//                + "lower bounds: %s, upper bounds: %s\n"
//                + "hue percent: %s, hue: %s\n"
//                + "sat percent: %s, sat: %s",
//                Arrays.toString(new double[]{d0,d1}),
//                Arrays.toString(lowerBounds), Arrays.toString(upperBounds),
//                huePercent, hueIndex,
//                saturationPercent, satIndex));

        return colors[satIndex][hueIndex];
    }

    Paint getPaint(double d){
        if(Double.isNaN(d)) return Color.GRAY;

        float saturationPercent = (float) Math.min(1, interpolate(d, 1));
        float huePercent = (float) Math.min(1, interpolate(d, 0));

        int satIndex = (int) ((saturationDepth-1) * saturationPercent);
        int hueIndex = (int) ((hueDepth -1) * huePercent);

        return colors[0][hueIndex];
    }
    
    // dumps the generated palettes of this paint scale as rgb values [r,g,b] in javascript array notation
    // format: [ [ [color1 satdepth1], [color2 satdepth2], ... ], [colors belonging to saturation depth 2], ... ]
    protected void printPalettesJSON(){
        
        System.out.println("[");
        
        for (int row = 0; row < saturationDepth; row++) {
            ArrayList<String> colorCodes = new ArrayList<>();
            
            for (int col = 0; col < hueDepth; col++) {
                Color color = colors[row][col];
                colorCodes.add(String.format("[%s,%s,%s]",(int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255)));
            }
            System.out.println(String.format("[%s],",Joiner.on(",").join(colorCodes)));
        }
        
        System.out.println("]");
        
    }
    
    public Double[] getLowerBounds() { return this.lowerBounds; }
    
    public MultiDimensionalPaintScale setLowerBounds(Double... d) {
        this.lowerBounds = d;
        return this;
    }
    
    public Double[] getUpperBounds() { return this.upperBounds; }
    
    public MultiDimensionalPaintScale setUpperBounds(Double... d) {
        this.upperBounds = d;
        return this;
    }
    
    public Color getPrimaryColor() { return primaryColor; }
    
    public void setPrimaryColor(Color primaryColor) {
        this.primaryColor = primaryColor;
        compute();
    }
    public Color getSecondaryColor() { return secondaryColor; }
    
    public void setSecondaryColor(Color secondaryColor) {
        this.secondaryColor = secondaryColor;
        compute();
    }

    public boolean isBiPolar() { return biPolar; }
    
    public void setBiPolar(boolean biPolar) {
        this.biPolar = biPolar;
        compute();
    }

    public int getHueDepth() { return hueDepth; }
    public void setHueDepth(int hueDepth) {
        this.hueDepth = hueDepth;
        compute();
    }

    public int getSaturationDepth() { return saturationDepth; }
    public void setSaturationDepth(int saturationDepth) {
        this.saturationDepth = saturationDepth;
        compute();
    }
    
    public Color[][] getColors() { return colors; }
}
