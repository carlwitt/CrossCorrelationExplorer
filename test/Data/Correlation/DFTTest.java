/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.Correlation.DFT;
import Data.ComplexSequence;
import Data.TimeSeries;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 *
 * @author Carl Witt
 */
public class DFTTest {

    // bit reversed numbers from 0 to 7 (represented as 3 bit binary numbers)
    int[] bitRevToEight = new int[]{0,4,2,6,1,5,3,7};

    // more or less arbitrary complex numbers for testing
    ComplexSequence arbitraryPattern = ComplexSequence.create(
                new double[]{2,2,0,4,2,0,3}, 
                new double[]{2,8,0,5,1,9,8});
    
    // the DFT of arbitraryPattern as computed by R
    ComplexSequence arbitraryPatternDFT = ComplexSequence.create(
            new double[]{
                13.000000,
                -7.327180,
                5.405962,
                7.096225,
                -14.776165,
                3.850706,
                6.750452
            }, new double[]{
                +33.000000,
                +4.481399,
                -3.389525,
                -9.655191,
                -6.623247,
                -8.466707,
                +4.653271
            });
    
    static class ReferenceAlgorithms{
        
        /** Reference implementation according to http://nayuki.eigenstate.org/page/how-to-implement-the-discrete-fourier-transform .
         *  A naive implementation of the DFT formula using Euler's equation.
         */
        static ComplexSequence DFT(ComplexSequence in) {
            int n = in.re.length;
            ComplexSequence result = ComplexSequence.create(new double[n], new double[n]);
            for (int k = 0; k < n; k++) {  // For each output element
                double sumreal = 0;
                double sumimag = 0;
                for (int t = 0; t < n; t++) {  // For each input element
                    sumreal +=  in.re[t]*Math.cos(2*Math.PI * t * k / n) + in.im[t]*Math.sin(2*Math.PI * t * k / n);
                    sumimag += -in.re[t]*Math.sin(2*Math.PI * t * k / n) + in.im[t]*Math.cos(2*Math.PI * t * k / n);
                }
                result.re[k] = sumreal;
                result.im[k] = sumimag;
            }
            return result;
        }
    }
    
    // -------------------------------------------------------------------------
    // external validation: tests with reference values
    // -------------------------------------------------------------------------
    
    @Test
    // test that the reference implementation matches the R implementation
    public void testReferenceDFT() {
        
        System.out.println(arbitraryPattern);
        
        ComplexSequence referenceResult = ReferenceAlgorithms.DFT(arbitraryPattern);
        assertEquals(arbitraryPatternDFT, referenceResult);
        
    }
    
    @Test
    @Ignore
    // Compare to reference DFT implementation
    public void testCompareWithReference(){
        System.out.println("compareWithReference");
        
        // generate random input of length N
        int N = 10;
        Random rg = new Random(1);
        double[] randomInputRe = new double[N];
        double[] randomInputIm = new double[N];
        for (int i = 0; i < N; i++) {
            randomInputRe[i] = rg.nextDouble();
            randomInputIm[i] = rg.nextDouble();
        }
        
        ComplexSequence initial = ComplexSequence.create(randomInputRe, randomInputIm);
        ComplexSequence transformed = DFT.transform(DFT.DIRECTION.FORWARD, initial);
        ComplexSequence reference = ReferenceAlgorithms.DFT(initial);

        assertEquals(reference, transformed);
        
        ComplexSequence twiceTransformed = DFT.transform(DFT.DIRECTION.FORWARD, transformed);
        ComplexSequence refTwTransformed = ReferenceAlgorithms.DFT(reference);

        assertEquals(refTwTransformed, twiceTransformed);
//        System.out.println(ReferenceAlgorithms.DFT(initial));
    }
    
    @Test
    @Ignore
    // Test known arbitrary output (computed using the fft function in R)
    public void testArbitraryTransform(){
        System.out.println("testArbitraryTransform");
        
        ComplexSequence result = DFT.transform(DFT.DIRECTION.FORWARD, arbitraryPattern);
        
        System.out.println("transformed series: "+result);
        
        assertEquals(arbitraryPatternDFT, result);
    }
    
    // -------------------------------------------------------------------------
    // internal validation: tests of mathematical properties 
    // -------------------------------------------------------------------------
    
    @Test
    @Ignore
    // For a unit impulse at t = 1, the frequency domain signal is very simple. Check that the intermediate
    // steps of the DFT are correct.
    public void testTransformationProcess(){
        System.out.println("transformationProcess");

        TimeSeries f = generateUnitImpulse(0, 8);
        ComplexSequence unitImpulse = ComplexSequence.create(f.getDataItems().im);
        TimeSeries g = generateUnitImpulse(1, 8);
        ComplexSequence shiftedUnitImpulse = ComplexSequence.create(g.getDataItems().im);
        
        TimeSeries c = DFT.crossCorrelation(f, g);
        
        // the DFT of g is defined as re: cos(x), im: -sin(x)
        double TwoPi = 2*Math.PI;
        ComplexSequence Fg = ComplexSequence.create(new double[8], new double[8]);
        for (int i = 0; i < 8; i++) {
            Fg.re[i] =  Math.cos(i*TwoPi/8);
            Fg.im[i] = -Math.sin(i*TwoPi/8);
        }
        
        // assert that g is transformed correctly
        assertEquals(DFT.transform(DFT.DIRECTION.FORWARD, shiftedUnitImpulse), Fg);
        
        // CC(f,g) is the inverse transformed of ( F(f)* pointwise multiplied with F(g) )
        // CC(f,g) equals g
        // thus, we assert that F(f)* pointwise multiplied with F(g) is F(g)
        assertEquals(Fg,
                DFT.transform(DFT.DIRECTION.FORWARD, unitImpulse)
                .conjugate()
                .pointWiseProduct(DFT.transform(DFT.DIRECTION.FORWARD, shiftedUnitImpulse)) );
    }
    
