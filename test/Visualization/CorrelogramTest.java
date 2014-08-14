package Visualization;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import javafx.scene.transform.Affine;
import org.junit.Test;

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
}