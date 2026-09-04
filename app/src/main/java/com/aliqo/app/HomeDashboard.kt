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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HomeBg = Color(0xFF061126)
private val HomeCard = Color(0xFF101C34)
private val HomeCard2 = Color(0xFF15233D)
private val HomeMuted = Color(0xFFAEB8D1)
private val HomeWhite = Color(0xFFF7F9FF)
private val HomePurple = Color(0xFF7C32F2)
private val HomePurple2 = Color(0xFFB14DFF)
private val HomeBlue = Color(0xFF0D8DFF)
private val HomeBlue2 = Color(0xFF2563EB)
private val HomeGreen = Color(0xFF18D67C)

@Composable
fun ApprovedHomeDashboard(
    me: UserDto?,
    onlineFriends: List<UserDto>,
    unread: Int,
    onDiscover: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HomeBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        HomeHeader(me, unread, onNotifications, onProfile)
        HeroBanner()
        FeatureRow(onDiscover)
        UpdateCard()
        OnlineFriendsCard(onlineFriends)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun HomeHeader(me: UserDto?, unread: Int, onNotifications: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("ALIQO", color = HomeWhite, fontSize = 31.sp, fontWeight = FontWeight.Black)
            Text("أكثر من مجرد دردشة", color = HomeMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onNotifications),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (unread > 0) "🔔$unread" else "🔔", fontSize = 21.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(HomePurple2, HomePurple)))
                .padding(2.dp)
                .clip(CircleShape)
                .background(HomeCard)
                .clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (me?.profile?.displayName?.ifBlank { me.username } ?: me?.username ?: "A")
                    .trim().take(1).uppercase(),
                color = HomeWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun HeroBanner() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF351074), Color(0xFF6B2FD4), Color(0xFF1253B8), Color(0xFF0B1735))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "أصدقاء جدد، قصص جديدة",
                    color = HomeWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Start,
                )
                Text(
                    "تواصل، اكتشف، وشارك اهتماماتك",
                    color = Color(0xFFD7DBEA),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Text("●  ○  ○", color = Color(0xFFE4D2FF), fontSize = 13.sp)
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0x667E22CE)),
                contentAlignment = Alignment.Center,
            ) {
                Text("•••", color = Color(0xFF7DD3FC), fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun FeatureRow(onDiscover: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FeatureCard(
            modifier = Modifier.weight(1f),
            icon = "⚡",
            title = "التطابق",
            subtitle = "اكتشف أشخاصًا\nيشبهونك",
            action = "ابدأ الآن",
            brush = Brush.linearGradient(listOf(HomePurple2, HomePurple, Color(0xFF4612B8))),
            onClick = onDiscover,
        )
        FeatureCard(
            modifier = Modifier.weight(1f),
            icon = "👥",
            title = "الرومات",
            subtitle = "ادخل محادثات جماعية\nحسب اهتماماتك",
            action = "استكشف الآن",
            brush = Brush.linearGradient(listOf(Color(0xFF1CA8FF), HomeBlue, HomeBlue2)),
            onClick = onDiscover,
        )
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier,
    icon: String,
    title: String,
    subtitle: String,
    action: String,
    brush: Brush,
    onClick: () -> Unit,
) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(178.dp)
                .background(brush)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(icon, fontSize = 34.sp)
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            ) {
                Text(
                    "$action  ›",
                    Modifier.padding(vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun UpdateCard() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تحديثات التطبيق", color = HomeWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("عرض الكل  ›", color = Color(0xFFC276FF), fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = HomeCard2) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF25154E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📣", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "التطابق والرومات صار لهم قسم مستقل، والخاص يبدأ من قائمة أصدقائك.",
                        color = HomeWhite,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Text("›", color = HomeMuted, fontSize = 21.sp)
                }
            }
        }
    }
}

@Composable
private fun OnlineFriendsCard(friends: List<UserDto>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCard),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(HomeGreen))
                    Spacer(Modifier.width(7.dp))
                    Text("نشط الآن", color = HomeWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.weight(1f))
                Text(if (friends.isEmpty()) "0 متصل" else "عرض الكل  ›", color = if (friends.isEmpty()) HomeMuted else Color(0xFFC276FF), fontSize = 12.sp)
            }
            if (friends.isEmpty()) {
                Text("ما فيه أصدقاء متصلين الآن", color = HomeMuted, fontSize = 12.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    friends.take(10).forEach { friend ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    Modifier.size(46.dp).clip(CircleShape).background(HomeCard2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (friend.profile?.displayName?.ifBlank { friend.username } ?: friend.username).take(1).uppercase(),
                                        color = HomeWhite,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Box(Modifier.size(12.dp).clip(CircleShape).background(HomeGreen))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                friend.profile?.displayName?.ifBlank { friend.username } ?: friend.username,
                                color = HomeWhite,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
