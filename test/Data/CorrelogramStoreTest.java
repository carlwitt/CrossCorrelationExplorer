/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import Data.Correlation.*;
import com.google.common.collect.Lists;

import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

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
        
        List<CorrelationMatrix.Column> matrix1 = Lists.newArrayList(
                new CorrelationMatrix.Column(new double[]{1.,4.,7.}, new double[]{11.,44.,77.}, 0, 0),
                new CorrelationMatrix.Column(new double[]{2.,5.,8.}, new double[]{22.,55.,88.}, 3, 0),
                new CorrelationMatrix.Column(new double[]{3.,6.,9.}, new double[]{33.,66.,99.}, 6, 0)
        );
        c1 = new CorrelationMatrix(new CorrelationMetadata(a, b, 0, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        c1.append(matrix1.get(0));
        c1.append(matrix1.get(1));
        c1.append(matrix1.get(2));

        c2 = new CorrelationMatrix(new CorrelationMetadata(a, b, 8, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        c2.append(new CorrelationMatrix.Column(new double[]{1.,0.,0.}, new double[]{111.,444.,777.}, 0, 0));
        c2.append(new CorrelationMatrix.Column(new double[]{0.,1.,0.}, new double[]{222.,555.,888.}, 3, 0));
        c2.append(new CorrelationMatrix.Column(new double[]{0.,0.,1.}, new double[]{333.,666.,999.}, 6, 0));
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

        CorrelationMetadata m1 = new CorrelationMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.REPLACE_WITH_ZERO, 1);
        CorrelationMetadata m2 = new CorrelationMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        
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
        
        CorrelationMetadata metadata = new CorrelationMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1);
        CorrelationMatrix result = CorrelogramStore.getResult(metadata);
        
        // should now be retrieved from cache
        assertEquals(CorrelogramStore.getResult(metadata), result);

        // results with different NaN strategies can not be exchanged
        CorrelationMetadata metadata2 = new CorrelationMetadata(a, b, 4, -2, 2, CrossCorrelation.NA_ACTION.REPLACE_WITH_ZERO, 1);
        assertFalse(CorrelogramStore.contains(metadata2));
    }

    @Test @Ignore
    public void testContains_metadata() {
        System.out.println("contains metadata");
        
        boolean expResult = false;
        boolean result = CorrelogramStore.contains(new CorrelationMetadata(a, b, 3, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        assertEquals(expResult, result);
        
        // depends on whether the zero is interpreted as a single window, but that makes look-ups more complicated
        expResult = true;
        result = CorrelogramStore.contains(new CorrelationMetadata(a, b, 0, -2, 2, CrossCorrelation.NA_ACTION.LEAVE_UNCHANGED, 1));
        assertEquals(expResult, result);
    }

}