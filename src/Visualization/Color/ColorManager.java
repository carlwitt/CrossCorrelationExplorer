/**
 * 
 */
package Visualization.Color;
import java.util.Vector;

import Visualization.Color.Datatype.Color;

/**
 * @author juanzi
 *
 */
public class ColorManager {


//! An object of the class is responsible for managing the display colors.
/*!
 The basic concept of the algorithm is based on Bezier curves.
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
    
	Vector<Vector<Color> > _color_palettes;                             /*!< Available color palettes */
	Vector<Color> _current_color_palette;                               /*!< Current color palette */
    
	Color _grid_color;                                                  /*!< Predefined color for grid lines (implicit constant) */
	Color _polygon_color; 												/*!< Predefined color for polygonal lines (implicit constant) */
	
	// constructor
	public ColorManager(){}
	
	
    //! Method responsible for initializing the 2D Bezier colors.
	protected void initColorPoints(Color _p_0, Color _p_1, Color _p_2, Color _q_0, Color _q_1, Color _q_2){
		this._p_0 = _p_0;
		this._p_1 = _p_1;
		this._p_2 = _p_2;
		this._q_0 = _q_0;
		this._q_1 = _q_1;
		this._q_2 = _q_2;
	}
    
    //! Method responsible for initializing the implicit constants.

	protected void initConstants(int _number_of_colors,float _max_lightness,float _max_chroma,
		float _max_hue,float _C,float _B,
		Color _grid_color,Color _polygon_color){
		this._number_of_colors = _number_of_colors;
		this._max_lightness = _max_lightness;
		this._max_chroma = _max_chroma;
		this._max_hue = _max_hue;
		this._C = _C;
		this._B = _B;
		this._grid_color = _grid_color;
		this._polygon_color = _polygon_color;
	}
	
    
    //! Method responsible for initializing the lightness and the chroma.
	protected void initLightnessAndChroma(float _lightness,float _chroma){
		this._lightness = _lightness;
		this._chroma = _chroma;
	}
    
    // Function responsible for generating a value based on Bezier curves.
    // return Bezier value.
	float evaluateBezier2D(float b_0, float b_1, float b_2, float t){
		 return (float)(((Math.pow((1.0f - t), 2.0f)) * b_0 + 2.0f * (1.0f - t) * t * b_1 + Math.pow(t, 2.0f) * b_2));
	}
	
    //! Function responsible for generating a value based on inverse Bezier curves.
    // return Inverse Bezier value. 
	float evaluateInverseBezier2D(float b_0, float b_1, float b_2, float v){
		return (b_0 - b_1 + (float)(Math.sqrt(Math.pow(b_1, 2.0f) - (b_0 * b_2) + (b_0 - 2.0f * b_1 + b_2) * v))) / (b_0 - 2.0f * b_1 + b_2);
	}
    
    //! Function responsible for calculating the lightness value.
    // return Lightness value.
	protected float L(float t){
		return (float)(125.0f - (125.0f * Math.pow(0.2f, ((1.0f - _C) * _B + t * _C)))); 
	}
    
    //! Function responsible for transforming the lightness value.
	protected float T(float l){
		float t;
	    
	    if (l <= _q_1.dimension_1){
	        t = 0.5f * evaluateInverseBezier2D(_p_0.dimension_1, _q_0.dimension_1, _q_1.dimension_1, l);
	    }else{
	        t = (0.5f * evaluateInverseBezier2D(_q_1.dimension_1, _q_2.dimension_1, _p_2.dimension_1, l)) + 0.5f;
	    }
	    
	    return t;
	}
    
    //! Method responsible for setting the lightness attribute.
	// If the passed lightness value is greater than the maximum lightness value a runtime error is thrown.
	public void setLightness(float lightness){}

    //! Method responsible for setting the chroma attribute.
    //If the passed chroma value is greater than the maximum chroma value a runtime error is thrown.
	public void setChroma(float chroma){}
    
    //! Method responsible for setting the hue attribute.
    // If the passed hue value is greater than the maxmium hue value a runtime erro is thrown.
	public void setHue(float hue){}
    
    //! Function that returns the generated color palettes.
	public Vector<Vector<Color>> getColorPalettes() { return _color_palettes; }
    
    //! Method responsible for updating the 2D Bezier colors.
    // The update is influenced by the most saturated color, which is generated based on the current hue value.
	public void updateColorPoints(){}
    
    //! Function responsible for generating a color palette based on the current hue value.
	public Vector<Color> generateColorPalette(){return _current_color_palette;}
    
    //! Method responsible for appending a color palette.
    // return Index of the color palette append
	public int appendColorPalette(Vector<Color> color_palette){return color_palette.size()-1;}
    
    //! Function that returns the current color palette
    // return Current color palette
	public Vector<Color> getCurrentColorPalette() { return _current_color_palette; }
    
    //! Method that sets the current color palette
	public void setCurrentColorPalette(int color_palette_id){}
/*    
    //! Function responsible for merging two generated color palettes.
    // return Merged color palette.
	public Vector<Color> mergeTwoColorPalettes(Vector<Color> color_palette_1, Vector<Color> color_palette_2){
		//return merged_color_palette;
	}
    
    //! Method responsible for setting the color palette attribute.
    // return ID of the color palette.
	public int generateColorPaletteOneColor(float hue){}
    
    //! Method responsible for setting the color palette attribute.
	public int generateColorPaletteTwoColors(float hue_1, float hue_2){}
    
    //! Method that adds a color palette based on the hue values given
    // return ID of the color palette.
	public int addColorPalette(Vector hue_values){}
    
    // Function that retrieves a color from the color palette based on the value ratio given.
    // return Normalized RGBA color.
	public Color getColorByValueRatio(double value_ratio){}
    
    //! Function that returns the color ID based on a value ratio.
    // return Color ID.
	public int getColorIdByValueRatio(double value_ratio){}
    
    //! Function that returns the color based on the color ID given.
	public Color getColorByColorId(long color_id) { return normalizeRGBAColor(_current_color_palette[(long)color_id]); }
    
    //! Function that returns the grid color.
	public Color getGridColor() { return normalizeRGBAColor(_grid_color); }
    
    //! Function that returns the polygon color.
	public Color getPolygonColor() { return normalizeRGBAColor(_polygon_color); }
    
    //! Function that normalizes a RGBA color.
	public Color normalizeRGBAColor(Color color){}
*/

//typedef shared_ptr<ColorManager> ColorManagerPtr;



}
