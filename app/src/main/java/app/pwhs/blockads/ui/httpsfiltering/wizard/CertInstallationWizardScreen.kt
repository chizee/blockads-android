package app.pwhs.blockads.ui.httpsfiltering.wizard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.httpsfiltering.CertStatus
import app.pwhs.blockads.ui.httpsfiltering.wizard.component.WizardExplanationPage
import app.pwhs.blockads.ui.httpsfiltering.wizard.component.WizardInstallCaPage
import app.pwhs.blockads.ui.httpsfiltering.wizard.component.WizardSaveCaPage
import app.pwhs.blockads.ui.httpsfiltering.wizard.component.WizardSecurityPage
import app.pwhs.blockads.ui.httpsfiltering.wizard.component.WizardStepIndicator
import app.pwhs.blockads.ui.httpsfiltering.wizard.component.WizardVerifyPage
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertInstallationWizardScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CertInstallationWizardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.processIntent(CertInstallationWizardUiIntent.VerifyCert)
    }

    // Handle back button presses in Android
    BackHandler(enabled = true) {
        if (uiState.currentStep.ordinal > 0) {
            viewModel.processIntent(CertInstallationWizardUiIntent.PrevStep)
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is CertInstallationWizardUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is CertInstallationWizardUiEffect.OpenSettings -> {
                    try {
                        settingsLauncher.launch(effect.intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Cannot open Settings: ${e.message}")
                    }
                }
                CertInstallationWizardUiEffect.FinishAndNavigateBack -> {
                    onNavigateBack()
                }
                is CertInstallationWizardUiEffect.CopyToClipboard -> {
                    clipboardManager.setText(AnnotatedString(effect.text))
                    snackbarHostState.showSnackbar(context.getString(R.string.https_wizard_filename_copied))
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.https_wizard_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep.ordinal > 0) {
                            viewModel.processIntent(CertInstallationWizardUiIntent.PrevStep)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (uiState.currentStep != WizardStep.VERIFY || uiState.certStatus != CertStatus.INSTALLED) {
                WizardBottomNav(
                    currentStep = uiState.currentStep,
                    onPrev = { viewModel.processIntent(CertInstallationWizardUiIntent.PrevStep) },
                    onNext = { viewModel.processIntent(CertInstallationWizardUiIntent.NextStep) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Step progression indicator
            WizardStepIndicator(
                currentStep = uiState.currentStep,
                onStepClick = { step ->
                    viewModel.processIntent(CertInstallationWizardUiIntent.GoToStep(step))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Animated step page container
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                            fadeOut(animationSpec = tween(180))
                },
                modifier = Modifier.weight(1f),
                label = "WizardStepTransition"
            ) { step ->
                when (step) {
                    WizardStep.EXPLANATION -> {
                        WizardExplanationPage()
                    }
                    WizardStep.SECURITY -> {
                        WizardSecurityPage()
                    }
                    WizardStep.SAVE_CA -> {
                        WizardSaveCaPage(
                            fileName = uiState.fileName,
                            isExported = uiState.isCertExported,
                            onExport = { viewModel.processIntent(CertInstallationWizardUiIntent.ExportCert) },
                            onCopyFileName = {
                                clipboardManager.setText(AnnotatedString(uiState.fileName))
                            }
                        )
                    }
                    WizardStep.INSTALL_CA -> {
                        WizardInstallCaPage(
                            brandName = uiState.brandName,
                            installSteps = uiState.installSteps,
                            isRootAvailable = uiState.isRootAvailable,
                            isExecutingRoot = uiState.isExecutingRoot,
                            onOpenSettings = { viewModel.processIntent(CertInstallationWizardUiIntent.OpenSecuritySettings) },
                            onInstallRootFast = { viewModel.processIntent(CertInstallationWizardUiIntent.InstallRootFast) },
                            onInstallRootModule = { viewModel.processIntent(CertInstallationWizardUiIntent.InstallRootModule) }
                        )
                    }
                    WizardStep.VERIFY -> {
                        WizardVerifyPage(
                            certStatus = uiState.certStatus,
                            onVerify = { viewModel.processIntent(CertInstallationWizardUiIntent.VerifyCert) },
                            onFinish = { viewModel.processIntent(CertInstallationWizardUiIntent.FinishWizard) },
                            onPrevStep = { viewModel.processIntent(CertInstallationWizardUiIntent.PrevStep) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardBottomNav(
    currentStep: WizardStep,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep.ordinal > 0) {
                    OutlinedButton(
                        onClick = onPrev,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.https_wizard_prev),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (currentStep != WizardStep.VERIFY) {
                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.https_wizard_next),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
