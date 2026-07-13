package co.golink.tester.data.encryption

import co.golink.tester.domain.encryption.StoreUserEncryptionBody
import co.golink.tester.domain.encryption.UpdateSecretBody
import co.golink.tester.network.UserEncryptionApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestão das chaves E2E no cliente Android (espelha o módulo Vuex `e2e`).
 * A chave privada vive APENAS em memória; ao logout limpa-se.
 *
 * Interopera com o servidor: as data keys são seladas à chave pública (o servidor
 * sela na migração; aqui abrimos com a privada) — nunca vê a privada em claro.
 */
@Singleton
class E2EKeyManager @Inject constructor(
    private val api: UserEncryptionApi,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile private var publicKeyB64: String? = null
    @Volatile private var publicKey: ByteArray? = null
    @Volatile private var secretKey: ByteArray? = null

    val isUnlocked: Boolean get() = secretKey != null
    /** Sempre cifrado: basta a chave estar desbloqueada. */
    val shouldEncrypt: Boolean get() = isUnlocked
    val myPublicKeyB64: String? get() = publicKeyB64

    /**
     * Fingerprint curto e estável da chave pública (deteta key-substitution):
     * SHA-256(pubkey) → primeiros 8 bytes → hex em grupos de 4. Igual ao web
     * para a mesma chave, por isso pode ser comparado out-of-band.
     */
    fun fingerprint(): String? {
        val pub = publicKey ?: return null
        return fingerprintBytes(pub)
    }

    /** Fingerprint de uma chave pública arbitrária (ex.: destinatário de partilha). */
    fun fingerprintOf(publicKeyB64: String): String = fingerprintBytes(Envelope.unb64(publicKeyB64))

    private fun fingerprintBytes(pub: ByteArray): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(pub)
        return hash.take(8).joinToString("") { "%02X".format(it) }
            .chunked(4).joinToString(" ")
    }

    /** true se já existe configuração E2E no servidor (para decidir setup vs unlock). */
    suspend fun isConfigured(): Boolean = api.get().body()?.configured == true

    /**
     * Setup 1x (devolve a recovery key formatada) ou unlock com o segredo
     * (password normal ou passphrase E2E). Devolve null no unlock.
     */
    suspend fun setupOrUnlock(secret: String): String? {
        val body = api.get().body() ?: error("sem resposta do servidor")

        if (body.configured) {
            val salt = Envelope.unb64(body.pwd_salt!!)
            val key = Envelope.deriveKey(secret, salt, body.kdf_ops!!, body.kdf_mem!!, body.kdf_alg ?: Envelope.KDF_ALG_ARGON2)
            val sk = Envelope.unwrapSecretKey(
                Envelope.unb64(body.wrapped_sk_password!!),
                Envelope.unb64(body.wrapped_sk_password_nonce!!),
                key,
            )
            setKeys(body.public_key!!, sk)
            return null
        }

        // Setup inicial.
        val kp = Envelope.generateKeypair()
        val salt = Envelope.generateSalt()
        val alg = Envelope.defaultKdfAlg()
        val ops = Envelope.defaultKdfOps()
        val mem = Envelope.defaultKdfMem()

        val pwKey = Envelope.deriveKey(secret, salt, ops, mem, alg)
        val wrappedPw = Envelope.wrapSecretKey(kp.secretKey, pwKey)

        val recovery = Envelope.generateRecoveryKey()
        val wrappedRec = Envelope.wrapSecretKey(kp.secretKey, recovery)

        val pubB64 = Envelope.b64(kp.publicKey)
        val resp = api.setup(
            StoreUserEncryptionBody(
                public_key = pubB64,
                wrapped_sk_password = Envelope.b64(wrappedPw.ciphertext),
                wrapped_sk_password_nonce = Envelope.b64(wrappedPw.nonce),
                pwd_salt = Envelope.b64(salt),
                wrapped_sk_recovery = Envelope.b64(wrappedRec.ciphertext),
                wrapped_sk_recovery_nonce = Envelope.b64(wrappedRec.nonce),
                kdf_ops = ops,
                kdf_mem = mem,
                kdf_alg = alg,
            )
        )
        check(resp.isSuccessful) { "setup falhou: HTTP ${resp.code()}" }

        setKeys(pubB64, kp.secretKey)
        return Envelope.formatRecovery(recovery)
    }

    /** "Esqueci a password": desbloqueia com a recovery key. */
    suspend fun recoverWithKey(recoveryKey: String) {
        val body = api.recovery().body() ?: error("sem recovery")
        val recBytes = Envelope.parseRecovery(recoveryKey)
        val sk = Envelope.unwrapSecretKey(
            Envelope.unb64(body.wrapped_sk_recovery),
            Envelope.unb64(body.wrapped_sk_recovery_nonce),
            recBytes,
        )
        setKeys(body.public_key, sk)
    }

    /** Rotação: re-embrulha a privada (em memória) com um novo segredo. */
    suspend fun rotateSecret(secret: String) {
        val sk = secretKey ?: error("e2e locked")
        val salt = Envelope.generateSalt()
        val alg = Envelope.defaultKdfAlg()
        val ops = Envelope.defaultKdfOps()
        val mem = Envelope.defaultKdfMem()
        val key = Envelope.deriveKey(secret, salt, ops, mem, alg)
        val wrapped = Envelope.wrapSecretKey(sk, key)
        api.updateSecret(
            UpdateSecretBody(
                wrapped_sk_password = Envelope.b64(wrapped.ciphertext),
                wrapped_sk_password_nonce = Envelope.b64(wrapped.nonce),
                pwd_salt = Envelope.b64(salt),
                kdf_ops = ops,
                kdf_mem = mem,
                kdf_alg = alg,
            )
        )
    }

    /** Abre a data key selada de um ficheiro (para decifrar). */
    fun openFileDataKey(wrappedDataKeyB64: String): ByteArray {
        val pk = publicKey ?: error("e2e locked")
        val sk = secretKey ?: error("e2e locked")
        return Envelope.openDataKey(Envelope.unb64(wrappedDataKeyB64), pk, sk)
    }

    /** Sela uma data key à própria chave pública (para upload). */
    fun sealForSelf(dataKey: ByteArray): String {
        val pk = publicKey ?: error("e2e locked")
        return Envelope.b64(Envelope.sealDataKey(dataKey, pk))
    }

    /** Sela uma data key a uma chave pública arbitrária (dono/membro de team folder). */
    fun sealForPublicKey(dataKey: ByteArray, publicKeyB64: String): String =
        Envelope.b64(Envelope.sealDataKey(dataKey, Envelope.unb64(publicKeyB64)))

    fun lock() {
        // Zera os bytes da privada antes de largar a referência (defesa contra
        // memory-scraping) — em vez de esperar pelo GC.
        secretKey?.fill(0)
        secretKey = null
        publicKey = null
        publicKeyB64 = null
        _unlocked.value = false
    }

    private fun setKeys(pubB64: String, sk: ByteArray) {
        val pub = Envelope.unb64(pubB64)
        // Verifier: a privada aberta tem de corresponder à pública guardada —
        // deteta wraps inválidos/corrompidos/trocados.
        require(Envelope.publicFromSecret(sk).contentEquals(pub)) { "e2e_key_mismatch" }
        publicKeyB64 = pubB64
        publicKey = pub
        secretKey = sk
        _unlocked.value = true
    }
}
