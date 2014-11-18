package Data.Statistics;

import Data.Correlation.CorrelationMatrix;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Arrays;

import static Data.Correlation.CorrelationMatrix.NUM_STATS;

/**
 *
 * Created by Carl Witt on 23.10.14.
 */
public class AggregatedCorrelationMatrix {

    /** the raw data for the aggregation. */
    CorrelationMatrix matrix;

    /** Contains all the parameters for the aggregation, e.g. columns and rows per region, source statistics, etc. */
    MatrixRegionData prototype;

    /** results of the aggregation. first dimension refers to column, second dimension refers to row. */
    MatrixRegionData[][] regions;

    /** the filtering ranges on all statistics that define the cells which are considered in the aggregation. */
    private double[][] matrixFilterRanges;

    /** Which statistics to use as source data for the aggregation. */
    private int correlationStatistic, uncertaintyStatistic;

    /** Reusable object for gathering e.g. percentile statistics about the distribution of means for the cells in a given region. */
    DescriptiveStatistics aggregateCorrelationStatistics = new DescriptiveStatistics();

    /**
     *
     * @param matrix see {@link #matrix}
     * @param matrixFilterRanges see {@link #matrixFilterRanges}
     * @param prototype contains all the parameters to use for the aggregation (will be applied to all regions)
     */
    public AggregatedCorrelationMatrix(CorrelationMatrix matrix, double[][] matrixFilterRanges, MatrixRegionData prototype) {
        this.matrix = matrix;
        this.matrixFilterRanges = matrixFilterRanges;
        this.prototype = prototype;
        aggregate();
    }

    /**
     * Computes the matrix region data for the given grid parameters (region width and height).
     */
    private void aggregate() {

        int numColumns = matrix.getSize();
        int numRows = matrix.metadata.getNumberOfDifferentTimeLags();
        int columnsPerRegion = prototype.width;
        int rowsPerRegion = prototype.height;
        int numRegionsHorizontal = (int) Math.ceil((double) numColumns / columnsPerRegion);
        int numRegionsVertical = (int) Math.ceil((double) numRows / rowsPerRegion);
        regions = new MatrixRegionData[numRegionsHorizontal][numRegionsVertical];

        for (int i = 0; i < numRegionsHorizontal; i++) {
            for (int j = 0; j < numRegionsVertical; j++) {
                MatrixRegionData newRegion = new MatrixRegionData(prototype);
                newRegion.column = i * columnsPerRegion;
                newRegion.row = j * rowsPerRegion;
                aggregateRegion(newRegion, matrixFilterRanges);
                regions[i][j] = newRegion;
            }
        }

    }

