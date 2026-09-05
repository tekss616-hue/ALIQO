package com.aliqo.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HomeBg = Color(0xFF061126)
private val HomeCard = Color(0xFF0C1830)
private val HomeMuted = Color(0xFFAEB8D1)
private val HomeWhite = Color(0xFFF7F9FF)
private val HomePurple = Color(0xFF7C32F2)
private val HomePurple2 = Color(0xFFB14DFF)
private val HomeGreen = Color(0xFF18D67C)

@Composable
fun ApprovedHomeDashboard(
    me: UserDto?,
    onlineFriends: List<UserDto>,
    unread: Int,
    onMatch: () -> Unit,
    onRooms: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBg)
    ) {
        val viewportHeight = maxHeight
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = viewportHeight)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ALIQO", color = HomeWhite, fontSize = 31.sp, fontWeight = FontWeight.Black)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ساحتك تبدأ من هنا", color = HomeMuted, fontSize = 13.sp)
                            Spacer(Modifier.width(5.dp))
                            AliqoArenaIcon(AliqoIcon.SWORDS, size = 20.dp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clickable(onClick = onNotifications),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        AliqoArenaIcon(AliqoIcon.BELL, size = 31.dp)
                        if (unread > 0) {
                            Surface(shape = CircleShape, color = Color(0xFFE64A68)) {
                                Text(
                                    unread.coerceAtMost(99).toString(),
                                    Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(HomePurple2, HomePurple)))
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(HomeCard)
                            .clickable(onClick = onProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (me?.profile?.displayName?.ifBlank { me.username } ?: me?.username ?: "A")
                                .take(1)
                                .uppercase(),
                            color = HomeWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onMatch),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF160B38),
                                        Color(0xFF24105A),
                                        Color(0xFF082A57),
                                        Color(0xFF09172F)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(Modifier.fillMaxWidth(.70f)) {
                            Text("جاهز للمواجهة؟", color = HomeWhite, fontSize = 23.sp, fontWeight = FontWeight.Black)
                            Text(
                                "تحدَّ لاعبين، حقق انتصارات،\nواصعد في الساحة",
                                color = Color(0xFFDDE3F4),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF7624E8)) {
                                Text(
                                    "ابدأ تحديًا  ›",
                                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            Modifier.align(Alignment.CenterEnd).size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AliqoArenaIcon(AliqoIcon.GAMEPAD, size = 90.dp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureCard(
                        Modifier.weight(1f),
                        AliqoIcon.SWORDS,
                        "التحديات",
                        "واجه لاعبين\nواختبر مهاراتك",
                        "ابدأ التحدي",
                        Brush.linearGradient(listOf(Color(0xFF211044), Color(0xFF35146B), Color(0xFF161333))),
                        onMatch
                    )
                    FeatureCard(
                        Modifier.weight(1f),
                        AliqoIcon.ROOMS,
                        "الرومات",
                        "ادخل محادثات جماعية\nحسب اهتماماتك",
                        "استكشف الآن",
                        Brush.linearGradient(listOf(Color(0xFF071E3E), Color(0xFF0A315A), Color(0xFF101A3A))),
                        onRooms
                    )
                }

                Spacer(Modifier.height(10.dp))

                Card(
                    Modifier.fillMaxWidth().heightIn(min = 108.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = HomeCard)
                ) {
                    Column(Modifier.fillMaxSize().padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(HomeGreen))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "اللاعبون في الساحة",
                                color = HomeWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (onlineFriends.isEmpty()) "0 متصل" else "${onlineFriends.size} متصل",
                                color = HomeMuted,
                                fontSize = 12.sp
                            )
                        }
                        if (onlineFriends.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("لا يوجد أصدقاء متصلون الآن", color = HomeMuted, fontSize = 12.sp)
                        } else {
                            Spacer(Modifier.height(9.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                onlineFriends.take(10).forEach { friend ->
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF25234B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            (friend.profile?.displayName?.ifBlank { friend.username } ?: friend.username)
                                                .take(1)
                                                .uppercase(),
                                            color = HomeWhite,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier,
    icon: AliqoIcon,
    title: String,
    subtitle: String,
    action: String,
    brush: Brush,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFF365A91).copy(alpha = .45f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .background(brush)
                .padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AliqoArenaIcon(icon, size = 60.dp)
            Spacer(Modifier.height(2.dp))
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                subtitle,
                color = Color(0xFFD6DDF0),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
            Spacer(Modifier.weight(1f))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = .045f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .32f))
            ) {
                Text(
                    "$action  ›",
                    Modifier.padding(vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
