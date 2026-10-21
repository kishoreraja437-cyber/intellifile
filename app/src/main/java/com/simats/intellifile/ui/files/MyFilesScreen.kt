package com.simats.intellifile.ui.files

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PrioritySummaryCard

@Composable
fun MyFilesScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onQuickAccessClick: (PriorityLevel) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Page Title
                item {
                    Text(
                        text = "My Files",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Summary Cards Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PriorityLevel.entries.forEach { level ->
                            PrioritySummaryCard(
                                priority = level,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onQuickAccessClick(level) }
                            )
                        }
                    }
                }

                // Total Files Card
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Files",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        Text(
                            text = "29",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                    }
                }

                // Priority Distribution Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Priority Distribution",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut Chart
                            Box(
                                modifier = Modifier.size(130.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 22.dp.toPx()
                                    val startAngleOffset = -90f

                                    // Important segment (28% -> ~100.8 deg)
                                    drawArc(
                                        color = Color(0xFFEF4444),
                                        startAngle = startAngleOffset,
                                        sweepAngle = 100.8f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth)
                                    )

                                    // Normal segment (52% -> ~187.2 deg)
                                    drawArc(
                                        color = Color(0xFFF59E0B),
                                        startAngle = startAngleOffset + 100.8f,
                                        sweepAngle = 187.2f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth)
                                    )

                                    // Blue segment (20% -> 72 deg)
                                    drawArc(
                                        color = Color(0xFF3B82F6),
                                        startAngle = startAngleOffset + 100.8f + 187.2f,
                                        sweepAngle = 72f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "29",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                    Text(
                                        text = "Files",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Legend Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DistributionLegendRow(
                                    dotColor = Color(0xFFEF4444),
                                    label = "Important",
                                    countText = "8 (28%)"
                                )
                                DistributionLegendRow(
                                    dotColor = Color(0xFFF59E0B),
                                    label = "Normal",
                                    countText = "15 (52%)"
                                )
                                DistributionLegendRow(
                                    dotColor = Color(0xFF10B981),
                                    label = "Low",
                                    countText = "6 (20%)"
                                )
                            }
                        }
                    }
                }

                // Quick Access Section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Quick Access",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        QuickAccessCard(
                            label = "Important Files",
                            badgeColor = Color(0xFFEF4444),
                            ringColor = Color(0xFFFFEAEE),
                            onClick = { onQuickAccessClick(PriorityLevel.IMPORTANT) }
                        )

                        QuickAccessCard(
                            label = "Normal Files",
                            badgeColor = Color(0xFFD97706),
                            ringColor = Color(0xFFFFF6D8),
                            onClick = { onQuickAccessClick(PriorityLevel.NORMAL) }
                        )

                        QuickAccessCard(
                            label = "Low Priority Files",
                            badgeColor = Color(0xFF10B981),
                            ringColor = Color(0xFFE2F7ED),
                            onClick = { onQuickAccessClick(PriorityLevel.LOW) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DistributionLegendRow(
    dotColor: Color,
    label: String,
    countText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
        }

        Text(
            text = countText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827)
        )
    }
}

@Composable
fun QuickAccessCard(
    label: String,
    badgeColor: Color,
    ringColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = Color(0xFF374151),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MyFilesScreenPreview() {
    MyFilesScreen()
}