    /**
     * Aggregates cells from a square region of the matrix by finding some statistics on them.
     * @param region the object to take the paramters from and to store the results to
     * @param matrixFilterRanges specification of the current matrix filter (see {@link Data.SharedData#getMatrixFilterRanges()})
     */
    protected void aggregateRegion(AggregatedCorrelationMatrix.MatrixRegionData region, double[][] matrixFilterRanges) {

        aggregateCorrelationStatistics.clear();

        // clip start and end to bounds
        int maxColumnIdx = matrix.getSize() - 1;
        int maxRowIdx = matrix.metadata.getNumberOfDifferentTimeLags() - 1;
        int firstColumnIdx = Math.max(0, Math.min(region.column, maxColumnIdx));
        int firstRowIdx = Math.max(0, Math.min(region.row, maxRowIdx));
        int lastColumnIdx = Math.min(firstColumnIdx + region.width - 1, maxColumnIdx);
        int lastRowIdx = Math.min(firstRowIdx + region.height - 1, maxRowIdx);

        region.croppedWidth = lastColumnIdx - firstColumnIdx + 1;
        region.croppedHeight = lastRowIdx - firstRowIdx + 1;

        double minUncertainty = Double.POSITIVE_INFINITY, averageUncertainty = 0, maxUncertainty = Double.NEGATIVE_INFINITY;
        int notNaNCorrelations = 0, notNaNUncertainties = 0;

        int[] summedHistogram = new int[CorrelationHistogram.NUM_BINS];
        DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();

        for (int i = firstColumnIdx; i <= lastColumnIdx; i++) {

            CorrelationMatrix.CorrelationColumn correlationColumn = matrix.getColumn(i);

            evaluateCell:
            for (int j = firstRowIdx; j <= lastRowIdx; j++) {

                // check matrix filters
                // if the cell is filtered out, it will not contribute to the aggregation.
                for (int STAT = 0; STAT < NUM_STATS; STAT++) {
                    if(matrixFilterRanges[STAT] == null) continue;
                    if(correlationColumn.data[STAT][j] < matrixFilterRanges[STAT][0] ||
                            correlationColumn.data[STAT][j] > matrixFilterRanges[STAT][1])
                        continue evaluateCell; // filtered cell doesn't influence aggregation
                }

                // correlation dimension
                if(CorrelationMatrix.isValidStatistic(region.CORRELATION_DIM)){
                    double correlation = correlationColumn.data[region.CORRELATION_DIM][j];
                    if( ! Double.isNaN(correlation)){
                        aggregateCorrelationStatistics.addValue(correlation);
                        notNaNCorrelations++;
                    }
                }
                // uncertainty dimension
                if(CorrelationMatrix.isValidStatistic(region.UNCERTAINTY_DIM)){
                    double uncertainty = correlationColumn.data[region.UNCERTAINTY_DIM][j];
                    if( ! Double.isNaN(uncertainty)){
                        minUncertainty = Math.min(minUncertainty, uncertainty);
                        maxUncertainty = Math.max(maxUncertainty, uncertainty);
                        averageUncertainty += uncertainty;
                        notNaNUncertainties++;

                        assert Double.isNaN(minUncertainty) || minUncertainty >= 0 : String.format("Negative uncertainty: %s in column \n%s",minUncertainty, correlationColumn);
                        assert Double.isNaN(maxUncertainty) || maxUncertainty >= 0 : String.format("Negative max uncertainty: %s", maxUncertainty);
                    }
                } // if uncertainty statistic is set

                // compute region histogram as sum of histograms in the region
                // for some matrices generated with an older version of the program, the offline histograms might not have been computed.
                // normally, none of the histograms should be null, or all of them are null.
                int[] histogram;
                if(correlationColumn.histogram == null){
                    double[] correlationValues = matrix.computeSingleCell(i, j);
                    for(double r : correlationValues)
                        if( ! Double.isNaN(r)) descriptiveStatistics.addValue(r);
                    histogram = CorrelationHistogram.computeHistogram(descriptiveStatistics, CorrelationHistogram.NUM_BINS);
                } else {
                    histogram = correlationColumn.histogram.getHistogram(j);
                }
                // add histogram to the summed histogram
                for (int k = 0; k < histogram.length; k++) summedHistogram[k] += histogram[k];


            } // for row
        } // for column

        region.minCorrelation = notNaNCorrelations > 0 ? aggregateCorrelationStatistics.getMin() : Double.NaN;
        region.firstQuartileCorrelation = notNaNCorrelations > 0 ? aggregateCorrelationStatistics.getPercentile(25) : Double.NaN;
        region.medianCorrelation = notNaNCorrelations > 0 ? aggregateCorrelationStatistics.getPercentile(50) : Double.NaN;
        region.thirdQuartileCorrelation = notNaNCorrelations > 0 ? aggregateCorrelationStatistics.getPercentile(75) : Double.NaN;
        region.maxCorrelation = notNaNCorrelations > 0 ? aggregateCorrelationStatistics.getMax() : Double.NaN;

        region.minUncertainty = notNaNUncertainties > 0 ? minUncertainty : Double.NaN;
        region.averageUncertainty = notNaNUncertainties > 0 ? averageUncertainty / notNaNUncertainties : Double.NaN;
        region.maxUncertainty = notNaNUncertainties > 0 ? maxUncertainty : Double.NaN;

        region.cellDistribution = summedHistogram;

        region.isAggregated = region.croppedWidth > 1 || region.croppedHeight > 1;

        assert Double.isNaN(region.averageUncertainty) || region.averageUncertainty <= region.maxUncertainty : String.format("Average uncertainty %s larger than max uncertainty %s",region.averageUncertainty,region.maxUncertainty);

    }

