package ir.asudehapp.sms.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.persian.SendSchedule
import java.time.ZoneId

/** مرحله‌های انتخاب زمان دلخواه: اول روز، بعد ساعت. */
private enum class CustomStep { NONE, DAY, TIME }

/**
 * «بعداً بفرست» (ADR-0011): چند زمان آمادهٔ نزدیک، و «زمان دلخواه» برای بقیه.
 * زمان‌های آماده از حساب خالص `SendSchedule` می‌آیند، پس همان چیزی‌اند که
 * آزمون می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(model: AsudehViewModel) {
    val request by model.scheduleRequest.collectAsState()
    val pending = request ?: return
    var step by remember { mutableStateOf(CustomStep.NONE) }
    var chosenDay by remember { mutableStateOf(0L) }
    val zone = ZoneId.systemDefault()

    when (step) {
        CustomStep.NONE -> AlertDialog(
            onDismissRequest = model::cancelSchedule,
            title = { Text(stringResource(R.string.schedule_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (choice in pending.presets) {
                        Text(
                            Texts.scheduleChoice(choice),
                            Modifier
                                .fillMaxWidth()
                                .clickable { model.scheduleSend(choice.atMillis) }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        HorizontalDivider()
                    }
                    Text(
                        stringResource(R.string.schedule_custom),
                        Modifier
                            .fillMaxWidth()
                            .clickable { step = CustomStep.DAY }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.schedule_note),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = model::cancelSchedule) { Text(stringResource(R.string.cancel)) }
            },
        )

        CustomStep.DAY -> {
            val now = System.currentTimeMillis()
            val dayState = rememberDatePickerState(
                initialSelectedDateMillis = now,
                // روز گذشته انتخاب‌شدنی نیست؛ زمان گذشته در هر حال رد می‌شود.
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        SendSchedule.epochDayOfUtcMillis(utcTimeMillis) >= SendSchedule.today(now, zone)
                },
            )
            DatePickerDialog(
                onDismissRequest = model::cancelSchedule,
                confirmButton = {
                    TextButton(
                        onClick = {
                            chosenDay = SendSchedule.epochDayOfUtcMillis(
                                dayState.selectedDateMillis ?: now,
                            )
                            step = CustomStep.TIME
                        },
                        enabled = dayState.selectedDateMillis != null,
                    ) { Text(stringResource(R.string.continue_)) }
                },
                dismissButton = {
                    TextButton(onClick = model::cancelSchedule) { Text(stringResource(R.string.cancel)) }
                },
            ) {
                DatePicker(dayState, title = { Text(stringResource(R.string.schedule_pick_day), Modifier.padding(16.dp)) })
            }
        }

        CustomStep.TIME -> {
            val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
            AlertDialog(
                onDismissRequest = model::cancelSchedule,
                title = { Text(stringResource(R.string.schedule_pick_time)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TimePicker(timeState)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        model.scheduleSend(
                            SendSchedule.at(zone, chosenDay, timeState.hour, timeState.minute),
                        )
                    }) { Text(stringResource(R.string.schedule_send)) }
                },
                dismissButton = {
                    TextButton(onClick = model::cancelSchedule) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}
