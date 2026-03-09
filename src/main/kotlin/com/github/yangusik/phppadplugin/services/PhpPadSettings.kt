package com.github.yangusik.phppadplugin.services

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.*
import java.util.UUID

class PhpPadSnippet {
    var id: String = UUID.randomUUID().toString()
    var name: String = "Snippet"
    var code: String = ""
    var createdAt: Long = System.currentTimeMillis()
}

class PhpPadHistoryEntry {
    var id: String = UUID.randomUUID().toString()
    var connectionName: String = ""
    var code: String = ""
    var duration: Double = 0.0
    var runAt: Long = System.currentTimeMillis()
}

class SshConnection {
    var id: String = UUID.randomUUID().toString()
    var type: String = "ssh"          // "ssh" or "docker"
    var name: String = "New Connection"
    var host: String = ""
    var port: Int = 22
    var username: String = ""
    var password: String = ""
    var privateKeyPath: String = ""
    var projectPath: String = "/var/www/html"
    var containerName: String = ""    // docker only
    override fun toString() = name
}

@Service(Service.Level.APP)
@State(name = "PhpPadSettings", storages = [Storage("phppad.xml")])
class PhpPadSettings : PersistentStateComponent<PhpPadSettings.State> {

    class State {
        var connectionsJson: String = "[]"
        var activeConnectionId: String = ""
        var lastCode: String = "<?php\n\n"
        var outputMode: String = "jcef"
        var splitterVertical: Boolean = false
        var snippetsJson: String = "[]"
        var historyJson: String = "[]"
    }

    private val gson = Gson()
    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    var connections: MutableList<SshConnection>
        get() = gson.fromJson(myState.connectionsJson, object : TypeToken<MutableList<SshConnection>>() {}.type) ?: mutableListOf()
        set(v) { myState.connectionsJson = gson.toJson(v) }

    var activeConnectionId: String
        get() = myState.activeConnectionId
        set(v) { myState.activeConnectionId = v }

    var lastCode: String
        get() = myState.lastCode
        set(v) { myState.lastCode = v }

    var outputMode: String
        get() = myState.outputMode
        set(v) { myState.outputMode = v }

    var splitterVertical: Boolean
        get() = myState.splitterVertical
        set(v) { myState.splitterVertical = v }

    var snippets: MutableList<PhpPadSnippet>
        get() = gson.fromJson(myState.snippetsJson, object : TypeToken<MutableList<PhpPadSnippet>>() {}.type) ?: mutableListOf()
        set(v) { myState.snippetsJson = gson.toJson(v) }

    var history: MutableList<PhpPadHistoryEntry>
        get() = gson.fromJson(myState.historyJson, object : TypeToken<MutableList<PhpPadHistoryEntry>>() {}.type) ?: mutableListOf()
        set(v) { myState.historyJson = gson.toJson(v) }

    fun addHistory(entry: PhpPadHistoryEntry) {
        val list = history
        addHistoryTo(list, entry)
        history = list
    }

    fun activeConnection(): SshConnection? = connections.find { it.id == activeConnectionId }

    companion object {
        const val MAX_HISTORY = 200

        /** Pure function — testable without IntelliJ platform. Mutates [list] in place. */
        fun addHistoryTo(list: MutableList<PhpPadHistoryEntry>, entry: PhpPadHistoryEntry) {
            list.add(0, entry)
            if (list.size > MAX_HISTORY) list.subList(MAX_HISTORY, list.size).clear()
        }

        fun getInstance(): PhpPadSettings = service()
    }
}
