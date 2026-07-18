package com.offlinepay.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.offlinepay.core.designsystem.OfflinePayTheme
import com.offlinepay.core.designsystem.sharedAxisZEnterTransition
import com.offlinepay.core.designsystem.sharedAxisZExitTransition
import com.offlinepay.feature.onboarding.OnboardingScreen
import com.offlinepay.core.security.SecurityGuard
import com.offlinepay.feature.dashboard.DashboardScreen
import com.offlinepay.feature.history.HistoryScreen
import com.offlinepay.feature.history.TransactionReceiptScreen
import com.offlinepay.feature.merchant.MerchantProfileScreen
import com.offlinepay.feature.pay123.ui.PaymentResultConfirmationScreen
import com.offlinepay.feature.payment.ui.PaymentFailureScreen
import com.offlinepay.feature.payment.ui.PaymentSuccessScreen
import com.offlinepay.feature.settings.SettingsAboutScreen
import com.offlinepay.feature.settings.SettingsPermissionsScreen
import com.offlinepay.feature.settings.SettingsRoutingScreen
import com.offlinepay.feature.settings.SettingsSimScreen
import com.offlinepay.feature.settings.SettingsScreen
import com.offlinepay.feature.settings.SettingsSecurityScreen
import com.offlinepay.feature.settings.SettingsThemeScreen
import com.offlinepay.feature.scanner.ScannerScreen
import com.offlinepay.feature.payment.PaymentConfirmationScreen
import com.offlinepay.feature.ussd.UssdInProgressScreen
import com.offlinepay.feature.pay123.ui.Pay123GuidanceScreen
import android.net.Uri
import kotlinx.serialization.json.Json
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var securityGuard: SecurityGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install SplashScreen BEFORE super.onCreate()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash on screen until security check completes (Design Section 12.3)
        var isSecurityCheckComplete by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isSecurityCheckComplete }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val win = window
        setContent {
            OfflinePayTheme {
                val navController = rememberNavController()
                SecurityWindowController(navController = navController, window = win)

                // Run startup security check, then navigate based on result (Task 21.4)
                LaunchedEffect(Unit) {
                    val status = securityGuard.performStartupChecks()
                    isSecurityCheckComplete = true
                    when (status) {
                        is SecurityGuard.SecurityStatus.Blocked -> {
                            navController.navigate("security_blocked") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                        // PASS, WarnStale, WarnVeryStale — all allow payments; warnings
                        // are displayed as banners in DashboardScreen (Task 28.4)
                        else -> Unit
                    }
                }

                AppNavGraph(navController = navController)
            }
        }
    }
}

