package com.ohgiraffers.handlermethod.search;

/** Only fixed codes cross the API boundary; provider errors may contain credentials or prompts. */
public class SearchFailure extends RuntimeException {
    private final String code;
    public SearchFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
