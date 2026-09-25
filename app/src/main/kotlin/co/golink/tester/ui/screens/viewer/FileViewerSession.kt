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
}
