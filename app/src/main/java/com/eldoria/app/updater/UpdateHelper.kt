package com.eldoria.app.updater

import android.app.AlertDialog
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.eldoria.app.R
import com.eldoria.app.databinding.DialogProgressoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Cuida da interface de atualização: diálogos, download com progresso
 * e permissão para instalar apps desconhecidos.
 */
class UpdateHelper(
    private val activity: AppCompatActivity,
    private val launcher: ActivityResultLauncher<Intent>
) {

    private var versaoPendente: Updater.Versao? = null
    private var apkPendente: File? = null

    /** Verificação silenciosa ao abrir o app (máx. 1x a cada 6h). */
    fun verificarAutomatico() {
        Updater.autoCheck(activity) { versao ->
            mostrarDialogoAtualizacao(versao)
        }
    }

    /** Botão "Verificar atualização" das configurações. */
    fun verificarManual() {
        val carregando = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.botao_verificar_atualizacao)
            .setMessage(R.string.atualizacao_procurando)
            .setCancelable(false)
            .create()
        carregando.show()

        Updater.verificar(activity) { resultado ->
            runCatching { carregando.dismiss() }
            when (resultado) {
                is Updater.Resultado.Disponivel -> mostrarDialogoAtualizacao(resultado.versao)
                Updater.Resultado.Atualizado ->
                    mostrarMensagem(activity.getString(R.string.atualizacao_atualizado))
                Updater.Resultado.SemInternet ->
                    mostrarMensagem(activity.getString(R.string.atualizacao_sem_internet))
                is Updater.Resultado.Ero ->
                    mostrarMensagem(activity.getString(R.string.atualizacao_erro))
            }
        }
    }

    /** Chame quando voltar da tela de permissão do Android. */
    fun aoVoltarDaPermissao() {
        if (!Updater.podeInstalar(activity)) return
        val arquivo = apkPendente
        val versao = versaoPendente
        when {
            arquivo != null -> Updater.instalar(activity, arquivo)
            versao != null -> baixar(versao)
        }
    }

    private fun mostrarDialogoAtualizacao(versao: Updater.Versao) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.atualizacao_titulo)
            .setMessage("${activity.getString(R.string.atualizacao_mensagem)}\n\nVersão: ${versao.nome}")
            .setPositiveButton(R.string.atualizacao_agora) { _, _ -> iniciar(versao) }
            .setNegativeButton(R.string.atualizacao_depois, null)
            .show()
    }

    private fun iniciar(versao: Updater.Versao) {
        versaoPendente = versao
        if (!Updater.podeInstalar(activity)) {
            pedirPermissaoInstalacao()
            return
        }
        baixar(versao)
    }

    private fun baixar(versao: Updater.Versao) {
        val binding = DialogProgressoBinding.inflate(activity.layoutInflater)
        val dialogo = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create()
        dialogo.show()

        Updater.baixar(
            activity,
            versao,
            aoProgresso = { pct ->
                binding.barraProgresso.progress = pct
                binding.textPercentual.text = "$pct%"
            },
            aoConcluir = { arquivo ->
                runCatching { dialogo.dismiss() }
                apkPendente = arquivo
                if (Updater.podeInstalar(activity)) {
                    Updater.instalar(activity, arquivo)
                } else {
                    pedirPermissaoInstalacao()
                }
            },
            aoFalhar = {
                runCatching { dialogo.dismiss() }
                mostrarMensagem(activity.getString(R.string.atualizacao_erro))
            }
        )
    }

    private fun pedirPermissaoInstalacao() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permissao_titulo)
            .setMessage(R.string.permissao_mensagem)
            .setPositiveButton(R.string.permissao_permitir) { _, _ ->
                try {
                    launcher.launch(Updater.intentPermissaoInstalacao(activity))
                } catch (_: Exception) {
                    mostrarMensagem(activity.getString(R.string.permissao_mensagem))
                }
            }
            .setNegativeButton(R.string.atualizacao_depois, null)
            .show()
    }

    private fun mostrarMensagem(texto: String) {
        MaterialAlertDialogBuilder(activity)
            .setMessage(texto)
            .setPositiveButton(R.string.novidades_botao, null)
            .show()
    }
}
