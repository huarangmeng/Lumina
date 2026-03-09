package com.hrm.lumina

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.lumina.core.EmitMode
import com.hrm.lumina.core.ParticleConfig
import com.hrm.lumina.core.ParticleShape
import com.hrm.lumina.core.luminaConfig
import com.hrm.lumina.render.ParticleBlendMode
import com.hrm.lumina.ui.LuminaParticleView

/** 粒子预设数据类 */
data class ParticlePreset(
    val name: String,
    val emoji: String,
    val background: Color,
    val config: ParticleConfig,
    val blendMode: ParticleBlendMode = ParticleBlendMode.Normal,
)

/** 预设列表 */
val presets = listOf(

    // 1. 星云 —— 多彩光球从中心向外扩散，大小差异产生景深感
    ParticlePreset(
        name = "星云",
        emoji = "✨",
        background = Color(0xFF000000),
        blendMode = ParticleBlendMode.Add,
        config = luminaConfig {
            maxCount = 300
            emitRate = 25f
            emitMode = EmitMode.Sphere
            colors = listOf(
                Color(0xFF00E5FF),  // 青色
                Color(0xFFFF1493),  // 品红
                Color(0xFFFFD700),  // 金黄
                Color(0xFFFFFFFF),  // 白色
                Color(0xFF00FF88),  // 绿色
                Color(0xFFFF6600),  // 橙色
                Color(0xFFAA44FF),  // 紫色
            )
            shapes = listOf(ParticleShape.Circle)
            // 小点(0.005) 到 中等光球(0.06)，避免过曝
            sizeRange = 0.005f..0.06f
            lifeRange = 3000f..8000f
            // 速度差异大产生景深感
            speedRange = 0.0001f..0.0015f
            gravity = 0f
            emitRadius = 0.08f   // 从中心小区域发射
            cameraZ = 3f
        }
    ),

    // 2. 万花筒 —— 网格几何旋转
    ParticlePreset(
        name = "万花筒",
        emoji = "🔷",
        background = Color(0xFF0A0015),
        blendMode = ParticleBlendMode.Add,
        config = luminaConfig {
            maxCount = 400
            emitRate = 60f
            emitMode = EmitMode.Grid
            gridColumns = 10
            gridRows = 10
            colors = listOf(
                Color(0xFFFF006E), Color(0xFFFF6B35),
                Color(0xFFFFBE0B), Color(0xFF3A86FF),
                Color(0xFF8338EC), Color(0xFF06FFB4),
            )
            shapes = listOf(
                ParticleShape.Square, ParticleShape.Diamond,
                ParticleShape.Triangle, ParticleShape.Hexagon,
            )
            sizeRange = 0.02f..0.055f
            lifeRange = 2000f..4000f
            speedRange = 0.0002f..0.0008f
            rotationSpeedRange = 0.0005f..0.003f
            gravity = 0f
            emitRadius = 1.8f
            cameraZ = 4f
        }
    ),

    // 3. 星环 —— 圆环发射荧光粒子
    ParticlePreset(
        name = "星环",
        emoji = "⭕",
        background = Color(0xFF000510),
        blendMode = ParticleBlendMode.Add,
        config = luminaConfig {
            maxCount = 500
            emitRate = 80f
            emitMode = EmitMode.Ring
            colors = listOf(
                Color(0xFF00FFFF), Color(0xFF00BFFF),
                Color(0xFF7B2FFF), Color(0xFFFF00FF),
                Color(0xFFFFFFFF),
            )
            shapes = listOf(ParticleShape.Circle)
            sizeRange = 0.008f..0.12f
            lifeRange = 1500f..4000f
            speedRange = 0.0002f..0.002f
            gravity = 0f
            emitRadius = 1.2f
            cameraZ = 4f
        }
    ),

    // 4. 银河 —— 螺旋星形扩散
    ParticlePreset(
        name = "银河",
        emoji = "🌀",
        background = Color(0xFF02000A),
        blendMode = ParticleBlendMode.Add,
        config = luminaConfig {
            maxCount = 600
            emitRate = 100f
            emitMode = EmitMode.Spiral
            colors = listOf(
                Color(0xFFFFFFFF), Color(0xFFFFF8DC),
                Color(0xFFFFD700), Color(0xFFFF8C00),
                Color(0xFFADD8E6), Color(0xFF9370DB),
            )
            shapes = listOf(ParticleShape.Star, ParticleShape.Circle)
            sizeRange = 0.006f..0.08f
            lifeRange = 2000f..5000f
            speedRange = 0.0001f..0.0008f
            rotationSpeedRange = 0.001f..0.005f
            gravity = 0f
            emitRadius = 1.5f
            cameraZ = 5f
        }
    ),

    // 5. 彩虹爆炸 —— 七彩几何爆炸
    ParticlePreset(
        name = "彩虹",
        emoji = "🌈",
        background = Color(0xFF080008),
        blendMode = ParticleBlendMode.Add,
        config = luminaConfig {
            maxCount = 700
            emitRate = 120f
            emitMode = EmitMode.Sphere
            colors = listOf(
                Color(0xFFFF0000), Color(0xFFFF7700),
                Color(0xFFFFFF00), Color(0xFF00FF00),
                Color(0xFF0077FF), Color(0xFF8800FF),
                Color(0xFFFF00FF),
            )
            shapes = listOf(
                ParticleShape.Hexagon, ParticleShape.Triangle,
                ParticleShape.Star, ParticleShape.Diamond,
            )
            sizeRange = 0.015f..0.05f
            lifeRange = 1200f..3000f
            speedRange = 0.0005f..0.003f
            rotationSpeedRange = 0.002f..0.008f
            gravity = 0.00003f
            emitRadius = 0.3f
            cameraZ = 3.5f
        }
    ),
)

@Composable
fun App() {
    MaterialTheme {
        var selectedIndex by remember { mutableStateOf(0) }
        val preset = presets[selectedIndex]

        val bgColor by animateColorAsState(
            targetValue = preset.background,
            animationSpec = tween(700),
            label = "bg"
        )

        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {

            // 粒子层：key 保证切换预设时重建粒子系统
            key(selectedIndex) {
                LuminaParticleView(
                    config = preset.config,
                    blendMode = preset.blendMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 预设名称标题
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = preset.emoji,
                    fontSize = 32.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preset.name,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
            }

            // 底部切换栏
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEachIndexed { index, p ->
                    val isSelected = index == selectedIndex
                    val itemBg by animateColorAsState(
                        targetValue = if (isSelected) Color.White.copy(alpha = 0.2f)
                                      else Color.Transparent,
                        animationSpec = tween(300),
                        label = "item_bg_$index"
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(itemBg)
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = p.emoji, fontSize = 20.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = p.name,
                                color = Color.White.copy(alpha = if (isSelected) 1f else 0.45f),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}