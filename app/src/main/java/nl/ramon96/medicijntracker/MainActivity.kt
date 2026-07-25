package nl.ramon96.medicijntracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.ui.history.HistoryScreen
import nl.ramon96.medicijntracker.ui.history.HistoryViewModel
import nl.ramon96.medicijntracker.ui.medicines.MedicineEditScreen
import nl.ramon96.medicijntracker.ui.medicines.MedicineEditViewModel
import nl.ramon96.medicijntracker.ui.medicines.MedicineListScreen
import nl.ramon96.medicijntracker.ui.medicines.MedicineListViewModel
import nl.ramon96.medicijntracker.ui.settings.SettingsScreen
import nl.ramon96.medicijntracker.ui.settings.SettingsViewModel
import nl.ramon96.medicijntracker.ui.theme.MedicijnTheme
import nl.ramon96.medicijntracker.ui.today.TodayScreen
import nl.ramon96.medicijntracker.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* card handles refusal */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = MedicijnApp.containerOf(this)
        setContent {
            MedicijnTheme {
                MedicijnAppUi(container)
            }
        }
    }
}

private enum class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector) {
    TODAY("vandaag", R.string.tab_today, Icons.Default.Home),
    MEDICINES("medicijnen", R.string.tab_medicines, Icons.AutoMirrored.Filled.List),
    HISTORY("geschiedenis", R.string.tab_history, Icons.Default.DateRange),
    SETTINGS("instellingen", R.string.tab_settings, Icons.Default.Settings),
}

private const val EDIT_ROUTE = "medicijn/{medicineId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicijnAppUi(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isEditing = currentRoute == EDIT_ROUTE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleFor(currentRoute))) },
                navigationIcon = {
                    if (isEditing) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!isEditing) {
                NavigationBar {
                    TopLevel.entries.forEach { destination ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            container = container,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevel.TODAY.route,
        modifier = modifier,
    ) {
        composable(TopLevel.TODAY.route) {
            val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
            TodayScreen(
                viewModel = viewModel,
                onAddMedicine = { navController.navigate("medicijn/0") },
            )
        }

        composable(TopLevel.MEDICINES.route) {
            val viewModel: MedicineListViewModel =
                viewModel(factory = MedicineListViewModel.factory(container))
            MedicineListScreen(
                viewModel = viewModel,
                onEdit = { id -> navController.navigate("medicijn/$id") },
            )
        }

        composable(TopLevel.HISTORY.route) {
            val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
            HistoryScreen(viewModel = viewModel)
        }

        composable(TopLevel.SETTINGS.route) {
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(viewModel = viewModel)
        }

        composable(
            route = EDIT_ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("medicineId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) { entry ->
            val medicineId = entry.arguments?.getLong("medicineId") ?: 0L
            val viewModel: MedicineEditViewModel = viewModel(
                key = "edit-$medicineId",
                factory = MedicineEditViewModel.factory(container, medicineId),
            )
            MedicineEditScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

private fun titleFor(route: String?): Int = when (route) {
    TopLevel.MEDICINES.route -> R.string.tab_medicines
    TopLevel.HISTORY.route -> R.string.tab_history
    TopLevel.SETTINGS.route -> R.string.tab_settings
    EDIT_ROUTE -> R.string.title_edit_medicine
    else -> R.string.tab_today
}
