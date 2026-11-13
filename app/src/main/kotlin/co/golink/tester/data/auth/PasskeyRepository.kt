package co.golink.tester.data.auth

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import co.golink.tester.domain.auth.AuthError
import co.golink.tester.network.PasskeyApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** Passkey da conta (Definições › Segurança). */
data class PasskeyInfo(
    val id: String,
    val name: String,
    val synced: Boolean,
    val createdAt: String?,
    val lastUsedAt: String?,
)

/** Erros próprios das passkeys, além dos AuthError do servidor. */
sealed class PasskeyError(message: String) : Exception(message) {
    /** O utilizador fechou o seletor: não é erro a mostrar. */
    class Cancelled : PasskeyError("cancelled")
    class NoPasskey : PasskeyError("Não há passkeys neste aparelho")
    class Duplicate : PasskeyError("Este aparelho já tem uma passkey para a tua conta")
    /** 423: é preciso confirmar com a password (ou outra passkey) antes. */
    class ConfirmationRequired : PasskeyError("Confirma a tua identidade")
    /** 423 em conta sem password (login social): é preciso um login recente. */
    class ReauthRequired : PasskeyError("Por segurança, termina sessão e volta a entrar; depois adiciona a passkey nos 15 minutos seguintes.")
    class Unavailable(message: String) : PasskeyError(message)
    /** A passkey escolhida foi apagada na conta mas continua no gestor de passwords. */
    class UnknownOnServer : PasskeyError("Esta passkey já não existe na tua conta. Remove-a do gestor de passwords e entra de outra forma.")
}

/**
 * Passkeys via Credential Manager. O [Context] tem de ser a Activity (o
 * seletor de credenciais abre por cima dela), por isso vem de cada chamada.
 */
