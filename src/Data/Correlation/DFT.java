package Data.Correlation;

import Data.ComplexSequence;
import Data.Correlation.CorrelationMatrix.Column;
import Data.TimeSeries;
import com.google.common.base.Preconditions;
import com.sun.istack.internal.NotNull;

/**
 * Implements the Discrete Fourier Transform for double arrays.
 * @author Carl Witt
 * TODO: direct computation of cross correlation faster if window size is small?    
 * 
 */
public class DFT {
    
    /** Two time series with length &lt; BRUTEFORCE_LENGTH will be cross correlated using the brute force algorithm. */
    public static int CC_BRUTEFORCE_LENGTH = 0;

    /** Behaviors to deal with not available (NA) values in the data. 
     * REPLACE_WITH_ZERO will treat NA values as zero
     * LEAVE_UNCHANGED will perform all computations without further treatment
     * NA_FAIL will raise an error if NA values exist in the input.
     */
    public static enum NA_ACTION{
//        NA_FAIL,
        REPLACE_WITH_ZERO,
        LEAVE_UNCHANGED
    };
    
    public static NA_ACTION naAction = NA_ACTION.REPLACE_WITH_ZERO;
    
    /* FORWARD denotes the standard Discrete Fourier Transform (DFT) and INVERSE denotes the inverse DFT. */
    public static enum DIRECTION{FORWARD, INVERSE};
    
    /** Computes the cross correlation of two time series via spectrum multiplication (using the DFT). */
    public static TimeSeries crossCorrelation(@NotNull TimeSeries tsA, @NotNull TimeSeries tsB){
        
        assert(tsA.getSize() == tsB.getSize());
        
        // use the time series values (function values) as real coefficients in a sequence of complex numbers
        ComplexSequence f = ComplexSequence.create(tsA.getDataItems().im);
        ComplexSequence g = ComplexSequence.create(tsB.getDataItems().im);
        
        // handle not a number values according to the static DFT.naAction attribute
        handleNaValues(f, g);
        
        ComplexSequence FfConj  = transform(DIRECTION.FORWARD, f).conjugate();
        ComplexSequence Fg      = transform(DIRECTION.FORWARD, g);
        ComplexSequence result  = transform(DIRECTION.INVERSE, FfConj.pointWiseProduct(Fg));
        
        // the cross correlation of two real-valued sequences is also real-valued, so the real part contains all information
        return new TimeSeries(result.re);
    }
    
    /** Computes the windowed cross correlation of two time series.
     * I.e. partitions both time series into time series of windowSize items and performs the cross correlation on the first windows, second windows, etc.
     * @param t1 The reference time series
     * @param t2 The time series that is being shifted (or, more exactly, which windows are shifted)
     * @param windowSize If windowSize is zero, a single window containing all entries is used.
     * @return A matrix containg the results of the cross correlations of the single windows as columns.
     */
    public static CorrelationMatrix crossCorrelation(@NotNull TimeSeries t1, @NotNull TimeSeries t2, int windowSize){
        
        // convert from coordinates (re = x, im = y) to complex numbers representing the time series values (re = y, im = 0)
        ComplexSequence f = ComplexSequence.create(t1.getDataItems().im);
        ComplexSequence g = ComplexSequence.create(t2.getDataItems().im);
        
        // handle not a number values according to the static DFT.naAction attribute
        handleNaValues(f, g);
        
        if(windowSize == 0)
            windowSize = Math.max(f.length, g.length);
        
        int N = f.size();
        if( f.size() != g.size() ) 
            System.err.println("Input sequences to windowed cross correlation have to have the same length. ");
        
        CorrelationMatrix result = new CorrelationMatrix(new CorrelogramMetadata(t1, t2, windowSize, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED)); //result = ComplexSequence.create(new double[f.size()], new double[f.size()]);
        
        // iterate over all windows. Integer division N/windowSize automatically rounds up.
        for (int w = 0; w < f.length; w+=windowSize) {
            
            // window extend    
            int left  = w;
            int right = Math.min(left + windowSize-1, N-1);
            
            ComplexSequence windowOfF = ComplexSequence.create(f, left, right);
            ComplexSequence windowOfG = ComplexSequence.create(g, left, right);
            
            TimeSeries resultWindow;
            int nextPowerOfTwo = (int) Math.pow(2,intLog2(windowOfF.size()));
            
            if(windowOfF.size() != nextPowerOfTwo){
                resultWindow = bruteForceCrossCorrelation(new TimeSeries(windowOfF.re), new TimeSeries(windowOfG.re));
            } else {
                resultWindow = crossCorrelation(new TimeSeries(windowOfF.re), new TimeSeries(windowOfG.re));
            }

            int numInputValues = right - left + 1;
//            int numOutputValues = resultWindow.getSize();
//            int zerosToRemove = numOutputValues - numInputValues;
            ComplexSequence useValues = ComplexSequence.create(resultWindow.getDataItems(),0,numInputValues-1);
            result.append(new Column(ComplexSequence.create(useValues.im), t1.getDataItems().re[w]));
        }
        return result;
    }
    
