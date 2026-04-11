package com.stablebridge.prism.infrastructure.grpc;

import static com.stablebridge.prism.domain.solana.SolanaAddress.LAMPORTS_PER_SOL_SCALE;
import static com.stablebridge.prism.domain.solana.SolanaAddress.truncate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.protobuf.ByteString;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.solana.SolanaBalanceMath;
import com.stablebridge.prism.domain.solana.SolanaPrograms;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransaction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.CompiledInstruction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.InnerInstruction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.InnerInstructions;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.Message;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.TransactionStatusMeta;
import com.stablebridge.prism.infrastructure.solana.Base58;

public class TransactionParser {

    private static final byte[] MEMO_V1_PROGRAM_ID_BYTES = Base58.decode(SolanaPrograms.MEMO_V1_PROGRAM_ID);
    private static final byte[] MEMO_V2_PROGRAM_ID_BYTES = Base58.decode(SolanaPrograms.MEMO_V2_PROGRAM_ID);

    public Optional<SolanaTransaction> parseTransaction(SubscribeUpdateTransaction update) {
        if (!update.hasTransaction()) {
            return Optional.empty();
        }
        var txInfo = update.getTransaction();
        if (!txInfo.hasMeta() || !txInfo.hasTransaction() || !txInfo.getTransaction().hasMessage()) {
            return Optional.empty();
        }
        var message = txInfo.getTransaction().getMessage();
        var meta = txInfo.getMeta();
        var signature = new Signature(Base58.encode(txInfo.getSignature().toByteArray()));
        var failed = meta.hasErr();
        var balances = SolanaBalanceMath.compute(
                meta.getPreBalancesList(), meta.getPostBalancesList(), message.getAccountKeysCount());
        var amount = BigDecimal.valueOf(balances.maxDecrease(), LAMPORTS_PER_SOL_SCALE);
        var from = resolvePubkey(message, balances.senderIndex());
        var to = resolvePubkey(message, balances.receiverIndex());
        var memo = extractMemo(message, meta);
        return Optional.of(SolanaTransaction.builder()
                .signature(signature)
                .slot(update.getSlot())
                .amount(amount)
                .failed(failed)
                .memo(memo)
                .from(from)
                .to(to)
                .build());
    }

    public Optional<Account> extractFeePayer(SubscribeUpdateTransaction update) {
        if (!update.hasTransaction()) {
            return Optional.empty();
        }
        var txInfo = update.getTransaction();
        if (!txInfo.hasMeta() || !txInfo.hasTransaction() || !txInfo.getTransaction().hasMessage()) {
            return Optional.empty();
        }
        var message = txInfo.getTransaction().getMessage();
        var meta = txInfo.getMeta();
        if (message.getAccountKeysCount() == 0 || meta.getPostBalancesCount() == 0) {
            return Optional.empty();
        }
        var pubkeyBytes = message.getAccountKeys(0).toByteArray();
        return Optional.of(Account.builder()
                .pubkey(new Pubkey(Base58.encode(pubkeyBytes)))
                .lamports(meta.getPostBalances(0))
                .slot(update.getSlot())
                .executable(false)
                .rentEpoch(0L)
                .build());
    }

    private static Pubkey resolvePubkey(Message message, int index) {
        if (index < 0 || index >= message.getAccountKeysCount()) {
            return null;
        }
        var encoded = Base58.encode(message.getAccountKeys(index).toByteArray());
        return new Pubkey(truncate(encoded));
    }

    private static String extractMemo(Message message, TransactionStatusMeta meta) {
        var memoIndices = memoProgramIndices(message.getAccountKeysList());
        if (memoIndices.isEmpty()) {
            return null;
        }
        var fromTopLevel = scanTopLevelInstructions(message.getInstructionsList(), memoIndices);
        if (fromTopLevel != null) {
            return fromTopLevel;
        }
        return scanInnerInstructions(meta.getInnerInstructionsList(), memoIndices);
    }

    private static Set<Integer> memoProgramIndices(List<ByteString> accountKeys) {
        var indices = new HashSet<Integer>();
        for (var i = 0; i < accountKeys.size(); i++) {
            var raw = accountKeys.get(i).toByteArray();
            if (Arrays.equals(raw, MEMO_V1_PROGRAM_ID_BYTES) || Arrays.equals(raw, MEMO_V2_PROGRAM_ID_BYTES)) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static String scanTopLevelInstructions(List<CompiledInstruction> instructions, Set<Integer> memoIndices) {
        for (var instruction : instructions) {
            if (memoIndices.contains(instruction.getProgramIdIndex())) {
                var decoded = decodeMemoData(instruction.getData().toByteArray());
                if (decoded != null) {
                    return decoded;
                }
            }
        }
        return null;
    }

    private static String scanInnerInstructions(List<InnerInstructions> groups, Set<Integer> memoIndices) {
        for (var group : groups) {
            for (var instruction : group.getInstructionsList()) {
                var decoded = decodeInnerInstruction(instruction, memoIndices);
                if (decoded != null) {
                    return decoded;
                }
            }
        }
        return null;
    }

    private static String decodeInnerInstruction(InnerInstruction instruction, Set<Integer> memoIndices) {
        if (!memoIndices.contains(instruction.getProgramIdIndex())) {
            return null;
        }
        return decodeMemoData(instruction.getData().toByteArray());
    }

    private static String decodeMemoData(byte[] data) {
        if (data.length == 0) {
            return null;
        }
        var decoded = new String(data, StandardCharsets.UTF_8).replace("\0", "");
        return decoded.isEmpty() ? null : decoded;
    }
}
