package nl.ramon96.medicijntracker.ui.medicines

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import nl.ramon96.medicijntracker.R

/**
 * Picks the notification sound for one medicine using Android's own ringtone picker, so she sees
 * the sounds already on her phone rather than a list we invented.
 */
@Composable
fun SoundPickerRow(
    soundUri: String?,
    onSoundChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked: Uri? = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        // A null uri here means she chose "Geen" / silent, which is a valid choice.
        onSoundChange(picked?.toString())
    }

    // Any ringtone still playing when the screen goes away has to be stopped by hand.
    val preview = remember { RingtonePreview(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.field_sound))
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            soundUri?.let(Uri::parse),
                        )
                    }
                    picker.launch(intent)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(soundTitle(context, soundUri))
            }

            IconButton(onClick = { preview.play(soundUri) }) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.action_play_sound),
                )
            }
        }

        Text(
            text = stringResource(R.string.field_sound_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Reads the human name of a ringtone; falls back to "Standaard" when nothing is set. */
private fun soundTitle(context: Context, soundUri: String?): String {
    if (soundUri == null) return context.getString(R.string.sound_default)
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(soundUri))?.getTitle(context)
    }.getOrNull() ?: context.getString(R.string.sound_unknown)
}

/** Plays at most one preview at a time. */
private class RingtonePreview(private val context: Context) {
    private var playing: Ringtone? = null

    fun play(soundUri: String?) {
        stop()
        val uri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        playing = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()
        playing?.play()
    }

    fun stop() {
        playing?.takeIf { it.isPlaying }?.stop()
        playing = null
    }
}
