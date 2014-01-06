package cpuex4;

import java.io.*;
import java.util.Random;
import cpuex4.FPUUtils.*;

public class FaddCalculator {
    static public FaddCalculator calc = new FaddCalculator(false);
    static public FaddCalculator calc_debug = new FaddCalculator(true);

    private boolean debug;

    FaddCalculator (boolean debug_flag) {
        debug = debug_flag;
    }

    public float fadd(float a, float b) {
        return FPUUtils.getFloat(
                fadd_uint32_t(FPUUtils.getUint32_t(a), FPUUtils.getUint32_t(b)));
    }

    public int fadd_uint32_t(int a, int b) {
        // check input values exception?
        if ( isZero(a) || isNaN(a) || isInf(a) ||
                isZero(b) || isNaN(b) || isInf(b)) {
            boolean isntPosInf = !(isPosInf(a) || isPosInf(b));
            boolean isntNegInf = !(isNegInf(a) || isNegInf(b));
            int posInf = setSign(setExp(0, 0xff), 0);
            int negInf = setSign(setExp(0, 0xff), 1);
            int nanValue = setSign(setExp(setFrac(0, 1), 0xff), 0);

            if (isZero(a) & isZero(b)) {
                return 0;
            } else if (isZero(a)) {
                return b;
            } else if (isZero(b)) {
                return a;
            } else if (isNaN(a)) {
                return a;
            } else if (isNaN(b)) {
                return b;
            } else if (isntPosInf) {
                return negInf;
            } else if (isntNegInf){
                return posInf;
            } else {
                return nanValue;
            }
        }

        // swap to be abs(a) > abs(b)
        if (getAbs(a) < getAbs(b)) {
            int tmp = a; a = b; b = tmp;
        }

        if (debug) System.err.printf("a: %08x, b: %08x\n", a, b);

        // convert fraction sum format
        // [1, fraction(23bit), g]
        int frac_add_a = (getExp(a) != 0) ? (setExp(getAbs(a), 1) << 1) : 0;
        int frac_format_b = (getExp(b) != 0) ? (setExp(getAbs(b), 1) << 1) : 0;

        // shift right b by amount of difference of exponents
        int exp_diff = getExp(a) - getExp(b);

        int frac_add_b = 0; int round = 0; int spare_guard = 0; int spare_round = 0;

        if (exp_diff == 0) {
            frac_add_b = frac_format_b;
            round = 0;
        } else if (exp_diff > 26) {
            frac_add_b = 0;
            round = 1;
        } else {
            frac_add_b = frac_format_b >> exp_diff;
            round = getRound(b, exp_diff - 1);
            spare_guard = getBit(setExp(getAbs(b), 1) >> (exp_diff - 2), 0);
            spare_round = getRound(b, exp_diff - 2);
        }

        if (debug) System.err.printf("exp_diff: %d - %d = %d\n",
                getExp(a), getExp(b), exp_diff);
        if (debug) System.err.printf("round: %d, spare_guard: %d, spare_round: %d\n",
                round, spare_guard, spare_round);

        // add fractions
        // TODO: fraction format should contain not only g bit but also r, s bits.
        //       such as [1, fraction(23 bit), g, r, s]
        //       ([1, fraction(23bit), g] at now)
        int frac_sum;
        if (getSign(a) == getSign(b)) {
            frac_sum = frac_add_a + frac_add_b;
        } else {
            frac_sum = frac_add_a - frac_add_b;
        }

        if (debug) System.err.printf("frac_sum: %08x %c %08x = %08x\n",
                frac_add_a, (getSign(a) == getSign(b) ? '+' : '-'),
                frac_add_b, frac_sum);

        if (frac_sum == 0) return 0;

        // find msb
        int msb = findMSB(frac_sum);
        int frac_shift_error = msb - 24;

        if (debug) System.err.printf("msb: %d, frac_shift_error: %d\n", msb, frac_shift_error);

        // exp and sign
        int result = 0;
        if (getExp(a) + frac_shift_error <= 0) {
            result = setExp(result, 0);
        } else {
            result = setExp(result, getExp(a) + frac_shift_error);
        }
        result = setSign(result, getSign(a));

        // remove g bit and round fraction
        final int remove_g_bit_shamt = 1;
        if (frac_shift_error < 0) {
            // round fraction
            if (getSign(a) != getSign(b) && frac_shift_error == -1) {
                frac_sum -= filterRound(frac_sum, 0, spare_round) & spare_guard;
            }

            if (debug) System.err.printf("frac_sum: %08x, result: %08x\n", frac_sum, result);

            result = setFrac(result, getFrac(frac_sum << Math.abs(frac_shift_error + remove_g_bit_shamt)));
        } else { 
            // round fraction
            // TODO: simplify
            if (frac_shift_error == 1) {
                frac_sum += 4 *
                    (getBit(frac_sum, 1) & (getBit(frac_sum, 0) | filterRound(frac_sum, 2, round)));
            } else if (frac_shift_error == 0) {
                if (getSign(a) == getSign(b)) {
                    frac_sum += 2 *
                        (getBit(frac_sum, 0) & filterRound(frac_sum, 1, round));
                } else if (frac_sum == 0x01000000 &&
                        (filterRound(frac_sum, 0, spare_round) & spare_guard) == 0x1) {
                    frac_sum--;
                    round = 0;
                    result = setExp(result, getExp(result) - 1);
                } else {
                    frac_sum += 2 * (getBit(frac_sum, 0) & getBit(frac_sum, 1) & ~round);
                }
            }

            if (debug) System.err.printf("frac_sum: %08x, result: %08x\n", frac_sum, result);

            // round up exponents
            if ((frac_sum >> (frac_shift_error + remove_g_bit_shamt)) >= 0x1000000) {
                result = setExp(result, getExp(result) + 1);
                result = setFrac(result, 0);
            } else {
                result = setFrac(result, getFrac(frac_sum >> (frac_shift_error + remove_g_bit_shamt)));
            }
        }

        // infinity
        if (getExp(result) == 0xff) return setFrac(result, 0);

        if (debug) System.err.printf("result: %08x\n", result);
        if (debug) System.err.println("-------------");

        return result;
    }

