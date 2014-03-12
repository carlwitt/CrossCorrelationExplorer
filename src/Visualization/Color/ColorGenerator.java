/**
 * 
 */
package Visualization.Color;
import Visualization.Datatype.*;
/**
 * @author juanzi
 *
 */
public class ColorGenerator {
	
	private static float color_contr(float h, float rgba_1, float rgba_2, float rgba_3){
	float u_r = (4.0f * 0.95047f) / (0.95047f + 15.0f + 3.0f * 1.08883f);
	float v_r = 9.0f / (0.95047f + 15.0f + 3.0f * 1.08883f);
	        
	float t_1 = (float)((Math.cos(h) * v_r) - (Math.sin(h) * u_r));
	float t_2 = rgba_1 + 15.0f * rgba_2 + 3.0f * rgba_3;
	float t_3 = (float)((Math.cos(h) * 9.0f * rgba_2) - (Math.sin(h) * 4.0f * rgba_1));
	        
	return t_1 * t_2 - t_3;
		}
	    
	public static Color generateMostSaturatedColor(float h){
	        // Step 1: select correct hue plane
	        // Note h is hue plan in LUV color space
			float r = 0.0f;
			float g = 0.0f;
	        float b = 0.0f;
	        
	        float h_rad = (h * (float)(Math.PI)) / 180.0f;
	        
	        float cc_1;
	        float cc_2;
	        float cc_3;
	        
	        // No Blue
			if((h >= 12.0f) && (h <= 138.0f)){
				cc_1 = color_contr(h_rad, 0.5767309f, 0.2973769f, 0.0270343f);  // red
				cc_2 = color_contr(h_rad, 0.1855540f, 0.6273491f, 0.0706872f); // green
	            
				if(h <= 85.0f){
					r = 255.0f;
					cc_3 = Math.abs(cc_1 / cc_2);
	                g = (float)Math.pow(cc_3, (1.0f /2.2f)) * 255.0f;
				} else{
					g = 255.0f;
					cc_3 = Math.abs(cc_2 / cc_1);
					r = (float)Math.pow(cc_3, (1.0f / 2.2f)) * 255.0f;
				}
			}
	        
	        // No Red
	        else if((h > 138.0f) && (h <= 265.0f)){
	            cc_1 = color_contr(h_rad, 0.1855540f, 0.6273491f, 0.0706872f); // green
	            cc_2 = color_contr(h_rad, 0.1881852f, 0.0752741f, 0.9911085f); // blue
	                
	            if(h <= 192.0f){
	                g = 255.0f;
	                cc_3 = Math.abs(cc_1/cc_2);
	                b = (float)Math.pow(cc_3, (1.0f / 2.2f)) * 255.0f;
	            } else{
	                b = 255.0f;
	                cc_3 = Math.abs(cc_2 / cc_1);
	                g = (float)Math.pow(cc_3, (1.0f / 2.2f)) * 255.0f;
	            		}
	        } 
	        
	        // No Green
	        else{
	            		
	        	cc_1 = color_contr(h_rad, 0.1881852f, 0.0752741f, 0.9911085f); // blue
	            cc_2 = color_contr(h_rad, 0.5767309f, 0.2973769f, 0.0270343f); // red
	                
	            if((h > 265.0f) && (h <= 318.0f)){
	                b = 255.0f;
	                cc_3 = Math.abs(cc_1 / cc_2);
	                r = (float)Math.pow(cc_3, (1.0f / 2.2f)) * 255.0f;
	            } else{
	                r = 255.0f;
	                cc_3 = Math.abs(cc_2/cc_1);
	                b = (float)Math.pow(cc_3, (1.0f/2.2f)) * 255.0f;
	            }
			}
			
			return new Color(ColorType.RGBA, r, g, b);
	}
	
}
