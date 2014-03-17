package Visualization;

import Visualization.Color.Datatype;
import Visualization.Color.Color_manager;
import com.google.common.base.Joiner;
import java.awt.Color;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
/**
 * Interpolates colors between min (blue) and max (red) via green 
 * As in the standard HSV color spectrum: http://en.wikipedia.org/wiki/HSV_color_space
 * @author Carl Witt
 */
public class MultiDimensionalPaintScale 
{
    double[] lowerBounds = new double[2], upperBounds = new double[2];
    
    Color_manager colorManager;
    /** How many base hue values are used to encode the first dimension. */
    int numColors;
    /** How many saturation steps are used to encode the second dimension. */
    int satDepth;
    /** The computed colors. First dimension refers to base color, second to saturation. */
    Datatype.Color colors[][];
    
    public static int DEFAULT_NUM_COLORS = 32;
    public static int DEFAULT_SAT_DEPTH = 5;
    
    public MultiDimensionalPaintScale(Integer numberOfColors, Integer saturationDepth){
        
        numColors = numberOfColors == null ? DEFAULT_NUM_COLORS : numberOfColors;
        satDepth  = saturationDepth == null ? DEFAULT_SAT_DEPTH : saturationDepth;
        
       computePaletteRainbow();
//       computeBiPolarPalette();
//        numColors = numberOfColors == null ? DEFAULT_NUM_COLORS : numberOfColors;
//        satDepth = saturationDepth == null ? DEFAULT_SAT_DEPTH : saturationDepth;
//        
//        colorManager = new Color_manager(numColors); 
//        colorManager.addColorPalette(new float[]{240f, 0f});
//        
//        colors = new Datatype.Color[2*numColors-1][satDepth];
//        numColors = 2*numColors-1;
//        
//        Vector<Datatype.Color> palette = colorManager._color_palettes.get(0);
//        System.out.println("palette: "+palette.size());
//        
//        for (int i = 0; i < numColors; i++) {
//            
//            for (int j = 0; j < satDepth; j++) {
//                colors[i][j] = palette.get(i);
//                System.out.println("["+palette.get(i).printColorRGB()+"],");
//            }
//        }
//        
//        printPalettesJSON();
        
    }
    
    // deprecated
    private void computePaletteRainbow(){

        int skipFirstSatValues = 2; //satDepth/4;

        colorManager = new Color_manager(satDepth); // the first depth level is dismissed because it's too dark
        colors = new Datatype.Color[numColors][satDepth-skipFirstSatValues];
        // create a gradient for each color 
        for (int baseColor = 0; baseColor < numColors; baseColor++) {

            // use hue values between blue (240˚) and red (0˚) 
            colorManager.addColorPalette(new float[]{220f-baseColor*160/(numColors-1)});
            Vector<Datatype.Color> palette = colorManager._color_palettes.get(baseColor);

            // write colors from vector to array
            for (int saturation = skipFirstSatValues; saturation < palette.size(); saturation++) {
                colors[baseColor][saturation-skipFirstSatValues] = palette.get(saturation);
            }
        }
        satDepth-=skipFirstSatValues;
    //        printPalettesJSON();
    
    }
    
    int HUE_DIM = 0;
    int SAT_DIM = 1;
    
    public Paint getPaint(double value) {
        return getPaint(new double[]{value,0,0});
    }
    
    public double getLowerBound() {
        return lowerBounds[0];
    }
    
    public double getUpperBound() {
        return upperBounds[0];
    }
    public MultiDimensionalPaintScale setLowerBound(double lowerBound) {
        this.lowerBounds[0] = lowerBound;
        return this;
    }
    public MultiDimensionalPaintScale setUpperBound(double upperBound) {
        this.upperBounds[0] = upperBound;
        return this;
    }

    public MultiDimensionalPaintScale setLowerBounds(double[] d) {
        this.lowerBounds = d;
        return this;
    }

    public MultiDimensionalPaintScale setUpperBounds(double[] d) {
        this.upperBounds = d;
        return this;
    }

    public double interpolate(double d, int dim){
        double offset = d - lowerBounds[dim];
        double range = upperBounds[dim] - lowerBounds[dim];
        return range < 1e-10 ? offset : offset / range;
        
        
//        Color hsb = Color.getHSBColor((float)hue, 1.f, 1.f);
//        return new Color(hsb.getRed(), hsb.getGreen(), hsb.getBlue(), (int)(255*Math.random()));//Color.getHSBColor((float)hue, 1.f, 1.f);
//        System.out.println(String.format("Color request for value = %s\nMin = %s, Max = %s\noffset = %s, percent = %s\nhue = %s",value,getLowerBound(),getUpperBound(),offset, percent,hue));
    }
    Paint getPaint(double[] d) {
        
        // 2/3 ~ 240˚ is a blue hue value (if value equals lowerBound)
        // 1/3 is green
        // 0 is red (if value equals upperBound)
        float huePercent = (float) interpolate(d[HUE_DIM], HUE_DIM);
//        float hue = 2f/3f - 2f/3f*huePercent;
        
        float saturationPercent = (float) interpolate(d[SAT_DIM], SAT_DIM);
        // when the paintscale legend is rendered, it requests always stdDev = 0, which might be 
        // below the min stdDev of the data set from which the lower bounds where passed to the paint scale.
        saturationPercent = Math.max(saturationPercent,0);
        
//        float saturation = Math.max(0.2f, 1-saturationPercent);
       
        int hueIndex = Math.round((numColors-1) * huePercent);
        int satIndex = Math.round((satDepth-1) * saturationPercent);
        
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
        
        Datatype.Color c;
        if(hueIndex < 0 || hueIndex >= colors.length){
            c = new Datatype.Color(Datatype.ColorType.RGBA, 0, 0, 0);
        } else {
            c = colors[hueIndex][satIndex];
        }
       
        return new Color((int)c.dimension_1, (int)c.dimension_2, (int)c.dimension_3);
//        return Color.getHSBColor(hue, saturation, 1f); //Math.min(1f,saturation)
    }
    
    
    
    
    // dumps the generated palettes of this paint scale as rgb values [r,g,b] in javascript array notation
    private void printPalettesJSON(){
        
        System.out.println("[");
        
        for (int i = 0; i < numColors; i++) {
System.out.println("Color1: "+i);
            System.out.println("colors[i]");
            ArrayList<String> colorCodes = new ArrayList<String>();
            
            for (Datatype.Color color : colors[i]) {
                colorCodes.add(String.format("[%s]",color.printColorRGB()));
            }
            System.out.println(String.format("[%s],",Joiner.on(",").join(colorCodes)));
        }
        
        System.out.println("]");
        
    }
    
    // test method to generate colors and their gradients
    private void computeAndPrintPalettesJSON(int numColors, int saturationResolution){
        Color_manager colorManager = new Color_manager(saturationResolution);
        
        System.out.println("[");
        
        for (int i = 0; i <= numColors; i++) {
            
            colorManager.addColorPalette(new float[]{240f-i*240f/numColors});
            Vector<Datatype.Color> cp = colorManager._color_palettes.get(i);
            
            ArrayList<String> colorCodes = new ArrayList<String>();
            for (Datatype.Color color : cp) {
                colorCodes.add(String.format("[%s]",color.printColorRGB()));
            }
            System.out.println(String.format("[%s],",Joiner.on(",").join(colorCodes)));
        }
        
        System.out.println("]");
        
    }
    
}
