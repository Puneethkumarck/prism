package com.stablebridge.prism.infrastructure.websocket;

import static com.stablebridge.prism.fixtures.GeyserTestFixtures.MEMO_V1_PROGRAM_ID_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithTopLevelMemo;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.infrastructure.grpc.TransactionParser;

class BlockNotificationParserTest {

    private static final long SOME_SLOT = 280_000_000L;
    private static final long FIVE_SOL_LAMPORTS = 5_000_000_000L;
    private static final String SOME_MEMO_PROGRAM_ID = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo";
    private static final String LONG_SENDER = "SenderPubkeyAbcdefg1234567890Hijklmn";
    private static final String LONG_RECEIVER = "ReceiverPubkeyZyxwvutsrqponmlkjihgfe";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockNotificationParser parser = new BlockNotificationParser();

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
                """.formatted(SOME_SIGNATURE_BASE58, LONG_SENDER, LONG_RECEIVER));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo(null)
                .from(new Pubkey(BlockNotificationParser.truncateAddress(LONG_SENDER)))
                .to(new Pubkey(BlockNotificationParser.truncateAddress(LONG_RECEIVER)))
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
                """.formatted(SOME_SIGNATURE_BASE58, LONG_SENDER, LONG_RECEIVER, SOME_MEMO_PROGRAM_ID, SOME_MEMO_PROGRAM_ID));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("hello solana")
                .from(new Pubkey(BlockNotificationParser.truncateAddress(LONG_SENDER)))
                .to(new Pubkey(BlockNotificationParser.truncateAddress(LONG_RECEIVER)))
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
                """.formatted(SOME_SIGNATURE_BASE58, LONG_SENDER, LONG_RECEIVER, SOME_MEMO_PROGRAM_ID, SOME_MEMO_PROGRAM_ID));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(false)
                .memo("deep memo")
                .from(new Pubkey(BlockNotificationParser.truncateAddress(LONG_SENDER)))
                .to(new Pubkey(BlockNotificationParser.truncateAddress(LONG_RECEIVER)))
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
                            {"pubkey": "FeePayerPubkey123"},
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
                """.formatted(SOME_SIGNATURE_BASE58, LONG_RECEIVER));

        // when
        var result = parser.extractFeePayers(block);

        // then
        var expected = Account.builder()
                .pubkey(new Pubkey("FeePayerPubkey123"))
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
                """.formatted(SOME_SIGNATURE_BASE58, LONG_SENDER, LONG_RECEIVER));

        // when
        var result = parser.parseBlock(block);

        // then
        var expected = SolanaTransaction.builder()
                .signature(new Signature(SOME_SIGNATURE_BASE58))
                .slot(SOME_SLOT)
                .amount(BigDecimal.valueOf(FIVE_SOL_LAMPORTS, 9))
                .failed(true)
                .memo(null)
                .from(new Pubkey(BlockNotificationParser.truncateAddress(LONG_SENDER)))
                .to(new Pubkey(BlockNotificationParser.truncateAddress(LONG_RECEIVER)))
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
                """.formatted(SOME_SIGNATURE_BASE58, LONG_SENDER, LONG_RECEIVER));

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
    void shouldTruncateAddressLongerThan16Chars() {
        // given
        var address = "abcdefghijklmnopqrstuv";

        // when
        var result = BlockNotificationParser.truncateAddress(address);

        // then
        assertThat(result).isEqualTo("abcdefgh...opqrstuv");
    }

    @Test
    void shouldNotTruncateShortAddress() {
        // given
        var address = "abcdefghijklmnop";

        // when
        var result = BlockNotificationParser.truncateAddress(address);

        // then
        assertThat(result).isEqualTo("abcdefghijklmnop");
    }

    @Test
    void shouldProduceSameDomainObjectAsProtobufParser() throws Exception {
        // given
        var protoTx = txWithTopLevelMemo(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, 0L),
                List.of(0L, FIVE_SOL_LAMPORTS),
                MEMO_V1_PROGRAM_ID_BYTES,
                "parity memo");
        var jsonBlock = readBlock(
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
                            {"pubkey": "%s", "signer": false, "writable": true, "source": "transaction"},
                            {"pubkey": "%s", "signer": false, "writable": false, "source": "transaction"}
                          ],
                          "instructions": [
                            {"programId": "%s", "parsed": "parity memo"}
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
        var protoResult = new TransactionParser().parseTransaction(protoTx);
        var jsonResult = parser.parseBlock(jsonBlock);

        // then
        assertThat(protoResult).isPresent();
        assertThat(jsonResult).hasSize(1);
        assertThat(jsonResult.get(0)).usingRecursiveComparison().isEqualTo(protoResult.get());
    }

    private JsonNode readBlock(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
