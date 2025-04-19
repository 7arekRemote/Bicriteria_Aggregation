package utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ArrayMath {

    
    public static void increaseArray(double[] array, double[] increment) {
        increaseArray(array, increment, 1);
    }

    
    public static void increaseArray(double[] array, double[] increment, double scalar) {
        
        if(increment == null)
            return;

        if (array.length != increment.length)
            throw new IllegalArgumentException("Arrays must have the same length.");

        for (int i = 0; i < array.length; i++)
            array[i] += increment[i]* scalar;
    }

    public static void multiplyArray(double[] array, double scalar) {
        for (int i = 0; i < array.length; i++)
            array[i] *= scalar;
    }

    
    public static boolean isLess(double[] aArray, double[] bArray) {
        if (aArray.length != bArray.length)
            throw new IllegalArgumentException("Arrays must have the same length.");

        if (aArray.length != 2)
            throw new RuntimeException("At the moment, compare is only implemented for the bicriteria case.");

        return (aArray[0] < bArray[0] || aArray[0] == bArray[0] && aArray[1] < bArray[1]);
    }
    public static void round(double[] array, int places) {
        if (places < 0) throw new IllegalArgumentException();

        for (int i = 0; i < array.length;i++) {
            BigDecimal bd = BigDecimal.valueOf(array[i]);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            array[i] = bd.doubleValue();
        }
    }

}
