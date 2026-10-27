package com.mountzou.flytly.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mountzou.flytly.R
import com.mountzou.flytly.atlas.*

@Composable
internal fun AccountPage(details: AccountDetails?, busy: Boolean, error: String?,
                         onSignOut: (() -> Unit)?, onDelete: (() -> Unit)?, onClearError: () -> Unit) {
    var confirmsDeletion by rememberSaveable { mutableStateOf(false) }
    val red = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFFF453A) else Color(0xFFFF3B30)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("accountView")
        .padding(horizontal = 24.dp).padding(top = 12.dp,bottom = 32.dp)) {
        AccountPageHeader("Account","Manage your profile and sign-in.")
        details?.let {
            Column(Modifier.fillMaxWidth().clipAccountCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AccountDetail("NAME",it.name)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                AccountDetail("EMAIL",it.email)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                AccountDetail("LOGIN METHOD",it.loginMethods)
            }
            Spacer(Modifier.height(28.dp))
        }
        AccountAction("Log out","signOutButton",enabled = !busy && onSignOut != null, onClick = { onSignOut?.invoke() })
        Spacer(Modifier.height(14.dp))
        AccountAction("Delete my account","deleteAccountButton",enabled = !busy && onDelete != null, destructive = true, onClick = {
            onClearError();confirmsDeletion = true
        })
        Text("Permanently delete your account and the visits and wishlist saved on this device.",
            Modifier.padding(top = 12.dp), fontSize = 13.sp,lineHeight = 18.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) Text("Completing your request…",Modifier.padding(top = 28.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("accountBusy"),
            fontSize = 13.sp,lineHeight = 18.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it,Modifier.padding(top = 28.dp).testTag("accountError").semantics { liveRegion = LiveRegionMode.Polite },fontSize = 13.sp,lineHeight = 18.sp,color = red) }
    }
    if (confirmsDeletion && !busy) AlertDialog(onDismissRequest = { confirmsDeletion = false },
        title = { Text("Delete your account?") },
        text = { Text("This permanently deletes your Flytly account and the visits and wishlist on this device. You’ll confirm your identity with your login provider first. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmsDeletion = false;onDelete?.invoke() },modifier = Modifier.testTag("confirmAccountDeletion")) { Text("Delete my account",color = red) } },
        dismissButton = { TextButton(onClick = { confirmsDeletion = false }) { Text("Cancel") } })
}

@Composable
internal fun AccountPageHeader(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 30.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title,fontSize = 34.sp,lineHeight = 41.sp,fontWeight = FontWeight.Bold,modifier = Modifier.semantics { heading() })
        Text(description,fontSize = 15.sp,lineHeight = 20.sp,color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Modifier.clipAccountCard() = this.background(cardColor(),RoundedCornerShape(20.dp))
@Composable private fun AccountDetail(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceCaption(label)
        SelectionContainer { Text(value?.takeIf { it.isNotBlank() } ?: "Not provided",fontSize = 17.sp,lineHeight = 22.sp,fontWeight = FontWeight.SemiBold) }
    }
}
@Composable private fun AccountAction(text: String, tag: String, enabled: Boolean, destructive: Boolean = false, onClick: () -> Unit) {
    val red = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFFF453A) else Color(0xFFFF3B30)
    Surface(onClick,Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (enabled) 1f else .5f).testTag(tag),enabled = enabled,shape = RoundedCornerShape(12.dp),
        color = if (destructive) red.copy(alpha = .1f) else placeAccent(),contentColor = if (destructive) red else placeOnAccent()) {
        Row(Modifier.padding(horizontal = 20.dp,vertical = 14.dp),horizontalArrangement = Arrangement.spacedBy(12.dp,androidx.compose.ui.Alignment.CenterHorizontally),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (destructive) Icon(painterResource(R.drawable.ic_atlas_trash),null,Modifier.size(20.dp))
            Text(text,fontSize = 17.sp,lineHeight = 24.sp,fontWeight = FontWeight.Medium)
        }
    }
}
