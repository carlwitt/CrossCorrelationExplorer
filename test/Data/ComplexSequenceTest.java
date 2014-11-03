/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Carl Witt
 */
public class ComplexSequenceTest {

    final ComplexSequence decRe = ComplexSequence.create(new double[]{6,5,4,3,2,1}, new double[6]);
    final ComplexSequence incIm = ComplexSequence.create(new double[6], new double[]{-1,0,1,2,3,4});
    final ComplexSequence single = ComplexSequence.create(new double[]{-11}, new double[]{11});
        
    /**
     * Test of roundPrecision method, of class ComplexSequence.
     */
    @Test
    public void testRoundPrecision() {
        System.out.println("roundPrecision");
        int decimals = 0;
        ComplexSequence instance = ComplexSequence.create(
                new double[]{1.11,2.22,3.33,4.44,5.55,6.66},
                new double[]{1.11,2.22,3.33,4.44,5.55,6.66});
        ComplexSequence expResult1 = ComplexSequence.create(
                new double[]{1.1,2.2,3.3,4.4,5.6,6.7},
                new double[]{1.1,2.2,3.3,4.4,5.6,6.7});
        ComplexSequence expResult0 = ComplexSequence.create(
                new double[]{1,2,3,4,6,7},
                new double[]{1,2,3,4,6,7});
        
        // assert cutting to current precision doesn't change the value
        ComplexSequence result = ComplexSequence.create(instance).roundPrecision(2);
        assertEquals(result, instance);
        
        // cutting to 1 decimal
        result = ComplexSequence.create(instance).roundPrecision(1);
        assertEquals(result, expResult1);
        // cutting twice to the same precision doesn't change the value
        result.roundPrecision(1);
        assertEquals(result, expResult1);
        
        // cutting to 0 decimals (rounding)
        result = ComplexSequence.create(instance).roundPrecision(0);
        assertEquals(result, expResult0);
    }
    
    @Test
    public void testPointWiseProduct() {
        System.out.println("pointWiseProduct");
        ComplexSequence instance  = ComplexSequence.create(new double[]{6,5,4,3,2,1}, new double[6]);
        ComplexSequence other     = ComplexSequence.create(new double[]{0,1,2,3,4,5}, new double[6]);
        ComplexSequence expResult = ComplexSequence.create(new double[]{0,5,8,9,8,5}, new double[6]);
        ComplexSequence result = instance.pointWiseProduct(other);
        assertEquals(expResult, result);
    }
        
//    @Test
//    @Ignore
//    public void testCreate_doubleArr() {
//        System.out.println("create");
//        double[] values = null;
//        ComplexSequence expResult = null;
//        ComplexSequence result = ComplexSequence.create(values);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    @Test
//    @Ignore
//    public void testCreate_doubleArr_doubleArr() {
//        System.out.println("create");
//        double[] realValues = null;
//        double[] imaginaryValues = null;
//        ComplexSequence expResult = null;
//        ComplexSequence result = ComplexSequence.create(realValues, imaginaryValues);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//
    /**
     * Test of getMin method, of class ComplexSequence.
     */
    @Test
    public void testGetMin() {
        System.out.println("getMin");
        double expResultRe = 1.0;
        double expResultIm = 0.0;
        assertEquals(expResultRe, decRe.getMin(ComplexSequence.Part.REAL), 0.0);
        assertEquals(expResultIm, decRe.getMin(ComplexSequence.Part.IMAGINARY), 0.0);
        
        expResultRe = 0.0;
        expResultIm = -1.0;
        assertEquals(expResultRe, incIm.getMin(ComplexSequence.Part.REAL), 0.0);
        assertEquals(expResultIm, incIm.getMin(ComplexSequence.Part.IMAGINARY), 0.0);
        
        expResultRe = -11.0;
        expResultIm =  11.0;
        assertEquals(expResultRe, single.getMin(ComplexSequence.Part.REAL), 0.0);
        assertEquals(expResultIm, single.getMin(ComplexSequence.Part.IMAGINARY), 0.0);
        
        ComplexSequence c = ComplexSequence.create(new double[]{4,1}, new double[]{5,2});
        assertEquals(1, c.getMin(ComplexSequence.Part.REAL), ComplexSequence.EQ_COMPARISON_THRESHOLD);
        assertEquals(4, c.getMax(ComplexSequence.Part.REAL), ComplexSequence.EQ_COMPARISON_THRESHOLD);
        assertEquals(2, c.getMin(ComplexSequence.Part.IMAGINARY), ComplexSequence.EQ_COMPARISON_THRESHOLD);
        assertEquals(5, c.getMax(ComplexSequence.Part.IMAGINARY), ComplexSequence.EQ_COMPARISON_THRESHOLD);
        
    }

