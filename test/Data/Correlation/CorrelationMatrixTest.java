package Data.Correlation;

import Data.TimeSeries;
import Data.TimeSeriesTest;
import Data.Windowing.WindowMetadata;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CorrelationMatrixTest {

    // tests the correct precomputation of the means and roots of summed squares of all windows
    @Test public void testPrecomputeData(){

        TimeSeries ts1 = new TimeSeries(1, 1,10,7,4,3,12);
        TimeSeries ts2 = new TimeSeries(1, 1,2,3,2,0,1);

        int windowSize = 3, baseWindowOffset = 1, tauMin = 0, tauMax = 0;
        WindowMetadata metadata = new WindowMetadata(ts1, ts2, windowSize, tauMin, tauMax, 1, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        CorrelationMatrix cc = new CorrelationMatrix(metadata);
        cc.precomputeTerms();

        double[] expectedMeansA = new double[]{6.0, 7.0, 4.666666666666666, 6.333333333333332, Double.NaN, Double.NaN};
        double[] expectedMeansB = new double[]{2.0, 2.3333333333333335, 1.666666666666667, 1.0000000000000002, Double.NaN, Double.NaN};
        double[] expectedL2NormsA = new double[]{6.48074069840786, 4.242640687119285, 2.9439202887759492, 6.97614984548545, Double.NaN, Double.NaN};
        double[] expectedL2NormsB = new double[]{1.4142135623730951, 0.816496580927726, 2.1602468994692865, 1.4142135623730951, Double.NaN, Double.NaN};

        assertArrayEquals(expectedMeansA, cc.meansA[0], 1e-15);
        assertArrayEquals(expectedMeansB, cc.meansB[0], 1e-15);
        assertArrayEquals(expectedL2NormsA, cc.L2NormsA[0], 1e-15);
        assertArrayEquals(expectedL2NormsB, cc.L2NormsB[0], 1e-15);

    }

    @Test public void compareWithHandComputedResult(){

        TimeSeries tsA = new TimeSeries(1, 1,2,3,4,4,3,2,1);
        TimeSeries tsB = new TimeSeries(1, 4,3,2,1,1,2,3,4);

        int windowSize = 4, baseWindowOffset = 2, tauMin = -1, tauMax = 3, tauStep = 2;
        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        CorrelationMatrix expected = CrossCorrelation.naiveCrossCorrelation(metadata);

        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println("Expected: "+expected);
        System.out.println("Result: "+result);

        for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
            assertEquals(expected.getMin(STAT), result.getMin(STAT), 1e-15);
            assertEquals(expected.getMax(STAT), result.getMax(STAT), 1e-15);
        }

        for (int i = 0; i < expected.columns.size(); i++) {
            CorrelationMatrix.CorrelationColumn expectedColumn = expected.getColumn(i);
            CorrelationMatrix.CorrelationColumn resultColumn = result.getColumn(i);
            for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++)
                assertArrayEquals(expectedColumn.data[stat], resultColumn.data[stat], 1e-15);
        }

    }

    @Test public void testCompareWithNaive() {

        int numTimeSeries = 50;
        int length = 100;

        List<TimeSeries> tsA = TimeSeriesTest.randomTimeSeries(numTimeSeries, length, 1l);
        List<TimeSeries> tsB = TimeSeriesTest.randomTimeSeries(numTimeSeries, length, 1l);

//        TimeSeries tsA = new TimeSeries(1, 1,2,3,4,4,3,2,1);
//        TimeSeries tsB = new TimeSeries(1, 4,3,2,1,1,2,3,4);

//        List<TimeSeries> setA = Arrays.asList(ts.get(0), ts.get(2));
//        List<TimeSeries> setB = Arrays.asList(ts.get(3), ts.get(8));

        int windowSize = 4, baseWindowOffset = 2, tauMin = -1, tauMax = 3, tauStep = 2;
//        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);

        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        long before = System.currentTimeMillis();

        CorrelationMatrix expected = CrossCorrelation.naiveCrossCorrelation(metadata);

        System.out.println(String.format("naive: %s", System.currentTimeMillis() - before));

        before = System.currentTimeMillis();
        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println(String.format("mem: %s", System.currentTimeMillis()-before));

//        System.out.println("Expected: "+expected);
//        System.out.println("Result: "+result);

        for (int STAT = 0; STAT < CorrelationMatrix.NUM_STATS; STAT++) {
            assertEquals(expected.getMin(STAT), result.getMin(STAT), 1e-15);
            assertEquals(expected.getMax(STAT), result.getMax(STAT), 1e-15);
        }

        for (int i = 0; i < expected.columns.size(); i++) {
            CorrelationMatrix.CorrelationColumn expectedColumn = expected.getColumn(i);
            CorrelationMatrix.CorrelationColumn resultColumn = result.getColumn(i);
            for (int stat = 0; stat < CorrelationMatrix.NUM_STATS; stat++)
                assertArrayEquals(expectedColumn.data[stat], resultColumn.data[stat], 1e-15);
        }

    }

    @Test @Ignore public void testPerformance() {

        int numTimeSeries = 100;
        int length = 1000;

        List<TimeSeries> tsA = TimeSeriesTest.randomTimeSeries(numTimeSeries, length, 1l);
        List<TimeSeries> tsB = TimeSeriesTest.randomTimeSeries(numTimeSeries, length, 1l);

        int windowSize = 200, baseWindowOffset = 30, tauMin = -100, tauMax = 100, tauStep = 10;
        WindowMetadata metadata = new WindowMetadata(tsA, tsB, windowSize, tauMin, tauMax, tauStep, baseWindowOffset);
        CorrelationMatrix.setSignificanceLevel(metadata, 0.05);

        long before = System.currentTimeMillis();
        CorrelationMatrix result = new CorrelationMatrix(metadata);
        result.compute();

        System.out.println(String.format("milliseconds: %s", System.currentTimeMillis()-before));

    }

    // test whether there's a performance difference in the way array elements are accessed.
    // java doesn't exactly use row-major or column-major order because multidimensional arrays are arrays of objects.
    // nevertheless, it turns out that accessing a multidimensional array by dereferencing the innermost array with the fastest running variable (~row-wise, for i ... for j ... sum += array[i][j])
    // CAN BE 30x faster than the other way around. This heavily depends on the array dimensions! also, the effects are much more pronounced for read access than for write access.
    // the reason is probably the cache line size. if accessing different arrays, they need to be evaded from the cache repeatedly, if they are too large. ---
    int[] sizes = new int[]{500,5,30000};
    private double[][][] randomArray(){
        double[][][] randoms = new double[sizes[0]][sizes[1]][sizes[2]];
        for (int i = 0; i < sizes[0]; i++) for (int j = 0; j < sizes[1]; j++) for (int k = 0; k < sizes[2]; k++) randoms[i][j][k] = Math.random();
        return randoms;
    }
    /** Accesses the array row-wise, i.e. array[1][1], array[1][2], ... array[m][1], ..., array[m][n]*/
    @Test @Ignore public void rowMajorOrder(){
        double[][][] randoms = randomArray();
        double sum = 0;
        long before = System.currentTimeMillis();
        for (int i = 0; i < sizes[0]; i++) for (int j = 0; j < sizes[1]; j++) for (int k = 0; k < sizes[2]; k++) sum += randoms[i][j][k];
        System.out.println(String.format("sum: %s (%s ms)", sum, System.currentTimeMillis() - before));
    }
    /** Accesses the array column-wise, i.e. array[1][1], array[2][1], ... array[1][n], ..., array[m][n]*/
    @Test @Ignore public void columnMajorOrder(){
        double[][][] randoms = randomArray();
        double sum = 0;
        long before = System.currentTimeMillis();
        for (int i = 0; i < sizes[2]; i++) for (int j = 0; j < sizes[1]; j++) for (int k = 0; k < sizes[0]; k++) sum += randoms[k][j][i];
        System.out.println(String.format("sum: %s (%s ms)", sum, System.currentTimeMillis() - before));
    }

    //------------------------------------------------------------------------------------------------------
    // testing the java Arrays.parallel_ methods didn't yield positive results.
    // seems as if traditional loops are much faster than parallelism even for very large arrays (100 million entries)
    // speedups of around 2x-3x (probably ~ cores - 1.5) are possible if the operation on each array element is expensive.
    // in these cases, the notation is much shorter than initializing threads, splitting array ranges etc...
    //------------------------------------------------------------------------------------------------------
    private double[] randomDoubleArray(){
        Random random = new Random(1l);
        double[] cValues = new double[100000];
        for (int i = 0; i < cValues.length; i++) cValues[i] = random.nextDouble();
        return cValues;
    }
    @Test public void testParallelArray(){
        double[] cValues = randomDoubleArray();
        long before = System.currentTimeMillis();
        Arrays.parallelSetAll(cValues, value -> {
            double sum = 0;
            for (int i = 0; i < value; i++) {
                sum += i;
            }
            return sum;
        });
        System.out.println(String.format("System.currentTimeMillis() - before: %s", System.currentTimeMillis() - before));
        System.out.println(String.format("cValues[cValues.length-1]: %s", cValues[cValues.length - 1]));
    }
    @Test public void testSerialArray(){
        double[] cValues = randomDoubleArray();
        long before = System.currentTimeMillis();
        for (int i = 0; i < cValues.length; i++) {
            double sum = 0;
            for (int j = 0; j < i; j++) {
                sum += j;
            }
            cValues[i] = sum;
        }
        System.out.println(String.format("System.currentTimeMillis() - before: %s", System.currentTimeMillis() - before));
        System.out.println(String.format("cValues[cValues.length-1]: %s", cValues[cValues.length - 1]));
    }

    @Test public void testMemoryUsage() throws IOException {

        byte[] doubleArray = new byte[10000000];
        System.in.read();

    }

}