    /**
     * @param column column index of the cell
     * @param row row index of the cell
     * @return the region containing the given cell
     */
    public MatrixRegionData getRegion(int column, int row){
        assert column >= 0 && row >= 0 : String.format("Negative cell indices given: column = %s, row = %s", column, row);
        int horizontalIdx = column / getColumnsPerRegion();
        int regionVerticalIdx = row / getRowsPerRegion();
        assert horizontalIdx < regions.length : String.format("No region for cell column %s (requesting region column %s of %s region columns)", column, horizontalIdx, regions.length);
        assert regionVerticalIdx < regions[horizontalIdx].length : String.format("No region for cell column %s cell row %s (requesting region column %s, region row %s of %s region rows.", column, row, horizontalIdx, regionVerticalIdx, regions[horizontalIdx].length);
        return getRegions()[horizontalIdx][regionVerticalIdx];
    }

    public int getRowsPerRegion() { return prototype.height; }
    public int getColumnsPerRegion() { return prototype.width; }

    public MatrixRegionData[][] getRegions() {
        return regions;
    }

    /**
     * Contains aggregated data about an excerpt of the correlation matrix. This can be a single cell but also a larger, contigous region.
     * The data structure is used by the correlogram to publish information about the active matrix region to other views, i.e. the cell distribution view and the correlogram legend.
     * Also, the Correlogram listens to itself, as it highlights the active region when the active region changes (because the user interacts with the correlogram).
     */
    public static class MatrixRegionData {
        // input parameters
        /** The upper left corner of the region to aggregate. */
        public int column, row;
        /** The side length (rows/columns) of the square region to aggregate. */
        public int width, height;
        /** Which data dimensions to use as source for the correlation and uncertainty data. */
        public int CORRELATION_DIM, UNCERTAINTY_DIM;

        // output parameters
        /** Number of matrix columns/rows actually covered by the aggregation region (can be influenced by clipping to the matrix size). */
        public int croppedWidth, croppedHeight;
        /** The correlation distribution summary of the cells in the region. */
        public double minCorrelation = Double.NaN, firstQuartileCorrelation = Double.NaN, medianCorrelation = Double.NaN, thirdQuartileCorrelation = Double.NaN, maxCorrelation = Double.NaN;
        /** The uncertainty distribution summary of the cells in the region */
        public double minUncertainty = Double.NaN, averageUncertainty = Double.NaN, maxUncertainty = Double.NaN;

        /** Whether the region is an aggregation of more than one correlation matrix cell. */
        public boolean isAggregated = false;

        /** The cell distribution aggregation in the region. The i-th int represents the number of entries in the i-th bin.
         * The i-th bin represents the correlation value range [-1 + i * 2/numBins, -1 + (i+1) * 2/numBins) where the last interval is closed and not open. */
        public int[] cellDistribution;

        public MatrixRegionData(){}

        public MatrixRegionData(MatrixRegionData prototype) {
            this.column = prototype.column;
            this.row = prototype.row;
            this.CORRELATION_DIM = prototype.CORRELATION_DIM;
            this.UNCERTAINTY_DIM = prototype.UNCERTAINTY_DIM;
            this.width = prototype.width;
            this.height = prototype.height;
            this.minCorrelation = prototype.minCorrelation;
            this.firstQuartileCorrelation = prototype.firstQuartileCorrelation;
            this.medianCorrelation = prototype.medianCorrelation;
            this.thirdQuartileCorrelation = prototype.thirdQuartileCorrelation;
            this.maxCorrelation = prototype.maxCorrelation;
            this.minUncertainty = prototype.minUncertainty;
            this.averageUncertainty = prototype.averageUncertainty;
            this.maxUncertainty = prototype.maxUncertainty;
            this.cellDistribution = prototype.cellDistribution;
        }

