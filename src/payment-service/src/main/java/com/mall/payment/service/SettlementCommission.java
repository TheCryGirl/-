package com.mall.payment.service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Stable line allocation and a single rounding at the sub-order boundary. */
final class SettlementCommission {
    private static final BigInteger BASE = BigInteger.valueOf(10000);

    record Line(long weight, int rate) {
        Line {
            if (weight <= 0 || rate < 0 || rate > 10000) {
                throw new IllegalArgumentException("Invalid commission weight or rate");
            }
        }
    }

    private SettlementCommission() {}

    static String snapshot(List<Line> lines) {
        return lines.stream().map(line -> line.weight() + ":" + line.rate()).collect(Collectors.joining(","));
    }

    static long historicalCommission(long newGross, long previousGross, long previousCommission) {
        if (previousGross <= 0 || previousCommission <= 0) return 0L;
        return BigInteger.valueOf(newGross).multiply(BigInteger.valueOf(previousCommission))
                .add(BigInteger.valueOf(previousGross / 2)).divide(BigInteger.valueOf(previousGross)).longValueExact();
    }

    static long calculate(long gross, String snapshot) {
        var lines = Arrays.stream(snapshot.split(",")).map(pair -> {
            String[] parts = pair.split(":");
            return new Line(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
        }).toList();
        BigInteger amount = BigInteger.valueOf(gross);
        BigInteger totalWeight = lines.stream().map(line -> BigInteger.valueOf(line.weight()))
                .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger weight = BigInteger.ZERO;
        BigInteger allocated = BigInteger.ZERO;
        BigInteger scaledCommission = BigInteger.ZERO;
        for (Line line : lines) {
            weight = weight.add(BigInteger.valueOf(line.weight()));
            // Cumulative floors deterministically allocate every cent in persisted line order.
            BigInteger throughLine = amount.multiply(weight).divide(totalWeight);
            scaledCommission = scaledCommission.add(throughLine.subtract(allocated)
                    .multiply(BigInteger.valueOf(line.rate())));
            allocated = throughLine;
        }
        return scaledCommission.add(BASE.shiftRight(1)).divide(BASE).longValueExact();
    }
}
