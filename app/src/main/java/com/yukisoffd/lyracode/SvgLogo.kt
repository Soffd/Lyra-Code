package com.yukisoffd.lyracode

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AiLogoBadge(
    @DrawableRes logoRes: Int?,
    fallback: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logoRes == null) {
            Text(
                fallback.trim().firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Image(
                painter = painterResource(logoRes),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
