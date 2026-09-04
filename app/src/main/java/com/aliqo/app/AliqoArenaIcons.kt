package com.aliqo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class AliqoIcon { HOME, FRIENDS, BELL, PROFILE, SWORDS, ROOMS, TROPHY, SPEED, PREDICT, COMPAT, DETECTIVE, NUMBER, PUZZLE, AVOID, GAMEPAD, ROCK, PAPER, SCISSORS, PLAYER }

@Composable
fun AliqoArenaIcon(
    type: AliqoIcon,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    active: Boolean = true
) {
    val drawable = when (type) {
        AliqoIcon.HOME -> R.drawable.aliqo_logo
        AliqoIcon.FRIENDS, AliqoIcon.ROOMS -> R.drawable.aliqo_group
        AliqoIcon.BELL -> R.drawable.aliqo_bell
        AliqoIcon.PROFILE, AliqoIcon.PLAYER -> R.drawable.aliqo_profile
        AliqoIcon.SWORDS -> R.drawable.aliqo_swords
        AliqoIcon.TROPHY -> R.drawable.aliqo_trophy
        AliqoIcon.SPEED -> R.drawable.aliqo_speed
        AliqoIcon.PREDICT, AliqoIcon.DETECTIVE -> R.drawable.aliqo_spy
        AliqoIcon.COMPAT -> R.drawable.aliqo_community
        AliqoIcon.NUMBER -> R.drawable.aliqo_number
        AliqoIcon.PUZZLE -> R.drawable.aliqo_puzzle
        AliqoIcon.AVOID -> R.drawable.aliqo_boost
        AliqoIcon.GAMEPAD -> R.drawable.aliqo_gamepad
        AliqoIcon.ROCK, AliqoIcon.PAPER, AliqoIcon.SCISSORS -> null
    }

    if (drawable != null) {
        val resources = LocalContext.current.resources
        val image = remember(drawable) { ImageBitmap.imageResource(resources, drawable) }
        Canvas(modifier.size(size).alpha(if (active) 1f else 0.72f)) {
            // Screen blending makes the dark baked-in PNG background disappear into
            // ALIQO's navy/gradient cards while preserving the neon 3D artwork.
            drawImage(
                image = image,
                dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                blendMode = BlendMode.Screen
            )
        }
        return
    }

    val primary = if (active) Color(0xFFA855F7) else Color(0xFF8B96B3)
    val cyan = if (active) Color(0xFF22C7FF) else Color(0xFF8B96B3)
    val white = Color(0xFFF5F2FF)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * .075f
        fun line(a: Offset, b: Offset, c: Color = primary, s: Float = sw) =
            drawLine(c, a, b, s, StrokeCap.Round)
        when (type) {
            AliqoIcon.ROCK -> {
                val p = Path().apply {
                    moveTo(w * .16f, h * .70f); lineTo(w * .24f, h * .35f)
                    lineTo(w * .42f, h * .16f); lineTo(w * .65f, h * .22f)
                    lineTo(w * .84f, h * .45f); lineTo(w * .77f, h * .77f)
                    lineTo(w * .54f, h * .86f); lineTo(w * .27f, h * .80f); close()
                }
                drawPath(p, primary)
                drawPath(p, white.copy(alpha = .55f), style = Stroke(sw * .45f))
                line(Offset(w * .32f, h * .52f), Offset(w * .64f, h * .43f), cyan, sw * .5f)
            }
            AliqoIcon.PAPER -> {
                val p = Path().apply {
                    moveTo(w * .25f, h * .12f); lineTo(w * .72f, h * .12f)
                    lineTo(w * .82f, h * .24f); lineTo(w * .82f, h * .88f)
                    lineTo(w * .25f, h * .88f); close()
                }
                drawPath(p, primary)
                line(Offset(w * .35f, h * .40f), Offset(w * .68f, h * .40f), cyan, sw * .55f)
                line(Offset(w * .35f, h * .56f), Offset(w * .68f, h * .56f), white, sw * .45f)
                line(Offset(w * .35f, h * .72f), Offset(w * .60f, h * .72f), cyan, sw * .45f)
            }
            AliqoIcon.SCISSORS -> {
                drawCircle(primary, w * .14f, Offset(w * .30f, h * .72f))
                drawCircle(cyan, w * .14f, Offset(w * .70f, h * .72f))
                drawCircle(Color(0xFF071126), w * .075f, Offset(w * .30f, h * .72f))
                drawCircle(Color(0xFF071126), w * .075f, Offset(w * .70f, h * .72f))
                line(Offset(w * .39f, h * .61f), Offset(w * .76f, h * .14f), white, sw)
                line(Offset(w * .61f, h * .61f), Offset(w * .24f, h * .14f), cyan, sw)
            }
            else -> Unit
        }
    }
}
