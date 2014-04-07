/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.DataModel;
import Data.TimeSeries;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 * @author Carl Witt
 */
public class CorrelationMatrixTest {
    
    DataModel dataModel;
    
    public CorrelationMatrixTest() {
    }
    
    @Before
    public void setUp() {
        dataModel = new DataModel();
        CorrelogramStore.clear();
    }
    
    @Test public void testSplitLagEven() {
        // time lags 0 ... 5 should be split rendered -2, -1, ..., 3
        int[] timeLags = new int[]{0, 1, 2, 3, 4, 5};
        int[] renderAs = new int[]{0, 1, 2, 3, -2, -1};
        
        for (int i = 0; i < timeLags.length; i++) {
            assertEquals(renderAs[i], CorrelationMatrix.splitLag(timeLags[i], timeLags.length)); 
        }
        
        assertEquals(-2, CorrelationMatrix.minLag(6));
        assertEquals(3, CorrelationMatrix.maxLag(6));
    }

    @Test public void testSplitLagOdd() {
        // time lags 0 ... 5 should be split rendered -2, -1, ..., 3
        int[] timeLags = new int[]{0, 1, 2, 3, 4, 5, 6};
        int[] renderAs = new int[]{0, 1, 2, 3, -3, -2, -1};
        
        for (int i = 0; i < timeLags.length; i++) {
            assertEquals(renderAs[i], CorrelationMatrix.splitLag(timeLags[i], timeLags.length)); 
        }
        
        assertEquals(-3, CorrelationMatrix.minLag(7));
        assertEquals(3, CorrelationMatrix.maxLag(7));
    }
    
    /**
     * Test of aggregation constructor, i.e. the computation of mean and standard deviation.
     */
    @Test public void testAggregateConstructor() {
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
     * Test of aggregation constructor, i.e. the computation of mean and standard deviation.
     */
    @Test
    public void testAggregateConstructor2() {
        System.out.println("aggregateConstructor2");
        
        TimeSeries ts1 = new TimeSeries(1, new double[]{1,2,3,4}, new double[]{1,1,1,1});
        TimeSeries ts2 = new TimeSeries(2, new double[]{1,2,3,4}, new double[]{1,2,3,4});
        TimeSeries ts3 = new TimeSeries(3, new double[]{1,2,3,4}, new double[]{1,4,1,8});
        
        dataModel.put(1, ts1);
        dataModel.put(2, ts2);
        dataModel.put(3, ts3);
        
        List<TimeSeries> timeSeriesSetA = Arrays.asList(new TimeSeries[]{ts1});
        List<TimeSeries> timeSeriesSetB = Arrays.asList(new TimeSeries[]{ts2,ts3});
        CorrelogramMetadata metadata = new CorrelogramMetadata(timeSeriesSetA, timeSeriesSetB, 4, DFT.NA_ACTION.LEAVE_UNCHANGED);
        CorrelationMatrix instance = CorrelogramStore.getResult(metadata);
        
        for (CorrelationMatrix m : CorrelogramStore.getAllResults()) {
            System.out.println(String.format("result: %s", m));
        }

        
        System.out.println(String.format("%s", instance));
//        System.out.println(String.format("correlogram store\n%s", CorrelogramStore.correlationMatricesByMetadata.entrySet()));
        for (int i = 0; i < 4; i++) {
            assertEquals(12, instance.columns.get(0).mean[i], 1e-5);
            assertEquals(2, instance.columns.get(0).stdDev[i], 1e-5);
        }
    }
    
    /**
     * Test the 
     */
    @Test
	@Ignore
    public void testGetMinMaxXY() {
        System.out.println("getting min/max x/y values");
        CorrelationMatrix result = new CorrelationMatrix(null);
        result.append(new CorrelationMatrix.Column(new double[]{1,1,1}, new double[]{2,1,0}, 1));
        result.append(new CorrelationMatrix.Column(new double[]{3.5,3.5,3.5}, new double[]{2,1,0}, 2));
        result.append(new CorrelationMatrix.Column(new double[]{6,6,6}, new double[]{2,1,0}, 3));
        System.out.println(String.format("mean min %s max %s\nstdd min %s max %s", result.getMeanMinValue(), result.getMeanMaxValue(), result.getStdDevMinValue(), result.getStdDevMaxValue()));
        System.out.println(String.format("X min %s max %s\nY min %s max %s", result.getMinX(), result.getMaxX(), result.getMinY(), result.getMaxY()));
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