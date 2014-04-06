/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix.Column;
import Data.TimeSeries;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Carl Witt
 */
public class CorrelationTest extends DFTTest {

    @Test
    /** Tests the brute force correlation computation. */
    public void testBruteForceCC(){
        System.out.println("bruteForceCC");
        
        // input
        TimeSeries f = new TimeSeries(new double[]{ 2, 3,-1, 1, 0, 0, 0});
        f.id = 1;
        TimeSeries g = new TimeSeries(new double[]{ 0, 0, 0, 1, 4, 2,-1});
        g.id = 2;
        
        // output
        TimeSeries initialF = new TimeSeries(new double[]{ 2, 3,-1, 1, 0, 0, 0});
        initialF.id = 1;
        TimeSeries initialG = new TimeSeries(new double[]{ 0, 0, 0, 1, 4, 2,-1});
        initialG.id = 2;
        TimeSeries expected = new TimeSeries(new double[]{1,3,1,11,15,1,-2});
        TimeSeries resultBrute = DFT.bruteForceCrossCorrelation(f,g);
        
        resultBrute.id = expected.id;
//        System.out.println("DFT result:\n"+DFT.crossCorrelation(f, g, 7));
//        System.out.println("BRUTE result:\n"+resultBrute);
        
        // check results are correct
        assertEquals(expected, resultBrute);
        
        // check initial data is not altered
        assertEquals(initialF, f);
        assertEquals(initialG, g);
    }

    @Test
    // A unit impulse at t = 0 and a unit impulse at t = 1 correlate with value one at lag tau = 1 and zero elsewhere
    public void testImpulseCorrelation() {
        System.out.println("impulseCorrelation");
        TimeSeries f = generateUnitImpulse(0, 7);
        TimeSeries g = generateUnitImpulse(1, 7);
        
        TimeSeries expected = generateUnitImpulse(1, 7);
        CorrelationMatrix result = DFT.crossCorrelation(f, g, 0);
        
        System.out.println("expected "+expected);
        System.out.println("result "+result);
        assertArrayEquals(expected.getDataItems().im, result.columns.get(0).mean, ComplexSequence.EQ_COMPARISON_THRESHOLD);
    }

    @Test
    public void testConstantOneSignal() {
        System.out.println("constantOneSignal");
        TimeSeries f = new TimeSeries(new double[]{2, 3, 4, 5, 6, 7, 8});
        TimeSeries g = new TimeSeries(new double[]{1, 1, 1, 1, 1, 1, 1});
        TimeSeries expected = new TimeSeries(new double[]{35, 35, 35, 35, 35, 35, 35});
        CorrelationMatrix result = DFT.crossCorrelation(f, g, 0);
        assertArrayEquals(expected.getDataItems().im, result.columns.get(0).mean,ComplexSequence.EQ_COMPARISON_THRESHOLD);
    }

    @Test
    // Tests that two windows of f and g produce predicted results
    public void testWindowedCrossCorrelation() {
        System.out.println("windowedCrossCorrelation");
        TimeSeries f = new TimeSeries(new double[]{1, 0, 0, 0, 1, 0, 0});
        TimeSeries g = new TimeSeries(new double[]{1, 0, 0, 0, 0, 1, 0});
        CorrelationMatrix expected = new CorrelationMatrix(new CorrelogramMetadata(f, g, 4, DFT.NA_ACTION.LEAVE_UNCHANGED));
        expected.append(new Column(ComplexSequence.create(new double[]{1, 0, 0, 0}, new double[4]), 0));
        expected.append(new Column(ComplexSequence.create(new double[]{0, 1, 0}, new double[3]), 4));
        CorrelationMatrix result = DFT.crossCorrelation(f, g, 4);
        assertEquals(expected, result);
    }

    @Test
    // Compares results of windowed CC for each window to brute force results
    public void testWindowedCrossCorrelation2() {
        System.out.println("windowedCrossCorrelation2");
        int seriesLength = 5;
        double[] valuesA = new double[seriesLength];
        double[] valuesB = new double[seriesLength];
        for (int i = 0; i < seriesLength; i++) {
            valuesA[i] = Math.round(Math.random() * 50);
            valuesB[i] = Math.round(Math.random() * 50);
        }
        TimeSeries f = new TimeSeries(valuesA);
        TimeSeries g = new TimeSeries(valuesB);
        TimeSeries result = new TimeSeries(DFT.crossCorrelation(f, g, seriesLength).columns.get(0).mean);
        TimeSeries expected = DFT.bruteForceCrossCorrelation(f, g);
        System.out.println(expected);
        System.out.println(result);
        assertEquals(expected, result);
    }
    
    
}