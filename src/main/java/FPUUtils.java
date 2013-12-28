package cpuex4;

class FPUUtils {
    public static float getFloat(int a) {
        return Float.intBitsToFloat(a);
    }

    public static int getUint32_t(float a) {

        return Float.floatToRawIntBits(a);
    }

    public static double max_double( double a, double b ) {
        return ( a < b ) ? b : a;
    }

    public static int float_with( int sign, int exp, int frac ) {

        if ( sign == 0 ) {
            int retVal = (frac & 0x007FFFFF);
            retVal += exp << 23;
            retVal += sign << 31;
            return retVal; 
        }
        String expStr = reverseString( Integer.toBinaryString( exp ) );
        expStr = repeatString( "1", 8 - expStr.length() ) + expStr;
        String fracStr = reverseString( Integer.toBinaryString( frac & 0x007FFFFF ) );
        fracStr = repeatString( "1", 23 - fracStr.length() ) + fracStr;
        return - ( Integer.parseInt( expStr + fracStr, 2 ) + 1 );  
    }

    public static String repeatString(String s, int n) {
        String t = "";
        for (int i = 0; i < n; i++) {
            t = t + s;
        }
        return t;
    }

    public static String reverseString(String s) {
        String t = "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ( c == '0' ) {
                t = t + '1';
            } else {
                t = t + '0'; 
            }
        }
        return t;
    }

    public static int[] parse_float( int a ) {
        String binary = Integer.toBinaryString( a ); 
        binary = repeatString( "0", 32 - binary.length() ) + binary;
        int intArray[] = new int[3];
        intArray[0] = Integer.parseInt( binary.substring( 0, 1 ), 2 );
        intArray[1] = Integer.parseInt( binary.substring( 1, 9 ), 2 );
        intArray[2] = Integer.parseInt( binary.substring( 9, 32 ), 2 );
        return intArray; 
    }
}
