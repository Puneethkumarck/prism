package com.stablebridge.prism.infrastructure.websocket;

import static com.stablebridge.prism.fixtures.GeyserTestFixtures.MEMO_V1_PROGRAM_ID_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_FEE_PAYER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_FEE_PAYER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.failedTxWithBalances;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithBalances;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithInnerMemo;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithTopLevelMemo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.solana.SolanaAddress;
import com.stablebridge.prism.infrastructure.grpc.TransactionParser;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransaction;
import com.stablebridge.prism.infrastructure.solana.Base58;

class BlockNotificationParserTest {

    private static final long SOME_SLOT = 280_000_000L;
    private static final long FIVE_SOL_LAMPORTS = 5_000_000_000L;
    private static final String SOME_MEMO_PROGRAM_ID = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockNotificationParser parser = new BlockNotificationParser();
    private final TransactionParser protobufParser = new TransactionParser();

    @Test
    void shouldParseTransactionFromBlockNotification() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s", "signer": true, "writable": true, "source": "transaction"},
                            {"pubkey": "%s", "signer": false, "writable": true, "source": "transaction"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """.formatted(SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @Test
    void shouldExtractMemoFromJsonInstructions() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": [
                            {
                              "programId": "%s",
                              "parsed": "hello solana"
                            }
                          ]
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0, 0],
                        "postBalances": [0, 5000000000, 0]
                      }
                    }
                  ]
                }
                """.formatted(
                        SOME_SIGNATURE_BASE58,
                        SOME_SENDER_PUBKEY_BASE58,
                        SOME_RECEIVER_PUBKEY_BASE58,
                        SOME_MEMO_PROGRAM_ID,
                        SOME_MEMO_PROGRAM_ID));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("hello solana")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @Test
    void shouldExtractMemoFromInnerInstructions() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0, 0],
                        "postBalances": [0, 5000000000, 0],
                        "innerInstructions": [
                          {
                            "index": 0,
                            "instructions": [
                              {"programId": "%s", "parsed": "deep memo"}
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(
                        SOME_SIGNATURE_BASE58,
                        SOME_SENDER_PUBKEY_BASE58,
                        SOME_RECEIVER_PUBKEY_BASE58,
                        SOME_MEMO_PROGRAM_ID,
                        SOME_MEMO_PROGRAM_ID));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("deep memo")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @Test
    void shouldDecodeMemoFromDataField() throws Exception {
        // given
        var memoBytes = "memo via data".getBytes(StandardCharsets.UTF_8);
        var dataBase58 = Base58.encode(memoBytes);
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": [
                            {"programId": "%s", "data": "%s", "accounts": []}
                          ]
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0, 0],
                        "postBalances": [0, 5000000000, 0]
                      }
                    }
                  ]
                }
                """.formatted(
                        SOME_SIGNATURE_BASE58,
                        SOME_SENDER_PUBKEY_BASE58,
                        SOME_RECEIVER_PUBKEY_BASE58,
                        SOME_MEMO_PROGRAM_ID,
                        SOME_MEMO_PROGRAM_ID,
                        dataBase58));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("memo via data")
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @Test
    void shouldExtractFeePayer() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [1000000000, 0],
                        "postBalances": [900000000, 100000000]
                      }
                    }
                  ]
                }
                """.formatted(SOME_SIGNATURE_BASE58, SOME_FEE_PAYER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58));

        // when
        var result = parser.extractFeePayers(block);

        // then
        var expected = Account.builder()
                .pubkey(new Pubkey(SOME_FEE_PAYER_PUBKEY_BASE58))
                .lamports(900_000_000L)
                .slot(SOME_SLOT)
                .executable(false)
                .rentEpoch(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @Test
    void shouldMarkFailedTransactions() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": {"InstructionError": [0, "Custom"]},
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """.formatted(SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(true)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @Test
    void shouldSkipTransactionWhenMetaMissing() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      }
                    }
                  ]
                }
                """.formatted(SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58));

        // when
        var result = parser.parseBlock(block);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyTransactionsArray() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": []
                }
                """);

        // when
        var result = parser.parseBlock(block);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNotificationIsNull() {
        // when
        var result = parser.parseBlock(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTransactionsArrayIsMissing() throws Exception {
        // given
        var block = readBlock("""
                {"slot": 280000000}
                """);

        // when
        var result = parser.parseBlock(block);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipTransactionWhenMetaIsNull() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": null
                    }
                  ]
                }
                """.formatted(SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58));

        // when
        var result = parser.parseBlock(block);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTreatMissingErrFieldAsSuccessful() throws Exception {
        // given
        var block = readBlock(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """.formatted(SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(SolanaAddress.truncate(SOME_SENDER_PUBKEY_BASE58)))
                .to(new Pubkey(SolanaAddress.truncate(SOME_RECEIVER_PUBKEY_BASE58)))
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parityCases")
    void shouldProduceSameDomainObjectAsProtobufParser(String name, SubscribeUpdateTransaction protoTx, String jsonBlock)
            throws Exception {
        // given
        var jsonNode = readBlock(jsonBlock);

        // when
        var protoResult = protobufParser.parseTransaction(protoTx);
        var jsonResult = parser.parseBlock(jsonNode);

        // then
        assertThat(protoResult).isPresent();
        assertThat(jsonResult).hasSize(1);
        assertThat(jsonResult.get(0)).usingRecursiveComparison().isEqualTo(protoResult.get());
    }

    private static Stream<Arguments> parityCases() {
        return Stream.of(
                Arguments.of(
                        "transfer with top-level memo",
                        txWithTopLevelMemo(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS),
                                MEMO_V1_PROGRAM_ID_BYTES,
                                "parity memo"),
                        jsonTransferWithTopLevelMemo(
                                SOME_SIGNATURE_BASE58,
                                SOME_SENDER_PUBKEY_BASE58,
                                SOME_RECEIVER_PUBKEY_BASE58,
                                SOME_MEMO_PROGRAM_ID,
                                "parity memo")),
                Arguments.of(
                        "failed transfer with no memo",
                        failedTxWithBalances(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS)),
                        jsonFailedTransfer(
                                SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58)),
                Arguments.of(
                        "transfer with inner-instruction memo",
                        txWithInnerMemo(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS),
                                MEMO_V1_PROGRAM_ID_BYTES,
                                "inner parity"),
                        jsonTransferWithInnerMemo(
                                SOME_SIGNATURE_BASE58,
                                SOME_SENDER_PUBKEY_BASE58,
                                SOME_RECEIVER_PUBKEY_BASE58,
                                SOME_MEMO_PROGRAM_ID,
                                "inner parity")),
                Arguments.of(
                        "pure transfer without memo",
                        txWithBalances(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS)),
                        jsonPureTransfer(
                                SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58)),
                Arguments.of(
                        "transfer with distinct long pubkeys requiring truncation",
                        txWithBalances(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_FEE_PAYER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS)),
                        jsonPureTransfer(
                                SOME_SIGNATURE_BASE58, SOME_FEE_PAYER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58)),
                Arguments.of(
                        "pure transfer with string-form accountKeys",
                        txWithBalances(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS)),
                        jsonPureTransferWithStringAccountKeys(
                                SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58)),
                Arguments.of(
                        "top-level memo transfer with string-form accountKeys",
                        txWithTopLevelMemo(
                                SOME_SLOT,
                                SOME_SIGNATURE_BYTES,
                                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                                List.of(FIVE_SOL_LAMPORTS, 0L),
                                List.of(0L, FIVE_SOL_LAMPORTS),
                                MEMO_V1_PROGRAM_ID_BYTES,
                                "string-form memo"),
                        jsonTransferWithTopLevelMemoStringAccountKeys(
                                SOME_SIGNATURE_BASE58,
                                SOME_SENDER_PUBKEY_BASE58,
                                SOME_RECEIVER_PUBKEY_BASE58,
                                SOME_MEMO_PROGRAM_ID,
                                "string-form memo")));
    }

    private static String jsonTransferWithTopLevelMemo(
            String signature, String sender, String receiver, String memoProgramId, String memoText) {
        return """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": [
                            {"programId": "%s", "parsed": "%s"}
                          ]
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0, 0],
                        "postBalances": [0, 5000000000, 0]
                      }
                    }
                  ]
                }
                """.formatted(signature, sender, receiver, memoProgramId, memoProgramId, memoText);
    }

    private static String jsonTransferWithInnerMemo(
            String signature, String sender, String receiver, String memoProgramId, String memoText) {
        return """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0, 0],
                        "postBalances": [0, 5000000000, 0],
                        "innerInstructions": [
                          {
                            "index": 0,
                            "instructions": [
                              {"programId": "%s", "parsed": "%s"}
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(signature, sender, receiver, memoProgramId, memoProgramId, memoText);
    }

    private static String jsonFailedTransfer(String signature, String sender, String receiver) {
        return """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": {"InstructionError": [0, "Custom"]},
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """.formatted(signature, sender, receiver);
    }

    private static String jsonPureTransfer(String signature, String sender, String receiver) {
        return """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """.formatted(signature, sender, receiver);
    }

    private static String jsonPureTransferWithStringAccountKeys(String signature, String sender, String receiver) {
        return """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": ["%s", "%s"],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """.formatted(signature, sender, receiver);
    }

    private static String jsonTransferWithTopLevelMemoStringAccountKeys(
            String signature, String sender, String receiver, String memoProgramId, String memoText) {
        return """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": ["%s", "%s", "%s"],
                          "instructions": [
                            {"programId": "%s", "parsed": "%s"}
                          ]
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0, 0],
                        "postBalances": [0, 5000000000, 0]
                      }
                    }
                  ]
                }
                """.formatted(signature, sender, receiver, memoProgramId, memoProgramId, memoText);
    }

    private JsonNode readBlock(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
