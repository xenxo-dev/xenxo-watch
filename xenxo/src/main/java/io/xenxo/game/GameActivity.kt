package io.xenxo.game

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import com.google.android.wearable.compat.WearableActivityController
import io.xenxo.health.HealthManager
import io.xenxo.sync.XenxoSync

/**
 * Actividad principal del videojuego "Ruta xenxo" (xenxoRun) en el reloj.
 *
 * Características:
 * - Renderizado Canvas a 60 fps sobre SurfaceView sin asignaciones en el ciclo.
 * - Soporte de controles táctiles (toque / deslizamientos) y corona rotatoria hardware.
 * - Soporte de modo ambiente Wear OS para apagado inmediato del bucle y cero consumo en reposo.
 * - Multiplicador de boost activado automáticamente si el usuario supera 3000 pasos diarios reales.
 * - Sincronización automática de resultados mediante Data Layer hacia xenxoHub.
 */
class GameActivity : Activity() {

    companion object {
        private const val TAG = "xenxo-game"
        private const val STEP_BOOST_THRESHOLD = 3000
    }

    private lateinit var gameView: GameView
    private var ambient: WearableActivityController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantener pantalla encendida mientras se juega activamente
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = GameView(this)
        setContentView(gameView)

        // Verificar boost por pasos reales del día
        try {
            val healthManager = HealthManager(this)
            val todaySteps = healthManager.currentSteps
            if (todaySteps >= STEP_BOOST_THRESHOLD) {
                gameView.engine.stepBoostActive = true
                Log.i(TAG, "Boost de pasos activado (pasos hoy: $todaySteps)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo consultar pasos para boost: ${e.message}")
        }

        gameView.onExitRequested = {
            finish()
        }

        gameView.onRunFinished = { record ->
            Log.i(TAG, "Partida finalizada: score=${record.score}, dist=${record.distanceMeters}m. Sincronizando...")
            XenxoSync.sync(this)
        }

        setupAmbient()
    }

    private fun setupAmbient() {
        ambient = runCatching {
            WearableActivityController(TAG, this, object : WearableActivityController.AmbientCallback() {
                override fun onEnterAmbient(details: Bundle?) {
                    Log.i(TAG, "Ambiente ON: deteniendo bucle de juego")
                    gameView.onPauseGame()
                }

                override fun onExitAmbient() {
                    Log.i(TAG, "Ambiente OFF")
                    gameView.onResumeGame()
                }
            }).also {
                it.onCreate()
                it.setAmbientEnabled()
            }
        }.onFailure {
            Log.w(TAG, "Sin soporte nativo de WearableActivityController: ${it.message}")
        }.getOrNull()
    }

    override fun onResume() {
        super.onResume()
        ambient?.onResume()
        gameView.onResumeGame()
    }

    override fun onPause() {
        ambient?.onPause()
        gameView.onPauseGame()
        super.onPause()
    }

    override fun onStop() {
        ambient?.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        ambient?.onDestroy()
        super.onDestroy()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            (event.source and InputDevice.SOURCE_ROTARY_ENCODER) == InputDevice.SOURCE_ROTARY_ENCODER
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta > 0.25f) {
                gameView.engine.moveRight()
                return true
            } else if (delta < -0.25f) {
                gameView.engine.moveLeft()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
