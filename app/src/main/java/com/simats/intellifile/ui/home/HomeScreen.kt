package com.simats.intellifile.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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

enum class PriorityLevel(
    val title: String,
    val count: Int,
    val backgroundColor: Color,
    val iconBackgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector
) {
    IMPORTANT(
        title = "Important",
        count = 8,
        backgroundColor = Color(0xFFFFEAEE),
        iconBackgroundColor = Color(0xFFEF4444),
        textColor = Color(0xFFEF4444),
        icon = Icons.Default.PriorityHigh
    ),
    NORMAL(
        title = "Normal",
        count = 15,
        backgroundColor = Color(0xFFFFF6D8),
        iconBackgroundColor = Color(0xFFD97706),
        textColor = Color(0xFFD97706),
        icon = Icons.Default.Remove
    ),
    LOW(
        title = "Low",
        count = 6,
        backgroundColor = Color(0xFFE2F7ED),
        iconBackgroundColor = Color(0xFF10B981),
        textColor = Color(0xFF10B981),
        icon = Icons.Default.Check
    )
}

data class RecentFile(
    val title: String,
    val format: String,
    val size: String,
    val dateText: String,
    val priority: PriorityLevel,
    val iconResId: Int = R.drawable.pdf
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSeeAllClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onPriorityCardClick: (PriorityLevel) -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val recentFiles = remember {
        listOf(
            RecentFile(
                title = "AML Assignment.pdf",
                format = "PDF",
                size = "2.4 MB",
                dateText = "Today",
                priority = PriorityLevel.IMPORTANT
            ),
            RecentFile(
                title = "DBMS Notes.pdf",
                format = "PDF",
                size = "1.8 MB",
                dateText = "1 day ago",
                priority = PriorityLevel.NORMAL
            ),
            RecentFile(
                title = "Project Report.pdf",
                format = "PDF",
                size = "3.1 MB",
                dateText = "2 days ago",
                priority = PriorityLevel.LOW
            ),
            RecentFile(
                title = "Resume.pdf",
                format = "PDF",
                size = "0.9 MB",
                dateText = "3 days ago",
                priority = PriorityLevel.NORMAL
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Search Bar Section
            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onMicClick = onMicClick
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
                            onClick = { onPriorityCardClick(level) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recent Files Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Files",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "See all",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.clickable { onSeeAllClick() }
                    )
                }
            }

            // Recent File Items
            items(recentFiles) { file ->
                RecentFileCard(
                    file = file,
                    onClick = { onFileClick(file) }
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search Icon",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Ask IntelliFile...",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onMicClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Search",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PrioritySummaryCard(
    priority: PriorityLevel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(priority.backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(priority.iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = priority.icon,
                contentDescription = priority.title,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = priority.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = priority.textColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = priority.count.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RecentFileCard(
    file: RecentFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = file.iconResId),
            contentDescription = "File Type Icon",
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${file.format} · ${file.size} · ${file.dateText}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        PriorityTag(priority = file.priority)
    }
}

@Composable
fun PriorityTag(
    priority: PriorityLevel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(priority.backgroundColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(priority.iconBackgroundColor)
        )

        Spacer(modifier = Modifier.width(5.dp))

        Text(
            text = priority.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = priority.textColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}