    /** Brute force cross correlation WITHOUT using DFT.
     * @param tsA The reference time series
     * @param tsB The time series to be shifted 
     * @return Brute force cross correlation of both time series
     */
    public static TimeSeries bruteForceCrossCorrelation (@NotNull TimeSeries tsA, @NotNull TimeSeries tsB){

        Preconditions.checkArgument(tsA.getSize() == tsB.getSize(), 
                "Complex sequences must have the same length. Given:%s\n%s",tsA,tsB);
        
        // handle not a number values according to the static DFT.naAction attribute
        handleNaValues(tsA.getDataItems(), tsB.getDataItems());
        
        int N = tsA.getSize();
        
        double[] correlation = new double[N];
        
        for (int timeLag = 0; timeLag < N; timeLag++) {
            
            double cc = 0;  // cross correlation function value at this time lag
            for (int t = 0; t < N; t++) {
                cc += tsA.getDataItems().im[t] * tsB.getDataItems().im[ (t+timeLag)%N ];
            }
            correlation[timeLag] = cc;
        }
        
        return new TimeSeries(ComplexSequence.create(tsA.getDataItems().re,correlation));
    }
    
    /** Transforms a sequence of complex numbers from time domain into frequency domain. */
    protected static ComplexSequence transform(DIRECTION direction, ComplexSequence a){
        
        ComplexSequence result = bitReverseCopy(a);
        
        int n = result.size();
        double TWOPI = 2. * Math.PI;
        
        // the real and imaginary parts of the bit reverse copy of the input
        double real[], imgy[];
        if(direction == DIRECTION.INVERSE){
            real = result.im;
            imgy = result.re;
        } else {
            real = result.re;
            imgy = result.im;
        }
        
        for (int s = 1; s <= intLog2(n); s++) {
            
//System.out.println("   s: "+s);
            int m = (int) Math.pow(2, s);
            int mHalf = m/2;
            
            for (int k = 0; k < n; k += m) {
//System.out.println("   k: "+k);
                for (int j = 0; j < mHalf; j++) {
                    
                    double omega_R = Math.cos(-j * TWOPI / m);
                    double omega_I = Math.sin(-j * TWOPI / m);
                    
//System.out.println("even: "+(k+j));
//System.out.println(" odd: "+(k+j+mHalf));
                    
                    double Aodd_R = real[k+j+mHalf];
                    double Aodd_I = imgy[k+j+mHalf];
                    double Aeven_R = real[k+j];
                    double Aeven_I = imgy[k+j];
                   
                    double t_R = omega_R * Aodd_R - omega_I * Aodd_I;
                    double t_I = omega_R * Aodd_I + omega_I * Aodd_R;
                    double u_R = Aeven_R;
                    double u_I = Aeven_I;
                    
                    real[k+j] = u_R + t_R;
                    imgy[k+j] = u_I + t_I;
                    real[k+j+mHalf] = u_R - t_R;
                    imgy[k+j+mHalf] = u_I - t_I;
                }
            }
        }
        
        if(direction == DIRECTION.INVERSE){
            for (int j = 0; j < result.length; j++) {
                real[j] /= n;
                imgy[j] /= n;
            }
        }
        return ComplexSequence.create(result,0,a.size()-1);
//        return ComplexSequence.create(result,0,a.length-1);
        
    }
    
