package com.aklzo.videoplayer.domain

import com.aklzo.videoplayer.data.source.drive.DriveFile

/**
 * ドライブ全体のフォルダ一覧から親子関係を組み立て、
 * 「選択フォルダ配下(自身の直下+子孫フォルダ内)のファイル」だけに絞る純粋ロジック。
 * フォルダ数が多くても API 呼び出し回数に影響しない(→ docs/md/design/decisions.md)。
 */
object DriveFolderTree {

    private const val MAX_HOPS = 100

    fun filterUnder(
        files: List<DriveFile>,
        folders: List<DriveFile>,
        rootFolderId: String,
    ): List<DriveFile> {
        val parentOf: Map<String, String?> =
            folders.associate { it.id to it.parents.firstOrNull() }

        return files.filter { file ->
            var current = file.parents.firstOrNull()
            var hops = 0
            // 親を辿って選択フォルダに到達すれば配下。循環・過深はカウンタで打ち切る
            while (current != null && hops < MAX_HOPS) {
                if (current == rootFolderId) return@filter true
                current = parentOf[current]
                hops++
            }
            false
        }
    }

    /**
     * 選択フォルダからの相対フォルダパスを解決する関数を返す。
     * 引数はファイルの親フォルダ ID。選択フォルダ直下なら空文字、
     * 選択フォルダ配下に到達できなければ null。
     * 例: 選択=suzuminemo、ファイルが suzuminemo/streams/20260420_x/ 内 → "streams/20260420_x"
     */
    fun pathResolver(
        folders: List<DriveFile>,
        rootFolderId: String,
    ): (parentFolderId: String?) -> String? {
        val folderById = folders.associateBy { it.id }
        return resolver@{ parentFolderId ->
            if (parentFolderId == null) return@resolver null
            val names = mutableListOf<String>()
            var current: String? = parentFolderId
            var hops = 0
            while (current != null && hops < MAX_HOPS) {
                if (current == rootFolderId) return@resolver names.reversed().joinToString("/")
                val folder = folderById[current] ?: return@resolver null
                names += folder.name
                current = folder.parents.firstOrNull()
                hops++
            }
            null
        }
    }
}
