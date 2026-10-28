package com.simats.intellifile.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.ui.theme.IntellifileTheme

@Composable
fun PermissionScreen(
    onSelectFilesClick: () -> Unit = {},
    onSelectFolderClick: () -> Unit = {},
    onMaybeLaterClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Launcher for Android 11+ (API 30+) Document / All Files Access Permission
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onSelectFilesClick()
    }

    // Launcher for Android 10 and below standard runtime permission
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onSelectFilesClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF8F8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Bar with Back Arrow
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF183A3C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Center Folder Illustration with Shield Badge
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Folder Logo
                Image(
                    painter = painterResource(id = R.drawable.mainlogo),
                    contentDescription = "Main Folder Logo",
                    modifier = Modifier.size(120.dp)
                )

                // Shield Badge on bottom right
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-10).dp, y = (-10).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD5ECEE))
                        .border(2.dp, Color(0xFF26E07F), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Permission Granted",
                        tint = Color(0xFF26E07F),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title Text
            Text(
                text = "Allow IntelliFile\nto access your documents",
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF183A3C),
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Permission Points List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PermissionPointItem(
                    icon = Icons.Default.Person,
                    text = "Access documents (PDF, DOC, PPT, XLS, TXT)\nfor organizing"
                )

                PermissionPointItem(
                    icon = Icons.Default.Shield,
                    text = "Your files remain private on phone"
                )

                PermissionPointItem(
                    icon = Icons.Default.Folder,
                    text = "No images, music, or media accessed"
                )

                PermissionPointItem(
                    icon = Icons.Default.PhoneAndroid,
                    text = "Files are never uploaded without your consent"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Grant Document Access Button
            Button(
                onClick = {
                    if (LocalFileManager.hasStoragePermission(context)) {
                        onSelectFilesClick()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(fallbackIntent)
                        }
                    } else {
                        legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF55A9AF)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant Document Access",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Maybe Later Text
            Text(
                text = "Maybe Later",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF537779),
                modifier = Modifier
                    .clickable(onClick = onMaybeLaterClick)
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PermissionPointItem(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF55A9AF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF183A3C),
            lineHeight = 18.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionScreenPreview() {
    IntellifileTheme {
        PermissionScreen()
    }
}
