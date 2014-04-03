package Visualization.Color;

import java.util.Vector;
import java.lang.Throwable;

import Visualization.Color.ColorGenerator;
import Visualization.Color.ColorManager;
import Visualization.Color.ColorTransformer;
import Visualization.Color.Datatype.Color;
import Visualization.Color.Datatype.ColorType;


public class Color_manager extends ColorManager{ 
	/**
	 * @param args
	 */
	
	protected int _number_of_colors;                                              /*!< Number of colors generated for one subpalette */
	protected float _max_lightness;                                               /*!< Maximum value for lightness */
	protected float _max_chroma;                                                  /*!< Maximum value for chroma */
	protected float _max_hue;                                                     /*!< Maximum value for hue */
    
	protected float _lightness;                                                   /*!< Lightness value */
	protected float _chroma;                                                      /*!< Chroma value */
	protected float _hue;                                                         /*!< Hue value */
    
	protected float _C;                                                           /*!< C value (implicit constant) */
	protected float _B;                                                           /*!< B value (implicit constant) */
    
	Color _p_0;                                                         /*!< 2D Bezier color p0 */
	Color _p_1;                                                         /*!< 2D Bezier color p1 (most saturated color) */
	Color _p_2;                                                         /*!< 2D Bezier color p2 */
    
	Color _q_0;                                                         /*!< 2D Bezier color q0 */
	Color _q_1;                                                         /*!< 2D Bezier color q1 */
	Color _q_2;                                                         /*!< 2D Bezier color q2 */
    
	public Vector<Vector<Color> > _color_palettes = new Vector<Vector<Color> >();                             /*!< Available color palettes */
	public Vector<Color> _current_color_palette;                               /*!< Current color palette */
    
	Color _grid_color;                                                  /*!< Predefined color for grid lines (implicit constant) */
	Color _polygon_color; 												/*!< Predefined color for polygonal lines (implicit constant) */
	
	// Constructor
	public Color_manager(int numberOfColors){
		 this._p_0 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
		 this._p_1 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
		 this._p_2 = new Color(ColorType.LCH, 100.0f, 0.0f, 0.0f);
		    
		 this._q_0 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
		 this._q_1 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
		 this._q_2 = new Color(ColorType.LCH, 100.0f, 0.0f, 0.0f);
		 this._number_of_colors = numberOfColors;                                          
		 this._max_lightness = 100.0f;                                      
		 this._max_chroma = 100.0f;                                         
		 this._max_hue = 360.0f;                                            
		    
		 this._B = 0.8f;
		 this._C = (float)Math.min(0.88f, 0.34f + 0.06f * _number_of_colors);
		    
		 this._grid_color = new Color(ColorType.RGBA, 120.0f, 120.0f, 120.0f, 0.4f);
		 this._polygon_color = new Color(ColorType.RGBA, 50.0f, 50.0f, 50.0f, 0.4f);
	    
		 this._lightness = 100.0f;
		 this._chroma = 100.0f;
	  
	}
	/*public void initColorPoints(){
	    this._p_0 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
	    this._p_1 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
	    this._p_2 = new Color(ColorType.LCH, 100.0f, 0.0f, 0.0f);
	    
	    this._q_0 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
	    this._q_1 = new Color(ColorType.LCH, 0.0f, 0.0f, 0.0f);
	    this._q_2 = new Color(ColorType.LCH, 100.0f, 0.0f, 0.0f);
	}

	protected void initConstants(){
	    this._number_of_colors = 9;                                          
	    this._max_lightness = 100.0f;                                      
	    this._max_chroma = 100.0f;                                         
	    this._max_hue = 360.0f;                                            
	    
	    this._B = 0.8f;
	    this._C = (float)Math.min(0.88f, 0.34f + 0.06f * _number_of_colors);
	    
	    this._grid_color = new Color(ColorType.RGBA, 120.0f, 120.0f, 120.0f, 0.4f);
	    this._polygon_color = new Color(ColorType.RGBA, 50.0f, 50.0f, 50.0f, 0.4f);
	}
	protected void initLightnessAndChroma(){
	    _lightness = 100.0f;
	    _chroma = 100.0f;
	}
	*/
	public float evaluateBezier2D(float b_0, float b_1, float b_2, float t){ 
	    return (float)(((Math.pow((1.0f - t), 2.0f)) * b_0 + 2.0f * (1.0f - t) * t * b_1 + Math.pow(t, 2.0f) * b_2)); 
	}

