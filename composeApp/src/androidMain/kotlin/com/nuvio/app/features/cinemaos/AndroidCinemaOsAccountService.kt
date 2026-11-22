package com.nuvio.app.features.cinemaos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.profiles.MAX_PROFILES
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.servers.ServerItemRef
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

private const val SITE_BASE_URL = "https://cinemaos.tech"

/** Public Firebase web client key (same value as the website's lib/firebase.ts, project cinemaos-v3); not a secret. */
private const val FIREBASE_WEB_API_KEY = "AIzaSyAW0OmHCatRci3Qksp0ApnGEw0O3YdATlo"

private const val TAG = "CinemaOsAccount"

private class CinemaOsAuthException(val code: String) : Exception(code)

/**
 * Android implementation of the CinemaOS account: device login against the website's /api/device routes
 * routes, Firebase custom-token sign-in over the Identity Toolkit REST API (no Firebase SDK), and
 * Continue Watching / Watchlist sync against /api/watch-history and /api/watchlist (Bearer ID token).
 * Ported from the CinemaOS TV app (core/cinemaos/account/), which mirrors the website's
 * continue-watching-service.ts, watchlist-service.ts and storage.ts write queue.
 *
 * Content ids: the site uses TMDB ids; the app keys content by IMDb id ("tt...") with "tmdb:<id>" as
 * fallback. Anime rows from the site (media_type "anime", AniList ids) are ignored on mobile.
 * Pulls write through the repositories' external-apply paths so nothing echoes back to the site.
 *
 * Profiles: the website has no profile concept, so every pushed row carries a client-side marker
 * (see [CinemaOsProfileMarker]). Pulls only apply rows whose marker matches the active local profile;
 * unmarked rows (website writes, pre-profile data) belong to profile "1". The server's row id has no
 * profile in it and its upsert never rewrites `description`, so the first profile to write a title
 * owns that row: pushes/deletes for a row owned by another profile are skipped (kept local only).
 */
class AndroidCinemaOsAccountService(context: Context) : CinemaOsAccountService {
    private val appContext = context.applicationContext
    private val store = CinemaOsAccountStore(appContext)
    private val http = OkHttpClient.Builder().dns(com.nuvio.app.core.network.IPv4FirstDns()).build()
    private val json = "application/json".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences("cinemaos_sync", Context.MODE_PRIVATE)

    private var stored: CinemaOsStoredSession? = store.load()
    private val _session = MutableStateFlow(stored?.toPublic())
    override val session: StateFlow<CinemaOsAccountSession?> = _session.asStateFlow()
    override val isSupported: Boolean = true

    private val _lastSyncedAt = MutableStateFlow(prefs.getLong(K_LAST_SYNC, 0L))
    override val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val refreshMutex = Mutex()
    private val pullMutex = Mutex()
    private val flushMutex = Mutex()
    private val queueLock = Any()
    private val lastPushAt = ConcurrentHashMap<String, Long>()
    private val pendingPush = ConcurrentHashMap<String, Pair<WatchProgressEntry, Int>>()

    private fun CinemaOsStoredSession.toPublic() = CinemaOsAccountSession(label = label, uid = uid, displayName = displayName)
    private val isSignedIn: Boolean get() = stored != null

