package Global;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * Contains general purpose helper methods and classes.
 * Created by Carl Witt on 04.11.14.
 */
public class Util {


    /**
     * Interleaves the values of two arrays. E.g. zip([1,2,3], [a,b,c]) gives [1,a,2,b,3,c]
     * @param xValues These values will have even indices (0, 2, 4, ...)
     * @param yValues These values will have odd indices (1, 3, 5, ...)
     * @return An array of double length containing alternatingly one value of xValues and yValues
     */
    public static double[] zip(double[] xValues, double[] yValues){
        assert xValues.length == yValues.length;
        double[] result = new double[xValues.length*2];
        for (int i = 0; i < xValues.length; i++) {
            result[2*i] = xValues[i];
            result[2*i+1] = yValues[i];
        }
        return result;
    }

    /**
     * Inverse operation of zip. De-interleaves the values of an array by alternatingly storing one value in the first given array and in the second given array.
     * E.g. unzip([1,a,2,b,3,c], A, B) given A = [1,2,3], B = [a,b,c]
     * @param zipped The interleaved values.
     * @param xValues The allocated output memory for the elements with even indices.
     * @param yValues The allocated output memory for the elements with odd indices.
     */
    public static void unzip(double[] zipped, double[] xValues, double[] yValues){
        assert xValues.length == yValues.length;
        assert zipped.length == 2*xValues.length;
        for (int i = 0; i < xValues.length; i++) {
            xValues[i] = zipped[2*i];
            yValues[i] = zipped[2*i+1];
        }
    }

    /**
     * Provides a method for checking whether the elapsed time exceeds a given threshold.
     * Can be used in a loop to limit the execution time of a very expensive operation that doesn't need to finish at any cost.
     */
    public static class TimeOutChecker{
        /** Time stamp of the last reset. */
        long started;
        /** Threshold in nanoseconds. */
        long maxNanoSeconds;

        /**
         *
         * @param maxMilliSeconds The maximum number of milliseconds to elapse.
         */
        public TimeOutChecker(long maxMilliSeconds){ this.maxNanoSeconds = maxMilliSeconds * 1000000; reset(); }
        public void reset(){ started = System.nanoTime(); }
        public boolean isTimeOut(){ return System.nanoTime() - started > maxNanoSeconds; }

        /**
         * If the time out threshold has been exceeded, the user is asked whether the operation should continue.
         * @return Whether the the current process should be aborted. */
        public boolean isTimeOutUserWantsToAbort() {
            if(! isTimeOut()) return false;
            String message = String.format("The issued operation has exceeded its maximum computing time of %s seconds. Would you like it to continue for another %s seconds?", maxNanoSeconds / 1000000000, maxNanoSeconds / 1000000000);
            Alert proceedRequest = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.NO, ButtonType.YES);
            ButtonType userAnswer = proceedRequest.showAndWait().orElse(ButtonType.NO);
            if(userAnswer == ButtonType.YES){
                reset();
                return false;
            }
            return true;
        }
    }

    /**
     * @return the bounding box of two axis parallel bounding boxes.
     */
    public static Bounds union(Bounds a, Bounds b){
        double minX = Math.min(a.getMinX(), b.getMinX());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    public static boolean distanceSmallerThan(double a, double b, double threshold){
        return Math.abs(a-b) < threshold;
    }
}
