package com.eldoria.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * Atualizador do app: consulta a última Release pública do GitHub,
 * compara com a versão instalada e baixa/instala o APK.
 */
object Updater {

    const val REPO = "viniciusy30-commits/eldoria"

    private const val INTERVALO_AUTO_MS = 6L * 60L * 60L * 1000L // 6 horas

    data class Versao(val codigo: Int, val nome: String, val urlApk: String, val tamanho: Long)

    sealed class Resultado {
        data class Disponivel(val versao: Versao) : Resultado()
        object Atualizado : Resultado()
        object SemInternet : Resultado()
        data class Erro(val mensagem: String) : Resultado()
    }

    private val main = Handler(Looper.getMainLooper())

    fun versaoAtual(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info).toInt()
    }

    /** Procura a versão mais nova e responde na thread principal. */
    fun verificar(context: Context, callback: (Resultado) -> Unit) {
        Thread {
            val resultado: Resultado = try {
                val url = URL("https://api.github.com/repos/$REPO/releases/latest")
                val conexao = url.openConnection() as HttpURLConnection
                conexao.connectTimeout = 12000
                conexao.readTimeout = 15000
                conexao.setRequestProperty("User-Agent", "Eldoria-App")
                conexao.setRequestProperty("Accept", "application/vnd.github+json")

                if (conexao.responseCode != 200) {
                    throw IOException("HTTP ${conexao.responseCode}")
                }

                val texto = conexao.inputStream.bufferedReader().use { it.readText() }
                conexao.disconnect()
                val json = JSONObject(texto)

                val tag = json.optString("tag_name", "v0")
                val codigo = tag.replace("v", "").replace("V", "").trim().toIntOrNull() ?: 0

                var urlApk = ""
                var tamanho = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val nome = asset.optString("name", "")
                        if (nome.endsWith(".apk")) {
                            urlApk = asset.optString("browser_download_url", "")
                            tamanho = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                when {
                    codigo <= 0 || urlApk.isEmpty() -> Resultado.Ero("A release não tem APK publicado")
                    codigo > versaoAtual(context) -> Resultado.Disponivel(Versao(codigo, "v$codigo", urlApk, tamanho))
                    else -> Resultado.Atualizado
                }
            } catch (e: UnknownHostException) {
                Resultado.SemInternet
            } catch (e: IOException) {
                Resultado.SemInternet
            } catch (e: Exception) {
                Resultado.Ero(e.message ?: "Erro desconhecido")
            }
            main.post { callback(resultado) }
        }.start()
    }

    /** Verificação automática: no máximo uma vez a cada 6 horas. */
    fun autoCheck(context: Context, aoDisponivel: (Versao) -> Unit) {
        val prefs = context.getSharedPreferences("atualizacao", Context.MODE_PRIVATE)
        val ultima = prefs.getLong("ultima_verificacao", 0L)
        val agora = System.currentTimeMillis()
        if (agora - ultima < INTERVALO_AUTO_MS) return
        prefs.edit().putLong("ultima_verificacao", agora).apply()

        verificar(context) { resultado ->
            if (resultado is Resultado.Disponivel) {
                aoDisponivel(resultado.versao)
            }
        }
    }

    /** Baixa o APK para o cache e responde o progresso (0 a 100). */
    fun baixar(
        context: Context,
        versao: Versao,
        aoProgresso: (Int) -> Unit,
        aoConcluir: (File) -> Unit,
        aoFalhar: (String) -> Unit
    ) {
        Thread {
            try {
                val pasta = File(context.cacheDir, "updates")
                if (!pasta.exists()) pasta.mkdirs()
                val arquivo = File(pasta, "eldoria-v${versao.codigo}.apk")

                val conexao = URL(versao.urlApk).openConnection() as HttpURLConnection
                conexao.connectTimeout = 15000
                conexao.readTimeout = 60000
                conexao.setRequestProperty("User-Agent", "Eldoria-App")

                val total = conexao.contentLengthLong
                conexao.inputStream.use { entrada ->
                    FileOutputStream(arquivo).use { saida ->
                        val buffer = ByteArray(64 * 1024)
                        var lidos = entrada.read(buffer)
                        var totalLido = 0L
                        var ultimoAviso = -1
                        while (lidos >= 0) {
                            saida.write(buffer, 0, lidos)
                            totalLido += lidos
                            if (total > 0) {
                                val pct = ((totalLido * 100) / total).toInt()
                                if (pct != ultimoAviso) {
                                    ultimoAviso = pct
                                    main.post { aoProgresso(pct) }
                                }
                            }
                            lidos = entrada.read(buffer)
                        }
                    }
                }
                conexao.disconnect()
                main.post { aoConcluir(arquivo) }
            } catch (e: Exception) {
                main.post { aoFalhar(e.message ?: "Falha no download") }
            }
        }.start()
    }

    fun podeInstalar(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun intentPermissaoInstalacao(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    /** Abre o instalador do Android com o APK baixado. */
    fun instalar(context: Context, arquivo: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                arquivo
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // se falhar, o app continua funcionando normalmente
        }
    }
}
