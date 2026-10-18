package com.mikimn.fixture.shared;

/** Stands for a library bundled by several apps (androidx, OkHttp, ...): same name in each, different copy. */
public final class SharedLib {
    private SharedLib() {}
    /** What a library asks its context: the answer must be this app's, whichever app bundled the library first. */
    public static String packageOf(android.content.Context context) { return context.getPackageName(); }
    public static String owner() { return "fx-application"; }
}
