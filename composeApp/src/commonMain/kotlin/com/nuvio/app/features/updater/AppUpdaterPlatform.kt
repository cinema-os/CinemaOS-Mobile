package com.nuvio.app.features.updater

expect object AppUpdaterPlatform {
    val isSupported: Boolean
    val isDebugBuild: Boolean

    /** True on Meta Quest headsets, where the in-app APK install is replaced by a download page. */
    val isMetaQuest: Boolean

    /** Opens [url] in the system browser; false when nothing could handle it. */
    fun openExternalUrl(url: String): Boolean

    fun getSupportedAbis(): List<String>

    fun getIgnoredTag(): String?

    fun setIgnoredTag(tag: String?)

    fun getUpdateChannel(): String?

    fun setUpdateChannel(channel: String)

    fun deleteDownloadedApk(path: String)

    suspend fun downloadApk(
        assetUrl: String,
        assetName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<String>

    fun canRequestPackageInstalls(): Boolean

    fun openUnknownSourcesSettings()

    fun installDownloadedApk(path: String): Result<Unit>
}