    /**
     * Test of getMax method, of class ComplexSequence.
     */
    @Test
    public void testGetMax() {
        System.out.println("getMax");
        double expResultRe = 6.0;
        double expResultIm = 0.0;
        assertEquals(expResultRe, decRe.getMax(ComplexSequence.Part.REAL), 0.0);
        assertEquals(expResultIm, decRe.getMax(ComplexSequence.Part.IMAGINARY), 0.0);
        
        expResultRe = 0.0;
        expResultIm = 4.0;
        assertEquals(expResultRe, incIm.getMax(ComplexSequence.Part.REAL), 0.0);
        assertEquals(expResultIm, incIm.getMax(ComplexSequence.Part.IMAGINARY), 0.0);
        
        expResultRe = -11.0;
        expResultIm =  11.0;
        assertEquals(expResultRe, single.getMax(ComplexSequence.Part.REAL), 0.0);
        assertEquals(expResultIm, single.getMax(ComplexSequence.Part.IMAGINARY), 0.0);
    }
//
//    /**
//     * Test of size method, of class ComplexSequence.
//     */
//    @Test
//    public void testSize() {
//        System.out.println("size");
//        ComplexSequence instance = null;
//        int expResult = 0;
//        int result = instance.size();
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of isEmpty method, of class ComplexSequence.
//     */
//    @Test
//    public void testIsEmpty() {
//        System.out.println("isEmpty");
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.isEmpty();
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of get method, of class ComplexSequence.
//     */
//    @Test
//    public void testGet() {
//        System.out.println("get");
//        int i = 0;
//        ComplexSequence instance = null;
//        Double expResult = null;
//        Double result = instance.get(i);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of toString method, of class ComplexSequence.
//     */
//    @Test
//    public void testToString() {
//        System.out.println("toString");
//        ComplexSequence instance = null;
//        String expResult = "";
//        String result = instance.toString();
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of contains method, of class ComplexSequence.
//     */
//    @Test
//    public void testContains() {
//        System.out.println("contains");
//        Object o = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.contains(o);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of iterator method, of class ComplexSequence.
//     */
//    @Test
//    public void testIterator() {
//        System.out.println("iterator");
//        ComplexSequence instance = null;
//        Iterator expResult = null;
//        Iterator result = instance.iterator();
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of toArray method, of class ComplexSequence.
//     */
//    @Test
//    public void testToArray_0args() {
//        System.out.println("toArray");
//        ComplexSequence instance = null;
//        Object[] expResult = null;
//        Object[] result = instance.toArray();
//        assertArrayEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//
//    /**
//     * Test of add method, of class ComplexSequence.
//     */
//    @Test
//    public void testAdd_Double() {
//        System.out.println("add");
//        Double e = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.add(e);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of remove method, of class ComplexSequence.
//     */
//    @Test
//    public void testRemove_Object() {
//        System.out.println("remove");
//        Object o = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.remove(o);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of containsAll method, of class ComplexSequence.
//     */
//    @Test
//    public void testContainsAll() {
//        System.out.println("containsAll");
//        Collection<?> clctn = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.containsAll(clctn);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of addAll method, of class ComplexSequence.
//     */
//    @Test
//    public void testAddAll_Collection() {
//        System.out.println("addAll");
//        Collection<? extends Double> clctn = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.addAll(clctn);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of addAll method, of class ComplexSequence.
//     */
//    @Test
//    public void testAddAll_int_Collection() {
//        System.out.println("addAll");
//        int i = 0;
//        Collection<? extends Double> clctn = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.addAll(i, clctn);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of removeAll method, of class ComplexSequence.
//     */
//    @Test
//    public void testRemoveAll() {
//        System.out.println("removeAll");
//        Collection<?> clctn = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.removeAll(clctn);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of retainAll method, of class ComplexSequence.
//     */
//    @Test
//    public void testRetainAll() {
//        System.out.println("retainAll");
//        Collection<?> clctn = null;
//        ComplexSequence instance = null;
//        boolean expResult = false;
//        boolean result = instance.retainAll(clctn);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of clear method, of class ComplexSequence.
//     */
//    @Test
//    public void testClear() {
//        System.out.println("clear");
//        ComplexSequence instance = null;
//        instance.clear();
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of set method, of class ComplexSequence.
//     */
//    @Test
//    public void testSet() {
//        System.out.println("set");
//        int i = 0;
//        Double e = null;
//        ComplexSequence instance = null;
//        Double expResult = null;
//        Double result = instance.set(i, e);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of add method, of class ComplexSequence.
//     */
//    @Test
//    public void testAdd_int_Double() {
//        System.out.println("add");
//        int i = 0;
//        Double e = null;
//        ComplexSequence instance = null;
//        instance.add(i, e);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of remove method, of class ComplexSequence.
//     */
//    @Test
//    public void testRemove_int() {
//        System.out.println("remove");
//        int i = 0;
//        ComplexSequence instance = null;
//        Double expResult = null;
//        Double result = instance.remove(i);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of indexOf method, of class ComplexSequence.
//     */
//    @Test
//    public void testIndexOf() {
//        System.out.println("indexOf");
//        Object o = null;
//        ComplexSequence instance = null;
//        int expResult = 0;
//        int result = instance.indexOf(o);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of lastIndexOf method, of class ComplexSequence.
//     */
//    @Test
//    public void testLastIndexOf() {
//        System.out.println("lastIndexOf");
//        Object o = null;
//        ComplexSequence instance = null;
//        int expResult = 0;
//        int result = instance.lastIndexOf(o);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of listIterator method, of class ComplexSequence.
//     */
//    @Test
//    public void testListIterator_0args() {
//        System.out.println("listIterator");
//        ComplexSequence instance = null;
//        ListIterator expResult = null;
//        ListIterator result = instance.listIterator();
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of listIterator method, of class ComplexSequence.
//     */
//    @Test
//    public void testListIterator_int() {
//        System.out.println("listIterator");
//        int i = 0;
//        ComplexSequence instance = null;
//        ListIterator expResult = null;
//        ListIterator result = instance.listIterator(i);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of subList method, of class ComplexSequence.
//     */
//    @Test
//    public void testSubList() {
//        System.out.println("subList");
//        int i = 0;
//        int i1 = 0;
//        ComplexSequence instance = null;
//        List expResult = null;
//        List result = instance.subList(i, i1);
//        assertEquals(expResult, result);
//        
//        fail("The test case is a prototype.");
//    }

}