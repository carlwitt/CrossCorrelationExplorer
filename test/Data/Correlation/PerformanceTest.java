/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Data.Correlation;

import Data.Correlation.DFT;
import Data.TimeSeries;
import java.util.Random;
import org.junit.Test;
import org.junit.Ignore;

/**
 *
 * @author Carl Witt
 */

@Ignore
public class PerformanceTest extends DFTTest {
    
    
    @Test
    public void testRuntimePrediction(){
        System.out.println("runtimePrediction");
        
        for (int exp = 10; exp <= 20; exp++) {
            int inputSize = (int) Math.pow(2, exp);
            float expected = DFT.computationTime.predict(inputSize);
            System.out.println("expected runtime for input size "+inputSize+" = "+expected + "s");
        }
        System.out.println(DFT.computationTime.testRuns);
        
    }
    
    @Test
    @Ignore
    /** Tests the speed difference between pure brute force and pure DFT-based correlation computation. */
    public void testPerformance(){
        System.out.println("test performance of brute force and fourier cross correlation");
        
        long bruteTotal = 0, fourierTotal = 1;
        int inputLength = 1;
        while(inputLength < 20000){
            
            // generate random test sequences
            double[][] realValues = new double[2][inputLength];
            Random rg = new Random();
            for (int i = 0; i < inputLength; i++) {
                realValues[0][i] = rg.nextDouble()*rg.nextInt();
                realValues[1][i] = rg.nextDouble()*rg.nextInt();
            }
            TimeSeries f = new TimeSeries(realValues[0]);
            TimeSeries g = new TimeSeries(realValues[1]);
            
            // switching computation order of brute and fourier doesn't affect running time.
            fourierTotal = System.currentTimeMillis();
            for (int i = 0; i < 500; i++) {
                //                CorrelogramStore.getResult(new CorrelogramMetadata(f, g, inputLength));
                DFT.crossCorrelation(f, g, inputLength);
            }
            fourierTotal = System.currentTimeMillis() - fourierTotal;
            
            bruteTotal = System.currentTimeMillis();
            for (int i = 0; i < 500; i++) {
//                DFT.bruteForceCrossCorrelation(f, g);
            }
            bruteTotal = System.currentTimeMillis() - bruteTotal;
            
            // write result in copy/pasteable format to console
            System.out.println(String.format("%d\t%s\t%s",inputLength,fourierTotal,bruteTotal));
            
            inputLength*=2;
        }
    }
    
    @Test
    @Ignore
    /** Tests the speed difference between combined brute force with DFT-based correlation computation at different switch thresholds. */
    public void testSwitchPerformance(){
        System.out.println("test performance at different switch levels between brute force and fourier cross correlation");
        
        int[][] results = new int[90][9];
        int inputLength = 9000;
        for (int switchLevel = 0; switchLevel <= 800; switchLevel+=100) {
            
            // generate random test sequences
            double[][] realValues = new double[2][inputLength];
            Random rg = new Random();
            for (int i = 0; i < inputLength; i++) {
                realValues[0][i] = rg.nextDouble()*rg.nextInt();
                realValues[1][i] = rg.nextDouble()*rg.nextInt();
            }
            TimeSeries f = new TimeSeries(realValues[0]);
            TimeSeries g = new TimeSeries(realValues[1]);
            
            DFT.CC_BRUTEFORCE_LENGTH = switchLevel;
            
            // switching computation order of brute and fourier doesn't affect running time.
            for (int windowSize = 1; windowSize < 9000; windowSize+=100) {
                //                CorrelogramStore.getResult(new CorrelogramMetadata(f, g, inputLength));
                long fourier = System.currentTimeMillis();
                for (int j = 0; j < 20; j++) {
                    DFT.crossCorrelation(f, g, windowSize);
                }
                fourier = System.currentTimeMillis() - fourier;
                results[windowSize/100][switchLevel/100] = (int)fourier;
            }
            
        }
        
        System.out.print("windowSize\t");
        for (int switchLevel = 0; switchLevel < 10; switchLevel++) {
            System.out.print("switch at "+(switchLevel*100)+"\t");
        }
        
        // write result in copy/pasteable format to console
        for (int windowSize = 0; windowSize < 90; windowSize++) {
            System.out.print(windowSize*100+"\t");
            for (int switchLevel = 0; switchLevel < 9; switchLevel++) {
                System.out.print(results[windowSize][switchLevel]+"\t");
            }
            System.out.println("");
        }
        
    }
    
    
}