@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.trakt.tv.auth.AuthEvent
import com.trakt.tv.auth.AuthManager
import com.trakt.tv.data.model.DeviceCode
import com.trakt.tv.data.remote.TraktConfig
import com.trakt.tv.ui.appContainer
import com.trakt.tv.util.generateQrCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SignInState {
    data object Starting : SignInState
    data class AwaitingUser(val code: DeviceCode) : SignInState
    data object Authorized : SignInState
    data class Failed(val reason: String) : SignInState
    data object NotConfigured : SignInState
}

class SignInViewModel(private val auth: AuthManager) : ViewModel() {
    private val _state = MutableStateFlow<SignInState>(SignInState.Starting)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    init { start() }

    fun start() {
        if (!TraktConfig.isConfigured) {
            _state.value = SignInState.NotConfigured
            return
        }
        _state.value = SignInState.Starting
        viewModelScope.launch {
            auth.authorize().collect { event ->
                _state.value = when (event) {
                    is AuthEvent.ShowCode -> SignInState.AwaitingUser(event.code)
                    is AuthEvent.Authorized -> SignInState.Authorized
                    is AuthEvent.Failed -> SignInState.Failed(event.reason)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SignInViewModel(appContainer().authManager) }
        }
    }
}

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = viewModel(factory = SignInViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state is SignInState.Authorized) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onSignedIn() }
    }

    Box(modifier.fillMaxSize().padding(64.dp), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is SignInState.Starting -> Loading("Preparing sign-in…")
            is SignInState.AwaitingUser -> CodePanel(s.code, onSkip)
            is SignInState.Authorized -> Loading("Signed in!")
            is SignInState.Failed -> Failed(s.reason, viewModel::start, onSkip)
            is SignInState.NotConfigured -> Failed(
                "No Trakt API key configured.\nAdd trakt.clientId & trakt.clientSecret to local.properties and rebuild.",
                onRetry = null,
                onSkip = onSkip,
            )
        }
    }
}

@Composable
private fun CodePanel(code: DeviceCode, onSkip: () -> Unit) {
    val qr = remember(code.userCode) {
        generateQrCode("${TraktConfig.ACTIVATE_URL}/${code.userCode}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(520.dp)) {
            Text("Connect your Trakt account", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            Text("1. On your phone or computer, go to", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(code.verificationUrl, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("2. Enter this code", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = code.userCode,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                Text("Waiting for you to authorize…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSkip) { Text("Browse without signing in") }
        }
        if (qr != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qr,
                    contentDescription = "QR code to open the activation page",
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("or scan to open the page", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Loading(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Failed(reason: String, onRetry: (() -> Unit)?, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(reason, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onRetry != null) Button(onClick = onRetry) { Text("Try again") }
            Button(onClick = onSkip) { Text("Not now") }
        }
    }
}
