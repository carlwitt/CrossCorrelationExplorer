/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import Data.TimeSeries;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
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
public class TimeSeriesTest {
    
    TimeSeries instance;
    public TimeSeriesTest() {
        instance = new TimeSeries(new double[]{1.,2.,3.,4.});
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

    /**
     * Test of getSize method, of class TimeSeries.
     */
    @Test
    public void testGetSize() {
        System.out.println("getSize");
        
        int expResult = 4;
        int result = instance.getSize();
        assertEquals(expResult, result);
    }
    
//    @Test
//    public void testAppend() {
//        System.out.println("append");
//        TimeSeries expResult = new TimeSeries(0, null, new double[]{1.,2.,3.,4.});
//        instance.append(4.);
//        assertEquals(expResult, instance);
//        assertTrue(instance.getMaxItem() == 4.);
//    }

    /**
     * Test of getItemById method, of class TimeSeries.
     */
    @Test
    public void testGetItemById() {
        System.out.println("getItemById");
        int id = 1;
        Double expResult = 2.;
        Double result = instance.getItemById(id);
        assertEquals(expResult, result);
    }
    
    /**
     * Test of getMinItem method, of class TimeSeries.
     */
    @Test
    public void testGetMinItem() {
        System.out.println("getMinItem");
        Double expResult = 1.;
        Object result = instance.getMinItem();
        assertEquals(expResult, result);
    }

    /**
     * Test of getMaxItem method, of class TimeSeries.
     */
    @Test
    public void testGetMaxItem() {
        System.out.println("getMaxItem");
        Double expResult = 4.;
        Object result = instance.getMaxItem();
        assertEquals(expResult, result);
    }
    
}