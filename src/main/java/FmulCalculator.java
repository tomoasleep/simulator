package cpuex4;

import java.io.*;
import java.util.Random;
import cpuex4.FPUUtils.*;

public class FmulCalculator {
    static public FmulCalculator calc = new FmulCalculator(false);
    static public FmulCalculator calc_debug = new FmulCalculator(true);
    static final int int_filter = 0xFFFFFFFF;
    static final long double_filter = ((long)int_filter >> 32) | (long)int_filter;

    private boolean debug;

    FmulCalculator (boolean debug_flag) {
        debug = debug_flag;
    }

    public float fmul(float a, float b) {
        return FPUUtils.getFloat(
                fmul_uint32_t(FPUUtils.getUint32_t(a), FPUUtils.getUint32_t(b)));
    }

    public int fmul_uint32_t(int a, int b) {
        int sign_mul = getSign(a) ^ getSign(b);

        // exception
        if (isNaN(a)) {
            return a;
        } else if (isNaN(b)) {
            return b;
        } else if (isZero(a) || isZero(b)) {
            return 0;
        } else if (isInf(a) || isInf(b)) {
            return buildUint(sign_mul, 0xff, 0);
        }

        int exponents_sum = getExp(a) + getExp(b) - 127;
        long fraction_mul = (long)getFullFrac(a) * (long)getFullFrac(b);

        if (debug) System.err.printf("a: %08x, b: %08x\n", a, b);
        if (debug) System.err.printf("a_exp: %d, a_frac: %08x\n", getExp(a), getFrac(a));
        if (debug) System.err.printf("b_exp: %d, b_frac: %08x\n", getExp(b), getFrac(b));

        if (debug) System.err.printf("exp: %08x(%d), frac: %012x\n", exponents_sum, exponents_sum, fraction_mul);


        // calc guard bits
        final int fraction_mul_top = 47;
        final int shifted_fraction_length = 27;
        int exponent_round_up = 0; int msb;

        // take bits (msb downto (msb - 25))
        // [1, fraction(23bits), guard_bit, round_bit(logical sum of left bits)] 
        if (getBit(fraction_mul, fraction_mul_top) != 0) {
            msb = fraction_mul_top;
            exponent_round_up++;
        } else {
            msb = fraction_mul_top - 1;
        }

        int s_bit = (getRange(fraction_mul, msb - shifted_fraction_length + 1) != 0) ? 1 : 0;
        long shifted_fraction = getRange(fraction_mul, msb, msb - shifted_fraction_length + 1)
            | s_bit;

        // exponent++ only this case by rounding fraction
        if (getRange(shifted_fraction, shifted_fraction_length - 2, 2) == 0xFFFFFF) {
            exponent_round_up++;
        }

        if (debug) System.err.printf("msb: %d, sbit: %d, shifted_frac: %08x\n", msb, s_bit, shifted_fraction);

        // rounding fraction
        int round_up = getBit(shifted_fraction, 2) &
            (getBit(shifted_fraction, 3) |
             getBit(shifted_fraction, 1) | getBit(shifted_fraction, 0));

        int rounded_fraction = (int)shifted_fraction + (round_up << 2);
        int rounded_exponent = exponents_sum + exponent_round_up;

        if (debug) System.err.printf("round_up: %d, rounded_exp: %d, rounded_frac: %08x\n",
                round_up, rounded_exponent, rounded_fraction);

        if (exponents_sum < 0) {
            return buildUint(sign_mul, 0, 0);
        } else if (exponents_sum >= 255) {
            return buildUint(sign_mul, 0xff, 0);
        }

        int result_fraction = (int)getRange((long)rounded_fraction, shifted_fraction_length - 2, 3);
        int result_exponent = rounded_exponent;

        if ((getBit(rounded_exponent, 8) == 1) || (getRange(rounded_exponent, 7, 0) == 0xff)) {
            return buildUint(sign_mul, 0xff, 0);
        } else if (getRange(rounded_exponent, 7, 0) == 0x00) {
            return buildUint(sign_mul, 0, 0);
        }

        if (debug) System.err.printf("result_exp: %d, result_frac: %08x\n",
                result_exponent, result_fraction);

        return buildUint(sign_mul, result_exponent, result_fraction);
    }

