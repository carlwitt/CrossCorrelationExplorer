package Data.Windowing;

import Data.Correlation.CrossCorrelation;
import Data.TimeSeries;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class WindowMetadataTest {

    TimeSeries tsA = new TimeSeries(new double[]{});
    WindowMetadata a = new WindowMetadata(tsA, tsA, 4, -1, 1, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 2);
    WindowMetadata b = new WindowMetadata(tsA, tsA, 4, -1, 1, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 3);

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