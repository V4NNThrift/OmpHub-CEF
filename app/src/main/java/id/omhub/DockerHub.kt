package id.omphub

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object DockerHub {
    private const val ACCEPT =
        "application/vnd.oci.image.index.v1+json, " +
        "application/vnd.docker.distribution.manifest.list.v2+json, " +
        "application/vnd.oci.image.manifest.v1+json, " +
        "application/vnd.docker.distribution.manifest.v2+json"

    private fun getText(url: String, token: String?, accept: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "OmpHub/1.0")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (accept != null) c.setRequestProperty("Accept", accept)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code untuk $url")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    fun downloadRootfs(
        repo: String,
        tag: String,
        dest: File,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ) {
        val token = JSONObject(
            getText("https://auth.docker.io/token?service=registry.docker.io&scope=repository:$repo:pull", null)
        ).getString("token")

        val reg = "https://registry-1.docker.io/v2/$repo"
        var manifest = JSONObject(getText("$reg/manifests/$tag", token, ACCEPT))

        if (manifest.has("manifests")) {
            val arr = manifest.getJSONArray("manifests")
            var digest: String? = null
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val pf = m.optJSONObject("platform")
                if (pf != null && pf.optString("os") == "linux" && pf.optString("architecture") == "386") {
                    digest = m.getString("digest")
                    break
                }
            }
            if (digest == null) throw IOException("Platform linux/386 tidak ada di $repo:$tag")
            manifest = JSONObject(getText("$reg/manifests/$digest", token, ACCEPT))
        }

        val layers = manifest.getJSONArray("layers")
        if (layers.length() == 0) throw IOException("Manifest $repo:$tag tidak punya layer")
        val digest = layers.getJSONObject(0).getString("digest")

        val c = URL("$reg/blobs/$digest").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "OmpHub/1.0")
        c.setRequestProperty("Authorization", "Bearer $token")
        val location: String
        try {
            val code = c.responseCode
            if (code !in 300..399) throw IOException("HTTP $code saat mengambil blob $digest")
            location = c.getHeaderField("Location") ?: throw IOException("Redirect tanpa Location")
        } finally {
            c.disconnect()
        }
        downloadFile(location, dest, onProgress)
    }
}