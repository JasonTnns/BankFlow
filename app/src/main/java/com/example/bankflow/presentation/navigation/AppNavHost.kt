package com.example.bankflow.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.bankflow.R
import com.example.bankflow.domain.model.User
import com.example.bankflow.presentation.accounts.AccountDetailsScreen
import com.example.bankflow.presentation.auth.login.LoginScreen
import com.example.bankflow.presentation.auth.register.RegisterScreen
import com.example.bankflow.presentation.beneficiaries.BeneficiariesScreen
import com.example.bankflow.presentation.dashboard.DashboardScreen
import com.example.bankflow.presentation.insights.InsightsScreen
import com.example.bankflow.presentation.profile.ProfileScreen
import com.example.bankflow.presentation.session.SessionState
import com.example.bankflow.presentation.transactions.TransactionDetailsScreen
import com.example.bankflow.presentation.transfer.TransferScreen


@Composable
fun AppNavHost(
    session: SessionState,
    onLogout: () -> Unit,
) {
    val authScope: NavScopeViewModel = viewModel(key = "auth")
    val mainScope: NavScopeViewModel = viewModel(key = "main")

    val signedIn = session is SessionState.SignedIn
    LaunchedEffect(signedIn) {
        if (signedIn) {
            authScope.clear()
        } else if (session is SessionState.SignedOut) {
            mainScope.clear()
        }
    }

    when (session) {
        SessionState.Loading -> SplashScreen()
        is SessionState.SignedIn -> CompositionLocalProvider(LocalViewModelStoreOwner provides mainScope) {
            MainNavHost(user = session.user, onLogout = onLogout)
        }
        SessionState.SignedOut -> CompositionLocalProvider(LocalViewModelStoreOwner provides authScope) {
            AuthNavHost()
        }
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(onNavigateToRegister = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(onNavigateToLogin = { navController.popBackStack() })
        }
    }
}
private enum class TopLevelTab(val route: Any, @StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(DashboardRoute, R.string.tab_home, Icons.Filled.Home),
    TRANSFER(TransferRoute, R.string.tab_transfer, Icons.Filled.SwapHoriz),
    INSIGHTS(InsightsRoute, R.string.tab_insights, Icons.Filled.BarChart),
    PROFILE(ProfileRoute, R.string.tab_profile, Icons.Filled.Person);

    fun matches(currentRoute: String?): Boolean {
        val name = route::class.qualifiedName ?: return false
        return currentRoute != null && currentRoute.startsWith(name)
    }
}

private fun NavController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavHost(user: User, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelTab.entries.any { it.matches(currentRoute) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.matches(currentRoute),
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag("tab_${tab.name}"),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable<DashboardRoute> {
                DashboardScreen(
                    userName = user.fullName,
                    onAccountClick = { accountId -> navController.navigate(AccountDetailsRoute(accountId)) },
                    onTransactionClick = { navController.navigate(TransactionDetailsRoute(it.accountId, it.transferId)) },
                )
            }
            composable<TransferRoute> {
                TransferScreen(
                    onDone = { navController.navigateToTab(DashboardRoute) },
                    onManageRecipients = { navController.navigate(BeneficiariesRoute) },
                )
            }
            composable<InsightsRoute> {
                InsightsScreen()
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    onOpenRecipients = { navController.navigate(BeneficiariesRoute) },
                    onLogout = onLogout,
                )
            }
            composable<BeneficiariesRoute> {
                BeneficiariesScreen(onBack = { navController.popBackStack() })
            }
            composable<AccountDetailsRoute> { entry ->
                val route = entry.toRoute<AccountDetailsRoute>()
                AccountDetailsScreen(
                    accountId = route.accountId,
                    onBack = { navController.popBackStack() },
                    onTransactionClick = { navController.navigate(TransactionDetailsRoute(it.accountId, it.transferId)) },
                )
            }
            composable<TransactionDetailsRoute> { entry ->
                val route = entry.toRoute<TransactionDetailsRoute>()
                TransactionDetailsScreen(
                    accountId = route.accountId,
                    transferId = route.transferId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}