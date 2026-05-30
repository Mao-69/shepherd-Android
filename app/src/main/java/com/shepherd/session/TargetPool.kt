package com.shepherd.session

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/**
 * Target pool — Kotlin port of crv/targets.py.
 *
 * Real image targets are pulled from Wikimedia Commons Featured Pictures,
 * sealed (encrypted) on disk under app storage, and each assigned a blind
 * SRI-style coordinate. The session draws a coordinate; the target stays
 * hidden until the post-session reveal.
 *
 * The cipher is the same hash-based stream cipher as the Python tool: a
 * SHA-256 keystream XORed with the plaintext. As the original notes, this
 * only makes accidental peeking inconvenient (keys live beside ciphertext);
 * it is not adversarial-grade.
 */

data class FetchedTarget(
    val title: String,
    val description: String,
    val imageUrl: String?,
    val imageBytes: ByteArray?,
    val source: String,
    val license: String,
    val attribution: String,
    val fileTitle: String?,
)

data class RevealedTarget(
    val coord: String,
    val title: String,
    val description: String,
    val attribution: String,
    val license: String,
    val source: String,
    val imageFile: File?,
    val hasImage: Boolean = false,
)

object TargetCrypto {
    private fun keystream(key: ByteArray, nonce: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var counter = 0L
        var pos = 0
        val md = MessageDigest.getInstance("SHA-256")
        while (pos < length) {
            md.reset()
            md.update(key); md.update(nonce)
            val cb = ByteArray(8)
            for (i in 0 until 8) cb[i] = (counter shr (8 * (7 - i))).toByte()
            md.update(cb)
            val block = md.digest()
            val n = minOf(block.size, length - pos)
            System.arraycopy(block, 0, out, pos, n)
            pos += n; counter++
        }
        return out
    }

    fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val ks = keystream(key, nonce, plain.size)
        val ct = ByteArray(plain.size) { (plain[it].toInt() xor ks[it].toInt()).toByte() }
        return nonce + ct
    }

    fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
        val nonce = blob.copyOfRange(0, 16)
        val ct = blob.copyOfRange(16, blob.size)
        val ks = keystream(key, nonce, ct.size)
        return ByteArray(ct.size) { (ct[it].toInt() xor ks[it].toInt()).toByte() }
    }

    fun newKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}

/** Pulls one random Featured Picture from Wikimedia Commons. */
class WikimediaSource(
    private val thumbWidth: Int = 1024,
    private val maxImageBytes: Int = 8_000_000,
) {
    private val api = "https://commons.wikimedia.org/w/api.php"
    private val category = "Featured_pictures_on_Wikimedia_Commons"
    private val ua = "shepherd-crv/1.0 (personal-research) Android"

    private fun apiCall(params: Map<String, String>): JSONObject? {
        val sb = StringBuilder(api).append("?")
        params.entries.forEachIndexed { i, e ->
            if (i > 0) sb.append("&")
            sb.append(e.key).append("=").append(java.net.URLEncoder.encode(e.value, "UTF-8"))
        }
        return runCatching {
            val conn = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", ua)
                connectTimeout = 15000; readTimeout = 20000
            }
            conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull()
    }

    private fun download(url: String): ByteArray? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", ua)
            connectTimeout = 15000; readTimeout = 30000
        }
        conn.inputStream.use { ins ->
            val buf = ins.readBytes()
            if (buf.size > maxImageBytes) null else buf
        }
    }.getOrNull()

    fun fetchOne(): FetchedTarget? {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val members = apiCall(mapOf(
            "action" to "query", "list" to "categorymembers",
            "cmtitle" to "Category:$category", "cmtype" to "file",
            "cmlimit" to "500", "format" to "json",
            "cmstartsortkey" to letters.random().toString(),
        ))?.optJSONObject("query")?.optJSONArray("categorymembers") ?: return null
        if (members.length() == 0) return null
        val chosen = members.getJSONObject((0 until members.length()).random())
        val fileTitle = chosen.getString("title")

        val info = apiCall(mapOf(
            "action" to "query", "titles" to fileTitle,
            "prop" to "imageinfo", "iiprop" to "url|size|extmetadata",
            "iiurlwidth" to thumbWidth.toString(), "format" to "json",
        )) ?: return null
        val pages = info.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val page = pages.optJSONObject(pages.keys().asSequence().firstOrNull() ?: return null)
            ?: return null
        val ii = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val thumb = ii.optString("thumburl", ii.optString("url", ""))
        val original = ii.optString("url", "")
        val meta = ii.optJSONObject("extmetadata") ?: JSONObject()
        fun metaVal(k: String) = stripHtml(meta.optJSONObject(k)?.optString("value", "") ?: "")
        val desc = metaVal("ImageDescription")
        val license = meta.optJSONObject("LicenseShortName")?.optString("value", "") ?: ""
        val artist = metaVal("Artist"); val credit = metaVal("Credit")
        val attribution = if (artist.isNotBlank() || credit.isNotBlank())
            "$artist ($credit)".trim() else fileTitle

        var cleanTitle = fileTitle.removePrefix("File:").substringBeforeLast(".").replace("_", " ")
        val bytes = if (thumb.isNotBlank()) download(thumb) else null

        return FetchedTarget(
            title = cleanTitle,
            description = (desc.ifBlank { "Featured Picture: $cleanTitle" }).take(2000),
            imageUrl = original, imageBytes = bytes,
            source = "Wikimedia Commons Featured Pictures",
            license = license, attribution = attribution, fileTitle = fileTitle,
        )
    }

    private fun stripHtml(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(); var depth = 0
        for (ch in text) when (ch) {
            '<' -> depth++
            '>' -> if (depth > 0) depth--
            else -> if (depth == 0) sb.append(ch)
        }
        return sb.toString().trim()
    }
}

