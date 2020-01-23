package Visualization;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class HistogramTimeSeriesChartTest {
//
//    @Test
//    public void testArrayUtilsContains(){
//
//        double[] values = new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
//
//        // fails
//        assertTrue(ArrayUtils.contains(values, Double.NaN));
//
//        double negInf = Double.NEGATIVE_INFINITY;
//        double posInf = Double.POSITIVE_INFINITY;
//        double nan = Double.NaN;
//
//        System.out.println(String.format("nan == nan: %s", nan == nan));                // false
//        System.out.println(String.format("negInf == negInf: %s", negInf == negInf));    // true
//        System.out.println(String.format("posInf == posInf: %s", posInf == posInf));    // true
//        System.out.println(String.format("negInf == posInf: %s", negInf == posInf));    // false
//
//    }
//
//    @Test public void testFindNaN(){
//
//        double[] values = new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
//
//        // fails
////        assertTrue(Arrays.contains(values, Double.NaN));
//        // both succeed
//        assertTrue(com.fastdtw.util.Arrays.contains(values, Double.POSITIVE_INFINITY));
//        assertTrue(com.fastdtw.util.Arrays.contains(values, Double.NEGATIVE_INFINITY));
//
//    }
//
//    public static int indexOf(double[] array, double valueToFind, int startIndex) {
//        if (ArrayUtils.isEmpty(array)) {
//            return -1;
//        }
//        if (startIndex < 0) {
//            startIndex = 0;
//        }
//        if(Double.isNaN(valueToFind)){
//            for (int i = startIndex; i < array.length; i++) if (Double.isNaN(array[i])) return i;
//        } else {
//            for (int i = startIndex; i < array.length; i++) if (valueToFind == array[i]) return i;
//        }
//
//        return -1;
//    }
//
//    // evaluating FastMath.pow(double, 3) is about 2.5 times faster than evaluating Math.pow(double, 3) in math.
//    // evaluating double * double * double is about 5 times faster than using FastMath.
//
//    @Test public void testPowPerformanceBrute3(){
//        double[] data = new double[1000000];
//        for (int i = 0; i < data.length; i++) data[i] = Math.random();
//
//        long before = System.currentTimeMillis();
//
//        double sum = 0;
//        for (int i = 0; i < data.length; i++) sum += data[i]*data[i]*data[i];
//
//        System.out.println(String.format("sum: %s", sum));
//        System.out.println(String.format("System.currentTimeMillis() - before: %s", System.currentTimeMillis() - before));
//    }
//
//    @Test public void testPowPerformanceFastMath(){
//        double[] data = new double[1000000];
//        for (int i = 0; i < data.length; i++) data[i] = Math.random();
//
//        long before = System.currentTimeMillis();
//
//        double sum = 0;
//        for (int i = 0; i < data.length; i++) sum += FastMath.pow(data[i], 3);
//
//        System.out.println(String.format("sum: %s", sum));
//        System.out.println(String.format("System.currentTimeMillis() - before: %s", System.currentTimeMillis() - before));
//    }
//
//    @Test public void testPowPerformanceMath(){
//        double[] data = new double[1000000];
//        for (int i = 0; i < data.length; i++) data[i] = Math.random();
//
//        long before = System.currentTimeMillis();
//
//        double sum = 0;
//        for (int i = 0; i < data.length; i++) sum += Math.pow(data[i], 3);
//
//        System.out.println(String.format("sum: %s", sum));
//        System.out.println(String.format("System.currentTimeMillis() - before: %s", System.currentTimeMillis() - before));
//    }
//
//    @Test public void testArrayUtils(){
//
//        double[] xValues = new double[]{1,2,3};
//        double[] minValues = new double[]{10,11,12};
//        double[] maxValues = new double[]{90,91,92};
//
//        double[] allXValues = ArrayUtils.clone(xValues);
//        double[] xValuesReverse = ArrayUtils.clone(xValues);
//        ArrayUtils.reverse(xValuesReverse);
//        allXValues = ArrayUtils.addAll(allXValues, xValuesReverse);
//
//        double[] allYValues = ArrayUtils.clone(minValues);
//        double[] maxYValuesReversed = ArrayUtils.clone(maxValues);
//        ArrayUtils.reverse(maxYValuesReversed);
//        allYValues = ArrayUtils.addAll(allYValues, maxYValuesReversed);
//
//        System.out.println(String.format("Arrays.toString(allXValues): %s", Arrays.toString(allXValues)));
//        System.out.println(String.format("Arrays.toString(allYValues): %s", Arrays.toString(allYValues)));
//
//
//
//    }

}