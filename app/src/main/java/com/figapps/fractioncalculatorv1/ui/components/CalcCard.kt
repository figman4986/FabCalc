package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.figapps.fractioncalculatorv1.ui.CalcCornerRadius
import com.figapps.fractioncalculatorv1.ui.CalcTheme

/**
 * A rounded, bordered surface used to group content in the app's palette. Shared so every tool
 * looks the same. Mirrors the styling already used by the calculator's input slots.
 */
@Composable
fun CalcCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CalcTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}
