package Data.Correlation;

import Data.TimeSeries;
import org.junit.Test;

import static org.junit.Assert.*;

public class CorrelationMetadataTest {

    TimeSeries tsA = new TimeSeries(new double[]{});
    CorrelationMetadata a = new CorrelationMetadata(tsA, tsA, 4, -1, 1, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 2);
    CorrelationMetadata b = new CorrelationMetadata(tsA, tsA, 4, -1, 1, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 3);

    @Test
    public void testEquals() throws Exception {

        assertFalse(a.equals(b));
        assertFalse(b.equals(a));

    }

    @Test
    public void testHashCode() throws Exception {

        assertNotEquals(a.hashCode(), b.hashCode());

    }
}