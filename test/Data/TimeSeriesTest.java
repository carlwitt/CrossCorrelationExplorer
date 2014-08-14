/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import org.junit.Test;

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
        instance = new TimeSeries(1, new double[]{1,2,3,4},new double[]{1.,2.,3.,4.});
    }
    
    @Test public void testGetSize() {
        System.out.println("getSize");
        
        int expResult = 4;
        int result = instance.getSize();
        assertEquals(expResult, result);
    }
    
    @Test
    public void testGetItemById() {
        System.out.println("getItemById");
        int id = 1;
        Double expResult = 2.;
        Double result = instance.getItemById(id);
        assertEquals(expResult, result);
    }
    
    @Test public void testGetMinY() {
        System.out.println("getMinY");
        Double expResult = 1.;
        Object result = instance.getMinY();
        assertEquals(expResult, result);
    }

    @Test public void testGetMaxX() {
        System.out.println("getMaxX");
        Double expResult = 4.;
        Object result = instance.getMaxX();
        assertEquals(expResult, result);
    }

    @Test public void testEquals(){
        List<TimeSeries> randomSeries = randomTimeSeries(1000, 10000, 1l);
        for (int i = 0; i < randomSeries.size(); i++) {
            for (int j = 0; j < randomSeries.size(); j++) {
                boolean isEqual = i==j;
                assertEquals(isEqual, randomSeries.get(i).equals(randomSeries.get(j)));
            }
        }
    }

    public static List<TimeSeries> randomTimeSeries(int numTimeSeries, int timeSeriesLength, long seed) {
        List<TimeSeries> set = new ArrayList<>(numTimeSeries);

        Random rdg = new Random(seed);

        for (int i = 0; i < numTimeSeries; i++) {
            double[] data = new double[timeSeriesLength];
            for (int j = 0; j < timeSeriesLength; j++) data[j] = rdg.nextDouble();
            set.add(new TimeSeries(i+1, data));
        }
        return set;
    }

}