    @Test
    @Ignore
    // Check F^{-1}(F(a)) = a
    public void testInverseTransform(){
        System.out.println("inverseTransform");
//        ComplexSequence initial = ComplexSequence.create(new double[]{1,2,3,4,5,6,7,8}, new double[]{7,6,5,4,3,2,1,0});
        ComplexSequence initial = ComplexSequence.create(
                new double[]{1,2,3,4,5,6,7}, 
                new double[]{2,8,0,5,1,9,8});
        ComplexSequence transformed = DFT.transform(DFT.DIRECTION.FORWARD, initial);
        ComplexSequence shouldBeInitial = DFT.transform(DFT.DIRECTION.INVERSE, transformed);
        assertEquals(initial, shouldBeInitial);
    }
    
    // -------------------------------------------------------------------------
    // internal validation: tests of special cases
    // -------------------------------------------------------------------------
    
    /** Generates a all-zero signal z of length sequenceLength with x(0) = 1. */
    protected TimeSeries generateUnitImpulse(int impulseOffset, int sequenceLength) {
        double[] functionValues = new double[sequenceLength];
        functionValues[impulseOffset] = 1;
        return new TimeSeries(functionValues);
    }
    protected TimeSeries generateConstantSignal(double value, int sequenceLength) {
        double[] functionValues = new double[sequenceLength];
        Arrays.fill(functionValues, value);
        return new TimeSeries(functionValues);
    }
    
    @Test
    @Ignore
    // a constant 0 + 0i signal is transformed into the same
    public void testAllZeroGivesAllZero() {
        System.out.println("allZeroGivesAllZero");
        ComplexSequence cZero = ComplexSequence.create(new double[8], new double[8]);
        ComplexSequence exp = ComplexSequence.create(new double[8], new double[8]);
        ComplexSequence transformed = DFT.transform(DFT.DIRECTION.FORWARD, cZero);
        assertEquals(transformed, exp);
    }
    
    @Test
    @Ignore
     // a signal that's zero everywhere, except at x(0) = 1 gives a constant-1-function in frequency domain
    public void testUnitImpulseGivesDCSignal() {
        System.out.println("unitImpulseGivesDCSignal");
        
        double[] unitImpulseReValues = generateUnitImpulse(0, 10).getDataItems().im;
        ComplexSequence unitImpulse = ComplexSequence.create(unitImpulseReValues);
        
        double[] constantReValues = generateConstantSignal(1, 10).getDataItems().im;
        ComplexSequence constant = ComplexSequence.create(constantReValues);
        
        ComplexSequence transformed = DFT.transform(DFT.DIRECTION.FORWARD, unitImpulse);
        assertEquals(constant, transformed);
        
        ComplexSequence unitImpulseBack = DFT.transform(DFT.DIRECTION.INVERSE, transformed);
        assertEquals(unitImpulse, unitImpulseBack);
    }
    
    // -------------------------------------------------------------------------
    // auxiliary functions
    // -------------------------------------------------------------------------
    
    @Test
    // tests the bit-reversal of binary numbers
    public void testBitReverse() {
        System.out.println("bitReverse");
        int numBits = (int) Math.round( Math.log(8) / Math.log(2) );
        for (int number = 0; number < 8; number++) {
            assertEquals(DFT.bitReverse(number, numBits), bitRevToEight[number]);
        }
    }
    
    @Test
    // tests the reordering of array elements according to bit reversed indices
    // and that the padding happens correctly
    public void testBitReverseCopy(){
        System.out.println("bitReverseCopy");
        
        ComplexSequence initial  = ComplexSequence.create(new double[]{1,2,3,4,5,6});
        ComplexSequence expected = ComplexSequence.create(new double[]{1,5,3,0,2,6,4,0});
        
        ComplexSequence result = DFT.bitReverseCopy(initial);
        
        assertEquals(expected, result);
        
        // test that it is a real copy
        initial.re[0] = 100;
        assertEquals(expected, result);
    }
    
    @Test
    // tests that the next larger power of two is correctly computed
    public void testIntLog2(){
        System.out.println("intLog2");
        
        int a = 33554432;
        int expected = 25;
        assertEquals((int) DFT.intLog2(a), expected);
        
        a -= 1;
        expected = 25;
        assertEquals((int) DFT.intLog2(a), expected);
        
        a = 7;
        expected = 3;
        assertEquals((int) DFT.intLog2(a), expected);
    }
    
    
}