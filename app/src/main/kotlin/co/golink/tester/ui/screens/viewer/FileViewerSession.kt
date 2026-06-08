package co.golink.tester.ui.screens.viewer

import co.golink.tester.domain.browse.BrowseItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileViewerSession @Inject constructor() {
    var files: List<BrowseItem.File> = emptyList()
    var startId: String? = null
}