        public MatrixRegionData(int column, int row, int width, int height, int CORRELATION_DIM, int UNCERTAINTY_DIM, int croppedWidth, int croppedHeight, double minCorrelation, double firstQuartileCorrelation, double medianCorrelation, double thirdQuartileCorrelation, double maxCorrelation, double minUncertainty, double averageUncertainty, double maxUncertainty, int[] cellDistribution) {
            this.column = column;
            this.row = row;
            this.width = width;
            this.height = height;
            this.CORRELATION_DIM = CORRELATION_DIM;
            this.UNCERTAINTY_DIM = UNCERTAINTY_DIM;
            this.croppedWidth = croppedWidth;
            this.croppedHeight = croppedHeight;
            this.minCorrelation = minCorrelation;
            this.firstQuartileCorrelation = firstQuartileCorrelation;
            this.medianCorrelation = medianCorrelation;
            this.thirdQuartileCorrelation = thirdQuartileCorrelation;
            this.maxCorrelation = maxCorrelation;
            this.minUncertainty = minUncertainty;
            this.averageUncertainty = averageUncertainty;
            this.maxUncertainty = maxUncertainty;
            this.cellDistribution = cellDistribution;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MatrixRegionData that = (MatrixRegionData) o;

            if (CORRELATION_DIM != that.CORRELATION_DIM) return false;
            if (UNCERTAINTY_DIM != that.UNCERTAINTY_DIM) return false;
            if (Double.compare(that.averageUncertainty, averageUncertainty) != 0) return false;
            if (column != that.column) return false;
            if (croppedHeight != that.croppedHeight) return false;
            if (croppedWidth != that.croppedWidth) return false;
            if (Double.compare(that.firstQuartileCorrelation, firstQuartileCorrelation) != 0) return false;
            if (height != that.height) return false;
            if (isAggregated != that.isAggregated) return false;
            if (Double.compare(that.maxCorrelation, maxCorrelation) != 0) return false;
            if (Double.compare(that.maxUncertainty, maxUncertainty) != 0) return false;
            if (Double.compare(that.medianCorrelation, medianCorrelation) != 0) return false;
            if (Double.compare(that.minCorrelation, minCorrelation) != 0) return false;
            if (Double.compare(that.minUncertainty, minUncertainty) != 0) return false;
            if (row != that.row) return false;
            if (Double.compare(that.thirdQuartileCorrelation, thirdQuartileCorrelation) != 0) return false;
            if (width != that.width) return false;
            if (!Arrays.equals(cellDistribution, that.cellDistribution)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = column;
            result = 31 * result + row;
            result = 31 * result + width;
            result = 31 * result + height;
            result = 31 * result + CORRELATION_DIM;
            result = 31 * result + UNCERTAINTY_DIM;
            result = 31 * result + croppedWidth;
            result = 31 * result + croppedHeight;
            temp = Double.doubleToLongBits(minCorrelation);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(firstQuartileCorrelation);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(medianCorrelation);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(thirdQuartileCorrelation);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxCorrelation);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minUncertainty);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(averageUncertainty);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxUncertainty);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (isAggregated ? 1 : 0);
            result = 31 * result + Arrays.hashCode(cellDistribution);
            return result;
        }

        @Override
        public String toString() {
            return "MatrixRegionData{" +
                    "column=" + column +
                    ", row=" + row +
                    ", width=" + width +
                    ", height=" + height +
                    ", CORRELATION_DIM=" + CORRELATION_DIM +
                    ", UNCERTAINTY_DIM=" + UNCERTAINTY_DIM +
                    ", croppedWidth=" + croppedWidth +
                    ", croppedHeight=" + croppedHeight +
                    ", minCorrelation=" + minCorrelation +
                    ", firstQuartileCorrelation=" + firstQuartileCorrelation +
                    ", medianCorrelation=" + medianCorrelation +
                    ", thirdQuartileCorrelation=" + thirdQuartileCorrelation +
                    ", maxCorrelation=" + maxCorrelation +
                    ", minUncertainty=" + minUncertainty +
                    ", averageUncertainty=" + averageUncertainty +
                    ", maxUncertainty=" + maxUncertainty +
                    ", isAggregated=" + isAggregated +
                    ", cellDistribution=" + Arrays.toString(cellDistribution) +
                    '}';
        }
    }
}
