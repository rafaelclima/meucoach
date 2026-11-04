package com.meucoach.app.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import kotlinx.coroutines.launch

/** Runs the intro walkthrough and reports completion (or dismissal) to the caller. */
@Composable
fun TutorialScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: TutorialViewModel = hiltViewModel()) {
    TutorialContent(
        onFinish = {
            viewModel.markSeen()
            onDone()
        },
        onDismiss = {
            viewModel.markSeen()
            onDone()
        },
        modifier = modifier,
    )
}

private data class TutorialPage(
    val imageVector: ImageVector,
    val overline: String,
    val title: String,
    val description: String,
    val bullets: List<String> = emptyList(),
)

private val tutorialPages = listOf(
    TutorialPage(
        imageVector = Icons.AutoMirrored.Outlined.DirectionsRun,
        overline = "BEM-VINDO",
        title = "Seu coach de corrida",
        description = "O MeuCoach lê seus treinos, aprende com seu descanso e prescreve o próximo " +
            "passo — explicando sempre por quê. Este tour rápido mostra o essencial.",
    ),
    TutorialPage(
        imageVector = Icons.Outlined.SpaceDashboard,
        overline = "AS TELAS",
        title = "Quatro abas, um coach",
        description = "Tudo fica na base do app, organizado em quatro lugares:",
        bullets = listOf(
            "Hoje — prontidão do dia, treino recomendado e plano da semana",
            "Corridas — histórico dos treinos importados e detalhe de cada um",
            "Tendências — carga, consistência e evolução semana a semana",
            "Perfil — calibração, dados de saúde e backup dos seus dados",
        ),
    ),
    TutorialPage(
        imageVector = Icons.Outlined.Favorite,
        overline = "SEU PAPEL",
        title = "O que mantém o coach informado",
        description = "Quanto mais frescas as informações, mais certeiro o treino de amanhã:",
        bullets = listOf(
            "Check-in diário — 4 perguntas de 30 segundos (sono, fadiga, dor e estresse); há lembrete às 18h",
            "Corridas importadas — compartilhe o arquivo FIT, TCX ou GPX com o app quando terminar o treino",
            "Descanso automático — conecte o Health Connect para trazer sono, FC de repouso, HRV e SpO₂",
            "Calibração — depois de uma prova ou teste de 30 min, use Calibrar para refinar zonas e ritmos",
        ),
    ),
    TutorialPage(
        imageVector = Icons.Outlined.Lock,
        overline = "VOCÊ NO CONTROLE",
        title = "Nada sai do aparelho",
        description = "Perfil, corridas e check-ins vivem apenas no seu celular. O app nunca inventa " +
            "dados nem culpa você: sem informação suficiente, ele fica conservador.",
        bullets = listOf(
            "Cada treino tem a explicação em “Por que este treino?” — sem jargão, sem bibliografia no caminho",
            "Você decide: concluir, adiar ou pular uma sessão com motivo — tudo fica registrado",
            "Exporte ou apague tudo quando quiser, em Perfil → Seus dados",
        ),
    ),
)

@Composable
internal fun TutorialContent(onFinish: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    val scope = rememberCoroutineScope()
    val isLastPage: (Int) -> Boolean = { it == tutorialPages.lastIndex }
    val pagerState = rememberPagerState(pageCount = { tutorialPages.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TUTORIAL",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag("tutorial-title"),
                )
                if (!isLastPage(pagerState.currentPage)) {
                    McTextButton(
                        text = "Pular",
                        onClick = onDismiss,
                        trailingArrow = false,
                        modifier = Modifier.testTag("tutorial-skip"),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("tutorial-pager"),
            ) { page ->
                TutorialPageView(page = tutorialPages[page])
            }

            if (!isLastPage(pagerState.currentPage)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    tutorialPages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .background(
                                    color = if (index == pagerState.currentPage) colors.accent else colors.surfaceInset,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    McSecondaryButton(
                        text = "Voltar",
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutorial-back"),
                    )
                }
                McPrimaryButton(
                    text = if (isLastPage(pagerState.currentPage)) "Tudo pronto" else "Continuar",
                    onClick = {
                        scope.launch {
                            if (isLastPage(pagerState.currentPage)) {
                                onFinish()
                            } else {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tutorial-next"),
                )
            }
        }
    }
}

@Composable
private fun TutorialPageView(page: TutorialPage) {
    val colors = McTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag("tutorial-page"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = page.overline, style = McTextStyles.overline, color = colors.textTertiary)
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(72.dp)
                        .background(color = colors.accentSoft, shape = RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = page.imageVector,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        items(page.bullets) { bullet ->
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(color = colors.accent, shape = CircleShape),
                )
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
