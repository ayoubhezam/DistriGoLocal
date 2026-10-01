package com.distrigo.app.ui.format

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.distrigo.app.core.format.MoneyFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the root provides is what every amount below it shows, and a change shows at once. */
@RunWith(AndroidJUnit4::class)
class LocalMoneyFormatterTest {

    @get:Rule
    val compose = createComposeRule()

    private val nb = ' '

    @Test
    fun withoutAProviderItIsTheDefault() {
        compose.setContent { Text(LocalMoneyFormatter.current.da(1236790.5)) }
        compose.onNodeWithText("1${nb}236${nb}790,50${nb}DA").assertExists()
    }

    @Test
    fun aChangedFormatRewritesWhatIsOnScreen() {
        var format by mutableStateOf(MoneyFormat.SPACES)
        compose.setContent {
            ProvideMoneyFormat(format) { Text(LocalMoneyFormatter.current.da(1236790.5)) }
        }
        compose.onNodeWithText("1${nb}236${nb}790,50${nb}DA").assertExists()

        format = MoneyFormat.DOTS
        compose.onNodeWithText("1.236.790,50${nb}DA").assertExists()

        format = MoneyFormat.COMMAS
        compose.onNodeWithText("1,236,790.50${nb}DA").assertExists()
    }

    /** A dialog is a window of its own; it must still write amounts the business's way. */
    @Test
    fun dialogsInheritTheFormat() {
        compose.setContent {
            ProvideMoneyFormat(MoneyFormat.COMMAS) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    text = { Text(LocalMoneyFormatter.current.da(3500.0)) },
                )
            }
        }
        compose.onNodeWithText("3,500.00${nb}DA").assertExists()
    }
}
