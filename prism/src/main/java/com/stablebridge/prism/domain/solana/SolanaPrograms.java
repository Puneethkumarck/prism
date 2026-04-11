package com.stablebridge.prism.domain.solana;

public final class SolanaPrograms {

    public static final String MEMO_V1_PROGRAM_ID = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo";
    public static final String MEMO_V2_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr";

    private SolanaPrograms() {}

    public static boolean isMemoProgram(String programId) {
        return MEMO_V1_PROGRAM_ID.equals(programId) || MEMO_V2_PROGRAM_ID.equals(programId);
    }
}
