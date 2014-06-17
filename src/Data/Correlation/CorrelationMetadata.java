package Data.Correlation;

/**
 * Stores computation input parameters that are specific to cross correlation.
 * Created by Carl Witt on 09.06.14.
 */
class CorrelationMetadata {

    private final double significance;
    // further attributes could be percentiles to use for quantifying median uncertainty

    public CorrelationMetadata(double significance) {
        this.significance = significance;
    }
}
