package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    CoachScreen(
        consented = consented,
        showConsent = !consented && !dismissed,
        onGrant = { viewModel.grant() },
        onDismiss = { viewModel.dismiss() },
        onRevoke = { viewModel.revoke() },
    )
}

@Composable
fun CoachScreen(
    consented: Boolean,
    showConsent: Boolean,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Coach",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            color = McTheme.colors.textPrimary,
        )
        Text(
            text = "Aqui você conversa sobre sua semana: o que foi planejado, " +
                "o que foi feito e como reorganizar quando algo mudar.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = McTheme.colors.textSecondary,
        )
        if (showConsent) {
            McCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
            Text(
                text = "Coach ativado. Em breve: explicação da semana e ajuda para reorganizar treinos.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = McTheme.colors.textTertiary,
            )
            TextButton(onClick = onRevoke) {
                Text(text = "Desativar Coach")
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
