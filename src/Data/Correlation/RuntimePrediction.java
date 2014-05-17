package Data.Correlation;

import java.util.HashMap;

/**
 *
 * @author Carl Witt
 */
public class RuntimePrediction {
    
    public static interface RuntimePredictable{
        
        /** Gives the values of the Big oh (preferrably theta) notation expressions for input size n. */
        public long complexity(int n);
        /** Performs a test computation of input size n. */
        public void execute(int n);
    
    }
    
    /** Average milliseconds per analytical step (the number of analytical steps for input size n is given by complexity(n)). */
    private double stepFactor;
    /** Whether stepFactor was calculated yet. Lazy calibration. */
    private boolean calibrated = false;
    
    /** Saves the execution times by input size. */
    public final HashMap<Integer, Long> testRuns = new HashMap<>();
    
    /** The wrapper for the method to profile. */
    private final RuntimePredictable testSubject;
    
    public RuntimePrediction(RuntimePredictable testSubject){
        this.testSubject = testSubject;
    }
    
    /** Determines stepFactor through a series of test computations. The calibration time can be limited. */
    void calibrate(){
    // * @param maxMilliSeconds The maximum number of milliseconds to spend. Is ignored if negative. 
    // (Difficult because another thread terminating this one wouldn't allow to calculate the stepSize (a running average needs the number of total computations, which is unknown in the beginnging.)
    
        int base = 2;  // test input sizes of exponential growth
        int numComputations = 10;
        long stepsTotal = 0;
        long totalExecutionTime = 0;
        System.out.print("Profiling: ");
        
        // profile a number of input sizes
        for (int computation = 1; computation <= numComputations; computation++) {
            // compute input size
            int exponent = computation;
            int inputSize = (int)Math.pow(base, exponent);
            stepsTotal += testSubject.complexity(inputSize);
            // profile
            long before = System.currentTimeMillis();
                testSubject.execute(inputSize);
            long executionTime = System.currentTimeMillis() - before;
            
            // save
            testRuns.put(inputSize, executionTime);
            totalExecutionTime += executionTime;
            System.out.print( Math.round((1.*computation/numComputations)*100) + "% ");
        }
        System.out.println("");
        
        // average
        stepFactor = 1. * totalExecutionTime / stepsTotal;
        calibrated = true;
    }
    
    /** Predicts the needed execution time of testSubject in seconds. */
    public float predict(int inputSize){
        
        if( ! calibrated )
            calibrate();
        
        if(testRuns.containsKey(inputSize))
            System.out.println("previous run took "+ (testRuns.get(inputSize) / 1000.f));
//            return testRuns.get(inputSize) / 1000.f;
            
        return (float) (1. * testSubject.complexity(inputSize) * stepFactor / 1000.f);
        
    }

}
