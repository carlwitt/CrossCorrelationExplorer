/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Visualization;

import com.sun.javafx.tk.FontLoader;
import javafx.geometry.Orientation;
import javafx.scene.text.Font;
import javafx.util.converter.NumberStringConverter;
import org.junit.Before;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Carl Witt
 */
public class NumberAxisTest {
    
    NumberAxis instance;

    // TODO with a scroll bar, the axis cannot be instantiated in tests
    @Before public void setUp() {
        instance = new NumberAxis(Orientation.HORIZONTAL);
    }
    
    @Test public void testSRAlgorithm(){
        assertEquals(200d, instance.tickUnit(5, 99d, 799d),1e-10);
        assertEquals(500d, instance.tickUnit(2, 0d, 1000d), 1e-10);
        assertEquals(0.005, instance.tickUnit(2, 0.01, 0.02), 1e-10);
        assertEquals(0.1, instance.tickUnit(10, 0d, 1d), 1e-10);
        assertEquals(1d, instance.tickUnit(10, 1d, 10d), 1e-10);
    }

    @Test public void testNextLowerTickMarkValue(){
        assertEquals(1000, instance.nextLowerTickMarkValue(1001.1, 0, 1000), 1e-10);
        assertEquals(248, instance.nextLowerTickMarkValue(250, 3, 5), 1e-10);
        
    }
    
    @Test public void tickLabelFormatter(){
        NumberStringConverter tickLabelFormatter = new NumberStringConverter(new DecimalFormat("0.###E0",DecimalFormatSymbols.getInstance(Locale.ENGLISH)));
        System.out.println(String.format("2 gives %s", tickLabelFormatter.toString(2)));
        System.out.println(String.format("2.2d gives %s", tickLabelFormatter.toString(2.2d)));
        System.out.println(String.format("2.2f gives %s", tickLabelFormatter.toString(2.2f)));
        System.out.println(String.format("0.00000000129032 gives %s", tickLabelFormatter.toString(0.00000000129032)));
        
    }
    
    @Test public void testStringWidth(){
        String s = "102.1230";
        FontLoader fl = com.sun.javafx.tk.Toolkit.getToolkit().getFontLoader();
        Font font = new Font("Verdana", 10);
        System.out.println(String.format("text snapshot\nw: %s h: %s", fl.computeStringWidth(s, font),fl.getFontMetrics(font).getLineHeight()));
                
    }
    

}
