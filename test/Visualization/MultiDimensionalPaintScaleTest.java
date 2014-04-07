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
import org.junit.Ignore;

/**
 *
 * @author Carl Witt
 */
public class MultiDimensionalPaintScaleTest {
    
    MultiDimensionalPaintScale instance = new MultiDimensionalPaintScale(12, 4);
    
    public MultiDimensionalPaintScaleTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testComputePaletteBiPolar() {
        instance.printPalettesJSON();
    }
    
    @Test @Ignore
    public void testInterpolate() {
        System.out.println("interpolate");
        double d = 0.0;
        int dim = 0;
        double expResult = 0.0;
        double result = instance.interpolate(d, dim);
        assertEquals(expResult, result, 0.0);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test that the boundary values return the boundary colors.
     */
    @Test
    public void testGetPaint_doubleArr() {
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
