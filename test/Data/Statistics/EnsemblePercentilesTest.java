package Data.Statistics;

import Data.Experiment;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class EnsemblePercentilesTest {

    @Test
    public void testConstructor() throws IOException {

        Experiment experiment = new Experiment("data/ld.nc");
        EnsemblePercentiles ep = new EnsemblePercentiles(experiment.dataModel);
//        ep.print();

    }

}