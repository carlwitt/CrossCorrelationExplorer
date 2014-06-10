/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

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

    public static List<TimeSeries> randomTimeSerieses(int numTimeSeries, int timeSeriesLength) {
        List<TimeSeries> set = new ArrayList<>(numTimeSeries);

        Random rdg = new Random(1l);

        for (int i = 0; i < numTimeSeries; i++) {
            double[] data = new double[timeSeriesLength];
            for (int j = 0; j < timeSeriesLength; j++) data[j] = rdg.nextDouble();
            set.add(new TimeSeries(data));
        }
        return set;
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
//        assertTrue(instance.getMaxX() == 4.);
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
     * Test of getMinY method, of class TimeSeries.
     */
    @Test
    public void testGetMinItem() {
        System.out.println("getMinY");
        Double expResult = 1.;
        Object result = instance.getMinY();
        assertEquals(expResult, result);
    }

    /**
     * Test of getMaxX method, of class TimeSeries.
     */
    @Test
    public void testGetMaxItem() {
        System.out.println("getMaxX");
        Double expResult = 4.;
        Object result = instance.getMaxX();
        assertEquals(expResult, result);
    }
    
}