    /** Creates a new array with the elements of the array reordered such that the new index of an element equals the bit reversed old index of the array.
        For bit reversal, the binary representation that has the minimum number of bits to represent all indices. */
    protected static ComplexSequence bitReverseCopy(ComplexSequence a){
        
        Integer numBits = intLog2(a.size());
        // pad the input if its length is not a power of 2
        if(numBits == null){
            System.err.println("Padded the input array with zeros to be able to transform it!");
            numBits = (int) Math.ceil(Math.log(a.size())/Math.log(2));
//            result = bitReverseCopy(ComplexSequence.create(Arrays.copyOf(a.re, nextPower), Arrays.copyOf(a.im, nextPower)));
        }
        int nextPower = (int) Math.pow(2,numBits);
        
        double[] permutation_R = new double[nextPower];
        double[] permutation_I = new double[nextPower];
        
        for (int i = 0; i < a.size(); i++) {
            permutation_R[bitReverse(i, numBits)] = a.re == null ? 0 : a.re[i];
            permutation_I[bitReverse(i, numBits)] = a.im == null ? 0 : a.im[i];
        }
        
        return ComplexSequence.create(permutation_R, permutation_I);
    }

    /** Returns the number with the reversed binary representation of the first numBits lowest order bits of number. 
     * Inspired by the algorithm from http://stackoverflow.com/questions/3165776/reverse-bits-in-number 
     * Note that Integer.reverse() won't work since the number of bits must be specified
     */
    protected static int bitReverse(int number, int numBits) {
        int result = 0;
        // takes the lowest order bit of x and appends it as lowest order bit of the result number
        // this way, the last bit will be the first (like in a queue)
        for (int j = 0; j < numBits; j++) {
            result <<= 1;           // "create" empty lowest order bit by shifting all present bits to the left
            result |= (number&1);   // copy lowest order bit 
            number >>= 1;           // discard the copied bit
        }
        return result;
    }

    /** Checks if NaN values are present in the input sequences and acts accordingly. */
    private static void handleNaValues(ComplexSequence... sequences) {
        for(ComplexSequence cs : sequences){
            for (int i = 0; i < cs.im.length; i++) {
                switch(naAction){
                    case REPLACE_WITH_ZERO:
                        if(Double.isNaN(cs.im[i])) {
                            cs.im[i] = 0.;
                        }
                        break;
//                    case NA_FAIL:
//                        if(Double.isNaN(d))
//                            throw new Exception("Not a number in input data. Can not compute DFT or Cross Correlation.");
//                        break;
                }
            }
        }
    }
    
    /** Calculates the integer logarithm of an integer. If the number is not a power of 2, returns the next larger integer of the result logarithm. */
    protected static Integer intLog2(int number){
        double log = Math.log(number) / Math.log(2);
        if(Math.abs(number - Math.pow(2,Math.ceil(log))) > 1e-100){
            return (int) Math.ceil(log);
        }
        return (int) log;
    }
 
    public static RuntimePrediction computationTime = new RuntimePrediction(new RuntimePrediction.RuntimePredictable() {
        public long complexity(int n) { 
            // n*log_2(n)
            return (long) (n * (Math.log(n)/Math.log(2))); 
        }
        // generates two real valued double sequences of length n and computes their cross correlation
        public void execute(int n) {
            double realValues1[] = new double[n];
            double realValues2[] = new double[n];
            // use random values for both sequences
            for (int i = 0; i < n; i++){
                realValues1[i] = Math.random();
                realValues2[i] = Math.random();
            }
            ComplexSequence testData1 = ComplexSequence.create(realValues1);
            ComplexSequence testData2 = ComplexSequence.create(realValues2);
            // compute
            bruteForceCrossCorrelation(new TimeSeries(testData1), new TimeSeries(testData2));
        }
    });
    
}
