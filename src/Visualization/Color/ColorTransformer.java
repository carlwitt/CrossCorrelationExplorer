package Visualization.Color;


import java.lang.Throwable;
import java.lang.Float;

import Visualization.Color.Datatype.*;


public class ColorTransformer {
	//Color color;

	public ColorTransformer(){};
	
	//Color color;
	public static float dealNaN(float num){
		float dealed = num;
		if(Float.isNaN(num)){
			dealed = (float)0.0;
		}
		return dealed;
	}
	
	public static float dealInfinite(float num){
		float dealed = num;
		if(Float.isInfinite(num)){
			dealed = (float)255.0;
		}
		return dealed;
	}
	public static Color rgba2xyz(Color color){
        	if(color.type != ColorType.RGBA){
        		Throwable ta = new Throwable("expected color type is RGBA!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        	
        	
        	float r = (float) Math.pow((color.dimension_1 / 255.0f), 2.2f);
        	float g = (float) Math.pow((color.dimension_2 / 255.0f), 2.2f);
        	float b = (float) Math.pow((color.dimension_3 / 255.0f), 2.2f);
        	r = dealNaN(r);
        	g = dealNaN(g);
        	b = dealNaN(b);
        	r = dealInfinite(r);
        	g = dealInfinite(g);
        	b = dealInfinite(b);
        	//System.out.println("rgb: "+ r + "  " + g+ "  " + b);
        	float x = r * 0.5767309f + g * 0.1855540f + b * 0.1881852f;
        	float y = r * 0.2973769f + g * 0.6273491f + b * 0.0752741f;
        	float z = r * 0.0270343f + g * 0.0706872f + b * 0.9911085f;

        	return new Color(ColorType.XYZ, x, y, z);
	}
	
	public static Color rgba2lch(Color color){
        	if(color.type != ColorType.RGBA){
        		Throwable ta = new Throwable("expected color type is RGBA!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        
		return luv2lch(xyz2luv(rgba2xyz(color)));
	}
    
	public static Color xyz2rgba(Color color){
        	if(color.type != ColorType.XYZ){
        		Throwable ta = new Throwable("expected color type is XYZ!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        
        	float x = color.dimension_1;
        	float y = color.dimension_2;
        	float z = color.dimension_3;
        
        	float r_2 = 2.0413691f * x - 0.5649464f * y - 0.3446944f * z;
        	float g_2 = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z;
        	float b_2 = 0.0134474f * x - 0.1183897f * y + 1.0154096f * z;
        	
        	float r = 255.0f * (float)Math.pow(r_2, 1.0f / (2.2f));
        	float g = 255.0f * (float)Math.pow(g_2, 1.0f / (2.2f));
	    	float b = 255.0f * (float)Math.pow(b_2, 1.0f / (2.2f));
	    	
        	if((r < 0.0f)||(Float.isNaN(r))){
            		r = 0.0f;
        	}
        	else if(r > 255.0f || Float.isInfinite(r)){
            		r = 255.0f;
        	}
        
        	if((g < 0.0f)||(Float.isNaN(g))){
            		g = 0.0f;
        	}
        	else if(g > 255.0f || Float.isInfinite(g)){
            		g = 255.0f;
        	}
        
        	if((b < 0.0f)||(Float.isNaN(b))){
            		b = 0.0f;
        	}
        	else if(b > 255.0f || Float.isInfinite(b)){
            		b = 255.0f;
        	}
        	
        	return new Color(ColorType.RGBA, r, g, b);
	}
    
	public static Color xyz2luv(Color color){
        	if(color.type != ColorType.XYZ){	
        		Throwable ta = new Throwable("expected color type is XYZ!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        	if(color.type != ColorType.XYZ){
        		RuntimeException ta = new RuntimeException("expected color type is XYZ!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        
        
        	float x = color.dimension_1;
        	float y = color.dimension_2;
        	float z = color.dimension_3;
        
        	float y_r = y / 1.0f;
        	float u_r = (4.0f * 0.95047f) / (0.95047f + 15.0f + 3.0f * 1.08883f);
        	float v_r = 9.0f / (0.95047f + 15.0f + 3.0f * 1.08883f);
        
        	float u_2 = (4.0f * x) / (x + 15.0f * y + 3.0f * z);
        	float v_2 = (9.0f * y) / (x + 15.0f * y + 3.0f * z);
    
        	float l;
        
        	if(y_r > 0.008856){
        		l = (116.0f * (float)Math.pow(y_r, (1.0f / 3.0f))) - 16.0f;
        	} 
        	else{
        		l = 903.3f * y_r;
        	}
        
        	float u = 13.0f * l * (u_2 - u_r);
        	float v = 13.0f * l * (v_2 - v_r);
        
        	return new Color(ColorType.LUV, l, u, v);
    	}
		
	public static Color luv2xyz(Color color){
        if(color.type != ColorType.LUV){
        	Throwable ta = new Throwable("expected color type is LUV!");
    	    ta.getMessage();
    	    System.out.println(ta.getMessage());
        }
        
        float l = color.dimension_1;
        float u = color.dimension_2;
        float v = color.dimension_3;
        
		float u_r = (4.0f * 0.95047f) / (0.95047f + 15.0f + 3.0f * 1.08883f);
		float v_r = 9.0f / (0.95047f + 15.0f + 3.0f * 1.08883f);
        
		float a = (((52.0f * l) / (u + 13.0f * l * u_r)) - 1.0f) / 3.0f;
		float c = -1.0f / 3.0f;
        
		float y;
        
		if(l > (0.008856f * 903.3f)){
			y = (float) Math.pow(((l + 16.0f) / 116.0f), 3.0f);
		} 
        	else{
			y = l / 903.3f;
		}
        
		float b = -5.0f * y;
		float d = y * (((39.0f * l) / (v + 13.0f * l * v_r)) - 5.0f);
        
		float x = (d - b) / (a - c);
		float z = x * a + b;
        
        	return new Color(ColorType.XYZ, x, y, z);
	}
    
	public static Color luv2lch(Color color){
        if(color.type != ColorType.LUV){
        	Throwable ta = new Throwable("expected color type is LUV!");
    	    ta.getMessage();
    	    System.out.println(ta.getMessage());
        	}
        
        float l = color.dimension_1;
        float u = color.dimension_2;
        float v = color.dimension_3;
        
        float c = (float) Math.sqrt(u * u + v * v);
        
		float h_0 = (float) Math.atan2(v, u);
		float h = (h_0 * 180.0f) / (float) Math.PI;
        
		if(h < 0.0f){
			h = 360.0f - Math.abs(h);
		}
        
		return new Color(ColorType.LCH, l, c, h);
	}
    
	public static Color lch2luv(Color color){
        	if(color.type != ColorType.LCH){
        		Throwable ta = new Throwable("expected color type is LCH!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        
        float l = color.dimension_1;
		float c = color.dimension_2;
		float h = color.dimension_3;
        
		float h_rad = (h * (float)(Math.PI)) / 180.0f;
        
		float u = (float)(c * Math.cos(h_rad));
		float v = (float)(c * Math.sin(h_rad));
        
		return new Color(ColorType.LUV, l, u, v);
	}
    
	public static Color lch2rgba(Color color){
        	if(color.type != ColorType.LCH){
        		Throwable ta = new Throwable("expected color type is LCH!");
    	    	ta.getMessage();
    	    	System.out.println(ta.getMessage());
        	}
        
        	return xyz2rgba(luv2xyz(lch2luv(color)));
    	}
	/*
	public static void main(String[] args) {
		//Color color = new Color(ColorType.LCH, (float)17.895212, (float)47.41563,(float)256.0,(float)1.0 );
		//Color c_luv = lch2luv(color);
		//Color c_luv = new Color(ColorType.LUV,49.83157f, -72.534775f, 41.877953f, 1.0f);
		Datatype.printColor(c_luv);
		Color c_xyz = luv2xyz(c_luv);
		Datatype.printColor(c_xyz);
		Color c_rgba = xyz2rgba(c_xyz);
		Datatype.printColor(c_rgba);
		Color c_rgbaTMP = new Color(ColorType.RGBA, 0.0f, -72.534775f, 255.0f, 1.0f);
		//Color c_xyzTMP = rgba2xyz(c_rgbaTMP);
		//Color c_luvTMP = xyz2luv(c_xyzTMP);
		Color c_luvTMP = xyz2luv(c_rgbaTMP);
		Datatype.printColor(c_luvTMP);
		
	}	*/
}


