package com.stablebridge.prism.fixtures;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.google.protobuf.ByteString;
import com.stablebridge.prism.infrastructure.grpc.Base58;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransaction;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransactionInfo;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.CompiledInstruction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.InnerInstruction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.InnerInstructions;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.Message;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.Transaction;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.TransactionError;
import com.stablebridge.prism.infrastructure.grpc.proto.solana.TransactionStatusMeta;

public final class GeyserTestFixtures {

    public static final byte[] MEMO_V1_PROGRAM_ID_BYTES =
            HexFormat.of().parseHex("054a5350f85dc882d614a55672788a296ddf1eababd0a60678884932f4eef6a0");

    public static final byte[] MEMO_V2_PROGRAM_ID_BYTES =
            HexFormat.of().parseHex("054a535a992921064d24e87160da387c7c35b5ddbc92bb81e41fa8404105448d");

    public static final byte[] SOME_SIGNATURE_BYTES = buildSignatureBytes();
    public static final String SOME_SIGNATURE_BASE58 = Base58.encode(SOME_SIGNATURE_BYTES);

    public static final byte[] SOME_SENDER_PUBKEY_BYTES = buildPubkeyBytes((byte) 0x10);
    public static final String SOME_SENDER_PUBKEY_BASE58 = Base58.encode(SOME_SENDER_PUBKEY_BYTES);

    public static final byte[] SOME_RECEIVER_PUBKEY_BYTES = buildPubkeyBytes((byte) 0x20);
    public static final String SOME_RECEIVER_PUBKEY_BASE58 = Base58.encode(SOME_RECEIVER_PUBKEY_BYTES);

    public static final byte[] SOME_FEE_PAYER_PUBKEY_BYTES = buildPubkeyBytes((byte) 0x30);
    public static final String SOME_FEE_PAYER_PUBKEY_BASE58 = Base58.encode(SOME_FEE_PAYER_PUBKEY_BYTES);

    public static final byte[] SHORT_PUBKEY_BYTES = new byte[] {(byte) 0x2A};
    public static final String SHORT_PUBKEY_BASE58 = Base58.encode(SHORT_PUBKEY_BYTES);

    private GeyserTestFixtures() {}

    public static SubscribeUpdateTransaction txWithoutMeta(long slot, byte[] sigBytes) {
        var transaction = Transaction.newBuilder()
                .addSignatures(ByteString.copyFrom(sigBytes))
                .setMessage(Message.newBuilder().build())
                .build();
        var info = SubscribeUpdateTransactionInfo.newBuilder()
                .setSignature(ByteString.copyFrom(sigBytes))
                .setTransaction(transaction)
                .build();
        return SubscribeUpdateTransaction.newBuilder()
                .setSlot(slot)
                .setTransaction(info)
                .build();
    }

    public static SubscribeUpdateTransaction txWithoutMessage(long slot, byte[] sigBytes) {
        var transaction = Transaction.newBuilder()
                .addSignatures(ByteString.copyFrom(sigBytes))
                .build();
        var info = SubscribeUpdateTransactionInfo.newBuilder()
                .setSignature(ByteString.copyFrom(sigBytes))
                .setTransaction(transaction)
                .setMeta(TransactionStatusMeta.newBuilder().build())
                .build();
        return SubscribeUpdateTransaction.newBuilder()
                .setSlot(slot)
                .setTransaction(info)
                .build();
    }

    public static SubscribeUpdateTransaction txWithoutInnerTransaction(long slot, byte[] sigBytes) {
        var info = SubscribeUpdateTransactionInfo.newBuilder()
                .setSignature(ByteString.copyFrom(sigBytes))
                .setMeta(TransactionStatusMeta.newBuilder().build())
                .build();
        return SubscribeUpdateTransaction.newBuilder()
                .setSlot(slot)
                .setTransaction(info)
                .build();
    }

    public static SubscribeUpdateTransaction updateWithoutTransaction(long slot) {
        return SubscribeUpdateTransaction.newBuilder().setSlot(slot).build();
    }

    public static SubscribeUpdateTransaction txWithBalances(
            long slot,
            byte[] sigBytes,
            List<byte[]> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances) {
        return buildTx(slot, sigBytes, accountKeys, preBalances, postBalances, false, List.of(), List.of());
    }