	public float evaluateInverseBezier2D(float b_0, float b_1, float b_2, float v) 
	{ 
	    return (b_0 - b_1 + (float)(Math.sqrt(Math.pow(b_1, 2.0f) - (b_0 * b_2) + (b_0 - 2.0f * b_1 + b_2) * v))) / (b_0 - 2.0f * b_1 + b_2); 
	}

	protected float L(float t) 
	{ 
	    return (float)(125.0f - (125.0f * Math.pow(0.2f, ((1.0f - _C) * _B + t * _C)))); 
	}

	protected float T(float l){
	    float t;
	    
	    if (l <= _q_1.dimension_1){
	        t = 0.5f * evaluateInverseBezier2D(_p_0.dimension_1, _q_0.dimension_1, _q_1.dimension_1, l);
	    }else{
	        t = (0.5f * evaluateInverseBezier2D(_q_1.dimension_1, _q_2.dimension_1, _p_2.dimension_1, l)) + 0.5f;
	    }
	    
	    return t;
	}
public Vector<Vector<Color>> getColorPalettes() { return _color_palettes; }
	
	public void RuntimeException(String message){
		
	}
	public void setLightness(float lightness){
	    if(lightness > _max_lightness){
	        Throwable ta = new Throwable("lightness is greater than maximum lightness!");
	        ta.getMessage();
	        System.out.println(ta.getMessage());
	    }
	    
	    _lightness = lightness;
	}

	public void setChroma(float chroma){
	    if(chroma > _max_chroma)
	    {
	    	Throwable ta = new Throwable("chroma is greater than maximum chroma!");
	    	ta.getMessage();
	    	System.out.println(ta.getMessage());
	    }
	    
	    _chroma = chroma;
	}

	public void setHue(float hue){
	    if(hue > _max_hue)
	    {
	    	Throwable ta = new Throwable("hue is greater than maximum hue!");
	    	ta.getMessage();
	    	System.out.println(ta.getMessage());
	    }
	    
	    _hue = hue;
	}

	public void updateColorPoints(){
	    float saturation = _chroma / _max_chroma;
	    
	    _p_0.dimension_3 = _hue;
	    _p_1 = ColorTransformer.rgba2lch(ColorGenerator.generateMostSaturatedColor(_hue));
	    _p_2.dimension_3 = _hue;
	    
	    _q_0.dimension_1 = (1.0f - saturation) * _p_0.dimension_1 + saturation * _p_1.dimension_1;
	    _q_0.dimension_2 = (1.0f - saturation) * _p_0.dimension_2 + saturation * _p_1.dimension_2;
	    _q_0.dimension_3 = (1.0f - saturation) * _p_0.dimension_3 + saturation * _p_1.dimension_3;
	    
	    _q_2.dimension_1 = (1.0f - saturation) * _p_2.dimension_1 + saturation * _p_1.dimension_1;
	    _q_2.dimension_2 = (1.0f - saturation) * _p_2.dimension_2 + saturation * _p_1.dimension_2;
	    _q_2.dimension_3 = (1.0f - saturation) * _p_2.dimension_3 + saturation * _p_1.dimension_3;
	    
	    _q_1.dimension_1 = 0.5f * (_q_0.dimension_1 + _q_2.dimension_1);
	    _q_1.dimension_2 = 0.5f * (_q_0.dimension_2 + _q_2.dimension_2);
	    _q_1.dimension_3 = 0.5f * (_q_0.dimension_3 + _q_2.dimension_3);
	}

	public Vector<Color> generateColorPalette(){
	    Vector<Color> color_palette = new Vector<Color> ();
	    
	    for(int i = 0; i < _number_of_colors; ++i)
	    {            
	        float t = (float)(i) / (_number_of_colors - 1);
	        
	        float lightness = L(t);
	        
	        float cp = T(lightness);
	        
	        float chroma;
	        if(cp <= 0.5f)
	        {
	            chroma = evaluateBezier2D(_p_0.dimension_2, _q_0.dimension_2, _q_1.dimension_2, (2.0f * cp));
	        }
	        else
	        {
	            chroma = evaluateBezier2D(_q_1.dimension_2, _q_2.dimension_2, _p_2.dimension_2, (2.0f * (cp - 0.5f)));
	        }
	        
	        Color color = new Color(ColorType.LCH, lightness, chroma, _hue);
	        
	        color_palette.add(ColorTransformer.lch2rgba(color));
	    }
	    
	    return color_palette;
	}

	public int appendColorPalette(Vector<Color> color_palette)
	{
	    _color_palettes.add(color_palette);
	    
	    return (_color_palettes.size() - 1);
	}