    /** Pulls when a user signs in (and on app start if already signed in); then every 10 minutes. */
    fun start() {
        scope.launch {
            _session.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) pullAll()
            }
        }
        scope.launch {
            while (true) {
                delay(PERIODIC_SYNC_MS)
                if (isSignedIn) pullAll()
            }
        }
        // Switching profile reloads Continue Watching / library from the cloud for the new profile.
        scope.launch {
            ProfileRepository.state.map { it.activeProfile?.profileIndex }
                .distinctUntilChanged()
                .drop(1)
                .collect { index -> if (index != null && isSignedIn) pullAll() }
        }
    }

    // ---------------------------------------------------------------- Profiles

    private fun activeProfileIndex(): Int = ProfileRepository.activeProfileId

    private fun profileName(index: Int): String =
        ProfileRepository.state.value.profiles.firstOrNull { it.profileIndex == index }?.name.orEmpty()

    /** Per-profile key for local bookkeeping; profile 1 keeps the pre-profile key so existing data carries over. */
    private fun profileKey(base: String, index: Int): String = if (index == 1) base else base + "_p" + index

    // ---------------------------------------------------------------- Auth

    override suspend fun startDeviceLogin(): Result<CinemaOsDeviceCode> = runCatching {
        val res = postJson(
            "$SITE_BASE_URL/api/device/start",
            JSONObject().put("deviceName", "CinemaOS Mobile"),
        )
        CinemaOsDeviceCode(
            deviceCode = res.getString("deviceCode"),
            userCode = res.getString("userCode"),
            verificationUrl = res.optString("verificationUrl", "$SITE_BASE_URL/tv-login"),
            verificationUrlComplete = res.getString("verificationUrlComplete"),
            expiresInSec = res.optInt("expiresIn", 600),
            intervalSec = res.optInt("interval", 5).coerceAtLeast(1),
        )
    }

    override suspend fun pollDeviceLogin(deviceCode: String): Result<CinemaOsPollResult> = runCatching {
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$SITE_BASE_URL/api/device/poll")
                .post(JSONObject().put("deviceCode", deviceCode).toString().toRequestBody(json))
                .build()
            http.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val o = runCatching { JSONObject(text) }.getOrNull()
                when (o?.optString("status")) {
                    "approved" -> CinemaOsPollResult.Approved(o.getString("customToken"))
                    "pending" -> CinemaOsPollResult.Pending
                    "slow_down" -> CinemaOsPollResult.SlowDown
                    "expired" -> CinemaOsPollResult.Expired
                    "denied" -> CinemaOsPollResult.Denied
                    else -> when (r.code) {
                        410 -> CinemaOsPollResult.Expired
                        429 -> CinemaOsPollResult.SlowDown
                        403 -> CinemaOsPollResult.Denied
                        else -> throw CinemaOsAuthException("HTTP_${r.code}")
                    }
                }
            }
        }
    }

    override suspend fun completeSignIn(customToken: String): Result<Unit> = runCatching {
        val key = FIREBASE_WEB_API_KEY
        val res = postJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=$key",
            JSONObject().put("token", customToken).put("returnSecureToken", true),
        )
        val idToken = res.getString("idToken")
        val lookup = runCatching {
            postJson(
                "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=$key",
                JSONObject().put("idToken", idToken),
            ).optJSONArray("users")?.optJSONObject(0)
        }.getOrNull()
        val label = lookup?.optString("email")?.takeIf { it.isNotBlank() }
            ?: lookup?.optString("displayName")?.takeIf { it.isNotBlank() }
            ?: "CinemaOS account"
        val uid = lookup?.optString("localId")?.takeIf { it.isNotBlank() }
            ?: res.optString("localId").takeIf { it.isNotBlank() }
            ?: throw CinemaOsAuthException("NO_UID")
        val session = CinemaOsStoredSession(
            label = label,
            displayName = lookup?.optString("displayName")?.takeIf(String::isNotBlank),
            uid = uid,
            refreshToken = res.getString("refreshToken"),
            idToken = idToken,
            idTokenExpiresAtMs = expiry(res.optString("expiresIn")),
        )
        store.save(session)
        stored = session
        _session.value = session.toPublic()
    }

    override fun signOut() {
        store.clear()
        stored = null
        _session.value = null
        synchronized(queueLock) {
            val edit = prefs.edit()
                .remove(K_QUEUE)
                .remove(K_HISTORY_OWNERS)
                .remove(K_WATCHLIST_OWNERS)
                .remove(K_LAST_SYNC)
            for (index in 1..MAX_PROFILES) {
                edit.remove(profileKey(K_SYNCED_HISTORY, index))
                edit.remove(profileKey(K_SYNCED_WATCHLIST, index))
            }
            edit.apply()
        }
        _lastSyncedAt.value = 0L
    }

    /** Returns a valid Firebase ID token (refreshing when near expiry), or null when signed out/failed. */
    private suspend fun getIdToken(forceRefresh: Boolean = false): String? = refreshMutex.withLock {
        val current = stored ?: return null
        if (!forceRefresh && current.idToken.isNotBlank() &&
            System.currentTimeMillis() < current.idTokenExpiresAtMs - 60_000L
        ) {
            return current.idToken
        }
        runCatching {
            val res = withContext(Dispatchers.IO) {
                val form = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", current.refreshToken)
                    .build()
                val req = Request.Builder()
                    .url("https://securetoken.googleapis.com/v1/token?key=$FIREBASE_WEB_API_KEY")
                    .post(form)
                    .build()
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw CinemaOsAuthException(errorCode(text, r.code))
                    JSONObject(text)
                }
            }
            val updated = current.copy(
                idToken = res.getString("id_token"),
                refreshToken = res.optString("refresh_token", current.refreshToken),
                idTokenExpiresAtMs = expiry(res.optString("expires_in")),
            )
            store.save(updated)
            stored = updated
            updated.idToken
        }.getOrElse { error ->
            // A revoked/invalid refresh token means the session is dead; network errors keep it.
            if (error is CinemaOsAuthException &&
                (error.code.contains("TOKEN_EXPIRED") || error.code.contains("USER_DISABLED") ||
                    error.code.contains("USER_NOT_FOUND") || error.code.contains("INVALID_REFRESH_TOKEN"))
            ) {
                signOut()
            }
            null
        }
    }

    private suspend fun postJson(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(json)).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw CinemaOsAuthException(errorCode(text, r.code))
            JSONObject(text)
        }
    }

    private fun errorCode(text: String, httpCode: Int): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }
            .getOrDefault("HTTP_$httpCode")

    private fun expiry(expiresInSeconds: String): Long =
        System.currentTimeMillis() + (expiresInSeconds.toLongOrNull() ?: 3600L) * 1000L

    override fun qrCode(content: String): ImageBitmap? = runCatching {
        val size = 512
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 2),
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.asImageBitmap()
    }.getOrNull()

    // ---------------------------------------------------------------- Sync

    override fun syncNow() {
        if (!isSignedIn) return
        scope.launch { pullAll() }
    }

    /** flush the write queue, then pull history + watchlist. */
    private suspend fun pullAll() = pullMutex.withLock {
        _isSyncing.value = true
        try {
            flushQueue()
            val a = runCatching { pullHistory() }.onFailure { Log.w(TAG, "history pull failed", it) }.isSuccess
            val b = runCatching { pullWatchlist() }.onFailure { Log.w(TAG, "watchlist pull failed", it) }.isSuccess
            if (a || b) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(K_LAST_SYNC, now).apply()
                _lastSyncedAt.value = now
            }
        } finally {
            _isSyncing.value = false
        }
    }

    /** Mirrors continue-watching-service buildDocId: season/episode suffix only when non-zero. */
    private fun historyDocId(uid: String, mediaType: String, mediaId: String, season: Int?, episode: Int?) =
        "${uid}_${mediaType}_${mediaId}" +
            (if (season != null && season != 0) "_s$season" else "") +
            (if (episode != null && episode != 0) "_e$episode" else "")

    /** Mirrors watchlist-service buildDocId. */
    private fun watchlistDocId(uid: String, movieType: String, mediaId: String) = "${uid}_${movieType}_$mediaId"

    // ---- Pull: watch history ----

    private suspend fun pullHistory() {
        val uid = stored?.uid ?: return
        val profileIndex = activeProfileIndex()
        val profileId = profileIndex.toString()
        val (arr, _) = getJsonArray("/api/watch-history") ?: return
        val pendingIds = pendingDocIds("/api/watch-history", uid)
        val previous = loadMap(profileKey(K_SYNCED_HISTORY, profileIndex))
        val fullView = arr.length() < HISTORY_PAGE_LIMIT
        val owners: MutableMap<String, String> =
            if (fullView) LinkedHashMap() else LinkedHashMap(loadMap(K_HISTORY_OWNERS))
        val newSynced = LinkedHashMap<String, String>()
        val entries = mutableListOf<WatchProgressEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val idStr = o.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val mediaType = o.optString("media_type", "movie")
            val isSeries = mediaType == "tv"
            val season = if (isSeries && o.has("season") && !o.isNull("season")) o.optInt("season") else null
            val episode = if (isSeries && o.has("episode") && !o.isNull("episode")) o.optInt("episode") else null
            val docId = o.optString("docId").takeIf { it.isNotBlank() }
                ?: historyDocId(uid, mediaType, idStr, season, episode)
            val owner = CinemaOsProfileMarker.parse(o.optString("description")).profileId
            owners[docId] = owner
            if (mediaType == "anime") continue
            if (owner != profileId) continue // another local profile's row
            val tmdbNum = idStr.toIntOrNull() ?: continue
            val contentId = resolveContentId(tmdbNum, if (isSeries) "tv" else "movie")
            newSynced[docId] = contentId
            if (docId in pendingIds) continue // a queued local write wins over the cloud snapshot
            val durationMs = (o.optDouble("duration", 0.0) * 1000).toLong()
            val positionMs = (o.optDouble("progress", 0.0) * 1000).toLong()
            if (durationMs <= 0L || positionMs <= 0L) continue
            val watchedAt = toMs(o.opt("watchedAt")).takeIf { it > 0L } ?: System.currentTimeMillis()
            val videoId = if (isSeries && season != null && episode != null) "$contentId:$season:$episode" else contentId
            val parentType = if (isSeries) "series" else "movie"
            entries += WatchProgressEntry(
                contentType = parentType,
                parentMetaId = contentId,
                parentMetaType = parentType,
                videoId = videoId,
                title = o.optString("name").ifBlank { "Unknown" },
                background = backdropUrl(o.optString("backdrop_path")),
                seasonNumber = season,
                episodeNumber = episode,
                lastPositionMs = positionMs,
                durationMs = durationMs,
                lastUpdatedEpochMs = watchedAt,
            )
        }
        synchronized(queueLock) { saveMap(K_HISTORY_OWNERS, owners) }
        // The user switched profile mid-pull: these rows belong to the previous profile.
        if (activeProfileIndex() != profileIndex) return
        // applyExternalProgress keeps whichever side is newer and never pushes back.
        if (entries.isNotEmpty()) WatchProgressRepository.applyExternalProgress(entries)
        // Rows pulled earlier that are gone from the cloud (and have no pending write) were deleted
        // elsewhere. The server returns at most 100 rows, so only trust a full view.
        if (fullView) {
            for ((docId, contentId) in previous) {
                // Still in the cloud but now owned by another profile: keep this profile's local copy.
                if (docId in newSynced || docId in pendingIds || docId in owners) continue
                val (type, season, episode) = historyKeyFromDocId(uid, docId) ?: continue
                if (type == "tv" && (season == null || episode == null)) continue
                WatchProgressRepository.removeExternalProgress(contentId, season, episode)
            }
        }
        saveMap(profileKey(K_SYNCED_HISTORY, profileIndex), newSynced)
    }

    /** Splits "<uid>_<type>_<id>[_s<n>][_e<n>]" into (type, season, episode). */
    private fun historyKeyFromDocId(uid: String, docId: String): Triple<String, Int?, Int?>? {
        var rest = docId.removePrefix("${uid}_")
        if (rest == docId) return null
        var episode: Int? = null
        var season: Int? = null
        Regex("_e(\\d+)$").find(rest)?.let { episode = it.groupValues[1].toInt(); rest = rest.removeSuffix(it.value) }
        Regex("_s(\\d+)$").find(rest)?.let { season = it.groupValues[1].toInt(); rest = rest.removeSuffix(it.value) }
        return Triple(rest.substringBefore('_'), season, episode)
    }

    // ---- Pull: watchlist ----

    private suspend fun pullWatchlist() {
        val uid = stored?.uid ?: return
        val profileIndex = activeProfileIndex()
        val profileId = profileIndex.toString()
        val pendingIds = pendingDocIds("/api/watchlist", uid)
        val rows = mutableListOf<JSONObject>()
        var offset = 0
        var guard = 0
        while (guard++ < 40) {
            val (arr, next) = getJsonArray("/api/watchlist?offset=$offset") ?: return
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { rows += it }
            if (next == null || next <= offset) break
            offset = next
        }
        val previous = loadMap(profileKey(K_SYNCED_WATCHLIST, profileIndex))
        val owners = LinkedHashMap<String, String>()
        val newSynced = LinkedHashMap<String, String>() // docId -> "<type>|<contentId>"
        for (o in rows) {
            val idStr = o.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val movieType = o.optString("movie_type", "movie")
            val docId = watchlistDocId(uid, movieType, idStr)
            val marker = CinemaOsProfileMarker.parse(o.optString("description"))
            owners[docId] = marker.profileId
            if (movieType == "anime") continue
            if (marker.profileId != profileId) continue // another local profile's row
            val isSeries = movieType != "movie"
            val type = if (isSeries) "series" else "movie"
            val tmdbNum = idStr.toIntOrNull() ?: continue
            val id = resolveContentId(tmdbNum, if (isSeries) "tv" else "movie")
            newSynced[docId] = "$type|$id"
            if (docId in pendingIds) continue
            if (activeProfileIndex() != profileIndex) continue
            if (LibraryRepository.cinemaOsHasLocal(id, type)) continue
            LibraryRepository.cinemaOsSave(
                LibraryItem(
                    id = id,
                    type = type,
                    name = o.optString("name").ifBlank { "Unknown" },
                    poster = if (id.startsWith("tt")) "https://images.metahub.space/poster/medium/$id/img" else null,
                    banner = backdropUrl(o.optString("backdrop_path")),
                    description = marker.description.takeIf { it.isNotBlank() },
                    imdbId = id.takeIf { it.startsWith("tt") },
                    tmdbId = tmdbNum,
                    savedAtEpochMs = toMs(o.opt("addedAt")).takeIf { it > 0 } ?: System.currentTimeMillis(),
                ),
            )
        }
        synchronized(queueLock) { saveMap(K_WATCHLIST_OWNERS, owners) }
        if (activeProfileIndex() != profileIndex) return
        // Rows synced before but missing now (no pending write) were removed on another device.
        for ((docId, ref) in previous) {
            if (docId in newSynced || docId in pendingIds || docId in owners) continue
            val type = ref.substringBefore('|')
            val id = ref.substringAfter('|', "")
            if (id.isNotBlank()) LibraryRepository.cinemaOsRemove(id, type)
        }
        saveMap(profileKey(K_SYNCED_WATCHLIST, profileIndex), newSynced)
    }

    // ---- Push: watch history ----

    private fun progressKey(e: WatchProgressEntry, profileIndex: Int) =
        "$profileIndex|${e.parentMetaId}|${e.seasonNumber}|${e.episodeNumber}"

    /** True when the last pull saw [docId] owned by a different local profile (see class doc). */
    private fun ownedByOtherProfile(ownersKey: String, docId: String, profileIndex: Int): Boolean {
        val owner = loadMap(ownersKey)[docId] ?: return false
        return owner != profileIndex.toString()
    }

    private fun setOwner(ownersKey: String, docId: String, profileIndex: Int?) = synchronized(queueLock) {
        val map = LinkedHashMap(loadMap(ownersKey))
        if (profileIndex == null) map.remove(docId) else map[docId] = profileIndex.toString()
        saveMap(ownersKey, map)
    }

    private fun isPushable(e: WatchProgressEntry): Boolean =
        isSignedIn && !ServerItemRef.isServerId(e.parentMetaId) && e.durationMs > 0L &&
            (e.isEffectivelyCompleted || e.lastPositionMs >= 5_000L)

    /** Throttled to ~60 s per item (the website saves every 60 s) with a trailing push; completed marks push immediately. */
    override fun onProgressSaved(entry: WatchProgressEntry) {
        if (!isPushable(entry)) return
        val profileIndex = activeProfileIndex()
        val key = progressKey(entry, profileIndex)
        val now = System.currentTimeMillis()
        val last = lastPushAt[key] ?: 0L
        if (now - last >= PUSH_INTERVAL_MS || entry.isEffectivelyCompleted) {
            lastPushAt[key] = now
            pendingPush.remove(key)
            scope.launch { pushProgress(entry, profileIndex) }
        } else if (pendingPush.put(key, entry to profileIndex) == null) {
            scope.launch {
                delay(PUSH_INTERVAL_MS - (now - last))
                pendingPush.remove(key)?.let { (pending, pendingProfile) ->
                    lastPushAt[key] = System.currentTimeMillis()
                    pushProgress(pending, pendingProfile)
                }
            }
        } else {
            pendingPush[key] = entry to profileIndex
        }
    }

    override fun onProgressRemoved(entries: Collection<WatchProgressEntry>) {
        val uid = stored?.uid ?: return
        val profileIndex = activeProfileIndex()
        scope.launch {
            runCatching {
                for (e in entries) {
                    val isSeries = e.parentMetaType.equals("series", true) || e.seasonNumber != null
                    val mediaType = if (isSeries) "tv" else "movie"
                    val mediaId = TmdbService.ensureTmdbId(e.parentMetaId, mediaType) ?: continue
                    val docId = historyDocId(uid, mediaType, mediaId, e.seasonNumber, e.episodeNumber)
                    // Never delete another profile's cloud row.
                    if (ownedByOtherProfile(K_HISTORY_OWNERS, docId, profileIndex)) continue
                    enqueue(uid, "/api/watch-history", "DELETE", docId, "")
                    setOwner(K_HISTORY_OWNERS, docId, null)
                }
                flushQueue()
            }.onFailure { Log.w(TAG, "history delete failed", it) }
        }
    }

    private suspend fun pushProgress(e: WatchProgressEntry, profileIndex: Int) {
        runCatching {
            val uid = stored?.uid ?: return
            val isSeries = e.parentMetaType.equals("series", true) || e.seasonNumber != null
            val mediaType = if (isSeries) "tv" else "movie"
            val mediaId = TmdbService.ensureTmdbId(e.parentMetaId, mediaType) ?: return
            val docId = historyDocId(uid, mediaType, mediaId, e.seasonNumber, e.episodeNumber)
            // The server upsert keeps the first writer's description (marker), so writing here
            // would put this profile's progress on another profile's row. Keep it local instead.
            if (ownedByOtherProfile(K_HISTORY_OWNERS, docId, profileIndex)) return
            val profileName = profileName(profileIndex)
            val completed = e.isEffectivelyCompleted
            val durationSec = e.durationMs / 1000
            val progressSec = if (completed) durationSec else e.lastPositionMs / 1000
            val item = JSONObject()
                .put("id", mediaId)
                .put("name", e.title.ifBlank { "Unknown" })
                .put("media_type", mediaType)
                .put("progress", progressSec)
                .put("duration", durationSec)
                .put("backdrop_path", toBackdropPath(e.background))
                .put("description", CinemaOsProfileMarker.mark(profileIndex.toString(), profileName, ""))
                .put(CinemaOsProfileMarker.FIELD_PROFILE_ID, profileIndex.toString())
                .put(CinemaOsProfileMarker.FIELD_PROFILE_NAME, profileName)
            if (isSeries && e.episodeNumber != null) {
                if (e.seasonNumber != null) item.put("season", e.seasonNumber)
                item.put("episode", e.episodeNumber)
                // Same string the website's TV player sends.
                if (e.seasonNumber != null) item.put("lastPosition", "Season ${e.seasonNumber}, Episode ${e.episodeNumber}")
            }
            enqueue(uid, "/api/watch-history", "POST", docId, JSONObject().put("userId", uid).put("item", item).toString())
            setOwner(K_HISTORY_OWNERS, docId, profileIndex)
            flushQueue()
        }.onFailure { Log.w(TAG, "progress push failed", it) }
    }

    // ---- Push: watchlist ----

    override fun onLibraryChanged(item: LibraryItem, added: Boolean) {
        val uid = stored?.uid ?: return
        if (ServerItemRef.isServerId(item.id)) return
        val profileIndex = activeProfileIndex()
        scope.launch {
            runCatching {
                val isSeries = !item.type.equals("movie", true)
                val type = if (isSeries) "tv" else "movie"
                val mediaId = (item.tmdbId?.toString() ?: TmdbService.ensureTmdbId(item.id, type, item.imdbId))
                    ?: return@launch
                val docId = watchlistDocId(uid, type, mediaId)
                // Rows owned by another local profile stay untouched (kept local only).
                if (ownedByOtherProfile(K_WATCHLIST_OWNERS, docId, profileIndex)) return@launch
                if (added) {
                    val profileName = profileName(profileIndex)
                    val body = JSONObject().put("id", mediaId).put("name", item.name.ifBlank { "Unknown" })
                        .put("movie_type", type)
                        .put("backdrop_path", toBackdropPath(item.banner))
                        .put(
                            "description",
                            CinemaOsProfileMarker.mark(profileIndex.toString(), profileName, item.description.orEmpty()),
                        )
                        .put(CinemaOsProfileMarker.FIELD_PROFILE_ID, profileIndex.toString())
                        .put(CinemaOsProfileMarker.FIELD_PROFILE_NAME, profileName)
                    enqueue(uid, "/api/watchlist", "POST", docId, JSONObject().put("userId", uid).put("item", body).toString())
                    setOwner(K_WATCHLIST_OWNERS, docId, profileIndex)
                } else {
                    enqueue(uid, "/api/watchlist", "DELETE", docId, "")
                    setOwner(K_WATCHLIST_OWNERS, docId, null)
                }
                flushQueue()
            }.onFailure { Log.w(TAG, "watchlist push failed", it) }
        }
    }

    // ---- Persistent write queue (mirrors lib/services/storage.ts writeQueue) ----

    private data class Pending(
        val uid: String,
        val path: String,
        val method: String,
        val docId: String,
        val body: String,
        val addedAt: Long,
    )

    private fun loadQueue(): MutableList<Pending> = synchronized(queueLock) {
        val arr = runCatching { JSONArray(prefs.getString(K_QUEUE, "[]")) }.getOrDefault(JSONArray())
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Pending(
                    it.optString("uid"), it.optString("path"), it.optString("method"),
                    it.optString("docId"), it.optString("body"), it.optLong("addedAt"),
                )
            }
        }.toMutableList()
    }

    private fun saveQueue(list: List<Pending>) = synchronized(queueLock) {
        val arr = JSONArray()
        list.takeLast(500).forEach {
            arr.put(
                JSONObject().put("uid", it.uid).put("path", it.path).put("method", it.method)
                    .put("docId", it.docId).put("body", it.body).put("addedAt", it.addedAt),
            )
        }
        prefs.edit().putString(K_QUEUE, arr.toString()).apply()
    }

    /** Latest intent replaces an earlier add/delete for the same account + path + document. */
    private fun enqueue(uid: String, path: String, method: String, docId: String, body: String) =
        synchronized(queueLock) {
            val q = loadQueue().filterNot { it.uid == uid && it.path == path && it.docId == docId }.toMutableList()
            q += Pending(uid, path, method, docId, body, System.currentTimeMillis())
            saveQueue(q)
        }

    private fun pendingDocIds(path: String, uid: String): Set<String> =
        loadQueue().filter { it.uid == uid && it.path == path }.map { it.docId }.toSet()

    /** Sequential; drop on success/4xx, keep on 401/403/429/5xx/network error. */
    private suspend fun flushQueue(): Unit = flushMutex.withLock {
        val uid = stored?.uid ?: return
        val attempted = HashSet<String>()
        while (true) {
            val write = loadQueue().firstOrNull {
                it.uid == uid && "${it.path}|${it.docId}|${it.addedAt}" !in attempted
            } ?: return
            attempted += "${write.path}|${write.docId}|${write.addedAt}"
            val url = if (write.method == "DELETE") {
                "${write.path}?id=${URLEncoder.encode(write.docId, "UTF-8")}"
            } else {
                write.path
            }
            val code = runCatching {
                request(write.method, url, if (write.method == "DELETE") null else write.body)
            }.getOrElse {
                Log.w(TAG, "flush ${write.method} ${write.path} failed", it)
                return
            }
            if (code in 200..299 || (code in 400..499 && code != 401 && code != 403 && code != 429)) {
                synchronized(queueLock) {
                    saveQueue(
                        loadQueue().filterNot {
                            it.uid == write.uid && it.path == write.path && it.docId == write.docId &&
                                it.addedAt == write.addedAt
                        },
                    )
                }
            } else {
                return
            }
        }
    }

    // ---- HTTP (Bearer Firebase ID token; one token refresh + retry on 401) ----

    private suspend fun <T> withAuth(block: suspend (token: String) -> Response, read: (Response) -> T): T? =
        withContext(Dispatchers.IO) {
            var token = getIdToken() ?: return@withContext null
            var resp = block(token)
            if (resp.code == 401) {
                resp.close()
                token = getIdToken(forceRefresh = true) ?: return@withContext null
                resp = block(token)
            }
            resp.use { read(it) }
        }

    private suspend fun request(method: String, path: String, body: String?): Int =
        withAuth(
            { token ->
                val req = Request.Builder().url(SITE_BASE_URL + path)
                    .header("Authorization", "Bearer $token")
                    .method(method, if (method == "DELETE" && body == null) null else (body ?: "").toRequestBody(json))
                    .build()
                http.newCall(req).execute()
            },
            { r ->
                if (!r.isSuccessful) Log.w(TAG, "$method $path -> ${r.code}")
                r.code
            },
        ) ?: 401

    /** Returns the array plus the next offset from X-Next-Offset (null when no more pages). */
    private suspend fun getJsonArray(path: String): Pair<JSONArray, Int?>? =
        withAuth(
            { token ->
                http.newCall(
                    Request.Builder().url(SITE_BASE_URL + path)
                        .header("Authorization", "Bearer $token").get().build(),
                ).execute()
            },
            { r ->
                if (!r.isSuccessful) {
                    Log.w(TAG, "GET $path -> ${r.code}")
                    null
                } else {
                    JSONArray(r.body?.string().orEmpty()) to r.header("X-Next-Offset")?.toIntOrNull()
                }
            },
        )

    // ---- Helpers ----

    private suspend fun resolveContentId(tmdbId: Int, type: String): String =
        runCatching { TmdbService.tmdbToImdb(tmdbId, type) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "tmdb:$tmdbId"

    private fun loadMap(key: String): Map<String, String> {
        val o = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { k -> out[k] = o.optString(k) }
        return out
    }

    private fun saveMap(key: String, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(key, o.toString()).apply()
    }

    /** The website stores TMDB-relative backdrop paths ("/abc.jpg"); the app keeps full URLs. */
    private fun toBackdropPath(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val m = Regex("^https://image\\.tmdb\\.org/t/p/[^/]+(/.+)$").find(url)
        return m?.groupValues?.get(1) ?: url
    }

    private fun backdropUrl(raw: String): String? = when {
        raw.isBlank() || raw == "null" -> null
        raw.startsWith("http") -> raw
        else -> "https://image.tmdb.org/t/p/w780" + (if (raw.startsWith("/")) raw else "/$raw")
    }

    /** Mirrors toMs in continue-watching-service: number (seconds or ms), ISO string, else 0. */
    private fun toMs(v: Any?): Long = when (v) {
        is Number -> v.toLong().let { if (it in 1..99_999_999_999L) it * 1000 else it }
        is String -> v.toLongOrNull()
            ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            ?: 0L
        else -> 0L
    }

    private companion object {
        const val PUSH_INTERVAL_MS = 60_000L
        const val PERIODIC_SYNC_MS = 10 * 60_000L
        const val HISTORY_PAGE_LIMIT = 100
        const val K_LAST_SYNC = "last_sync_at"
        const val K_QUEUE = "pending_writes"
        const val K_SYNCED_HISTORY = "synced_history_docs"
        const val K_SYNCED_WATCHLIST = "synced_watchlist_docs"
        const val K_HISTORY_OWNERS = "history_doc_owners"
        const val K_WATCHLIST_OWNERS = "watchlist_doc_owners"
    }
}
