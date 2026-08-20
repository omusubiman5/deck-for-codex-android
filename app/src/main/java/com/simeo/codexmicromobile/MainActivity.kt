package com.simeo.codexmicromobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n")
class MainActivity : Activity(), RelayEvents {
  private enum class Screen { CONTROL, PALETTE, USAGE, HOSTS }

  private lateinit var repository: ProfileRepository
  private lateinit var discovery: NearbyDiscovery
  private val handler = Handler(Looper.getMainLooper())
  private var client: RelayClient? = null
  private var profile: PairingProfile? = null
  private var snapshot: DeckSnapshot? = null
  private var currentScreen = Screen.CONTROL
  private var connectionState = "connecting"
  private var connectionDetail: String? = null
  private var connectedMarked = false
  private var usageMode = "auto"
  private var paletteCategory = "すべて"
  private var paletteQuery = ""
  private var selectedKeycap = OfficialKeycaps.all.first()
  private var errorMessage: String? = null
  private val keycapResults = mutableMapOf<String, String>()
  private val pressButtons = mutableListOf<CommandButton>()
  private val scrollPositions = mutableMapOf<Screen, Int>()
  private var activeScroll: ScrollView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    repository = ProfileRepository(SecureProfileStore(this))
    discovery = NearbyDiscovery(this, ::onDiscoveredRelay)
    if (!handlePairingIntent(intent)) {
      val selected = repository.selected()
      if (selected == null) showScreen(Screen.HOSTS) else connect(selected)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handlePairingIntent(intent)
  }

