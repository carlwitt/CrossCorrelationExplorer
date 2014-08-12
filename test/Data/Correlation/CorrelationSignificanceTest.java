package Data.Correlation;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class CorrelationSignificanceTest {

    /**
     * Tests that the computed critical values equal a standard text book table.
     */
    @Test
    public void testFindCriticalValue(){

        // data taken from T. C. Urdan "Statistics in Plain English" 3rd ed.
        double[] significance = new double[]{ 0.1,     0.05,    0.01,     0.001 };
        double[] df1          = new double[]{ 6.314,  12.706,  63.657,  636.619  };
        double[] df13         = new double[]{ 1.771,   2.160,   3.012,    4.221 };
        double[] df120        = new double[]{ 1.658,   1.980,   2.617,    3.373 };

//        CorrelationSignificance s1   = new CorrelationSignificance(1, 0.01);
        CorrelationSignificance s13  = new CorrelationSignificance(13, 0.01);
        CorrelationSignificance s120 = new CorrelationSignificance(120, 0.01);

//        for (int i = 0; i < significance.length; i++)
//            assertEquals(df1[i], s1.criticalTValue(significance[i]), 1e-3);

        for (int i = 0; i < significance.length; i++)
            assertEquals(df13[i], s13.criticalTValue(significance[i]), 1e-3);

        for (int i = 0; i < significance.length; i++)
            assertEquals(df120[i], s120.criticalTValue(significance[i]), 1e-3);
    }

    /**
     * Tests that the simplified correlation significance test gives the same results as a full fledged correlation significance test.
     */
    @Test public void testThresholdComputation(){

        int[] degreesOfFreedom = new int[]{3, 10, 100, 200, 500, 1000, 10000};
        double[] significances = new double[]{0.2, 0.1, 0.01, 0.001, 0.0001, 0.00001};

        Random random = new Random(10);
        for ( int N : degreesOfFreedom){

            for (double alpha : significances ){

//                System.out.println(String.format("N = %s alpha = %s", N, alpha));
                CorrelationSignificance significance = new CorrelationSignificance(N, alpha);
                double criticalTValue = significance.criticalTValue(alpha);
                for (int i = 0; i < 100000; i++) {

                    double r = random.nextDouble();
                    double t = r * Math.sqrt((N-2)/(1-Math.pow(r,2)));
                    if( t >= criticalTValue + 1e-15 && !significance.significanceTest(r)){
                        System.out.println(String.format("mismatch. r = %s t = %s criticalT = %s, criticalR = %s", r, t, criticalTValue, significance.criticalCorrelationValue));
                    }
                    assertEquals( t - criticalTValue > 1e-15, significance.significanceTest(r));

                }

            }
        }

    }

    /**
     * Tests the performance gain by precomputing the critical correlation value.
     * Is approximately 5x faster than always computing the t value first.
     * 3e9 fast tests require approximately 1 minute. for a full scale 1000x1000 correlogram, 30e9 tests need to be performed.
     */
    @Test public void testPerformance(){

        Random random = new Random(1);
        int N = 200;
        double alpha = 0.01;
        long runs = 30000000l;
        CorrelationSignificance tester = new CorrelationSignificance(N, alpha);

        int significantCorrelations = 0;
        double criticalTValue = tester.criticalTValue(alpha);

        long before = System.currentTimeMillis();
        // compute t value for each correlation score
        for (int i = 0; i < runs; i++) {
            double r = random.nextDouble();
            double t = r * Math.sqrt((N-2)/(1-Math.pow(r,2)));
            if (t - criticalTValue > 1e-15) significantCorrelations++;

        }
        System.out.println("Time for naïve: "+(System.currentTimeMillis()-before) + " significant correlations: "+significantCorrelations);

        random = new Random(1);
        int significantCorrelationsB = 0;
        before = System.currentTimeMillis();
        // use direct comparison
        for (int i = 0; i < runs; i++) {

            double r = random.nextDouble();
            if (tester.significanceTest(r)) significantCorrelationsB++;

        }

        System.out.println("Time for precomputed: "+(System.currentTimeMillis()-before) + " significant correlations: "+significantCorrelationsB);

        assertEquals(significantCorrelations, significantCorrelationsB);

    }

}