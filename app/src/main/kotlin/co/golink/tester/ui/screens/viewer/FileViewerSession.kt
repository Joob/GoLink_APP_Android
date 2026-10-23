package co.golink.tester.ui.screens.viewer

import co.golink.tester.ui.i18n.tr
import co.golink.tester.domain.browse.BrowseItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileViewerSession @Inject constructor() {
    var files: List<BrowseItem.File> = emptyList()
    var startId: String? = null

    /**
     * Ficheiro de texto acabado de criar pelo menu "+": o viewer abre já em
     * modo de edição. Consumido uma vez, na primeira carga do texto.
     */
    var startInEditMode: Boolean = false

    /** Aberto a partir do Lixo: só ver (sem editar, mover, partilhar ou renomear). */
    var fromTrash: Boolean = false

    /**
     * Editor de documentos: última content_revision gravada por ficheiro. A
     * lista de onde se abre pode ainda ter a antiga — sem isto a gravação
     * seguinte dava um falso conflito (409).
     */
    val contentRevisions: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()
}
