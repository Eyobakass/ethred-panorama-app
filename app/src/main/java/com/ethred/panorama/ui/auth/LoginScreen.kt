package com.ethred.panorama.ui.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethred.panorama.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var emailError      by remember { mutableStateOf<String?>(null) }
    var passwordError   by remember { mutableStateOf<String?>(null) }
    var networkError    by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager   = LocalFocusManager.current

    fun validate(): Boolean {
        var ok = true
        emailError    = null
        passwordError = null
        networkError  = null

        if (email.isBlank()) {
            emailError = "Email is required"
            ok = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            emailError = "Enter a valid email address"
            ok = false
        }
        if (password.isBlank()) {
            passwordError = "Password is required"
            ok = false
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            ok = false
        }
        return ok
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Logo ─────────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.size(80.dp),
            shape    = RoundedCornerShape(20.dp),
            color    = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("360°", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text       = "Ethred 360° Capture",
            style      = MaterialTheme.typography.headlineMedium,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text       = "Sign in with your Ethred Agent account",
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier   = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ── Email Field ───────────────────────────────────────────────────────
        OutlinedTextField(
            value         = email,
            onValueChange = { email = it; emailError = null; networkError = null },
            label         = { Text("Email Address") },
            leadingIcon   = { Icon(Icons.Default.Email, contentDescription = null) },
            isError       = emailError != null,
            supportingText = emailError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction    = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Password Field ────────────────────────────────────────────────────
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it; passwordError = null; networkError = null },
            label         = { Text("Password") },
            leadingIcon   = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon  = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector        = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            isError       = passwordError != null,
            supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine    = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier      = Modifier.fillMaxWidth(),
            shape         = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (validate()) {
                        isLoading = true
                        coroutineScope.launch {
                            val result = authRepository.login(email.trim(), password)
                            isLoading = false
                            result.fold(
                                onSuccess = { onLoginSuccess() },
                                onFailure = { error ->
                                    networkError = when {
                                        error.message?.contains("401") == true ||
                                        error.message?.contains("403") == true ->
                                            "Invalid email or password."
                                        error.message?.contains("certificate") == true ||
                                        error.message?.contains("SSL") == true ->
                                            "Security error. Please check your connection."
                                        else -> "Could not connect. Check your internet connection."
                                    }
                                }
                            )
                        }
                    }
                }
            )
        )

        // ── Network Error ─────────────────────────────────────────────────────
        networkError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape    = MaterialTheme.shapes.small
            ) {
                Text(
                    text     = error,
                    color    = MaterialTheme.colorScheme.onErrorContainer,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Login Button ──────────────────────────────────────────────────────
        Button(
            onClick = {
                focusManager.clearFocus()
                if (!validate()) return@Button
                isLoading = true
                coroutineScope.launch {
                    val result = authRepository.login(email.trim(), password)
                    isLoading = false
                    result.fold(
                        onSuccess = { onLoginSuccess() },
                        onFailure = { error ->
                            networkError = when {
                                error.message?.contains("401") == true ||
                                error.message?.contains("403") == true ->
                                    "Invalid email or password."
                                error.message?.contains("certificate") == true ||
                                error.message?.contains("SSL") == true ->
                                    "Security error. Please check your connection."
                                else -> "Could not connect. Check your internet connection."
                            }
                        }
                    )
                }
            },
            enabled  = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = MaterialTheme.shapes.medium
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp),
                    color       = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Log In", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Demo skip (small, at bottom — not a big button) ──────────────────
        TextButton(
            onClick  = {
                authRepository.saveDemoSession()
                onLoginSuccess()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text  = "Continue with Demo Mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}