    public static SubscribeUpdateTransaction failedTxWithBalances(
            long slot,
            byte[] sigBytes,
            List<byte[]> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances) {
        return buildTx(slot, sigBytes, accountKeys, preBalances, postBalances, true, List.of(), List.of());
    }

    public static SubscribeUpdateTransaction txWithTopLevelMemo(
            long slot,
            byte[] sigBytes,
            List<byte[]> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances,
            byte[] memoProgramIdBytes,
            String memoText) {
        var extendedKeys = appendKey(accountKeys, memoProgramIdBytes);
        var extendedPre = appendBalance(preBalances);
        var extendedPost = appendBalance(postBalances);
        var memoInstruction = CompiledInstruction.newBuilder()
                .setProgramIdIndex(accountKeys.size())
                .setData(ByteString.copyFrom(memoText.getBytes(StandardCharsets.UTF_8)))
                .build();
        return buildTx(slot, sigBytes, extendedKeys, extendedPre, extendedPost, false, List.of(memoInstruction), List.of());
    }

    public static SubscribeUpdateTransaction txWithInnerMemo(
            long slot,
            byte[] sigBytes,
            List<byte[]> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances,
            byte[] memoProgramIdBytes,
            String memoText) {
        var extendedKeys = appendKey(accountKeys, memoProgramIdBytes);
        var extendedPre = appendBalance(preBalances);
        var extendedPost = appendBalance(postBalances);
        var innerMemo = InnerInstruction.newBuilder()
                .setProgramIdIndex(accountKeys.size())
                .setData(ByteString.copyFrom(memoText.getBytes(StandardCharsets.UTF_8)))
                .build();
        var innerGroup = InnerInstructions.newBuilder()
                .setIndex(0)
                .addInstructions(innerMemo)
                .build();
        return buildTx(slot, sigBytes, extendedKeys, extendedPre, extendedPost, false, List.of(), List.of(innerGroup));
    }

    private static SubscribeUpdateTransaction buildTx(
            long slot,
            byte[] sigBytes,
            List<byte[]> accountKeys,
            List<Long> preBalances,
            List<Long> postBalances,
            boolean failed,
            List<CompiledInstruction> topLevelInstructions,
            List<InnerInstructions> innerInstructions) {
        var messageBuilder = Message.newBuilder();
        accountKeys.forEach(key -> messageBuilder.addAccountKeys(ByteString.copyFrom(key)));
        topLevelInstructions.forEach(messageBuilder::addInstructions);
        var transaction = Transaction.newBuilder()
                .addSignatures(ByteString.copyFrom(sigBytes))
                .setMessage(messageBuilder.build())
                .build();
        var metaBuilder = TransactionStatusMeta.newBuilder()
                .addAllPreBalances(preBalances)
                .addAllPostBalances(postBalances);
        innerInstructions.forEach(metaBuilder::addInnerInstructions);
        if (failed) {
            metaBuilder.setErr(TransactionError.newBuilder()
                    .setErr(ByteString.copyFrom(new byte[] {0x01}))
                    .build());
        }
        var info = SubscribeUpdateTransactionInfo.newBuilder()
                .setSignature(ByteString.copyFrom(sigBytes))
                .setTransaction(transaction)
                .setMeta(metaBuilder.build())
                .build();
        return SubscribeUpdateTransaction.newBuilder()
                .setSlot(slot)
                .setTransaction(info)
                .build();
    }

    private static List<byte[]> appendKey(List<byte[]> accountKeys, byte[] extra) {
        var copy = new ArrayList<byte[]>(accountKeys.size() + 1);
        copy.addAll(accountKeys);
        copy.add(extra);
        return List.copyOf(copy);
    }

    private static List<Long> appendBalance(List<Long> balances) {
        var copy = new ArrayList<Long>(balances.size() + 1);
        copy.addAll(balances);
        copy.add(0L);
        return List.copyOf(copy);
    }

    private static byte[] buildSignatureBytes() {
        var bytes = new byte[64];
        bytes[0] = 0x01;
        bytes[63] = 0x02;
        return bytes;
    }

    private static byte[] buildPubkeyBytes(byte sentinel) {
        var bytes = new byte[32];
        bytes[0] = sentinel;
        return bytes;
    }
}
