package cpuex4;

import java.io.*;

class FPU {
    private boolean dumpEnable = false;
    private PrintWriter faddOut;
    private PrintWriter fsubOut;
    private PrintWriter fmulOut;
    private PrintWriter fdivOut;
    private PrintWriter finvOut;
    private PrintWriter fsqrtOut;
    private long faddCount = 0;
    private long fsubCount = 0;
    private long fmulCount = 0;
    private long fdivCount = 0;
    private long finvCount = 0;
    private long fsqrtCount = 0;
    private long splitCount = 0;
    private long SPLIT_SIZE = 1000000;
    private long SPLIT_LIMIT = 200;

    FPU(boolean dump) {
        dumpEnable = dump;
    }

    // TODO: These implementation should be functionally equivalent to FPU's 

    float fadd(float a, float b) throws IOException {
        float r = FaddCalculator.calc.fadd(a, b);
        FaddCalculator.validCheck(a, b);

        if (dumpEnable && splitCount <= SPLIT_LIMIT) {
            if (faddCount % SPLIT_SIZE == 0 ) {
                splitCount++;
                if (faddOut != null) {
                    faddOut.close();
                }
                if (splitCount > SPLIT_LIMIT) {
                    return r;
                }
                faddOut = new PrintWriter(String.format("fadd.%d", faddCount / SPLIT_SIZE));
            }
            faddCount++;
            faddOut.printf("0x%08x 0x%08x 0x%08x%n",
                Float.floatToRawIntBits(a), Float.floatToRawIntBits(b), Float.floatToRawIntBits(r));
        }

        return r;
    }

    float fsub(float a, float b) throws IOException {
        float r = fadd(a, -b);

        if (dumpEnable && splitCount <= SPLIT_LIMIT) {
            if (fsubCount % SPLIT_SIZE == 0 ) {
                splitCount++;
                if (fsubOut != null) {
                    fsubOut.close();
                }
                if (splitCount > SPLIT_LIMIT) {
                    return r;
                }
                fsubOut = new PrintWriter(String.format("fsub.%d", fsubCount / SPLIT_SIZE));
            }
            fsubCount++;
            fsubOut.printf("0x%08x 0x%08x 0x%08x%n",
                Float.floatToRawIntBits(a), Float.floatToRawIntBits(b), Float.floatToRawIntBits(r));
        }

        return r;
    }

    float fmul(float a, float b) throws IOException {
        float r = a * b;

        if (dumpEnable && splitCount <= SPLIT_LIMIT) {
            if (fmulCount % SPLIT_SIZE == 0 ) {
                splitCount++;
                if (fmulOut != null) {
                    fmulOut.close();
                }
                if (splitCount > SPLIT_LIMIT) {
                    return r;
                }
                fmulOut = new PrintWriter(String.format("fmul.%d", fmulCount / SPLIT_SIZE));
            }
            fmulCount++;
            fmulOut.printf("0x%08x 0x%08x 0x%08x%n",
                Float.floatToRawIntBits(a), Float.floatToRawIntBits(b), Float.floatToRawIntBits(r));
        }

        return r;
    }

    float finv(float a) throws IOException {
        float r = FinvCalculator.finv(a);

        if (dumpEnable && splitCount <= SPLIT_LIMIT) {
            if (finvCount % SPLIT_SIZE == 0 ) {
                splitCount++;
                if (finvOut != null) {
                    finvOut.close();
                }
                if (splitCount > SPLIT_LIMIT) {
                    return r;
                }
                finvOut = new PrintWriter(String.format("finv.%d", finvCount / SPLIT_SIZE));
            }
            finvCount++;
            finvOut.printf("0x%08x 0x%08x%n", Float.floatToRawIntBits(a), Float.floatToRawIntBits(r));
        }

        return r;
    }

    float fsqrt(float a) throws IOException {
        float r = FsqrtCalculator.fsqrt(a);

        if (dumpEnable && splitCount <= SPLIT_LIMIT) {
            if (fsqrtCount % SPLIT_SIZE == 0 ) {
                splitCount++;
                if (fsqrtOut != null) {
                    fsqrtOut.close();
                }
                if (splitCount > SPLIT_LIMIT) {
                    return r;
                }
                fsqrtOut = new PrintWriter(String.format("fsqrt.%d", fsqrtCount / SPLIT_SIZE));
            }
            fsqrtCount++;
            fsqrtOut.printf("0x%08x 0x%08x%n", Float.floatToRawIntBits(a), Float.floatToRawIntBits(r));
        }

        return r;
    }

    void beforeExit() {
        if (faddOut != null) faddOut.close();
        if (fmulOut != null) fmulOut.close();
        if (finvOut != null) finvOut.close();
        if (fsqrtOut != null) fsqrtOut.close();
    }
}
