package Visualization;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.scene.transform.Affine;
import org.junit.Test;

import java.util.function.Function;

public class CorrelogramTest {

    @Test public void testCellToData() throws Exception {

        int tsLength = 81;
        double[] xValues = new double[tsLength];
        for (int i = 0; i < xValues.length; i++) xValues[i] = i+1960;

        TimeSeries ts = new TimeSeries(1, xValues, new double[tsLength]);
        WindowMetadata metadata = new WindowMetadata.Builder(-100,100,40,1,10).tsA(ts).tsB(ts).build();

        Correlogram c = new Correlogram(new MultiDimensionalPaintScale(10,10));
        Affine cellToData = c.cellToScreen(metadata);

        System.out.println(String.format("cellToData: %s", cellToData.transform(0,0)));
        System.out.println(String.format("cellToData: %s", cellToData.transform(1,0)));
        System.out.println(String.format("cellToData: %s", cellToData.transform(0,1)));
        System.out.println(String.format("cellToData: %s", cellToData.transform(1,-1)));

    }

    // 120 has 15 divisors
    // 180 has 17 divisors
    // 240 has 19 divisors
    // divisors of 180 int[] divisors = new int[]{1,2,3,4,5,6,9,10,12,15}; //,18,20,30,36,45,60,90
    @Test public void maxDivisors(){

        Function<Integer, Integer> numberOfDivisors = number -> {
            int divs = 0;
            for (int i = 1; i < number; i++) if(number % i == 0) divs++;
            return divs;
        };

        int best, divs = 1;

        for (int number = 100; number < 250; number++) {

            int divsOfNumber = numberOfDivisors.apply(number);
            if(divsOfNumber >divs){
                best = number;
                divs= divsOfNumber;
                System.out.println(String.format("best: %s", best));
                System.out.println(String.format("divs: %s", divs));
            }

        }


    }

}