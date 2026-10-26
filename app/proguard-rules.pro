# JNI_OnLoad resolves this class and callback by name; the native entry point also
# uses its full JNI symbol. Preserve just this bridge, not the entire application.
-keep class cn.nascp.clash.core.NativeCore {
    private native java.lang.String call(java.lang.String);
    public static boolean protectSocket(int);
}
