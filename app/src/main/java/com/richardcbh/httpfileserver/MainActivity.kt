package com.richardcbh.httpfileserver

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.richardcbh.httpfileserver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serverService: FileServerService? = null
    private var isBound = false
    private var selectedFolderUri: Uri? = null
    private var selectedFolderName: String = ""

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist permission
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedFolderUri = it
            val docFile = DocumentFile.fromTreeUri(this, it)
            selectedFolderName = docFile?.name ?: "Unknown Folder"
            binding.tvSelectedFolder.text = getString(R.string.folder_selected) + " " + selectedFolderName
            updateStartButtonState()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FileServerService.LocalBinder
            serverService = binder.getService()
            isBound = true
            updateUIFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serverService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateStartButtonState()

        // Bind to service if running
        Intent(this, FileServerService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupListeners() {
        binding.btnSelectFolder.setOnClickListener {
            folderPicker.launch(null)
        }

        binding.btnStartStop.setOnClickListener {
            if (serverService?.isRunning == true) {
                stopServer()
            } else {
                startServer()
            }
        }

        binding.btnCopyLink.setOnClickListener {
            val link = binding.tvWebAddress.text.toString()
            if (link.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("HTTP Link", link)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
            }
        }

        // Listen for port changes
        binding.etPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateStartButtonState()
        }
    }

    private fun startServer() {
        val portStr = binding.etPort.text.toString()
        val port = portStr.toIntOrNull() ?: 8080

        if (selectedFolderUri == null) {
            Toast.makeText(this, "请先选择文件夹", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, FileServerService::class.java).apply {
            putExtra("folderUri", selectedFolderUri.toString())
            putExtra("port", port)
        }
        startForegroundService(intent)

        // Rebind to get updates
        if (!isBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        Toast.makeText(this, "正在启动服务器...", Toast.LENGTH_SHORT).show()
    }

    private fun stopServer() {
        val intent = Intent(this, FileServerService::class.java)
        stopService(intent)
        updateUIStopped()
    }

    private fun updateStartButtonState() {
        val hasFolder = selectedFolderUri != null
        val portValid = binding.etPort.text.toString().toIntOrNull() != null
        binding.btnStartStop.isEnabled = hasFolder && portValid
    }

    private fun updateUIFromService() {
        serverService?.let { service ->
            if (service.isRunning) {
                binding.btnStartStop.text = getString(R.string.stop_server)
                binding.tvStatus.text = getString(R.string.status_running)
                binding.tvWebAddressLabel.visibility = android.view.View.VISIBLE
                binding.tvWebAddress.visibility = android.view.View.VISIBLE
                binding.btnCopyLink.visibility = android.view.View.VISIBLE

                val ipv6 = service.currentIPv6Address ?: "[::1]"
                val port = service.currentPort
                val link = "http://[$ipv6]:$port/"
                binding.tvWebAddress.text = link
            } else {
                updateUIStopped()
            }
        }
    }

    private fun updateUIStopped() {
        binding.btnStartStop.text = getString(R.string.start_server)
        binding.tvStatus.text = getString(R.string.status_stopped)
        binding.tvWebAddressLabel.visibility = android.view.View.GONE
        binding.tvWebAddress.visibility = android.view.View.GONE
        binding.btnCopyLink.visibility = android.view.View.GONE
        binding.tvWebAddress.text = ""
    }

    override fun onResume() {
        super.onResume()
        if (isBound) {
            updateUIFromService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
    }
}
