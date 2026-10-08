package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.PartnerEntity
import com.example.data.firebase.AccountLookupResult
import com.example.ui.theme.rememberResponsiveDimensions
import com.example.ui.utils.trackFocusedField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class AuthMethod {
    PHONE,
    EMAIL,
    GOOGLE
}

/**
 * Model representing a user-friendly custom error popup.
 */
data class LoginErrorState(
    val title: String,
    val message: String,
    val subMessage: String? = null,
    val primaryActionLabel: String? = null,
    val onPrimaryAction: (() -> Unit)? = null,
    val dismissLabel: String = "Close",
    val onDismiss: () -> Unit = {}
)

@Composable
fun LoginScreen(
    partners: List<PartnerEntity> = emptyList(),
    isTamil: Boolean = false,
    onToggleLanguage: (() -> Unit)? = null,
    onLoginSuccess: (phone: String, otp: String) -> Unit,
    onGoogleSignInRequested: (() -> Unit)? = null,
    onEmailSignInRequested: ((email: String, password: String, onNoAccount: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onEmailSignUpRequested: ((email: String, password: String) -> Unit)? = null,
    onSendOtpRequested: ((phoneNumber: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onVerifyOtpRequested: ((otpCode: String, onError: (String) -> Unit) -> Unit)? = null,
    onQuickPartnerSelected: ((PartnerEntity) -> Unit)? = null,
    onGmailLoginRequested: ((email: String) -> Unit)? = null,
    onCheckPhoneAccount: (suspend (phoneNumber: String) -> AccountLookupResult)? = null,
    onPhoneCreateAccountRequested: ((phoneNumber: String) -> Unit)? = null,
    onPhoneRegisterAccount: ((fullName: String, businessName: String, onError: (String) -> Unit) -> Unit)? = null,
    onCancelPhoneAccountCreation: (() -> Unit)? = null,
    isOnline: Boolean = true,
    isGoogleAccountNotFound: Boolean = false,
    onGoogleCreateAccountRequested: (() -> Unit)? = null,
    onGoogleCancelRequested: (() -> Unit)? = null,
    isLoggingIn: Boolean = false,
    errorMessage: String? = null,
    initialAuthMethod: AuthMethod = AuthMethod.PHONE
) {
    var selectedMethod by rememberSaveable { mutableStateOf(initialAuthMethod) }

    // Phone Auth State
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var isOtpSent by rememberSaveable { mutableStateOf(false) }
    var resendCountdown by rememberSaveable { mutableIntStateOf(28) }

    LaunchedEffect(isOtpSent) {
        if (isOtpSent) {
            resendCountdown = 28
            while (resendCountdown > 0) {
                delay(1000L)
                resendCountdown--
            }
        }
    }

    var isRequestingOtpLocally by rememberSaveable { mutableStateOf(false) }
    var notFoundPhoneNumber by rememberSaveable { mutableStateOf("") }
    var isCreatingPhoneAccount by rememberSaveable { mutableStateOf(false) }
    var newAccountFullName by rememberSaveable { mutableStateOf("") }
    var newAccountBusinessName by rememberSaveable { mutableStateOf("") }

    // Email & Password Auth State
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }

    // Reusable Custom Error Popup State
    var activeErrorState by remember { mutableStateOf<LoginErrorState?>(null) }
    var isVerifyingOtp by rememberSaveable { mutableStateOf(false) }

    val responsive = rememberResponsiveDimensions()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val phoneRequester = remember { BringIntoViewRequester() }
    val otpRequester = remember { BringIntoViewRequester() }
    val emailRequester = remember { BringIntoViewRequester() }
    val passwordRequester = remember { BringIntoViewRequester() }
    val hiddenOtpFocusRequester = remember { FocusRequester() }

    fun submitOtpVerification(code: String) {
        if (isVerifyingOtp) return
        val cleanCode = code.trim()
        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "தவறான OTP" else "Invalid OTP",
                message = if (isTamil) "தயவுசெய்து சரியான 6 இலக்க OTP குறியீட்டை உள்ளிடவும்." else "Please enter a valid 6-digit OTP code.",
                dismissLabel = if (isTamil) "சரி" else "OK",
                onDismiss = {
                    activeErrorState = null
                    isVerifyingOtp = false
                }
            )
            return
        }
        if (phoneNumber.isBlank()) return

        isVerifyingOtp = true
        focusManager.clearFocus()

        // 12-second watchdog guard so the verification NEVER hangs indefinitely
        coroutineScope.launch {
            delay(12000L)
            if (isVerifyingOtp) {
                isVerifyingOtp = false
                activeErrorState = LoginErrorState(
                    title = if (isTamil) "இணைப்பு சிக்கல்" else "Connection problem",
                    message = if (isTamil) "சரிபார்க்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்." else "Unable to verify OTP. Please check your internet connection and try again.",
                    primaryActionLabel = if (isTamil) "மீண்டும் முயற்சி" else "Try Again",
                    onPrimaryAction = {
                        activeErrorState = null
                        submitOtpVerification(cleanCode)
                    },
                    dismissLabel = if (isTamil) "சரி" else "OK",
                    onDismiss = {
                        activeErrorState = null
                        isVerifyingOtp = false
                    }
                )
            }
        }

        if (onVerifyOtpRequested != null) {
            onVerifyOtpRequested.invoke(cleanCode) { err ->
                isVerifyingOtp = false
                activeErrorState = mapRawErrorToLoginError(
                    rawError = err,
                    isTamil = isTamil,
                    onRetry = { submitOtpVerification(cleanCode) },
                    onResendOtp = {
                        if (phoneNumber.isNotBlank() && onSendOtpRequested != null) {
                            resendCountdown = 28
                            onSendOtpRequested.invoke(phoneNumber.trim(), {}, { resendErr ->
                                activeErrorState = mapRawErrorToLoginError(resendErr, isTamil)
                            })
                        }
                    },
                    onCreateAccount = {
                        activeErrorState = null
                        isCreatingPhoneAccount = true
                        onPhoneCreateAccountRequested?.invoke(phoneNumber)
                    }
                )
            }
        } else {
            onLoginSuccess(phoneNumber, cleanCode)
            isVerifyingOtp = false
        }
    }

    // Intercept external errorMessage from MainActivity and map to custom error popup
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            isVerifyingOtp = false
            activeErrorState = mapRawErrorToLoginError(
                rawError = errorMessage,
                isTamil = isTamil,
                onRetry = {
                    if (otpCode.length == 6) {
                        submitOtpVerification(otpCode)
                    }
                },
                onResendOtp = {
                    if (phoneNumber.isNotBlank() && onSendOtpRequested != null) {
                        resendCountdown = 28
                        onSendOtpRequested.invoke(phoneNumber.trim(), {}, { err ->
                            activeErrorState = mapRawErrorToLoginError(err, isTamil)
                        })
                    }
                },
                onCreateAccount = {
                    activeErrorState = null
                    isCreatingPhoneAccount = true
                    onPhoneCreateAccountRequested?.invoke(phoneNumber)
                }
            )
        }
    }

    // Intercept Google account not found and map to custom error popup
    LaunchedEffect(isGoogleAccountNotFound) {
        if (isGoogleAccountNotFound) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "கணக்கு கிடைக்கவில்லை" else "No account found",
                message = if (isTamil) "இந்த Google கணக்கிற்கு AIDHUNT Trac கணக்கு எதுவும் இல்லை." else "No AIDHUNT Trac account is associated with this Google account.",
                primaryActionLabel = if (isTamil) "கணக்கு உருவாக்கவும்" else "Create Account",
                onPrimaryAction = {
                    activeErrorState = null
                    onGoogleCreateAccountRequested?.invoke()
                },
                dismissLabel = if (isTamil) "ரத்து செய்" else "Cancel",
                onDismiss = {
                    activeErrorState = null
                    onGoogleCancelRequested?.invoke()
                }
            )
        }
    }

    // Auto-focus OTP hidden field when OTP is sent
    LaunchedEffect(isOtpSent) {
        if (isOtpSent) {
            delay(150L)
            try {
                hiddenOtpFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    fun submitPhoneLogin() {
        if (isRequestingOtpLocally || isLoggingIn) return
        focusManager.clearFocus()
        otpCode = ""

        val canonical = com.example.data.firebase.normalizePhoneNumber(phoneNumber)
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length < 10 || canonical.isBlank()) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "செல்லுபடியாகாத தொலைபேசி எண்" else "Invalid phone number",
                message = if (isTamil) "தயவுசெய்து சரியான 10 இலக்க மொபைல் எண்ணை உள்ளிடவும்." else "Please enter a valid 10-digit mobile number.",
                dismissLabel = if (isTamil) "சரி" else "OK",
                onDismiss = {
                    activeErrorState = null
                    isRequestingOtpLocally = false
                }
            )
            isRequestingOtpLocally = false
            return
        }

        if (!isOnline) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "இணைப்பு சிக்கல்" else "Connection problem",
                message = if (isTamil) "இணைக்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்." else "Please check your internet connection and try again.",
                primaryActionLabel = if (isTamil) "மீண்டும் முயற்சி" else "Try Again",
                onPrimaryAction = {
                    activeErrorState = null
                    isRequestingOtpLocally = false
                    submitPhoneLogin()
                },
                dismissLabel = if (isTamil) "மூடு" else "Close",
                onDismiss = {
                    activeErrorState = null
                    isRequestingOtpLocally = false
                }
            )
            isRequestingOtpLocally = false
            return
        }

        // Direct OTP dispatch via Firebase Phone Authentication - NO pre-OTP Firestore lookup gate
        isRequestingOtpLocally = true
        val sendWatchdog = coroutineScope.launch {
            delay(15000L)
            if (isRequestingOtpLocally) {
                isRequestingOtpLocally = false
                activeErrorState = LoginErrorState(
                    title = if (isTamil) "இணைப்பு சிக்கல்" else "Connection problem",
                    message = if (isTamil) "OTP அனுப்ப முடியவில்லை. மீண்டும் முயற்சிக்கவும்." else "Unable to send OTP. Please check your connection and try again.",
                    primaryActionLabel = if (isTamil) "மீண்டும் முயற்சி" else "Try Again",
                    onPrimaryAction = {
                        activeErrorState = null
                        submitPhoneLogin()
                    },
                    dismissLabel = if (isTamil) "சரி" else "OK",
                    onDismiss = { activeErrorState = null }
                )
            }
        }
        onSendOtpRequested?.invoke(
            canonical,
            {
                sendWatchdog.cancel()
                isRequestingOtpLocally = false
                isOtpSent = true
                resendCountdown = 28
            },
            { err ->
                sendWatchdog.cancel()
                isRequestingOtpLocally = false
                activeErrorState = mapRawErrorToLoginError(err, isTamil, onRetry = { submitPhoneLogin() })
            }
        )
    }

    fun submitEmailAuth() {
        focusManager.clearFocus()

        if (!isOnline) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "இணைப்பு சிக்கல்" else "Connection problem",
                message = if (isTamil) "இணைக்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்." else "Please check your internet connection and try again.",
                dismissLabel = if (isTamil) "சரி" else "OK",
                onDismiss = { activeErrorState = null }
            )
            return
        }

        val trimmedEmail = emailInput.trim()
        val trimmedPassword = passwordInput
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

        if (trimmedEmail.isBlank() || !emailRegex.matches(trimmedEmail)) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "செல்லுபடியாகாத மின்னஞ்சல்" else "Invalid email",
                message = if (isTamil) "சரியான மின்னஞ்சல் முகவரியை உள்ளிடவும்." else "Please enter a valid email address.",
                dismissLabel = if (isTamil) "சரி" else "OK",
                onDismiss = { activeErrorState = null }
            )
            return
        }

        if (trimmedPassword.length < 6) {
            activeErrorState = LoginErrorState(
                title = if (isTamil) "கடவுச்சொல் பிழை" else "Password too short",
                message = if (isTamil) "கடவுச்சொல் குறைந்தது 6 எழுத்துகள் இருக்க வேண்டும்." else "Password must be at least 6 characters.",
                dismissLabel = if (isTamil) "சரி" else "OK",
                onDismiss = { activeErrorState = null }
            )
            return
        }

        if (!isLoggingIn) {
            if (isSignUpMode) {
                onEmailSignUpRequested?.invoke(trimmedEmail, trimmedPassword)
            } else {
                onEmailSignInRequested?.invoke(
                    trimmedEmail,
                    trimmedPassword,
                    {
                        // No account found
                        activeErrorState = LoginErrorState(
                            title = if (isTamil) "கணக்கு கிடைக்கவில்லை" else "No account found",
                            message = if (isTamil) "இந்த மின்னஞ்சலில் AIDHUNT Trac கணக்கு எதுவும் இல்லை." else "No AIDHUNT Trac account is associated with this email.",
                            subMessage = trimmedEmail,
                            primaryActionLabel = if (isTamil) "கணக்கு உருவாக்கவும்" else "Create Account",
                            onPrimaryAction = {
                                isSignUpMode = true
                                activeErrorState = null
                            },
                            dismissLabel = if (isTamil) "மூடு" else "Close",
                            onDismiss = { activeErrorState = null }
                        )
                    },
                    { err ->
                        activeErrorState = mapRawErrorToLoginError(err, isTamil)
                    }
                )
            }
        }
    }

    // Main Layout Surface
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isCreatingPhoneAccount) {
                PhoneAccountCreationContent(
                    phoneNumber = phoneNumber,
                    isTamil = isTamil,
                    onSubmit = { fName, bName ->
                        onPhoneRegisterAccount?.invoke(fName, bName) { err ->
                            activeErrorState = mapRawErrorToLoginError(err, isTamil)
                        }
                    },
                    onBackToLogin = {
                        isCreatingPhoneAccount = false
                        activeErrorState = null
                        otpCode = ""
                        isOtpSent = false
                        onCancelPhoneAccountCreation?.invoke()
                    }
                )
            } else {
                // Background Decorative Art: Top-Left Leaves & Bottom Agricultural Landscape
                LoginBackgroundDecorations(modifier = Modifier.fillMaxSize())

            // Main Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ─────────────────────────────────────────────────────────────
                // 1. TOP LANGUAGE TOGGLE BUTTON (TOP-RIGHT)
                // ─────────────────────────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onToggleLanguage != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { onToggleLanguage() }
                                .testTag("login_language_toggle")
                        ) {
                            Text(
                                text = if (isTamil) "English" else "தமிழ்",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Spacing between top area and Tractor Logo
                Spacer(modifier = Modifier.height(44.dp))

                // ─────────────────────────────────────────────────────────────
                // 2. TRACTOR EMBLEM & APP TITLE
                // ─────────────────────────────────────────────────────────────
                Image(
                    painter = painterResource(id = R.drawable.ic_login_tractor_emblem),
                    contentDescription = "AIDHUNT Trac Logo",
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isTamil) "AiDHUNT டிராக்" else "AiDHUNT Trac",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = if (isTamil) "பகிர்வு டிராக்டர் வணிக நிர்வாகம்" else "Shared Tractor Business Management",
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(38.dp))

                // ─────────────────────────────────────────────────────────────
                // 3. LOGIN METHOD SELECTOR (PHONE / EMAIL / GOOGLE)
                // ─────────────────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isTamil) "உள்நுழைவு முறை" else "Login Method",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Phone Method Pill
                        LoginMethodPill(
                            title = if (isTamil) "தொலைபேசி" else "Phone",
                            icon = Icons.Default.Phone,
                            isSelected = selectedMethod == AuthMethod.PHONE,
                            onClick = { selectedMethod = AuthMethod.PHONE },
                            testTag = "tab_auth_phone",
                            modifier = Modifier.weight(1f)
                        )

                        // Email Method Pill
                        LoginMethodPill(
                            title = if (isTamil) "மின்னஞ்சல்" else "Email",
                            icon = Icons.Default.Email,
                            isSelected = selectedMethod == AuthMethod.EMAIL,
                            onClick = { selectedMethod = AuthMethod.EMAIL },
                            testTag = "tab_auth_email",
                            modifier = Modifier.weight(1f)
                        )

                        // Google Method Pill
                        LoginMethodPill(
                            title = "Google",
                            isGoogle = true,
                            isSelected = selectedMethod == AuthMethod.GOOGLE,
                            onClick = { selectedMethod = AuthMethod.GOOGLE },
                            testTag = "tab_auth_google",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ─────────────────────────────────────────────────────────────
                // 4. MAIN AUTH SECTION (PHONE / EMAIL / GOOGLE)
                // ─────────────────────────────────────────────────────────────
                when (selectedMethod) {
                    AuthMethod.PHONE -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Phone Number Label & Rounded Field
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isTamil) "தொலைபேசி எண்" else "Phone Number",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )

                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = Color(0xFFF3FAF6),
                                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = Color(0xFF0B532E),
                                            modifier = Modifier.size(19.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = "+91 ",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF0F172A)
                                        )

                                        Box(modifier = Modifier.weight(1f)) {
                                            if (phoneNumber.isEmpty()) {
                                                Text(
                                                    text = if (isTamil) "உங்கள் மொபைல் எண்" else "Enter your phone number",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            }
                                            BasicTextField(
                                                value = phoneNumber,
                                                onValueChange = { input ->
                                                    val digits = input.filter { it.isDigit() }
                                                    if (digits.length <= 10) {
                                                        phoneNumber = digits
                                                    }
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color(0xFF0F172A),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Phone,
                                                    imeAction = if (isOtpSent) ImeAction.Next else ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        focusManager.clearFocus()
                                                        if (!isOtpSent) {
                                                            submitPhoneLogin()
                                                        }
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .trackFocusedField(phoneRequester, coroutineScope)
                                                    .testTag("login_phone_input")
                                            )
                                        }

                                        if (phoneNumber.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    phoneNumber = ""
                                                    otpCode = ""
                                                    isOtpSent = false
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Clear phone number",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // If OTP is not yet requested and phone is valid, show Get OTP button
                            if (!isOtpSent) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { submitPhoneLogin() },
                                    enabled = !isLoggingIn && !isRequestingOtpLocally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("send_otp_button"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0B532E),
                                        disabledContainerColor = Color(0xFFE2E8F0)
                                    )
                                ) {
                                    if (isLoggingIn || isRequestingOtpLocally) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isTamil) "OTP அனுப்பப்படுகிறது..." else "Sending OTP...",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = if (isTamil) "OTP பெறுக" else "Get Login OTP",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // ─────────────────────────────────────────────────────────────
                            // 5. OTP SECTION — MATCHES THE REFERENCE EXACTLY
                            // ─────────────────────────────────────────────────────────────
                            if (isOtpSent) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 42.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Section Divider with "Enter 6-Digit OTP"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(0.8.dp)
                                                .background(Color(0xFFE2E8F0))
                                        )
                                        Text(
                                            text = if (isTamil) "6 இலக்க OTP ஐ உள்ளிடவும்" else "Enter 6-Digit OTP",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(0.8.dp)
                                                .background(Color(0xFFE2E8F0))
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = if (isTamil) "உங்கள் மொபைல் எண்ணுக்கு அனுப்பப்பட்ட OTP ஐ உள்ளிடவும்" else "Enter the OTP sent to your mobile number",
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(28.dp))

                                    // 6 Individual OTP Circles using decorationBox
                                    BasicTextField(
                                        value = otpCode,
                                        onValueChange = { input ->
                                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                                otpCode = input
                                                if (input.length < 6) {
                                                    isVerifyingOtp = false
                                                }
                                                if (input.length == 6 && phoneNumber.isNotBlank()) {
                                                    submitOtpVerification(input)
                                                }
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (otpCode.length == 6) {
                                                    submitOtpVerification(otpCode)
                                                }
                                            }
                                        ),
                                        modifier = Modifier
                                            .focusRequester(hiddenOtpFocusRequester)
                                            .testTag("login_otp_input")
                                            .trackFocusedField(otpRequester, coroutineScope),
                                        decorationBox = {
                                            Row(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                repeat(6) { index ->
                                                    val isFilled = index < otpCode.length
                                                    Box(
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White)
                                                            .border(1.5.dp, Color(0xFF0B532E), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isFilled) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF0B532E))
                                                            )
                                                        } else {
                                                            // Centered soft placeholder dots matching reference
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF94A3B8))
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    // Secondary/Manual Verification Arrow Button
                                    val isOtpComplete = otpCode.length == 6
                                    val isArrowLoading = isOtpComplete && isVerifyingOtp
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isOtpComplete) Color(0xFF0B532E) else Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clickable(enabled = isOtpComplete && !isArrowLoading) {
                                                submitOtpVerification(otpCode)
                                            }
                                            .testTag("verify_otp_arrow_button")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (isArrowLoading) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Verify OTP",
                                                    tint = if (isOtpComplete) Color.White else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Resend OTP Countdown / Action matching reference exactly
                                    if (resendCountdown > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = if (isTamil) "OTP மீண்டும் பெற " else "Resend OTP in ",
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                text = "00:${resendCountdown.toString().padStart(2, '0')}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0B532E)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = if (isTamil) "OTP மீண்டும் அனுப்பு" else "Resend OTP",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0B532E),
                                            modifier = Modifier
                                                .clickable {
                                                    resendCountdown = 28
                                                    onSendOtpRequested?.invoke(phoneNumber.trim(), {}, { err ->
                                                        activeErrorState = mapRawErrorToLoginError(err, isTamil)
                                                    })
                                                }
                                                .testTag("resend_otp_button")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AuthMethod.EMAIL -> {
                        // ─────────────────────────────────────────────────────────────
                        // EMAIL AUTHENTICATION VIEW
                        // ─────────────────────────────────────────────────────────────
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSignUpMode) (if (isTamil) "புதிய மின்னஞ்சல் கணக்கு" else "Create Email Account") else (if (isTamil) "மின்னஞ்சல் உள்நுழைவு" else "Email Sign-In"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                    modifier = Modifier.clickable {
                                        isSignUpMode = !isSignUpMode
                                    }
                                ) {
                                    Text(
                                        text = if (isSignUpMode) (if (isTamil) "உள்நுழைவுக்கு மாறவும்" else "Switch to Sign In") else (if (isTamil) "புதியவரா? பதிவு செய்க" else "New? Sign Up"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            // Email input pill
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = Color(0xFFF4FBF7),
                                border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (emailInput.isEmpty()) {
                                            Text(
                                                text = if (isTamil) "name@example.com" else "name@example.com",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 14.5.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = emailInput,
                                            onValueChange = { emailInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = Color(0xFF0F172A),
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Email,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .trackFocusedField(emailRequester, coroutineScope)
                                                .testTag("login_email_input")
                                        )
                                    }
                                }
                            }

                            // Password input pill
                            Surface(
                                shape = RoundedCornerShape(26.dp),
                                color = Color(0xFFF4FBF7),
                                border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (passwordInput.isEmpty()) {
                                            Text(
                                                text = if (isTamil) "கடவுச்சொல் (குறைந்தது 6 எழுத்துகள்)" else "Password (min 6 characters)",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 14.5.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = passwordInput,
                                            onValueChange = { passwordInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = Color(0xFF0F172A),
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = { submitEmailAuth() }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .trackFocusedField(passwordRequester, coroutineScope)
                                                .testTag("login_password_input")
                                        )
                                    }
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle password visibility",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { submitEmailAuth() },
                                enabled = !isLoggingIn && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag(if (isSignUpMode) "login_email_signup_button" else "login_email_signin_button"),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0D5E3A),
                                    disabledContainerColor = Color(0xFFE2E8F0)
                                )
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isSignUpMode) (if (isTamil) "கணக்கை உருவாக்கு" else "Create Account") else (if (isTamil) "மின்னஞ்சல் மூலம் உள்நுழைக" else "Sign In with Email"),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    AuthMethod.GOOGLE -> {
                        // ─────────────────────────────────────────────────────────────
                        // GOOGLE AUTHENTICATION VIEW
                        // ─────────────────────────────────────────────────────────────
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isTamil) "வேகமான மற்றும் பாதுகாப்பான உள்நுழைவுக்கு உங்கள் Google கணக்கைப் பயன்படுத்தவும்." else "Sign in directly with your Google Account for fast, secure cloud backup and synchronization.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (!isOnline) {
                                        activeErrorState = LoginErrorState(
                                            title = if (isTamil) "இணைப்பு சிக்கல்" else "Connection problem",
                                            message = if (isTamil) "இணைக்க முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்." else "Please check your internet connection and try again.",
                                            dismissLabel = if (isTamil) "சரி" else "OK",
                                            onDismiss = { activeErrorState = null }
                                        )
                                        return@Button
                                    }
                                    if (onGoogleSignInRequested != null) {
                                        onGoogleSignInRequested()
                                    } else {
                                        onGmailLoginRequested?.invoke("")
                                    }
                                },
                                enabled = !isLoggingIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("login_gmail_continue_button"),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D5E3A))
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GoogleGLogo(modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (isTamil) "Google மூலம் தொடரவும்" else "Continue with Google",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // 6. QUICK SELECT PARTNER (IF AVAILABLE IN APP DATA)
                // ─────────────────────────────────────────────────────────────
                if (partners.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (isTamil) "— அல்லது பங்குதாரரை விரைவாகத் தேர்ந்தெடுக்கவும் —" else "— Or Quick Select Partner —",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        partners.forEach { partner ->
                            val sanitizedTag = partner.name.lowercase().replace(" ", "_")
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        phoneNumber = partner.phone
                                        otpCode = ""
                                        isOtpSent = false
                                        selectedMethod = AuthMethod.PHONE
                                    }
                                    .testTag("quick_login_$sanitizedTag")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = partner.name,
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "(${partner.role})",
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Generous bottom spacing for agricultural landscape illustration
                Spacer(modifier = Modifier.height(160.dp))
            }
        }

            // ─────────────────────────────────────────────────────────────
            // 7. CUSTOM ERROR POPUP / SURFACE (REPLACES ALL RAW ERRORS)
            // ─────────────────────────────────────────────────────────────
            activeErrorState?.let { err ->
                CustomLoginErrorDialog(
                    errorState = err,
                    onDismiss = {
                        activeErrorState = null
                        isVerifyingOtp = false
                        err.onDismiss()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: LOGIN METHOD SELECTOR PILL (PHONE / EMAIL / GOOGLE)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoginMethodPill(
    title: String,
    icon: ImageVector? = null,
    isGoogle: Boolean = false,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(23.dp),
        color = if (isSelected) Color(0xFF0B532E) else Color.White,
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF0B532E) else Color(0xFFE2E8F0)
        ),
        modifier = modifier
            .height(46.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isGoogle) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_g),
                    contentDescription = "Google",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFF0B532E),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else Color(0xFF0F172A)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: GOOGLE "G" LOGO (OFFICIAL VECTOR)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = R.drawable.ic_google_g),
        contentDescription = "Google",
        tint = Color.Unspecified,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: CUSTOM LOGIN ERROR DIALOG (SURFACE / POPUP)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CustomLoginErrorDialog(
    errorState: LoginErrorState,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("custom_error_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Red Error Icon in Pale Red Circular Badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF2F2))
                        .border(1.5.dp, Color(0xFFFECACA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error Icon",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = errorState.title,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Human-readable Message
                Text(
                    text = errorState.message,
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                // Optional SubMessage (e.g. phone number or identifier)
                if (!errorState.subMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(0.8.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = errorState.subMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (errorState.primaryActionLabel != null && errorState.onPrimaryAction != null) {
                        Button(
                            onClick = { errorState.onPrimaryAction.invoke() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("error_primary_action"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D5E3A))
                        ) {
                            Text(
                                text = errorState.primaryActionLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("error_dismiss_action"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text(
                                text = errorState.dismissLabel,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("error_dismiss_action"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D5E3A))
                        ) {
                            Text(
                                text = errorState.dismissLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: BACKGROUND DECORATIONS (TOP-LEFT LEAVES & BOTTOM TRACTOR LANDSCAPE)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LoginBackgroundDecorations(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // 1. Top-Left Organic Foliage & Pale-Green Curve
        Image(
            painter = painterResource(id = R.drawable.bg_login_top_left),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(160.dp)
                .wrapContentHeight(),
            contentScale = ContentScale.FillWidth
        )

        // 2. Bottom Agricultural Landscape (Rolling Hills, Sprout Foliage, Tractor Silhouette)
        Image(
            painter = painterResource(id = R.drawable.bg_login_bottom_landscape),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight(),
            contentScale = ContentScale.FillWidth
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR MAPPING HELPER FUNCTION (CENTRALIZED ERROR TO USER-FACING CONVERSION)
// ─────────────────────────────────────────────────────────────────────────────
private fun mapRawErrorToLoginError(
    rawError: String?,
    isTamil: Boolean,
    onRetry: (() -> Unit)? = null,
    onResendOtp: (() -> Unit)? = null,
    onCreateAccount: (() -> Unit)? = null
): LoginErrorState? {
    if (rawError.isNullOrBlank()) return null
    val lower = rawError.lowercase()

    return when {
        lower.contains("invalid phone") || lower.contains("10-digit") || lower.contains("10 இலக்க") || lower.contains("செல்லுபடியாகாத எண்") -> {
            LoginErrorState(
                title = if (isTamil) "செல்லுபடியாகாத தொலைபேசி எண்" else "Invalid phone number",
                message = if (isTamil) "தயவுசெய்து சரியான 10 இலக்க மொபைல் எண்ணை உள்ளிடவும்." else "Please enter a valid 10-digit mobile number.",
                dismissLabel = if (isTamil) "சரி" else "OK"
            )
        }
        lower.contains("invalid-verification-code") || lower.contains("invalid code") || lower.contains("invalid otp") || lower.contains("incorrect code") || lower.contains("தவறான otp") -> {
            LoginErrorState(
                title = if (isTamil) "தவறான OTP" else "Invalid OTP",
                message = if (isTamil) "நீங்கள் உள்ளிட்ட OTP தவறானது. தயவுசெய்து சரிபார்த்து மீண்டும் முயற்சிக்கவும்." else "The OTP you entered is incorrect. Please check the code and try again.",
                dismissLabel = if (isTamil) "மீண்டும் முயற்சிக்கவும்" else "Try Again"
            )
        }
        lower.contains("session-expired") || lower.contains("expired") || lower.contains("காலாவதியானது") -> {
            LoginErrorState(
                title = if (isTamil) "OTP காலாவதியானது" else "OTP expired",
                message = if (isTamil) "இந்த OTP காலாவதியானது. தயவுசெய்து புதிய OTP ஐ கோரவும்." else "This OTP has expired. Please request a new OTP.",
                primaryActionLabel = if (isTamil) "OTP மீண்டும் அனுப்பு" else "Resend OTP",
                onPrimaryAction = onResendOtp,
                dismissLabel = if (isTamil) "மூடு" else "Close"
            )
        }
        lower.contains("network") || lower.contains("connect") || lower.contains("timeout") || lower.contains("unavailable") || lower.contains("இணைக்க முடியவில்லை") || lower.contains("இணைய") -> {
            LoginErrorState(
                title = if (isTamil) "இணைப்பு சிக்கல்" else "Connection problem",
                message = if (isTamil) "தயவுசெய்து உங்கள் இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்." else "Please check your internet connection and try again.",
                primaryActionLabel = if (onRetry != null) (if (isTamil) "மீண்டும் முயற்சி" else "Try Again") else null,
                onPrimaryAction = onRetry,
                dismissLabel = if (isTamil) "மூடு" else "Close"
            )
        }
        lower.contains("permission_denied") || lower.contains("permission-denied") || lower.contains("access denied") -> {
            LoginErrorState(
                title = if (isTamil) "அணுகல் மறுக்கப்பட்டது" else "Access denied",
                message = if (isTamil) "சேவையகத்தை அணுக அனுமதி இல்லை. உங்கள் இணைய இணைப்பைச் சரிபார்க்கவும் அல்லது ஆதரவைத் தொடர்பு கொள்ளவும்." else "Unable to access the server. Please check your connection or contact support.",
                dismissLabel = if (isTamil) "சரி" else "OK"
            )
        }
        lower.contains("user-not-found") || lower.contains("not found") || lower.contains("no account") || lower.contains("account not registered") || lower.contains("not registered") -> {
            LoginErrorState(
                title = if (isTamil) "கணக்கு கிடைக்கவில்லை" else "No account found",
                message = if (isTamil) "இந்த மொபைல் எண்ணிற்கு AIDHUNT Trac கணக்கு எதுவும் இல்லை." else "No AIDHUNT Trac account is associated with this phone number.",
                primaryActionLabel = if (isTamil) "கணக்கு உருவாக்கவும்" else "Create Account",
                onPrimaryAction = onCreateAccount,
                dismissLabel = if (isTamil) "மூடு" else "Close"
            )
        }
        lower.contains("wrong-password") || (lower.contains("password") && (lower.contains("incorrect") || lower.contains("invalid"))) -> {
            LoginErrorState(
                title = if (isTamil) "தவறான கடவுச்சொல்" else "Incorrect password",
                message = if (isTamil) "நீங்கள் உள்ளிட்ட கடவுச்சொல் தவறானது. மீண்டும் முயற்சிக்கவும்." else "The password you entered is incorrect. Please try again.",
                dismissLabel = if (isTamil) "சரி" else "OK"
            )
        }
        lower.contains("invalid-email") || lower.contains("badly formatted") -> {
            LoginErrorState(
                title = if (isTamil) "செல்லுபடியாகாத மின்னஞ்சல்" else "Invalid email",
                message = if (isTamil) "சரியான மின்னஞ்சல் முகவரியை உள்ளிடவும்." else "Please enter a valid email address.",
                dismissLabel = if (isTamil) "சரி" else "OK"
            )
        }
        lower.contains("too-many-requests") || lower.contains("blocked") -> {
            LoginErrorState(
                title = if (isTamil) "அதிக முயற்சிகள்" else "Too many attempts",
                message = if (isTamil) "பாதுகாப்பு காரணமாக இந்த சாதனத்திலிருந்து கோரிக்கைகள் தற்காலிகமாக தடுக்கப்பட்டுள்ளன. சிறிது நேரம் கழித்து முயற்சிக்கவும்." else "We have temporarily blocked requests from this device. Please try again later.",
                dismissLabel = if (isTamil) "சரி" else "OK"
            )
        }
        else -> {
            LoginErrorState(
                title = if (isTamil) "ஏதோ தவறு நடந்துவிட்டது" else "Something went wrong",
                message = if (isTamil) "உங்கள் கோரிக்கையை முடிக்க முடியவில்லை. தயவுசெய்து மீண்டும் முயற்சிக்கவும்." else "We couldn't complete your request. Please try again.",
                dismissLabel = if (isTamil) "சரி" else "OK"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: DEDICATED PHONE ACCOUNT CREATION SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PhoneAccountCreationContent(
    phoneNumber: String,
    isTamil: Boolean,
    onSubmit: (fullName: String, businessName: String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var businessName by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        LoginBackgroundDecorations(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToLogin,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = if (isTamil) "பின்செல்" else "Back",
                        tint = Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "புதிய கணக்கு உருவாக்குதல்" else "Create AIDHUNT Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Verified Phone Number Badge Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isTamil) "தொலைபேசி எண் சரிபார்க்கப்பட்டது" else "Phone Verified via OTP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = phoneNumber.ifBlank { "+91 ----------" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Registration Form Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = if (isTamil) "கணக்கு விவரங்கள்" else "Account Details",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (isTamil) "உங்கள் வணிகத்திற்கான விவரங்களை உள்ளிடவும்" else "Set up your workspace and profile",
                        fontSize = 12.5.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Full Name
                    Text(
                        text = if (isTamil) "முழு பெயர்" else "Full Name",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.5.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (fullName.isEmpty()) {
                                        Text(
                                            text = if (isTamil) "எ.கா. ராம்குமார்" else "e.g. John Doe",
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_account_fullname")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Business / Farm Name
                    Text(
                        text = if (isTamil) "வணிகம் / பண்ணை பெயர்" else "Business / Farm Name",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.5.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        ),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (businessName.isEmpty()) {
                                        Text(
                                            text = if (isTamil) "எ.கா. ஸ்ரீ முருகன் டிராக்டர்ஸ்" else "e.g. Green Valley Farm",
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_account_business_name")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Info box indicating registration will be finalized in upcoming release
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTamil)
                                    "புதிய கணக்கு பதிவு செயல்முறை விரைவில் முழுமைபெறும். இந்த எண் அங்கீகரிக்கப்பட்டு தயாராக உள்ளது."
                                else
                                    "Account creation flow is being finalized. Your phone number is verified and ready for workspace setup.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (!isSubmitting && fullName.isNotBlank() && businessName.isNotBlank()) {
                                isSubmitting = true
                                onSubmit(fullName.trim(), businessName.trim())
                            }
                        },
                        enabled = fullName.isNotBlank() && businessName.isNotBlank() && !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_create_account_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0B532E),
                            disabledContainerColor = Color(0xFFE2E8F0)
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isTamil) "கணக்கை உறுதிப்படுத்தவும்" else "Submit Registration",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fullName.isNotBlank() && businessName.isNotBlank()) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onBackToLogin,
                modifier = Modifier.testTag("cancel_create_account_button")
            ) {
                Text(
                    text = if (isTamil) "உள்நுழைவிற்குத் திரும்பு" else "Back to Login",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

