package ru.bruce.dispatcherinbox.ui

import ru.bruce.dispatcherinbox.data.InboxStatus

data class DeliveryStatus(val label: String, val isError: Boolean, val waitingForDrive: Boolean)

fun deliveryStatus(status: String, driveConnected: Boolean): DeliveryStatus = when {
    status == InboxStatus.SYNCED -> DeliveryStatus("Отправлено", false, false)
    !driveConnected -> DeliveryStatus("На телефоне · Ждёт Drive", false, true)
    status == InboxStatus.ERROR -> DeliveryStatus("Ошибка загрузки", true, false)
    status == InboxStatus.SYNCING -> DeliveryStatus("Отправляется", false, false)
    else -> DeliveryStatus("На телефоне · В очереди", false, false)
}
