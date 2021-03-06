package com.muhammadiqbalafandi.enotes.ui.addedit

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.lifecycle.*
import com.muhammadiqbalafandi.enotes.Event
import com.muhammadiqbalafandi.enotes.R
import com.muhammadiqbalafandi.enotes.data.Result.Success
import com.muhammadiqbalafandi.enotes.data.source.NoteRepository
import com.muhammadiqbalafandi.enotes.data.source.local.Note
import com.muhammadiqbalafandi.enotes.databinding.FragAddEditNoteBinding
import com.muhammadiqbalafandi.enotes.databinding.NavHeaderAddEditNoteBinding
import com.muhammadiqbalafandi.enotes.ui.encryptiontext.ENCRYPTION_KEY_PREFERENCES
import com.muhammadiqbalafandi.enotes.ui.encryptiontext.ENCRYPTION_KEY_SAVED_STATE_KEY
import com.muhammadiqbalafandi.enotes.ui.note.ADD_RESULT_OK
import com.muhammadiqbalafandi.enotes.ui.note.DELETE_RESULT_OK
import com.muhammadiqbalafandi.enotes.ui.note.EDIT_RESULT_OK
import com.muhammadiqbalafandi.enotes.utils.Utils
import kotlinx.coroutines.launch
import java.util.*

