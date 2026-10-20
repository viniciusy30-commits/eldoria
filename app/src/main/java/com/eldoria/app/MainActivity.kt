package com.eldoria.app

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.eldoria.app.databinding.ActivityMainBinding
import com.eldoria.app.game.EstadoJogador
import com.eldoria.app.game.Salvamento
import com.eldoria.app.ui.DialogoClasse
import com.eldoria.app.ui.JoystickView
import com.eldoria.app.updater.UpdateHelper
import com.eldoria.app.updater.Updater
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var updateHelper: UpdateHelper

    private val launcherPermissao = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (::updateHelper.isInitialized) updateHelper.aoVoltarDaPermissao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateHelper = UpdateHelper(this, launcherPermissao)

        val jogo = binding.gameView
        Salvamento.carregar(this, jogo.jogador)

        jogo.aoAtualizarEstado = { estado -> runOnUiThread { atualizarHud(estado) } }
        jogo.aoMorrer = { runOnUiThread { mostrarDialogoMorte() } }

        binding.joystick.listener = object : JoystickView.Listener {
            override fun emMovimento(dx: Float, dy: Float) {
                jogo.definirDirecaoJoystick(dx, dy)
            }

            override fun emRepouso() {
                jogo.definirDirecaoJoystick(0f, 0f)
            }
        }

        binding.botaoAtacar.setOnClickListener { jogo.atacar() }

        binding.botaoPocao.setOnClickListener {
            val erro = jogo.usarPocao()
            if (erro != null) mostrarAviso(erro)
        }

        binding.botaoConfiguracoes.setOnClickListener {
            startActivity(Intent(this, ConfiguracoesActivity::class.java))
        }

        atualizarHud(jogo.jogador.estado())
        escolherClasseSePreciso()
        mostrarTutorialSePreciso()
        mostrarNovidadesSeAtualizou()
        updateHelper.verificarAutomatico()
    }

    override fun onResume() {
        super.onResume()
        Salvamento.carregar(this, binding.gameView.jogador)
        binding.gameView.continuar()
    }

    override fun onPause() {
        super.onPause()
        Salvamento.salvar(this, binding.gameView.jogador)
        binding.gameView.pausar()
    }

    private fun atualizarHud(e: EstadoJogador) {
        binding.textNivel.text = "Nv. ${e.nivel} • ${binding.gameView.jogador.classe.nome}"
        binding.textOuro.text = "${e.ouro}"
        binding.textVida.text = "${e.vida} / ${e.vidaMax}"
        binding.textXp.text = "${e.xp} / ${e.xpProx} XP"
        binding.textPocoes.text = "Poções: ${e.pocoes}"

        val pesoVida = (e.vida.toFloat() / e.vidaMax).coerceIn(0f, 1f)
        (binding.barraVida.layoutParams as LinearLayout.LayoutParams).weight = pesoVida
        (binding.barraVidaResto.layoutParams as LinearLayout.LayoutParams).weight = 1f - pesoVida

        val pesoXp = (e.xp.toFloat() / e.xpProx).coerceIn(0f, 1f)
        (binding.barraXp.layoutParams as LinearLayout.LayoutParams).weight = pesoXp
        (binding.barraXpResto.layoutParams as LinearLayout.LayoutParams).weight = 1f - pesoXp

        binding.barraVida.requestLayout()
        binding.barraXp.requestLayout()
    }

    private fun mostrarDialogoMorte() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Você morreu!")
            .setMessage("Um monstro derrotou você. Você renasceu no centro do mapa e perdeu 10% do ouro.")
            .setPositiveButton("Continuar", null)
            .show()
    }

    private fun mostrarAviso(texto: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(texto)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun escolherClasseSePreciso() {
        val prefs = getSharedPreferences("jogo", MODE_PRIVATE)
        if (prefs.getBoolean("escolheu_classe", false)) return
        DialogoClasse.mostrar(this) { classe ->
            val jogador = binding.gameView.jogador
            jogador.classe = classe
            jogador.vidaMax = classe.vidaBase + (jogador.nivel - 1) * 18
            jogador.vida = jogador.vidaMax
            prefs.edit().putBoolean("escolheu_classe", true).apply()
            Salvamento.salvar(this, jogador)
            atualizarHud(jogador.estado())
        }
    }

    private fun mostrarTutorialSePreciso() {
        val prefs = getSharedPreferences("jogo", MODE_PRIVATE)
        if (prefs.getBoolean("viu_tutorial", false)) return
        prefs.edit().putBoolean("viu_tutorial", true).apply()

        MaterialAlertDialogBuilder(this)
            .setTitle("Bem-vindo a Eldoria")
            .setMessage(
                "Use o joystick para andar pelo mundo.\n\n" +
                        "Toque em um monstro ou no botão de ataque para lutar.\n\n" +
                        "Cada abate dá experiência e ouro. Ao subir de nível você fica mais forte.\n\n" +
                        "Use as poções para recuperar vida. Cuidado: os monstros também atacam!"
            )
            .setPositiveButton("Começar", null)
            .show()
    }

    private fun mostrarNovidadesSeAtualizou() {
        val prefs = getSharedPreferences("jogo", MODE_PRIVATE)
        val versaoAtual = Updater.versaoAtual(this)
        val ultimaVista = prefs.getInt("ultima_versao_vista", -1)
        if (ultimaVista != -1 && ultimaVista < versaoAtual) {
            val secao = Novidades.maisRecente(this)
            if (secao != null) Novidades.mostrarDialogo(this, secao)
        }
        prefs.edit().putInt("ultima_versao_vista", versaoAtual).apply()
    }
}
