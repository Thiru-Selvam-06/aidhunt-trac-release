package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.firebase.*
import com.example.ui.components.CashBalanceEditor
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashBalanceDeviceTest {
    @get:Rule val compose = createComposeRule()
    @org.junit.Before fun syntheticOnly() {
        check(BuildConfig.DEVICE_TEST)
        check(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".devicetest"))
        check(com.google.firebase.FirebaseApp.getInstance().options.projectId == "demo-aidhunt-v16")
    }
    private fun ready() = CashBalanceState("ws_test", enabled=true, loaded=true, confirmed=true)

    @Test fun installedLauncherUsesTheExactSuppliedArtworkAndRendersAnAdaptiveIcon() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(R.mipmap.pms_launcher,context.applicationInfo.icon)
        val actual=context.resources.openRawResource(R.drawable.pms_app_icon).use { input ->
            java.security.MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
        }
        assertEquals("a8c5c46f12902c5235051186be79419297979b355f7fb3fb7e388687030c7be9",actual)
        val drawable=context.packageManager.getApplicationIcon(context.applicationInfo)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertTrue(drawable is android.graphics.drawable.AdaptiveIconDrawable)
        } else {
            assertTrue(drawable is android.graphics.drawable.BitmapDrawable)
        }
        val bitmap=android.graphics.Bitmap.createBitmap(192,192,android.graphics.Bitmap.Config.ARGB_8888)
        drawable.setBounds(0,0,192,192);drawable.draw(android.graphics.Canvas(bitmap))
        java.io.File(context.cacheDir,"pms-launcher-preview.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            it.fd.sync()
        }
    }

    @Test fun exactTargetAndRequiredReasonReachOneCashCorrection() {
        var saved = false
        compose.setContent { MyApplicationTheme { CashBalanceEditor(ready(),1286.67,false,true,true,{},
            { target, base, revision, reason, id, done ->
                assertEquals(500050L,target);assertEquals(128667L,base);assertEquals(0L,revision)
                assertEquals("Cash counted",reason);assertTrue(id.isNotBlank());saved=true;done(null)
            }) } }
        compose.onNodeWithTag("cash_balance_save").assertIsNotEnabled()
        compose.onNodeWithTag("cash_balance_amount").performTextReplacement("5000.50")
        compose.onNodeWithTag("cash_balance_reason").performTextInput("Cash counted")
        compose.onNodeWithTag("cash_balance_save").assertIsEnabled().performClick()
        assertTrue(saved)
        compose.onNodeWithText("This changes shared available cash and withdrawal limits. Customer dues, recorded receipts and earned profit stay unchanged.").assertExists()
    }
    @Test fun inactiveCloudFeatureDoesNotAttemptAnUnauthorizedWrite() {
        compose.setContent { MyApplicationTheme { CashBalanceEditor(CashBalanceState("ws_test",loaded=true),
            1286.67,false,true,true,{}, {_,_,_,_,_,_->fail("Inactive write")}) } }
        compose.onNodeWithTag("cash_balance_amount").assertIsNotEnabled()
        compose.onNodeWithTag("cash_balance_save").assertIsNotEnabled()
        compose.onNodeWithText("Activation is required after all partner apps are updated. No cash balance has been changed.").assertExists()
    }
    @Test fun offlineTamilHistoryKeepsUserTextAndCannotSave() {
        val edit=CashBalanceEdit("original","ws_test",1,128667,500050,371383,"Original user reason","partner","Perumal",1795241623375)
        compose.setContent { MyApplicationTheme { CashBalanceEditor(ready().copy(totalAdjustmentPaise=371383,revision=1,confirmed=false,edits=listOf(edit)),
            1286.67,true,false,true,{}, {_,_,_,_,_,_->fail("Offline correction")}) } }
        compose.onNodeWithTag("cash_balance_save").assertIsNotEnabled()
        compose.onNodeWithText("காரணம் (கட்டாயம்)").assertExists()
        compose.onNodeWithTag("cash_balance_content").performScrollToNode(hasText("Original user reason", substring = true))
        compose.onNodeWithText("Perumal",substring=true).assertExists()
        compose.onNodeWithText("Original user reason",substring=true).assertExists()
    }
    @Test fun changingCashRequiresReviewBeforeSaving() {
        val base=androidx.compose.runtime.mutableStateOf(1286.67)
        compose.setContent { MyApplicationTheme { CashBalanceEditor(ready(),base.value,false,true,true,{},
            {_,_,_,_,_,_->fail("Stale correction")}) } }
        compose.onNodeWithTag("cash_balance_amount").performTextReplacement("2000")
        compose.onNodeWithTag("cash_balance_reason").performTextInput("Cash counted")
        compose.onNodeWithTag("cash_balance_save").assertIsEnabled()
        compose.runOnIdle { base.value=1500.0 }
        compose.onNodeWithTag("cash_balance_save").assertIsNotEnabled()
        compose.onNodeWithText("The balance changed. Close and reopen to review the latest amount.").assertExists()
    }
    @Test fun failedSaveKeepsThePageAndOriginalLogicalOperation() {
        val ids=mutableListOf<String>();var dismissed=false
        compose.setContent { MyApplicationTheme { CashBalanceEditor(ready(),1286.67,false,true,true,{dismissed=true},
            {_,_,_,_,id,done->ids.add(id);done("Authorization denied; original balance retained")}) } }
        compose.onNodeWithTag("cash_balance_amount").performTextReplacement("2000")
        compose.onNodeWithTag("cash_balance_reason").performTextInput("Cash counted")
        compose.onNodeWithTag("cash_balance_save").performClick()
        compose.onNodeWithText("Authorization denied; original balance retained").assertExists()
        assertFalse(dismissed)
        compose.onNodeWithTag("cash_balance_save").performClick()
        assertEquals(2,ids.size);assertEquals(ids[0],ids[1])
    }
}
