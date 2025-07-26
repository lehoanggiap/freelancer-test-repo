import com.respiroc.webapp.constant.ShortcutScreen

data class ShortcutAction(
    val screen: ShortcutScreen,
    val keyCombination: String,
    val description: String,
    val actionId: String
)
