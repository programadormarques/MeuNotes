package br.com.meunotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = NotesDatabase.get(applicationContext).noteDao()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF315C45),
                    secondary = Color(0xFF547B64),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE)
                )
            ) {
                NotesApp(dao)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesApp(dao: NoteDao) {
    val notes by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (editing != null || creating) {
        NoteEditor(
            initial = editing,
            onBack = { editing = null; creating = false },
            onSave = { title, content ->
                scope.launch {
                    if (title.isNotBlank() || content.isNotBlank()) {
                        val old = editing
                        dao.save(
                            Note(
                                id = old?.id ?: 0,
                                title = title.trim().ifBlank { "Sem título" },
                                content = content,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                editing = null
                creating = false
            },
            onDelete = {
                editing?.let { note -> scope.launch { dao.delete(note) } }
                editing = null
                creating = false
            }
        )
        return
    }

    val filtered = notes.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.content.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Meu Notes", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${notes.size} ${if (notes.size == 1) "anotação" else "anotações"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nova anotação")
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                placeholder = { Text("Pesquisar anotações") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Nenhuma anotação ainda.\nToque em + para criar."
                        else "Nenhuma anotação encontrada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(filtered, key = { it.id }) { note ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { editing = note }
                                .padding(vertical = 14.dp)
                        ) {
                            Text(
                                note.title.ifBlank { "Sem título" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (note.content.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    note.content.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(note.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
    initial: Note?,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var content by remember(initial?.id) { mutableStateOf(initial?.content ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nova anotação" else "Editar anotação") },
                navigationIcon = {
                    IconButton(onClick = { onSave(title, content) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Salvar e voltar")
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir anotação")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text("Título", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    inner()
                },
                singleLine = true
            )
            Spacer(Modifier.height(18.dp))
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { inner ->
                    if (content.isEmpty()) {
                        Text("Comece a escrever...", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    inner()
                }
            )
        }
    }
}
