package com.unamentis.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unamentis.BuildConfig
import com.unamentis.R
import com.unamentis.ui.components.BrandLogo
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.components.Size
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography

/**
 * About screen showing app information, version, and links.
 *
 * Features:
 * - App version and build number
 * - Links to documentation
 * - Privacy policy
 * - Terms of service
 * - Open source licenses
 * - Contact/support email
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val backDescription = stringResource(R.string.cd_go_back)
    val noAppMessage = stringResource(R.string.error_no_app_available)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = backDescription },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("about_screen"),
            contentPadding = PaddingValues(Dimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // App Info Header
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.SpacingXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandLogo(size = Size.Large)
                    Spacer(modifier = Modifier.height(Dimensions.SpacingMedium))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = IOSTypography.title2,
                    )
                    Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                    Text(
                        text = stringResource(R.string.about_tagline),
                        style = IOSTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Version Info
            item {
                IOSCard {
                    Column {
                        AboutInfoRow(
                            label = stringResource(R.string.about_version),
                            value = BuildConfig.VERSION_NAME,
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SpacingSmall))
                        AboutInfoRow(
                            label = stringResource(R.string.about_build),
                            value = BuildConfig.VERSION_CODE.toString(),
                        )
                    }
                }
            }

            // Links Section
            item {
                Text(
                    text = stringResource(R.string.about_links_section),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                IOSCard {
                    Column {
                        AboutLinkRow(
                            icon = Icons.Default.Description,
                            title = stringResource(R.string.about_documentation),
                            onClick = {
                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://unamentis.com/docs"),
                                    )
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimensions.CardPadding))
                        AboutLinkRow(
                            icon = Icons.Default.Policy,
                            title = stringResource(R.string.about_privacy_policy),
                            onClick = {
                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://unamentis.com/privacy"),
                                    )
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimensions.CardPadding))
                        AboutLinkRow(
                            icon = Icons.Default.Gavel,
                            title = stringResource(R.string.about_terms_of_service),
                            onClick = {
                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://unamentis.com/terms"),
                                    )
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Dimensions.CardPadding))
                        AboutLinkRow(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.about_open_source_licenses),
                            onClick = {
                                val intent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://unamentis.com/licenses"),
                                    )
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }

            // Support Section
            item {
                Text(
                    text = stringResource(R.string.about_support_section),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                )
            }

            item {
                val supportSubject = stringResource(R.string.about_support_email_subject, BuildConfig.VERSION_NAME)
                IOSCard {
                    AboutLinkRow(
                        icon = Icons.Default.Email,
                        title = stringResource(R.string.about_contact_support),
                        subtitle = stringResource(R.string.about_support_email),
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@unamentis.com")
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        supportSubject,
                                    )
                                }
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            // Copyright
            item {
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.SpacingLarge),
                )
            }
        }
    }
}

/**
 * Row displaying a label-value pair.
 */
@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = IOSTypography.body,
        )
        Text(
            text = value,
            style = IOSTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Row with an icon, title, and optional subtitle that opens a link.
 */
@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Dimensions.CardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.padding(start = Dimensions.SpacingMedium)) {
                    Text(
                        text = title,
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = IOSTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.cd_open_external_link),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
