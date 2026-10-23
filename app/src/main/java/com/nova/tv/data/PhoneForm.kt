package com.nova.tv.data

import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Petit serveur web local : la TV affiche un QR code, le téléphone (iPhone ou Android,
 * sur le même Wi-Fi) ouvre une page pour remplir un formulaire, et la TV reçoit les valeurs.
 * Aucun serveur Internet : tout reste dans la maison.
 */
class PhoneForm(
    private val page: (token: String) -> String,
    private val onSubmit: (Map<String, String>) -> Unit,
) {
    private var server: ServerSocket? = null
    private val token = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
    private val main = Handler(Looper.getMainLooper())

    /** Démarre le serveur et renvoie l'adresse à ouvrir sur le téléphone, ou null (pas de réseau local). */
    fun start(): String? {
        val ip = localIp() ?: return null
        val s = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORTS.firstOrNull { p -> runCatching { ServerSocket(p).close() }.isSuccess } ?: 0))
            }
        } catch (e: Exception) {
            return null
        }
        server = s
        thread(isDaemon = true, name = "phone-form") {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { handle(client) }
            }
        }
        return "http://$ip:${s.localPort}/?t=$token"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 15000
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readLine(input) ?: return
            var length = 0
            while (true) {
                val h = readLine(input) ?: break
                if (h.isEmpty()) break
                if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0
            }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val target = parts.getOrNull(1) ?: "/"
            val query = parse(target.substringAfter('?', ""))
            val out = sock.getOutputStream()
            fun respond(status: String, body: String) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                out.write(
                    ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                        "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
                )
                out.write(bytes)
                out.flush()
            }
            if (query["t"] != token) {
                respond("403 Forbidden", simplePage("Lien expiré", "Scannez à nouveau le QR code affiché sur la TV."))
                return
            }
            if (method == "POST") {
                val body = ByteArray(length.coerceIn(0, 64 * 1024))
                var read = 0
                while (read < body.size) {
                    val n = input.read(body, read, body.size - read)
                    if (n < 0) break
                    read += n
                }
                val values = parse(String(body, 0, read, Charsets.UTF_8))
                main.post { onSubmit(values) }
                respond("200 OK", simplePage("C'est envoyé !", "Regardez votre TV : SamothTV a bien reçu vos informations."))
            } else {
                respond("200 OK", page(token))
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun parse(q: String): Map<String, String> =
        q.split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }

    companion object {
        private val PORTS = listOf(8642, 8643, 8644)

        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        private const val STYLE = """
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            :root{color-scheme:dark}
            body{margin:0;background:#08080a;color:#f4f3f6;font:16px/1.5 -apple-system,system-ui,Segoe UI,Roboto,sans-serif;padding:28px 20px}
            .logo{display:flex;align-items:center;gap:10px;font-weight:900;font-size:22px;letter-spacing:.02em;margin-bottom:22px}
            .logo i{width:30px;height:30px;border-radius:8px;background:linear-gradient(135deg,#d10a20,#7a000f);display:inline-block}
            .logo span{color:#d10a20}
            h1{font-size:26px;margin:0 0 6px}
            p{color:#a29fab;margin:0 0 18px}
            label{display:block;font-size:13px;color:#a29fab;margin:14px 0 6px}
            input{width:100%;box-sizing:border-box;font-size:17px;padding:13px 14px;border-radius:10px;border:1px solid #2a2830;background:#1b1a21;color:#f4f3f6}
            input:focus{outline:2px solid #d10a20;border-color:#d10a20}
            button{margin-top:22px;width:100%;font-size:17px;font-weight:700;padding:14px;border:0;border-radius:999px;color:#fff;background:linear-gradient(135deg,#d10a20,#7a000f)}
            .tabs{display:flex;gap:8px;margin:8px 0 4px}
            .tabs a{flex:1;text-align:center;padding:10px;border-radius:999px;background:#1b1a21;color:#a29fab;text-decoration:none;font-weight:700;font-size:15px}
            .tabs a.on{background:#d10a20;color:#fff}
            </style>"""

        private const val LOGO = """<div class="logo"><i></i>SAMOTH <span>TV</span></div>"""

        fun simplePage(title: String, text: String) =
            "<!doctype html><html lang=fr><head><meta charset=utf-8>$STYLE<title>SamothTV</title></head>" +
                "<body>$LOGO<h1>$title</h1><p>$text</p></body></html>"

        /** Formulaire de connexion (Xtream Codes ou lien M3U). */
        fun loginPage(token: String) = """<!doctype html><html lang=fr><head><meta charset=utf-8>$STYLE<title>SamothTV</title></head><body>
            $LOGO
            <h1>Ajouter une playlist</h1>
            <p>Remplissez ici, la TV se connecte toute seule.</p>
            <div class="tabs"><a href="#" id="tx" class="on">Xtream Codes</a><a href="#" id="tm">Lien M3U</a></div>
            <form method="post" action="/?t=$token">
              <input type="hidden" name="type" id="type" value="xtream">
              <div id="fx">
                <label for="server">Adresse du serveur</label><input id="server" name="server" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://exemple.com:8080">
                <label for="username">Nom d'utilisateur</label><input id="username" name="username" autocapitalize="off" autocorrect="off">
                <label for="password">Mot de passe</label><input id="password" name="password" type="password">
              </div>
              <div id="fm" hidden>
                <label for="m3u">Lien de la playlist (M3U, M3U8)</label><input id="m3u" name="m3u" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://exemple.com/playlist.m3u">
                <label for="epg">Lien du guide TV XMLTV (facultatif)</label><input id="epg" name="epg" inputmode="url" autocapitalize="off" autocorrect="off">
              </div>
              <label for="name">Nom de la playlist (facultatif)</label><input id="name" name="name" placeholder="Ma TV">
              <button type="submit">Envoyer à la TV</button>
            </form>
            <script>
            function mode(m){document.getElementById('type').value=m;document.getElementById('fx').hidden=m!=='xtream';document.getElementById('fm').hidden=m==='xtream';
              document.getElementById('tx').className=m==='xtream'?'on':'';document.getElementById('tm').className=m==='xtream'?'':'on';}
            document.getElementById('tx').onclick=function(e){e.preventDefault();mode('xtream')};
            document.getElementById('tm').onclick=function(e){e.preventDefault();mode('m3u')};
            </script>
            </body></html>"""

        /** Formulaire de saisie de la clé de licence. */
        fun licensePage(token: String) = """<!doctype html><html lang=fr><head><meta charset=utf-8>$STYLE<title>SamothTV</title></head><body>
            $LOGO
            <h1>Activer SamothTV</h1>
            <p>Collez la clé de licence reçue par e-mail.</p>
            <form method="post" action="/?t=$token">
              <label for="key">Clé de licence</label><input id="key" name="key" autocapitalize="characters" autocorrect="off" placeholder="XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX">
              <button type="submit">Envoyer à la TV</button>
            </form>
            </body></html>"""
    }
}
