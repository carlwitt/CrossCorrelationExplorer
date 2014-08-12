package Data.Windowing;

import Data.TimeSeries;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class WindowMetadataTest {

    TimeSeries tsA = new TimeSeries(1, new double[]{});
    WindowMetadata a = new WindowMetadata(tsA, tsA, 4, -1, 1, 1, 2);
    WindowMetadata b = new WindowMetadata(tsA, tsA, 4, -1, 1, 1, 3);

    @Test
    public void testGetDifferentTimeLags() throws Exception {
        WindowMetadata[] md = new WindowMetadata[]{new WindowMetadata(tsA, tsA, 4, -10, 10, 1, 2),
                                                    new WindowMetadata(tsA, tsA, 4, -10, 10, 2, 2),
                                                    new WindowMetadata(tsA, tsA, 4, -10, 10, 3, 2),
                                                    new WindowMetadata(tsA, tsA, 4, -10, 10, 4, 2),
                                                    new WindowMetadata(tsA, tsA, 4, -10, 10, 5, 2),
                                                    new WindowMetadata(tsA, tsA, 4, -10, 10, 9, 2),
                                                    new WindowMetadata(tsA, tsA, 4, -10, 10, 10, 2)};
        for(WindowMetadata a : md)
            System.out.println(String.format("Arrays.toString(a.testGetDifferentTimeLags): %s", Arrays.toString(a.getDifferentTimeLags())));
    }

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