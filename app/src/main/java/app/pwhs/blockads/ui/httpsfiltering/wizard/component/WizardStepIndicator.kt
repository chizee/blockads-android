package app.pwhs.blockads.ui.httpsfiltering.wizard.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.ui.httpsfiltering.wizard.WizardStep

@Composable
fun WizardStepIndicator(
    currentStep: WizardStep,
    onStepClick: (WizardStep) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WizardStep.entries.forEach { step ->
            val isActive = step == currentStep
            val isPassed = step.stepIndex < currentStep.stepIndex

            val color by animateColorAsState(
                targetValue = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                label = "stepColor"
            )

            val widthFactor by animateFloatAsState(
                targetValue = if (isActive) 28f else 10f,
                label = "stepWidth"
            )

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .size(width = widthFactor.dp, height = 8.dp)
                    .clip(if (isActive) RoundedCornerShape(4.dp) else CircleShape)
                    .background(color)
                    .clickable { onStepClick(step) }
            )
        }
    }
}
