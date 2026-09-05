package com.aliqo.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
        AliqoIcon.ROCK -> R.drawable.aliqo_rock
        AliqoIcon.PAPER -> R.drawable.aliqo_paper
        AliqoIcon.SCISSORS -> R.drawable.aliqo_scissors
    }

    val imageModifier=modifier.size(size).alpha(if(active)1f else 0.72f)
    if(type==AliqoIcon.ROCK){
        Image(
            bitmap=rememberCleanDarkEdgeBitmap(drawable),
            contentDescription=null,
            contentScale=ContentScale.Fit,
            modifier=imageModifier
        )
    }else{
        Image(
            painter=painterResource(drawable),
            contentDescription=null,
            contentScale=ContentScale.Fit,
            modifier=imageModifier
        )
    }
}
