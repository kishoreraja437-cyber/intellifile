package com.simats.intellifile.ui.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.security.AppLockManager

/**
 * App Lock Settings Screen accessible from the Profile screen.
 * Displays only three options matching requirements:
 * 1. App Lock – ON/OFF
 * 2. Fingerprint Unlock – ON/OFF
 * 3. Change PIN
 *
 * Maintains IntelliFile white theme with dusky aqua accents.
 */
@Composable
fun AppLockSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLockManager = remember { AppLockManager.getInstance(context) }

    var isAppLockEnabled by remember { mutableStateOf(appLockManager.isAppLockEnabled()) }
    var isFingerprintEnabled by remember { mutableStateOf(appLockManager.isFingerprintEnabled()) }
    val canUseBiometrics = remember { appLockManager.canUseBiometrics() }

    var activePinModalMode by remember { mutableStateOf<PinDialogMode?>(null) }

    // Synchronize UI states with storage
    fun refreshStates() {
        isAppLockEnabled = appLockManager.isAppLockEnabled()
        isFingerprintEnabled = appLockManager.isFingerprintEnabled()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "App Lock",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Text(
                text = "Protect IntelliFile with PIN or fingerprint authentication whenever the app is opened.",
                fontSize = 13.5.sp,
                color = Color(0xFF64748B),
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Settings Card containing the three options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(vertical = 4.dp)
            ) {
                // 1. App Lock – ON/OFF Switch
                SettingSwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = "App Lock",
                    subtitle = if (isAppLockEnabled) "App requires PIN or fingerprint on open" else "Disabled",
                    isChecked = isAppLockEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!appLockManager.hasPin()) {
                                activePinModalMode = PinDialogMode.CREATE
                            } else {
                                appLockManager.setAppLockEnabled(true)
                                refreshStates()
                                Toast.makeText(context, "App Lock enabled", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Require PIN verification before disabling
                            activePinModalMode = PinDialogMode.DISABLE
                        }
                    }
                )

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // 2. Fingerprint Unlock – ON/OFF Switch
                SettingSwitchRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Fingerprint Unlock",
                    subtitle = when {
                        !canUseBiometrics -> "Fingerprint not available on this device"
                        !isAppLockEnabled -> "Enable App Lock first"
                        isFingerprintEnabled -> "Unlock instantly using your fingerprint"
                        else -> "Use fingerprint as an alternative to PIN"
                    },
                    isChecked = isFingerprintEnabled && isAppLockEnabled && canUseBiometrics,
                    enabled = isAppLockEnabled && canUseBiometrics,
                    onCheckedChange = { checked ->
                        if (!canUseBiometrics) {
                            Toast.makeText(context, "Fingerprint hardware is not available on this device", Toast.LENGTH_SHORT).show()
                            return@SettingSwitchRow
                        }
                        appLockManager.setFingerprintEnabled(checked)
                        refreshStates()
                        val msg = if (checked) "Fingerprint unlock enabled" else "Fingerprint unlock disabled"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // 3. Change PIN Row
                SettingActionRow(
                    icon = Icons.Outlined.Pin,
                    title = "Change PIN",
                    subtitle = if (isAppLockEnabled) "Update your 4 or 6-digit security PIN" else "Enable App Lock first",
                    enabled = isAppLockEnabled,
                    onClick = {
                        if (isAppLockEnabled) {
                            activePinModalMode = PinDialogMode.CHANGE
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Information Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE0F2F1).copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Security Notice",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00695C)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your PIN is stored locally in Android Keystore with cryptographic salting and is never sent to external servers.",
                    fontSize = 12.sp,
                    color = Color(0xFF004D40),
                    lineHeight = 17.sp
                )
            }
        }

        // Active PIN Modal Dialog
        activePinModalMode?.let { mode ->
            PinSetupDialog(
                mode = mode,
                onDismissRequest = {
                    activePinModalMode = null
                    refreshStates()
                },
                onSuccess = {
                    activePinModalMode = null
                    refreshStates()
                }
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) Color(0xFFE0F2F1) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF00897B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color(0xFF0F172A) else Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00897B),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) Color(0xFFE0F2F1) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF00897B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color(0xFF0F172A) else Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) Color(0xFF94A3B8) else Color(0xFFCBD5E1),
            modifier = Modifier.size(20.dp)
        )
    }
}
