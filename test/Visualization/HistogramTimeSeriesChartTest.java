package Visualization;

import com.fastdtw.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class HistogramTimeSeriesChartTest {

    @Test
    public void testArrayUtilsContains(){

        double[] values = new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        // fails
        assertTrue(ArrayUtils.contains(values, Double.NaN));

        double negInf = Double.NEGATIVE_INFINITY;
        double posInf = Double.POSITIVE_INFINITY;
        double nan = Double.NaN;

        System.out.println(String.format("nan == nan: %s", nan == nan));                // false
        System.out.println(String.format("negInf == negInf: %s", negInf == negInf));    // true
        System.out.println(String.format("posInf == posInf: %s", posInf == posInf));    // true
        System.out.println(String.format("negInf == posInf: %s", negInf == posInf));    // false

    }

    @Test public void testFindNaN(){

        double[] values = new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        // fails
//        assertTrue(Arrays.contains(values, Double.NaN));
        // both succeed
        assertTrue(Arrays.contains(values, Double.POSITIVE_INFINITY));
        assertTrue(Arrays.contains(values, Double.NEGATIVE_INFINITY));

    }

    public static int indexOf(double[] array, double valueToFind, int startIndex) {
        if (ArrayUtils.isEmpty(array)) {
            return -1;
        }
        if (startIndex < 0) {
            startIndex = 0;
        }
        if(Double.isNaN(valueToFind)){
            for (int i = startIndex; i < array.length; i++) if (Double.isNaN(array[i])) return i;
        } else {
            for (int i = startIndex; i < array.length; i++) if (valueToFind == array[i]) return i;
        }

        return -1;
    }

}