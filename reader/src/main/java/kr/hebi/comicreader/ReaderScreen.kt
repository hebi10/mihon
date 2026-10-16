package kr.hebi.comicreader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ReaderScreen(
    book: ComicBook,
    repository: ComicRepository,
    mode: ReadingMode,
    direction: PageDirection,
    orientation: ScreenOrientation,
    onBack: () -> Unit,
    onModeChange: (ReadingMode) -> Unit,
    onDirectionChange: (PageDirection) -> Unit,
    onOrientationChange: (ScreenOrientation) -> Unit,
) {
    val pages = book.pages
    if (pages.isEmpty()) {
        Text("읽을 이미지가 없습니다.")
        return
    }

    val initialPage = remember(book.id) { repository.progress(book) }
    var currentPage by remember(book.id) { mutableIntStateOf(initialPage) }
    var showControls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    LaunchedEffect(book.id, mode) {
        if (mode == ReadingMode.PAGED) {
            pagerState.scrollToPage(currentPage)
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { currentPage = it }
        } else {
            listState.scrollToItem(currentPage)
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { currentPage = it }
        }
    }

    LaunchedEffect(currentPage) {
        repository.saveProgress(book, currentPage)
    }

    funOpenReaderContent(
        book = book,
        mode = mode,
        direction = direction,
        currentPage = currentPage,
        showControls = showControls,
        pagerState = pagerState,
        listState = listState,
        onToggleControls = { showControls = !showControls },
        onBack = onBack,
        onOpenChapters = { showChapters = true },
        onOpenSettings = { showSettings = true },
        onPrevious = {
            scope.launch {
                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
            }
        },
        onNext = {
            scope.launch {
                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pages.lastIndex))
            }
        },
    )

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("읽기 설정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("읽기 모드", style = MaterialTheme.typography.titleSmall)
                    Row {
                        Option("가로 페이지", mode == ReadingMode.PAGED) {
                            onModeChange(ReadingMode.PAGED)
                        }
                        Option("세로 웹툰", mode == ReadingMode.WEBTOON) {
                            onModeChange(ReadingMode.WEBTOON)
                        }
                    }
                    Text("페이지 넘김 방향", style = MaterialTheme.typography.titleSmall)
                    Row {
                        Option("오른쪽 → 왼쪽", direction == PageDirection.RIGHT_TO_LEFT) {
                            onDirectionChange(PageDirection.RIGHT_TO_LEFT)
                        }
                        Option("왼쪽 → 오른쪽", direction == PageDirection.LEFT_TO_RIGHT) {
                            onDirectionChange(PageDirection.LEFT_TO_RIGHT)
                        }
                    }
                    Text("화면 방향", style = MaterialTheme.typography.titleSmall)
                    Row {
                        Option("자동", orientation == ScreenOrientation.AUTO) {
                            onOrientationChange(ScreenOrientation.AUTO)
                        }
                        Option("세로", orientation == ScreenOrientation.PORTRAIT) {
                            onOrientationChange(ScreenOrientation.PORTRAIT)
                        }
                        Option("가로", orientation == ScreenOrientation.LANDSCAPE) {
                            onOrientationChange(ScreenOrientation.LANDSCAPE)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("완료") } },
        )
    }

    if (showChapters) {
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("회차 선택") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    var firstPage = 0
                    book.chapters.forEach { chapter ->
                        val index = firstPage
                        firstPage += chapter.pages.size
                        item(key = index) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        currentPage = index
                                        pagerState.scrollToPage(index)
                                        listState.scrollToItem(index)
                                        showChapters = false
                                    }
                                }.padding(vertical = 13.dp),
                            ) {
                                Text(chapter.title, modifier = Modifier.weight(1f))
                                Text("${chapter.pages.size}장", color = Color.LightGray)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("닫기") } },
        )
    }
}

@Composable
private fun Option(label: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            if (isSelected) "✓ $label" else label,
            color = if (isSelected) Color.White else Color.LightGray,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun funOpenReaderContent(
    book: ComicBook,
    mode: ReadingMode,
    direction: PageDirection,
    currentPage: Int,
    showControls: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (mode == ReadingMode.PAGED) {
            HorizontalPager(
                state = pagerState,
                reverseLayout = direction == PageDirection.RIGHT_TO_LEFT,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                ZoomablePage(
                    uri = book.pages[index].uri,
                    contentDescription = book.pages[index].filename,
                    isPaged = true,
                    onTap = onToggleControls,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(book.pages, key = { _, page -> page.uri.toString() }) { _, page ->
                    ZoomablePage(
                        uri = page.uri,
                        contentDescription = page.filename,
                        isPaged = false,
                        onTap = onToggleControls,
                    )
                }
            }
        }

        if (showControls) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xEE171717))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("뒤로") }
                Text(
                    book.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onOpenChapters) { Text("목차") }
                TextButton(onClick = onOpenSettings) { Text("설정") }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xEE171717))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mode == ReadingMode.PAGED) {
                    TextButton(onClick = onPrevious) { Text("이전") }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${currentPage + 1} / ${book.pages.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                if (mode == ReadingMode.PAGED) {
                    TextButton(onClick = onNext) { Text("다음") }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    uri: android.net.Uri,
    contentDescription: String,
    isPaged: Boolean,
    onTap: () -> Unit,
) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale <= 1f) Offset.Zero else offset + panChange
    }

    val container = if (isPaged) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    Box(
        modifier = container.clipToBounds()
            .transformable(state = transformState, canPan = { scale > 1f })
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        offset = Offset.Zero
                    },
                )
            },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = if (isPaged) {
                Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
            } else {
                Modifier.fillMaxWidth().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
            },
            contentScale = if (isPaged) ContentScale.Fit else ContentScale.FillWidth,
        )
    }
}
