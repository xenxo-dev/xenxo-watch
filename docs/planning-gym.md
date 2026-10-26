# Planning de gimnasio — rutina actual

Fuente de datos para `:gym` (rutina del día, pre-rellenado de series y última sesión).
Formato de cada ejercicio: **series × repeticiones → peso por serie**. Si hay varios pesos
(`75 / 70 / 65`), cada uno es el de una serie (series descendentes). Si hay uno solo, vale para todas.
`—` = sin peso apuntado todavía.

## Objetivos diarios

| Concepto | Objetivo |
|---|---|
| Calorías | 1.800 kcal |
| Proteína | 165 g |
| Grasas | 55 g |
| Carbohidratos | 165 g |
| Fibra | 25-30+ g |
| Agua | 2-3 L |
| Pasos | 8.000-10.000 |

---

## 🔴 Lunes — Pecho + Tríceps + Abdomen

| # | Grupo | Ejercicio | Series × reps | Peso (kg) |
|---|---|---|---|---|
| 1 | Pecho | Press banca / máquina convergente | 3 × 6-10 | 75 / 70 / 65 |
| 2 | Pecho | Press inclinado mancuernas | 3 × 8-12 | 55 / 45 / 45 |
| 3 | Pecho | Aperturas en polea | 2-3 × 10-15 | 80 / 70 / 70 |
| 4 | Pecho | Fondos / press declinado | 2 × 8-12 | 79 / 79 |
| 5 | Tríceps | Extensión polea | 3 × 10-15 | 26 / 21 / 21 |
| 6 | Tríceps | Extensión por encima de la cabeza | 2 × 10-15 | 21 / 21 / 18 |
| 7 | Abdomen | Crunch polea | 3 × 8-15 | 65 / 55 / 55 |
| 8 | Abdomen | Elevaciones piernas | 3 × 8-15 | 79 |

Cardio: 15-20 min cinta inclinada.

## 🔵 Martes — Espalda + Bíceps + Pádel

| # | Grupo | Ejercicio | Series × reps | Peso (kg) |
|---|---|---|---|---|
| 1 | Espalda | Dominadas | 3 × 6-10 | 79 |
| 2 | Espalda | Remo pecho apoyado | 3 × 8-12 | 80 |
| 3 | Espalda | Jalón agarre neutro | 2 × 10-15 | 80 |
| 4 | Espalda | Remo unilateral polea | 3 × 8-12 | 35 |
| 5 | Espalda | Face pulls / reverse fly | 2 × 12-20 | 17,5 |
| 6 | Bíceps | Curl barra | 3 × 8-12 | 40 |
| 7 | Bíceps | Curl inclinado | 2 × 10-15 | 15 |
| 8 | Bíceps | Curl martillo | 2 × 10-15 | 12,5 |

🏓 Pádel a las 21:00. Sin cinta.

## 🟢 Miércoles — Descanso

8.000-10.000 pasos. Movilidad opcional.

## 🟠 Jueves — Hombros + Brazos + Abdomen + Pierna ligera

| # | Grupo | Ejercicio | Series × reps | Peso (kg) |
|---|---|---|---|---|
| 1 | Hombros | Press militar / máquina | 3 × 6-10 | 40 |
| 2 | Hombros | Elevaciones laterales | 4 × 10-20 | 12,5 |
| 3 | Hombros | Reverse pec deck | 3 × 12-20 | 80 |
| 4 | Hombros | Elevaciones laterales polea | 2 × 12-20 | — |
| 5 | Brazos | Curl bíceps polea | 2 × 10-15 | 25 |
| 6 | Brazos | Extensión tríceps polea | 2 × 10-15 | 25 |
| 7 | Pierna | Curl femoral | 2 × 10-15 | 35 |
| 8 | Pierna | Extensión cuádriceps | 2 × 10-15 | 42,5 |
| 9 | Abdomen | Crunch polea | 3 × 8-15 | 65 |
| 10 | Abdomen | Elevaciones piernas | 3 × 8-15 | 79 |

Cardio: 15-20 min.
🏓 Si hay pádel: quitar pierna ligera (7-8) y cardio.

## 🟣 Viernes — Pierna + Abdomen

| # | Grupo | Ejercicio | Series × reps | Peso (kg) |
|---|---|---|---|---|
| 1 | Pierna | Hack squat / sentadilla | 3 × 6-10 | — |
| 2 | Pierna | Prensa | 3 × 8-12 | 80 |
| 3 | Pierna | Peso muerto rumano | 2-3 × 6-10 | — |
| 4 | Pierna | Curl femoral | 3 × 10-15 | 35 |
| 5 | Pierna | Extensión cuádriceps | 2 × 10-15 | 42,5 |
| 6 | Pierna | Abductores/aductores | 2 × 10-15 | 42,5 |
| 7 | Abdomen | Crunch polea | 3 × 8-15 | 65 |

Cardio: opcional 10-15 min suave.

## ⚪ Sábado — Descanso / actividad

8.000-10.000 pasos. 🏓 Si hay pádel, cuenta como actividad/cardio.

## ⚪ Domingo — Descanso

Descanso completo o paseo tranquilo.

---

## Notas para `:gym` (pendientes de confirmar)

- **Ejercicios repetidos entre días** (Crunch polea, Elevaciones piernas, Curl femoral, Extensión
  cuádriceps): mismo ejercicio en la base de datos, con historial común.
- **«A / B»** (Press banca / máquina convergente, Fondos / press declinado…): se trata como un único
  ejercicio con la variante elegible el día que se hace.
- **79 kg** en dominadas, fondos y elevaciones de piernas: posiblemente peso corporal (sin lastre).
- **Rangos de series** (`2-3`): la app propone el máximo y se puede terminar antes.
- **Faltan por definir**: descanso por ejercicio y paso de peso de la corona (0,5 / 1 / 2,5 kg).
- **Pádel** los martes a las 21:00 (y opcional jueves/sábado): acceso directo al marcador `:padel`.
