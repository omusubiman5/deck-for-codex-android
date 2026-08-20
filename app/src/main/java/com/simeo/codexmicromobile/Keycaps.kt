package com.simeo.codexmicromobile

data class KeycapDefinition(
  val id: String,
  val name: String,
  val category: String,
  val description: String
)

object OfficialKeycaps {
  val all = listOf(
    KeycapDefinition("FAST", "高速実行", "アクション", "Fast modeを切り替えます。"),
    KeycapDefinition("APPR", "承認", "アクション", "現在の承認要求を承認します。"),
    KeycapDefinition("REJ", "拒否", "アクション", "現在の承認要求を拒否します。"),
    KeycapDefinition("SPLIT", "分岐", "アクション", "現在のtaskを分岐します。"),
    KeycapDefinition("MIC", "マイク", "アクション", "音声入力を開始します。"),
    KeycapDefinition("CODEX", "送信", "アクション", "composerを送信します。"),
    KeycapDefinition("BUG", "バグ報告", "その他", "feedback画面を開きます。"),
    KeycapDefinition("OAI", "OpenAI", "その他", "OpenAI developer docsを開きます。"),
    KeycapDefinition("TERM", "ターミナル", "開発", "Codex terminalを切り替えます。"),
    KeycapDefinition("DWN", "Markdownコピー", "その他", "会話をMarkdownとしてコピーします。"),
    KeycapDefinition("DEL", "アーカイブ", "アクション", "現在のtaskをarchiveします。"),
    KeycapDefinition("NEW", "新規task", "アクション", "新しいCodex taskを作成します。"),
    KeycapDefinition("NAV", "ブラウザ", "ナビゲーション", "Codex browser tabを開きます。"),
    KeycapDefinition("MAGIC", "Pin切替", "アクション", "現在のtaskのpinを切り替えます。"),
    KeycapDefinition("DIFF", "Review", "開発", "review表示を切り替えます。"),
    KeycapDefinition("PLAY", "環境Action", "開発", "最初の環境アクションを実行します。"),
    KeycapDefinition("GIT", "Git Commit", "開発", "native commit flowを開きます。"),
    KeycapDefinition("BRCH", "Branch Review", "開発", "branch reviewを開きます。"),
    KeycapDefinition("MRG", "Merge Review", "開発", "merge reviewを開きます。"),
    KeycapDefinition("PR", "Pull Request", "開発", "pull-request flowを開きます。"),
    KeycapDefinition("PAINT", "写真追加", "その他", "composerへ写真を追加します。"),
    KeycapDefinition("LAB", "Lab", "その他", "Lab／Settingsを開きます。"),
    KeycapDefinition("PARTY", "Side Chat", "その他", "side chatを開きます。"),
    KeycapDefinition("TIME", "Task管理", "ナビゲーション", "task管理を開きます。"),
    KeycapDefinition("MIND+", "Reasoning +", "アクション", "reasoning effortを上げます。"),
    KeycapDefinition("MIND-", "Reasoning -", "アクション", "reasoning effortを下げます。"),
    KeycapDefinition("SETUP", "設定", "ナビゲーション", "Codex settingsを開きます。"),
    KeycapDefinition("FOLD", "Folder", "ナビゲーション", "folderを開きます。"),
    KeycapDefinition("UPL", "File追加", "その他", "composerへファイルを追加します。"),
    KeycapDefinition("APPS", "Skills", "ナビゲーション", "Codex Skillsを開きます。")
  )

  val ids = all.mapTo(linkedSetOf()) { it.id }
  fun find(id: String) = all.firstOrNull { it.id == id }
}