@Composable
fun SecurityWindowController(navController: NavController, window: android.view.Window) {
    val secureRoutes = setOf(
        "scanner", "payment_confirmation", "ussd_in_progress",
        "pay123_guidance", "payment_success", "payment_failure",
        "payment_result_confirmation", "transaction_receipt"
    )
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && secureRoutes.any { currentRoute.startsWith(it) }) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        enterTransition = { sharedAxisZEnterTransition() },
        exitTransition = { sharedAxisZExitTransition() },
        popEnterTransition = { sharedAxisZEnterTransition() },
        popExitTransition = { sharedAxisZExitTransition() },
    ) {
        // ── Splash (SplashScreen API handles this; route kept for NavGraph completeness) ──
        composable("splash") { /* Handled by SplashScreen API in MainActivity */ }

        // ── Onboarding (Task 22) ────────────────────────────────────────────
        composable("onboarding") {
            OnboardingScreen(
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }

        // ── Permission check (redirects into onboarding flow) ──────────────
        composable("permission_check") {
            OnboardingScreen(
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("permission_check") { inclusive = true }
                    }
                },
            )
        }

        // ── Dashboard (Task 28) ─────────────────────────────────────────────
        composable("dashboard") {
            DashboardScreen(
                onNavigateToScanner = { navController.navigate("scanner") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToMerchantProfile = { upiId ->
                    navController.navigate("merchant_profile/$upiId")
                },
            )
        }

        composable("scanner") {
            ScannerScreen(
                onNavigateToPaymentConfirmation = { params ->
                    val json = Json.encodeToString(com.offlinepay.core.domain.model.PaymentParams.serializer(), params)
                    val encodedJson = Uri.encode(json)
                    navController.navigate("payment_confirmation/$encodedJson")
                }
            )
        }
        composable(
            route = "payment_confirmation/{paymentParamsJson}",
            arguments = listOf(navArgument("paymentParamsJson") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedJson = backStackEntry.arguments?.getString("paymentParamsJson").orEmpty()
            val json = Uri.decode(encodedJson)
            val params = Json.decodeFromString(com.offlinepay.core.domain.model.PaymentParams.serializer(), json)
            backStackEntry.arguments?.putSerializable("paymentParams", params)

            PaymentConfirmationScreen(
                onNavigateToUssd = { ussdParams, subId ->
                    val ussdJson = Json.encodeToString(com.offlinepay.core.domain.model.PaymentParams.serializer(), ussdParams)
                    val encodedUssd = Uri.encode(ussdJson)
                    navController.navigate("ussd_in_progress/$encodedUssd/$subId")
                },
                onNavigateToPay123 = { pay123Params, subId ->
                    val pay123Json = Json.encodeToString(com.offlinepay.core.domain.model.PaymentParams.serializer(), pay123Params)
                    val encodedPay123 = Uri.encode(pay123Json)
                    navController.navigate("pay123_guidance/$encodedPay123/$subId")
                }
            )
        }
        composable(
            route = "ussd_in_progress/{paymentParamsJson}/{subscriptionId}",
            arguments = listOf(
                navArgument("paymentParamsJson") { type = NavType.StringType },
                navArgument("subscriptionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedJson = backStackEntry.arguments?.getString("paymentParamsJson").orEmpty()
            val json = Uri.decode(encodedJson)
            val params = Json.decodeFromString(com.offlinepay.core.domain.model.PaymentParams.serializer(), json)
            backStackEntry.arguments?.putSerializable("paymentParams", params)

            UssdInProgressScreen(
                onFallbackAccepted = {
                    // Switch to 123PAY
                    val pay123Json = Json.encodeToString(com.offlinepay.core.domain.model.PaymentParams.serializer(), params)
                    val encodedPay123 = Uri.encode(pay123Json)
                    val subId = backStackEntry.arguments?.getString("subscriptionId").orEmpty()
                    navController.navigate("pay123_guidance/$encodedPay123/$subId") {
                        popUpTo("ussd_in_progress/{paymentParamsJson}/{subscriptionId}") { inclusive = true }
                    }
                },
                onFallbackDismissed = {
                    navController.popBackStack("dashboard", inclusive = false)
                },
                onNavigateUp = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = "pay123_guidance/{paymentParamsJson}/{subscriptionId}",
            arguments = listOf(
                navArgument("paymentParamsJson") { type = NavType.StringType },
                navArgument("subscriptionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedJson = backStackEntry.arguments?.getString("paymentParamsJson").orEmpty()
            val json = Uri.decode(encodedJson)
            val params = Json.decodeFromString(com.offlinepay.core.domain.model.PaymentParams.serializer(), json)
            backStackEntry.arguments?.putSerializable("paymentParams", params)

            Pay123GuidanceScreen(
                onNavigateToSuccess = { transactionId ->
                    navController.navigate("payment_success/$transactionId") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                },
                onNavigateToFailure = {
                    navController.navigate("payment_failure") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                },
                onNavigateToResultConfirmation = { transactionId ->
                    navController.navigate("payment_result_confirmation/$transactionId") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                }
            )
        }

        // ── Payment Result Confirmation (Task 25) ───────────────────────────
        composable(
            route = "payment_result_confirmation/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) {
            PaymentResultConfirmationScreen(
                onNavigateToSuccess = { transactionId ->
                    navController.navigate("payment_success/$transactionId") {
                        popUpTo("payment_result_confirmation/{transactionId}") {
                            inclusive = true
                        }
                    }
                },
                onNavigateToFailure = {
                    navController.navigate("payment_failure") {
                        popUpTo("payment_result_confirmation/{transactionId}") {
                            inclusive = true
                        }
                    }
                },
                onNavigateToReceipt = { transactionId ->
                    navController.navigate("transaction_receipt/$transactionId")
                },
            )
        }

        // ── Payment Success (Task 26.1) ─────────────────────────────────────
        composable(
            route = "payment_success/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) {
            PaymentSuccessScreen(
                onNavigateToReceipt = { transactionId ->
                    navController.navigate("transaction_receipt/$transactionId")
                },
                onNavigateToScanner = {
                    navController.navigate("scanner") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                },
            )
        }

        // ── Payment Failure (Task 26.2) ─────────────────────────────────────
        composable("payment_failure") {
            PaymentFailureScreen(
                onRetry = {
                    navController.popBackStack()
                },
                onNavigateToPay123 = {
                    navController.navigate("pay123_guidance") {
                        popUpTo("payment_failure") { inclusive = true }
                    }
                },
                onNavigateToUssd = {
                    navController.navigate("ussd_in_progress") {
                        popUpTo("payment_failure") { inclusive = true }
                    }
                },
            )
        }

        // ── History (Task 29) ───────────────────────────────────────────────
        composable("history") {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReceipt = { transactionId ->
                    navController.navigate("transaction_receipt/$transactionId")
                },
            )
        }

        // ── Transaction Receipt (Task 29) ───────────────────────────────────
        composable(
            route = "transaction_receipt/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "offlinepay://history/{transactionId}" }),
        ) {
            TransactionReceiptScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Merchant Profile (Task 31) ──────────────────────────────────────
        composable(
            route = "merchant_profile/{upiId}",
            arguments = listOf(navArgument("upiId") { type = NavType.StringType }),
        ) {
            MerchantProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReceipt = { transactionId ->
                    navController.navigate("transaction_receipt/$transactionId")
                },
            )
        }

        // ── Settings (Task 32) ──────────────────────────────────────────────
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateTo = { route -> navController.navigate(route) },
            )
        }
        composable("settings/theme") {
            SettingsThemeScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("settings/routing") {
            SettingsRoutingScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("settings/sim") {
            SettingsSimScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("settings/permissions") {
            SettingsPermissionsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("settings/about") {
            SettingsAboutScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable("settings/security") {
            SettingsSecurityScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Deep links ──────────────────────────────────────────────────────
        composable(
            route = "scan",
            deepLinks = listOf(navDeepLink { uriPattern = "offlinepay://scan" }),
        ) {
            // Redirect in a LaunchedEffect — calling navigate() directly in the
            // composable body runs during composition and re-fires on every
            // recomposition. popUpTo removes this shim destination from the back stack.
            LaunchedEffect(Unit) {
                navController.navigate("scanner") {
                    popUpTo("scan") { inclusive = true }
                }
            }
        }

        composable("security_blocked") { SecurityBlockedScreen() }
    }
}
