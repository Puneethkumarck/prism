package com.stablebridge.prism.domain.port;

import java.util.List;

import com.stablebridge.prism.domain.model.Memo;

public interface MemoRepository {

    void bulkInsert(List<Memo> memos);

    List<Memo> findAll(long limit, long offset);

    long countAll();
}
