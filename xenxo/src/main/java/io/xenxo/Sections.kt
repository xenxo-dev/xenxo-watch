package io.xenxo

/*
 * Cada sección de xenxo es su propia "app" en el panal del launcher, con nombre e icono propios
 * (petición del usuario: "Gym xenxo", "Pádel xenxo"…). Misma app y misma base de datos por dentro.
 * El marcador de pádel ("Pádel xenxo") es PadelActivity; su historial se abre desde el marcador.
 */

/** "Energía xenxo": batería, perfiles y sincronización con el móvil. */
class EnergyActivity : MainActivity() {
    override val fixedSection = Section.POWER
}

/** "Gym xenxo": rutina del día, series, descansos y peso en ayunas. */
class GymActivity : MainActivity() {
    override val fixedSection = Section.GYM
}

/** "Salud xenxo": pasos, calorías, distancia, frecuencia cardíaca en vivo e historial. */
class HealthActivity : MainActivity() {
    override val fixedSection = Section.HEALTH
}

/** Historial de partidos y CSV, abierto desde el marcador de "Pádel xenxo". */
class PadelHistoryActivity : MainActivity() {
    override val fixedSection = Section.PADEL
}

/** "Música xenxo": reproductor local de 20 canciones con auto-renovación y soporte para AirPods vía Bluetooth. */
class MusicActivity : MainActivity() {
    override val fixedSection = Section.MUSIC
}