class AddEditNoteViewModel(
    private val noteRepository: NoteRepository, application: Application
) : AndroidViewModel(application) {

    // Used in this class.
    private val context: Application = getApplication()

    private var sharedPreferences: SharedPreferences =
        context.getSharedPreferences(
            ENCRYPTION_KEY_PREFERENCES, Activity.MODE_PRIVATE
        )

    private val noteId: MutableLiveData<String> = MutableLiveData()

    private val time: Date = Calendar.getInstance().time

    private var isNewNote: Boolean = false

    private var isDataLoaded: Boolean = false

    /**
     * Used directly in [FragAddEditNoteBinding].
     */
    val title: MutableLiveData<String> = MutableLiveData()

    val body: MutableLiveData<String> = MutableLiveData()

    private val pin: MutableLiveData<Boolean> = MutableLiveData(false)

    private val _encryptionKey: MutableLiveData<String> = MutableLiveData()
    val encryptionKey: LiveData<String> = _encryptionKey

    /**
     * Used directly in [NavHeaderAddEditNoteBinding].
     * Set menu title in drawer navigation view.
     */
    val pinStringRes: LiveData<Int> = pin.map {
        if (it) R.string.menu_title_unpin else R.string.menu_title_pin
    }

    val encryptionStringRes: LiveData<Int> = _encryptionKey.map {
        if (!it.isNullOrBlank()) R.string.menu_title_encryption_text_active else R.string.menu_title_encryption_text
    }

    val isDeletedNoteAvailable: LiveData<Boolean> = noteId.map {
        !it.isNullOrBlank()
    }

    // Used in fragment, to listen changes.
    private val _snackbarTextEvent: MutableLiveData<Event<Int>> = MutableLiveData()
    val snackbarTextEvent: LiveData<Event<Int>> = _snackbarTextEvent

    private val _goToEncryptionNoteEvent: MutableLiveData<Event<Unit>> = MutableLiveData()
    val goToEncryptionNoteEvent: LiveData<Event<Unit>> = _goToEncryptionNoteEvent

    private val _goToListNoteEvent: MutableLiveData<Event<Int>> = MutableLiveData()
    val goToListNoteEvent: LiveData<Event<Int>> = _goToListNoteEvent

    private val _showDialogDeleteNoteEvent: MutableLiveData<Event<Unit>> = MutableLiveData()
    val showDialogDeleteNoteEvent: LiveData<Event<Unit>> = _showDialogDeleteNoteEvent

    fun start(noteId: String?) {
        this.noteId.value = noteId

        if (noteId == null) {
            // No need to populate, it's a new note
            this.isNewNote = true
            return
        }

        if (this.isDataLoaded) {
            // No need to populate, already have data.
            return
        }

        this.isNewNote = false
        viewModelScope.launch {
            noteRepository.getNote(noteId).let {
                if (it is Success) {
                    onNotesValidation(it.data)
                } else {
                    // TODO: 03/10/20 test to error, what will happen.
                    onDataNotAvailable()
                }
            }
        }
    }

    private fun onNotesValidation(noteResult: Note) {
        val encryptionKey = noteResult.encryptionKey
        if (!encryptionKey.isNullOrBlank()) {
            noteDecryption(noteResult)
        } else {
            onNotesLoaded(noteResult, null)
        }
    }

    private fun onDataNotAvailable() {
        showSnackbarMessage(R.string.message_error_no_note)
    }

    private fun noteDecryption(noteResult: Note) {
        val body = noteResult.body
        val encryptionKey = noteResult.encryptionKey
        encryptionKey?.run {
            val decryptedBody = Utils.decryptionText(body, this)
            onNotesLoaded(noteResult, decryptedBody)
        }
    }

    private fun onNotesLoaded(noteResult: Note, decryptedBody: String?) {
        this.title.value = noteResult.title
        this.body.value = decryptedBody ?: noteResult.body
        this.pin.value = noteResult.pin
        this._encryptionKey.value = noteResult.encryptionKey

        this.isDataLoaded = true
    }

    /**
     * Used directly in [NavHeaderAddEditNoteBinding].
     */
    fun pin() {
        this.pin.value = this.pin.value?.run { !this }
    }

    fun goToEncryptionNote() {
        this._goToEncryptionNoteEvent.value = Event(Unit)
    }

    fun showDialogDeleteNote() {
        this._showDialogDeleteNoteEvent.value = Event(Unit)
    }

    fun agreeDeletedNote() {
        deleteNote()
    }

    private fun deleteNote() = viewModelScope.launch {
        noteId.value?.let {
            noteRepository.deleteNote(it)
            goToListNote(DELETE_RESULT_OK)
        }
    }

    fun saveNote() {
        val title = this.title.value
        val body = this.body.value
        val time = this.time
        val pin = this.pin.value ?: false
        val encryptionKey = this._encryptionKey.value
        val id = this.noteId.value

        if (body.isNullOrBlank()) {
            showSnackbarMessage(R.string.message_error_no_body)
            return
        }

        fun encryptionText(): String {
            return if (encryptionKey.isNullOrBlank()) {
                body
            } else {
                Utils.encryptionText(body, encryptionKey)
            }
        }

        if (this.isNewNote || id == null) {
            val note = Note(title, encryptionText(), time, pin, encryptionKey)
            createNote(note)
        } else {
            val note = Note(title, encryptionText(), time, pin, encryptionKey, id)
            updateNote(note)
        }
    }

    private fun createNote(note: Note) = viewModelScope.launch {
        noteRepository.saveNote(note)

        goToListNote(ADD_RESULT_OK)
    }

    private fun updateNote(note: Note) {
        if (this.isNewNote) {
            throw RuntimeException("updateNote() was called but note is new.")
        }
        viewModelScope.launch {
            noteRepository.saveNote(note)

            goToListNote(EDIT_RESULT_OK)
        }
    }

    private fun goToListNote(resultCode: Int) {
        this._goToListNoteEvent.value = Event(resultCode)
    }

    fun getSavedEncryptionKey() {
        val sharedPrefResult = this.sharedPreferences.getString(ENCRYPTION_KEY_SAVED_STATE_KEY, null)
        this._encryptionKey.value = sharedPrefResult
    }

    fun clearEncryptionKey() {
        this.sharedPreferences.edit().clear().apply()
    }

    private fun showSnackbarMessage(@StringRes message: Int) {
        this._snackbarTextEvent.value = Event(message)
    }
}