@Singleton
class PasskeyRepository @Inject constructor(
    private val api: PasskeyApi,
    private val tokenStore: TokenStore,
    private val errorParser: ApiErrorParser,
    private val json: Json,
) {
    /** Entrar com passkey: sem OTP (a passkey já é posse + biometria/PIN). */
    suspend fun signIn(activity: Context): Result<Unit> = co.golink.tester.data.net.netCatching {
        val options = publicKey(api.loginOptions())
        val credential = getAssertion(activity, options)

        val response = api.loginVerify(buildJsonObject {
            put("credential", credential)
            put("token", true)
        })
        if (!response.isSuccessful) throw parseError(response)

        val token = response.body()?.get("data")?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
            ?: throw AuthError.Unknown("Sem token na resposta")
        // Passkey dispensa o OTP: uma só escrita e uma só emissão (sem passar por OtpRequired).
        tokenStore.setToken(token, otpValidated = true)
    }.recoverCatching { throw map(it) }

    suspend fun list(): Result<List<PasskeyInfo>> = co.golink.tester.data.net.netCatching {
        val response = api.list()
        if (!response.isSuccessful) throw errorParser.parse(response)
        response.body()?.get("data")?.jsonArray.orEmpty().map { el ->
            val o = el.jsonObject
            PasskeyInfo(
                id = o.str("id").orEmpty(),
                name = o.str("name").orEmpty(),
                synced = o["synced"]?.jsonPrimitive?.booleanOrNull == true,
                createdAt = o.str("created_at"),
                lastUsedAt = o.str("last_used_at"),
            )
        }
    }.recoverCatching { throw map(it) }

    /**
     * Cria uma passkey neste aparelho. Precisa de [password] ou de
     * [passkeyToken] (confirmação com outra passkey); contas só com login
     * social passam sem nenhum.
     */
    suspend fun register(
        activity: Context,
        password: String?,
        passkeyToken: String?,
        deviceName: String?,
    ): Result<Unit> = co.golink.tester.data.net.netCatching {
        // O servidor valida a confirmação no options e gasta-a no store:
        // cancelar o diálogo do sistema não obriga a confirmar outra vez.
        val confirmation = confirmationBody(password, passkeyToken)
        val optionsResponse = api.registerOptions(confirmation)
        if (optionsResponse.code() == 423) {
            throw if (optionsResponse.peekFlag("reauth_required")) PasskeyError.ReauthRequired()
            else PasskeyError.ConfirmationRequired()
        }
        val options = publicKey(optionsResponse)

        val registration = try {
            val result = CredentialManager.create(activity).createCredential(
                activity,
                CreatePublicKeyCredentialRequest(options.toString()),
            ) as CreatePublicKeyCredentialResponse
            json.parseToJsonElement(result.registrationResponseJson).jsonObject
        } catch (e: CreateCredentialCancellationException) {
            throw PasskeyError.Cancelled()
        } catch (e: CreatePublicKeyCredentialDomException) {
            // InvalidStateError = credencial excluída (já registada neste aparelho).
            if (e.domError is InvalidStateError) throw PasskeyError.Duplicate()
            throw PasskeyError.Unavailable(e.errorMessage?.toString() ?: "Não foi possível criar a passkey")
        } catch (e: CreateCredentialException) {
            throw PasskeyError.Unavailable(e.errorMessage?.toString() ?: "Não foi possível criar a passkey")
        }

        val response = api.register(buildJsonObject {
            confirmation.forEach { (k, v) -> put(k, v) }
            put("credential", registration)
            if (!deviceName.isNullOrBlank()) put("name", deviceName.take(80))
        })
        if (!response.isSuccessful) {
            if (response.code() == 423) {
                throw if (response.peekFlag("reauth_required")) PasskeyError.ReauthRequired()
                else PasskeyError.ConfirmationRequired()
            }
            // O corpo de erro só se lê uma vez: a flag e o parser usam a mesma cópia
            // (senão a password errada / token expirado perdia a mensagem do servidor).
            val (raw, flags) = response.readErrorOnce()
            if (response.code() == 422 && flags?.get("duplicate")?.jsonPrimitive?.booleanOrNull == true) throw PasskeyError.Duplicate()
            throw errorParser.parse(Response.error<JsonObject>(response.code(), raw.toResponseBody(response.errorBody()?.contentType())))
        }
    }.recoverCatching { throw map(it) }

    suspend fun rename(id: String, name: String): Result<Unit> = co.golink.tester.data.net.netCatching {
        val response = api.rename(id, buildJsonObject { put("name", name.trim().take(80)) })
        if (!response.isSuccessful) throw errorParser.parse(response)
    }.recoverCatching { throw map(it) }

    /**
     * Remove uma passkey. Como juntar, exige [password] ou [passkeyToken]
     * (finalidade remove_passkey); contas sociais passam com login recente.
     */
    suspend fun delete(id: String, password: String? = null, passkeyToken: String? = null): Result<Unit> = co.golink.tester.data.net.netCatching {
        val response = api.delete(id, confirmationBody(password, passkeyToken))
        if (response.code() == 423) {
            throw if (response.peekFlag("reauth_required")) PasskeyError.ReauthRequired()
            else PasskeyError.ConfirmationRequired()
        }
        if (!response.isSuccessful) throw errorParser.parse(response)
    }.recoverCatching { throw map(it) }

    private fun confirmationBody(password: String?, passkeyToken: String?): JsonObject = buildJsonObject {
        if (!passkeyToken.isNullOrBlank()) put("passkey_token", passkeyToken)
        else if (!password.isNullOrBlank()) put("password", password)
    }

    /**
     * Confirma a identidade com uma passkey da conta e devolve o
     * passkey_token (5 min) que substitui a password no pedido sensível.
     */
    suspend fun confirm(activity: Context, purpose: String): Result<String> = co.golink.tester.data.net.netCatching {
        val options = publicKey(api.confirmOptions())
        val credential = getAssertion(activity, options)

        val response = api.confirm(buildJsonObject {
            put("credential", credential)
            // O token só serve para esta ação (password, email, delete_account, add_passkey, remove_passkey).
            put("purpose", purpose)
        })
        if (!response.isSuccessful) throw parseError(response)
        response.body()?.str("passkey_token") ?: throw AuthError.Unknown("Sem passkey_token")
    }.recoverCatching { throw map(it) }

    private suspend fun getAssertion(activity: Context, options: JsonObject): JsonObject = try {
        val result = CredentialManager.create(activity).getCredential(
            activity,
            GetCredentialRequest(listOf(GetPublicKeyCredentialOption(options.toString()))),
        )
        val credential = result.credential as? PublicKeyCredential
            ?: throw PasskeyError.Unavailable("Credencial inesperada")
        json.parseToJsonElement(credential.authenticationResponseJson).jsonObject
    } catch (e: GetCredentialCancellationException) {
        throw PasskeyError.Cancelled()
    } catch (e: NoCredentialException) {
        throw PasskeyError.NoPasskey()
    } catch (e: GetCredentialException) {
        throw PasskeyError.Unavailable(e.errorMessage?.toString() ?: "Passkey indisponível")
    }

    /** { "publicKey": {...} } → as opções WebAuthn. */
    private fun publicKey(response: Response<JsonObject>): JsonObject {
        if (!response.isSuccessful) throw errorParser.parse(response)
        return response.body()?.get("publicKey")?.jsonObject
            ?: throw AuthError.Unknown("Opções de passkey ausentes")
    }

    /** 422 passkey_failed → mensagem própria; resto (suspensa, email) via parser. */
    private fun parseError(response: Response<JsonObject>): Throwable {
        // O corpo de erro só se lê uma vez: guarda-se para a flag e o parser.
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val body = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (response.code() == 422 && body?.get("unknown_credential")?.jsonPrimitive?.booleanOrNull == true) {
            return PasskeyError.UnknownOnServer()
        }
        val error = errorParser.parse(Response.error<JsonObject>(response.code(), raw.toResponseBody(response.errorBody()?.contentType())))
        if (response.code() == 422 && error.message?.contains("passkey could not be verified", ignoreCase = true) == true) {
            return AuthError.InvalidCredentials("A passkey não foi aceite")
        }
        return error
    }

    private fun Response<*>.readErrorOnce(): Pair<String, JsonObject?> {
        val raw = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
        return raw to runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    private fun Response<*>.peekFlag(name: String): Boolean = runCatching {
        val raw = errorBody()?.string().orEmpty()
        json.parseToJsonElement(raw).jsonObject[name]?.jsonPrimitive?.booleanOrNull == true
    }.getOrDefault(false)

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun map(t: Throwable): Throwable = if (t is PasskeyError) t else errorParser.fromThrowable(t)
}
