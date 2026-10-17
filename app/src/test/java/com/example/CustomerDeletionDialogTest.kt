package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.ui.screens.report.CustomerDetailSheet
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CustomerDeletionDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun show(owner: Boolean) {
        compose.setContent {
            MyApplicationTheme {
                CustomerDetailSheet(
                    customer = CustomerEntity(id = 7, workspaceId = "stage", name = "Disposable Test", phone = ""),
                    jobs = emptyList(),
                    settings = AppSettingsEntity(workspaceId = "stage"),
                    onDismiss = {}, onAddPayment = {}, onCall = {}, onWhatsApp = {}, onSharePdf = {},
                    onDeleteCustomer = {}, isOwner = owner, actorUid = if (owner) "owner" else "partner"
                )
            }
        }
    }

    @Test fun ownerConfirmationExplainsDependencyProtectionWithoutClaimingCascadeDeletion() {
        show(true)
        compose.onNodeWithTag("btn_delete_customer").performScrollTo().performClick()
        compose.onNodeWithText("This deletes the customer only if no Entries or Collections still reference them. Linked records and collection history are preserved.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test fun partnerCannotSeeCustomerDeletionAction() {
        show(false)
        compose.onNodeWithTag("btn_delete_customer").assertDoesNotExist()
    }
}
