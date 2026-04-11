package com.stablebridge.prism.infrastructure.websocket;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.infrastructure.grpc.Base58;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlockNotificationParser {

    private static final String MEMO_V1_PROGRAM_ID = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo";
    private static final String MEMO_V2_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr";
    private static final Set<String> MEMO_PROGRAM_IDS = Set.of(MEMO_V1_PROGRAM_ID, MEMO_V2_PROGRAM_ID);
    private static final int TRUNCATION_THRESHOLD = 16;
    private static final int TRUNCATION_PREFIX = 8;
    private static final int TRUNCATION_SUFFIX = 8;
    private static final int LAMPORTS_PER_SOL_SCALE = 9;

    public List<SolanaTransaction> parseBlock(JsonNode blockNotification) {
        if (blockNotification == null || blockNotification.isMissingNode() || blockNotification.isNull()) {
            return List.of();
        }
        var slot = readSlot(blockNotification);
        var transactions = blockNotification.path("transactions");
        if (!transactions.isArray()) {
            return List.of();
        }
        var results = new ArrayList<SolanaTransaction>(transactions.size());
        for (var txNode : transactions) {
            parseTransaction(txNode, slot).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    public List<Account> extractFeePayers(JsonNode blockNotification) {
        if (blockNotification == null || blockNotification.isMissingNode() || blockNotification.isNull()) {
            return List.of();
        }
        var slot = readSlot(blockNotification);
        var transactions = blockNotification.path("transactions");
        if (!transactions.isArray()) {
            return List.of();
        }
        var results = new ArrayList<Account>(transactions.size());
        for (var txNode : transactions) {
            extractFeePayer(txNode, slot).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    static String truncateAddress(String address) {
        if (address.length() > TRUNCATION_THRESHOLD) {
            return address.substring(0, TRUNCATION_PREFIX)
                    + "..."
                    + address.substring(address.length() - TRUNCATION_SUFFIX);
        }
        return address;
    }

    private Optional<SolanaTransaction> parseTransaction(JsonNode txNode, long slot) {
        if (txNode == null || !txNode.isObject()) {
            return Optional.empty();
        }
        var meta = txNode.path("meta");
        var transaction = txNode.path("transaction");
        var message = transaction.path("message");
        if (meta.isMissingNode() || meta.isNull() || transaction.isMissingNode() || message.isMissingNode()) {
            return Optional.empty();
        }
        var signatureValue = readFirstSignature(transaction);
        if (signatureValue == null) {
            return Optional.empty();
        }
        var accountKeys = readAccountKeys(message);
        if (accountKeys.isEmpty()) {
            return Optional.empty();
        }
        var preBalances = readLongArray(meta.path("preBalances"));
        var postBalances = readLongArray(meta.path("postBalances"));
        var balances = computeBalances(preBalances, postBalances, accountKeys.size());
        var amount = BigDecimal.valueOf(balances.maxDecrease(), LAMPORTS_PER_SOL_SCALE);
        var from = resolvePubkey(accountKeys, balances.senderIndex());
        var to = resolvePubkey(accountKeys, balances.receiverIndex());
        var failed = isFailed(meta);
        var memo = extractMemo(message, meta, accountKeys);
        return Optional.of(SolanaTransaction.builder()
                .signature(new Signature(signatureValue))
                .slot(slot)
                .amount(amount)
                .failed(failed)
                .memo(memo)
                .from(from)
                .to(to)
                .build());
    }

    private Optional<Account> extractFeePayer(JsonNode txNode, long slot) {
        if (txNode == null || !txNode.isObject()) {
            return Optional.empty();
        }
        var meta = txNode.path("meta");
        var transaction = txNode.path("transaction");
        var message = transaction.path("message");
        if (meta.isMissingNode() || meta.isNull() || message.isMissingNode()) {
            return Optional.empty();
        }
        var accountKeys = readAccountKeys(message);
        var postBalances = readLongArray(meta.path("postBalances"));
        if (accountKeys.isEmpty() || postBalances.isEmpty()) {
            return Optional.empty();
        }
        var payerKey = accountKeys.get(0);
        return Optional.of(Account.builder()
                .pubkey(new Pubkey(payerKey))
                .lamports(postBalances.get(0))
                .slot(slot)
                .executable(false)
                .rentEpoch(0L)
                .build());
    }

    private static long readSlot(JsonNode blockNotification) {
        var slotNode = blockNotification.path("slot");
        return slotNode.isNumber() ? slotNode.asLong() : 0L;
    }

    private static String readFirstSignature(JsonNode transaction) {
        var signatures = transaction.path("signatures");
        if (!signatures.isArray() || signatures.isEmpty()) {
            return null;
        }
        var first = signatures.get(0);
        return first.isTextual() ? first.asText() : null;
    }

    private static List<String> readAccountKeys(JsonNode message) {
        var node = message.path("accountKeys");
        if (!node.isArray()) {
            return List.of();
        }
        var keys = new ArrayList<String>(node.size());
        for (var entry : node) {
            if (entry.isTextual()) {
                keys.add(entry.asText());
            } else if (entry.isObject() && entry.hasNonNull("pubkey")) {
                keys.add(entry.get("pubkey").asText());
            }
        }
        return List.copyOf(keys);
    }

    private static List<Long> readLongArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<Long>(node.size());
        for (var entry : node) {
            values.add(entry.isNumber() ? entry.asLong() : 0L);
        }
        return List.copyOf(values);
    }

    private static boolean isFailed(JsonNode meta) {
        var err = meta.path("err");
        return !err.isMissingNode() && !err.isNull();
    }

    private static BalanceWalk computeBalances(List<Long> preBalances, List<Long> postBalances, int accountKeyCount) {
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

    private static Pubkey resolvePubkey(List<String> accountKeys, int index) {
        if (index < 0 || index >= accountKeys.size()) {
            return null;
        }
        return new Pubkey(truncateAddress(accountKeys.get(index)));
    }

    private static String extractMemo(JsonNode message, JsonNode meta, List<String> accountKeys) {
        var memoIndices = memoProgramIndices(accountKeys);
        var fromTopLevel = scanInstructions(message.path("instructions"), memoIndices);
        if (fromTopLevel != null) {
            return fromTopLevel;
        }
        return scanInnerInstructionGroups(meta.path("innerInstructions"), memoIndices);
    }

    private static Set<Integer> memoProgramIndices(List<String> accountKeys) {
        var indices = new HashSet<Integer>();
        for (var i = 0; i < accountKeys.size(); i++) {
            if (MEMO_PROGRAM_IDS.contains(accountKeys.get(i))) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static String scanInstructions(JsonNode instructions, Set<Integer> memoIndices) {
        if (!instructions.isArray()) {
            return null;
        }
        for (var instruction : instructions) {
            if (!isMemoInstruction(instruction, memoIndices)) {
                continue;
            }
            var decoded = decodeMemo(instruction);
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    private static String scanInnerInstructionGroups(JsonNode groups, Set<Integer> memoIndices) {
        if (!groups.isArray()) {
            return null;
        }
        for (var group : groups) {
            var decoded = scanInstructions(group.path("instructions"), memoIndices);
            if (decoded != null) {
                return decoded;
            }
        }
        return null;
    }

    private static boolean isMemoInstruction(JsonNode instruction, Set<Integer> memoIndices) {
        if (instruction == null || !instruction.isObject()) {
            return false;
        }
        var programId = instruction.path("programId");
        if (programId.isTextual() && MEMO_PROGRAM_IDS.contains(programId.asText())) {
            return true;
        }
        var programIdIndex = instruction.path("programIdIndex");
        return programIdIndex.isNumber() && memoIndices.contains(programIdIndex.asInt());
    }

    private static String decodeMemo(JsonNode instruction) {
        var parsed = instruction.path("parsed");
        if (parsed.isTextual()) {
            return sanitizeMemo(parsed.asText());
        }
        var data = instruction.path("data");
        if (data.isTextual()) {
            return decodeMemoData(data.asText());
        }
        return null;
    }

    private static String decodeMemoData(String data) {
        if (data.isEmpty()) {
            return null;
        }
        try {
            var bytes = Base58.decode(data);
            if (bytes.length == 0) {
                return null;
            }
            return sanitizeMemo(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            log.debug("failed to decode memo data as base58: {}", ex.getMessage());
            return null;
        }
    }

    private static String sanitizeMemo(String raw) {
        var cleaned = raw.replace("\0", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private record BalanceWalk(long maxDecrease, int senderIndex, int receiverIndex) {}
}
