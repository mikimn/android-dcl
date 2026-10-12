package com.mikimn.fixture.shared;

/** Stands for a library bundled by several apps (androidx, OkHttp, ...): same name in each, different copy. */
public final class SharedLib {
    private SharedLib() {}
    public static String owner() { return "fx-application"; }
}
