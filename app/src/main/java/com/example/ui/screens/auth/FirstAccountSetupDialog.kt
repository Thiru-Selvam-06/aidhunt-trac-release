
package com.example.ui.screens.auth
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.example.ui.utils.trackFocusedField
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.AppSettingsEntity
private val DeepSageGreen = Color(0xFF1B4332)
private val ForestGreenHeader = Color(0xFF133220)
private val SoftSageGreen = Color(0xFFE8F0EA)
private val SageBorder = Color(0xFF81C784)
@Composable
fun FirstAccountSetupDialog(
    settings: AppSettingsEntity,
    initialDisplayName: String = "",
    initialBusinessName: String = "",
    isTamil: Boolean = false,
    onCompleteSetup: (displayName: String, businessName: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var displayName by remember {
        mutableStateOf(
            initialDisplayName.ifBlank {
                settings.ownerName.ifBlank { settings.activePartnerName }
            }
        )
    }
    var businessName by remember {
        mutableStateOf(
            initialBusinessName.ifBlank { settings.businessName }
        )
    }
    var hasValidated by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val nameRequester = remember { BringIntoViewRequester() }
    val businessRequester = remember { BringIntoViewRequester() }
    val isDisplayNameValid = displayName.trim().isNotBlank()
    val isBusinessNameValid = businessName.trim().isNotBlank()
    val canSubmit = isDisplayNameValid && isBusinessNameValid
    Dialog(
        onDismissRequest = { /* Non-dismissable until setup is completed */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(SoftSageGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Agriculture,
                            contentDescription = null,
                            tint = DeepSageGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isTamil) "சுயவிவர அமைவு" else "Welcome to AIDHUNT Trac",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isTamil)
                            "தொடங்குவதற்கு உங்கள் பெயர் மற்றும் வணிகப் பெயரை உள்ளிடவும்"
                        else
                            "Set up your profile and tractor business name to get started.",
                        fontSize = 13.sp,
                        color = Color(0xFF555555),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    // 1. Display Name / Owner Name
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = {
                            Text(
                                if (isTamil) "உங்கள் பெயர் (உரிமையாளர் / பங்குதாரர்)" else "Your Name (Owner / Partner)",
                                fontSize = 13.sp
                            )
                        },
                        placeholder = { Text(if (isTamil) "எ.கா: சிவா" else "e.g. Siva") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = DeepSageGreen)
                        },
                        singleLine = true,
                        isError = hasValidated && !isDisplayNameValid,
                        supportingText = {
                            if (hasValidated && !isDisplayNameValid) {
                                Text(
                                    if (isTamil) "பெயர் தேவை" else "Please enter your name",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 11.sp
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepSageGreen,
                            focusedLabelColor = DeepSageGreen,
                            unfocusedBorderColor = SageBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .trackFocusedField(nameRequester, coroutineScope)
                            .testTag("input_setup_display_name")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 2. Business Name / Fleet Name
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = {
                            Text(
                                if (isTamil) "வணிகம் / டிராக்டர் பெயர்" else "Business / Fleet Name",
                                fontSize = 13.sp
                            )
                        },
                        placeholder = { Text(if (isTamil) "எ.கா: சிவா டிராக்டர் சர்வீசஸ்" else "e.g. Siva Tractor Services") },
                        leadingIcon = {
                            Icon(Icons.Default.Business, contentDescription = null, tint = DeepSageGreen)
                        },
                        singleLine = true,
                        isError = hasValidated && !isBusinessNameValid,
                        supportingText = {
                            if (hasValidated && !isBusinessNameValid) {
                                Text(
                                    if (isTamil) "வணிகப் பெயர் தேவை" else "Please enter your business name",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 11.sp
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepSageGreen,
                            focusedLabelColor = DeepSageGreen,
                            unfocusedBorderColor = SageBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .trackFocusedField(businessRequester, coroutineScope)
                            .testTag("input_setup_business_name")
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    // Submit Button
                    Button(
                        onClick = {
                            hasValidated = true
                            if (canSubmit && !isSubmitting) {
                                isSubmitting = true
                                onCompleteSetup(displayName.trim(), businessName.trim())
                            }
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_complete_setup")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTamil) "அமைப்பை முடித்து தொடங்கு" else "Complete & Get Started",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
