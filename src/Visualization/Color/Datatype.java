package Visualization.Color;

import com.google.common.base.Joiner;
import java.util.Arrays;

public class Datatype {

	//! Enum that contains the available color types.
	public static enum ColorType  {
		RGBA,   /*!< Enum value representing the RGB color type incl. the Alpha value. */
		XYZ,    /*!< Enum value representing the XYZ color type */
		LUV,    /*!< Enum value representing the CIELUV color type */
		LCH     /*!< Enum value representing the LUV color type (Lightness, Chroma, Hue) */
		}

	//! An instance of the struct represents a color value.
	/*!
	 Each color can consists of up to four dimensions. The interpretation of the color depends on its type.
	*/
	public static class Color{
		public ColorType type;     /*!< Color type */
		public float dimension_1;  /*!< First dimension */
		public float dimension_2;  /*!< Second dimension */
		public float dimension_3;  /*!< Third dimension */
		public float dimension_4;  /*!< Fourth dimension (only needed for RGBA) */
	    
	    //! Constructor without arguments
		public Color() {}
	    
	    //! Constructor assigning values by parameters to the color type and the first three dimensions
	    /*!
	     The value for the fourth dimension is set to 1.0 by default.
	     \param p_type Color type.
	     \param p_dimension_1 First dimension.
	     \param p_dimension_2 Second dimension.
	     \param p_dimension_3 Third dimension.
	    */
		public Color(ColorType p_type, float p_dimension_1, float p_dimension_2, float p_dimension_3){
	        type = p_type;
	        dimension_1 = p_dimension_1;
	        dimension_2 = p_dimension_2;
	        dimension_3 = p_dimension_3;
	        dimension_4 = 1.0f;
		}
	    
	    //! Constructor assigning values by parameters to the color type and all four dimensions. 
	    /*!
	     \param p_type Color type.
	     \param p_dimension_1 First dimension.
	     \param p_dimension_2 Second dimension.
	     \param p_dimension_3 Third dimension.
	     \param p_dimension_4 Fourth dimension.
	     */
		public Color(ColorType p_type, float p_dimension_1, float p_dimension_2, float p_dimension_3, float p_dimension_4){
			type = p_type;
	        dimension_1 = p_dimension_1;
	        dimension_2 = p_dimension_2;
	        dimension_3 = p_dimension_3;
	        dimension_4 = p_dimension_4;
	    }
		
            public String printColorRGB(){
                return Joiner.on(",").join(Arrays.asList(new Integer[]{(int)dimension_1,(int)dimension_2,(int)dimension_3}));
            }
		
	}
	public static void printColor(Color color){
		System.out.print(color.type);
		System.out.print("dim1 : " + color.dimension_1 + " ");
		System.out.print("dim2 : " + color.dimension_2+ " ");
		System.out.print("dim3 : " + color.dimension_3+ " ");
		System.out.println("dim4 : " + color.dimension_4);
	}

}
