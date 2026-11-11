# Regras R8 do release. O build.gradle.kts já referenciava este ficheiro, mas
# ele não existia: o Gradle aceita ficheiros de regras em falta em silêncio.
# As libs (Retrofit, kotlinx.serialization, Hilt, OkHttp) trazem consumer-rules
# próprias, por isso o APK funcionava; aqui ficam as garantias que faltavam.

# Mapa de desofuscação: o do AGP (build/outputs/mapping/release/mapping.txt).
# Arquivar por versão fora do repositório.

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

# DTOs da app: os nomes do JSON vêm do serializer gerado pelo kotlinx.serialization,
# por isso os campos podem ser ofuscados (allowobfuscation). A regra só impede o R8
# de remover membros que a app não lê (rede de segurança; não protege nomes).
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

# --- Logcat ------------------------------------------------------------------
# Sem Log.d/v/i no release (caminhos, estados, mensagens do JS das Notes).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# --- OkHttp ------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Credential Manager (passkeys): o provider do Play Services é carregado por reflexão.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Tesseract4Android (OCR dos talões): o código nativo acede por JNI a campos e
# callbacks (mNativeData, onProgressValues) — a AAR não traz regras próprias.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
