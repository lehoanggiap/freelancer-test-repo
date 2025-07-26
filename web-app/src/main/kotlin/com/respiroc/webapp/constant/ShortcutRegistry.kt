package com.respiroc.webapp.constant

import ShortcutAction
import kotlin.collections.listOf

object ShortcutRegistry {
    private val allActions = listOf(
        ShortcutAction(ShortcutScreen.VOUCHERS_ADVANCED, "ctrl+r", "Add new row", "addNewRow"),
        ShortcutAction(ShortcutScreen.VOUCHERS_ADVANCED, "ctrl+s", "Save the voucher", "saveVoucher"),
        )

    fun getByScreen(screen: ShortcutScreen): List<ShortcutAction> {
        return allActions.filter { it.screen == screen }
    }

    fun getAll(): List<ShortcutAction> = allActions
}
