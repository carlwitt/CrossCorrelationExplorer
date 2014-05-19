/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.ComplexSequence;
import Data.TimeSeries;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Carl Witt
 */
@Ignore
public class CorrelationTest extends DFTTest {


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
        CorrelationMatrix expected = new CorrelationMatrix(new CorrelationMetadata(f, g, 4, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        expected.append(expected.new Column(ComplexSequence.create(new double[]{1, 0, 0, 0}, new double[4]), 0, 0));
        expected.append(expected.new Column(ComplexSequence.create(new double[]{0, 1, 0}, new double[3]), 4, 0));
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