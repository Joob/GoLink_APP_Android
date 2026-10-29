package co.golink.tester.data.calendar

import co.golink.tester.data.browse.BrowseRepository
import co.golink.tester.data.encryption.E2EShareService
import co.golink.tester.data.share.ShareRepository
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.calendar.EventAttachment
import co.golink.tester.network.UserEncryptionApi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anexos do Drive nos eventos. O payload só guarda {file_id, name, mime,
 * parent_id, share_url}; o ficheiro recupera-se pela listagem da pasta-mãe,
 * como ensureAttachmentLinks na web.
 */
@Singleton
class CalendarAttachments @Inject constructor(
    private val browse: BrowseRepository,
    private val shares: ShareRepository,
    private val e2eShare: E2EShareService,
    private val encryptionApi: UserEncryptionApi,
) {
    // Anexos cujo link falhou (sem acesso, não é dono…): não repetir a cada gravação.
    private val shareFailed = ConcurrentHashMap.newKeySet<String>()

    /** Ficheiros escolhidos no seletor nesta sessão (evita voltar a listar a pasta). */
    private val picked = ConcurrentHashMap<String, BrowseItem.File>()

    suspend fun listFolder(id: String?): Result<List<BrowseItem>> = runCatching {
        val out = ArrayList<BrowseItem>()
        var page = 1
        do {
            val res = browse.listFolder(id, page, perPage = 200).getOrThrow()
            out += res.items
            page++
        } while (page <= res.lastPage && page <= 20)
        out
    }

    fun remember(files: List<BrowseItem.File>) = files.forEach { picked[it.id] = it }

    private companion object {
        const val ROOT_KEY = "__root"
        const val LISTING_TTL_MS = 60_000L
    }

    fun toAttachment(file: BrowseItem.File) = EventAttachment(
        file_id = file.id,
        name = file.name,
        mime = file.mimetype.orEmpty(),
        parent_id = file.parentId,
    )

    // Listagens recentes por pasta: vários anexos da mesma pasta = uma listagem.
    private data class Listing(val at: Long, val files: Map<String, BrowseItem.File>)
    private val listings = ConcurrentHashMap<String, Listing>()

    /** Ficheiro do anexo (null se não houver acesso à pasta-mãe). */
    suspend fun findFile(a: EventAttachment): BrowseItem.File? {
        picked[a.file_id]?.let { return it }
        val folder = a.parent_id ?: ROOT_KEY
        val now = System.currentTimeMillis()
        val listing = listings[folder]?.takeIf { now - it.at < LISTING_TTL_MS } ?: run {
            val items = listFolder(a.parent_id).getOrNull() ?: return null
            Listing(now, items.filterIsInstance<BrowseItem.File>().associateBy { it.id }).also { listings[folder] = it }
        }
        return listing.files[a.file_id]?.also { picked[it.id] = it }
    }

    /**
     * Links "só ver" para os convidados. Só o dono do ficheiro consegue
     * partilhar; os restantes ficam sem link (o convidado vê só o nome).
     * Devolve os anexos (com share_url) e os share_token criados agora.
     */
    suspend fun ensureLinks(attachments: List<EventAttachment>): Pair<List<EventAttachment>, Map<String, String>> {
        val tokens = HashMap<String, String>()
        val out = attachments.map { a ->
            if (!a.share_url.isNullOrBlank() || a.file_id in shareFailed) return@map a
            val file = findFile(a)
            if (file == null) {
                shareFailed += a.file_id
                return@map a
            }
            runCatching {
                var share = file.share
                var created = false
                if (share == null) {
                    share = shares.create(file, null, null, null, null, null, null).getOrThrow()
                    created = true
                }
                val base = share.link?.takeIf { it.isNotBlank() } ?: error("sem link")
                val fragment = if (!file.encrypted) "" else {
                    val wrapped = encryptionApi.fileKey(file.id).body()?.wrapped_data_key ?: error("sem chave")
                    e2eShare.buildFileShareFragment(file.id, share.token, wrapped, file.name).fragment ?: error("sem fragmento")
                }
                picked[file.id] = file.copy(share = share)
                if (created) tokens[a.file_id] = share.token
                a.copy(share_url = base + fragment)
            }.getOrElse {
                shareFailed += a.file_id
                a
            }
        }
        return out to tokens
    }
}
