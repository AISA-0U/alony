package com.mindskip.xzs.service.impl;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AutoPaperAllocationTest {

    @Test
    public void preservesBankAndQuestionTypeTotals() {
        int[][] allocation = AutoPaperAllocation.allocate(
                new int[]{80, 10, 10}, new int[]{30, 20, 40, 10}, 100);

        assertArrayEquals(new int[]{24, 16, 32, 8}, allocation[0]);
        assertArrayEquals(new int[]{3, 2, 4, 1}, allocation[1]);
        assertArrayEquals(new int[]{3, 2, 4, 1}, allocation[2]);

        for (int row = 0; row < allocation.length; row++) {
            int expected = row == 0 ? 80 : 10;
            assertEquals(expected, sum(allocation[row]));
        }
        assertEquals(30, columnSum(allocation, 0));
        assertEquals(20, columnSum(allocation, 1));
        assertEquals(40, columnSum(allocation, 2));
        assertEquals(10, columnSum(allocation, 3));
    }

    private int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }

    private int columnSum(int[][] values, int column) {
        int total = 0;
        for (int[] value : values) total += value[column];
        return total;
    }
}
