package Data.Statistics;

import Data.Correlation.CorrelationMatrix;
import Data.TimeSeries;
import Data.Windowing.WindowMetadata;
import org.junit.Before;
import org.junit.Test;

import static Data.Statistics.AggregatedCorrelationMatrix.MatrixRegionData;
import static org.junit.Assert.assertEquals;

public class AggregatedCorrelationMatrixTest {

    CorrelationMatrix matrix;
    MatrixRegionData prototype;
    AggregatedCorrelationMatrix aggregatedCorrelationMatrix;
    MatrixRegionData[][] expected;

    @Before
    public void initTestData(){

        // build matrix
        WindowMetadata metadata = new WindowMetadata.Builder(0,3,1,1,1).tsA(new TimeSeries(0, 1.,2.,3.)).tsB(new TimeSeries(1,1.,2.,3.)).build();
        matrix = new CorrelationMatrix(metadata);
        matrix.getColumns().add(matrix.new CorrelationColumnBuilder(0,0).interquartileRange(new double[]{1,2,3,4}).mean(new double[]{-1,-2,-3,-4}).build());
        matrix.getColumns().add(matrix.new CorrelationColumnBuilder(1,0).interquartileRange(new double[]{5, 6, 7, 8}).mean(new double[]{-5, -6, -7, -8}).build());
        matrix.getColumns().add(matrix.new CorrelationColumnBuilder(2,0).interquartileRange(new double[]{9, 10, 11, 12}).mean(new double[]{-9, -10, -11, -12}).build());

        // configure aggregation
        prototype = new MatrixRegionData();
        prototype.width = 2;
        prototype.height = 3;
        prototype.CORRELATION_DIM = CorrelationMatrix.MEAN;
        prototype.UNCERTAINTY_DIM = CorrelationMatrix.IQR;

        // aggregate matrix
        aggregatedCorrelationMatrix = new AggregatedCorrelationMatrix(matrix, new double[CorrelationMatrix.NUM_STATS][], prototype);
        assert aggregatedCorrelationMatrix.getRegions().length == 2;
        assert aggregatedCorrelationMatrix.getRegions()[0].length == 2;

        // build expected values for aggregation
        expected = new MatrixRegionData[][]{
            { // first column
                new MatrixRegionData(0, 0, 2, 3, 0, 3, 2, 3, -7.0, -6.25, -4.0, -1.75, -1.0, 1.0, 4.0, 7.0, null),      // first row
                new MatrixRegionData(0, 3, 2, 3, 0, 3, 2, 1, -8.0, -8.0, -6.0, -4.0, -4.0, 4.0, 6.0, 8.0, null),        // second row
            },
            { // second column
                new MatrixRegionData(2, 0, 2, 3, 0, 3, 1, 3, -11.0, -11.0, -10.0, -9.0, -9.0, 9.0, 10.0, 11.0, null),       // first row
                new MatrixRegionData(2, 3, 2, 3, 0, 3, 1, 1, -12.0, -12.0, -12.0, -12.0, -12.0, 12.0, 12.0, 12.0, null)     // second row
            }
        };
    }

    /**
     * Test whether the aggregated matrix corresponds to the expected values.
     */
    @Test
    public void testConstruct(){

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                assertEquals(expected[i][j], aggregatedCorrelationMatrix.getRegions()[i][j]);
            }
        }
    }

    /**
     * Tests that the correct regions for a query by cell are returned.
     */
    @Test
    public void testGetRegion() {

        // lower left region
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(expected[0][0], aggregatedCorrelationMatrix.getRegion(i,j));
            }
        }

        // upper left region
        for (int i = 0; i < 2; i++) {
            assertEquals(expected[0][1], aggregatedCorrelationMatrix.getRegion(i,3));
        }

        // lower right region
        for (int j = 0; j < 3; j++) {
            assertEquals(expected[1][0], aggregatedCorrelationMatrix.getRegion(2,j));
        }

        // upper right region
        assertEquals(expected[1][1], aggregatedCorrelationMatrix.getRegion(2,3));
    }
}