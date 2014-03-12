/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import Data.ComplexSequence;
import Data.TimeSeries;
import Data.DataModel;
import Data.Correlation.CorrelogramStore;
import Data.Correlation.CorrelationMatrix;
import Data.Correlation.CorrelogramMetadata;
import Data.Correlation.CorrelogramMetadata;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Iterator;
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
        
        dataModel.add(a);
        dataModel.add(b);
        
        List<CorrelationMatrix.Column> matrix1 = Lists.newArrayList(
                new CorrelationMatrix.Column(new double[]{1.,4.,7.}, new double[]{11.,44.,77.}),
                new CorrelationMatrix.Column(new double[]{2.,5.,8.}, new double[]{22.,55.,88.}),
                new CorrelationMatrix.Column(new double[]{3.,6.,9.}, new double[]{33.,66.,99.})
        );
        c1 = new CorrelationMatrix(new Data.Correlation.CorrelogramMetadata(a, b, 0));
        c1.append(matrix1.get(0));
        c1.append(matrix1.get(1));
        c1.append(matrix1.get(2));
        
        c2 = new CorrelationMatrix(new Data.Correlation.CorrelogramMetadata(a, b, 8));
        c2.append(new CorrelationMatrix.Column(new double[]{1.,0.,0.}, new double[]{111.,444.,777.}));
        c2.append(new CorrelationMatrix.Column(new double[]{0.,1.,0.}, new double[]{222.,555.,888.}));
        c2.append(new CorrelationMatrix.Column(new double[]{0.,0.,1.}, new double[]{333.,666.,999.}));
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

    /**
     * Test of getResult method, of class CorrelogramStore.
     */
    @Test
    public void testMetadataEquality() {
        System.out.println("metadataEquality");
        
        CorrelogramMetadata m1 = new CorrelogramMetadata(a, b, 4);
        CorrelogramMetadata m2 = new CorrelogramMetadata(b, a, 4);
        
        // the pair of IDs is unordered, so the metadata should be considered equal
        assertEquals(m1, m2);
    }
    
    /**
     * Test the cache function of the correlogram store.
     */
    @Test
    public void testGetResult_Metadata() {
        System.out.println("getResult");
        
        
        
        CorrelogramMetadata metadata = new CorrelogramMetadata(a, b, 4);
//        CorrelationMatrix expResult = c2;
        CorrelationMatrix result = CorrelogramStore.getResult(metadata);
        
        // should now be retrieved from cache
        assertEquals(CorrelogramStore.getResult(metadata), result);
    }

    @Test
    public void testContains_metadata() {
        System.out.println("contains metadata");
        
        boolean expResult = false;
        boolean result = CorrelogramStore.contains(new Data.Correlation.CorrelogramMetadata(a, b, 3));
        assertEquals(expResult, result);
        
        expResult = true;
        result = CorrelogramStore.contains(new Data.Correlation.CorrelogramMetadata(a, b, 0));
        assertEquals(expResult, result);
    }

    /**
     * Test of getAllResults method, of class CorrelogramStore.
     */
    @Test
    @Ignore
    public void testGetAllResults() {
        System.out.println("getAllResults");
        CorrelogramStore instance = new CorrelogramStore(dataModel);
        Collection expResult = null;
        Collection result = instance.getAllResults();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}