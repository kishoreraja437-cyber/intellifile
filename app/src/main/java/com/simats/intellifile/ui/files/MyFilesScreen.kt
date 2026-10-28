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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PrioritySummaryCard

enum class StorageSourceTab {
    DEVICE_FILES,
    AI_VAULT
}

@Composable
fun MyFilesScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onQuickAccessClick: (PriorityLevel) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf(StorageSourceTab.DEVICE_FILES) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var realFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        realFiles = LocalFileManager.getDeviceFiles(context)
        isLoading = false
    }

    val totalFiles = realFiles.size
    val importantCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.IMPORTANT } }
    val normalCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.NORMAL } }
    val lowCount = remember(realFiles) { realFiles.count { it.priority == PriorityLevel.LOW } }
    val unprioritizedCount = remember(realFiles) { realFiles.count { it.priority == null } }

    val vaultFilesCount = remember(realFiles) { realFiles.count { it.isVaultSynced } }
    val totalPrioritized = importantCount + normalCount + lowCount

    // Sweep angles calculated strictly from user-assigned priorities
    val importantSweep = if (totalPrioritized > 0) (importantCount.toFloat() / totalPrioritized) * 360f else 0f
    val normalSweep = if (totalPrioritized > 0) (normalCount.toFloat() / totalPrioritized) * 360f else 0f
    val lowSweep = if (totalPrioritized > 0) (lowCount.toFloat() / totalPrioritized) * 360f else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
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

                // Storage Source Selector: Device Files vs AI Vault
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        // Device Files Tab
                        val isDeviceSelected = selectedSource == StorageSourceTab.DEVICE_FILES
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDeviceSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { selectedSource = StorageSourceTab.DEVICE_FILES }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isDeviceSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Device Files ($totalFiles)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDeviceSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // AI Vault Tab
                        val isVaultSelected = selectedSource == StorageSourceTab.AI_VAULT
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isVaultSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { selectedSource = StorageSourceTab.AI_VAULT }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isVaultSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Vault ($vaultFilesCount)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVaultSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedSource == StorageSourceTab.DEVICE_FILES) {
                    // Summary Cards Row with Real User-Marked Counts
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrioritySummaryCard(
                                priority = PriorityLevel.IMPORTANT,
                                count = importantCount,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onQuickAccessClick(PriorityLevel.IMPORTANT) }
                            )
                            PrioritySummaryCard(
                                priority = PriorityLevel.NORMAL,
                                count = normalCount,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onQuickAccessClick(PriorityLevel.NORMAL) }
                            )
                            PrioritySummaryCard(
                                priority = PriorityLevel.LOW,
                                count = lowCount,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onQuickAccessClick(PriorityLevel.LOW) }
                            )
                        }
                    }

                    // Total Files Card with Real Document Count
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
                            Column {
                                Text(
                                    text = "Total Documents",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$totalPrioritized prioritized · $unprioritizedCount unassigned",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }

                            Text(
                                text = totalFiles.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                        }
                    }

                    // Priority Distribution Donut Chart Section
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
                                // Dynamic Donut Chart
                                Box(
                                    modifier = Modifier.size(130.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 22.dp.toPx()
                                        val startAngleOffset = -90f

                                        if (totalPrioritized == 0) {
                                            // Empty state circle
                                            drawArc(
                                                color = Color(0xFFE5E7EB),
                                                startAngle = 0f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = strokeWidth)
                                            )
                                        } else {
                                            // Important segment
                                            if (importantSweep > 0) {
                                                drawArc(
                                                    color = Color(0xFFEF4444),
                                                    startAngle = startAngleOffset,
                                                    sweepAngle = importantSweep,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth)
                                                )
                                            }

                                            // Normal segment
                                            if (normalSweep > 0) {
                                                drawArc(
                                                    color = Color(0xFFF59E0B),
                                                    startAngle = startAngleOffset + importantSweep,
                                                    sweepAngle = normalSweep,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth)
                                                )
                                            }

                                            // Low segment
                                            if (lowSweep > 0) {
                                                drawArc(
                                                    color = Color(0xFF10B981),
                                                    startAngle = startAngleOffset + importantSweep + normalSweep,
                                                    sweepAngle = lowSweep,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = totalPrioritized.toString(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827)
                                        )
                                        Text(
                                            text = "Marked",
                                            fontSize = 12.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(20.dp))

                                // Legend Column with Real Counts
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    DistributionLegendRow(
                                        dotColor = Color(0xFFEF4444),
                                        label = "Important",
                                        countText = "$importantCount files"
                                    )
                                    DistributionLegendRow(
                                        dotColor = Color(0xFFF59E0B),
                                        label = "Normal",
                                        countText = "$normalCount files"
                                    )
                                    DistributionLegendRow(
                                        dotColor = Color(0xFF10B981),
                                        label = "Low",
                                        countText = "$lowCount files"
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
                                label = "Important Files ($importantCount)",
                                badgeColor = Color(0xFFEF4444),
                                ringColor = Color(0xFFFFEAEE),
                                onClick = { onQuickAccessClick(PriorityLevel.IMPORTANT) }
                            )

                            QuickAccessCard(
                                label = "Normal Files ($normalCount)",
                                badgeColor = Color(0xFFD97706),
                                ringColor = Color(0xFFFFF6D8),
                                onClick = { onQuickAccessClick(PriorityLevel.NORMAL) }
                            )

                            QuickAccessCard(
                                label = "Low Priority Files ($lowCount)",
                                badgeColor = Color(0xFF10B981),
                                ringColor = Color(0xFFE2F7ED),
                                onClick = { onQuickAccessClick(PriorityLevel.LOW) }
                            )
                        }
                    }
                } else {
                    // AI Vault Tab Content
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudUpload,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "AI Vault Documents",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                    Text(
                                        text = "$vaultFilesCount documents synced with AI",
                                        fontSize = 13.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF0FDF4))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Privacy Protected",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Your device documents are never uploaded automatically. Documents only enter the AI Vault when you explicitly choose 'Add to AI Vault' or 'Analyze with AI'.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF166534),
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "AI Vault Benefits:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )

                            VaultBenefitItem(
                                title = "Deep Content Extraction",
                                desc = "Server-side text extraction for PDF, DOCX, PPTX, XLSX, and TXT."
                            )
                            VaultBenefitItem(
                                title = "Semantic AI Search & Q&A",
                                desc = "Search by meaning and ask questions about your documents."
                            )
                            VaultBenefitItem(
                                title = "Executive Summaries",
                                desc = "Instant key-takeaway generation powered by AI."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultBenefitItem(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF2563EB))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937)
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
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