    private boolean isZero(int a) {
        return getExp(a) == 0;
    }

    private boolean isInf(int a) {
        return getExp(a) == 0xff && getFrac(a) == 0;
    }

    private boolean isPosInf(int a) {
        return getSign(a) == 0 && isInf(a);
    }

    private boolean isNegInf(int a) {
        return getSign(a) == 1 && isInf(a);
    }

    private boolean isNaN(int a) {
        return getExp(a) == 0xff && getFrac(a) != 0;
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

    private int getRange(int a, int max) {
        int filter = max < 0 ? 0 : (0xFFFFFFFF >>> (31 - max));
        return a & filter;
    }

    private int getRange(int a, int max, int min) {
        int filter = min < 0 ? ~0 : ~(0xFFFFFFFF >>> (31 - min));
        return getRange(a, max) & filter;
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
    private int getBit(int a, int pos) {
        return (a & (0x1 << pos)) >> pos;
    }

    private int findMSB(int a) {
        for (int i = 26;  i >= 0; i--) {
            if (getBit(a, i) != 0) {
                return i;
            }
        }
        return 0;
    }

    private int getRound(int a, int shamt) {
        if (debug) System.err.printf("getRound(%08x, %d): %06x, %b\n",
                a, shamt,
                getRange(a, shamt - 1),
                getRange(a, shamt - 1) != 0);
        return getRange(a, shamt - 1) != 0 ? 1 : 0;
    }

    private int filterRound(int a, int shamt, int round) {
        return (getBit(a >> shamt, 0) | round) & 0x1;
    }

    public static boolean validCheck(float a, float b) {
        float my_result = calc.fadd(a, b);
        float result = a + b;
        if (my_result != result) {
            calc_debug.fadd(a, b);
            System.err.printf("error: %e + %e = %e, but %e\n",
                    a, b, result, my_result);
            System.err.printf("error: %08x + %08x = %08x, but %08x\n",
                    FPUUtils.getUint32_t(a), FPUUtils.getUint32_t(b),
                    FPUUtils.getUint32_t(result), FPUUtils.getUint32_t(my_result));
        }
        return my_result == result;
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
