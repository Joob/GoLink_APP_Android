# Regras R8 do release. O build.gradle.kts já referenciava este ficheiro, mas
# ele não existia: o Gradle aceita ficheiros de regras em falta em silêncio.
# As libs (Retrofit, kotlinx.serialization, Hilt, OkHttp) trazem consumer-rules
# próprias, por isso o APK funcionava; aqui ficam as garantias que faltavam.

# Mapa de desofuscação, para traduzir stack traces de produção.
-printmapping mapping.txt

# Mantém números de linha e nomes de ficheiro nos stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ---------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclassmembers class ** implements kotlinx.serialization.KSerializer {
    public static ** INSTANCE;
}

# DTOs da app: os nomes dos campos sao a chave do JSON da API.
-keepclassmembers,allowobfuscation class co.golink.tester.domain.** {
    *;
}

# --- Retrofit ----------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- libsodium (Lazysodium + JNA) --------------------------------------------
# O JNA liga os métodos Java aos símbolos nativos do libsodium PELO NOME. Se o R8
# renomear ou remover esses métodos, a ligação falha em silêncio: as chamadas
# passam a devolver false e TODA a cripto E2E deixa de funcionar — só no release,
# porque o debug não é minificado. Era isto que impedia o unlock da passphrase
# (o secretbox não fazia sequer round-trip com uma chave conhecida).
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
# As Structure do JNA têm os campos lidos por reflexão.
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
}
# O JNA referencia AWT, que não existe no Android.
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# --- OkHttp ------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
