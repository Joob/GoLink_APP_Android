package co.golink.tester.data.activity

import co.golink.tester.data.encryption.E2EKeyManager
import co.golink.tester.domain.activity.ActivityEntry
import co.golink.tester.domain.activity.ActivityPage
import co.golink.tester.domain.activity.ActivityRowDto
import co.golink.tester.domain.activity.ActivitySection
import co.golink.tester.domain.activity.toEntry
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.toItem
import co.golink.tester.network.ActivityApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atividade recente / histórico por item. O servidor nunca envia nomes em claro
 * de itens cifrados — só `name_encrypted` (do item e da pasta-mãe), que se
 * decifra aqui como no BrowseRepository. Trancado: fica o placeholder.
 */
@Singleton
class ActivityRepository @Inject constructor(
    private val api: ActivityApi,
    private val keys: E2EKeyManager,
) {
    suspend fun recent(): Result<Map<ActivitySection, ActivityPage>> = runCatching {
        val response = api.recent()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        ActivitySection.entries.associateWith { section ->
            val dto = body.data[section.key]
            ActivityPage(
                entries = dto?.items.orEmpty().map { it.decEntry() },
                hasMore = dto?.has_more ?: false,
                page = dto?.page ?: 1,
            )
        }
    }

    suspend fun recentSection(section: ActivitySection, page: Int): Result<ActivityPage> = runCatching {
        val response = api.recent(section = section.key, page = page, perPage = 20)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val dto = response.body()?.data?.get(section.key)
        ActivityPage(
            entries = dto?.items.orEmpty().map { it.decEntry() },
            hasMore = dto?.has_more ?: false,
            page = dto?.page ?: page,
        )
    }

    suspend fun forItem(item: BrowseItem, page: Int): Result<ActivityPage> = runCatching {
        val type = if (item is BrowseItem.Folder) "folder" else "file"
        val response = api.item(type, item.id, page)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        ActivityPage(entries = body.data.map { it.decEntry() }, hasMore = body.has_more, page = body.page)
    }

    suspend fun feed(cursor: String?): Result<co.golink.tester.domain.activity.FeedPage> = runCatching {
        val response = api.feed(cursor = cursor)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = response.body() ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            co.golink.tester.domain.activity.FeedPage(body.data.map { it.decFeed() }, body.has_more, body.next_cursor)
        }
    }

    suspend fun summary(): Result<co.golink.tester.domain.activity.ActivitySummary> = runCatching {
        val response = api.summary()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val dto = response.body()?.data ?: error("Resposta vazia")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            // Mesmo tamanho+tipo no servidor; aqui confirma-se pelo nome (decifrado).
            val files = dto.duplicate_candidates.mapNotNull { env ->
                val dec = env.data.decrypted()
                (dec.toItem() as? BrowseItem.File)?.let { file ->
                    val parent = dec.relationships?.parent?.data?.attributes
                    file to ((keys.openNameOrNull(parent?.name_encrypted) ?: parent?.name)?.takeIf { it.isNotBlank() && it != "•" } ?: "O meu Drive")
                }
            }.filter { (f, _) -> f.name.isNotBlank() && f.name != "•" }
            val groups = files.groupBy { (f, _) -> "${f.filesizeBytes}|${f.name.lowercase()}" }
                .values.filter { it.size > 1 }
                .map { g -> co.golink.tester.domain.activity.DuplicateGroup(g.first().first.name, g.first().first.filesize, g.map { it.first }, g.map { it.second }.distinct(), g.map { it.second }) }
            co.golink.tester.domain.activity.ActivitySummary(dto.uploaded_7d, dto.folders_changed_7d, groups)
        }
    }

    private fun co.golink.tester.domain.browse.BrowseEntry.decrypted(): co.golink.tester.domain.browse.BrowseEntry {
        val plain = keys.openNameOrNull(attributes.name_encrypted)
        return if (plain != null) copy(attributes = attributes.copy(name = plain)) else this
    }

    private fun co.golink.tester.domain.activity.FeedEntryDto.decFeed(): co.golink.tester.domain.activity.FeedEntry {
        val row = ActivityRowDto(
            id = id, action = action, item_type = item_type, is_child = is_child, is_mine = is_mine,
            created_at = created_at, metadata = metadata, actor = actor, item_owner_id = item_owner_id,
            item_trashed = item_trashed, item = item,
        )
        val folderItem = folder?.data?.decrypted()?.toItem() as? BrowseItem.Folder
        val base = row.decEntry().let { e -> if (kind == "batch") e.copy(parentName = folderItem?.name ?: e.parentName) else e }
        return co.golink.tester.domain.activity.FeedEntry(
            id = id,
            kind = kind,
            category = category,
            base = base,
            count = count,
            totalBytes = total_bytes,
            types = types,
            folder = folderItem,
            folderOwnerId = folder_owner_id,
            files = items.mapNotNull { it.item?.data?.decrypted()?.toItem() },
        )
    }

    private fun ActivityRowDto.decEntry(): ActivityEntry {
        val entry = item?.data
        val decrypted = entry?.let { e ->
            val plain = keys.openNameOrNull(e.attributes.name_encrypted)
            if (plain != null) e.copy(attributes = e.attributes.copy(name = plain)) else e
        }
        val parentAttrs = entry?.relationships?.parent?.data?.attributes
        val parentName = (keys.openNameOrNull(parentAttrs?.name_encrypted) ?: parentAttrs?.name)
            ?.takeIf { it.isNotBlank() && it != "•" }
        return toEntry(decrypted?.toItem(), parentName)
    }
}
