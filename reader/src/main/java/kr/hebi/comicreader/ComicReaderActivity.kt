package kr.hebi.comicreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComicReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ComicRepository(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            var books by remember { mutableStateOf<List<ComicBook>>(emptyList()) }
            var busy by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var refreshVersion by remember { mutableIntStateOf(0) }
            var selectedBookId by rememberSaveable { mutableStateOf<String?>(null) }
            var mode by remember { mutableStateOf(repository.readMode()) }
            var direction by remember { mutableStateOf(repository.direction()) }
            var orientation by remember { mutableStateOf(repository.orientation()) }

            val folderPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        repository.addRoot(uri)
                        error = null
                        refreshVersion++
                    } catch (_: SecurityException) {
                        error = "폴더 접근 권한을 유지할 수 없습니다. 다른 폴더를 선택해 주세요."
                    }
                }
            }

            LaunchedEffect(refreshVersion) {
                busy = true
                error = null
                try {
                    books = withContext(Dispatchers.IO) { repository.scanBooks() }
                } catch (_: Exception) {
                    error = "폴더를 읽는 중 문제가 발생했습니다. 접근 권한을 확인해 주세요."
                } finally {
                    busy = false
                }
            }

            val activeBook = books.firstOrNull { it.id == selectedBookId }
            LaunchedEffect(selectedBookId, orientation) {
                requestedOrientation = when {
                    activeBook == null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    orientation == ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    orientation == ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (activeBook == null) {
                        show(WindowInsetsCompat.Type.systemBars())
                    } else {
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            BackHandler(enabled = selectedBookId != null) { selectedBookId = null }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                background = Color(0xFF101010),
                onBackground = Color.White,
                surface = Color(0xFF191919),
                onSurface = Color.White,
                surfaceVariant = Color(0xFF292929),
                onSurfaceVariant = Color(0xFFBBBBBB),
            )) {
                if (activeBook == null) {
                    LibraryScreen(
                        books = books,
                        roots = repository.roots().map { it to repository.rootTitle(it) },
                        repository = repository,
                        busy = busy,
                        error = error,
                        onAddFolder = { folderPicker.launch(null) },
                        onRefresh = { refreshVersion++ },
                        onRemoveFolder = {
                            repository.removeRoot(it)
                            refreshVersion++
                        },
                        onOpenBook = { selectedBookId = it.id },
                    )
                } else {
                    androidx.compose.runtime.key(activeBook.id) {
                        ReaderScreen(
                            book = activeBook,
                            repository = repository,
                            mode = mode,
                            direction = direction,
                            orientation = orientation,
                            onBack = { selectedBookId = null },
                            onModeChange = {
                                mode = it
                                repository.saveMode(it)
                            },
                            onDirectionChange = {
                                direction = it
                                repository.saveDirection(it)
                            },
                            onOrientationChange = {
                                orientation = it
                                repository.saveOrientation(it)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    books: List<ComicBook>,
    roots: List<Pair<android.net.Uri, String>>,
    repository: ComicRepository,
    busy: Boolean,
    error: String?,
    onAddFolder: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveFolder: (android.net.Uri) -> Unit,
    onOpenBook: (ComicBook) -> Unit,
) {
    var showFolders by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("내 만화", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("새로고침") }
            TextButton(onClick = { showFolders = true }) { Text("폴더 관리") }
            TextButton(onClick = onAddFolder) { Text("폴더 추가") }
        }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Text(error, color = Color(0xFFFFA6A6), modifier = Modifier.padding(16.dp))
        }
        Text(
            "작품 ${books.size}개 · 등록 폴더 ${roots.size}개",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )

        if (books.isEmpty() && !busy) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 등록된 만화가 없습니다.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("이미지 폴더를 선택하면 이곳에 표시됩니다.", color = Color.LightGray)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onAddFolder) { Text("폴더 불러오기") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(books, key = ComicBook::id) { book ->
                    Column(modifier = Modifier.clickable { onOpenBook(book) }) {
                        AsyncImage(
                            model = book.pages.firstOrNull()?.uri,
                            contentDescription = "${book.title} 표지",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(205.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            book.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${(repository.progress(book) + 1).coerceAtMost(book.pages.size)} / ${book.pages.size}장",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (showFolders) {
        AlertDialog(
            onDismissRequest = { showFolders = false },
            title = { Text("등록한 폴더") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (roots.isEmpty()) Text("등록된 폴더가 없습니다.")
                    roots.forEach { (uri, title) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, modifier = Modifier.weight(1f), maxLines = 2)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                onRemoveFolder(uri)
                                showFolders = false
                            }) {
                                Text("제거")
                            }
                        }
                    }
                    Text(
                        "목록에서만 제거되며 원본 이미지는 삭제되지 않습니다.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolders = false }) { Text("닫기") }
            },
        )
    }
}
