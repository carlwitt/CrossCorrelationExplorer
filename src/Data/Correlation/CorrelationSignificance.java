package Data.Correlation;

import org.apache.commons.math3.distribution.TDistribution;

/**
 * Provides methods to test a given pearson product-moment correlation for significance.
 * Internally precomputes the critical correlation value (threshold) by first computing the critical
 * t-Value for a given number of degrees of freedom and a significance level and then solving for the correlation value.
 *
 * Created by Carl Witt on 02.06.14.
 */
public class CorrelationSignificance {

    /** The sample size used to parameterize the t-distribution. This is usually the size of a time series window. */
    private final int sampleSize;
    /** The desired level of significance (equals the probability to get a result as strong as the observed by chance). Is usually 0.05 (5%) or 0.01 (1%). */
    private final double significanceLevel;
    /** The minimum correlation value that leads to the rejection of the null hypothesis that there is no correlation. */
    final double criticalCorrelationValue;

    /**
     * @param sampleSize see {@link #sampleSize}
     * @param significanceLevel see {@link #significanceLevel}
     */
    public CorrelationSignificance(int sampleSize, double significanceLevel) {
        this.sampleSize = sampleSize;
        this.significanceLevel = significanceLevel;
        /* The minimum t value that leads to the rejection of the null hypothesis (that there is no correlation). */
        double criticalTValue = criticalTValue(significanceLevel);

        // since the formula for the standard error for the pearson correlation is strictly increasing,
        // only a single threshold for the correlation needs to be computed.
        // by comparing the raw correlation to the critical correlation spares the computation of the standard error for each correlation score.
        criticalCorrelationValue = criticalTValue / Math.sqrt(sampleSize + Math.pow(criticalTValue,2) - 2);
    }

    /**
     * @param pearsonCorrelation the pearson product-moment correlation between to random variables.
     * @return whether the observed cross correlation is significant according to degrees of freedom and significance level.
     */
    public boolean significanceTest(double pearsonCorrelation){
        return pearsonCorrelation >= criticalCorrelationValue;
    }

    /**
     * Used for significance testing in two-sided tests. Replaces a table of critical t values.
     * @param significanceLevel The p-value (probability of error), e.g. 5% = 0.05, 1% = 0.01 or 0.1% = 0.001
     * @return the minimum (positive) t-value that leads to the rejection of the null-hypothesis (no correlation present)
     * for a given significance level.
     */
    protected double criticalTValue(double significanceLevel){

        /** The method computes the critical t-Value such that the integral of the t distribution (with the given degrees of freedom)
         * within the limits of -criticalValue and criticalValue equals 1-significanceLevel.
         * Since the t distribution is symmetrical and strictly monotonous falling for t>0 a binary search can be used.
         * The method is started with a search interval of [0, +infinity]. It increases the value of t until [0, t] contains the
         * desired area. Then, the interval is probed in its middle and search is continued within one half.
         *
         * The method terminates usually within a few dozen steps but care must be taken when demanding too high precisions.
         * Precisions beyond 1e-15 for instance are likely to end up in an infinite loop since double arithmetic is quite inaccurate.
         */

        // given some alpha (risk I, or error probability), find the critical t value
        double desiredArea = 1-significanceLevel;

        TDistribution tDistribution = new TDistribution(sampleSize-2);

        double criticalT = 0;                                   // computation result
        double precision = 1e-10;                               // maximum error tolerated on the result
        double error;                                           // current error on the result
        double searchRangeFrom = 0,                             // current search interval
               searchRangeTo = Double.POSITIVE_INFINITY;

        do {

            // if there's no upper limit to the search interval, increase t
            if(Double.isInfinite(searchRangeTo)){
                criticalT += 100;
            // otherwise place t in the middle of the current search interval
            } else {
                criticalT = (searchRangeTo+searchRangeFrom)/2;
            }

            // probability for any t-Value to fall within range [-criticalT, criticalT]
            double twoTailedAreaForT = tDistribution.cumulativeProbability(criticalT) - tDistribution.cumulativeProbability(-criticalT);

            // adapt search interval based on computed area
            if(twoTailedAreaForT > desiredArea){
                searchRangeTo = criticalT;
            }
            if(twoTailedAreaForT < desiredArea){
                searchRangeFrom = criticalT;
            }

            // compute area
            error = Math.abs(twoTailedAreaForT - desiredArea);

//System.out.println(String.format("current critical value estimate: %s gives area %s error is %s", criticalT, twoTailedAreaForT, error));
        } while(error > precision);

        return criticalT;
    }

}
