package nl.ramon96.medicijntracker

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.ramon96.medicijntracker.data.prefs.ThemeSettings
import nl.ramon96.medicijntracker.di.AppContainer
import nl.ramon96.medicijntracker.domain.barcode.ScannedCode
import nl.ramon96.medicijntracker.ui.history.HistoryScreen
import nl.ramon96.medicijntracker.ui.history.HistoryViewModel
import nl.ramon96.medicijntracker.ui.medicines.MedicineEditScreen
import nl.ramon96.medicijntracker.ui.medicines.MedicineEditViewModel
import nl.ramon96.medicijntracker.ui.medicines.MedicineListScreen
import nl.ramon96.medicijntracker.ui.medicines.MedicineListViewModel
import nl.ramon96.medicijntracker.ui.medicines.ScanPrefill
import nl.ramon96.medicijntracker.ui.scan.ScanSheet
import nl.ramon96.medicijntracker.ui.scan.ScanUiState
import nl.ramon96.medicijntracker.ui.scan.ScanViewModel
import nl.ramon96.medicijntracker.ui.settings.SettingsScreen
import nl.ramon96.medicijntracker.ui.settings.SettingsViewModel
import nl.ramon96.medicijntracker.scan.rememberCodeScanner
import nl.ramon96.medicijntracker.ui.theme.MedicijnTheme
import nl.ramon96.medicijntracker.ui.today.TodayScreen
import nl.ramon96.medicijntracker.ui.today.TodayViewModel
import java.time.LocalDate

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
            // Until DataStore has emitted, render the defaults rather than nothing - otherwise
            // the app flashes an unstyled frame on every launch.
            val themeSettings by container.settings.theme
                .collectAsStateWithLifecycle(initialValue = ThemeSettings())

            MedicijnTheme(settings = themeSettings) {
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

/**
 * Optional query arguments carry what a scan already read into the form. They do not affect the
 * base path, so plain "medicijn/0" and "medicijn/7" navigation keeps working unchanged.
 */
private const val EDIT_ROUTE =
    "medicijn/{medicineId}?gtin={gtin}&vervaldatum={vervaldatum}&naam={naam}&sterkte={sterkte}"

/**
 * Values come from a scan and from a third-party database, so they can contain spaces, slashes
 * and hashes - all of which break Navigation-Compose route matching unless encoded.
 */
private fun editRoute(medicineId: Long, prefill: ScanPrefill? = null): String {
    if (prefill == null || prefill.isEmpty) return "medicijn/$medicineId"
    val query = listOfNotNull(
        prefill.gtin?.let { "gtin=" + Uri.encode(it) },
        prefill.expiry?.let { "vervaldatum=" + Uri.encode(it.toString()) },
        prefill.name?.let { "naam=" + Uri.encode(it) },
        prefill.dosage?.let { "sterkte=" + Uri.encode(it) },
    ).joinToString("&")
    return "medicijn/$medicineId?$query"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicijnAppUi(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isEditing = currentRoute == EDIT_ROUTE

    // Hoisted above the Scaffold so a scan can be started from any tab and the sheet lands on top
    // of the bottom bar rather than underneath it.
    val scanViewModel: ScanViewModel = viewModel(factory = ScanViewModel.factory(container))
    val scanState by scanViewModel.state.collectAsStateWithLifecycle()
    val scanMedicines by scanViewModel.medicines.collectAsStateWithLifecycle()
    val startScan = rememberCodeScanner { scanViewModel.onScanResult(it) }

    ScanSheet(
        state = scanState,
        medicines = scanMedicines,
        onAmountChange = scanViewModel::setAmount,
        onConfirmStock = { scanViewModel.confirmStock() },
        onSkipLookup = scanViewModel::skipLookup,
        onDismiss = scanViewModel::dismiss,
        onCreateFromScan = {
            val code = scanState.scannedCode()
            scanViewModel.dismiss()
            navController.navigate(
                editRoute(0L, ScanPrefill(gtin = code?.gtin, expiry = code?.expiry)),
            )
        },
        onUseSuggestion = {
            val suggested = scanState as? ScanUiState.Suggested
            scanViewModel.dismiss()
            if (suggested != null) {
                navController.navigate(
                    editRoute(
                        0L,
                        ScanPrefill(
                            gtin = suggested.code.gtin,
                            expiry = suggested.code.expiry,
                            name = suggested.suggestion.name,
                        ),
                    ),
                )
            }
        },
        onLinkToExisting = { medicineId ->
            scanState.scannedCode()?.let { code ->
                scanViewModel.linkTo(medicineId, code) { navController.navigate(editRoute(it)) }
            }
        },
    )

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
            onScan = startScan,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun NavGraph(
    navController: NavHostController,
    container: AppContainer,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevel.TODAY.route,
        modifier = modifier,
        // Navigation-Compose cross-fades between destinations by default. Switching tabs is not
        // a journey anywhere, and the fade just delays reading the screen.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(TopLevel.TODAY.route) {
            val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
            TodayScreen(
                viewModel = viewModel,
                onAddMedicine = { navController.navigate(editRoute(0L)) },
                onScan = onScan,
            )
        }

        composable(TopLevel.MEDICINES.route) {
            val viewModel: MedicineListViewModel =
                viewModel(factory = MedicineListViewModel.factory(container))
            MedicineListScreen(
                viewModel = viewModel,
                onEdit = { id -> navController.navigate(editRoute(id)) },
                onScan = onScan,
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
                navArgument("medicineId") { type = NavType.LongType },
                navArgument("gtin") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("vervaldatum") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("naam") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("sterkte") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val medicineId = entry.arguments?.getLong("medicineId") ?: 0L
            val prefill = entry.arguments?.let { args ->
                ScanPrefill(
                    gtin = args.getString("gtin"),
                    expiry = args.getString("vervaldatum")
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    name = args.getString("naam"),
                    dosage = args.getString("sterkte"),
                )
            }?.takeIf { !it.isEmpty }

            val viewModel: MedicineEditViewModel = viewModel(
                // The scanned code is part of the key, so scanning a second box into the same
                // medicine does not reuse the view model built for the first one.
                key = "edit-$medicineId-${prefill?.gtin.orEmpty()}",
                factory = MedicineEditViewModel.factory(container, medicineId, prefill),
            )
            MedicineEditScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }
    }
}

/** The code behind whichever sheet state is showing, so navigation can carry it into the form. */
private fun ScanUiState.scannedCode(): ScannedCode? = when (this) {
    is ScanUiState.Recognised -> code
    is ScanUiState.LookingUp -> code
    is ScanUiState.Suggested -> code
    is ScanUiState.Unknown -> code
    ScanUiState.Idle, is ScanUiState.Failed -> null
}

private fun titleFor(route: String?): Int = when (route) {
    TopLevel.MEDICINES.route -> R.string.tab_medicines
    TopLevel.HISTORY.route -> R.string.tab_history
    TopLevel.SETTINGS.route -> R.string.tab_settings
    EDIT_ROUTE -> R.string.title_edit_medicine
    else -> R.string.tab_today
}
