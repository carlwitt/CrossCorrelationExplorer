package Data.Statistics;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Test;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class CorrelationHistogramTest {

    /**
     * This test currently fails because there are rounding errors in the bin assignment, though the overal impression is the same.
     */
    @Test public void testHistogramCreation(){
        // The commons math empirical distribution can be used to generate histograms but there is no option to define the binning.
        // The binning is always dependent on the data (min/max).
        final int BIN_COUNT = 180;
        // what's strange about this is that adding Double.MIN_VALUE after 0.0 results in Double.MIN_VALUE being assigned to the bin that should cover [-1,0] although it
        // is larger than the maximum bin value
        double[] data = {-1.0, -0.3, 0.0, 1e-15, 0.2, 0.2, 0.333, 0.4, 1.0, 1.0, 1.0};

        int[] expectedHistogram = new int[BIN_COUNT];
        org.apache.commons.math3.random.EmpiricalDistribution distribution = new org.apache.commons.math3.random.EmpiricalDistribution(BIN_COUNT);
        distribution.load(data);
        int k = 0;
        for(org.apache.commons.math3.stat.descriptive.SummaryStatistics stats: distribution.getBinStats())
            expectedHistogram[k++] = (int) stats.getN();

        WindowMetadata metadata = new WindowMetadata.Builder(0, 0, 8, 1, 1).tsA(new TimeSeries(1,1)).tsB(new TimeSeries(2, 1)).build();
        CorrelationHistogram correlationHistogram = new CorrelationHistogram(metadata);

        correlationHistogram.setDistribution(0, data);
        int[] histogram = correlationHistogram.getHistogram(0);

        System.out.println(String.format("expected histogram: %s", Arrays.toString(expectedHistogram)));
        System.out.println(String.format("actual   histogram: %s", Arrays.toString(histogram)));

        distribution.getBinStats().stream().filter(summaryStatistics -> summaryStatistics.getN()>0).forEach(summaryStatistics -> System.out.println(String.format("summaryStatistics: %s", summaryStatistics)));

        assertArrayEquals(expectedHistogram, histogram);

    }
    /**
     * Tests how much error the logarithmic encoding scheme introduces.
     * All in all the relative errors are very small
     */
    @Test
    public void testLogarithmicShortEncoding(){

        int unsignedShortMaxValue = Short.MAX_VALUE*2+1;    // the largest value that an unsigned short can take
        int maxSupportedFrequency = 4000000;

        double forwardBase = Math.log(maxSupportedFrequency + 1) / unsignedShortMaxValue;
        double reverseBase = Math.exp(forwardBase);

        System.out.println(String.format("unsignedShortMaxValue: %s", unsignedShortMaxValue));
        System.out.println(String.format("Base for forward transform: %s", forwardBase));
        System.out.println(String.format("Base for reverse transform: %s", reverseBase));

        int samplingRate = 1;
        int maxOutputLines = 5000;

        boolean liveOutput = maxSupportedFrequency / samplingRate < maxOutputLines;

        DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics(maxSupportedFrequency+1);

        int breakPoint = Short.MAX_VALUE;
        // for each possible frequency, compute the according short value and observe the resulting error.
        // start at one because frequency 0 is transformed forth and back without loss.
        for (int i = 0; i <= maxSupportedFrequency; i++) {
            double mapped = i < breakPoint ? i : Math.ceil(Math.log(i + 1) / forwardBase);
            assert mapped <= unsignedShortMaxValue;
            short transformed = (short) mapped;
            int inverseTransformed = transformed >= 0 && transformed < breakPoint ? Short.toUnsignedInt(transformed) : (int) Math.pow(reverseBase, Short.toUnsignedInt(transformed))-1;
//            double inverseTransformed = Math.pow(reverseBase, Short.toUnsignedInt(transformed))-1;
//            int mapped = Math.max(1, i/unsignedShortMaxValue);
//            short transformed = (short) (mapped);
//            int inverseTransformed = Short.toUnsignedInt(transformed)*unsignedShortMaxValue;

            if(i % samplingRate == 0){
                double relativeError = 1. * Math.abs(inverseTransformed - i) / i;
                if(i>0) descriptiveStatistics.addValue(relativeError);
                if(liveOutput || i<1000 && Math.random()<1e-2 || Math.random()<1e-6){
                    System.out.println(String.format("> frequency\t\t\t%s", i));
                    System.out.println(String.format("transformed\t\t\t%s", Short.toUnsignedInt(transformed)));
                    System.out.println(String.format("inverseTransformed\t%s", inverseTransformed));
                    System.out.println(String.format("absolute error\t\t%s", inverseTransformed - i));
                    System.out.println(String.format("relative error\t\t%s", relativeError));
                }
            }
        }


//        if(errors.length < maxOutputLines)
//            System.out.println(String.format("Arrays.toString(errors): %s", Arrays.toString(errors)));

//        double[] errors = descriptiveStatistics.getValues();
//        List<String> lines = new ArrayList<>(errors.length);
//        for (int i = 0; i < errors.length; i++) lines.add(i + "\t" + errors[i]);
//        try {
//            FileUtils.writeLines(new File("errorStatistics.txt"), lines);   //Arrays.asList(Arrays.stream(errors).<String>mapToObj(value -> "" + value).toArray())
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        DoubleSummaryStatistics errorStatistics = Arrays.stream(descriptiveStatistics.getValues()).summaryStatistics();
        System.out.println(String.format("descriptiveStatistics.getMean(): %.4f per mil", descriptiveStatistics.getMean()*1000));
        System.out.println(String.format("errorStatistics: %s", errorStatistics));
        System.out.println("TimeSeriesAveragerTest.testLogarithmicShortEncoding");
    }

    // test the cyclic wrapping of value when casting to short
    @Test public void testCastToShort(){
        assertTrue((short) (Short.MAX_VALUE + 10) == Short.MIN_VALUE + 9);
        System.out.println(String.format("2<<16-1: %s", 2 << 16 - 1));
        System.out.println(String.format("2*Short.MAX_VALUE+1: %s", 2 * Short.MAX_VALUE +1));
        System.out.println(String.format("(short)65535: %s", Short.toUnsignedInt((short) 65535)));
        System.out.println(String.format("(short)65536: %s", Short.toUnsignedInt((short)65536)));
    }

    @Test public void testSignumTestByHighestOrderBit(){
        short a = -1;
        short b = 1;
        int aBits = a | 0;
        System.out.println(String.format("Integer.toBinaryString(a): %s", Integer.toBinaryString(a)));
    }

}