class TargetPool(context: Context) {
    private val root = File(context.filesDir, "targets")
    private val poolDir = File(root, "pool").apply { mkdirs() }
    private val keysDir = File(root, "keys").apply { mkdirs() }
    private val revealedDir = File(root, "revealed").apply { mkdirs() }
    private val manifestFile = File(poolDir, "manifest.json")

    private fun coordFn(coord: String) = coord.filter { it.isLetterOrDigit() }

    fun manifest(): JSONArray =
        if (manifestFile.exists()) JSONArray(manifestFile.readText()) else JSONArray()

    private fun saveManifest(arr: JSONArray) = manifestFile.writeText(arr.toString(2))

    fun coordinates(): List<String> {
        val m = manifest(); return (0 until m.length()).map { m.getJSONObject(it).getString("coord") }
    }

    fun availableCoordinates(): List<String> {
        val revealed = revealedDir.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        return coordinates().filter { coordFn(it) !in revealed }
    }

    fun size(): Int = coordinates().size

    fun add(t: FetchedTarget): String {
        var coord = Protocol.generateCoordinate()
        val existing = coordinates().toSet()
        while (coord in existing) coord = Protocol.generateCoordinate()
        val fn = coordFn(coord)

        val content = JSONObject().apply {
            put("coord", coord); put("title", t.title); put("description", t.description)
            put("image_url", t.imageUrl); put("source", t.source); put("license", t.license)
            put("attribution", t.attribution); put("file_title", t.fileTitle)
            put("has_image", t.imageBytes != null)
        }
        val key = TargetCrypto.newKey()
        val plain = content.toString(2).toByteArray()
        File(poolDir, "$fn.enc").writeBytes(TargetCrypto.encrypt(plain, key))
        t.imageBytes?.let { File(poolDir, "$fn.image").writeBytes(TargetCrypto.encrypt(it, key)) }
        File(keysDir, "$fn.key").writeText(Base64.encodeToString(key, Base64.NO_WRAP))

        val arr = manifest()
        arr.put(JSONObject().apply {
            put("coord", coord); put("sha256", TargetCrypto.sha256Hex(plain))
            put("has_image", t.imageBytes != null); put("source", t.source)
        })
        saveManifest(arr)
        return coord
    }

    fun pickRandom(): String? = availableCoordinates().randomOrNull()

    fun hasEntry(coord: String): Boolean = coord in coordinates()

    /** Decrypt a target, write its image + plaintext to revealed/, mark it used. */
    fun reveal(coord: String): RevealedTarget? {
        val fn = coordFn(coord)
        val enc = File(poolDir, "$fn.enc"); val keyF = File(keysDir, "$fn.key")
        if (!enc.exists() || !keyF.exists()) return null
        val key = Base64.decode(keyF.readText(), Base64.NO_WRAP)
        val content = JSONObject(String(TargetCrypto.decrypt(enc.readBytes(), key)))

        var imgFile: File? = null
        val imgEnc = File(poolDir, "$fn.image")
        if (imgEnc.exists()) {
            runCatching {
                val out = File(revealedDir, "$fn.jpg")
                out.writeBytes(TargetCrypto.decrypt(imgEnc.readBytes(), key))
                if (out.length() > 0) imgFile = out
            }
        }
        File(revealedDir, "$fn.json").writeText(content.toString(2))
        return RevealedTarget(
            coord = coord,
            title = content.optString("title"),
            description = content.optString("description"),
            attribution = content.optString("attribution"),
            license = content.optString("license"),
            source = content.optString("source"),
            imageFile = imgFile,
            hasImage = content.optBoolean("has_image", false),
        )
    }

    fun isRevealed(coord: String): Boolean = File(revealedDir, "${coordFn(coord)}.json").exists()

    fun pickDecoys(exclude: String, count: Int = 3): List<String> {
        val candidates = coordinates().filter { it != exclude }
        return if (candidates.size <= count) candidates else candidates.shuffled().take(count)
    }

    /** Title/description for a coordinate WITHOUT marking it revealed (for decoy ranking display). */
    fun peekForRanking(coord: String): Pair<String, String>? {
        val fn = coordFn(coord)
        val enc = File(poolDir, "$fn.enc"); val keyF = File(keysDir, "$fn.key")
        if (!enc.exists() || !keyF.exists()) return null
        val key = Base64.decode(keyF.readText(), Base64.NO_WRAP)
        val c = JSONObject(String(TargetCrypto.decrypt(enc.readBytes(), key)))
        return c.optString("title") to c.optString("description")
    }
}
