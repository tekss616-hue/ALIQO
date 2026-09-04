package com.aliqo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AliqoIcon {
    HOME, FRIENDS, BELL, PROFILE, SWORDS, ROOMS, TROPHY, SPEED, PREDICT,
    COMPAT, DETECTIVE, NUMBER, PUZZLE, AVOID, GAMEPAD, ROCK, PAPER,
    SCISSORS, PLAYER, CROWN, STATS, HELP, LOGOUT, SETTINGS, CHAT, SEARCH,
    MESSAGE, DOWNLOAD, UPLOAD, BOOKMARK
}

@Composable
fun AliqoArenaIcon(
    type: AliqoIcon,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    active: Boolean = true
) {
    val drawable = when (type) {
        AliqoIcon.HOME -> R.drawable.home
        AliqoIcon.FRIENDS, AliqoIcon.ROOMS -> R.drawable.group
        AliqoIcon.BELL -> R.drawable.bell
        AliqoIcon.PROFILE, AliqoIcon.PLAYER -> R.drawable.profile
        AliqoIcon.SWORDS -> R.drawable.swords
        AliqoIcon.TROPHY -> R.drawable.trophy
        AliqoIcon.SPEED -> R.drawable.speed
        AliqoIcon.PREDICT, AliqoIcon.DETECTIVE -> R.drawable.detective
        AliqoIcon.COMPAT -> R.drawable.compatibility
        AliqoIcon.NUMBER -> R.drawable.secret_number
        AliqoIcon.PUZZLE -> R.drawable.puzzle_code
        AliqoIcon.AVOID -> R.drawable.avoid_same
        AliqoIcon.GAMEPAD -> R.drawable.gamepad
        AliqoIcon.CROWN -> R.drawable.crown
        AliqoIcon.STATS -> R.drawable.stats
        AliqoIcon.HELP -> R.drawable.help
        AliqoIcon.LOGOUT -> R.drawable.logout
        AliqoIcon.SETTINGS -> R.drawable.settings
        AliqoIcon.CHAT -> R.drawable.chat
        AliqoIcon.SEARCH -> R.drawable.search
        AliqoIcon.MESSAGE -> R.drawable.message
        AliqoIcon.DOWNLOAD -> R.drawable.download
        AliqoIcon.UPLOAD -> R.drawable.upload
        AliqoIcon.BOOKMARK -> R.drawable.bookmark
        AliqoIcon.ROCK, AliqoIcon.PAPER, AliqoIcon.SCISSORS -> null
    }

    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .alpha(if (active) 1f else 0.72f)
        )
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
                    moveTo(w * .16f, h * .70f)
                    lineTo(w * .24f, h * .35f)
                    lineTo(w * .42f, h * .16f)
                    lineTo(w * .65f, h * .22f)
                    lineTo(w * .84f, h * .45f)
                    lineTo(w * .77f, h * .77f)
                    lineTo(w * .54f, h * .86f)
                    lineTo(w * .27f, h * .80f)
                    close()
                }
                drawPath(p, primary)
                drawPath(p, white.copy(alpha = .55f), style = Stroke(sw * .45f))
                line(Offset(w * .32f, h * .52f), Offset(w * .64f, h * .43f), cyan, sw * .5f)
            }
            AliqoIcon.PAPER -> {
                val p = Path().apply {
                    moveTo(w * .25f, h * .12f)
                    lineTo(w * .72f, h * .12f)
                    lineTo(w * .82f, h * .24f)
                    lineTo(w * .82f, h * .88f)
                    lineTo(w * .25f, h * .88f)
                    close()
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