	public void setCurrentColorPalette(int color_palette_id)
	{   
	    if(color_palette_id < _color_palettes.size())
	    {
	        _current_color_palette = _color_palettes.get(color_palette_id);
	    }
	    else
	    {
	        _current_color_palette = _color_palettes.get(0);
	    }
	}
	
	public Vector<Color> reverse(Vector<Color> color_palette){
		
		int paletteSize = color_palette.size()-1;
		Vector<Color> reversed = new Vector<Color>(paletteSize+1);
		reversed.clear();
		
		
		while(paletteSize >= 0){
			reversed.add(color_palette.get(paletteSize));
			paletteSize--;
			
		}
		
		return reversed;
	}
	public Vector<Color> mergeTwoColorPalettes(Vector<Color> color_palette_1, Vector<Color> color_palette_2)
	{
	    Vector<Color> merged_color_palette = color_palette_1;
	    
	    color_palette_2.removeElementAt(color_palette_2.size()-1);
	  
	    //reverse 
	    color_palette_2 = reverse(color_palette_2);
	   
	    merged_color_palette.addAll(color_palette_2);
	    
	    return merged_color_palette;
	}

	public int generateColorPaletteOneColor(float hue){
	    setHue(hue);
	    updateColorPoints();
	    Vector<Color> color_palette = generateColorPalette();
//	    printColorPalette(color_palette);
	    return appendColorPalette(color_palette);
	}

	public int generateColorPaletteTwoColors(float hue_1, float hue_2){
	    Vector<Color> color_palette_1;
	    Vector<Color> color_palette_2;
	    Vector<Color> merged_color_palette;
	    
	    setHue(hue_1);
	    updateColorPoints();
	    color_palette_1 = generateColorPalette();
	    
	    setHue(hue_2);
	    updateColorPoints();
	    color_palette_2 = generateColorPalette();
//	    System.out.println("cp_1: ");
//	    printColorPalette(color_palette_1);
//	    System.out.println("cp_2: ");
//	    printColorPalette(color_palette_2);
	    merged_color_palette = mergeTwoColorPalettes(color_palette_1, color_palette_2);
//	    System.out.println("merged_cp: ");
//	    printColorPalette(merged_color_palette);
	    return appendColorPalette(merged_color_palette);
	}
	
	public void printColorPalette(Vector<Color> color_palette){
		int i = color_palette.size();
		int j = 0;
		while (j < i){
			Datatype.printColor(color_palette.get(j));
			j++;
		}
		System.out.println();
	}

	public int addColorPalette(float[] hue_values){
		
	    int number_of_hue_values = hue_values.length;
	    int color_palette_index = 0;
	    
	    switch(number_of_hue_values)
	    {
	        case 1:color_palette_index = generateColorPaletteOneColor(hue_values[0]);
	            break;
	        case 2:color_palette_index = generateColorPaletteTwoColors(hue_values[0], hue_values[1]);
	            break;
	        default:
	            break;
	    }
//	    System.out.println("color_p index by addColorPalette: " + color_palette_index);
	    return color_palette_index;
	}

	public Color getColorByValueRatio(double value_ratio){
	    int color_id = _current_color_palette.size() / 2;
	    
	    if(value_ratio < 0)
	    {
	        color_id -= (1 - color_id * value_ratio);
	    }
	    else if(value_ratio > 0)
	    {
	        color_id += (1 + color_id * value_ratio);
	    }
	    
	    return normalizeRGBAColor(_current_color_palette.get(color_id));
	}

	public int getColorIdByValueRatio(double value_ratio){
	    int color_id = _current_color_palette.size() / 2;
	    
	    if(value_ratio < 0)
	    {
	        color_id -= (1 - color_id * value_ratio);
	    }
	    else if(value_ratio > 0)
	    {
	        color_id += (1 + color_id * value_ratio);
	    }
	    
	    return color_id;
	}

	public Color normalizeRGBAColor(Color color){
	    color.dimension_1 /= 255.0f;
	    color.dimension_2 /= 255.0f;
	    color.dimension_3 /= 255.0f;
	    
	    return color;
	}

	public static void main(String[] args){
		int numberOfColors = 3;
		//float [] hues = {(float)(0.0)};
		Color_manager cm = new Color_manager(numberOfColors);
		float [] hues = {(float)(-285.0), (float)(-0.0)};
		cm.addColorPalette(hues);
		//System.out.println(cm);
		int l = cm._color_palettes.size();
		System.out.println("legth of cps:" + l);
		Vector<Color> cp = cm._color_palettes.get(0);
		int ll = cp.size();
		System.out.println("legth of current_cp:" + ll);
		
	}

}
