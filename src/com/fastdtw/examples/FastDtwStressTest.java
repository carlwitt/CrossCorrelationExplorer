/*
* FastDtwTest.java   Jul 14, 2004
*
* Copyright (c) 2004 Stan Salvador
* stansalvador@hotmail.com
*/

package com.fastdtw.examples;

import Data.Experiment;
import com.fastdtw.EnsembleDelayHistogram;

import java.io.IOException;


/**
 * This class contains a main method that executes the FastDTW algorithm on two
 * time series with a specified radius.
 *
 * @author Stan Salvador, stansalvador@hotmail.com
 * @since Jul 14, 2004
 */
public class FastDtwStressTest {
    /**
     * This main method executes the FastDTW algorithm on two time series with a
     * specified radius. The time series arguments are file names for files that
     * contain one measurement per line (time measurements are an optional value
     * in the first column). After calculating the warp path, the warp
     * path distance will be printed to standard output, followed by the path
     * in the format "(0,0),(1,0),(2,1)..." were each pair of numbers in
     * parenthesis are indexes of the first and second time series that are
     * linked in the warp path
     *
     * @param args command line arguments (see method comments)
     */
    public static void main(String[] args) throws IOException {

        int ensembleID = 1;

        Experiment experiment = new Experiment("data/sop/sop.nc");

        EnsembleDelayHistogram edh = new EnsembleDelayHistogram(experiment.dataModel, ensembleID);


    }


}  // end class FastDtwTest
