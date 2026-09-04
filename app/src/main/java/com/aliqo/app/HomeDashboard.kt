package com.aliqo.app

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

private val HomeBg = Color(0xFF071126)
private val HomeCard = Color(0xFF101B33)
private val HomeCard2 = Color(0xFF15213B)
private val HomeMuted = Color(0xFFAAB5D2)
private val HomeWhite = Color(0xFFF8FAFF)
private val HomePurple = Color(0xFF7A35F2)
private val HomePurple2 = Color(0xFFB866FF)
private val HomeBlue = Color(0xFF0D8BFF)
private val HomeBlue2 = Color(0xFF2563EB)
private val HomeGreen = Color(0xFF16D67A)

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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeHeader(me, unread, onNotifications, onProfile)
        HeroBanner()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = "⚡",
                title = "التطابق",
                subtitle = "اكتشف أشخاصًا يشبهونك",
                action = "ابدأ الآن",
                brush = Brush.linearGradient(listOf(HomePurple2, HomePurple, Color(0xFF3C12A6))),
                onClick = onDiscover,
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = "👥",
                title = "الرومات",
                subtitle = "ادخل محادثات جماعية حسب اهتماماتك",
                action = "استكشف الآن",
                brush = Brush.linearGradient(listOf(Color(0xFF22A8FF), HomeBlue, HomeBlue2)),
                onClick = onDiscover,
            )
        }
        UpdateCard()
        OnlineFriendsCard(onlineFriends)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun HomeHeader(me: UserDto?, unread: Int, onNotifications: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("ALIQO", color = HomeWhite, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onNotifications),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (unread > 0) "🔔$unread" else "🔔", fontSize = 23.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(48.dp)
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
                fontSize = 20.sp,
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
                .height(188.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF25105A), Color(0xFF7131D7), Color(0xFF163B8B), Color(0xFF0B1734))
                    )
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("أصدقاء جدد", color = HomeWhite, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Text("\"قصص جديدة\"", color = HomeWhite, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(18.dp))
                Text("ALIQO", color = HomeWhite, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("More Than Chat", color = Color(0xFFE2E7F5), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("●  ○  ○", color = Color(0xFFCFA7FF), fontSize = 16.sp)
            }
        }
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
                .height(238.dp)
                .background(brush)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(icon, fontSize = 43.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
            Spacer(Modifier.weight(1f))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            ) {
                Text(
                    "$action  ›",
                    Modifier.padding(vertical = 11.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تحديثات التطبيق", color = HomeWhite, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("عرض الكل  ›", color = Color(0xFFBE7CFF), fontSize = 14.sp)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = HomeCard2) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "التطابق والرومات صار لهم قسم مستقل، والخاص يبدأ من قائمة أصدقائك.",
                        color = HomeWhite,
                        modifier = Modifier.weight(1f),
                        lineHeight = 22.sp,
                    )
                    Text("›", color = HomeMuted, fontSize = 26.sp)
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("نشط الآن", color = HomeWhite, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("${friends.size} متصل", color = HomeMuted, fontSize = 14.sp)
            }
            if (friends.isEmpty()) {
                Text("ما فيه أصدقاء متصلين الآن", color = HomeMuted)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    friends.take(10).forEach { friend ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    Modifier.size(54.dp).clip(CircleShape).background(HomeCard2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (friend.profile?.displayName?.ifBlank { friend.username } ?: friend.username).take(1).uppercase(),
                                        color = HomeWhite,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Box(Modifier.size(13.dp).clip(CircleShape).background(HomeGreen))
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                friend.profile?.displayName?.ifBlank { friend.username } ?: friend.username,
                                color = HomeWhite,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
