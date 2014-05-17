/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Visualization;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Carl Witt
 */
public class MultiDimensionalPaintScaleTest {
    
    MultiDimensionalPaintScale instance = new MultiDimensionalPaintScale(12, 4);

    @Test
    public void testComputePaletteBiPolar() {
        instance.printPalettesJSON();
    }

    @Test
    public void testGetPaintForNaN() {

        MultiDimensionalPaintScale scale = new MultiDimensionalPaintScale(12, 5);
        scale.setLowerBounds(1d,10d);
        scale.setUpperBounds(5d,100d);
        System.out.println(scale.getPaint(3., Double.NaN));

    }
    /**
     * Test that the boundary values return the boundary colors.
     */
    @Test
    public void testGetPaint() {
        System.out.println("getPaint");
        
        MultiDimensionalPaintScale scale = new MultiDimensionalPaintScale(12, 5);
        
        Paint minMin = Color.valueOf("0x005687ff");
        Paint maxMin = Color.valueOf("0x860042ff");
        Paint minMax = Color.valueOf("0x6c7d87ff");
        Paint maxMax = Color.valueOf("0x866b78ff");
        
        scale.setLowerBounds(1d,10d);
        scale.setUpperBounds(5d,100d);
        
        assertEquals(minMin.toString(), scale.getPaint(1d,10d).toString());
        assertEquals(maxMin.toString(), scale.getPaint(5d,10d).toString());
        assertEquals(minMax.toString(), scale.getPaint(1d,100d).toString());
        assertEquals(maxMax.toString(), scale.getPaint(5d,100d).toString());
    }
    
}
