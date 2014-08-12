package Data.Correlation;

import Data.TimeSeries;
import Data.Windowing.WindowMetadata;

import java.util.ArrayList;


/**
 * Contains the naive (reference) algorithm to compute a correlation matrix.
 * This is the (standard?) asymmetric version that takes windows from the first time series X at the specified offsets (window overlap)
 * and considers all lag windows from the second time series Y that are within range tauMin and tauMax.
 * The resulting correlation matrix is not symmetric since the base window rastering for X is usually coarser than the
 * lag window rastering for Y.
 *
 * ! IGNORES the tauStep parameter of the metadata !
 *
 * Created by Carl Witt
 */
@Deprecated
public class AsymmetricCrossCorrelation extends CrossCorrelation {

    /**
     * Naive windowed cross correlation algorithm. The computation scheme used is the asymmetric (standard) cross correlation that conceptually shifts only the second time series and leaves the first one fixed.
     * ! IGNORES the tauStep parameter of the metadata !
     * @param metadata describes the computation input.
     * @return the windowed cross correlation between the two time series.
     */
    public static CorrelationMatrix naiveCrossCorrelation(WindowMetadata metadata){

        return CrossCorrelation.naiveCrossCorrelation(metadata, AsymmetricCrossCorrelation::naiveCrossCorrelationAtomic);

    }

    /**
     * Naive windowed cross correlation algorithm for two time series. Is used by {@link #naiveCrossCorrelation(Data.Windowing.WindowMetadata)} to compute the partial results.
     * The computation scheme used is the asymmetric (standard) cross correlation that conceptually shifts only the second time series and leaves the first one fixed.
     * ! IGNORES the tauStep parameter of the metadata !
     * @param metadata describes the computation input.
     * @return the windowed cross correlation between two time series.
     */
    protected static CorrelationMatrix naiveCrossCorrelationAtomic(WindowMetadata metadata){

        CorrelationMatrix result = new CorrelationMatrix(metadata);

        assert metadata.setA.size() == 1 && metadata.setB.size() == 1 : "The atomic version of the cross correlation matrix algorithm takes only two time series.";

        TimeSeries tsA = metadata.setA.get(0);
        TimeSeries tsB = metadata.setB.get(0);

        int baseWindowFrom = 0;

        while(baseWindowFrom + metadata.windowSize-1 < tsA.getSize()){

            ArrayList<Double> correlationCoefficients = new ArrayList<>();
            int lagWindowFrom = Math.max(0, baseWindowFrom + metadata.tauMin);
            int tau = lagWindowFrom - baseWindowFrom;
            int minTau = tau;
            while(baseWindowFrom + tau + metadata.windowSize-1 < tsB.getSize() && tau <= metadata.tauMax){

                correlationCoefficients.add(correlationCoefficient(tsA, tsB, baseWindowFrom, baseWindowFrom+ metadata.windowSize-1, tau));
                tau++;
            }

            // copy means from computed list to array
            double[] means = new double[correlationCoefficients.size()];
            for (int i = 0; i < means.length; i++) means[i] = correlationCoefficients.get(i);
            // create 0 values standard deviation array
            double[] stdDevs = new double[means.length];
            result.append(result.new CorrelationColumnBuilder(baseWindowFrom, minTau).mean(means).standardDeviation(stdDevs).build());

            baseWindowFrom += metadata.baseWindowOffset;
        }

        return result;

    }

}
