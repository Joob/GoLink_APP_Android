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
    private val backendUrl: co.golink.tester.data.config.BackendUrlHolder,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    // Tem E2E configurada no servidor. Serve para decidir se escondemos a
    // tabela e bloqueamos a navegação (só quando configurado E trancado).
    private val _configured = MutableStateFlow(false)
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

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
    suspend fun isConfigured(): Boolean = (api.get().body()?.configured == true).also { _configured.value = it }

    /**
     * Como [configured], mas seguro em processos sem UI (ex.: backup automático):
     * o flag só é preenchido pelo gate, por isso num worker arrancado a frio vinha
     * `false` e um "não está configurado" falso deixava passar uploads em claro.
     * Confirma no servidor quando ainda não sabemos. Em erro de rede assume
     * configurado — falhar o upload é recuperável; enviar em claro não é.
     */
    suspend fun isConfiguredOrUnknown(): Boolean =
        if (_configured.value || isUnlocked) true
        else runCatching { isConfigured() }.getOrDefault(true)

    /**
     * Setup 1x (devolve a recovery key formatada) ou unlock com o segredo
     * (password normal ou passphrase E2E). Devolve null no unlock.
     */
    suspend fun setupOrUnlock(secret: String): String? {
        // Distinguir falha de rede/sessão de passphrase errada: um 401/500 vinha
        // com body() a null e acabava a ser mostrado como "segredo errado".
        val resp = api.get()
        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code()}")
        val body = resp.body() ?: throw java.io.IOException("resposta vazia do servidor")

        if (body.configured) {
            val salt = Envelope.unb64(body.pwd_salt!!)
            val wrapped = Envelope.unb64(body.wrapped_sk_password!!)
            val nonce = Envelope.unb64(body.wrapped_sk_password_nonce!!)
            val alg = body.kdf_alg ?: Envelope.KDF_ALG_ARGON2
            val key = Envelope.deriveKey(secret, salt, body.kdf_ops!!, body.kdf_mem!!, alg)
            val sk = runCatching { Envelope.unwrapSecretKey(wrapped, nonce, key) }.getOrElse {
                // Antes de culpar a passphrase, confirmar que o secretbox funciona
                // de todo: se o R8 voltar a partir a ligação nativa ao libsodium,
                // nenhuma passphrase abriria e o erro seria enganador.
                if (!Envelope.selfTestSecretBox()) {
                    error("e2e_sodium_broken: secretbox não faz round-trip neste dispositivo")
                }
                android.util.Log.w(
                    "E2EUnlock",
                    "unwrap falhou: alg=$alg ops=${body.kdf_ops} mem=${body.kdf_mem} " +
                        "salt=${salt.size}B wrap=${wrapped.size}B nonce=${nonce.size}B",
                )
                throw it
            }
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

        // SELF-TEST antes de registar (igual à Web): simula o unlock completo com
        // os MESMOS parâmetros que vão ser gravados. Se algum passo falhar, o
        // setup aborta aqui — nunca se guarda no servidor uma chave que depois
        // não abre com a passphrase, que é irrecuperável sem a recovery key.
        val rederived = Envelope.deriveKey(secret, salt, ops, mem, alg)
        check(rederived.contentEquals(pwKey)) { "e2e_selftest: KDF não determinístico ($alg)" }
        val reopened = runCatching {
            Envelope.unwrapSecretKey(wrappedPw.ciphertext, wrappedPw.nonce, rederived)
        }.getOrNull()
        check(reopened != null && reopened.contentEquals(kp.secretKey)) {
            "e2e_selftest: o wrap por passphrase não reabre"
        }
        val reopenedRec = runCatching {
            Envelope.unwrapSecretKey(wrappedRec.ciphertext, wrappedRec.nonce, recovery)
        }.getOrNull()
        check(reopenedRec != null && reopenedRec.contentEquals(kp.secretKey)) {
            "e2e_selftest: o wrap por recovery não reabre"
        }
        check(Envelope.publicFromSecret(kp.secretKey).contentEquals(kp.publicKey)) {
            "e2e_selftest: par de chaves inconsistente"
        }

        val pubB64 = Envelope.b64(kp.publicKey)
        val setupResp = api.setup(
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
        check(setupResp.isSuccessful) { "setup falhou: HTTP ${setupResp.code()}" }

        setKeys(pubB64, kp.secretKey)
        return Envelope.formatRecovery(recovery)
    }

    /** "Esqueci a password": desbloqueia com a recovery key. */
    suspend fun recoverWithKey(recoveryKey: String) {
        val resp = api.recovery()
        if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code()}")
        val body = resp.body() ?: throw java.io.IOException("resposta vazia do servidor")
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
        // Self-test: o novo wrap tem de reabrir antes de o gravar. Se falhasse,
        // a passphrase antiga deixava de servir e a nova nunca abriria.
        val reopened = runCatching {
            Envelope.unwrapSecretKey(wrapped.ciphertext, wrapped.nonce, Envelope.deriveKey(secret, salt, ops, mem, alg))
        }.getOrNull()
        check(reopened != null && reopened.contentEquals(sk)) {
            "e2e_selftest: o novo wrap não reabre — rotação abortada"
        }
        val resp = api.updateSecret(
            UpdateSecretBody(
                wrapped_sk_password = Envelope.b64(wrapped.ciphertext),
                wrapped_sk_password_nonce = Envelope.b64(wrapped.nonce),
                pwd_salt = Envelope.b64(salt),
                kdf_ops = ops,
                kdf_mem = mem,
                kdf_alg = alg,
            )
        )
        // Sem isto, um 4xx/5xx passava despercebido e o utilizador ficava
        // convencido de que a passphrase tinha mudado.
        check(resp.isSuccessful) { "rotação falhou: HTTP ${resp.code()}" }
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

    // ---- nomes cifrados (Fase 2) ----
    /** Capaz de cifrar nomes: unlocked (par de chaves em memória). */
    val canSealNames: Boolean get() = publicKey != null && secretKey != null

    /** Sela um nome à própria chave pública. Devolve base64 ou null se trancado. */
    fun sealName(name: String): String? {
        val pub = publicKey ?: return null
        return Envelope.b64(Envelope.sealName(name, pub))
    }

    /** Abre um nome cifrado (base64) com o próprio par. Null se ausente/falhar. */
    fun openNameOrNull(nameEncryptedB64: String?): String? {
        if (nameEncryptedB64.isNullOrEmpty()) return null
        val pub = publicKey ?: return null
        val sec = secretKey ?: return null
        return runCatching { Envelope.openName(Envelope.unb64(nameEncryptedB64), pub, sec) }.getOrNull()
    }

    /**
     * E2E Fase 2 — migração de nomes: re-cifra em background os nomes ainda em
     * claro (itens do próprio utilizador). Corre por lotes até não sobrar nada.
     * Silencioso — qualquer erro (rede/endpoint) apenas termina; tenta no próximo
     * unlock. Chamar de um coroutine scope (ex.: viewModelScope, Dispatchers.IO).
     */
    suspend fun migrateNames() {
        if (!canSealNames) return
        // Não cruzar com a migração dos FICHEIROS (bucket antigo → E2E): os nomes
        // só começam depois dessa passagem terminar. Espera em fundo até acabar.
        var waits = 0
        while (true) {
            val status = runCatching { api.migrationStatus().body() }.getOrNull()
                ?: return // sem estado fiável → tenta no próximo unlock
            if (!status.in_progress) break
            if (++waits >= 240) return
            kotlinx.coroutines.delay(15_000)
            if (!canSealNames) return
        }
        repeat(2000) {
            val resp = runCatching { api.plainNames() }.getOrNull() ?: return
            if (!resp.isSuccessful) return
            val body = resp.body() ?: return
            if (body.files.isEmpty() && body.folders.isEmpty()) return

            val outFiles = body.files.mapNotNull { f ->
                sealName(f.name)?.let { co.golink.tester.domain.encryption.EncryptedNameItem(f.id, it) }
            }
            val outFolders = body.folders.mapNotNull { f ->
                sealName(f.name)?.let { co.golink.tester.domain.encryption.EncryptedNameItem(f.id, it) }
            }
            if (outFiles.isEmpty() && outFolders.isEmpty()) return

            val post = runCatching {
                api.encryptNames(co.golink.tester.domain.encryption.EncryptNamesBody(outFiles, outFolders))
            }.getOrNull() ?: return
            if (!post.isSuccessful) return
        }
    }

    // Callbacks de limpeza no lock (ex.: caches de nomes decifrados noutros
    // singletons). Registados uma vez; corridos em todos os locks.
    private val onLockCallbacks = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addOnLock(callback: () -> Unit) {
        onLockCallbacks.add(callback)
    }

    fun lock() {
        // Zera os bytes da privada antes de largar a referência (defesa contra
        // memory-scraping) — em vez de esperar pelo GC.
        secretKey?.fill(0)
        secretKey = null
        publicKey = null
        publicKeyB64 = null
        _unlocked.value = false
        _configured.value = false
        onLockCallbacks.forEach { runCatching { it() } }
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
        _configured.value = true
    }
}