  override fun onStart() {
    super.onStart()
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), NEARBY_PERMISSION_REQUEST)
    } else discovery.start()
  }

  override fun onStop() { releasePresses(); discovery.stop(); super.onStop() }
  override fun onDestroy() { releasePresses(); client?.stop(); super.onDestroy() }

  @Deprecated("ZXing compatibility")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
    if (result != null) result.contents?.let(::pair)
      ?: Toast.makeText(this, "QRスキャンをキャンセルしました。", Toast.LENGTH_SHORT).show()
    else super.onActivityResult(requestCode, resultCode, data)
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode != NEARBY_PERMISSION_REQUEST) return
    if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) discovery.start()
    else setError("Nearbyデバイス権限がないためLAN探索を利用できません。")
  }

  private fun handlePairingIntent(intent: Intent?): Boolean = intent?.dataString?.let(::pair) ?: false

  private fun pair(raw: String): Boolean = runCatching { PairingProfile.parse(raw) }.fold(
    onSuccess = { scanned ->
      when (val update = repository.upsert(scanned)) {
        is ProfileUpdate.Saved -> connect(update.profile)
        is ProfileUpdate.FingerprintChanged -> AlertDialog.Builder(this)
          .setTitle("証明書が変更されています")
          .setMessage("${update.existing.name}は同じhost IDですが証明書が一致しません。既存profileを削除して再ペアリングしてください。")
          .setPositiveButton("Hostsへ") { _, _ -> showScreen(Screen.HOSTS) }.setCancelable(false).show()
        ProfileUpdate.LimitReached -> setError("PCは最大8台です。不要なprofileを削除してください。")
      }
      true
    },
    onFailure = { setError(it.message ?: "ペアリングできませんでした。"); false }
  )

  private fun scanQr() {
    IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
      .setPrompt("PCアプリ内のCodex MicroペアリングQRを読み取ります")
      .setBeepEnabled(false).setOrientationLocked(false).initiateScan()
  }

  private fun connect(next: PairingProfile) {
    rememberScrollPosition()
    releasePresses()
    client?.stop()
    connectedMarked = false
    profile = next
    snapshot = null
    connectionState = "connecting"
    connectionDetail = next.name
    repository.select(next.id)
    currentScreen = Screen.CONTROL
    render()
    client = RelayClient(next, this).also { it.start() }
  }

  private fun showScreen(screen: Screen) {
    releasePresses()
    rememberScrollPosition()
    currentScreen = screen
    render()
  }

  private fun render() {
    rememberScrollPosition()
    pressButtons.clear()
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SURFACE) }
    root.addView(appBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
    val scroll = ScrollView(this).apply { isFillViewport = true }
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(12), dp(12), dp(12), dp(22))
    }
    when (currentScreen) {
      Screen.CONTROL -> renderControl(content)
      Screen.PALETTE -> renderPalette(content)
      Screen.USAGE -> renderUsage(content)
      Screen.HOSTS -> renderHosts(content)
    }
    scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    activeScroll = scroll
    val restoreY = scrollPositions[currentScreen] ?: 0
    scroll.post { if (activeScroll === scroll) scroll.scrollTo(0, restoreY) }
    root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    root.addView(bottomNavigation(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }
    setContentView(root)
    ViewCompat.requestApplyInsets(root)
  }

  private fun rememberScrollPosition() {
    activeScroll?.let { scrollPositions[currentScreen] = it.scrollY }
    activeScroll = null
  }

  private fun appBar(): View {
    val bar = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(8), 0, dp(10), 0); setBackgroundColor(NAVY)
    }
    val menu = flatButton(if (currentScreen == Screen.CONTROL) "☰" else "‹", NAVY, Color.WHITE).apply {
      textSize = 23f
      setOnClickListener { if (currentScreen == Screen.CONTROL) showAppMenu(this) else showScreen(Screen.CONTROL) }
    }
    bar.addView(menu, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT))
    val title = when (currentScreen) {
      Screen.CONTROL -> "Codex Micro"
      Screen.PALETTE -> "公式 Keycap 30"
      Screen.USAGE -> "利用状況 & 制限"
      Screen.HOSTS -> "ホスト & 設定"
    }
    bar.addView(label(title, 20f, Color.WHITE, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (currentScreen == Screen.CONTROL) bar.addView(chip(statusLabel(connectionState), statusColor(connectionState)))
    else if (currentScreen == Screen.PALETTE) bar.addView(label("⌕", 27f, Color.WHITE, false))
    else {
      val overflow = flatButton("⋮", NAVY, Color.WHITE).apply { textSize = 26f; setOnClickListener { showScreenMenu(this) } }
      bar.addView(overflow, LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT))
    }
    return bar
  }

  private fun renderControl(content: LinearLayout) {
    val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    targetRow.addView(targetButton(HostPlatform.WINDOWS, "▣  Windows"), weighted())
    targetRow.addView(targetButton(HostPlatform.MACOS, "●  Mac"), weighted(left = 8))
    content.addView(targetRow)
    val observed = snapshot?.receivedAt
    val freshness = observed?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) }
    val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    meta.addView(small("最終更新: ${observed?.let(::time) ?: "—"}"), weighted())
    meta.addView(small(if (freshness == null) "● waiting" else "● fresh (${freshness}s)", if (freshness != null && freshness < 30) GREEN else ORANGE))
    meta.addView(flatButton("⟳", SURFACE, NAVY).apply { setOnClickListener { reconnectSelected() } }, LinearLayout.LayoutParams(dp(48), dp(44)))
    content.addView(meta)
    errorMessage?.let { content.addView(alert(it), match(top = 6)) }

    content.addView(sectionHeader("AGENTS  (動的スロット)", "6 スロット"))
    val agentGrid = grid(2)
    val agents = snapshot?.agents ?: List(6) { AgentState(it, null, null, null, "off", false, null, null, null) }
    agents.forEach { agent ->
      val project = agent.projectName?.trim().takeUnless { it.isNullOrBlank() }
        ?: agent.nativeTitle?.trim().takeUnless { it.isNullOrBlank() } ?: "未割当"
      val subtitle = if (agent.projectName != null) agent.nativeTitle.orEmpty() else platformName(snapshot?.platform ?: profile?.platform)
      val context = agent.contextPercent?.roundToInt()?.let { "$it%" } ?: "—"
      val card = commandButton("${agent.slot + 1}  $project${if (agent.selected) "  ✓" else ""}\n${statusLabel(agent.status)}   $context\n$subtitle",
        if (agent.selected) BLUE else Color.WHITE, if (agent.selected) Color.WHITE else TEXT)
      card.isEnabled = agent.threadKey != null && canCommand()
      card.setOnClickListener {
        val thread = agent.threadKey ?: return@setOnClickListener
        haptic(); send(CommandFactory.agentTap(agent.slot, thread), "Agent ${agent.slot + 1}")
      }
      card.setOnLongClickListener { showAgentDetail(agent); true }
      agentGrid.addView(card, gridParams(2, 116))
    }
    content.addView(agentGrid)

    content.addView(sectionHeader("MICRO ACTIONS  (動的アクション)", "6 スロット"))
    val actionGrid = grid(6)
    RelayClient.ACTION_SLOTS.forEachIndexed { index, slot ->
      val label = snapshot?.actionLabels?.get(slot) ?: defaultActionLabel(slot)
      val keycap = snapshot?.actionKeycaps?.get(slot).orEmpty()
      val operation = when {
        keycap.isBlank() -> "待機中"
        else -> "実行"
      }
      val button = commandButton("ACT0${index + 1}\n$label\n$operation", keycapColor(keycap), Color.WHITE)
      button.isEnabled = canCommand()
      bindPress(button) { { act -> CommandFactory.action(slot, act) } }
      actionGrid.addView(button, gridParams(6, 82))
    }
    content.addView(actionGrid)

    content.addView(sectionHeader("JOYSTICK / REASONING  (動的コントロール)"))
    val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    controls.addView(featureCard("✣\nJoystick", "操作モード\nACTIVE") { showJoystick() }, weighted())
    controls.addView(featureCard("◉\nReasoning", "推論モード\n${snapshot?.let { "LIVE" } ?: "—"}") { showReasoning() }, weighted(left = 8))
    content.addView(controls)
  }

  private fun renderPalette(content: LinearLayout) {
    val search = EditText(this).apply {
      hint = "Keycap ID・名前・説明を検索"; setSingleLine(); setText(paletteQuery); setTextColor(TEXT); setHintTextColor(MUTED)
      setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(Color.WHITE, 10f, BORDER)
      addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { paletteQuery = s?.toString().orEmpty() }
        override fun afterTextChanged(s: Editable?) {}
      })
      setOnEditorActionListener { _, _, _ -> render(); true }
    }
    content.addView(search, match())
    val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("すべて", "アクション", "ナビゲーション", "開発", "その他").forEach { category ->
      categories.addView(flatButton(category, if (paletteCategory == category) BLUE else Color.WHITE,
        if (paletteCategory == category) Color.WHITE else TEXT).apply { setOnClickListener { paletteCategory = category; render() } }, weighted(left = 3))
    }
    content.addView(categories, match(top = 8))
    val filtered = OfficialKeycaps.all.filter { item ->
      (paletteCategory == "すべて" || item.category == paletteCategory) &&
        (paletteQuery.isBlank() || listOf(item.id, item.name, item.description).any { it.contains(paletteQuery, true) })
    }
    content.addView(sectionHeader("公式 KEYCAP PALETTE", "${filtered.size} キー"))
    val keyGrid = grid(5)
    filtered.forEach { keycap ->
      val button = commandButton("${keycap.id}\n${keycap.name}", keycapColor(keycap.id), Color.WHITE)
      val capability = snapshot?.keycapCapabilities?.get(keycap.id)
      val available = canCommand() && capability?.status == "ready"
      button.alpha = if (available) 1f else .42f
      button.isEnabled = available
      if (keycap.id == "MIC") bindMicPress(button)
      else button.setOnClickListener { selectedKeycap = keycap; send(CommandFactory.keycap(keycap.id), "KEYCAP:${keycap.id}"); render() }
      keyGrid.addView(button, gridParams(5, 72))
    }
    content.addView(keyGrid)
    content.addView(small("● アクション   ● ナビゲーション   ● 開発   ● その他   ● システム", MUTED), match(top = 10))
    content.addView(sectionHeader("選択中キー — キー情報"))
    content.addView(infoCard("${selectedKeycap.id}  ${selectedKeycap.name}", listOf(
      "カテゴリ" to selectedKeycap.category,
      "説明" to selectedKeycap.description,
      "互換性" to "Windows / Mac",
      "状態" to when {
        !canCommand() -> "接続待ち"
        snapshot?.keycapCapabilities?.get(selectedKeycap.id)?.status != "ready" -> "registry／handler未解決"
        else -> "実行可能（Codex画面条件あり） ✓"
      }
    ) + listOf(
      "Action型" to (snapshot?.keycapCapabilities?.get(selectedKeycap.id)?.actionType ?: "—"),
      "最終結果" to (keycapResults[selectedKeycap.id] ?: "未実行")
    )))
  }

  private fun renderUsage(content: LinearLayout) {
    content.addView(sectionHeader("使用量モード"))
    val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("auto" to "自動\n(推奨)", "five-hour" to "5時間\n制限", "weekly" to "週間\n制限", "other" to "その他\nカスタム").forEach { (mode, text) ->
      modes.addView(flatButton(text, if (usageMode == mode) BLUE else Color.WHITE, if (usageMode == mode) Color.WHITE else TEXT).apply {
        setOnClickListener { usageMode = mode; render() }
      }, weighted(left = 4, height = 62))
    }
    content.addView(modes)
    val usage = snapshot?.usage
    val windows = usage?.windows.orEmpty()
    val chosen = when (usageMode) {
      "five-hour", "weekly", "other" -> windows.firstOrNull { it.kind == usageMode }
      else -> windows.firstOrNull { it.kind == "five-hour" } ?: windows.firstOrNull { it.kind == "weekly" } ?: windows.firstOrNull()
    }
    content.addView(sectionHeader("全体使用状況サマリー  (${usageModeLabel()})"))
    content.addView(usageCard("現在", chosen, snapshot?.hostName ?: "—", usage?.observedAt))
    windows.filter { it !== chosen }.forEach { window ->
      content.addView(usageCard(windowKind(window.kind), window, snapshot?.hostName ?: "—", usage?.observedAt), match(top = 10))
    }
    if (windows.isEmpty()) content.addView(alert("Usage snapshotを受信していません。"), match(top = 8))

    content.addView(sectionHeader("Rate Limit Reset  (上限リセット)"))
    val available = usage?.resetCreditsAvailable ?: 0
    val applicable = usage?.resetCreditsApplicable ?: 0
    val reset = commandButton("⚠   1.2秒長押しで実行   🔒\ncredit: $available / applicable: $applicable", RED, Color.WHITE)
    reset.isEnabled = available > 0 && applicable > 0 && canCommand()
    bindHold(reset, 1_200) { send(CommandFactory.rateLimitReset(), "Rate Limit Reset") }
    content.addView(reset, match(height = 74))
    content.addView(small(if (reset.isEnabled) "長押しして、リセットを実行してください" else "現在の状態: 実行不可", if (reset.isEnabled) RED else MUTED), match(top = 5))
  }

  private fun renderHosts(content: LinearLayout) {
    content.addView(sectionHeader("ターゲット切替"))
    val targets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    targets.addView(targetButton(HostPlatform.WINDOWS, "▣  Windows"), weighted())
    targets.addView(targetButton(HostPlatform.MACOS, "●  Mac"), weighted(left = 8))
    content.addView(targets)
    content.addView(sectionHeader("ホスト / プロファイル一覧", "${repository.profiles().size} / 8"))
    repository.profiles().forEach { item ->
      val selected = profile?.id == item.id
      val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.WHITE, 10f, if (selected) BLUE else BORDER)
        setPadding(dp(12), dp(8), dp(4), dp(8))
      }
      row.addView(label("${platformName(item.platform)}  ${item.name}\n${if (selected) statusLabel(connectionState) else "登録済み"}", 14f, TEXT, selected), weighted())
      if (selected) row.addView(chip("現在のホスト", BLUE))
      row.addView(flatButton("⋮", Color.WHITE, TEXT).apply { setOnClickListener { showHostMenu(this, item) } }, LinearLayout.LayoutParams(dp(48), dp(52)))
      row.setOnClickListener { connect(item) }
      content.addView(row, match(top = 7))
    }
    if (repository.profiles().isEmpty()) content.addView(alert("PCが未登録です。PCアプリ内のQRをスキャンしてください。"))
    content.addView(flatButton("＋  PCアプリ内QRをスキャン", BLUE, Color.WHITE).apply { setOnClickListener { scanQr() } }, match(top = 12, height = 52))
    val link = EditText(this).apply { hint = "codexdeck://pair?..."; setTextColor(TEXT); setHintTextColor(MUTED); minLines = 2; background = rounded(Color.WHITE, 9f, BORDER) }
    content.addView(link, match(top = 10))
    content.addView(flatButton("ペアリングリンクから追加", Color.WHITE, BLUE).apply { setOnClickListener { pair(link.text.toString()) } }, match(top = 5, height = 48))

    content.addView(sectionHeader("選択中ホストのセッション情報"))
    val selected = profile
    content.addView(infoCard(selected?.name ?: "未選択", listOf(
      "接続状態" to statusLabel(connectionState),
      "最終更新" to (snapshot?.receivedAt?.let(::time) ?: "—"),
      "Endpoint" to (selected?.endpoint ?: "—"),
      "Agent source" to (snapshot?.agentSource ?: "—"),
      "Codex version" to (snapshot?.codexVersion ?: "—"),
      "Lighting" to (snapshot?.lightingAutoOff ?: "—")
    )))
    connectionDetail?.let { content.addView(small("health: $it", MUTED), match(top = 6)) }

    content.addView(sectionHeader("設定とアクション"))
    val settings = grid(4)
    listOf(
      "◐\nテーマ切替" to { toast("System / Light / Dark は次回設定保存で反映します。") },
      "♢\n通知設定" to { toast("通知設定") },
      "⟳\n再読み込み" to { reconnectSelected() },
      "▷\n接続テスト" to { reconnectSelected(); toast("接続テストを開始しました。") }
    ).forEach { (text, action) -> settings.addView(commandButton(text, Color.WHITE, TEXT).apply { setOnClickListener { action() } }, gridParams(4, 72)) }
    content.addView(settings)
    val links = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    links.addView(flatButton("Usage 詳細", PALE_BLUE, BLUE).apply { setOnClickListener { showScreen(Screen.USAGE) } }, weighted())
    links.addView(flatButton("接続 詳細", PALE_BLUE, BLUE).apply { setOnClickListener { toast(connectionDetail ?: connectionState) } }, weighted(left = 5))
    links.addView(flatButton("PC一覧・追加・削除", PALE_BLUE, BLUE).apply { setOnClickListener { showScreen(Screen.HOSTS) } }, weighted(left = 5))
    content.addView(links, match(top = 10))
  }

  private fun showJoystick() {
    val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(16)) }
    val grid = grid(3)
    listOf(null, "up" to "↑ Plan", null, "left" to "← Back", "down" to "↓ Sidebar", "right" to "Forward →").forEach { item ->
      if (item == null) grid.addView(View(this), gridParams(3, 64)) else {
        val button = commandButton(item.second, PALE_BLUE, BLUE)
        bindPress(button) { { act -> CommandFactory.joystick(item.first, act) } }
        grid.addView(button, gridParams(3, 64))
      }
    }
    container.addView(grid)
    AlertDialog.Builder(this).setTitle("Joystick — 4方向").setView(container).setNegativeButton("閉じる", null).show()
  }

  private fun showReasoning() {
    val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(8), dp(12), dp(16)) }
    val encoder = commandButton("Encoder\nPress", PALE_BLUE, BLUE)
    bindPress(encoder) { { act -> CommandFactory.encoder(act) } }
    container.addView(encoder, weighted(height = 72))
    val less = commandButton("MIND-\nLESS", Color.rgb(104, 58, 210), Color.WHITE)
    bindRepeat(less, "decrease")
    container.addView(less, weighted(left = 6, height = 72))
    val more = commandButton("MIND+\nMORE", Color.rgb(104, 58, 210), Color.WHITE)
    bindRepeat(more, "increase")
    container.addView(more, weighted(left = 6, height = 72))
    AlertDialog.Builder(this).setTitle("Reasoning").setView(container).setNegativeButton("閉じる", null).show()
  }

  private fun showAgentDetail(agent: AgentState) {
    AlertDialog.Builder(this).setTitle(agent.projectName ?: agent.nativeTitle ?: "未割当")
      .setMessage(listOf(
        "Native title: ${agent.nativeTitle ?: "—"}", "Status: ${agent.status}",
        "Context: ${agent.contextPercent?.roundToInt()?.let { "$it%" } ?: "—"}",
        "Thread: ${agent.threadKey?.takeLast(18) ?: "—"}", "Activity: ${agent.activityAt?.let(::time) ?: "—"}",
        "Owned: ${agent.ownedByHost ?: "—"}"
      ).joinToString("\n")).setPositiveButton("閉じる", null).show()
  }

  private fun showAppMenu(anchor: View) {
    PopupMenu(this, anchor).apply {
      listOf("Control", "公式 Keycap Palette", "Usage / 制限", "Hosts / 設定", "QRをスキャン", "診断情報", "このアプリについて").forEach { menu.add(it) }
      setOnMenuItemClickListener {
        when (it.title.toString()) {
          "Control" -> showScreen(Screen.CONTROL)
          "公式 Keycap Palette" -> showScreen(Screen.PALETTE)
          "Usage / 制限" -> showScreen(Screen.USAGE)
          "Hosts / 設定" -> showScreen(Screen.HOSTS)
          "QRをスキャン" -> scanQr()
          "診断情報" -> toast("${statusLabel(connectionState)} / ${profile?.endpoint ?: "未登録"}")
          else -> AlertDialog.Builder(this@MainActivity).setTitle("Codex Micro Mobile")
            .setMessage("Version 0.2.6\nMIT License").setPositiveButton("閉じる", null).show()
        }; true
      }
      show()
    }
  }

  private fun showScreenMenu(anchor: View) {
    PopupMenu(this, anchor).apply {
      if (currentScreen == Screen.USAGE) listOf("最新状態へ更新", "自動更新", "取得元を表示", "診断情報をコピー").forEach { menu.add(it) }
      else listOf("PCを追加", "QRスキャナー", "Relay診断", "ログ", "診断情報をコピー", "アプリ設定").forEach { menu.add(it) }
      setOnMenuItemClickListener { item ->
        when (item.title.toString()) {
          "PCを追加", "QRスキャナー" -> scanQr()
          "最新状態へ更新", "Relay診断" -> reconnectSelected()
          else -> toast(item.title.toString())
        }; true
      }
      show()
    }
  }

  private fun showHostMenu(anchor: View, item: PairingProfile) {
    PopupMenu(this, anchor).apply {
      listOf("ターゲットに設定", "接続 / 再接続", "接続テスト", "QRで再ペアリング", "資格情報を失効", "削除").forEach { menu.add(it) }
      setOnMenuItemClickListener { selected ->
        when (selected.title.toString()) {
          "ターゲットに設定", "接続 / 再接続", "接続テスト" -> connect(item)
          "QRで再ペアリング" -> scanQr()
          "資格情報を失効", "削除" -> confirmDelete(item, selected.title.toString())
        }; true
      }
      show()
    }
  }

  private fun confirmDelete(item: PairingProfile, action: String) {
    AlertDialog.Builder(this).setTitle("${item.name}の$action")
      .setMessage("保存したtokenとprofile用Keystore鍵を削除します。")
      .setNegativeButton("キャンセル", null).setPositiveButton(action) { _, _ ->
        if (profile?.id == item.id) { client?.stop(); client = null; profile = null; snapshot = null }
        repository.delete(item.id); render()
      }.show()
  }

  private fun targetButton(platform: HostPlatform, text: String): Button {
    val active = profile?.platform == platform
    return flatButton(text, if (active) BLUE else Color.rgb(224, 228, 235), if (active) Color.WHITE else TEXT).apply {
      setOnClickListener {
        repository.profiles().firstOrNull { it.platform == platform }?.let(::connect)
          ?: toast("${platformName(platform)}のPCが未登録です。")
      }
    }
  }

  private fun bottomNavigation(): View {
    val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE) }
    listOf(Screen.CONTROL to "⌁\nControl", Screen.PALETTE to "⌘\nPalette", Screen.USAGE to "▧\nUsage", Screen.HOSTS to "▣\nHosts").forEach { (screen, text) ->
      bar.addView(flatButton(text, if (currentScreen == screen) BLUE else Color.WHITE, if (currentScreen == screen) Color.WHITE else TEXT).apply {
        setOnClickListener { showScreen(screen) }
      }, weighted())
    }
    return bar
  }

  private fun reconnectSelected() {
    profile?.let(::connect) ?: showScreen(Screen.HOSTS)
  }

  private fun bindPress(button: CommandButton, factory: () -> ((Int) -> JSONObject)?) {
    var active = false
    button.cancelPress = { active = false; button.isPressed = false }
    pressButtons += button
    button.setOnTouchListener { view, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          val command = factory() ?: return@setOnTouchListener true
          active = true; haptic(); send(command(1), button.text.toString()); view.isPressed = true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (active) factory()?.let { send(it(0), button.text.toString()) }
          active = false; view.isPressed = false
          if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
        }
      }; true
    }
    button.setOnClickListener { }
  }

  private fun bindMicPress(button: CommandButton) {
    var active = false
    button.cancelPress = {
      if (active && canCommand()) send(CommandFactory.keycapPress("MIC", 0), "MIC:release")
      active = false; button.isPressed = false
    }
    pressButtons += button
    button.setOnTouchListener { view, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          active = true; selectedKeycap = OfficialKeycaps.find("MIC") ?: selectedKeycap
          haptic(); send(CommandFactory.keycapPress("MIC", 1), "MIC:press"); view.isPressed = true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (active) send(CommandFactory.keycapPress("MIC", 0), "MIC:release")
          active = false; view.isPressed = false
          if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
        }
      }; true
    }
    button.setOnClickListener { }
  }

  private fun bindRepeat(button: CommandButton, direction: String) {
    var active = false
    lateinit var repeat: Runnable
    repeat = Runnable { if (active) { send(CommandFactory.reasoning(direction), "Reasoning $direction"); handler.postDelayed(repeat, 300) } }
    button.cancelPress = { active = false; handler.removeCallbacks(repeat); button.isPressed = false }
    pressButtons += button
    button.setOnTouchListener { view, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { active = true; haptic(); send(CommandFactory.reasoning(direction), "Reasoning $direction"); handler.postDelayed(repeat, 500); view.isPressed = true }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { active = false; handler.removeCallbacks(repeat); view.isPressed = false; if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick() }
      }; true
    }
    button.setOnClickListener { }
  }

  private fun bindHold(button: CommandButton, millis: Long, action: () -> Unit) {
    var active = false
    val execute = Runnable { if (active) { active = false; haptic(); action() } }
    button.cancelPress = { active = false; handler.removeCallbacks(execute); button.isPressed = false }
    pressButtons += button
    button.setOnTouchListener { view, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { active = true; view.isPressed = true; handler.postDelayed(execute, millis) }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { active = false; view.isPressed = false; handler.removeCallbacks(execute); if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick() }
      }; true
    }
    button.setOnClickListener { }
  }

  private fun releasePresses() { pressButtons.toList().forEach { it.cancelPress() }; pressButtons.clear() }
  private fun send(command: JSONObject, label: String) { if (canCommand()) client?.send(command, label) else toast("操作できる接続状態ではありません。") }
  private fun sendActionTap(slot: String, label: String) {
    send(CommandFactory.action(slot, 1), label)
    handler.postDelayed({ send(CommandFactory.action(slot, 0), label) }, 16)
  }
  private fun canCommand() = connectionState == "ready" && snapshot != null

  override fun onConnection(state: String, detail: String?) {
    connectionState = state; connectionDetail = detail
    if (state != "ready") releasePresses()
    if (state in setOf("certificate_mismatch", "protocol_mismatch")) errorMessage = detail ?: state
    if (state == "ready" && !connectedMarked) { connectedMarked = true; profile?.let { repository.markConnected(it.id) } }
    render()
  }

  override fun onSnapshot(snapshot: DeckSnapshot) {
    this.snapshot = snapshot; connectionState = "ready"; connectionDetail = snapshot.hostName; errorMessage = null
    if (pressButtons.any { it.isPressed }) return
    render()
  }

  override fun onCommandResult(label: String, ok: Boolean, error: String?, data: JSONObject?) {
    when {
      label.startsWith("KEYCAP:") -> {
        val id = label.substringAfter(':')
        keycapResults[id] = if (ok) "成功 ${time(System.currentTimeMillis())}" else (error ?: "実行失敗")
        if (!ok) errorMessage = "$id: ${keycapResults[id]}"
        render()
      }
      label.startsWith("MIC:") -> {
        keycapResults["MIC"] = if (ok) "${label.substringAfter(':')} 成功 ${time(System.currentTimeMillis())}" else (error ?: "実行失敗")
        if (!ok) { errorMessage = "MIC: ${keycapResults["MIC"]}"; render() }
      }
      !ok -> { errorMessage = "$label: ${error ?: "実行できませんでした。"}"; render() }
      else -> toast("$label: 完了")
    }
  }

  override fun onPressedStateReset() { releasePresses() }

  private fun onDiscoveredRelay(relay: DiscoveredRelay) {
    val current = profile ?: return
    if (current.mode != "nearby" || current.hostId != relay.hostId || current.fingerprint != relay.fingerprint || current.platform != relay.platform || current.endpoint == relay.endpoint) return
    when (val update = repository.upsert(current.copy(endpoint = relay.endpoint))) {
      is ProfileUpdate.Saved -> connect(update.profile)
      else -> Unit
    }
  }

  private fun setError(message: String) { errorMessage = message; toast(message); render() }
  private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  private fun haptic() { getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)) }

  private fun sectionHeader(text: String, badge: String? = null): View {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(3), dp(18), dp(3), dp(7)) }
    row.addView(label(text, 16f, TEXT, true), weighted())
    badge?.let { row.addView(chip(it, Color.rgb(238, 241, 246), TEXT)) }
    return row
  }

  private fun usageCard(title: String, window: UsageWindowState?, host: String, observedAt: Long?): View {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(11), dp(12), dp(11)); background = rounded(Color.WHITE, 10f, BORDER) }
    box.addView(label("●  $title", 15f, TEXT, true))
    val used = window?.usedPercent ?: 0.0
    box.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = used.roundToInt(); progressTintList = android.content.res.ColorStateList.valueOf(if (used >= 90) RED else if (used >= 70) ORANGE else GREEN) }, match(top = 9, height = 10))
    box.addView(label("${used.roundToInt()}% 使用 / ${(window?.remainingPercent ?: 100.0).roundToInt()}% 残り", 18f, TEXT, true), match(top = 7))
    box.addView(small("期間: ${window?.windowDurationMins?.roundToInt()?.let { "${it}分" } ?: "—"}\nリセット: ${window?.resetsAt?.let(::dateTime) ?: "—"}\nホスト: $host\n更新: ${observedAt?.let(::time) ?: "—"}", MUTED), match(top = 6))
    return box
  }

  private fun infoCard(title: String, rows: List<Pair<String, String>>): View {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(11), dp(12), dp(11)); background = rounded(Color.WHITE, 10f, BORDER) }
    box.addView(label(title, 17f, BLUE, true))
    rows.forEach { (name, value) -> box.addView(small("$name:  $value", if (name == "状態") GREEN else TEXT), match(top = 4)) }
    return box
  }

  private fun alert(text: String) = label("⚠ $text", 13f, RED, true).apply { setPadding(dp(10), dp(9), dp(10), dp(9)); background = rounded(Color.rgb(255, 235, 238), 8f, RED) }
  private fun featureCard(title: String, detail: String, action: () -> Unit) = commandButton("$title\n$detail", Color.WHITE, TEXT).apply { setOnClickListener { action() } }
  private fun chip(text: String, color: Int, textColor: Int = Color.WHITE) = label(text, 11f, textColor, true).apply { setPadding(dp(8), dp(4), dp(8), dp(4)); background = rounded(color, 14f) }
  private fun small(text: String, color: Int = MUTED) = label(text, 12f, color, false)
  private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
  }
  private fun commandButton(text: String, backgroundColor: Int, textColor: Int) = CommandButton(this).apply {
    this.text = text; textSize = 11f; isAllCaps = false; gravity = Gravity.CENTER; setTextColor(textColor); setPadding(dp(3), dp(5), dp(3), dp(5)); background = rounded(backgroundColor, 8f, BORDER)
  }
  private fun flatButton(text: String, backgroundColor: Int, textColor: Int) = Button(this).apply {
    this.text = text; textSize = 12f; isAllCaps = false; gravity = Gravity.CENTER; setTextColor(textColor); setPadding(dp(4), 0, dp(4), 0); background = rounded(backgroundColor, 8f)
  }
  private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
  }
  private fun grid(columns: Int) = GridLayout(this).apply { columnCount = columns; alignmentMode = GridLayout.ALIGN_BOUNDS; useDefaultMargins = false }
  private fun gridParams(columns: Int, height: Int) = GridLayout.LayoutParams().apply {
    width = 0; this.height = dp(height); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(2), dp(2), dp(2), dp(2))
  }
  private fun weighted(left: Int = 0, height: Int = 48) = LinearLayout.LayoutParams(0, dp(height), 1f).apply { leftMargin = dp(left) }
  private fun match(top: Int = 0, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (height > 0) dp(height) else height).apply { topMargin = dp(top) }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
  private fun time(value: Long) = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(value))
  private fun dateTime(value: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
  private fun platformName(value: HostPlatform?) = if (value == HostPlatform.MACOS) "Mac" else "Windows"
  private fun statusLabel(status: String) = when (status) {
    "ready", "idle" -> "ready"; "working", "thinking" -> "動作中"; "unread", "complete", "completed", "done" -> "完了"
    "approval", "awaiting-approval", "awaiting-response" -> "承認待ち"; "error" -> "エラー"; "offline" -> "PC未接続"
    "stale" -> "stale"; "bridge_waiting" -> "Codex未接続"; "degraded" -> "制限あり"; "certificate_mismatch" -> "証明書不一致"; "protocol_mismatch" -> "protocol非互換"
    "off" -> "停止中"; else -> "接続中"
  }
  private fun statusColor(status: String) = when (status) {
    "ready", "idle", "complete", "completed", "done", "unread" -> GREEN
    "working", "thinking" -> PURPLE; "approval", "awaiting-approval", "awaiting-response" -> ORANGE
    "error", "offline", "certificate_mismatch", "protocol_mismatch" -> RED; "stale", "bridge_waiting", "degraded" -> ORANGE; else -> GRAY
  }
  private fun defaultActionLabel(slot: String) = mapOf("ACT06" to "FAST", "ACT07" to "APPR", "ACT08" to "REJ", "ACT09" to "SPLIT", "ACT10_ACT11" to "MIC", "ACT12" to "CODEX")[slot] ?: slot
  private fun keycapColor(id: String) = when (OfficialKeycaps.find(id)?.category) {
    "アクション" -> BLUE; "ナビゲーション" -> Color.rgb(38, 142, 88); "開発" -> Color.rgb(9, 146, 154); "その他" -> Color.rgb(105, 58, 210); else -> Color.rgb(91, 99, 110)
  }
  private fun usageModeLabel() = when (usageMode) { "auto" -> "自動モード"; "five-hour" -> "5時間"; "weekly" -> "週間"; else -> "カスタム" }
  private fun windowKind(kind: String) = when (kind) { "five-hour" -> "5時間ウィンドウ"; "weekly" -> "週次ウィンドウ"; else -> "その他（カスタム）" }

  companion object {
    private const val NEARBY_PERMISSION_REQUEST = 4701
    private val NAVY = Color.rgb(0, 39, 58)
    private val SURFACE = Color.rgb(246, 247, 249)
    private val TEXT = Color.rgb(15, 24, 39)
    private val MUTED = Color.rgb(82, 92, 107)
    private val BORDER = Color.rgb(218, 223, 231)
    private val BLUE = Color.rgb(20, 91, 226)
    private val PALE_BLUE = Color.rgb(230, 239, 255)
    private val GREEN = Color.rgb(20, 163, 68)
    private val PURPLE = Color.rgb(105, 31, 211)
    private val ORANGE = Color.rgb(245, 126, 20)
    private val RED = Color.rgb(226, 42, 48)
    private val GRAY = Color.rgb(139, 147, 158)
  }
}

private class CommandButton(context: Context) : Button(context) {
  var cancelPress: () -> Unit = {}
  override fun performClick(): Boolean = super.performClick()
}
