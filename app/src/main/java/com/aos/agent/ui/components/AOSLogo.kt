package com.aos.agent.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.aos.agent.R
import com.aos.agent.ui.theme.AOSSizing

@Composable
fun AOSLogo(modifier: Modifier = Modifier, size: Dp = AOSSizing.logoSize) {
    Icon(painterResource(R.drawable.ic_brand_mark), contentDescription = null,
        modifier = modifier.size(size), tint = MaterialTheme.colorScheme.primary)
}

@Composable
fun AOSLogoMark(modifier: Modifier = Modifier) { AOSLogo(modifier) }
