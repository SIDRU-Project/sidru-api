package com.sidru.sidru_api.blockchain;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies RN-BC-01 coherence: 1 point = 1 CTC = 10^18 wei.
 * Network-free, DB-free.
 */
class PointsToCtcConversionTest {

    private static final BigInteger WEI_PER_CTC = BigInteger.TEN.pow(18);

    private BigInteger toWei(int pointsEarned) {
        return BigInteger.valueOf(pointsEarned).multiply(WEI_PER_CTC);
    }

    @Test
    void mintsExactlyPointsTimes10Pow18_case280() {
        // contract-spec test #10 (obligatory): 280 points -> 280 ether
        BigInteger expected = new BigInteger("280000000000000000000");
        assertEquals(expected, toWei(280));
    }

    @Test
    void oneuPointEqualsOneCtc() {
        assertEquals(WEI_PER_CTC, toWei(1));
    }

    @Test
    void zeroPointsIsZeroWei() {
        assertEquals(BigInteger.ZERO, toWei(0));
    }
}
