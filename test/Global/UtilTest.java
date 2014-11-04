package Global;

import org.junit.Test;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;

import static org.junit.Assert.assertTrue;

public class UtilTest {

    @Test public void testTimeOut(){

        int thresholdMS = 2000;
        Util.TimeOutChecker timeOutChecker = new Util.TimeOutChecker(thresholdMS);

        double[] values = new double[1000000];
        boolean timeOut = false;

        long before = System.currentTimeMillis();

        for (int i = 0; i < 100000; i++) {
//            System.out.println(String.format("System.nanoTime(): %s", System.nanoTime()));
            for (int j = 0; j < 10; j++) {
                DoubleSummaryStatistics stats = Arrays.stream(values).summaryStatistics();
//                System.out.println(String.format("stats: %s", stats));
            }
            if(timeOutChecker.isTimeOut()){
                timeOut = true;
                break;
            }
        }

        assertTrue(timeOut);
        assertTrue(System.currentTimeMillis()-before < thresholdMS + 100);

    }

}