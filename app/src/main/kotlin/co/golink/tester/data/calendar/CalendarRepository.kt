package co.golink.tester.data.calendar

import co.golink.tester.data.encryption.CalendarCrypto
import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.calendar.CalEvent
import co.golink.tester.domain.calendar.CalInfo
import co.golink.tester.domain.calendar.CalendarDates
import co.golink.tester.domain.calendar.CalendarDto
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.domain.calendar.EventDto
import co.golink.tester.domain.calendar.EventPayload
import co.golink.tester.domain.calendar.EventResponse
import co.golink.tester.domain.calendar.LookupKey
import co.golink.tester.domain.calendar.LookupKeysRequest
import co.golink.tester.domain.calendar.RepeatRule
import co.golink.tester.network.CalendarApi
import co.golink.tester.ui.i18n.I18n
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import retrofit2.Response

/** O servidor gravou outra versão entretanto (409); [fresh] é a atual, já decifrada. */
class EventConflictException(val fresh: CalEvent) : Exception("conflict")

/** Partilha: o email não tem conta com E2E ativo. */
class CalendarShareNotFoundException : Exception("not_found")

/**
 * Formulário do evento. Dia inteiro: [start] = meia-noite local do 1.º dia e
 * [end] = meia-noite local do dia a seguir ao último (fim exclusivo).
 */
data class EventForm(
    val calendarId: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val timezone: String,
    val repeat: String,
    val repeatUntil: String?,
    val rule: RepeatRule = RepeatRule(),
    val alerts: List<Int>,
    val guestsCanEdit: Boolean,
    val title: String,
    val location: String,
    val notes: String,
    val attachments: List<EventAttachment>,
    /** share_token por file_id (vai só em files[], nunca no payload). */
    val shareTokens: Map<String, String> = emptyMap(),
    val guests: List<String>,
)

