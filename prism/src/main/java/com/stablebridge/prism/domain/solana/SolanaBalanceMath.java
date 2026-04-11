package com.stablebridge.prism.domain.solana;

import java.util.List;

public final class SolanaBalanceMath {

    private SolanaBalanceMath() {}

    public static BalanceWalk compute(List<Long> preBalances, List<Long> postBalances, int accountKeyCount) {
        var limit = Math.min(Math.min(preBalances.size(), postBalances.size()), accountKeyCount);
        var maxDecrease = 0L;
        var maxIncrease = 0L;
        var senderIndex = -1;
        var receiverIndex = -1;
        for (var i = 0; i < limit; i++) {
            var pre = preBalances.get(i);
            var post = postBalances.get(i);
            var decrease = Math.max(0L, pre - post);
            var increase = Math.max(0L, post - pre);
            if (decrease > maxDecrease) {
                maxDecrease = decrease;
                senderIndex = i;
            }
            if (increase > maxIncrease) {
                maxIncrease = increase;
                receiverIndex = i;
            }
        }
        return new BalanceWalk(maxDecrease, senderIndex, receiverIndex);
    }
}
