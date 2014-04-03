/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package Visualization;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author macbookdata
 */
public class NumberAxisTest {
    
    public NumberAxisTest() {
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
    public void testSRAlgorithm(){
        
        NumberAxis instance = new NumberAxis();
        
        assertEquals(200d, instance.tickUnit(5, 99d, 799d),1e-10);
        assertEquals(500d, instance.tickUnit(2, 0d, 1000d), 1e-10);
        assertEquals(0.005, instance.tickUnit(2, 0.01, 0.02), 1e-10);
        assertEquals(0.1, instance.tickUnit(10, 0d, 1d), 1e-10);
        assertEquals(1d, instance.tickUnit(10, 1d, 10d), 1e-10);
        
    }

    /**
     * Test of setTickOrigin method, of class NumberAxis.
     */
    @Test @Ignore
    public void testSetTickOrigin() {
        System.out.println("setTickOrigin");
        double value = 0.0;
        NumberAxis instance = new NumberAxis();
        instance.setTickOrigin(value);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of setTickUnit method, of class NumberAxis.
     */
    @Test @Ignore
    public void testSetTickUnit() {
        System.out.println("setTickUnit");
        double value = 0.0;
        NumberAxis instance = new NumberAxis();
        instance.setTickUnit(value);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getRange method, of class NumberAxis.
     */
    @Test @Ignore
    public void testGetRange() {
        System.out.println("getRange");
        NumberAxis instance = new NumberAxis();
        double expResult = 0.0;
        double result = instance.getRange();
        assertEquals(expResult, result, 0.0);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }
    
}