    private int buildUint(int sign, int exp, int frac) {
        return (sign << 31) | ((exp & 0xFF) << 23) | (frac & 0x7FFFFF);
    }

    private int getSign(int a) {
        if (false) System.err.printf("getSign(%08x: %d): %b\n", a, a, a < 0);
        return (a < 0) ? 1 : 0;
    }

    private int getExp(int a) {
        return (a & 0x7F800000) >> 23;
    }

    private int getFrac(int a) {
        return (a & 0x007FFFFF);
    }

//    private int getRange(int a, int max) {
//        return (int)getRange((long)a, max);
//    }
//
//    private int getRange(int a, int max, int min) {
//        return (int)getRange((long)a, max, min);
//    }
    private boolean isZero(int a) {
        return getExp(a) == 0;
    }

    private boolean isInf(int a) {
        return getExp(a) == 0xff && getFrac(a) == 0;
    }

    private boolean isNaN(int a) {
        return getExp(a) == 0xff && getFrac(a) != 0;
    }

    private long getRange(long a, int max) {
        long filter = (max < 0) ? 0 : (double_filter >>> (63 - max));
        long res = a & filter;
        if (debug) System.err.printf("getRange(%08x, %d): %x\n", a, max, res);
        return res;
    }

    private long getRange(long a, int max, int min) {
        long res = getRange(a >>> min, max - min);
        if (debug) System.err.printf("getRange(%08x, %d, %d): %x\n", a, max, min, res);
        return res;
    }

    private int getAbs(int a) {
        return (a & 0x7FFFFFFF);
    }

    private int setSign(int a, int sign) {
        return (a & 0x7FFFFFFF) | ((sign & 0x1) << 31);
    }

    private int setExp(int a, int exp) {
        return (a & 0x807FFFFF) | ((exp & 0xFF) << 23);
    }

    private int setFrac(int a, int frac) {
        return (a & 0xff800000) | (frac & 0x7FFFFF);
    }

    // getBit(a, pos) means a(pos downto pos).
    // Example:
    //   getBit(0x10, 4)
    //     => 0x1
    private int getBit(long a, int pos) {
        return (int)(a >>> pos) & 0x1;
    }

    // fraction with hide '1' bit
    private int getFullFrac(int a) {
        if (debug) System.err.printf("getFullFrac(%x: %x): %x\n",
                a, getFrac(a), (getFrac(a) | (0x1 << 23)));
        return (getFrac(a) | (0x1 << 23));
    }

    public static boolean validCheck(float a, float b) {
        float my_result = calc.fmul(a, b);
        float result = a * b;
        if (my_result != result) {
            calc_debug.fmul(a, b);
            System.err.printf("error: %e * %e = %e, but %e\n",
                    a, b, result, my_result);
            System.err.printf("error: %08x + %08x = %08x, but %08x\n",
                    FPUUtils.getUint32_t(a), FPUUtils.getUint32_t(b),
                    FPUUtils.getUint32_t(result), FPUUtils.getUint32_t(my_result));
        }
        return my_result == result;
    }

    public static void tester(String args[]) {
        tester(100000);
    }

    public static void tester(int test_times) {
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        try {
            for ( int i = 0; i < test_times; i++ ) {
                // String buf = br.readLine();
                // float a = Float.parseFloat(buf);
                // buf = br.readLine();
                // float b = Float.parseFloat(buf);
                float a = random_float();
                float b = random_float();
                if ( !validCheck(a, b) ) {
                    System.err.println("failed.");
                    return; 
                }
            }
            System.err.println("successed.");
        } catch (Exception e) {
        }
    }

    private static float random_float() {
        Random r = new Random(); 
        int k = r.nextInt(50);
        double d = r.nextDouble();  
        if (r.nextBoolean()) {
            return (float)(k * d);
        } else {
            return - (float)(k * d);
        }
    }
}