/**
 * Calendário E2E — espelho de store/modules/calendar.js. O servidor só vê
 * ciphertext: nomes dos calendários (K_cal) e conteúdo dos eventos (K_ev).
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val api: CalendarApi,
    private val keys: E2EKeyManager,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    // Igual ao JSON.stringify da web: todos os campos, parent_id null incluído.
    private val payloadJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true; explicitNulls = true }

    private val _calendars = MutableStateFlow<List<CalInfo>>(emptyList())
    val calendars: StateFlow<List<CalInfo>> = _calendars

    /** Algo mudou (evento gravado/apagado, calendário…): quem mostra eventos recarrega. */
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    private val bootstrap = Mutex()
    private var bootstrapped = false

    init {
        // As K_cal decifradas não sobrevivem ao lock.
        // Também volta a criar os de defeito para a próxima conta (logout → login).
        keys.addOnLock {
            _calendars.value = emptyList()
            bootstrapped = false
        }
    }

    fun calendarById(id: String?): CalInfo? = _calendars.value.firstOrNull { it.id == id }

    private fun <T> Response<T>.ok(): T {
        check(isSuccessful) { "HTTP ${code()}: ${errorBody()?.string()?.take(300)}" }
        return body() ?: error("Resposta vazia")
    }

    private fun Response<*>.okUnit() {
        check(isSuccessful) { "HTTP ${code()}: ${errorBody()?.string()?.take(300)}" }
    }

    // ---- calendários ---------------------------------------------------------

    suspend fun loadCalendars(): Result<List<CalInfo>> = runCatching {
        withContext(Dispatchers.IO) {
            var list = api.calendars().ok().data
            // Primeira visita: os calendários de defeito são criados pelo cliente
            // (o servidor não consegue cifrar os nomes). Uma vez por sessão.
            if (list.isEmpty() && keys.isUnlocked) {
                bootstrap.withLock {
                    if (!bootstrapped) {
                        createDefaults()
                        bootstrapped = true
                    }
                }
                list = api.calendars().ok().data
            }
            val decrypted = list.map(::decryptCalendar)
            _calendars.value = decrypted
            decrypted
        }
    }

    private suspend fun createDefaults() {
        val defaults = listOf(
            Triple("Pessoal", "green", "personal"),
            Triple("Trabalho", "blue", "personal"),
            Triple("Família", "pink", "personal"),
            Triple("Aniversários", "slate", "birthdays"),
        )
        defaults.forEachIndexed { i, (name, color, kind) ->
            val key = CalendarCrypto.genKey()
            api.createCalendar(buildJsonObject {
                put("kind", kind)
                put("color", color)
                put("position", i)
                put("name_encrypted", CalendarCrypto.encryptString(I18n.t(name), key))
                put("wrapped_key", keys.sealForSelf(key))
            }).okUnit()
        }
    }

    private fun decryptCalendar(raw: CalendarDto): CalInfo {
        val key = runCatching { raw.wrapped_key?.let(keys::openFileDataKey) }.getOrNull()
        val name = key?.let { k -> runCatching { CalendarCrypto.decryptString(raw.name_encrypted.orEmpty(), k) }.getOrNull() }
        return CalInfo(
            id = raw.id,
            ownerId = raw.owner_id,
            kind = raw.kind,
            color = raw.color,
            name = name ?: "🔒",
            position = raw.position,
            permission = raw.permission,
            visible = raw.visible,
            members = raw.members,
            locked = name == null,
            key = if (name == null) null else key,
        )
    }

    suspend fun createCalendar(name: String, color: String): Result<Unit> = runCatching {
        val key = CalendarCrypto.genKey()
        api.createCalendar(buildJsonObject {
            put("color", color)
            put("name_encrypted", CalendarCrypto.encryptString(name, key))
            put("wrapped_key", keys.sealForSelf(key))
        }).okUnit()
        loadCalendars().getOrThrow()
        _changes.tryEmit(Unit)
    }

    suspend fun updateCalendar(id: String, name: String? = null, color: String? = null, visible: Boolean? = null): Result<Unit> {
        val cal = calendarById(id) ?: return Result.failure(IllegalStateException("calendário desconhecido"))
        // Sem K_cal o nome não se cifra: não o mostrar como mudado.
        if (name != null && cal.key == null) return Result.failure(IllegalStateException("calendário bloqueado"))
        // Otimista (a checkbox da gaveta muda logo); qualquer falha repõe.
        _calendars.value = _calendars.value.map {
            if (it.id != id) it else it.copy(
                name = name ?: it.name,
                color = color ?: it.color,
                visible = visible ?: it.visible,
            )
        }
        return runCatching {
            val body = buildJsonObject {
                visible?.let { put("visible", it) }
                color?.let { put("color", it) }
                if (name != null && cal.key != null) put("name_encrypted", CalendarCrypto.encryptString(name, cal.key))
            }
            api.updateCalendar(id, body).okUnit()
        }.onFailure {
            _calendars.value = _calendars.value.map { if (it.id == id) cal else it }
        }
    }

    suspend fun deleteCalendar(id: String): Result<Unit> = runCatching {
        api.deleteCalendar(id).okUnit()
        _calendars.value = _calendars.value.filterNot { it.id == id }
        _changes.tryEmit(Unit)
    }

    suspend fun lookupKeys(emails: List<String>): Map<String, LookupKey?> {
        if (emails.isEmpty()) return emptyMap()
        return api.lookupKeys(LookupKeysRequest(emails)).ok().data
    }

    /** Partilha com um utilizador registado (sela K_cal à pubkey dele). */
    suspend fun shareCalendar(id: String, email: String, permission: String): Result<Unit> = runCatching {
        val cal = calendarById(id) ?: error("calendário desconhecido")
        val calKey = cal.key ?: error("calendário bloqueado")
        val found = lookupKeys(listOf(email.trim().lowercase())).values.firstOrNull()
            ?: throw CalendarShareNotFoundException()
        api.addMember(id, buildJsonObject {
            put("user_id", found.user_id)
            put("permission", permission)
            put("wrapped_key", keys.sealForPublicKey(calKey, found.public_key))
        }).okUnit()
        loadCalendars().getOrThrow()
    }

    suspend fun removeMember(id: String, userId: String): Result<Unit> = runCatching {
        api.removeMember(id, userId).okUnit()
        loadCalendars().getOrThrow()
    }

    // ---- eventos ---------------------------------------------------------------

    suspend fun fetchEvents(from: Instant, to: Instant): Result<List<CalEvent>> = runCatching {
        withContext(Dispatchers.IO) {
            api.events(from.toString(), to.toString()).ok().data.map(::decryptEvent)
        }
    }

    suspend fun fetchEvent(id: String): Result<CalEvent> = runCatching {
        withContext(Dispatchers.IO) {
            decryptEvent(api.event(id).ok().data ?: error("Resposta vazia"))
        }
    }

    /** Abre K_ev (membro: key_cal com K_cal; convidado: selada à sua pubkey). */
    private fun eventKey(raw: EventDto): ByteArray? {
        raw.key_cal?.let { wrapped ->
            calendarById(raw.calendar_id)?.key?.let { calKey ->
                runCatching { CalendarCrypto.unwrapKey(wrapped, calKey) }.getOrNull()?.let { return it }
            }
        }
        return raw.me?.wrapped_key?.let { runCatching { keys.openFileDataKey(it) }.getOrNull() }
    }

    private fun decryptEvent(raw: EventDto): CalEvent = decryptWithKey(raw, eventKey(raw))

    private fun decryptWithKey(raw: EventDto, key: ByteArray?): CalEvent {
        val data = key?.let { k ->
            runCatching { payloadJson.decodeFromString(EventPayload.serializer(), CalendarCrypto.decryptString(raw.payload.orEmpty(), k)) }.getOrNull()
        }
        val zone = ZoneId.systemDefault()
        return CalEvent(
            raw = raw,
            start = if (raw.all_day) CalendarDates.dayStart(CalendarDates.allDayFromIso(raw.starts_at), zone) else CalendarDates.parseInstant(raw.starts_at),
            end = if (raw.all_day) CalendarDates.dayStart(CalendarDates.allDayFromIso(raw.ends_at), zone) else CalendarDates.parseInstant(raw.ends_at),
            data = data ?: EventPayload(),
            locked = data == null,
            key = if (data == null) null else key,
        )
    }

    /** Cria ou atualiza (como saveEvent da web). 409 → [EventConflictException]. */
    suspend fun saveEvent(event: CalEvent?, form: EventForm): Result<CalEvent> = runCatching {
        withContext(Dispatchers.IO) {
            val key = event?.key ?: CalendarCrypto.genKey()
            val calendar = calendarById(form.calendarId)

            val existing = event?.raw?.guests.orEmpty().map { it.email.lowercase() }.toSet()
            val guests = form.guests.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            val newEmails = guests.filterNot { it in existing }

            // Convidados e anexos são de quem gere o evento (o servidor ignora o resto).
            val manages = event == null || event.canManage

            val payload = CalendarCrypto.encryptString(
                payloadJson.encodeToString(
                    EventPayload.serializer(),
                    EventPayload(form.title, form.location, form.notes, form.attachments),
                ),
                key,
            )

            val zone = ZoneId.systemDefault()
            fun dayIso(i: Instant) = CalendarDates.allDayToIso(i.atZone(zone).toLocalDate())

            val body = buildJsonObject {
                // Dia inteiro vai como data (meia-noite UTC), igual em todos os fusos.
                put("starts_at", if (form.allDay) dayIso(form.start) else form.start.toString())
                put("ends_at", if (form.allDay) dayIso(form.end) else form.end.toString())
                put("all_day", form.allDay)
                put("timezone", form.timezone)
                put("repeat", form.repeat)
                put("repeat_until", form.repeatUntil?.let { JsonPrimitive(it) } ?: JsonNull)
                put("repeat_interval", form.rule.interval)
                put("repeat_days", if (form.repeat == "weekly" && form.rule.days.isNotEmpty()) JsonArray(form.rule.days.map { JsonPrimitive(it) }) else JsonNull)
                put("repeat_count", form.rule.count?.let { JsonPrimitive(it) } ?: JsonNull)
                put("alerts", JsonArray(form.alerts.map { JsonPrimitive(it) }))
                put("payload", payload)

                if (manages) {
                    put("files", buildJsonArray {
                        form.attachments.forEach { a ->
                            add(buildJsonObject {
                                put("file_id", a.file_id)
                                put("share_token", form.shareTokens[a.file_id]?.let { JsonPrimitive(it) } ?: JsonNull)
                            })
                        }
                    })
                    put("guests_can_edit", form.guestsCanEdit)
                }

                // Criar ou mudar de calendário: K_ev embrulhada com o K_cal de destino.
                if (event == null || (event.canManage && form.calendarId != event.calendarId)) {
                    val calKey = calendar?.key ?: error("calendário bloqueado")
                    put("calendar_id", form.calendarId)
                    put("key_cal", CalendarCrypto.wrapKey(key, calKey))
                }

                val sameGuests = !manages || (event != null && newEmails.isEmpty() && guests.size == event.raw.guests.size)
                if (!sameGuests) {
                    val found = lookupKeys(newEmails)
                    val links = mutableMapOf<String, String>()
                    put("guests", buildJsonArray {
                        guests.forEach { email ->
                            if (email in existing) {
                                add(buildJsonObject { put("email", email) })
                                return@forEach
                            }
                            val pub = found[email]
                            add(buildJsonObject {
                                put("id", UUID.randomUUID().toString())
                                put("email", email)
                                if (pub != null) put("wrapped_key", keys.sealForPublicKey(key, pub.public_key))
                            })
                            // Só o fragmento: o servidor monta o URL com APP_URL.
                            if (pub == null) links[email] = CalendarCrypto.keyToFragment(key)
                        }
                    })
                    putJsonObject("guest_links") { links.forEach { (k, v) -> put(k, v) } }
                }

                if (event != null) put("version", event.raw.version)
            }

            val response = if (event != null) api.updateEvent(event.id, body) else api.createEvent(body)
            if (response.code() == 409) throw conflict(response)
            val saved = decryptWithKey(response.ok().data ?: error("Resposta vazia"), key)
            _changes.tryEmit(Unit)
            saved
        }
    }

    private fun conflict(response: Response<EventResponse>): Exception {
        val fresh = runCatching {
            json.decodeFromString(EventResponse.serializer(), response.errorBody()?.string().orEmpty()).data
        }.getOrNull() ?: return IllegalStateException("HTTP 409")
        return EventConflictException(decryptEvent(fresh))
    }

    suspend fun deleteEvent(id: String): Result<Unit> = runCatching {
        api.deleteEvent(id).okUnit()
        _changes.tryEmit(Unit)
    }

    suspend fun rsvp(event: CalEvent, status: String): Result<CalEvent> = runCatching {
        val raw = api.rsvp(event.id, buildJsonObject { put("status", status) }).ok().data ?: error("Resposta vazia")
        _changes.tryEmit(Unit)
        decryptWithKey(raw, event.key)
    }

    /** País dos feriados no perfil (vale em todos os aparelhos); null = automático. */
    suspend fun setHolidayCountry(code: String?): Result<Unit> = runCatching {
        api.updateUserSetting(buildJsonObject {
            put("name", "holiday_country")
            put("value", code?.let { JsonPrimitive(it) } ?: JsonNull)
        }).okUnit()
    }

    /**
     * Eventos para a pesquisa local (título/local/notas): 12 meses para trás e
     * para a frente (o servidor limita a 400 dias por pedido), em paralelo.
     * Carregado uma vez por sessão de pesquisa; o filtro é feito por quem chama.
     */
    suspend fun searchPool(around: LocalDate): Result<List<CalEvent>> = runCatching {
        val zone = ZoneId.systemDefault()
        kotlinx.coroutines.coroutineScope {
            val a = async { fetchEvents(CalendarDates.dayStart(around.minusMonths(12), zone), CalendarDates.dayStart(around, zone)).getOrThrow() }
            val b = async { fetchEvents(CalendarDates.dayStart(around, zone), CalendarDates.dayStart(around.plusMonths(12), zone)).getOrThrow() }
            (a.await() + b.await()).distinctBy { it.id }
        }
    }
}
