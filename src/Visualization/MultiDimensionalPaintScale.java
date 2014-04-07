package Visualization;

import Visualization.Color.Datatype;
import Visualization.Color.Color_manager;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
/**
 * Converts value pairs into colors. The first dimension is encoded as a hue value, 
 * the second is encoded by decreasing saturation of the color used in the first dimension.
 * @author Carl Witt
 */
public class MultiDimensionalPaintScale 
{
    
    /** The two colors that define the range between which colors are interpolated.<br>
     * If the palette is bipolar, the interpolation will be primary color ... white ... secondary color.<br>
     * If the palette is not bipolar, the interpolation will be primary color ... white */
    protected Color primaryColor = Color.BLUE, secondaryColor = Color.RED;

    /** Whether the palette is a bipolar color palette. */
    protected boolean biPolar = true;
    
    protected Double[] lowerBounds = new Double[2], upperBounds = new Double[2];
    
    /** How many base hue values are used to encode the first dimension. */
    protected int colorDepth;
    /** How many saturation steps are used to encode the second dimension. */
    protected int saturationDepth;
    /** The computed colors. First dimension refers to base color, second to saturation. */
    private Color colors[][];
    
    public MultiDimensionalPaintScale(int colorDepth, int saturationDepth){
        
        this.colorDepth = colorDepth;// == null ? DEFAULT_COLOR_DEPTH : colorDepth;
        this.saturationDepth  = saturationDepth; //== null ? DEFAULT_SAT_DEPTH : saturationDepth;

        compute();
        
    }
    
    /** Uses the state of the palette (primary and secondary color, resolution, bipolar or not) to compute the colors. */
    protected final void compute(){
        
        // use the color manager to generate an optically optimized gradient between colors
        Color_manager colorManager;
        Vector<Datatype.Color> palette;
        
        if(this.biPolar){
            // even color depth with bipolar scale is not allowed because the color manager always generates two-color scales with an odd number of colors
            if( colorDepth % 2 == 0 ){ colorDepth++; }
            
            colorManager = new Color_manager((int)Math.ceil((colorDepth+1)/2.f)); 
            colorManager.addColorPalette(new float[]{
                (float)primaryColor.getHue(), 
                (float)secondaryColor.getHue()
            });
            palette = colorManager.getColorPalettes().get(0);
        } else {
            colorManager = new Color_manager(colorDepth); 
            colorManager.addColorPalette(new float[]{
                (float)primaryColor.getHue()
            });
            palette = colorManager.reverse(colorManager.getColorPalettes().get(0));
        }

        colors = new Color[this.saturationDepth][this.colorDepth];
        
        for (int i = 0; i < colorDepth; i++) {
            
            for (int j = 0; j < saturationDepth; j++) {
                Datatype.Color color = palette.get(i);
                
                // the factor (in range 0..1) by which the saturation is multiplied compared to the base color
                // the following gives stronger decay in the beginning and a softer decay in the end leading to a visually more continuous saturation loss
                double satPercent = (double)j/(saturationDepth-1);
                double saturationReduction = -Math.log(0.45*satPercent+1/Math.E);// 1.f - 0.85 * ( (float)j/(saturationDepth-1) );
                
                colors[j][i] = Color
                        .rgb((int)color.dimension_1,(int)color.dimension_2,(int)color.dimension_3)
                        .deriveColor(0, saturationReduction, 1, 1); // leave all unchanged except saturation
            }
        }
    }

    protected double interpolate(double d, int dim){
        double offset = d - lowerBounds[dim];
        double range = upperBounds[dim] - lowerBounds[dim];
        return range < 1e-10 ? offset : offset / range;
    }
    
    int HUE_DIM = 0;
    int SAT_DIM = 1;
    /**
     * Returns the associated color
     * @param x The value in the first dimension.
     * @param y The value in the second dimension.
     * @return A color encoding the value pair.
     */
    Paint getPaint(Double... d) {
        
        float saturationPercent = (float) interpolate(d[SAT_DIM], SAT_DIM);
        float huePercent = (float) interpolate(d[HUE_DIM], HUE_DIM);
        
        int satIndex = Math.round((saturationDepth-1) * saturationPercent);
        int hueIndex = Math.round((colorDepth-1) * huePercent);
        
        if(huePercent > 1 || saturationPercent > 1 || huePercent < 0 || saturationPercent < 0)
        System.out.println(String.format(
                "in values: %s\n"
                + "lower bounds: %s, upper bounds: %s\n"
                + "hue percent: %s, hue: %s\n"
                + "sat percent: %s, sat: %s",
                Arrays.toString(d), 
                Arrays.toString(lowerBounds), Arrays.toString(upperBounds),
                huePercent, hueIndex,
                saturationPercent, satIndex));
        
        Color c;
//        if(hueIndex < 0 || hueIndex >= colors.length){
//            c = Color.BLACK;
//        } else {
            c = colors[satIndex][hueIndex];
//        }
       
        return c;
    }
    
    // dumps the generated palettes of this paint scale as rgb values [r,g,b] in javascript array notation
    // format: [ [ [color1 satdepth1], [color2 satdepth2], ... ], [colors belonging to saturation depth 2], ... ]
    protected void printPalettesJSON(){
        
        System.out.println("[");
        
        for (int row = 0; row < saturationDepth; row++) {
            ArrayList<String> colorCodes = new ArrayList<String>();
            
            for (int col = 0; col < colorDepth; col++) {
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

    public int getColorDepth() { return colorDepth; }
    public void setColorDepth(int colorDepth) {
        this.colorDepth = colorDepth;
        compute();
    }

    public int getSaturationDepth() { return saturationDepth; }
    public void setSaturationDepth(int saturationDepth) {
        this.saturationDepth = saturationDepth;
        compute();
    }
    
    public Color[][] getColors() { return colors; }
}
