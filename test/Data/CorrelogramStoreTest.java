/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CrossCorrelation;
import Data.Windowing.WindowMetadata;
import com.google.common.collect.Lists;
import org.junit.*;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 * @author Carl Witt
 */
public class CorrelogramStoreTest {
    
    CorrelationMatrix c1, c2;
    DataModel dataModel = new DataModel();
    
    TimeSeries a = new TimeSeries(ComplexSequence.create(
                new double[]{  -1.0000000e+01,   -0.9000000e+01,   -0.7000000e+01,   -0.4000000e+01,   -0.1000000e+01,    0.0000000e+01,    0.1000000e+01,    0.3000000e+01}, 
                new double[]{1, 2, 3, 4, 5, 6, 7, 8}));
    TimeSeries b = new TimeSeries(ComplexSequence.create(
                new double[]{  -1.0000000e+01,   -0.9000000e+01,   -0.7000000e+01,   -0.4000000e+01,   -0.1000000e+01,    0.0000000e+01,    0.1000000e+01,    0.3000000e+01}, 
                new double[]{ 1 ,  0 ,  0 ,  0 ,  0 ,  0 ,  0 ,  0 }));
        
    public CorrelogramStoreTest() {
        
        dataModel.put(1, a);
        dataModel.put(2, b);

        c1 = new CorrelationMatrix(new WindowMetadata(a, b, 0, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        List<CorrelationMatrix.CorrelationColumn> matrix1 = Lists.newArrayList(
                c1.new CorrelationColumnBuilder( 0,  0).mean(new double[]{1.,4.,7.}).standardDeviation( new double[]{11.,44.,77.}).build(),
                c1.new CorrelationColumnBuilder( 3,  0).mean(new double[]{2.,5.,8.}).standardDeviation( new double[]{22.,55.,88.}).build(),
                c1.new CorrelationColumnBuilder( 6,  0).mean(new double[]{3.,6.,9.}).standardDeviation( new double[]{33.,66.,99.}).build()
        );
        c1.append(matrix1.get(0));
        c1.append(matrix1.get(1));
        c1.append(matrix1.get(2));

        c2 = new CorrelationMatrix(new WindowMetadata(a, b, 8, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        c2.append(c2.new CorrelationColumnBuilder( 0,  0).mean(new double[]{1.,0.,0.}).standardDeviation( new double[]{111.,444.,777.}).build());
        c2.append(c2.new CorrelationColumnBuilder(3, 0).mean(new double[]{0., 1., 0.}).standardDeviation(new double[]{222., 555., 888.}).build());
        c2.append(c2.new CorrelationColumnBuilder(6, 0).mean(new double[]{0., 0., 1.}).standardDeviation(new double[]{333., 666., 999.}).build());
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testMetadataInequality() {
        System.out.println("metadata inequality");

        WindowMetadata m1 = new WindowMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.REPLACE_WITH_ZERO, 1);
        WindowMetadata m2 = new WindowMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        
        // the way to deal with NaN values is different, so a cache result can not be used
        assertNotSame(m1, m2);
    }
    
    /**
     * Test the cache function of the correlogram store.
     */
    @Test
    public void testGetResult_Metadata() {
        System.out.println("getResult");
        
        CorrelogramStore.clear();
        
        WindowMetadata metadata = new WindowMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix result = CorrelogramStore.getResult(metadata);
        
        // should now be retrieved from cache
        assertEquals(CorrelogramStore.getResult(metadata), result);

        // results with different NaN strategies can not be exchanged
        WindowMetadata metadata2 = new WindowMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.REPLACE_WITH_ZERO, 1);
        assertFalse(CorrelogramStore.contains(metadata2));
    }

    @Test @Ignore
    public void testContains_metadata() {
        System.out.println("contains metadata");
        
        boolean expResult = false;
        boolean result = CorrelogramStore.contains(new WindowMetadata(a, b, 3, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        assertEquals(expResult, result);
        
        // depends on whether the zero is interpreted as a single window, but that makes look-ups more complicated
        expResult = true;
        result = CorrelogramStore.contains(new WindowMetadata(a, b, 0, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        assertEquals(expResult, result);
    }

}