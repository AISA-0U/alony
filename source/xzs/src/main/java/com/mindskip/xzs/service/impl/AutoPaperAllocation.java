package com.mindskip.xzs.service.impl;

final class AutoPaperAllocation {
    private AutoPaperAllocation() { }

    static int[][] allocate(int[] bankCounts, int[] typeCounts, int total) {
        int[][] result = new int[bankCounts.length][typeCounts.length];
        int[] rowRemaining = bankCounts.clone();
        int[] columnRemaining = typeCounts.clone();

        for (int row = 0; row < bankCounts.length; row++) {
            for (int column = 0; column < typeCounts.length; column++) {
                int value = bankCounts[row] * typeCounts[column] / total;
                result[row][column] = value;
                rowRemaining[row] -= value;
                columnRemaining[column] -= value;
            }
        }

        while (hasRemaining(rowRemaining)) {
            int bestRow = -1;
            int bestColumn = -1;
            int bestRemainder = -1;
            for (int row = 0; row < bankCounts.length; row++) {
                if (rowRemaining[row] == 0) continue;
                for (int column = 0; column < typeCounts.length; column++) {
                    if (columnRemaining[column] == 0) continue;
                    int remainder = (bankCounts[row] * typeCounts[column]) % total;
                    if (remainder > bestRemainder) {
                        bestRow = row;
                        bestColumn = column;
                        bestRemainder = remainder;
                    }
                }
            }
            if (bestRow < 0) throw new IllegalArgumentException("Unable to allocate paper quotas");
            result[bestRow][bestColumn]++;
            rowRemaining[bestRow]--;
            columnRemaining[bestColumn]--;
        }
        return result;
    }

    private static boolean hasRemaining(int[] values) {
        for (int value : values) if (value > 0) return true;
        return false;
    }
}
