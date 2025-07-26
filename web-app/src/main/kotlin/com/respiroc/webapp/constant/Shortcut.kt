data class Shortcut(
    val screen: ShortcutScreen,
    val keyCombination: String,
    val description: String,
    val actionId: String
)

object ShortcutRegistry {
    private val allActions = listOf(
        Shortcut(ShortcutScreen.VOUCHERS_ADVANCED, "ctrl+r", "Add new row", "addNewRow"),
        Shortcut(ShortcutScreen.VOUCHERS_ADVANCED, "ctrl+s", "Save the voucher", "saveVoucher"),
    )

    fun getByScreen(screen: ShortcutScreen): List<Shortcut> {
        return allActions.filter { it.screen == screen }
    }

}

enum class ShortcutScreen(val displayName: String) {
    VOUCHERS_ADVANCED("Advanced voucher screen"),
}
