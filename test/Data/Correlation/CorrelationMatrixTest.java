/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.Correlation.CorrelogramMetadata;
import Data.Correlation.CorrelationMatrix;
import Data.ComplexSequence;
import Data.DataModel;
import Data.TimeSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author Carl Witt
 */
public class CorrelationMatrixTest {
    
    DataModel dataModel = new DataModel();
    
    public CorrelationMatrixTest() {
    }
    
    /**
     * Test of aggregation constructor, i.e. the computation of mean and standard deviation.
     */
    @Test
    public void testAggregateConstructor() {
        System.out.println("aggregateConstructor");
        
        TimeSeries ts1 = new TimeSeries(1, new double[]{0,1,2}, new double[]{0,1,2});
        TimeSeries ts2 = new TimeSeries(2, new double[]{0,1,2}, new double[]{1,2,3});
        TimeSeries ts3 = new TimeSeries(3, new double[]{0,1,2}, new double[]{2,3,4});
        
        dataModel.put(1, ts1);
        dataModel.put(2, ts2);
        dataModel.put(3, ts3);
        
        List<TimeSeries> timeSeriesSet = Arrays.asList(new TimeSeries[]{ts1,ts2,ts3});
        CorrelogramMetadata metadata = new CorrelogramMetadata(timeSeriesSet, timeSeriesSet, 1, DFT.NA_ACTION.LEAVE_UNCHANGED);
        CorrelationMatrix instance = new CorrelationMatrix(metadata);
        instance.compute();

        System.out.println(String.format("%s", instance));
//        System.out.println(String.format("correlogram store\n%s", CorrelogramStore.correlationMatricesByMetadata.entrySet()));
        assertEquals(1, instance.columns.get(0).mean[0], 1e-5);
        assertEquals(4, instance.columns.get(1).mean[0], 1e-5);
        assertEquals(9, instance.columns.get(2).mean[0], 1e-5);

        // from wolfram alpha
        assertEquals(1.33333, instance.columns.get(0).stdDev[0], 1e-4);
        assertEquals(2.40370, instance.columns.get(1).stdDev[0], 1e-4);
        assertEquals(3.52766, instance.columns.get(2).stdDev[0], 1e-4);
    }
    
    
    
    
    /**
     * Test of getSize method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetSize() {
        System.out.println("getSize");
        CorrelationMatrix instance = null;
        int expResult = 0;
        int result = instance.getSize();
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of contains method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testContains() {
        System.out.println("contains");
        int id = 0;
        CorrelationMatrix instance = null;
        boolean expResult = false;
        boolean result = instance.contains(id);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of isEmpty method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testIsEmpty() {
        System.out.println("isEmpty");
        CorrelationMatrix instance = null;
        boolean expResult = false;
        boolean result = instance.isEmpty();
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getResultItems method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetResultItems() {
        System.out.println("getResultItems");
        CorrelationMatrix instance = null;
        List expResult = null;
        List result = instance.getResultItems();
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of add method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testAppend() {
        System.out.println("append");
        CorrelationMatrix.Column c = null;
        CorrelationMatrix instance = null;
        instance.append(c);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getMetadata method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetMetadata() {
        System.out.println("getMetadata");
        CorrelationMatrix instance = null;
        CorrelogramMetadata expResult = null;
        CorrelogramMetadata result = instance.getMetadata();
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getItembyID method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetItembyID() {
        System.out.println("getItembyID");
        int id = 0;
        CorrelationMatrix instance = null;
        CorrelationMatrix.Column expResult = null;
        CorrelationMatrix.Column result = instance.getItembyID(id);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getMean method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetMean() {
        System.out.println("getMean");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getMean(window, timeLag);
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getStdDev method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetStdDev() {
        System.out.println("getStdDev");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getStdDev(window, timeLag);
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getZ method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetZ() {
        System.out.println("getZ");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        Number expResult = null;
        Number result = instance.getZ(window, timeLag);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getZValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetZValue() {
        System.out.println("getZValue");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getZValue(window, timeLag);
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getItemCount method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetItemCount() {
        System.out.println("getItemCount");
        int window = 0;
        CorrelationMatrix instance = null;
        int expResult = 0;
        int result = instance.getItemCount(window);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getX method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetX() {
        System.out.println("getX");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        Number expResult = null;
        Number result = instance.getX(window, timeLag);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getXValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetXValue() {
        System.out.println("getXValue");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getXValue(window, timeLag);
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getY method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetY() {
        System.out.println("getY");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        Number expResult = null;
        Number result = instance.getY(window, timeLag);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getYValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetYValue() {
        System.out.println("getYValue");
        int window = 0;
        int timeLag = 0;
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getYValue(window, timeLag);
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getSeriesCount method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetSeriesCount() {
        System.out.println("getSeriesCount");
        CorrelationMatrix instance = null;
        int expResult = 0;
        int result = instance.getSeriesCount();
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }


    /**
     * Test of getSeriesKey method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetSeriesKey() {
        System.out.println("getSeriesKey");
        int window = 0;
        CorrelationMatrix instance = null;
        Comparable expResult = null;
        Comparable result = instance.getSeriesKey(window);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of indexOf method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testIndexOf() {
        System.out.println("indexOf");
        Comparable windowKey = null;
        CorrelationMatrix instance = null;
        int expResult = 0;
        int result = instance.indexOf(windowKey);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getMeanMinValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetMeanMinValue() {
        System.out.println("getMeanMinValue");
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getMeanMinValue();
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getMeanMaxValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetMeanMaxValue() {
        System.out.println("getMeanMaxValue");
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getMeanMaxValue();
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getStdDevMinValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetStdDevMinValue() {
        System.out.println("getStdDevMinValue");
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getStdDevMinValue();
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of getStdDevMaxValue method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testGetStdDevMaxValue() {
        System.out.println("getStdDevMaxValue");
        CorrelationMatrix instance = null;
        double expResult = 0.0;
        double result = instance.getStdDevMaxValue();
        assertEquals(expResult, result, 0.0);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of hashCode method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testHashCode() {
        System.out.println("hashCode");
        CorrelationMatrix instance = null;
        int expResult = 0;
        int result = instance.hashCode();
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }

    /**
     * Test of equals method, of class CorrelationMatrix.
     */
    @Test
	@Ignore
    public void testEquals() {
        System.out.println("equals");
        Object obj = null;
        CorrelationMatrix instance = null;
        boolean expResult = false;
        boolean result = instance.equals(obj);
        assertEquals(expResult, result);
        
        fail("The test case is a prototype.");
    }
}