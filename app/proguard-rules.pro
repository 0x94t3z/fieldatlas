# Commons Compress registers ZIP extra-field implementations by class and
# constructs them reflectively. Keep their concrete shape and public no-arg
# constructors while still allowing R8 to shorten class names.
-keep,allowobfuscation class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}

# Commons Compress exposes optional XZ and Zstandard codecs. Field Atlas accepts
# only STORED ZIP packs and never loads those codec adapters.
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.XZInputStream
