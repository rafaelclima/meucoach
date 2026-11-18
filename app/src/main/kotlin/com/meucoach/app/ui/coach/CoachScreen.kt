package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.theme.McTheme

@Composable
fun CoachRoute(viewModel: CoachViewModel = hiltViewModel()) {
    val consented by viewModel.consented.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()
    val explanation by viewModel.explanation.collectAsStateWithLifecycle()
    CoachScreen(
        consented = consented,
        showConsent = !consented && !dismissed,
        explanation = explanation,
        onGrant = { viewModel.grant() },
        onDismiss = { viewModel.dismiss() },
        onRevoke = { viewModel.revoke() },
        onRetry = { viewModel.retry() },
    )
}

@Composable
fun CoachScreen(
    consented: Boolean,
    showConsent: Boolean,
    explanation: CoachExplanationState,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    onRevoke: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Coach",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            color = McTheme.colors.textPrimary,
        )
        Text(
            text = "Aqui você conversa sobre a sua semana — o que foi planejado, " +
                "o que foi feito e como reorganizar quando algo mudar.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = McTheme.colors.textSecondary,
        )
        if (showConsent) {
            McCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Ativar o Coach?",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = McTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Para explicar sua semana, o app envia um resumo dos treinos " +
                            "(sem nome e sem mapa) para o serviço de IA. " +
                            "Você pode desligar quando quiser em Dados.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = McTheme.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        McPrimaryButton(text = "Ativar", onClick = onGrant, modifier = Modifier.weight(1f))
                        McSecondaryButton(text = "Depois", onClick = onDismiss, modifier = Modifier.weight(1f))
                    }
                }
            }
        } else if (consented) {
            when (explanation) {
                CoachExplanationState.Unavailable -> {
                    Text(
                        text = "Coach ativado. Em breve: explicação da semana e ajuda para reorganizar treinos.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = McTheme.colors.textTertiary,
                    )
                }

                CoachExplanationState.Loading -> {
                    CircularProgressIndicator(color = McTheme.colors.accent)
                }

                is CoachExplanationState.Ready -> {
                    McCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = explanation.title,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = McTheme.colors.textPrimary,
                            )
                            Text(
                                text = explanation.body,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = McTheme.colors.textSecondary,
                            )
                            explanation.bullets.forEach { bullet ->
                                Text(
                                    text = "• $bullet",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = McTheme.colors.textSecondary,
                                )
                            }
                        }
                    }
                    TextButton(onClick = onRevoke) {
                        Text(text = "Desativar Coach")
                    }
                }

                is CoachExplanationState.Fallback -> {
                    McCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Resumo da semana",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = McTheme.colors.textPrimary,
                            )
                            Text(
                                text = explanation.planRationale,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = McTheme.colors.textSecondary,
                            )
                            Text(
                                text = "O Coach não conseguiu explicar desta vez. Tente de novo em instantes.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = McTheme.colors.textTertiary,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        McSecondaryButton(text = "Tentar de novo", onClick = onRetry)
                        TextButton(onClick = onRevoke) {
                            Text(text = "Desativar")
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Na primeira vez, pedimos sua permissão para usar um resumo dos treinos. " +
                    "Nada sai do aparelho sem você permitir.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = McTheme.colors.textTertiary,
            )
        }
    }
}
