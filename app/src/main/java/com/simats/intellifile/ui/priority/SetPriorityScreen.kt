package com.simats.intellifile.ui.priority

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.ui.home.PriorityLevel

data class PriorityOptionInfo(
    val priority: PriorityLevel,
    val description: String,
    val icon: ImageVector
)

val priorityOptions = listOf(
    PriorityOptionInfo(
        priority = PriorityLevel.IMPORTANT,
        description = "High priority files",
        icon = Icons.Default.PriorityHigh
    ),
    PriorityOptionInfo(
        priority = PriorityLevel.NORMAL,
        description = "Regular files",
        icon = Icons.Default.Remove
    ),
    PriorityOptionInfo(
        priority = PriorityLevel.LOW,
        description = "Less important files",
        icon = Icons.Default.Check
    )
)

@Composable
fun SetPriorityScreen(
    fileName: String = "AML Assignment.pdf",
    fileSize: String = "2.4 MB",
    fileFormat: String = "PDF",
    iconResId: Int = R.drawable.pdf,
    initialPriority: PriorityLevel = PriorityLevel.IMPORTANT,
    onBackClick: () -> Unit = {},
    onSavePriority: (PriorityLevel) -> Unit = {}
) {
    var selectedPriority by remember { mutableStateOf(initialPriority) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Set Priority",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File Card Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = "File Type Icon",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = fileName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$fileFormat · $fileSize",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instruction Sub-header
            Text(
                text = "Choose a priority for this file",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Horizontal Priority Buttons Row
            HorizontalPrioritySelectorRow(
                selectedPriority = selectedPriority,
                onPrioritySelected = { selectedPriority = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Priority Selection Options List
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                priorityOptions.forEach { option ->
                    val isSelected = option.priority == selectedPriority
                    PriorityOptionCard(
                        option = option,
                        isSelected = isSelected,
                        onClick = { selectedPriority = option.priority }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Priority Button
            Button(
                onClick = { onSavePriority(selectedPriority) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Save Priority",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PriorityOptionCard(
    option: PriorityOptionInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) option.priority.backgroundColor else Color.White
    val cardBorder = if (isSelected) {
        Modifier.border(
            width = 1.5.dp,
            color = option.priority.textColor,
            shape = RoundedCornerShape(18.dp)
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .then(cardBorder)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = option.priority.badgeResId),
            contentDescription = option.priority.title,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = option.priority.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = option.priority.textColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = option.description,
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(option.priority.textColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun HorizontalPrioritySelectorRow(
    selectedPriority: PriorityLevel,
    onPrioritySelected: (PriorityLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PriorityLevel.entries.forEach { priority ->
            val isSelected = priority == selectedPriority
            PriorityButtonCard(
                priority = priority,
                isSelected = isSelected,
                onClick = { onPrioritySelected(priority) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PriorityButtonCard(
    priority: PriorityLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnCardScale"
    )

    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = priority.textColor,
            shape = RoundedCornerShape(20.dp)
        )
    } else {
        Modifier.border(
            width = 1.5.dp,
            color = priority.borderColor,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = priority.glowColor,
                spotColor = priority.glowColor,
                clip = false
            )
            .clip(RoundedCornerShape(20.dp))
            .background(brush = Brush.verticalGradient(priority.gradientColors))
            .then(borderModifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val shineBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.80f),
                    Color.White.copy(alpha = 0.25f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.70f, size.height * 0.45f)
            )
            val cornerR = 18.dp.toPx()
            val strokeW = 2.dp.toPx()
            val path = Path().apply {
                moveTo(cornerR, strokeW)
                lineTo(size.width * 0.65f, strokeW)
                quadraticTo(
                    size.width * 0.35f, size.height * 0.30f,
                    strokeW, size.height * 0.52f
                )
                lineTo(strokeW, cornerR)
                arcTo(
                    rect = Rect(strokeW, strokeW, cornerR * 2, cornerR * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                close()
            }
            drawPath(path = path, brush = shineBrush)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = priority.badgeResId),
                contentDescription = priority.title,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = priority.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = priority.textColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetPriorityScreenPreview() {
    SetPriorityScreen()
}
